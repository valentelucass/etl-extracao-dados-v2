package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.ExtrairFretesDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ExtrairManifestosDataExport;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCycle;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneSource;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalCaptureSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.fretes.JdbcSqlServerFreteStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.manifestos.JdbcSqlServerManifestoStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.sombra.JdbcDataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * One-shot laboratory composition over existing extraction, audit, exact staging and control plane.
 */
public final class LocalRelationalRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final JdbcRelationalLaboratory laboratory;
    private final Clock technicalClock;
    private final Clock logicalClock;
    private final UUID run;
    private final RelationalLaboratoryPolicy policy;

    public LocalRelationalRuntime(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final RelationalLaboratoryPolicy policy,
            final Clock logicalClock,
            final Clock technicalClock) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
        this.policy = Objects.requireNonNull(policy);
        this.logicalClock = Objects.requireNonNull(logicalClock);
        this.technicalClock = Objects.requireNonNull(technicalClock);
        laboratory = new JdbcRelationalLaboratory(session, logicalClock);
    }

    public Capture capture(
            final DataExportTemplate template,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final RelationalCaptureSource source,
            final CancellationToken cancellation)
            throws SQLException {
        return capture(
                UUID.randomUUID(),
                template,
                date,
                mode,
                replayOf,
                source,
                cancellation,
                LaboratoryCaptureWindow.day(
                        date,
                        ZoneOffset.UTC,
                        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy.FULL));
    }

    public Capture capture(
            final UUID execution,
            final DataExportTemplate template,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final RelationalCaptureSource source,
            final CancellationToken cancellation,
            final LaboratoryCaptureWindow window)
            throws SQLException {
        Objects.requireNonNull(execution);
        if (!window.dates().startInclusive().equals(date)
                || !window.dates().endInclusive().equals(date)) {
            throw new IllegalArgumentException("REL_LAB_SINGLE_SOURCE_DAY_REQUIRED");
        }
        policy.validateDate(date);
        policy.validateDate(window.dates().startInclusive());
        policy.validateDate(window.dates().endInclusive());
        if (mode == ExecutionMode.SWEEP
                || mode == null
                || (mode == ExecutionMode.REPLAY) != (replayOf != null)) {
            throw new IllegalArgumentException("REL_LAB_MODE_DENIED");
        }
        cancellation.throwIfCancellationRequested();
        if (!policy.equals(laboratory.policy(run))) {
            throw new IllegalArgumentException("REL_LAB_PERSISTED_POLICY_MISMATCH");
        }
        final var sourceScope = laboratory.scope(run);
        final var release = source.contractRelease(template);
        final var configuration =
                new ImmutableFingerprint(JdbcRelationalLaboratory.VERSION, policy.fingerprint());
        final var compatibility =
                ContractCompatibilityPolicy.create(
                        "synthetic-relational-policy-v1", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(
                        execution,
                        release,
                        compatibility,
                        configuration,
                        ContractObservationLimits.runtimeDefaults());
        final var start =
                new ControlPlaneStart(
                        execution,
                        UUID.randomUUID(),
                        new ExecutionPartitionKey(
                                "LOCAL_SHADOW",
                                sourceScope.source(),
                                sourceScope.tenant(),
                                entity(template),
                                mode,
                                window.start(),
                                window.endExclusive()),
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
                                sourceScope.source(), "DATA_EXPORT", technicalClock.instant()));
                control.startCycle(
                        new ControlPlaneCycle(
                                start.cycleId(), configuration, technicalClock.instant()));
                control.startExecution(start);
                final var streamer =
                        new DataExportPageStreamer(
                                source.gateway(template, guard, release, configuration),
                                new JdbcDataExportExtractionAudit(session),
                                logicalClock);
                final var request =
                        new DataExportPageRequest(
                                template,
                                window.dates(),
                                Optional.empty(),
                                1,
                                policy.pageSize(),
                                template.defaultOrderBy());
                final var limits =
                        new DataExportExtractionLimits(
                                policy.maximumPages(), policy.maximumRows(), policy.pageSize());
                switch (template) {
                    case MANIFESTOS ->
                            new ExtrairManifestosDataExport(
                                            streamer,
                                            new ManifestoDataExportRecordMapper(),
                                            batch -> {
                                                source.batchStarted(batch.size());
                                                new JdbcSqlServerManifestoStagingGateway(session)
                                                        .stage(batch, cancellation);
                                                source.batchStaged(batch.size());
                                            })
                                    .execute(guard, request, limits, cancellation);
                    case COLETAS ->
                            new ExtrairColetasDataExport(
                                            streamer,
                                            new ColetaDataExportRecordMapper(),
                                            batch -> {
                                                source.batchStarted(batch.size());
                                                new JdbcSqlServerColetaStagingGateway(session, true)
                                                        .stage(batch, cancellation);
                                                source.batchStaged(batch.size());
                                            })
                                    .execute(guard, request, limits, cancellation);
                    case FRETES ->
                            new ExtrairFretesDataExport(
                                            streamer,
                                            new FreteDataExportRecordMapper(),
                                            batch -> {
                                                source.batchStarted(batch.size());
                                                new JdbcSqlServerFreteStagingGateway(session)
                                                        .stage(batch, cancellation);
                                                source.batchStaged(batch.size());
                                            })
                                    .execute(guard, request, limits, cancellation);
                    default -> throw new IllegalArgumentException("REL_LAB_ENTITY_DENIED");
                }
                final var receipt =
                        laboratory.capture(run, execution, UUID.randomUUID(), cancellation);
                // The operational control plane explicitly reports an unpromoted laboratory result.
                control.transition(
                        new ControlPlaneTransition(
                                execution,
                                ExecutionState.STAGED,
                                ExecutionState.DEGRADED,
                                "REL_LAB_CAPTURE_ONLY",
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

    private static String entity(final DataExportTemplate template) {
        return switch (template) {
            case MANIFESTOS -> "manifestos";
            case COLETAS -> "coletas";
            case FRETES -> "fretes";
            default -> throw new IllegalArgumentException("REL_LAB_ENTITY_DENIED");
        };
    }

    public JdbcRelationalLaboratory laboratory() {
        return laboratory;
    }

    public record Capture(
            UUID executionId,
            JdbcRelationalLaboratory.CaptureReceipt receipt,
            RelationalCaptureSource.Metrics metrics) {}
}
