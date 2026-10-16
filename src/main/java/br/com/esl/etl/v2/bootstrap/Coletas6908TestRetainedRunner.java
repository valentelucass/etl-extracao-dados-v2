package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.TestRetainedSqlTarget;
import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCounts;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedSqlSession;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/** Explicit partial capture; the separate bank session alone owns commit and readback. */
final class Coletas6908TestRetainedRunner {
    private Coletas6908TestRetainedRunner() {}

    static Map<String, Object> execute(
            final Coletas6908TestRetainedRequest request,
            final DataExportSourceConfiguration configuration,
            final SecretProvider secrets,
            final Clock clock,
            final Runnable deadline)
            throws java.sql.SQLException {
        deadline.run();
        final var target = TestRetainedSqlTarget.approved("USER_20261005_E3_TEST_RETAINED");
        return execute(
                request,
                configuration,
                secrets,
                clock,
                deadline,
                () ->
                        TestRetainedSqlSession.open(
                                target,
                                secrets,
                                new TestRetainedSqlSession.Limits(1200, 1000, 10, 60),
                                request.executionId,
                                Coletas6908TestRetainedRequest.SOURCE,
                                Coletas6908TestRetainedRequest.TENANT),
                Coletas6908TestRetainedSource::gateways);
    }

    @FunctionalInterface
    interface SessionFactory {
        TestRetainedSqlSession open() throws java.sql.SQLException;
    }

    @FunctionalInterface
    interface SourceFactory {
        br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle create(
                DataExportSourceConfiguration configuration,
                SecretProvider secrets,
                Clock clock,
                Coletas6908TestRetainedSource.Attempts attempts,
                Runnable deadline);
    }

    static Map<String, Object> execute(
            final Coletas6908TestRetainedRequest request,
            final DataExportSourceConfiguration configuration,
            final SecretProvider secrets,
            final Clock clock,
            final Runnable deadline,
            final SessionFactory sessionFactory,
            final SourceFactory sourceFactory)
            throws java.sql.SQLException {
        deadline.run();
        final var session = sessionFactory.open();
        final var receipt = new java.util.LinkedHashMap<String, Object>();
        receipt.put("sessionPostCommitReadback", false);
        final Runnable checkpoint =
                () -> {
                    deadline.run();
                    try {
                        session.checkpoint();
                    } catch (final java.sql.SQLException failure) {
                        throw new BankCheckpointFailure(failure);
                    }
                };
        final var attempts = new Coletas6908TestRetainedSource.Attempts(deadline);
        try {
            try (session) {
                checkpoint.run();
                final var control = new JdbcSqlServerControlPlane(session);
                final var staging = new JdbcSqlServerColetaStagingGateway(session);
                final var capture =
                        captureAndStage(
                                request,
                                configuration,
                                control,
                                staging,
                                clock,
                                checkpoint,
                                deadline,
                                () ->
                                        Coletas6908TestRetainedSource.capture(
                                                sourceFactory.create(
                                                        configuration,
                                                        secrets,
                                                        clock,
                                                        attempts,
                                                        deadline),
                                                clock,
                                                attempts,
                                                deadline,
                                                checkpoint));
                checkpoint.run();
                receipt.put("physicalRows", capture.records().size());
                receipt.put("mappedRows", capture.records().size());
                receipt.put("distinctRoots", capture.distinctRoots());
                receipt.put("quarantinedRows", 0);
                final var committed =
                        session.finishPartial(
                                new TestRetainedSqlSession.Expected(
                                        request.executionId,
                                        capture.records().size(),
                                        capture.records().size(),
                                        capture.distinctRoots()),
                                deadline);
                receipt.put("bankOutcome", committed.outcome());
                receipt.put("committed", committed.committed());
                receipt.put("physicalRows", committed.physicalRows());
                receipt.put("mappedRows", committed.mappedRows());
                receipt.put("distinctRoots", capture.distinctRoots());
                receipt.put("quarantinedRows", 0);
                receipt.put("sqlExecutions", committed.sqlExecutions());
                receipt.put("suppressedCommits", committed.suppressedCommits());
                receipt.put("sessionPostCommitReadback", committed.postCommitReadback());
                receipt.put("infoHttpStatus", capture.infoHttpStatus());
                receipt.put("metadataFields", capture.metadataFields());
                receipt.put("dataHttpStatusClass", "2XX_EXACT_STATUS_NOT_EXPOSED_BY_PORT");
            }
            deadline.run();
            receipt.put("status", "CAPTURE_COMMITTED_PARTIAL_AWAITING_INDEPENDENT_READBACK");
        } catch (final java.sql.SQLException | RuntimeException failure) {
            receipt.put("status", "STOP_NO_RETRY");
            receipt.put("failureType", failure.getClass().getSimpleName());
            receipt.put("failureCode", Coletas6908TestRetainedMain.failureCode(failure));
            Coletas6908TestRetainedMain.describeFailure(receipt, failure);
        }
        receipt.put("infoAttempts", attempts.info);
        receipt.put("dataAttempts", attempts.data);
        receipt.put("callsAttempted", attempts.info + attempts.data);
        receipt.put("closeObserved", session.closeObserved());
        receipt.put("commitUncertain", session.commitUncertain());
        receipt.put("committed", session.committed());
        receipt.put("sqlExecutions", session.sqlExecutions());
        receipt.put("independentPostCommitReadback", false);
        receipt.put("windowComplete", false);
        receipt.put("corePromotion", false);
        receipt.put(
                "ctlState",
                "CAPTURE_COMMITTED_PARTIAL_AWAITING_INDEPENDENT_READBACK"
                                .equals(receipt.get("status"))
                        ? "CANCELLED"
                        : "NOT_CLAIMED");
        return Map.copyOf(receipt);
    }

