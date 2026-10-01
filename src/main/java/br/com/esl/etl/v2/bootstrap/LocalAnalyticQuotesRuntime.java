package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesCaptureSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRuntimeWorkload;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionResult;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionSession;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuality;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuotePromotion;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteStaging;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerRuntimeRecovery;
import br.com.esl.etl.v2.plataforma.persistencia.observabilidade.JdbcSqlServerObservabilityGateway;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** One rollback-only capture through the existing dispatcher, quotation pipeline and DQ engine. */
public final class LocalAnalyticQuotesRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final Clock clock;
    private final AnalyticScenarioObserver observer;
    private final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope;

    public LocalAnalyticQuotesRuntime(
            final ColetaTemporalLaboratorySession session, final Clock clock) {
        this(session, clock, AnalyticScenarioObserver.NONE);
    }

    public LocalAnalyticQuotesRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock clock,
            final AnalyticScenarioObserver observer) {
        this(
                session,
                clock,
                observer,
                new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(
                        "LOCAL_V2", "LOCAL_V2"));
    }

    public LocalAnalyticQuotesRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock clock,
            final AnalyticScenarioObserver observer,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope) {
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
        this.observer = Objects.requireNonNull(observer);
        this.scope = Objects.requireNonNull(scope);
    }

    public JdbcAnalyticQuotePromotion.Receipt capture(
            final UUID run,
            final UUID execution,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final int revision,
            final long tariffRelease,
            final int pageSize,
            final AnalyticQuotesCaptureSource source,
            final CancellationToken cancellation)
            throws Exception {
        return capture(
                run,
                execution,
                date,
                mode,
                replayOf,
                revision,
                tariffRelease,
                pageSize,
                source,
                cancellation,
                LaboratoryCaptureWindow.day(date, ZoneOffset.UTC, RuntimeWindowStrategy.FULL));
    }

    public JdbcAnalyticQuotePromotion.Receipt capture(
            final UUID run,
            final UUID execution,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final int revision,
            final long tariffRelease,
            final int pageSize,
            final AnalyticQuotesCaptureSource source,
            final CancellationToken cancellation,
            final LaboratoryCaptureWindow window)
            throws Exception {
        if ((mode != ExecutionMode.BACKFILL
                        && mode != ExecutionMode.REPLAY
                        && mode != ExecutionMode.BOOTSTRAP
                        && mode != ExecutionMode.INCREMENTAL)
                || (mode == ExecutionMode.REPLAY) != (replayOf != null)
                || pageSize < 1
                || pageSize > 16
                || tariffRelease < 1) {
            throw new IllegalArgumentException("ANA_QUOTE_CAPTURE_SCOPE");
        }
        cancellation.throwIfCancellationRequested();
        final var release = source.contractRelease();
        final var configuration =
                new ImmutableFingerprint(
                        "analytic-quotes-runtime-v1",
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        (run
                                                                        + ":"
                                                                        + revision
                                                                        + ":"
                                                                        + tariffRelease
                                                                        + ":"
                                                                        + pageSize)
                                                                .getBytes(
                                                                        StandardCharsets.UTF_8))));
        final var compatibility =
                ContractCompatibilityPolicy.create(
                        "analytic-quotes-strict-v1", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(execution, release, compatibility, configuration);
        final var id = new RuntimeWorkloadId("cotacoes");
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        scope.source(),
                        scope.tenant(),
                        "cotacoes",
                        binding.contractFingerprint(),
                        binding.configurationFingerprint(),
                        Duration.ofMinutes(5));
        final var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        UUID.randomUUID(),
                                        "LOCAL_SHADOW",
                                        configuration,
                                        clock.instant(),
                                        new RuntimeExecutionRequest(
                                                execution,
                                                id,
                                                mode,
                                                window.strategy(),
                                                window.start(),
                                                window.endExclusive(),
                                                execution.toString(),
                                                Optional.ofNullable(replayOf))));
        final RuntimeExecutionPlanItem[] selected = new RuntimeExecutionPlanItem[1];
        plan.forEach(item -> selected[0] = item);
        final var item = selected[0];
        final var start =
                new ControlPlaneStart(
                        execution,
                        plan.cycleId(),
                        item.partition(),
                        item.request().windowStrategy().name(),
                        definition.contract(),
                        definition.configuration(),
                        execution.toString(),
                        Optional.ofNullable(replayOf),
                        definition.leaseDuration(),
                        clock.instant());
        final var guard =
                new ContractRunGuard(binding, release, compatibility, start, ignored -> {});
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try {
                final var quality =
                        new JdbcAnalyticQuality(session)
                                .register(run, JdbcAnalyticQuality.Entity.COTACOES, mode, scope);
                final var staging = new JdbcAnalyticQuoteStaging(session, run);
                final var promotion = new JdbcAnalyticQuotePromotion(session, run, revision);
                final var input =
                        new DataExportRuntimeWorkload.Input(
                                item.partition(),
                                guard,
                                new DataExportPageRequest(
                                        DataExportTemplate.COTACOES,
                                        window.dates(),
                                        Optional.empty(),
                                        1,
                                        pageSize,
                                        DataExportTemplate.COTACOES.defaultOrderBy()),
                                new DataExportExtractionLimits(10000, 163840, pageSize),
                                source.observed(observer).bundle(guard, configuration));
                final var handler =
                        LocalFiveVerticalRuntime.cotacoes(
                                input,
                                batch -> {
                                    observer.batchStarted(batch.size());
                                    staging.stage(batch);
                                    observer.batchStaged(batch.size());
                                },
                                promotion,
                                tariffRelease,
                                new FailClosedDataQualityEngine(
                                        new JdbcSqlServerObservabilityGateway(
                                                session, clock, Duration.ofSeconds(10))),
                                quality);
                final RuntimeException[] failure = new RuntimeException[1];
                final var result =
                        new RuntimeDispatcher(
                                        new JdbcSqlServerControlPlane(session),
                                        clock,
                                        new JdbcSqlServerRuntimeRecovery(
                                                session,
                                                Duration.ofSeconds(10),
                                                Duration.ofSeconds(20),
                                                false,
                                                tariffRelease),
                                        new RuntimeDispatchBinding(
                                                id,
                                                active -> {
                                                    try {
                                                        staging.begin(
                                                                execution,
                                                                release.contractFingerprint()
                                                                        .sha256());
                                                        promotion.bindReference(
                                                                execution, tariffRelease);
                                                        handler.execute(active);
                                                    } catch (final SQLException error) {
                                                        final var captured =
                                                                new IllegalStateException(
                                                                        "ANA_QUOTE_BEGIN", error);
                                                        failure[0] = captured;
                                                        throw captured;
                                                    } catch (final RuntimeException error) {
                                                        failure[0] = error;
                                                        throw error;
                                                    }
                                                }))
                                .dispatch(plan, cancellation)
                                .result(id);
                if (result.status() != RuntimeExecutionResult.Status.PUBLISHED) {
                    throw new SQLException(
                            "ANA_QUOTE_CAPTURE_" + result.status(),
                            result.recovery()
                                    .flatMap(RuntimeExecutionSession::failureCause)
                                    .orElse(failure[0]));
                }
                cancellation.throwIfCancellationRequested();
                final var receipt = promotion.prepare(execution);
                new JdbcAnalyticDimensions(session)
                        .attachExecution(run, AnalyticDimensionBinding.Entity.COT, execution);
                return receipt;
            } catch (final Exception failure) {
                guard.invalidateEvidence();
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            } finally {
                observer.captureClosed();
            }
        }
    }
}
