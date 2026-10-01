package br.com.esl.etl.v2.plataforma.configuracao;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.ExecutionDeadlines;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

/**
 * Lê a configuração não secreta de um arquivo explícito e aplica somente overrides V2 do ambiente.
 *
 * <p>A precedência é única: variável de ambiente V2 não vazia, depois arquivo indicado em {@code
 * --config}. Propriedades de sistema nunca configuram o runtime e só são verificadas para rejeitar
 * tentativa de injeção de segredo.
 */
public final class RuntimeConfigurationFactory {

    private static final long MAX_CONFIGURATION_BYTES = 64L * 1024L;
    private static final int MAX_CONFIGURATION_READ_BYTES =
            Math.toIntExact(MAX_CONFIGURATION_BYTES + 1L);
    private static final Set<String> FILE_KEYS =
            Set.of(
                    "runtime.environment",
                    "runtime.business-timezone",
                    "dataexport.enabled",
                    "dataexport.base-url",
                    "dataexport.source-instance",
                    "dataexport.tenant-scope",
                    "dataexport.timezone",
                    "dataexport.transport",
                    "dataexport.timeout-seconds",
                    "dataexport.max-response-bytes",
                    "dataexport.retry.max-attempts",
                    "dataexport.retry.initial-delay-ms",
                    "dataexport.retry.max-delay-ms",
                    "dataexport.resilience.minimum-request-interval-ms",
                    "dataexport.resilience.max-in-flight",
                    "dataexport.resilience.max-requests-per-cycle",
                    "dataexport.resilience.max-requests-per-workload",
                    "dataexport.resilience.step-timeout-seconds",
                    "dataexport.resilience.cycle-timeout-seconds",
                    "dataexport.resilience.max-retry-after-ms",
                    "dataexport.resilience.max-repartitions",
                    "dataexport.resilience.circuit-failure-threshold",
                    "dataexport.resilience.circuit-cooldown-seconds",
                    "graphql.enabled",
                    "graphql.endpoint",
                    "graphql.source-instance",
                    "graphql.tenant-scope",
                    "graphql.timeout-seconds",
                    "graphql.max-response-bytes",
                    "graphql.retry.max-attempts",
                    "graphql.retry.initial-delay-ms",
                    "graphql.retry.max-delay-ms",
                    "graphql.resilience.minimum-request-interval-ms",
                    "graphql.resilience.max-in-flight",
                    "graphql.resilience.max-requests-per-cycle",
                    "graphql.resilience.max-requests-per-workload",
                    "graphql.resilience.step-timeout-seconds",
                    "graphql.resilience.cycle-timeout-seconds",
                    "graphql.resilience.max-retry-after-ms",
                    "graphql.resilience.max-repartitions",
                    "graphql.resilience.circuit-failure-threshold",
                    "graphql.resilience.circuit-cooldown-seconds",
                    "shadow.audit.enabled",
                    "shadow.target-kind",
                    "shadow.jdbc-url",
                    "shadow.approval-reference");
    private static final Set<String> ENVIRONMENT_KEYS =
            Set.of(
                    "V2_RUNTIME_ENVIRONMENT",
                    "V2_RUNTIME_BUSINESS_TIMEZONE",
                    "V2_DATAEXPORT_ENABLED",
                    "V2_DATAEXPORT_BASE_URL",
                    "V2_DATAEXPORT_SOURCE_INSTANCE",
                    "V2_DATAEXPORT_TENANT_SCOPE",
                    "V2_DATAEXPORT_TIMEZONE",
                    "V2_DATAEXPORT_TRANSPORT",
                    "V2_DATAEXPORT_TIMEOUT_SECONDS",
                    "V2_DATAEXPORT_MAX_RESPONSE_BYTES",
                    "V2_DATAEXPORT_RETRY_MAX_ATTEMPTS",
                    "V2_DATAEXPORT_RETRY_INITIAL_DELAY_MS",
                    "V2_DATAEXPORT_RETRY_MAX_DELAY_MS",
                    "V2_DATAEXPORT_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS",
                    "V2_DATAEXPORT_RESILIENCE_MAX_IN_FLIGHT",
                    "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_CYCLE",
                    "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD",
                    "V2_DATAEXPORT_RESILIENCE_STEP_TIMEOUT_SECONDS",
                    "V2_DATAEXPORT_RESILIENCE_CYCLE_TIMEOUT_SECONDS",
                    "V2_DATAEXPORT_RESILIENCE_MAX_RETRY_AFTER_MS",
                    "V2_DATAEXPORT_RESILIENCE_MAX_REPARTITIONS",
                    "V2_DATAEXPORT_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD",
                    "V2_DATAEXPORT_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS",
                    "V2_DATAEXPORT_TOKEN",
                    "V2_GRAPHQL_ENABLED",
                    "V2_GRAPHQL_ENDPOINT",
                    "V2_GRAPHQL_SOURCE_INSTANCE",
                    "V2_GRAPHQL_TENANT_SCOPE",
                    "V2_GRAPHQL_TIMEOUT_SECONDS",
                    "V2_GRAPHQL_MAX_RESPONSE_BYTES",
                    "V2_GRAPHQL_RETRY_MAX_ATTEMPTS",
                    "V2_GRAPHQL_RETRY_INITIAL_DELAY_MS",
                    "V2_GRAPHQL_RETRY_MAX_DELAY_MS",
                    "V2_GRAPHQL_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS",
                    "V2_GRAPHQL_RESILIENCE_MAX_IN_FLIGHT",
                    "V2_GRAPHQL_RESILIENCE_MAX_REQUESTS_PER_CYCLE",
                    "V2_GRAPHQL_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD",
                    "V2_GRAPHQL_RESILIENCE_STEP_TIMEOUT_SECONDS",
                    "V2_GRAPHQL_RESILIENCE_CYCLE_TIMEOUT_SECONDS",
                    "V2_GRAPHQL_RESILIENCE_MAX_RETRY_AFTER_MS",
                    "V2_GRAPHQL_RESILIENCE_MAX_REPARTITIONS",
                    "V2_GRAPHQL_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD",
                    "V2_GRAPHQL_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS",
                    "V2_GRAPHQL_TOKEN",
                    "V2_SHADOW_AUDIT_ENABLED",
                    "V2_SHADOW_TARGET_KIND",
                    "V2_SHADOW_JDBC_URL",
                    "V2_SHADOW_APPROVAL_REFERENCE");

