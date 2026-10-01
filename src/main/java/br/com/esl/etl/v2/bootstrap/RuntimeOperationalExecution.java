package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.EnvironmentSecretProvider;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.JdbcSqlServerControlPlane;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttemptObserver;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRuntimeWorkload;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatchBinding;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeDispatcher;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlanItem;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryExpectation;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoveryRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeRecoverySnapshot;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadHandler;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerRuntimeRecovery;
import br.com.esl.etl.v2.plataforma.persistencia.fretes.JdbcSqlServerFretePromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.fretes.JdbcSqlServerFreteStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.observabilidade.JdbcSqlServerObservabilityGateway;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.time.Duration;

/** One-shot concrete handlers. Called exclusively after durable authorization consumption. */
final class RuntimeOperationalExecution {
    private RuntimeOperationalExecution() {}

    static RuntimeExitCategory execute(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeAction action) {
        return execute(configuration, request, action, ignored -> {});
    }

    static RuntimeExitCategory execute(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeAction action,
            final java.util.function.Consumer<String> diagnostic) {
        return execute(configuration, request, action, diagnostic, null);
    }

    static RuntimeExitCategory execute(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeAction action,
            final java.util.function.Consumer<String> diagnostic,
            final java.io.InputStream controls) {
        final var attempts =
                new RuntimeHttpAttempts(
                        request.users == null
                                ? configuration
                                        .dataExport()
                                        .orElseThrow()
                                        .resiliencePolicy()
                                        .maxRequestsPerWorkload()
                                : configuration
                                        .graphQl()
                                        .orElseThrow()
                                        .resiliencePolicy()
                                        .maxRequestsPerWorkload());
        try {
            return execute(
                    configuration,
                    request,
                    action,
                    new BoundedRuntimeDataSource(configuration.shadowStorage()),
                    observedSourceGateways(attempts),
                    diagnostic,
                    controls);
        } finally {
            diagnostic.accept(sourceAttemptsSummary(request, attempts));
        }
    }

    static String sourceAttemptsSummary(
            final RuntimeOperationalRequest request, final RuntimeHttpAttempts attempts) {
        return request.users == null ? attempts.summary() : attempts.graphQlSummary();
    }

    static SourceGateways observedSourceGateways(final RuntimeHttpAttempts attempts) {
        return new SourceGateways() {
            @Override
            public br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle create(
                    final RuntimeConfiguration config,
                    final RuntimeOperationalRequest frozen,
                    final CancellationSignal cancellation,
                    final DataExportContractObservationConfiguration observation) {
                return sourceGateways(config, frozen, cancellation, observation, attempts);
            }

            @Override
            public br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlHttpAttemptObserver
                    graphQlAttempts() {
                return attempts;
            }
        };
    }

    static RuntimeExitCategory execute(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeAction action,
            final javax.sql.DataSource dataSource,
            final SourceGateways gateways) {
        return execute(configuration, request, action, dataSource, gateways, ignored -> {}, null);
    }

    static RuntimeExitCategory execute(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeAction action,
            final javax.sql.DataSource dataSource,
            final SourceGateways gateways,
            final java.util.function.Consumer<String> diagnostic,
            final java.io.InputStream controls) {
        return executeInternal(
                configuration, request, action, dataSource, gateways, diagnostic, controls, null);
    }

    static RuntimeExitCategory executePilot(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final javax.sql.DataSource dataSource,
            final SourceGateways gateways,
            final Coletas6908PilotExtraction extraction,
            final ColetaPromotionGateway promotion,
            final Runnable checkpoint) {
        return executeInternal(
                configuration,
                request,
                RuntimeAction.RUN,
                dataSource,
                gateways,
                ignored -> {},
                null,
                new PilotComponents(extraction, promotion, checkpoint));
    }

