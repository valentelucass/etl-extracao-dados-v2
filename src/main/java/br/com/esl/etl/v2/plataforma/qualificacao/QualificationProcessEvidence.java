package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Closed child evidence. Exit, rollback, process ownership and exact coverage are distinct checks.
 */
public final class QualificationProcessEvidence {
    private QualificationProcessEvidence() {}

    public static void process(
            final JsonNode node, final UUID nonce, final String jar, final Path java) {
        QualificationJson.fields(node, "pid", "start", "nonce", "java", "jar");
        if (!node.path("pid").isIntegralNumber()
                || !node.path("pid").canConvertToLong()
                || node.path("pid").longValue() < 1
                || !nonce.toString().equals(QualificationJson.text(node, "nonce", 36))
                || !jar.equals(QualificationJson.digest(node, "jar"))
                || !java.toAbsolutePath()
                        .normalize()
                        .equals(
                                Path.of(QualificationJson.text(node, "java", 512))
                                        .toAbsolutePath()
                                        .normalize())) {
            throw new IllegalArgumentException("QUAL_PROCESS_EVIDENCE_BINDING");
        }
        Instant.parse(QualificationJson.text(node, "start", 40));
    }

    public static boolean sameLiveOwner(final JsonNode node) {
        final var handle = ProcessHandle.of(node.path("pid").longValue());
        return handle.isPresent()
                && handle.get().isAlive()
                && handle.get()
                        .info()
                        .startInstant()
                        .map(Instant::toString)
                        .filter(node.path("start").asText()::equals)
                        .isPresent()
                && handle.get()
                        .info()
                        .command()
                        .map(Path::of)
                        .map(path -> path.toAbsolutePath().normalize())
                        .filter(
                                Path.of(node.path("java").asText()).toAbsolutePath().normalize()
                                        ::equals)
                        .isPresent();
    }

    public static void coverage(final JsonNode report) {
        if (QualificationJson.number(report, "physicalColumns", 971, 971) != 971) {
            throw new IllegalArgumentException("QUAL_RECEIPT_PHYSICAL_COLUMNS");
        }
        final Set<String> outputs =
                Arrays.stream(AnalyticSqlContract.values())
                        .map(AnalyticSqlContract::id)
                        .collect(Collectors.toSet());
        final var observed = new HashSet<String>();
        QualificationJson.array(report.path("outputs"), 19, 19);
        for (final var row : report.path("outputs")) {
            QualificationJson.fields(
                    row,
                    "contract",
                    "expectedRows",
                    "observedRows",
                    "differences",
                    "gate",
                    "sample");
            if (!outputs.contains(row.path("contract").asText())
                    || !observed.add(row.path("contract").asText())
                    || QualificationJson.number(row, "differences", 0, 0) != 0
                    || QualificationJson.number(row, "expectedRows", 0, 4096)
                            != QualificationJson.number(row, "observedRows", 0, 4096)
                    || !"PASS_LOCAL".equals(row.path("gate").asText())
                    || !row.path("sample").isArray()
                    || !row.path("sample").isEmpty()) {
                throw new IllegalArgumentException("QUAL_RECEIPT_OUTPUT_COVERAGE");
            }
        }
        final Set<String> scopes =
                QualificationTopology.nodes().stream()
                        .map(QualificationTopology.Node::id)
                        .collect(Collectors.toSet());
        observed.clear();
        QualificationJson.array(report.path("scopes"), 35, 35);
        for (final var row : report.path("scopes")) {
            QualificationJson.fields(row, "scope", "state", "reason", "layer");
            final var scope =
                    new QualificationGate(
                            row.path("scope").asText(),
                            QualificationGate.State.valueOf(row.path("state").asText()),
                            row.path("reason").asText(),
                            row.path("layer").asText());
            if (!scopes.contains(scope.scope()) || !observed.add(scope.scope())) {
                throw new IllegalArgumentException("QUAL_RECEIPT_SCOPE_COVERAGE");
            }
        }
    }

    public static void reconciliation(
            final JsonNode node,
            final UUID nonce,
            final JsonNode baseline,
            final JsonNode receipt,
            final String receiptSha) {
        QualificationJson.fields(
                node,
                "nonce",
                "exit",
                "forced",
                "before",
                "after",
                "rollback",
                "receipt",
                "terminal");
        QualificationJson.fields(baseline, "nonce", "aggregate");
        final String before = QualificationJson.digest(node, "before");
        final String after = QualificationJson.digest(node, "after");
        final boolean terminal = QualificationJson.flag(node, "terminal");
        final boolean rollback = QualificationJson.flag(node, "rollback");
        final boolean forced = QualificationJson.flag(node, "forced");
        final int exit = QualificationJson.number(node, "exit", -1, 65535);
        if (!nonce.toString().equals(node.path("nonce").asText())
                || !nonce.toString().equals(baseline.path("nonce").asText())
                || !before.equals(QualificationJson.digest(baseline, "aggregate"))
                || rollback != before.equals(after)
                || !receiptSha.equals(node.path("receipt").asText())
                || terminal
                        && (forced
                                || !rollback
                                || receipt == null
                                || exit < 0
                                || exit != receipt.path("exit").intValue()
                                || !receipt.path("rollback").booleanValue())) {
            throw new IllegalArgumentException("QUAL_RECONCILIATION_BINDING");
        }
    }

    public static String seal(final Path directory) throws IOException {
        final var values = new java.util.ArrayList<String>(5);
        for (final var name :
                java.util.List.of(
                        "process.json",
                        "receipt.json",
                        "reconciliation.json",
                        "stdout.log",
                        "stderr.log")) {
            final var path = directory.resolve(name);
            values.add(Files.exists(path) ? QualificationJson.sha256(path) : "MISSING");
        }
        return QualificationJson.sha256(
                ("qualification-process-evidence-v1:" + String.join(":", values))
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
