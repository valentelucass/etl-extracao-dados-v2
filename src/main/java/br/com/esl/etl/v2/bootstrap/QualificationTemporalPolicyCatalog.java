package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import java.util.ArrayList;
import java.util.List;

/** Packaged, bounded temporal policy catalog; no SQL session is needed to inspect it. */
final class QualificationTemporalPolicyCatalog {
    private QualificationTemporalPolicyCatalog() {}

    static List<RuntimeTemporalOperation> policies() throws Exception {
        try (var stream =
                QualificationTemporalPolicyCatalog.class.getResourceAsStream(
                        "/analytic-laboratory/temporal-matrix-v2.synthetic.json")) {
            if (stream == null) {
                throw new IllegalArgumentException("QUAL_TEMPORAL_MATRIX_MISSING");
            }
            final var bytes = stream.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalArgumentException("QUAL_TEMPORAL_MATRIX_BOUND");
            }
            final var root = QualificationJson.parse(bytes, 16384);
            QualificationJson.fields(root, "version", "origin", "workloads");
            require(
                    root.path("version").asText().equals("qualification-temporal-matrix-v2")
                            && root.path("origin").asText().equals("CURRENT_FIVE_WORKLOAD_POLICIES")
                            && root.path("workloads").size() == 5,
                    "MATRIX_CONTRACT");
            final var result = new ArrayList<RuntimeTemporalOperation>();
            for (final var row : root.path("workloads")) {
                QualificationJson.fields(row, "path", "sha256", "document");
                require(
                        row.path("path")
                                        .asText()
                                        .matches(
                                                "config/laboratory/bloco5[45]-temporal-[a-z_]+\\.json")
                                && row.path("sha256").asText().matches("[a-f0-9]{64}"),
                        "MATRIX_ORIGIN");
                result.add(new RuntimeTemporalOperation(row.get("document")));
            }
            require(
                    result.stream().map(p -> p.workload).distinct().count() == 5,
                    "MATRIX_DUPLICATE");
            return List.copyOf(result);
        }
    }

    private static void require(final boolean condition, final String code) {
        if (!condition) {
            throw new IllegalArgumentException(code);
        }
    }
}
