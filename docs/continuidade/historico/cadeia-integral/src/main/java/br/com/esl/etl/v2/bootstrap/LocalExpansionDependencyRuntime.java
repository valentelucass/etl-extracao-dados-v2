package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.ExtrairFretesDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.ExtrairLocalizacaoCargasDataExport;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
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
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionDependencies;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.fretes.JdbcSqlServerFreteStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.localizacaocargas.JdbcSqlServerLocalizacaoCargaStagingGateway;
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
public final class LocalExpansionDependencyRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;
    private final ExpansionPolicy policy;
    private final Clock logicalClock;
    private final Clock technicalClock;

    public LocalExpansionDependencyRuntime(
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
            final ExpansionDependencySource source,
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
            final ExpansionDependencySource source,
            final CancellationToken cancellation)
            throws SQLException {
        return capture(
                execution,
                template,
                date,
                mode,
                replayOf,
                source,
                cancellation,
                LaboratoryCaptureWindow.day(
                        date,
                        ExpansionFreshness.ZONE,
                        br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy.FULL));
    }

    public Capture capture(
            final UUID execution,
            final DataExportTemplate template,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final ExpansionDependencySource source,
            final CancellationToken cancellation,
            final LaboratoryCaptureWindow window)
            throws SQLException {
        policy.validate(date);
        policy.validate(window.dates().startInclusive());
        policy.validate(window.dates().endInclusive());
        cancellation.throwIfCancellationRequested();
        if ((template != DataExportTemplate.FRETES
                        && template != DataExportTemplate.LOCALIZACAO_CARGAS)
                || mode == null
                || mode == ExecutionMode.SWEEP
                || (mode == ExecutionMode.REPLAY) != (replayOf != null)) {
            throw new IllegalArgumentException("EXP_CAPTURE_SCOPE");
        }
        final var laboratory = new JdbcExpansionLaboratory(session, logicalClock);
        if (!policy.equals(laboratory.policy(run))) {
            throw new IllegalArgumentException("EXP_PERSISTED_POLICY_MISMATCH");
        }
        final var release = source.contractRelease(template);
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
                                template == DataExportTemplate.FRETES
                                        ? "fretes"
                                        : "localizacao_cargas",
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
                                JdbcExpansionLaboratory.SOURCE,
                                "DATA_EXPORT",
                                technicalClock.instant()));
                control.startCycle(
                        new ControlPlaneCycle(
                                start.cycleId(), fingerprint, technicalClock.instant()));
                control.startExecution(start);
                final var dependencies = new JdbcExpansionDependencies(session, logicalClock);
                dependencies.begin(
                        run, execution, template, date, release.contractFingerprint().sha256());
                final var streamer =
                        new DataExportPageStreamer(
                                source.gateway(template, guard, release, fingerprint),
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
                    case FRETES ->
                            new ExtrairFretesDataExport(
                                            streamer,
                                            new FreteDataExportRecordMapper(),
                                            batch -> {
                                                source.batchStarted(batch.size());
                                                new JdbcSqlServerFreteStagingGateway(session)
                                                        .stage(batch, cancellation);
                                                dependencies.exactTimes(batch);
                                                dependencies.financialTerms(
                                                        batch, source::financialTerms);
                                                source.batchStaged(batch.size());
                                            })
                                    .execute(guard, request, limits, cancellation);
                    case LOCALIZACAO_CARGAS ->
                            new ExtrairLocalizacaoCargasDataExport(
                                            streamer,
                                            new LocalizacaoCargaDataExportRecordMapper(),
                                            batch -> {
                                                source.batchStarted(batch.size());
                                                new JdbcSqlServerLocalizacaoCargaStagingGateway(
                                                                session)
                                                        .stage(batch, cancellation);
                                                dependencies.exactTimes(batch);
                                                source.batchStaged(batch.size());
                                            })
                                    .execute(guard, request, limits, cancellation);
                    default -> throw new IllegalArgumentException("EXP_DEP_TEMPLATE_REQUIRED");
                }
                cancellation.throwIfCancellationRequested();
                final var receipt = dependencies.sealAndApply(run, execution);
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

    public record Capture(
            UUID executionId,
            JdbcExpansionDependencies.Receipt receipt,
            ExpansionDependencySource.Metrics metrics) {}
}