    private static RuntimeExitCategory executeInternal(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final RuntimeAction action,
            final javax.sql.DataSource dataSource,
            final SourceGateways gateways,
            final java.util.function.Consumer<String> diagnostic,
            final java.io.InputStream controls,
            final PilotComponents pilot) {
        final var recovery =
                new JdbcSqlServerRuntimeRecovery(
                        dataSource,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(20),
                        action == RuntimeAction.STATUS,
                        request.referenceReleaseId);
        final RuntimeExecutionPlanItem[] selected = new RuntimeExecutionPlanItem[1];
        request.plan.forEach(value -> selected[0] = value);
        final var item = selected[0];
        final var definition = item.definition();
        final var occurrence = item.request();
        final boolean scopedObservation = request.laboratory();
        if (scopedObservation) {
            // Required before composing the source; absent schema/grants never become success.
            diagnostic.accept(RuntimeSqlObservation.read(dataSource, occurrence.executionId()));
        }
        final var start =
                new ControlPlaneStart(
                        occurrence.executionId(),
                        request.plan.cycleId(),
                        item.partition(),
                        occurrence.windowStrategy().name(),
                        definition.contract(),
                        definition.configuration(),
                        occurrence.idempotencyKey(),
                        occurrence.replayOfExecutionId(),
                        definition.leaseDuration(),
                        configuration.clock().instant());
        try (var control = new RuntimeCancellationControl(new CancellationSignal(), controls)) {
            final var cancellation = control.signal();
            final var durableRequest =
                    new RuntimeRecoveryRequest(
                            start, request.plan.fingerprint(), request.binding, request.quality);
            if (pilot != null) {
                pilot.checkpoint().run();
            }
            final var snapshot = recovery.read(durableRequest, cancellation);
            if (pilot != null) {
                pilot.checkpoint().run();
            }
            diagnostic.accept(
                    "RUNTIME_OBSERVATION reason="
                            + snapshot.reason().name()
                            + " correlation="
                            + br.com.esl.etl.v2.plataforma.observabilidade.CorrelationReference
                                    .fromExecutionId(occurrence.executionId())
                                    .sha256());
            if (action == RuntimeAction.STATUS) {
                // Occurrence status uses the observer grants; plan reconciliation belongs to RUN.
                return snapshot.reason() == RuntimeRecoverySnapshot.Reason.PUBLISHED
                        ? RuntimeExitCategory.SUCCESS
                        : RuntimeExitCategory.DEGRADED;
            }
            if (request.temporal != null
                    && snapshot.reason() == RuntimeRecoverySnapshot.Reason.NOT_FOUND) {
                request.temporal.persistBeforeFirstAttempt(configuration, request.invocation);
            }
            // No source factory is composed for a known durable occurrence.
            final RuntimeWorkloadHandler handler =
                    snapshot.reason() == RuntimeRecoverySnapshot.Reason.NOT_FOUND
                            ? sourceHandler(
                                    configuration,
                                    request,
                                    start,
                                    cancellation,
                                    dataSource,
                                    gateways,
                                    pilot)
                            : session -> {
                                throw new IllegalStateException("RECOVERY_CANNOT_FETCH");
                            };
            final var dispatcher =
                    new RuntimeDispatcher(
                            pilot == null
                                    ? new JdbcSqlServerControlPlane(dataSource)
                                    : new Coletas6908PilotControlPlane(
                                            new JdbcSqlServerControlPlane(dataSource),
                                            pilot.checkpoint()),
                            configuration.clock(),
                            recovery,
                            new RuntimeDispatchBinding(definition.id(), handler));
            if (snapshot.reason() == RuntimeRecoverySnapshot.Reason.NOT_FOUND) {
                final var dispatch = dispatcher.dispatch(request.plan, cancellation);
                diagnostic.accept(
                        "RUNTIME_RESULT status=" + dispatch.result(definition.id()).status());
                final var result = dispatch.outcome().exitCategory();
                if (scopedObservation) {
                    diagnostic.accept(
                            RuntimeSqlObservation.read(dataSource, occurrence.executionId()));
                }
                if (request.temporal != null) {
                    request.temporal.reconcile(configuration, dataSource, diagnostic);
                }
                return result;
            }
            final var result =
                    dispatcher
                            .recover(
                                    request.plan,
                                    cancellation,
                                    new RuntimeRecoveryExpectation(
                                            definition.id(), request.binding, request.quality))
                            .dispatch()
                            .outcome()
                            .exitCategory();
            if (request.temporal != null) {
                request.temporal.reconcile(configuration, dataSource, diagnostic);
            }
            return result;
        }
    }

