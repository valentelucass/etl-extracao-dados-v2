package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.contasapagar.aplicacao.ExtrairContaPagarDataExport;
import br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao.ExtrairFaturaClienteDataExport;
import br.com.esl.etl.v2.modulos.inventario.aplicacao.ExtrairInventarioDataExport;
import br.com.esl.etl.v2.modulos.sinistros.aplicacao.ExtrairSinistroDataExport;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSemanticsFingerprint;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreshness;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionCaptureSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionStaging;
import br.com.esl.etl.v2.plataforma.persistencia.sombra.JdbcDataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Four typed use cases share the existing streamer, audit, control plane and rollback session. */
public final class LocalExpansionRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final ExpansionPolicy policy;
    private final Clock logicalClock;
    private final Clock technicalClock;

    public LocalExpansionRuntime(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final ExpansionPolicy policy,
            final Clock logicalClock,
            final Clock technicalClock) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.policy = Objects.requireNonNull(policy);
        this.logicalClock = Objects.requireNonNull(logicalClock);
        this.technicalClock = Objects.requireNonNull(technicalClock);
    }

    public Capture capture(
            final DataExportTemplate template,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final ExpansionCaptureSource source,
            final CancellationToken cancellation)
            throws SQLException {
        return capture(UUID.randomUUID(), template, date, mode, replayOf, source, cancellation);
    }

    public Capture capture(
            final UUID execution,
            final DataExportTemplate template,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final ExpansionCaptureSource source,
            final CancellationToken cancellation)
            throws SQLException {
        policy.validate(date);
        source.validate(template, date, policy);
        cancellation.throwIfCancellationRequested();
        if (!template.syntheticOccurrenceCapture()
                || mode == null
                || mode == ExecutionMode.SWEEP
                || (mode == ExecutionMode.REPLAY) != (replayOf != null)) {
            throw new IllegalArgumentException("EXP_CAPTURE_SCOPE");
        }
        final var laboratory = new JdbcExpansionLaboratory(session, logicalClock);
        if (!policy.equals(laboratory.policy(run))) {
            throw new IllegalArgumentException("EXP_PERSISTED_POLICY_MISMATCH");
        }
        final var release = source.contract(template);
        final var fingerprint =
                ContractSemanticsFingerprint.create(
                        JdbcExpansionLaboratory.VERSION, policy.toString());
        final var compatibility =
                ContractCompatibilityPolicy.create(
                        "expansion-policy-v1", release.contractFingerprint(), List.of());
        Objects.requireNonNull(execution);
        final var binding =
                ContractExecutionBinding.create(
                        execution,
                        release,
                        compatibility,
                        fingerprint,
                        ContractObservationLimits.runtimeDefaults());
        final var start =
                new ControlPlaneStart(
                        execution,
                        UUID.randomUUID(),
                        new ExecutionPartitionKey(
                                "LOCAL_SHADOW",
                                JdbcExpansionLaboratory.SOURCE,
                                JdbcExpansionLaboratory.TENANT,
                                JdbcExpansionLaboratory.vertical(template),
                                mode,
                                date.atStartOfDay(ExpansionFreshness.ZONE).toInstant(),
                                date.plusDays(1).atStartOfDay(ExpansionFreshness.ZONE).toInstant()),
                        "DATA_EXPORT_RESTART_FROM_BEGINNING",
                        release.contractFingerprint(),
                        binding.configurationFingerprint(),
                        execution.toString(),
                        Optional.ofNullable(replayOf),
                        Duration.ofMinutes(5),
                        technicalClock.instant());
        final var guard =
                new ContractRunGuard(binding, release, compatibility, start, ignored -> {});
        try (var transaction = session.getConnection()) {
            final var savepoint = transaction.setSavepoint();
            try {
                final var control = new JdbcSqlServerControlPlane(session);
                control.registerSource(
                        new ControlPlaneSource(
                                JdbcExpansionLaboratory.SOURCE,
                                "DATA_EXPORT",
                                technicalClock.instant()));
                control.startCycle(
                        new ControlPlaneCycle(
                                start.cycleId(), fingerprint, technicalClock.instant()));
                control.startExecution(start);
                laboratory.beginCapture(
                        run,
                        execution,
                        template,
                        date,
                        mode,
                        replayOf,
                        release.contractFingerprint().sha256());
                final var streamer =
                        new DataExportPageStreamer(
                                source.gateway(template, guard, release, fingerprint),
                                new JdbcDataExportExtractionAudit(session),
                                logicalClock);
                final var request =
                        new DataExportPageRequest(
                                template,
                                new BusinessDateRange(date, date),
                                Optional.empty(),
                                1,
                                policy.pageSize(),
                                template.defaultOrderBy());
                final var limits =
                        new DataExportExtractionLimits(
                                policy.maximumPages(), policy.maximumRows(), policy.pageSize());
                final var staging = new JdbcExpansionStaging(session, run);
                switch (template) {
                    case CONTAS_A_PAGAR ->
                            new ExtrairContaPagarDataExport(
                                            streamer,
                                            (id, n, rows, at, token) ->
                                                    stage(source, staging, id, n, rows, at, token))
                                    .execute(guard, request, limits, cancellation);
                    case FATURAS_POR_CLIENTE ->
                            new ExtrairFaturaClienteDataExport(
                                            streamer,
                                            (id, n, rows, at, token) ->
                                                    stage(source, staging, id, n, rows, at, token))
                                    .execute(guard, request, limits, cancellation);
                    case INVENTARIO ->
                            new ExtrairInventarioDataExport(
                                            streamer,
                                            (id, n, rows, at, token) ->
                                                    stage(source, staging, id, n, rows, at, token))
                                    .execute(guard, request, limits, cancellation);
                    case SINISTROS ->
                            new ExtrairSinistroDataExport(
                                            streamer,
                                            (id, n, rows, at, token) ->
                                                    stage(source, staging, id, n, rows, at, token))
                                    .execute(guard, request, limits, cancellation);
                    default -> throw new IllegalArgumentException("EXP_TEMPLATE_REQUIRED");
                }
                cancellation.throwIfCancellationRequested();
                laboratory.seal(run, execution);
                control.transition(
                        new ControlPlaneTransition(
                                execution,
                                ExecutionState.EXTRACTING,
                                ExecutionState.EXTRACTED,
                                "EXTRACTION_OK",
                                technicalClock.instant()));
                control.transition(
                        new ControlPlaneTransition(
                                execution,
                                ExecutionState.EXTRACTED,
                                ExecutionState.STAGED,
                                "STAGING_OK",
                                technicalClock.instant()));
                final var receipt = laboratory.apply(run, execution);
                control.transition(
                        new ControlPlaneTransition(
                                execution,
                                ExecutionState.STAGED,
                                ExecutionState.DEGRADED,
                                "EXP_LAB_CAPTURE_ONLY",
                                technicalClock.instant()));
                return new Capture(execution, receipt, source.metrics());
            } catch (final SQLException | RuntimeException failure) {
                guard.invalidateEvidence();
                try {
                    transaction.rollback(savepoint);
                } catch (final SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                    session.rollback();
                }
                throw failure;
            } finally {
                source.captureClosed();
            }
        }
    }

    private static void stage(
            final ExpansionCaptureSource source,
            final JdbcExpansionStaging staging,
            final UUID execution,
            final int number,
            final Iterable<? extends br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured<?>>
                    rows,
            final java.time.Instant at,
            final CancellationToken token) {
        final var batch =
                new java.util.ArrayList<
                        br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured<?>>();
        for (final var row : rows) {
            if (batch.size() == 100) {
                throw new IllegalArgumentException("EXP_BATCH_BOUND");
            }
            batch.add(row);
        }
        source.batchStarted(batch.size());
        staging.stage(execution, number, batch, at, token);
        source.batchStaged(batch.size());
    }

    public record Capture(
            UUID executionId,
            JdbcExpansionLaboratory.ApplyReceipt receipt,
            ExpansionCaptureSource.Metrics metrics) {}
}
