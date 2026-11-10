package br.com.esl.etl.v2.plataforma.producao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Source references select complete dates to recapture; they never identify or merge report rows.
 */
final class FreightDependencyIndex {
    private final Path directory;
    private final Path allowedRoot;
    private final String scope;

    FreightDependencyIndex(final Path directory, final Path allowedRoot, final String scope) {
        this.directory = directory.toAbsolutePath().normalize();
        this.allowedRoot = allowedRoot.toAbsolutePath().normalize();
        this.scope = scope;
        require(this.directory.startsWith(this.allowedRoot), "RELATED_INDEX_PRIVATE_SCOPE");
    }

    static boolean supports(final ReportSnapshotTemplate template) {
        return template == ReportSnapshotTemplate.OCORRENCIAS
                || template == ReportSnapshotTemplate.CONSOLIDACAO_DETALHADA;
    }

    private Path pointer(final ReportSnapshotTemplate template, final LocalDate day) {
        require(supports(template), "RELATED_INDEX_ENTITY");
        return directory.resolve(template.name()).resolve(day + ".json");
    }

    boolean exists(final ReportSnapshotTemplate template, final LocalDate day) {
        return Files.isRegularFile(pointer(template, day));
    }

    void remember(
            final ReportSnapshotTemplate template,
            final LocalDate day,
            final UUID run,
            final String captureSha,
            final List<String> rows,
            final Path captureRoot)
            throws Exception {
        if (!supports(template)) {
            return;
        }
        final boolean occurrences = template == ReportSnapshotTemplate.OCORRENCIAS;
        final var index =
                CurlSnapshotWire.JSON
                        .createObjectNode()
                        .put("schema", "FREIGHT_DEPENDENCY_INDEX0768")
                        .put("sourceScope", scope)
                        .put("entity", template.name())
                        .put("day", day.toString())
                        .put("runId", run.toString())
                        .put("captureSha256", captureSha)
                        .put("physicalRows", rows.size());
        final var references = index.putArray("references");
        final Set<String> seen = new HashSet<>();
        for (final var text : rows) {
            final var row = CurlSnapshotWire.JSON.readTree(text).required("record");
            final var reference = CurlSnapshotWire.JSON.createObjectNode();
            reference.put(
                    "draft",
                    number(
                            row.path(
                                    occurrences
                                            ? "ice_f_e_fit_corporation_sequence_number"
                                            : "ctd_fgs_corporation_sequence_number")));
            reference.put(
                    "cte",
                    number(
                            row.path(
                                    occurrences
                                            ? "ice_fit_fhe_cte_number"
                                            : "ctd_fgs_fhe_cte_number")));
            reference.put(
                    "serviceDay",
                    serviceDay(
                            row.path(occurrences ? "ice_fit_service_at" : "ctd_fgs_service_at")));
            if (occurrences) {
                reference.set("location", row.required("ice_fit_fln_cln_nickname"));
            }
            if (seen.add(reference.toString())) {
                references.add(reference);
            }
        }
        final var bytes = CurlSnapshotWire.JSON.writeValueAsBytes(index);
        require(bytes.length <= 16000000, "RELATED_INDEX_SIZE_BOUND");
        final var file =
                captureRoot.resolve(template.name() + "-" + day + "-" + run + ".index.json");
        require(file.toAbsolutePath().normalize().startsWith(allowedRoot), "RELATED_INDEX_PATH");
        RefreshCheckpoint.atomic(file, bytes);
        final var pin =
                CurlSnapshotWire.JSON
                        .createObjectNode()
                        .put("path", file.toString())
                        .put("sha256", RefreshCheckpoint.hash(bytes));
        // Called only after the complete snapshot and every SQL value were confirmed.
        RefreshCheckpoint.atomic(
                pointer(template, day), CurlSnapshotWire.JSON.writeValueAsBytes(pin));
    }

    JsonNode read(final ReportSnapshotTemplate template, final LocalDate day) throws Exception {
        final var pointer = pointer(template, day);
        require(Files.size(pointer) <= 8192, "RELATED_INDEX_POINTER_BOUND");
        final var pin = CurlSnapshotWire.JSON.readTree(Files.readAllBytes(pointer));
        final var file = Path.of(pin.required("path").asText()).toAbsolutePath().normalize();
        require(file.startsWith(allowedRoot) && Files.size(file) <= 16000000, "RELATED_INDEX_PATH");
        final var bytes = Files.readAllBytes(file);
        require(
                RefreshCheckpoint.hash(bytes).equals(pin.required("sha256").asText()),
                "RELATED_INDEX_SHA");
        final var index = CurlSnapshotWire.JSON.readTree(bytes);
        require(
                index.path("schema").asText().equals("FREIGHT_DEPENDENCY_INDEX0768")
                        && index.path("sourceScope").asText().equals(scope)
                        && index.path("entity").asText().equals(template.name())
                        && index.path("day").asText().equals(day.toString())
                        && index.path("references").isArray(),
                "RELATED_INDEX_BINDING");
        return index;
    }

    TreeSet<LocalDate> serviceDays(
            final ReportSnapshotTemplate template, final LocalDate floor, final LocalDate through)
            throws Exception {
        final var dates = new TreeSet<LocalDate>();
        for (var day = floor; !day.isAfter(through); day = day.plusDays(1)) {
            for (final var reference : read(template, day).required("references")) {
                if (reference.hasNonNull("serviceDay")) {
                    dates.add(LocalDate.parse(reference.required("serviceDay").asText()));
                }
            }
        }
        return dates;
    }

    static String number(final JsonNode value) {
        if (value.isIntegralNumber()
                || (value.isTextual() && value.asText().matches("[0-9]{1,30}"))) {
            final var parsed = new java.math.BigInteger(value.asText());
            return parsed.signum() > 0 ? parsed.toString() : null;
        }
        return null;
    }

    static String serviceDay(final JsonNode value) {
        if (!value.isTextual()) {
            return null;
        }
        try {
            final ObjectNode row = CurlSnapshotWire.JSON.createObjectNode();
            row.set("service_at", value);
            return ReportSnapshotExtractor.businessDate(ReportSnapshotTemplate.FRETES, row)
                    .toString();
        } catch (final RuntimeException invalid) {
            return null;
        }
    }

    static String location(final JsonNode value) {
        require(value.isNull() || value.isTextual(), "RELATED_LOCATION_TYPE");
        return value.toString();
    }

    private static void require(final boolean accepted, final String reason) {
        if (!accepted) {
            throw new IllegalStateException(reason);
        }
    }
}