    private static RuntimeWorkloadHandler sourceHandler(
            final RuntimeConfiguration configuration,
            final RuntimeOperationalRequest request,
            final ControlPlaneStart start,
            final CancellationSignal cancellation,
            final javax.sql.DataSource dataSource,
            final SourceGateways gateways,
            final PilotComponents pilot) {
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var boundary =
                ContractResponsePathBoundary.forRuntime(request.release, request.compatibility);
        final var observability =
                new JdbcSqlServerObservabilityGateway(
                        dataSource, configuration.clock(), Duration.ofSeconds(10));
        final var alertSequence = new java.util.concurrent.atomic.AtomicInteger();
        final var guard =
                new ContractRunGuard(
                        request.binding,
                        request.release,
                        request.compatibility,
                        start,
                        alert ->
                                observability.raiseScoped(
                                        alert.executionId(),
                                        new br.com.esl.etl.v2.plataforma.observabilidade
                                                .OperationalAlert(
                                                br.com.esl.etl.v2.plataforma.observabilidade
                                                        .CorrelationReference.fromExecutionId(
                                                        alert.executionId()),
                                                alertSequence.incrementAndGet(),
                                                br.com.esl.etl.v2.plataforma.observabilidade
                                                        .AlertSeverity.WARNING,
                                                "CONTRACT_COMPATIBLE_DRIFT",
                                                "contract-owner",
                                                1,
                                                configuration
                                                        .clock()
                                                        .instant()
                                                        .truncatedTo(
                                                                java.time.temporal.ChronoUnit
                                                                        .MILLIS))));
        final var quality =
                new FailClosedDataQualityEngine(
                        new JdbcSqlServerObservabilityGateway(
                                dataSource, configuration.clock(), Duration.ofSeconds(10)));
        final RuntimeWorkloadHandler delegate;
        if (request.users != null) {
            final var observation =
                    new br.com.esl.etl.v2.plataforma.fonte.graphql
                            .GraphQlContractObservationConfiguration(
                            br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation
                                    .USERS_SNAPSHOT,
                            limits,
                            boundary,
                            br.com.esl.etl.v2.plataforma.configuracao
                                    .GraphQlRuntimeConfigurationFingerprint.from(
                                    configuration.graphQl().orElseThrow()));
            delegate =
                    new LocalUsuariosRuntime(
                            start.partition(),
                            guard,
                            request.users.limits(),
                            (boundGuard, token) ->
                                    gateways.createUsers(
                                            configuration, request, token, observation, boundGuard),
                            new br.com.esl.etl.v2.plataforma.persistencia.usuarios
                                    .JdbcSqlServerUsuarioStagingGateway(dataSource),
                            new br.com.esl.etl.v2.plataforma.persistencia.usuarios
                                    .JdbcSqlServerUsuarioPromotionGateway(dataSource),
                            quality,
                            request.quality);
        } else {
            final var source = configuration.dataExport().orElseThrow();
            final var observation =
                    DataExportContractObservationConfiguration.forRelease(
                            request.template,
                            request.release,
                            limits,
                            boundary,
                            DataExportRuntimeConfigurationFingerprint.from(source));
            final var bundle = gateways.create(configuration, request, cancellation, observation);
            final var input =
                    new DataExportRuntimeWorkload.Input(
                            start.partition(), guard, request.firstPage(), request.limits, bundle);
            delegate =
                    switch (request.template) {
                        case COLETAS ->
                                pilot == null
                                        ? LocalColetasFretesRuntime.coletas(
                                                input,
                                                new JdbcSqlServerColetaStagingGateway(dataSource),
                                                new JdbcSqlServerColetaPromotionGateway(dataSource),
                                                quality,
                                                request.quality)
                                        : new DataExportRuntimeWorkload(
                                                input,
                                                pilot.extraction(),
                                                pilot.promotion(),
                                                quality,
                                                request.quality);
                        case FRETES ->
                                LocalColetasFretesRuntime.fretes(
                                        input,
                                        new JdbcSqlServerFreteStagingGateway(dataSource),
                                        new JdbcSqlServerFretePromotionGateway(dataSource),
                                        quality,
                                        request.quality);
                        case MANIFESTOS ->
                                LocalFiveVerticalRuntime.manifestos(
                                        input,
                                        new br.com.esl.etl.v2.plataforma.persistencia.manifestos
                                                .JdbcSqlServerManifestoStagingGateway(dataSource),
                                        new br.com.esl.etl.v2.plataforma.persistencia.manifestos
                                                .JdbcSqlServerManifestoPromotionGateway(dataSource),
                                        quality,
                                        request.quality);
                        case COTACOES ->
                                LocalFiveVerticalRuntime.cotacoes(
                                        input,
                                        new br.com.esl.etl.v2.plataforma.persistencia.cotacoes
                                                .JdbcSqlServerCotacaoStagingGateway(dataSource),
                                        new br.com.esl.etl.v2.plataforma.persistencia.cotacoes
                                                .JdbcSqlServerCotacaoPromotionGateway(dataSource),
                                        request.referenceReleaseId,
                                        quality,
                                        request.quality);
                        case LOCALIZACAO_CARGAS ->
                                LocalFiveVerticalRuntime.localizacao(
                                        input,
                                        new br.com.esl.etl.v2.plataforma.persistencia
                                                .localizacaocargas
                                                .JdbcSqlServerLocalizacaoCargaStagingGateway(
                                                dataSource),
                                        new br.com.esl.etl.v2.plataforma.persistencia
                                                .localizacaocargas
                                                .JdbcSqlServerLocalizacaoCargaPromotionGateway(
                                                dataSource),
                                        quality,
                                        request.quality);
                        default ->
                                throw new IllegalArgumentException(
                                        "EXP_SEPARATE_LABORATORY_REQUIRED");
                    };
        }
        return withRequiredDriftAlert(
                session -> {
                    if (request.temporal != null) {
                        request.temporal.initializeIncrementalFrontier(
                                new JdbcSqlServerControlPlane(dataSource),
                                session.start().partition(),
                                configuration.clock().instant());
                    }
                    delegate.execute(session);
                },
                failure ->
                        observability.raiseScoped(
                                start.executionId(),
                                new br.com.esl.etl.v2.plataforma.observabilidade.OperationalAlert(
                                        br.com.esl.etl.v2.plataforma.observabilidade
                                                .CorrelationReference.fromExecutionId(
                                                start.executionId()),
                                        alertSequence.incrementAndGet(),
                                        br.com.esl.etl.v2.plataforma.observabilidade.AlertSeverity
                                                .CRITICAL,
                                        "CONTRACT_DRIFT_REJECTED",
                                        "contract-owner",
                                        1,
                                        configuration
                                                .clock()
                                                .instant()
                                                .truncatedTo(
                                                        java.time.temporal.ChronoUnit.MILLIS))));
    }

