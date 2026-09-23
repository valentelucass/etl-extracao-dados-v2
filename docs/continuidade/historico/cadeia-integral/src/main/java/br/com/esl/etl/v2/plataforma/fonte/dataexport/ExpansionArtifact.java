package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A local synthetic capture manifest. File integrity never authenticates a supplier contract. */
public final class ExpansionArtifact {
    public static final int MAXIMUM_PAGE_BYTES = 65_536;
    private final Path directory;
    private final DataExportTemplate template;
    private final LocalDate date;
    private final int pageSize;
    private final int maximumPages;
    private final int maximumRows;
    private final boolean complete;
    private final List<Page> pages;
    private final String fingerprint;

    private ExpansionArtifact(final Path path, final byte[] bytes) throws IOException {
        final var root = QualificationJson.parse(bytes, 262_144);
        QualificationJson.fields(
                root,
                "version",
                "origin",
                "family",
                "contractSha256",
                "date",
                "source",
                "tenant",
                "pageSize",
                "maximumPages",
                "maximumRows",
                "complete",
                "pages");
        if (QualificationJson.number(root, "version", 1, 1) != 1
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1".equals(QualificationJson.text(root, "origin", 64))
                || !"SYNTHETIC_EXPANSION_LAB".equals(QualificationJson.text(root, "source", 64))
                || !"SYNTHETIC_TENANT".equals(QualificationJson.text(root, "tenant", 64))) {
            throw new IllegalArgumentException("EXP_ARTIFACT_SCOPE");
        }
        template =
                switch (QualificationJson.text(root, "family", 3)) {
                    case "CAP" -> DataExportTemplate.CONTAS_A_PAGAR;
                    case "FAT" -> DataExportTemplate.FATURAS_POR_CLIENTE;
                    case "INV" -> DataExportTemplate.INVENTARIO;
                    case "SIN" -> DataExportTemplate.SINISTROS;
                    default -> throw new IllegalArgumentException("EXP_ARTIFACT_FAMILY");
                };
        if (!ExpansionLocalContract.release(template)
                .contractFingerprint()
                .sha256()
                .equals(QualificationJson.digest(root, "contractSha256"))) {
            throw new IllegalArgumentException("EXP_ARTIFACT_CONTRACT");
        }
        date = LocalDate.parse(QualificationJson.text(root, "date", 10));
        pageSize = QualificationJson.number(root, "pageSize", 1, 100);
        maximumPages = QualificationJson.number(root, "maximumPages", 1, 1000);
        maximumRows = QualificationJson.number(root, "maximumRows", 1, 100000);
        complete = QualificationJson.flag(root, "complete");
        QualificationJson.array(root.path("pages"), 1, maximumPages);
        final var declared = new ArrayList<Page>();
        for (final var node : root.path("pages")) {
            QualificationJson.fields(node, "file", "sha256");
            final String file = QualificationJson.text(node, "file", 80);
            if (!file.matches("[a-z0-9][a-z0-9-]{0,70}\\.json")) {
                throw new IllegalArgumentException("EXP_ARTIFACT_PATH");
            }
            declared.add(new Page(file, QualificationJson.digest(node, "sha256")));
        }
        pages = List.copyOf(declared);
        directory = path.toAbsolutePath().normalize().getParent();
        fingerprint = QualificationJson.sha256(bytes);
    }

    public static ExpansionArtifact read(final Path path) throws IOException {
        QualificationJson.regular(path);
        try (var input = Files.newInputStream(path)) {
            return new ExpansionArtifact(path, input.readNBytes(262_145));
        }
    }

    public static ExpansionArtifact read(final PinnedLocalJson pin) throws IOException {
        return pin.consume(bytes -> new ExpansionArtifact(pin.file().toPath(), bytes));
    }