    public RuntimeConfiguration load(
            final Path configurationFile,
            final Properties systemProperties,
            final Map<String, String> environment,
            final Clock clock) {
        Objects.requireNonNull(configurationFile, "O arquivo de configuração é obrigatório.");
        Objects.requireNonNull(systemProperties, "As propriedades de sistema são obrigatórias.");
        Objects.requireNonNull(environment, "As variáveis de ambiente são obrigatórias.");
        Objects.requireNonNull(clock, "O relógio é obrigatório.");
        SecretInputPolicy.rejectSecretSystemProperties(systemProperties);

        final Properties fileProperties = readFile(configurationFile);
        SecretInputPolicy.rejectSecretFileProperties(fileProperties);
        rejectUnknownKeys(fileProperties, environment);
        final ConfigurationValues values = new ConfigurationValues(fileProperties, environment);
        rejectDormantFeatureConfiguration(values, fileProperties, environment);
        final RuntimeEnvironment environmentKind =
                RuntimeEnvironment.fromConfiguration(
                        values.required("runtime.environment", "V2_RUNTIME_ENVIRONMENT"));
        final ZoneId businessZone =
                parseIanaZone(
                        values.required(
                                "runtime.business-timezone", "V2_RUNTIME_BUSINESS_TIMEZONE"),
                        "Configuração de timezone de negócio inválida.");
        final Optional<DataExportSourceConfiguration> dataExport =
                values.booleanValue("dataexport.enabled", "V2_DATAEXPORT_ENABLED", false)
                        ? Optional.of(createDataExport(values))
                        : Optional.empty();
        final Optional<GraphQlSourceConfiguration> graphQl =
                values.booleanValue("graphql.enabled", "V2_GRAPHQL_ENABLED", false)
                        ? Optional.of(createGraphQl(values))
                        : Optional.empty();
        verifySharedEslConfiguration(dataExport, graphQl);
        final ShadowStorageProperties shadowStorage = createShadowStorage(values);
        return new RuntimeConfiguration(
                environmentKind, businessZone, clock, dataExport, graphQl, shadowStorage);
    }

