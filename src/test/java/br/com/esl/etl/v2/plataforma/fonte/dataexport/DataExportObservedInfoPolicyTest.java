package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Real factory/adapters and in-memory HttpClient only. No network/socket is created. */
class DataExportObservedInfoPolicyTest {
    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void rejectsRealRetryTwoBeforeInfoEvenWithMatchingDerivedBinding(
            final DataExportTemplate template) {
        final var fixture =
                new FactoryFixture(template, DataExportTransport.GET_WITH_BODY, 2, true);
        assertEquals(
                fixture.request.profile().runtimeFingerprint(fixture.base()),
                fixture.guard.sourceSelection().runtimeConfigurationFingerprint());
        assertRejectedBeforeInfo(fixture.bundle, fixture.request, fixture.guard, fixture.client);
        assertEquals(0, fixture.cycle.sourceRequests());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTransport.class,
            names = {"GET_WITH_QUERY", "POST_JSON"})
    void rejectsRealIncompatibleTransportBeforeInfo(final DataExportTransport transport) {
        final var fixture =
                new FactoryFixture(DataExportTemplate.LOCALIZACAO_CARGAS, transport, 1, true);
        assertRejectedBeforeInfo(fixture.bundle, fixture.request, fixture.guard, fixture.client);
        assertEquals(0, fixture.cycle.sourceRequests());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void approvedObservedPolicySendsInfoExactlyOnceOn500(final DataExportTemplate template) {
        final var fixture =
                new FactoryFixture(template, DataExportTransport.GET_WITH_BODY, 1, true);
        final var bound =
                DataExportContractGate.enforce(fixture.bundle, fixture.request, fixture.guard);
        assertThrows(
                DataExportUnavailableException.class,
                () -> bound.templateInfoGateway().fetchInfo(template));
        assertEquals(1, fixture.client.requests.size());
        assertEquals(1, fixture.cycle.sourceRequests());
        assertEquals("GET", fixture.client.requests.get(0).method());
        assertEquals(
                "/v1/api/analytics/reports/" + template.templateId() + "/info",
                fixture.client.requests.get(0).uri().getPath());
    }

    @Test
    void rejectsActualFallbackBeforeInfo() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var raw =
                fixture.gateway(
                        ObservedFlowTestSupport.properties(DataExportTransport.GET_WITH_BODY, 1),
                        DataExportTransportFallbackPolicy.ALLOW_COMPATIBLE_FALLBACK);
        final var info = info(fixture.client, 1);
        final var bundle =
                DataExportHttpGatewayBundle.materialized(
                        raw, info, registry(), fixture.observation);
        assertRejectedBeforeInfo(bundle, fixture.request, fixture.guard, fixture.client);
    }

