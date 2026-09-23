package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageTargetKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticUsersCaptureSource;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionResult;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticDimensions;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuality;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerRuntimeRecovery;
import br.com.esl.etl.v2.plataforma.persistencia.observabilidade.JdbcSqlServerObservabilityGateway;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.persistencia.usuarios.JdbcSqlServerUsuarioPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.usuarios.JdbcSqlServerUsuarioStagingGateway;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.net.URI;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Uses the existing users dispatcher, strict parser, staging, DQ and current/history publication.
 */
public final class LocalAnalyticUsersRuntime {
    private final ColetaTemporalLaboratorySession session;
    private final Clock clock;
    private final AnalyticScenarioObserver observer;
    private final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope;

    public LocalAnalyticUsersRuntime(
            final ColetaTemporalLaboratorySession session, final Clock clock) {
        this(session, clock, AnalyticScenarioObserver.NONE);
    }

    public LocalAnalyticUsersRuntime(
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

    public LocalAnalyticUsersRuntime(
            final ColetaTemporalLaboratorySession session,
            final Clock clock,
            final AnalyticScenarioObserver observer,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope) {
        this.session = Objects.requireNonNull(session);
        this.clock = Objects.requireNonNull(clock);
        this.observer = Objects.requireNonNull(observer);
        this.scope = Objects.requireNonNull(scope);
    }

    /**
     * Snapshot observations have no source time filter; cycle labels do not change that contract.
     */
    public static ExecutionMode observationMode(final ExecutionMode cycleMode) {
        return switch (Objects.requireNonNull(cycleMode)) {
            case BOOTSTRAP, INCREMENTAL, BACKFILL -> ExecutionMode.BACKFILL;
            case REPLAY -> ExecutionMode.REPLAY;
            default -> throw new IllegalArgumentException("ANA_USERS_CYCLE_MODE");
        };
    }

    public StagingPublicationResult capture(
            final UUID run,
            final UUID execution,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final AnalyticUsersCaptureSource source,
            final CancellationToken cancellation)
            throws Exception {
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try {
                final var result =
                        captureWithin(run, execution, date, mode, replayOf, source, cancellation);
                cancellation.throwIfCancellationRequested();
                return result;
            } catch (final Exception failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } finally {
            observer.captureClosed();
        }
    }

    private StagingPublicationResult captureWithin(
            final UUID run,
            final UUID execution,
            final LocalDate date,
            final ExecutionMode mode,
            final UUID replayOf,
            final AnalyticUsersCaptureSource source,
            final CancellationToken cancellation)
            throws Exception {
        if ((mode != ExecutionMode.BACKFILL && mode != ExecutionMode.REPLAY)
                || (mode == ExecutionMode.REPLAY) != (replayOf != null)) {
            throw new IllegalArgumentException("ANA_USERS_OBSERVATION_MODE");
        }
        cancellation.throwIfCancellationRequested();
        final var quality =
                new JdbcAnalyticQuality(session)
                        .register(run, JdbcAnalyticQuality.Entity.USUARIOS, mode, scope);
        final var configuration = configuration(clock, scope);
        final var document =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("protocol", "GRAPHQL")
                        .put("operation", "USERS_SNAPSHOT")
                        .put("invocationId", UUID.randomUUID().toString())
                        .put("executionId", execution.toString())
                        .put("cycleId", UUID.randomUUID().toString())
                        .put("mode", mode.name())
                        .put("start", date.atStartOfDay(ZoneId.of("UTC")).toInstant().toString())
                        .put(
                                "endExclusive",
                                date.plusDays(1)
                                        .atStartOfDay(ZoneId.of("UTC"))
                                        .toInstant()
                                        .toString())
                        .put("replayOf", replayOf == null ? "" : replayOf.toString())
                        .put("idempotencyKey", execution.toString())
                        .put("leaseSeconds", "60")
                        .put("pageSize", "20")
                        .put("maximumPages", "256")
                        .put("maximumNodes", "5120")
                        .put("qualityVersion", quality.version())
                        .put("qualityFingerprint", quality.sha256())
                        .put("compatibilityVersion", "analytic-users-strict-v1");
        final var request = RuntimeUsersRequest.read(configuration, document);
        final RuntimeExecutionPlanItem[] selected = new RuntimeExecutionPlanItem[1];
        request.plan().forEach(item -> selected[0] = item);
        final var item = selected[0];
        final var start =
                new ControlPlaneStart(
                        execution,
                        request.plan().cycleId(),
                        item.partition(),
                        item.request().windowStrategy().name(),
                        item.definition().contract(),
                        item.definition().configuration(),
                        item.request().idempotencyKey(),
                        item.request().replayOfExecutionId(),
                        item.definition().leaseDuration(),
                        clock.instant());
        final var guard =
                new ContractRunGuard(
                        request.binding(),
                        request.release(),
                        request.compatibility(),
                        start,
                        ignored -> {});
        final var observation =
                new GraphQlContractObservationConfiguration(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(
                                request.release(), request.compatibility()),
                        request.binding().runtimeConfigurationFingerprint());
        final var handler =
                new LocalUsuariosRuntime(
                        item.partition(),
                        guard,
                        request.limits(),
                        (bound, token) -> source.observed(observer).bind(observation, bound, token),
                        batch -> {
                            observer.batchStarted(batch.size());
                            new JdbcSqlServerUsuarioStagingGateway(session).stage(batch);
                            observer.batchStaged(batch.size());
                        },
                        new JdbcSqlServerUsuarioPromotionGateway(session),
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
                                        session, Duration.ofSeconds(10), Duration.ofSeconds(20)),
                                new RuntimeDispatchBinding(
                                        item.definition().id(),
                                        active -> {
                                            try {
                                                handler.execute(active);
                                            } catch (final RuntimeException error) {
                                                failure[0] = error;
                                                throw error;
                                            }
                                        }))
                        .dispatch(request.plan(), cancellation)
                        .result(item.definition().id());
        if (result.status() != RuntimeExecutionResult.Status.PUBLISHED) {
            throw new SQLException(
                    "ANA_USERS_CAPTURE_" + result.status(),
                    result.recovery()
                            .flatMap(
                                    br.com.esl.etl.v2.plataforma.orquestracao
                                                    .RuntimeExecutionSession
                                            ::failureCause)
                            .orElse(failure[0]));
        }
        new JdbcAnalyticDimensions(session)
                .attachExecution(run, AnalyticDimensionBinding.Entity.USUARIO, execution);
        return result.publication().orElseThrow();
    }

    private static RuntimeConfiguration configuration(
            final Clock clock,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope) {
        final var resilience =
                new EslResiliencePolicy(
                        Duration.ZERO,
                        1,
                        256,
                        256,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(1),
                        0,
                        3,
                        Duration.ofSeconds(10));
        return new RuntimeConfiguration(
                RuntimeEnvironment.LOCAL_SHADOW,
                ZoneId.of("America/Sao_Paulo"),
                clock,
                Optional.empty(),
                Optional.of(
                        new GraphQlSourceConfiguration(
                                scope.source(),
                                scope.tenant(),
                                new GraphQlClientSettings(
                                        URI.create("https://synthetic.invalid/graphql"),
                                        Duration.ofSeconds(10),
                                        new GraphQlRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                                        65536),
                                resilience)),
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;"
                                + "encrypt=true;trustServerCertificate=true",
                        null));
    }
}