    private DataExportSourceConfiguration createDataExport(final ConfigurationValues values) {
        final URI baseUri =
                parseUri(
                        values.required("dataexport.base-url", "V2_DATAEXPORT_BASE_URL"),
                        "Configuração de URL base do Data Export inválida.");
        final ZoneId sourceZone =
                parseIanaZone(
                        values.required("dataexport.timezone", "V2_DATAEXPORT_TIMEZONE"),
                        "Configuração de timezone do Data Export inválida.");
        final DataExportTransport transport =
                parseTransport(values.required("dataexport.transport", "V2_DATAEXPORT_TRANSPORT"));
        if (transport != DataExportTransport.GET_WITH_QUERY) {
            throw new IllegalArgumentException(
                    "O transporte operacional Data Export deve ser GET_WITH_QUERY.");
        }
        final DataExportClientSettings settings =
                new DataExportClientSettings(
                        baseUri,
                        sourceZone,
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "dataexport.timeout-seconds",
                                        "V2_DATAEXPORT_TIMEOUT_SECONDS",
                                        DataExportClientSettings.MAX_REQUEST_TIMEOUT.toSeconds())),
                        transport,
                        new DataExportRetryPolicy(
                                values.boundedPositiveInt(
                                        "dataexport.retry.max-attempts",
                                        "V2_DATAEXPORT_RETRY_MAX_ATTEMPTS",
                                        DataExportRetryPolicy.MAX_ATTEMPTS),
                                Duration.ofMillis(
                                        values.boundedNonNegativeLong(
                                                "dataexport.retry.initial-delay-ms",
                                                "V2_DATAEXPORT_RETRY_INITIAL_DELAY_MS",
                                                DataExportRetryPolicy.MAX_DELAY_MILLIS)),
                                Duration.ofMillis(
                                        values.boundedNonNegativeLong(
                                                "dataexport.retry.max-delay-ms",
                                                "V2_DATAEXPORT_RETRY_MAX_DELAY_MS",
                                                DataExportRetryPolicy.MAX_DELAY_MILLIS))),
                        values.boundedPositiveLong(
                                "dataexport.max-response-bytes",
                                "V2_DATAEXPORT_MAX_RESPONSE_BYTES",
                                DataExportClientSettings.MAX_RESPONSE_BYTES));
        final EslResiliencePolicy resiliencePolicy =
                new EslResiliencePolicy(
                        Duration.ofMillis(
                                values.boundedNonNegativeLong(
                                        "dataexport.resilience.minimum-request-interval-ms",
                                        "V2_DATAEXPORT_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS",
                                        EslResiliencePolicy.MAX_REQUEST_INTERVAL.toMillis())),
                        values.boundedPositiveInt(
                                "dataexport.resilience.max-in-flight",
                                "V2_DATAEXPORT_RESILIENCE_MAX_IN_FLIGHT",
                                1),
                        values.boundedPositiveInt(
                                "dataexport.resilience.max-requests-per-cycle",
                                "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_CYCLE",
                                EslResiliencePolicy.MAX_REQUESTS_PER_CYCLE),
                        values.boundedPositiveInt(
                                "dataexport.resilience.max-requests-per-workload",
                                "V2_DATAEXPORT_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD",
                                EslResiliencePolicy.MAX_REQUESTS_PER_WORKLOAD),
                        settings.requestTimeout(),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "dataexport.resilience.step-timeout-seconds",
                                        "V2_DATAEXPORT_RESILIENCE_STEP_TIMEOUT_SECONDS",
                                        ExecutionDeadlines.MAX_TIMEOUT.toSeconds())),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "dataexport.resilience.cycle-timeout-seconds",
                                        "V2_DATAEXPORT_RESILIENCE_CYCLE_TIMEOUT_SECONDS",
                                        ExecutionDeadlines.MAX_TIMEOUT.toSeconds())),
                        Duration.ofMillis(
                                values.boundedNonNegativeLong(
                                        "dataexport.resilience.max-retry-after-ms",
                                        "V2_DATAEXPORT_RESILIENCE_MAX_RETRY_AFTER_MS",
                                        DataExportRetryPolicy.MAX_DELAY_MILLIS)),
                        values.boundedNonNegativeInt(
                                "dataexport.resilience.max-repartitions",
                                "V2_DATAEXPORT_RESILIENCE_MAX_REPARTITIONS",
                                EslResiliencePolicy.MAX_REPARTITIONS),
                        values.boundedPositiveInt(
                                "dataexport.resilience.circuit-failure-threshold",
                                "V2_DATAEXPORT_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD",
                                EslResiliencePolicy.MAX_CIRCUIT_FAILURE_THRESHOLD),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "dataexport.resilience.circuit-cooldown-seconds",
                                        "V2_DATAEXPORT_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS",
                                        ExecutionDeadlines.MAX_TIMEOUT.toSeconds())));
        return new DataExportSourceConfiguration(
                values.required("dataexport.source-instance", "V2_DATAEXPORT_SOURCE_INSTANCE"),
                values.required("dataexport.tenant-scope", "V2_DATAEXPORT_TENANT_SCOPE"),
                settings,
                resiliencePolicy);
    }

    private GraphQlSourceConfiguration createGraphQl(final ConfigurationValues values) {
        final GraphQlClientSettings settings =
                new GraphQlClientSettings(
                        parseUri(
                                values.required("graphql.endpoint", "V2_GRAPHQL_ENDPOINT"),
                                "Configuração de endpoint GraphQL inválida."),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "graphql.timeout-seconds",
                                        "V2_GRAPHQL_TIMEOUT_SECONDS",
                                        GraphQlClientSettings.ABSOLUTE_MAXIMUM_REQUEST_TIMEOUT
                                                .toSeconds())),
                        new GraphQlRetryPolicy(
                                values.boundedPositiveInt(
                                        "graphql.retry.max-attempts",
                                        "V2_GRAPHQL_RETRY_MAX_ATTEMPTS",
                                        GraphQlRetryPolicy.ABSOLUTE_MAXIMUM_ATTEMPTS),
                                Duration.ofMillis(
                                        values.boundedNonNegativeLong(
                                                "graphql.retry.initial-delay-ms",
                                                "V2_GRAPHQL_RETRY_INITIAL_DELAY_MS",
                                                GraphQlRetryPolicy.ABSOLUTE_MAXIMUM_DELAY
                                                        .toMillis())),
                                Duration.ofMillis(
                                        values.boundedNonNegativeLong(
                                                "graphql.retry.max-delay-ms",
                                                "V2_GRAPHQL_RETRY_MAX_DELAY_MS",
                                                GraphQlRetryPolicy.ABSOLUTE_MAXIMUM_DELAY
                                                        .toMillis()))),
                        values.boundedPositiveLong(
                                "graphql.max-response-bytes",
                                "V2_GRAPHQL_MAX_RESPONSE_BYTES",
                                GraphQlClientSettings.ABSOLUTE_MAXIMUM_RESPONSE_BYTES));
        final EslResiliencePolicy resiliencePolicy =
                new EslResiliencePolicy(
                        Duration.ofMillis(
                                values.boundedNonNegativeLong(
                                        "graphql.resilience.minimum-request-interval-ms",
                                        "V2_GRAPHQL_RESILIENCE_MINIMUM_REQUEST_INTERVAL_MS",
                                        EslResiliencePolicy.MAX_REQUEST_INTERVAL.toMillis())),
                        values.boundedPositiveInt(
                                "graphql.resilience.max-in-flight",
                                "V2_GRAPHQL_RESILIENCE_MAX_IN_FLIGHT",
                                1),
                        values.boundedPositiveInt(
                                "graphql.resilience.max-requests-per-cycle",
                                "V2_GRAPHQL_RESILIENCE_MAX_REQUESTS_PER_CYCLE",
                                EslResiliencePolicy.MAX_REQUESTS_PER_CYCLE),
                        values.boundedPositiveInt(
                                "graphql.resilience.max-requests-per-workload",
                                "V2_GRAPHQL_RESILIENCE_MAX_REQUESTS_PER_WORKLOAD",
                                EslResiliencePolicy.MAX_REQUESTS_PER_WORKLOAD),
                        settings.requestTimeout(),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "graphql.resilience.step-timeout-seconds",
                                        "V2_GRAPHQL_RESILIENCE_STEP_TIMEOUT_SECONDS",
                                        ExecutionDeadlines.MAX_TIMEOUT.toSeconds())),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "graphql.resilience.cycle-timeout-seconds",
                                        "V2_GRAPHQL_RESILIENCE_CYCLE_TIMEOUT_SECONDS",
                                        ExecutionDeadlines.MAX_TIMEOUT.toSeconds())),
                        Duration.ofMillis(
                                values.boundedNonNegativeLong(
                                        "graphql.resilience.max-retry-after-ms",
                                        "V2_GRAPHQL_RESILIENCE_MAX_RETRY_AFTER_MS",
                                        GraphQlRetryPolicy.ABSOLUTE_MAXIMUM_DELAY.toMillis())),
                        values.boundedNonNegativeInt(
                                "graphql.resilience.max-repartitions",
                                "V2_GRAPHQL_RESILIENCE_MAX_REPARTITIONS",
                                EslResiliencePolicy.MAX_REPARTITIONS),
                        values.boundedPositiveInt(
                                "graphql.resilience.circuit-failure-threshold",
                                "V2_GRAPHQL_RESILIENCE_CIRCUIT_FAILURE_THRESHOLD",
                                EslResiliencePolicy.MAX_CIRCUIT_FAILURE_THRESHOLD),
                        Duration.ofSeconds(
                                values.boundedPositiveLong(
                                        "graphql.resilience.circuit-cooldown-seconds",
                                        "V2_GRAPHQL_RESILIENCE_CIRCUIT_COOLDOWN_SECONDS",
                                        ExecutionDeadlines.MAX_TIMEOUT.toSeconds())));
        return new GraphQlSourceConfiguration(
                values.required("graphql.source-instance", "V2_GRAPHQL_SOURCE_INSTANCE"),
                values.required("graphql.tenant-scope", "V2_GRAPHQL_TENANT_SCOPE"),
                settings,
                resiliencePolicy);
    }

    private static void verifySharedEslConfiguration(
            final Optional<DataExportSourceConfiguration> dataExport,
            final Optional<GraphQlSourceConfiguration> graphQl) {
        if (dataExport.isEmpty() || graphQl.isEmpty()) {
            return;
        }
        final DataExportSourceConfiguration dataExportSource = dataExport.orElseThrow();
        final GraphQlSourceConfiguration graphQlSource = graphQl.orElseThrow();
        if (!dataExportSource.sourceInstance().equals(graphQlSource.sourceInstance())
                || !dataExportSource.tenantScope().equals(graphQlSource.tenantScope())
                || !dataExportSource.resiliencePolicy().equals(graphQlSource.resiliencePolicy())) {
            throw new IllegalArgumentException(
                    "As fontes ESL habilitadas exigem identidade e resiliência compartilhadas.");
        }
    }

    private ShadowStorageProperties createShadowStorage(final ConfigurationValues values) {
        if (!values.booleanValue("shadow.audit.enabled", "V2_SHADOW_AUDIT_ENABLED", false)) {
            return ShadowStorageProperties.disabled();
        }
        final ShadowStorageTargetKind targetKind =
                ShadowStorageTargetKind.fromConfiguration(
                        values.required("shadow.target-kind", "V2_SHADOW_TARGET_KIND"));
        final String approvalReference =
                values.optional("shadow.approval-reference", "V2_SHADOW_APPROVAL_REFERENCE");
        return ShadowStorageProperties.enabled(
                targetKind,
                values.required("shadow.jdbc-url", "V2_SHADOW_JDBC_URL"),
                approvalReference);
    }

    private Properties readFile(final Path configurationFile) {
        final Path normalized = configurationFile.toAbsolutePath().normalize();
        final Path fileName = normalized.getFileName();
        if (fileName == null) {
            throw new IllegalArgumentException("O arquivo de configuração indicado é inválido.");
        }
        final String filename = fileName.toString().toLowerCase(Locale.ROOT);
        if (filename.equals(".env") || filename.startsWith(".env.")) {
            throw new IllegalArgumentException(
                    "Arquivos .env não são aceitos como configuração de runtime.");
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IllegalArgumentException("O arquivo de configuração indicado não existe.");
        }
        final Properties properties = new RejectingDuplicateProperties();
        try (InputStream input = Files.newInputStream(normalized)) {
            final byte[] content = input.readNBytes(MAX_CONFIGURATION_READ_BYTES);
            if (content.length > MAX_CONFIGURATION_BYTES) {
                throw new IllegalArgumentException(
                        "O arquivo de configuração excede o limite permitido.");
            }
            try (Reader reader =
                    new InputStreamReader(
                            new ByteArrayInputStream(content), StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            return properties;
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível carregar o arquivo de configuração.", exception);
        }
    }

    private static void rejectUnknownKeys(
            final Properties fileProperties, final Map<String, String> environment) {
        if (!FILE_KEYS.containsAll(fileProperties.stringPropertyNames())) {
            throw new IllegalArgumentException(
                    "O arquivo contém uma propriedade de runtime desconhecida.");
        }
        final boolean unknownEnvironmentKey =
                environment.keySet().stream()
                        .anyMatch(key -> key.startsWith("V2_") && !ENVIRONMENT_KEYS.contains(key));
        if (unknownEnvironmentKey) {
            throw new IllegalArgumentException(
                    "O ambiente contém uma variável de runtime desconhecida.");
        }
    }

    private static void rejectDormantFeatureConfiguration(
            final ConfigurationValues values,
            final Properties fileProperties,
            final Map<String, String> environment) {
        if (!values.booleanValue("dataexport.enabled", "V2_DATAEXPORT_ENABLED", false)
                && (hasSubordinateFileKey(fileProperties, "dataexport.", "dataexport.enabled")
                        || hasSubordinateEnvironmentKey(
                                environment, "V2_DATAEXPORT_", "V2_DATAEXPORT_ENABLED"))) {
            throw new IllegalArgumentException(
                    "Configuração Data Export dormente não é permitida.");
        }
        if (!values.booleanValue("shadow.audit.enabled", "V2_SHADOW_AUDIT_ENABLED", false)
                && (hasSubordinateFileKey(fileProperties, "shadow.", "shadow.audit.enabled")
                        || hasSubordinateEnvironmentKey(
                                environment, "V2_SHADOW_", "V2_SHADOW_AUDIT_ENABLED"))) {
            throw new IllegalArgumentException(
                    "Configuração de armazenamento dormente não é permitida.");
        }
        if (!values.booleanValue("graphql.enabled", "V2_GRAPHQL_ENABLED", false)
                && (hasSubordinateFileKey(fileProperties, "graphql.", "graphql.enabled")
                        || hasSubordinateEnvironmentKey(
                                environment, "V2_GRAPHQL_", "V2_GRAPHQL_ENABLED"))) {
            throw new IllegalArgumentException("Configuração GraphQL dormente não é permitida.");
        }
    }

    private static boolean hasSubordinateFileKey(
            final Properties properties, final String prefix, final String enableKey) {
        return properties.stringPropertyNames().stream()
                .anyMatch(
                        key ->
                                key.startsWith(prefix)
                                        && !enableKey.equals(key)
                                        && !properties.getProperty(key, "").isBlank());
    }

    private static boolean hasSubordinateEnvironmentKey(
            final Map<String, String> environment, final String prefix, final String enableKey) {
        return environment.entrySet().stream()
                .anyMatch(
                        entry ->
                                entry.getKey().startsWith(prefix)
                                        && !enableKey.equals(entry.getKey())
                                        && entry.getValue() != null
                                        && !entry.getValue().isBlank());
    }

    private static URI parseUri(final String value, final String message) {
        try {
            return URI.create(value);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private static ZoneId parseIanaZone(final String value, final String message) {
        try {
            final ZoneId zoneId = ZoneId.of(value);
            if (!ZoneId.getAvailableZoneIds().contains(zoneId.getId())) {
                throw new IllegalArgumentException(message);
            }
            return zoneId;
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(message);
        }
    }

    private static DataExportTransport parseTransport(final String value) {
        try {
            return DataExportTransport.parse(value);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Configuração de transporte do Data Export inválida.");
        }
    }

    private static final class ConfigurationValues {

        private final Properties fileProperties;
        private final Map<String, String> environment;

        private ConfigurationValues(
                final Properties fileProperties, final Map<String, String> environment) {
            this.fileProperties = fileProperties;
            this.environment = environment;
        }

        private String required(final String fileKey, final String environmentKey) {
            final String value = optional(fileKey, environmentKey);
            if (value == null) {
                throw new IllegalStateException("Configuração obrigatória ausente.");
            }
            return value;
        }

        private String optional(final String fileKey, final String environmentKey) {
            final String environmentValue = environment.get(environmentKey);
            if (environmentValue != null && !environmentValue.isBlank()) {
                return environmentValue.trim();
            }
            final String fileValue = fileProperties.getProperty(fileKey);
            if (fileValue != null && !fileValue.isBlank()) {
                return fileValue.trim();
            }
            return null;
        }

        private boolean booleanValue(
                final String fileKey, final String environmentKey, final boolean defaultValue) {
            final String value = optional(fileKey, environmentKey);
            if (value == null) {
                return defaultValue;
            }
            if (!value.matches("[A-Za-z]+")) {
                throw new IllegalArgumentException("Configuração booleana inválida.");
            }
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
            if ("false".equalsIgnoreCase(value)) {
                return false;
            }
            throw new IllegalArgumentException("Configuração booleana inválida.");
        }

        private long boundedPositiveLong(
                final String fileKey, final String environmentKey, final long maximum) {
            final long value = parseLong(required(fileKey, environmentKey));
            if (value <= 0 || value > maximum) {
                throw new IllegalArgumentException("Configuração numérica fora do limite.");
            }
            return value;
        }

        private int boundedPositiveInt(
                final String fileKey, final String environmentKey, final int maximum) {
            return Math.toIntExact(boundedPositiveLong(fileKey, environmentKey, maximum));
        }

        private long boundedNonNegativeLong(
                final String fileKey, final String environmentKey, final long maximum) {
            final long value = parseLong(required(fileKey, environmentKey));
            if (value < 0 || value > maximum) {
                throw new IllegalArgumentException("Configuração numérica fora do limite.");
            }
            return value;
        }

        private int boundedNonNegativeInt(
                final String fileKey, final String environmentKey, final int maximum) {
            return Math.toIntExact(boundedNonNegativeLong(fileKey, environmentKey, maximum));
        }

        private long parseLong(final String value) {
            try {
                return Long.parseLong(value);
            } catch (final NumberFormatException exception) {
                throw new IllegalArgumentException("Configuração numérica inválida.");
            }
        }
    }

    private static final class RejectingDuplicateProperties extends Properties {

        private static final long serialVersionUID = 1L;

        @Override
        public synchronized Object put(final Object key, final Object value) {
            if (containsKey(key)) {
                throw new IllegalArgumentException("O arquivo contém uma propriedade duplicada.");
            }
            return super.put(key, value);
        }
    }
}