    private record PilotComponents(
            Coletas6908PilotExtraction extraction,
            ColetaPromotionGateway promotion,
            Runnable checkpoint) {
        private PilotComponents {
            java.util.Objects.requireNonNull(extraction);
            java.util.Objects.requireNonNull(promotion);
            java.util.Objects.requireNonNull(checkpoint);
        }
    }

    static RuntimeWorkloadHandler withRequiredDriftAlert(
            final RuntimeWorkloadHandler delegate,
            final java.util.function.Consumer<
                            br.com.esl.etl.v2.plataforma.contrato.ContractDriftException>
                    alert) {
        return session -> {
            try {
                delegate.execute(session);
            } catch (final br.com.esl.etl.v2.plataforma.contrato.ContractDriftException failure) {
                try {
                    alert.accept(failure);
                } catch (final RuntimeException sinkFailure) {
                    sinkFailure.addSuppressed(failure);
                    throw new IllegalStateException(
                            "MANDATORY_DRIFT_ALERT_UNCONFIRMED", sinkFailure);
                }
                throw failure;
            }
        };
    }

    private static br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle
            sourceGateways(
                    final RuntimeConfiguration configuration,
                    final RuntimeOperationalRequest request,
                    final CancellationSignal cancellation,
                    final DataExportContractObservationConfiguration observation,
                    final DataExportHttpAttemptObserver attempts) {
        final var source = configuration.dataExport().orElseThrow();
        final var factory =
                new DataExportHttpGatewayFactory(
                        source,
                        new EnvironmentSecretProvider(System.getenv()),
                        configuration.clock());
        final var governor =
                new EslRequestGovernor(
                        source.resiliencePolicy(),
                        MonotonicTicker.systemTicker(),
                        ResilienceSleeper.threadSleeper());
        return factory.forWorkload(
                governor.beginCycle(cancellation),
                RuntimeVertical.workload(request.template),
                attempts,
                observation);
    }

    @FunctionalInterface
    interface SourceGateways {
        br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle create(
                RuntimeConfiguration configuration,
                RuntimeOperationalRequest request,
                CancellationSignal cancellation,
                DataExportContractObservationConfiguration observation);

        default br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlHttpAttemptObserver
                graphQlAttempts() {
            return br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlHttpAttemptObserver.noop();
        }

        default br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlGateway createUsers(
                final RuntimeConfiguration configuration,
                final RuntimeOperationalRequest request,
                final br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken cancellation,
                final br.com.esl.etl.v2.plataforma.fonte.graphql
                                .GraphQlContractObservationConfiguration
                        observation,
                final ContractRunGuard guard) {
            final var source = configuration.graphQl().orElseThrow();
            final var governor =
                    new EslRequestGovernor(
                            source.resiliencePolicy(),
                            MonotonicTicker.systemTicker(),
                            ResilienceSleeper.threadSleeper());
            return new br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlHttpGatewayFactory(
                            source,
                            new EnvironmentSecretProvider(System.getenv()),
                            governor,
                            configuration.clock())
                    .forOperation(
                            governor.beginCycle(cancellation),
                            br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation
                                    .USERS_SNAPSHOT,
                            observation,
                            guard,
                            cancellation,
                            graphQlAttempts());
        }
    }
}
