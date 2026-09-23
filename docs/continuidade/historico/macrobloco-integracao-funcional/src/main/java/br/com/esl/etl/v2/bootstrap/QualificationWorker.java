package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationCampaign;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationControlFiles;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualifiedPackage;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

/** Private child protocol: intent and PID/start handshake are proven before domain JDBC. */
public final class QualificationWorker {
    private QualificationWorker() {}

    public static int run(
            final QualifiedPackage payload,
            final QualificationCampaign campaign,
            final QualificationConfiguration configuration,
            final QualificationControlFiles files,
            final QualificationCampaign.Case item,
            final UUID nonce)
            throws Exception {
        final var directory = files.attempt(item.id(), false);
        final var intent = QualificationJson.read(directory.resolve("intent.json"), 4096);
        QualificationJson.fields(
                intent,
                "version",
                "case",
                "nonce",
                "campaign",
                "package",
                "configuration",
                "caseSeconds");
        final String campaignHash = QualificationJson.sha256(files.root().resolve("campaign.json"));
        final String configurationHash =
                QualificationJson.sha256(files.root().resolve("configuration.json"));
        if (!"qualification-intent-v1".equals(intent.path("version").asText())
                || !item.id().equals(intent.path("case").asText())
                || !nonce.toString().equals(intent.path("nonce").asText())
                || !campaignHash.equals(intent.path("campaign").asText())
                || !payload.manifestSha256().equals(intent.path("package").asText())
                || !configurationHash.equals(intent.path("configuration").asText())
                || intent.path("caseSeconds").intValue() != configuration.caseSeconds()) {
            throw new IllegalArgumentException("QUAL_WORKER_INTENT_MISMATCH");
        }
        final var process = handshake(directory, nonce, payload);
        final Instant started = Instant.now();
        final var report = JsonNodeFactory.instance.objectNode();
        QualificationGate.State state = QualificationGate.State.FAILED;
        String reason = "CASE_NOT_COMPLETED";
        boolean executionFailure = false;
        boolean rollback = false;
        int spid = 0;
        String before = "NO_SQL";
        String after = "NO_SQL";
        try (var control =
                new QualificationCaseControl(
                        directory, nonce, item.barrier(), configuration.caseSeconds())) {
            try {
                control.barrier(QualificationCampaign.Barrier.BEFORE_SQL);
                QualificationSqlEvidence.master(configuration);
                try (var session = ColetaTemporalLaboratorySession.open(configuration.jdbcUrl())) {
                    session.controlStatements(
                            configuration.querySeconds(), configuration.maximumJdbcCalls());
                    control.attach(session);
                    before = QualificationSqlEvidence.snapshot(session).sha256();
                    spid = QualificationSqlEvidence.spid(session);
                    QualificationControlFiles.atomic(
                            QualificationControlFiles.member(directory, "owner.json"),
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("nonce", nonce.toString())
                                    .put("spid", spid)
                                    .put("before", before));
                    try {
                        report.put(
                                "physicalColumns",
                                new QualificationPhysicalMetadata().verify(session));
                        final var result =
                                new QualificationCaseExecutor(payload, configuration)
                                        .execute(
                                                session,
                                                campaign,
                                                item,
                                                nonce,
                                                started,
                                                control::barrier,
                                                control);
                        state = result.state();
                        reason = result.reason();
                        report.setAll(result.report());
                    } finally {
                        // JDBC rollback is observed before receipt, not inferred from exit or a log
                        // message.
                        session.rollback();
                        after = QualificationSqlEvidence.snapshot(configuration).sha256();
                        rollback = before.equals(after) && session.openControlledStatements() == 0;
                    }
                }
            } catch (final Exception failure) {
                if (control.isCancellationRequested()
                        && !control.reason().startsWith("CANCELLATION_")) {
                    state = QualificationGate.State.CANCELLED;
                    reason = control.reason();
                    if (spid == 0) {
                        rollback = true;
                    }
                } else {
                    executionFailure = true;
                    state = QualificationGate.State.FAILED;
                    reason = "CASE_EXECUTION_EXCEPTION";
                    report.put("failureClass", failure.getClass().getSimpleName());
                    // The message is reduced to a closed diagnostic code, never raw SQL or driver
                    // text.
                    final String message = failure.getMessage();
                    if (message != null && message.matches("[A-Z][A-Z0-9_]{1,80}")) {
                        report.put("failureCode", message);
                    }
                }
            }
            if (!rollback) {
                state = QualificationGate.State.OUTCOME_UNKNOWN;
                reason = "ROLLBACK_UNCONFIRMED";
            }
            if (item.barrier() == QualificationCampaign.Barrier.BEFORE_RECEIPT
                    && rollback
                    && !executionFailure) {
                control.barrier(QualificationCampaign.Barrier.BEFORE_RECEIPT);
                return 0;
            }
        }
        final boolean passed = !executionFailure && rollback && item.expected() == state;
        final var receipt =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-receipt-v1")
                        .put("case", item.id())
                        .put("nonce", nonce.toString())
                        .put("campaign", campaignHash)
                        .put("package", payload.manifestSha256())
                        .put("configuration", configurationHash)
                        .put("pid", ProcessHandle.current().pid())
                        .put("processStart", process.path("start").asText())
                        .put("state", state.name())
                        .put("reason", reason)
                        .put("testPassed", passed)
                        .put("exit", passed ? 0 : 2)
                        .put("rollback", rollback)
                        .put("spid", spid)
                        .put("before", before)
                        .put("after", after)
                        .put("started", started.toString())
                        .put("finished", Instant.now().toString());
        receipt.set("report", report);
        QualificationControlFiles.atomic(
                QualificationControlFiles.member(directory, "receipt.json"), receipt);
        return passed ? 0 : 2;
    }

    private static ObjectNode handshake(
            final Path directory, final UUID nonce, final QualifiedPackage payload)
            throws Exception {
        final var path = directory.resolve("process.json");
        final long deadline = System.nanoTime() + 10_000_000_000L;
        while (!Files.exists(path) && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }
        final var json = QualificationJson.read(path, 8192);
        QualificationJson.fields(json, "pid", "start", "nonce", "java", "jar");
        br.com.esl.etl.v2.plataforma.qualificacao.QualificationProcessEvidence.process(
                json,
                nonce,
                payload.members().get("etl-dataexport-v2.jar").sha256(),
                Path.of(System.getProperty("java.home"), "bin", "java.exe"));
        final var process = ProcessHandle.current();
        final String start = process.info().startInstant().orElseThrow().toString();
        if (json.path("pid").longValue() != process.pid()
                || !start.equals(json.path("start").asText())
                || !nonce.toString().equals(json.path("nonce").asText())
                || !Files.isSameFile(
                        Path.of(json.path("java").asText()),
                        Path.of(process.info().command().orElseThrow()))) {
            throw new IllegalArgumentException("QUAL_WORKER_PROCESS_MISMATCH");
        }
        return (ObjectNode) json;
    }
}