    private static final class BankCheckpointFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;

        BankCheckpointFailure(final java.sql.SQLException cause) {
            super("TEST_BANK_CHECKPOINT_REFUSED", cause);
        }
    }

    static Coletas6908TestRetainedSource.Capture captureAndStage(
            final Coletas6908TestRetainedRequest request,
            final DataExportSourceConfiguration configuration,
            final ControlPlane control,
            final ColetaStagingGateway staging,
            final Clock clock,
            final Runnable checkpoint,
            final Supplier<Coletas6908TestRetainedSource.Capture> capture) {
        return captureAndStage(
                request, configuration, control, staging, clock, checkpoint, checkpoint, capture);
    }

    static Coletas6908TestRetainedSource.Capture captureAndStage(
            final Coletas6908TestRetainedRequest request,
            final DataExportSourceConfiguration configuration,
            final ControlPlane control,
            final ColetaStagingGateway staging,
            final Clock clock,
            final Runnable checkpoint,
            final Runnable deadline,
            final Supplier<Coletas6908TestRetainedSource.Capture> capture) {
        final var binding = Coletas6908TestRetainedSource.binding(request, configuration);
        final var zone = ZoneId.of("America/Sao_Paulo");
        final var partition =
                new ExecutionPartitionKey(
                        "LOCAL_SHADOW",
                        Coletas6908TestRetainedRequest.SOURCE,
                        Coletas6908TestRetainedRequest.TENANT,
                        "coletas",
                        ExecutionMode.BACKFILL,
                        Coletas6908TestRetainedRequest.DAY.atStartOfDay(zone).toInstant(),
                        Coletas6908TestRetainedRequest.DAY
                                .plusDays(1)
                                .atStartOfDay(zone)
                                .toInstant());
        checkpoint.run();
        control.registerSource(
                new ControlPlaneSource(
                        Coletas6908TestRetainedRequest.SOURCE, "DATA_EXPORT", clock.instant()));
        control.startCycle(
                new ControlPlaneCycle(request.cycleId, request.fingerprint, clock.instant()));
        control.startExecution(
                new ControlPlaneStart(
                        request.executionId,
                        request.cycleId,
                        partition,
                        "INTERVAL",
                        binding.contractFingerprint(),
                        binding.configurationFingerprint(),
                        "test-retained-" + request.executionId,
                        Optional.empty(),
                        Duration.ofSeconds(60),
                        clock.instant()));
        control.transition(
                new ControlPlaneTransition(
                        request.executionId,
                        ExecutionState.PLANNED,
                        ExecutionState.EXTRACTING,
                        "TEST_CAPTURE_STARTED",
                        clock.instant()));
        checkpoint.run();
        final var captured = capture.get();
        checkpoint.run();
        final int rows = captured.records().size();
        if (rows < 1
                || rows > Coletas6908TestRetainedRequest.MAXIMUM_ROWS
                || captured.distinctRoots() != 1
                || captured.records().stream()
                        .anyMatch(row -> row.quarantineReasonCode() != null)) {
            throw new IllegalStateException("TEST_CAPTURE_NOT_RETAINABLE");
        }
        // The existing data port does not expose byte count; preserve its established
        // zero=unmeasured convention.
        control.recordPage(
                new ControlPlanePage(
                        request.executionId, 1, 1, 1, rows, 1, 0, false, captured.observedAt()));
        for (int offset = 0, batch = 1;
                offset < rows;
                offset += ColetaStageRecord.MAXIMUM_PAGE_SIZE, batch++) {
            checkpoint.run();
            staging.stage(
                    new ColetaStageBatch(
                            request.executionId,
                            batch,
                            captured.records()
                                    .subList(
                                            offset,
                                            Math.min(
                                                    rows,
                                                    offset + ColetaStageRecord.MAXIMUM_PAGE_SIZE)),
                            captured.observedAt()),
                    () -> {
                        deadline.run();
                        return false;
                    });
        }
        checkpoint.run();
        control.recordCounts(
                new ControlPlaneCounts(
                        request.executionId,
                        "TEST_CAPTURE",
                        rows,
                        1,
                        rows - 1L,
                        1,
                        0,
                        0,
                        clock.instant()));
        control.transition(
                new ControlPlaneTransition(
                        request.executionId,
                        ExecutionState.EXTRACTING,
                        ExecutionState.CANCELLED,
                        "TEST_PAGE_LIMIT",
                        clock.instant()));
        checkpoint.run();
        return captured;
    }
}
