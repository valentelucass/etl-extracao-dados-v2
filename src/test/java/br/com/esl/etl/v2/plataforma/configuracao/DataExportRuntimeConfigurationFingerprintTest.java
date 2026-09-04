package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class DataExportRuntimeConfigurationFingerprintTest {

    @Test
    void isDeterministicSecretIndependentAndSensitiveToEveryConfigurationGroup() {
        final DataExportSourceConfiguration baseline = configuration("source-a", "tenant-a");

        assertEquals(
                DataExportRuntimeConfigurationFingerprint.from(baseline),
                DataExportRuntimeConfigurationFingerprint.from(baseline));
        assertEquals(
                DataExportRuntimeConfigurationFingerprint.VERSION,
                DataExportRuntimeConfigurationFingerprint.from(baseline).version());
        assertNotEquals(
                DataExportRuntimeConfigurationFingerprint.from(baseline),
                DataExportRuntimeConfigurationFingerprint.from(
                        configuration("source-b", "tenant-a")));
        assertNotEquals(
                DataExportRuntimeConfigurationFingerprint.from(baseline),
                DataExportRuntimeConfigurationFingerprint.from(
                        configuration("source-a", "tenant-b")));

        final DataExportClientSettings changedSettings =
                new DataExportClientSettings(
                        URI.create("https://synthetic.invalid/v2"),
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(3),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(2, Duration.ofMillis(10), Duration.ofMillis(20)),
                        2_048L);
        assertNotEquals(
                DataExportRuntimeConfigurationFingerprint.from(baseline),
                DataExportRuntimeConfigurationFingerprint.from(
                        new DataExportSourceConfiguration(
                                "source-a", "tenant-a", changedSettings, policy(10))));
        assertNotEquals(
                DataExportRuntimeConfigurationFingerprint.from(baseline),
                DataExportRuntimeConfigurationFingerprint.from(
                        new DataExportSourceConfiguration(
                                "source-a", "tenant-a", baseline.settings(), policy(11))));

        assertEquals(
                baseline.settings().materialize(key -> "synthetic-token-a").baseUri(),
                baseline.settings().materialize(key -> "synthetic-token-b").baseUri());
        assertEquals(
                DataExportRuntimeConfigurationFingerprint.from(baseline),
                DataExportRuntimeConfigurationFingerprint.from(baseline));
    }

    private static DataExportSourceConfiguration configuration(
            final String sourceInstance, final String tenantScope) {
        return new DataExportSourceConfiguration(
                sourceInstance,
                tenantScope,
                new DataExportClientSettings(
                        URI.create("https://synthetic.invalid/v1"),
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(3),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(2, Duration.ofMillis(10), Duration.ofMillis(20)),
                        1_024L),
                policy(10));
    }

    private static EslResiliencePolicy policy(final int requestsPerCycle) {
        return new EslResiliencePolicy(
                Duration.ZERO,
                1,
                requestsPerCycle,
                5,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                2,
                3,
                Duration.ofSeconds(1));
    }
}
