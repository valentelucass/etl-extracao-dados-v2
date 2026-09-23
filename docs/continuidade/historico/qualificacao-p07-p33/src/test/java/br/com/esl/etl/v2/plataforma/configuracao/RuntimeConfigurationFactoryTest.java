package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeConfigurationFactoryTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-30T15:00:00Z"), ZoneOffset.UTC);

    @TempDir Path temporaryDirectory;

    @Test
    void loadsAnExplicitSafeConfigurationAndInjectsTheClock() throws Exception {
        final RuntimeConfiguration configuration =
                load(safeConfiguration(), new Properties(), Map.of());

        assertEquals(RuntimeEnvironment.LOCAL_SHADOW, configuration.environment());
        assertEquals("America/Sao_Paulo", configuration.businessZone().getId());
        assertEquals(FIXED_CLOCK.instant(), configuration.clock().instant());
        assertTrue(configuration.dataExport().isEmpty());
        assertTrue(configuration.graphQl().isEmpty());
        assertFalse(configuration.shadowStorage().auditEnabled());
    }

    @Test
    void environmentOverridesTheExternalNonSecretConfiguration() throws Exception {
        final RuntimeConfiguration configuration =
                load(
                        safeConfiguration(),
                        new Properties(),
                        Map.of("V2_RUNTIME_BUSINESS_TIMEZONE", "UTC"));

        assertEquals("UTC", configuration.businessZone().getId());
    }

    @Test
    void rejectsEverySecretClassifiedSystemProperty() throws Exception {
        final Properties systemProperties = new Properties();
        systemProperties.setProperty("custom.authorization", "synthetic-value");

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> load(safeConfiguration(), systemProperties, Map.of()));

        assertEquals(
                "Segredos não são aceitos por propriedades de sistema.", exception.getMessage());
        assertFalse(exception.toString().contains("synthetic-value"));
    }

    @Test
    void rejectsSelfAssignedRuntimeRolesFromSystemProperties() {
        final Properties systemProperties = new Properties();
        systemProperties.setProperty("runtime.roles", "RUNTIME_FORCE_RUN");

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> load(safeConfiguration(), systemProperties, Map.of()));

        assertEquals(
                "Configuração de runtime não é aceita por propriedades de sistema.",
                exception.getMessage());
        assertFalse(exception.toString().contains("RUNTIME_FORCE_RUN"));
    }

    @Test
    void rejectsGraphQlRuntimeConfigurationFromSystemProperties() {
        final Properties systemProperties = new Properties();
        systemProperties.setProperty("graphql.endpoint", "https://graphql.example.test/graphql");

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> load(safeConfiguration(), systemProperties, Map.of()));

        assertEquals(
                "Configuração de runtime não é aceita por propriedades de sistema.",
                exception.getMessage());
        assertFalse(exception.toString().contains("graphql.example.test"));
    }

    @Test
    void rejectsASecretInTheExternalConfigurationFile() throws Exception {
        final String configuration = safeConfiguration() + "\ndataexport.token=synthetic-value\n";

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> load(configuration, new Properties(), Map.of()));

        assertEquals(
                "Segredos não são aceitos no arquivo de configuração.", exception.getMessage());
        assertFalse(exception.toString().contains("synthetic-value"));
    }

    @Test
    void rejectsSiblingEnvFilesInsteadOfLoadingThemImplicitly() throws Exception {
        final Path environmentFile = temporaryDirectory.resolve(".env.local");
        Files.writeString(environmentFile, safeConfiguration(), StandardCharsets.UTF_8);

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new RuntimeConfigurationFactory()
                                        .load(
                                                environmentFile,
                                                new Properties(),
                                                Map.of(),
                                                FIXED_CLOCK));

        assertEquals(
                "Arquivos .env não são aceitos como configuração de runtime.",
                exception.getMessage());
    }

    @Test
    void createsNonSecretDataExportSettingsWithoutResolvingTheToken() throws Exception {
        final String syntheticSensitiveValue = "must-not-be-materialized";
        final AtomicInteger tokenReads = new AtomicInteger();
        final Map<String, String> environment =
                new AbstractMap<>() {
                    @Override
                    public Set<Entry<String, String>> entrySet() {
                        return Map.of("V2_DATAEXPORT_TOKEN", syntheticSensitiveValue).entrySet();
                    }

                    @Override
                    public String get(final Object key) {
                        if ("V2_DATAEXPORT_TOKEN".equals(key)) {
                            tokenReads.incrementAndGet();
                        }
                        return super.get(key);
                    }
                };
        final RuntimeConfiguration configuration =
                load(
                        enabledDataExportConfiguration(30, 10_485_760L, 3, 500, 10_000),
                        new Properties(),
                        environment);

        assertTrue(configuration.dataExport().isPresent());
        assertEquals("esl-shadow", configuration.dataExport().orElseThrow().sourceInstance());
        assertEquals("tenant-synthetic", configuration.dataExport().orElseThrow().tenantScope());
        assertEquals(0, tokenReads.get());
        assertEquals(1, configuration.dataExport().orElseThrow().resiliencePolicy().maxInFlight());
        assertEquals(
                10_000,
                configuration.dataExport().orElseThrow().resiliencePolicy().maxRequestsPerCycle());
        assertFalse(
                configuration
                        .dataExport()
                        .orElseThrow()
                        .settings()
                        .toString()
                        .contains(syntheticSensitiveValue));
    }

    @Test
    void createsNonSecretGraphQlSettingsWithoutResolvingTheToken() throws Exception {
        final String syntheticSensitiveValue = "must-not-be-materialized";
        final AtomicInteger tokenReads = new AtomicInteger();
        final Map<String, String> environment =
                new AbstractMap<>() {
                    @Override
                    public Set<Entry<String, String>> entrySet() {
                        return Map.of("V2_GRAPHQL_TOKEN", syntheticSensitiveValue).entrySet();
                    }

                    @Override
                    public String get(final Object key) {
                        if ("V2_GRAPHQL_TOKEN".equals(key)) {
                            tokenReads.incrementAndGet();
                        }
                        return super.get(key);
                    }
                };
        final RuntimeConfiguration configuration =
                load(enabledGraphQlConfiguration(), new Properties(), environment);

        assertTrue(configuration.graphQl().isPresent());
        assertEquals("esl-shadow", configuration.graphQl().orElseThrow().sourceInstance());
        assertEquals("tenant-synthetic", configuration.graphQl().orElseThrow().tenantScope());
        assertEquals(0, tokenReads.get());
        assertEquals(1, configuration.graphQl().orElseThrow().resiliencePolicy().maxInFlight());
        assertEquals(
                java.time.Duration.ofSeconds(30),
                configuration.graphQl().orElseThrow().settings().requestTimeout());
        assertEquals(
                10_485_760L, configuration.graphQl().orElseThrow().settings().maxResponseBytes());
        assertEquals(
                3, configuration.graphQl().orElseThrow().settings().retryPolicy().maxAttempts());
        assertEquals(
                java.time.Duration.ofMillis(500),
                configuration.graphQl().orElseThrow().settings().retryPolicy().initialDelay());
        assertEquals(
                java.time.Duration.ofSeconds(10),
                configuration.graphQl().orElseThrow().settings().retryPolicy().maxDelay());
        assertFalse(
                configuration
                        .graphQl()
                        .orElseThrow()
                        .settings()
                        .toString()
                        .contains(syntheticSensitiveValue));
    }

    @Test
    void environmentOverridesGraphQlNonSecretConfiguration() throws Exception {
        final RuntimeConfiguration configuration =
                load(
                        enabledGraphQlConfiguration(),
                        new Properties(),
                        Map.of("V2_GRAPHQL_ENDPOINT", "https://override.example.test/graphql"));

        assertEquals(
                "override.example.test",
                configuration.graphQl().orElseThrow().settings().endpoint().getHost());
    }

    @Test
    void enforcesOneIdentityAndResiliencePolicyForEnabledEslTransports() throws Exception {
        final String bothEnabled =
                enabledDataExportConfiguration(30, 10_485_760L, 3, 500, 10_000)
                        + "\n"
                        + enabledGraphQlConfiguration().substring(safeConfiguration().length());

        final RuntimeConfiguration accepted = load(bothEnabled, new Properties(), Map.of());
        assertTrue(accepted.dataExport().isPresent());
        assertTrue(accepted.graphQl().isPresent());

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                bothEnabled.replace(
                                        "graphql.source-instance=esl-shadow",
                                        "graphql.source-instance=other-source"),
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                bothEnabled.replace(
                                        "graphql.tenant-scope=tenant-synthetic",
                                        "graphql.tenant-scope=other-tenant"),
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                bothEnabled.replace(
                                        "graphql.resilience.max-requests-per-cycle=10000",
                                        "graphql.resilience.max-requests-per-cycle=9999"),
                                new Properties(),
                                Map.of()));
    }

    @Test
    void enabledGraphQlFailsOnMissingAndMalformedTypedSettings() {
        final String missingEndpoint =
                enabledGraphQlConfiguration()
                        .replace("graphql.endpoint=https://graphql.example.test/graphql\n", "");

        assertThrows(
                IllegalStateException.class,
                () -> load(missingEndpoint, new Properties(), Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                enabledGraphQlConfiguration()
                                        .replace(
                                                "graphql.retry.max-attempts=3",
                                                "graphql.retry.max-attempts=invalid"),
                                new Properties(),
                                Map.of()));
    }

    @Test
    void rejectsAConfigurationFileLargerThan64KiB() {
        final String oversizedConfiguration =
                safeConfiguration() + "#" + "x".repeat((64 * 1024) + 1);

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> load(oversizedConfiguration, new Properties(), Map.of()));

        assertEquals(
                "O arquivo de configuração excede o limite permitido.", exception.getMessage());
    }

    @Test
    void rejectsDuplicateConfigurationKeysInsteadOfSilentlyKeepingTheLastValue() {
        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                load(
                                        safeConfiguration()
                                                + "runtime.environment=NON_PRODUCTION_SHADOW\n",
                                        new Properties(),
                                        Map.of()));

        assertEquals("O arquivo contém uma propriedade duplicada.", exception.getMessage());
    }

    @Test
    void rejectsDormantSubordinateFeatureValuesFromFilesAndEnvironment() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration()
                                        + "dataexport.base-url=https://dataexport.example.test\n",
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration()
                                        + "shadow.jdbc-url=jdbc:sqlserver://localhost\n",
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration(),
                                new Properties(),
                                Map.of(
                                        "V2_DATAEXPORT_BASE_URL",
                                        "https://dataexport.example.test")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration(),
                                new Properties(),
                                Map.of("V2_SHADOW_JDBC_URL", "jdbc:sqlserver://localhost")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration()
                                        + "graphql.endpoint=https://graphql.example.test/graphql\n",
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration(),
                                new Properties(),
                                Map.of(
                                        "V2_GRAPHQL_ENDPOINT",
                                        "https://graphql.example.test/graphql")));
    }

    @Test
    void acceptsExactNumericCeilingsAndRejectsEveryValueImmediatelyAboveThem() throws Exception {
        final String atCeilings =
                enabledDataExportConfiguration(
                        DataExportClientSettings.MAX_REQUEST_TIMEOUT.toSeconds(),
                        DataExportClientSettings.MAX_RESPONSE_BYTES,
                        DataExportRetryPolicy.MAX_ATTEMPTS,
                        DataExportRetryPolicy.MAX_DELAY_MILLIS,
                        DataExportRetryPolicy.MAX_DELAY_MILLIS);

        final DataExportClientSettings accepted =
                load(atCeilings, new Properties(), Map.of()).dataExport().orElseThrow().settings();

        assertEquals(DataExportClientSettings.MAX_REQUEST_TIMEOUT, accepted.requestTimeout());
        assertEquals(DataExportClientSettings.MAX_RESPONSE_BYTES, accepted.maxResponseBytes());
        assertEquals(DataExportRetryPolicy.MAX_ATTEMPTS, accepted.retryPolicy().maxAttempts());
        assertEquals(DataExportRetryPolicy.MAX_DELAY, accepted.retryPolicy().initialDelay());
        assertEquals(DataExportRetryPolicy.MAX_DELAY, accepted.retryPolicy().maxDelay());

        final String[] aboveCeilings = {
            atCeilings.replace("timeout-seconds=300", "timeout-seconds=301"),
            atCeilings.replace("max-response-bytes=67108864", "max-response-bytes=67108865"),
            atCeilings.replace("max-attempts=10", "max-attempts=11"),
            atCeilings.replace("initial-delay-ms=300000", "initial-delay-ms=300001"),
            atCeilings.replace("max-delay-ms=300000", "max-delay-ms=300001")
        };
        for (final String aboveCeiling : aboveCeilings) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> load(aboveCeiling, new Properties(), Map.of()));
        }
    }

    @Test
    void refusesAnEnabledSourceWithoutTheFixedTransport() throws Exception {
        final String enabledConfiguration =
                safeConfiguration()
                        + "\ndataexport.enabled=true"
                        + "\ndataexport.base-url=https://dataexport.example.test"
                        + "\ndataexport.source-instance=esl-shadow"
                        + "\ndataexport.tenant-scope=tenant-synthetic"
                        + "\ndataexport.timezone=America/Sao_Paulo"
                        + "\ndataexport.transport=POST_JSON"
                        + "\ndataexport.timeout-seconds=30"
                        + "\ndataexport.max-response-bytes=10485760"
                        + "\ndataexport.retry.max-attempts=3"
                        + "\ndataexport.retry.initial-delay-ms=500"
                        + "\ndataexport.retry.max-delay-ms=10000"
                        + resilienceConfiguration();

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                enabledConfiguration,
                                new Properties(),
                                Map.of("V2_DATAEXPORT_TOKEN", "value")));

        final String fixedTransport = enabledConfiguration.replace("POST_JSON", "GET_WITH_QUERY");
        assertTrue(load(fixedTransport, new Properties(), Map.of()).dataExport().isPresent());
    }

    @Test
    void rejectsInconsistentResilienceBudgetsAndDeadlines() {
        final String enabled = enabledDataExportConfiguration(30, 10_485_760L, 3, 500, 10_000);

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                enabled.replace(
                                        "resilience.step-timeout-seconds=900",
                                        "resilience.step-timeout-seconds=10"),
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                enabled.replace(
                                        "resilience.max-requests-per-workload=5000",
                                        "resilience.max-requests-per-workload=20000"),
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                enabled.replace(
                                        "resilience.max-retry-after-ms=10000",
                                        "resilience.max-retry-after-ms=300001"),
                                new Properties(),
                                Map.of()));
    }

    @Test
    void rejectsUnknownFileAndReservedEnvironmentKeys() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration() + "\ndataexport.enabld=true\n",
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration(),
                                new Properties(),
                                Map.of("V2_RUNTIME_ENABLDD", "true")));
    }

    @Test
    void createsOnlyTheAllowlistedLocalShadowTarget() throws Exception {
        final RuntimeConfiguration configuration =
                load(
                        safeConfiguration()
                                + "\n"
                                + "shadow.audit.enabled=true\n"
                                + "shadow.target-kind=LOCAL_EPHEMERAL\n"
                                + "shadow.jdbc-url=jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true",
                        new Properties(),
                        Map.of());

        final RuntimePreflightReport report = new RuntimeTargetPreflight().validate(configuration);

        assertTrue(report.shadowAuditEnabled());
    }

    @Test
    void preflightRefusesAnUnapprovedEnvironmentAndAProductionLikeTarget() throws Exception {
        final RuntimeConfiguration nonProduction =
                load(
                        safeConfiguration().replace("LOCAL_SHADOW", "NON_PRODUCTION_SHADOW"),
                        new Properties(),
                        Map.of());
        assertThrows(
                IllegalStateException.class,
                () -> new RuntimeTargetPreflight().validate(nonProduction));

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration()
                                        + "\nshadow.audit.enabled=true"
                                        + "\nshadow.target-kind=LOCAL_EPHEMERAL"
                                        + "\nshadow.jdbc-url=jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA",
                                new Properties(),
                                Map.of()));
    }

    @Test
    void failsFastForMalformedAndMissingNonSecretValues() throws Exception {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                "runtime.environment=LOCAL_SHADOW\n"
                                        + "runtime.business-timezone=UTC\n"
                                        + "dataexport.enabled=maybe\n",
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration().replace("America/Sao_Paulo", "-03:00"),
                                new Properties(),
                                Map.of()));
        assertThrows(
                IllegalStateException.class,
                () -> load("runtime.environment=LOCAL_SHADOW\n", new Properties(), Map.of()));
    }

    @Test
    void refusesInvalidNumbersAndUnsupportedEnvironmentNamesWithoutLeakingValues()
            throws Exception {
        final String invalidNumber =
                safeConfiguration()
                        + "\ndataexport.enabled=true"
                        + "\ndataexport.base-url=https://dataexport.example.test"
                        + "\ndataexport.source-instance=esl-shadow"
                        + "\ndataexport.tenant-scope=tenant-synthetic"
                        + "\ndataexport.timezone=America/Sao_Paulo"
                        + "\ndataexport.transport=GET_WITH_QUERY"
                        + "\ndataexport.timeout-seconds=invalid-value"
                        + "\ndataexport.max-response-bytes=10485760"
                        + "\ndataexport.retry.max-attempts=3"
                        + "\ndataexport.retry.initial-delay-ms=500"
                        + "\ndataexport.retry.max-delay-ms=10000";

        final IllegalArgumentException invalid =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                load(
                                        invalidNumber,
                                        new Properties(),
                                        Map.of("V2_DATAEXPORT_TOKEN", "value")));
        assertFalse(invalid.toString().contains("invalid-value"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                safeConfiguration().replace("LOCAL_SHADOW", "PRODUCTION"),
                                new Properties(),
                                Map.of()));
    }

    private RuntimeConfiguration load(
            final String content,
            final Properties systemProperties,
            final Map<String, String> environment)
            throws Exception {
        final Path configurationFile = temporaryDirectory.resolve("runtime.properties");
        Files.writeString(configurationFile, content, StandardCharsets.UTF_8);
        return new RuntimeConfigurationFactory()
                .load(configurationFile, systemProperties, environment, FIXED_CLOCK);
    }

    private static String safeConfiguration() {
        return "runtime.environment=LOCAL_SHADOW\nruntime.business-timezone=America/Sao_Paulo\n";
    }

    private static String enabledDataExportConfiguration(
            final long timeoutSeconds,
            final long maxResponseBytes,
            final int maxAttempts,
            final long initialDelayMillis,
            final long maxDelayMillis) {
        return safeConfiguration()
                + "dataexport.enabled=true"
                + "\ndataexport.base-url=https://dataexport.example.test"
                + "\ndataexport.source-instance=esl-shadow"
                + "\ndataexport.tenant-scope=tenant-synthetic"
                + "\ndataexport.timezone=America/Sao_Paulo"
                + "\ndataexport.transport=GET_WITH_QUERY"
                + "\ndataexport.timeout-seconds="
                + timeoutSeconds
                + "\ndataexport.max-response-bytes="
                + maxResponseBytes
                + "\ndataexport.retry.max-attempts="
                + maxAttempts
                + "\ndataexport.retry.initial-delay-ms="
                + initialDelayMillis
                + "\ndataexport.retry.max-delay-ms="
                + maxDelayMillis
                + resilienceConfiguration();
    }

    private static String enabledGraphQlConfiguration() {
        return safeConfiguration()
                + "graphql.enabled=true"
                + "\ngraphql.endpoint=https://graphql.example.test/graphql"
                + "\ngraphql.source-instance=esl-shadow"
                + "\ngraphql.tenant-scope=tenant-synthetic"
                + "\ngraphql.timeout-seconds=30"
                + "\ngraphql.max-response-bytes=10485760"
                + "\ngraphql.retry.max-attempts=3"
                + "\ngraphql.retry.initial-delay-ms=500"
                + "\ngraphql.retry.max-delay-ms=10000"
                + resilienceConfiguration().replace("dataexport.", "graphql.");
    }

    private static String resilienceConfiguration() {
        return "\ndataexport.resilience.minimum-request-interval-ms=250"
                + "\ndataexport.resilience.max-in-flight=1"
                + "\ndataexport.resilience.max-requests-per-cycle=10000"
                + "\ndataexport.resilience.max-requests-per-workload=5000"
                + "\ndataexport.resilience.step-timeout-seconds=900"
                + "\ndataexport.resilience.cycle-timeout-seconds=3600"
                + "\ndataexport.resilience.max-retry-after-ms=10000"
                + "\ndataexport.resilience.max-repartitions=8"
                + "\ndataexport.resilience.circuit-failure-threshold=3"
                + "\ndataexport.resilience.circuit-cooldown-seconds=60";
    }
}