    @Test
    void rejectsActualInfoRetryEvenWhenDataPolicyIsValid() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var bundle =
                DataExportHttpGatewayBundle.materialized(
                        fixture.gateway(
                                ObservedFlowTestSupport.properties(
                                        DataExportTransport.GET_WITH_BODY, 1),
                                DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY),
                        info(fixture.client, 2),
                        registry(),
                        fixture.observation);
        assertRejectedBeforeInfo(bundle, fixture.request, fixture.guard, fixture.client);
    }

    @Test
    void callerSuppliedFingerprintAndGatewaysCannotMintMaterializedPolicyEvidence() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var bundle =
                DataExportHttpGatewayBundle.contractBound(
                        request -> {
                            throw new AssertionError("DATA_DELEGATE_NOT_ALLOWED");
                        },
                        template -> {
                            throw new AssertionError("INFO_DELEGATE_NOT_ALLOWED");
                        },
                        fixture.observation);
        assertRejectedBeforeInfo(bundle, fixture.request, fixture.guard, fixture.client);
    }

    @Test
    void legacyGateRetainsRealInfoRetry500Then200() {
        final var fixture =
                new FactoryFixture(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        DataExportTransport.GET_WITH_QUERY,
                        2,
                        false);
        final var bound =
                DataExportContractGate.enforce(fixture.bundle, fixture.request, fixture.guard);
        assertEquals(
                200,
                bound.templateInfoGateway().fetchInfo(fixture.request.template()).httpStatus());
        assertEquals(2, fixture.client.requests.size());
        assertEquals(2, fixture.cycle.sourceRequests());
    }

    private static void assertRejectedBeforeInfo(
            final DataExportHttpGatewayBundle bundle,
            final DataExportPageRequest request,
            final ContractRunGuard guard,
            final ObservedFlowTestSupport.FakeClient client) {
        final var failure =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                DataExportContractGate.enforce(bundle, request, guard)
                                        .templateInfoGateway()
                                        .fetchInfo(request.template()));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, failure.reason());
        assertEquals(0, client.requests.size());
        assertThrows(ContractDriftException.class, guard::sourceSelection);
    }

    private static HttpDataExportTemplateInfoGateway info(
            final ObservedFlowTestSupport.FakeClient client, final int attempts) {
        return new HttpDataExportTemplateInfoGateway(
                client,
                ObservedFlowTestSupport.properties(DataExportTransport.GET_WITH_BODY, attempts),
                ObservedFlowTestSupport.JSON,
                ignored -> {
                    throw new AssertionError("NO_SLEEP_BEFORE_PREFLIGHT");
                });
    }

    private static DataExportCircuitBreakerRegistry registry() {
        return new DataExportCircuitBreakerRegistry(
                new DataExportCircuitBreakerPolicy(3, Duration.ofSeconds(1)), Clock.systemUTC());
    }

    private static final class FactoryFixture {
        final DataExportSourceConfiguration source;
        final DataExportPageRequest request;
        final ContractRunGuard guard;
        final ObservedFlowTestSupport.FakeClient client;
        final EslRequestGovernor.Cycle cycle;
        final DataExportHttpGatewayBundle bundle;

        FactoryFixture(
                final DataExportTemplate template,
                final DataExportTransport transport,
                final int attempts,
                final boolean observed) {
            final var resilience =
                    new EslResiliencePolicy(
                            Duration.ZERO,
                            1,
                            20,
                            10,
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(10),
                            Duration.ZERO,
                            2,
                            3,
                            Duration.ofSeconds(1));
            source =
                    new DataExportSourceConfiguration(
                            "synthetic-source",
                            "synthetic-tenant",
                            new DataExportClientSettings(
                                    URI.create("https://synthetic.invalid/v1"),
                                    ZoneId.of("America/Sao_Paulo"),
                                    Duration.ofSeconds(2),
                                    transport,
                                    new DataExportRetryPolicy(
                                            attempts, Duration.ZERO, Duration.ZERO),
                                    2 * 1024 * 1024),
                            resilience);
            final var pinned = DataExportObservedEntityContractCatalog.release(template);
            final var release =
                    observed
                            ? pinned
                            : SourceContractRelease.create(
                                    pinned.sourceKind(),
                                    pinned.documentReference(),
                                    "synthetic-legacy-v1",
                                    new ContractMetadata(
                                            List.of(
                                                    new ContractMetadata.Element(
                                                            ContractMetadata.ElementKind.DATA_FIELD,
                                                            "id",
                                                            ContractMetadata.DeclaredType
                                                                    .UNDECLARED)),
                                            Optional.empty()),
                                    pinned.response());
            request =
                    observed
                            ? DataExportPageRequest.observed(
                                    template, release, base(), ObservedFlowTestSupport.DATES, 1, 50)
                            : new DataExportPageRequest(
                                    template,
                                    ObservedFlowTestSupport.DATES,
                                    Optional.empty(),
                                    1,
                                    template.defaultPageSize(),
                                    template.defaultOrderBy());
            final var policy =
                    ContractCompatibilityPolicy.create(
                            "observed-info-policy-v1", release.contractFingerprint(), List.of());
            final var binding =
                    ContractExecutionBinding.create(
                            UUID.randomUUID(),
                            release,
                            policy,
                            request.profile().runtimeFingerprint(base()));
            guard =
                    new ContractRunGuard(
                            binding,
                            release,
                            policy,
                            ContractTestSupport.controlPlaneStart(binding),
                            ignored -> {});
            final var observation =
                    DataExportContractObservationConfiguration.forRelease(
                            template,
                            release,
                            ContractObservationLimits.runtimeDefaults(),
                            ContractResponsePathBoundary.forRuntime(release, policy),
                            base());
            client =
                    new ObservedFlowTestSupport.FakeClient(
                            List.of("{}", "{\"fields\":[\"id\"],\"filters\":[]}"));
            client.statuses = List.of(500, 200);
            cycle =
                    new EslRequestGovernor(
                                    resilience,
                                    MonotonicTicker.systemTicker(),
                                    ignored -> {
                                        throw new AssertionError("ZERO_DELAY_SYNTHETIC_POLICY");
                                    })
                            .beginCycle(CancellationToken.none());
            final var factory =
                    new DataExportHttpGatewayFactory(
                            source.settings().materialize(key -> "synthetic-token"),
                            client,
                            ObservedFlowTestSupport.JSON,
                            registry(),
                            Optional.of(base()));
            final var workload =
                    switch (template) {
                        case MANIFESTOS -> EslWorkload.MANIFESTOS;
                        case COTACOES -> EslWorkload.COTACOES;
                        case LOCALIZACAO_CARGAS -> EslWorkload.LOCALIZACAO_DE_CARGAS;
                        default -> throw new AssertionError("CLOSED_TEST_SELECTION");
                    };
            bundle =
                    factory.forWorkload(
                            cycle, workload, DataExportHttpAttemptObserver.noop(), observation);
        }

        br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint base() {
            return DataExportRuntimeConfigurationFingerprint.from(source);
        }
    }
}
