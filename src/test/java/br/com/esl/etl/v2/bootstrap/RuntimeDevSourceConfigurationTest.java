package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.io.StringReader;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class RuntimeDevSourceConfigurationTest {
    static Properties plan() throws Exception {
        final var result = new Properties();
        result.load(
                new StringReader(
                        """
runtime.environment=LOCAL_SHADOW
runtime.business-timezone=America/Sao_Paulo
dataexport.enabled=true
dataexport.source-instance=F4RootPartition
dataexport.tenant-scope=F4RootPartition
dataexport.timezone=America/Sao_Paulo
dataexport.transport=GET_WITH_QUERY
dataexport.timeout-seconds=30
dataexport.max-response-bytes=10485760
dataexport.retry.max-attempts=1
dataexport.retry.initial-delay-ms=0
dataexport.retry.max-delay-ms=0
dataexport.resilience.minimum-request-interval-ms=2000
dataexport.resilience.max-in-flight=1
dataexport.resilience.max-requests-per-cycle=2
dataexport.resilience.max-requests-per-workload=2
dataexport.resilience.step-timeout-seconds=60
dataexport.resilience.cycle-timeout-seconds=60
dataexport.resilience.max-retry-after-ms=0
dataexport.resilience.max-repartitions=0
dataexport.resilience.circuit-failure-threshold=1
dataexport.resilience.circuit-cooldown-seconds=1
graphql.enabled=false
shadow.audit.enabled=false
"""));
        return result;
    }

    @Test
    void explicitPlanSuppliesNewBudgetsAndPartitionsWithoutURIOrToken() throws Exception {
        final var merged = RuntimeDevSourceConfiguration.mergePlan(plan(), Map.of());
        assertEquals("F4RootPartition", merged.get("V2_DATAEXPORT_SOURCE_INSTANCE"));
        assertEquals("2000", merged.get("V2_DATAEXPORT_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS"));
        assertFalse(merged.containsKey("V2_DATAEXPORT_TOKEN"));
        assertFalse(merged.containsKey("V2_DATAEXPORT_BASE_URL"));
    }

    @Test
    void missingBudgetsAndConflictingNativeInputsCannotSilentlyInheritOldSettings()
            throws Exception {
        final var incomplete = plan();
        incomplete.remove("dataexport.resilience.max-requests-per-cycle");
        assertThrows(
                IOException.class,
                () -> RuntimeDevSourceConfiguration.mergePlan(incomplete, Map.of()));
        for (final String key : new String[] {"V2_DATAEXPORT_BASE_URL", "V2_DATAEXPORT_TOKEN"}) {
            assertThrows(
                    IOException.class,
                    () ->
                            RuntimeDevSourceConfiguration.mergePlan(
                                    plan(), Map.of(key, "fixture-only")));
        }
        assertThrows(
                IOException.class,
                () ->
                        RuntimeDevSourceConfiguration.mergePlan(
                                plan(),
                                Map.of("V2_DATAEXPORT_SOURCE_INSTANCE", "another-partition")));
    }

    @Test
    void supplierValuesAndLegacyCredentialReferenceAreOutsideMetadataPlan() throws Exception {
        final var input = plan();
        input.put("dataexport.base-url", "https://fixture.invalid");
        assertThrows(
                IOException.class, () -> RuntimeDevSourceConfiguration.mergePlan(input, Map.of()));
        input.remove("dataexport.base-url");
        input.put("dataexport.token", "fixture-only");
        assertThrows(
                IOException.class, () -> RuntimeDevSourceConfiguration.mergePlan(input, Map.of()));
        assertEquals(
                "runtime.private.properties",
                RuntimeDevSourceConfiguration.REFERENCE.getFileName().toString());
    }

    @Test
    void leasedProfileSelectsConcreteBranchesWithoutGrantingAuthority() throws Exception {
        final var input = new Properties();
        final String key = "runtime.dev.engine.sample.enabled";
        assertFalse(RuntimeDevSourceConfiguration.selectedOptIn(input, key));
        input.setProperty(key, "true");
        assertEquals(true, RuntimeDevSourceConfiguration.selectedOptIn(input, key));
        input.setProperty(key, "permit");
        assertThrows(
                IOException.class, () -> RuntimeDevSourceConfiguration.selectedOptIn(input, key));
    }
}
