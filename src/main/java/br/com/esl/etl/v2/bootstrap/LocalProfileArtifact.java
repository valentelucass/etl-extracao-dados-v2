package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Bounded normalized records, with explicit identities; this is not an authenticated wire format.
 */
public final class LocalProfileArtifact {
    private final LocalCharacterizationProfile profile;
    private final PinnedLocalJson manifest;
    private final List<PinnedLocalJson> pages;
    private final int pageSize;
    private final int maximumRows;
    private final int expectedRows;
    private final boolean complete;

    public LocalProfileArtifact(final Path file) throws IOException {
        manifest = PinnedLocalJson.open(file, 65536);
        final var root = manifest.read();
        QualificationJson.fields(
                root,
                "version",
                "origin",
                "family",
                "profileSha256",
                "revision",
                "source",
                "tenant",
                "windowStart",
                "windowEndExclusive",
                "zone",
                "pageSize",
                "maximumPages",
                "maximumRows",
                "expectedRows",
                "complete",
                "pages");
        profile = LocalCharacterizationProfile.valueOf(QualificationJson.text(root, "family", 4));
        if (!"local-profile-artifact-v1".equals(QualificationJson.text(root, "version", 40))
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1".equals(QualificationJson.text(root, "origin", 40))
                || !"SYNTHETIC_CHARACTERIZATION".equals(QualificationJson.text(root, "source", 40))
                || !"SYNTHETIC_TENANT".equals(QualificationJson.text(root, "tenant", 40))
                || !profile.sha256().equals(QualificationJson.digest(root, "profileSha256"))
                || !profile.revision().equals(QualificationJson.text(root, "revision", 64))
                || !"America/Sao_Paulo".equals(QualificationJson.text(root, "zone", 40))) {
            throw new IllegalArgumentException("LOCAL_PROFILE_BINDING");
        }
        final var start = LocalDate.parse(QualificationJson.text(root, "windowStart", 10));
        final var end = LocalDate.parse(QualificationJson.text(root, "windowEndExclusive", 10));
        if (!start.isBefore(end) || end.isAfter(start.plusDays(3))) {
            throw new IllegalArgumentException("LOCAL_PROFILE_WINDOW");
        }
        pageSize = QualificationJson.number(root, "pageSize", 1, 100);
        final int maximumPages = QualificationJson.number(root, "maximumPages", 1, 100);
        maximumRows = QualificationJson.number(root, "maximumRows", 1, 1000);
        expectedRows = QualificationJson.number(root, "expectedRows", 0, maximumRows);
        complete = QualificationJson.flag(root, "complete");
        QualificationJson.array(root.path("pages"), 1, maximumPages);
        final var declared = new ArrayList<PinnedLocalJson>();
        for (final var page : root.path("pages")) {
            declared.add(
                    PinnedLocalJson.reference(manifest.file().toPath().getParent(), page, 65536));
        }
        pages = List.copyOf(declared);
    }

    public Result inspect(final CancellationToken token) throws IOException {
        manifest.read();
        int rows = 0, invalid = 0, freshnessUnavailable = 0;
        int accumulatedBytes = 0;
        final var accumulatedShape = new int[2];
        for (int page = 0; page < pages.size(); page++) {
            token.throwIfCancellationRequested();
            final int previousBytes = accumulatedBytes;
            final var parsed =
                    pages.get(page)
                            .consume(
                                    bytes -> {
                                        if (bytes.length > 65536 - previousBytes) {
                                            throw new IllegalArgumentException(
                                                    "LOCAL_PROFILE_BYTES_BOUND");
                                        }
                                        return new Parsed(
                                                QualificationJson.parse(bytes, 65536),
                                                bytes.length);
                                    });
            accumulatedBytes += parsed.byteCount();
            final var records = parsed.records();
            QualificationJson.array(records, 0, pageSize);
            if (records.isEmpty() && page != pages.size() - 1
                    || complete && page == pages.size() - 1 && !records.isEmpty()) {
                throw new IllegalArgumentException("LOCAL_PROFILE_TERMINAL");
            }
            for (final var record : records) {
                token.throwIfCancellationRequested();
                if (++rows > maximumRows) {
                    throw new IllegalArgumentException("LOCAL_PROFILE_ROW_BOUND");
                }
                QualificationJson.fields(record, "sourceKey", "raw");
                final String raw = QualificationJson.text(record, "raw", 32768);
                final var data =
                        QualificationJson.parse(raw.getBytes(StandardCharsets.UTF_8), 32768);
                final var key = data.at(profile.identityPath());
                if (key.isMissingNode() || key.isNull() || !key.equals(record.path("sourceKey"))) {
                    throw new IllegalArgumentException("LOCAL_PROFILE_IDENTITY_BINDING");
                }
                final var observation = profile.inspect(raw, accumulatedShape);
                if (!observation.valid()) {
                    invalid++;
                }
                if (!observation.freshnessAvailable()) {
                    freshnessUnavailable++;
                }
            }
        }
        if (rows != expectedRows) {
            throw new IllegalArgumentException("LOCAL_PROFILE_CARDINALITY");
        }
        return new Result(
                profile.name(),
                manifest.sha256(),
                pages.size(),
                rows,
                invalid,
                freshnessUnavailable,
                complete);
    }

    private record Parsed(JsonNode records, int byteCount) {}

    public record Result(
            String family,
            String inputSha256,
            int pages,
            int rows,
            int invalid,
            int freshnessUnavailable,
            boolean completeSynthetic) {
        public boolean structurallyValid() {
            return invalid == 0 && freshnessUnavailable == 0 && completeSynthetic;
        }
    }
}
