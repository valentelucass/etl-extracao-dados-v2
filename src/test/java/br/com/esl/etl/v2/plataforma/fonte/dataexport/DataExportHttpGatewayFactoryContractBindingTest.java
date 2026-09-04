package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataExportHttpGatewayFactoryContractBindingTest {

    @Test
    void officialFactoryPreservesTheEffectiveFingerprintAndPassesGatePreflight() {
        final DataExportSourceConfiguration source = sourceConfiguration();
        final ImmutableFingerprint runtime = DataExportRuntimeConfigurationFingerprint.from(source);
        final SourceContractRelease release =
                SourceContractRelease.create(
                        ContractSourceKind.DATA_EXPORT,
                        DataExportContractAdapter.documentReference(DataExportTemplate.COLETAS),
                        "synthetic-v1",
                        ContractTestSupport.metadata(),
                        ContractTestSupport.response());
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "policy-v1", release.contractFingerprint(), List.of());
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(UUID.randomUUID(), release, policy, runtime);
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alert -> {});
        final DataExportContractObservationConfiguration observation =
                new DataExportContractObservationConfiguration(
                        DataExportTemplate.COLETAS,
                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                        "/id",
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, policy),
                        runtime);
        final DataExportHttpGatewayFactory factory =
                new DataExportHttpGatewayFactory(
                        source, key -> "synthetic-token", Clock.systemUTC());

        final DataExportHttpGatewayBundle bundle =
                factory.forWorkload(
                        cycle(source.resiliencePolicy()),
                        EslWorkload.COLETAS,
                        DataExportHttpAttemptObserver.noop(),
                        observation);

        assertTrue(bundle.observationConfiguration().isPresent());
        assertEquals(
                runtime,
                bundle.observationConfiguration().orElseThrow().runtimeConfigurationFingerprint());
        assertTrue(
                DataExportContractGate.enforce(bundle, DataExportTemplate.COLETAS, guard)
                        .observationConfiguration()
                        .isPresent());
        assertTrue(
                new DataExportHttpGatewayFactory(
                                source, key -> "different-synthetic-token", Clock.systemUTC())
                        .forWorkload(
                                cycle(source.resiliencePolicy()),
                                EslWorkload.COLETAS,
                                DataExportHttpAttemptObserver.noop(),
                                observation)
                        .observationConfiguration()
                        .isPresent());
    }

    @Test
    void unboundOrMismatchedFactoryRejectsObservationBeforeOpeningAWorkload() {
        final DataExportSourceConfiguration source = sourceConfiguration();
        final SourceContractRelease release = ContractTestSupport.release();
        final ContractCompatibilityPolicy policy = ContractTestSupport.policy(release);
        final DataExportContractObservationConfiguration observation =
                new DataExportContractObservationConfiguration(
                        DataExportTemplate.COLETAS,
                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                        "/id",
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, policy),
                        DataExportRuntimeConfigurationFingerprint.from(source));
        final EslRequestGovernor.Cycle unboundCycle = cycle(source.resiliencePolicy());
        final DataExportHttpGatewayFactory unboundFactory =
                new DataExportHttpGatewayFactory(
                        source.settings().materialize(key -> "synthetic-token"),
                        source.resiliencePolicy(),
                        Clock.systemUTC());

        final ContractDriftException unbound =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                unboundFactory.forWorkload(
                                        unboundCycle,
                                        EslWorkload.COLETAS,
                                        DataExportHttpAttemptObserver.noop(),
                                        observation));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, unbound.reason());
        assertEquals(0, unboundCycle.sourceRequests());

        final EslRequestGovernor.Cycle mismatchCycle = cycle(source.resiliencePolicy());
        final DataExportHttpGatewayFactory boundFactory =
                new DataExportHttpGatewayFactory(
                        source, key -> "synthetic-token", Clock.systemUTC());
        final DataExportContractObservationConfiguration mismatch =
                new DataExportContractObservationConfiguration(
                        observation.template(),
                        observation.expectedResponseForm(),
                        observation.approvedKeyPath(),
                        observation.observationLimits(),
                        observation.responsePathBoundary(),
                        new ImmutableFingerprint(
                                DataExportRuntimeConfigurationFingerprint.VERSION, "e".repeat(64)));
        assertThrows(
                ContractDriftException.class,
                () ->
                        boundFactory.forWorkload(
                                mismatchCycle,
                                EslWorkload.COLETAS,
                                DataExportHttpAttemptObserver.noop(),
                                mismatch));
        assertEquals(0, mismatchCycle.sourceRequests());
    }

    private static DataExportSourceConfiguration sourceConfiguration() {
        final EslResiliencePolicy resilience =
                new EslResiliencePolicy(
                        Duration.ZERO,
                        1,
                        20,
                        10,
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(5),
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(1),
                        2,
                        3,
                        Duration.ofSeconds(1));
        return new DataExportSourceConfiguration(
                "synthetic-source",
                "synthetic-tenant",
                new DataExportClientSettings(
                        URI.create("https://synthetic.invalid/v1"),
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(2),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ofMillis(10), Duration.ofMillis(20)),
                        1_024L),
                resilience);
    }

    private static EslRequestGovernor.Cycle cycle(final EslResiliencePolicy policy) {
        return new EslRequestGovernor(
                        policy, MonotonicTicker.systemTicker(), ResilienceSleeper.threadSleeper())
                .beginCycle(CancellationToken.none());
    }
}