    /** Preflight walks one page at a time; no capture effect or record universe is retained. */
    public Inspection inspect(final CancellationToken token, final Consumer<JsonNode> inspectRow) {
        long rows = 0;
        long bytes = 0;
        int absentBindings = 0;
        for (int ordinal = 1; ordinal <= pages.size(); ordinal++) {
            token.throwIfCancellationRequested();
            final String page = page(ordinal);
            bytes += page.getBytes(StandardCharsets.UTF_8).length;
            final JsonNode tree;
            try {
                tree =
                        QualificationJson.parse(
                                page.getBytes(StandardCharsets.UTF_8), MAXIMUM_PAGE_BYTES);
            } catch (final IOException failure) {
                throw new UncheckedIOException(failure);
            }
            QualificationJson.array(tree, 0, 100);
            if (tree.size() > pageSize
                    || tree.isEmpty() && ordinal != pages.size()
                    || complete && ordinal == pages.size() && !tree.isEmpty()) {
                throw new IllegalArgumentException("EXP_ARTIFACT_TERMINAL_OR_PAGE_BOUND");
            }
            rows += tree.size();
            if (rows > maximumRows) {
                throw new IllegalArgumentException("EXP_ARTIFACT_ROW_BOUND");
            }
            for (final var row : tree) {
                token.throwIfCancellationRequested();
                if (!row.path("capture_occurrence").isIntegralNumber()
                        || !row.path("capture_occurrence").canConvertToLong()
                        || !"FIXTURE_SINTETICA_EXPLICITA".equals(row.path("provenance").asText())
                        || !row.path("data").isObject()) {
                    throw new IllegalArgumentException("EXP_ARTIFACT_ROW_ENVELOPE");
                }
                if (!row.hasNonNull("binding")) {
                    absentBindings++;
                }
                inspectRow.accept(row);
            }
        }
        return new Inspection(
                pages.size(),
                rows,
                bytes,
                absentBindings,
                complete,
                "STRUCTURALLY_VALID_UNVERIFIED");
    }

    public ExpansionCaptureSource source(final ExpansionCaptureSource.Observer observer) {
        return new ExpansionPageSource(this::page, observer) {
            @Override
            public void validate(
                    final DataExportTemplate selected,
                    final LocalDate selectedDate,
                    final ExpansionPolicy policy) {
                if (selected != template
                        || !date.equals(selectedDate)
                        || policy.pageSize() != pageSize
                        || maximumPages > policy.maximumPages()
                        || maximumRows > policy.maximumRows()
                        || !complete) {
                    throw new IllegalArgumentException("EXP_ARTIFACT_EXECUTION_BINDING");
                }
                policy.validate(date);
                inspect(CancellationToken.none(), row -> {});
            }
        };
    }

    private String page(final int ordinal) {
        if (ordinal < 1 || ordinal > pages.size()) {
            throw new IllegalArgumentException("EXP_ARTIFACT_PAGE_MISSING");
        }
        final var descriptor = pages.get(ordinal - 1);
        final Path file = directory.resolve(descriptor.file());
        try {
            QualificationJson.regular(file);
            final byte[] bytes;
            try (var input = Files.newInputStream(file)) {
                bytes = input.readNBytes(MAXIMUM_PAGE_BYTES + 1);
            }
            // Parse the exact hashed bytes, preventing a second read from accepting a changed file.
            QualificationJson.parse(bytes, MAXIMUM_PAGE_BYTES);
            if (!QualificationJson.sha256(bytes).equals(descriptor.sha256())) {
                throw new IllegalArgumentException("EXP_ARTIFACT_PAGE_CHANGED");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (final IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public DataExportTemplate template() {
        return template;
    }

    public LocalDate date() {
        return date;
    }

    public int pageSize() {
        return pageSize;
    }

    public int maximumPages() {
        return maximumPages;
    }

    public int maximumRows() {
        return maximumRows;
    }

    public String fingerprint() {
        return fingerprint;
    }

    private record Page(String file, String sha256) {}

    public record Inspection(
            int pages,
            long rows,
            long bytes,
            int absentBindings,
            boolean completeSyntheticTraversal,
            String providerEvidence) {}
}
