package br.com.esl.etl.v2.contratos;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Configuração exclusiva da suíte de contratos remotos.
 *
 * <p>Ela aceita somente variáveis {@code CONTRACT_*}; em particular, nunca herda as variáveis
 * operacionais {@code API_*}. Os valores secretos e os endpoints não são representados em {@link
 * #toString()}.
 */
public final class ContractTestConfiguration {

    private static final int DEFAULT_TIMEOUT_SECONDS = 30;
    private static final long DEFAULT_MAX_RESPONSE_BYTES = 10L * 1024L * 1024L;
    private final URI dataExportBaseUri;
    private final String dataExportToken;
    private final URI graphQlEndpoint;
    private final String graphQlToken;
    private final ZoneId sourceZone;
    private final Duration requestTimeout;
    private final Map<DataExportTemplate, Integer> maxCallsByTemplate;
    private final int firstApprovedPageSize;
    private final int secondApprovedPageSize;
    private final DataExportTransport approvedTransport;
    private final long maxResponseBytes;
    private final ContractEntityWindows coletasWindows;
    private final ContractEntityWindows fretesWindows;
    private final boolean sourceTimezoneFormallyConfirmed;
    private final Map<DataExportTemplate, ContractTemplateConfirmation> confirmationsByTemplate;
    private final Optional<Contract4924Configuration> auxiliary4924;

    private ContractTestConfiguration(
            final URI dataExportBaseUri,
            final String dataExportToken,
            final URI graphQlEndpoint,
            final String graphQlToken,
            final ZoneId sourceZone,
            final Duration requestTimeout,
            final Map<DataExportTemplate, Integer> maxCallsByTemplate,
            final int firstApprovedPageSize,
            final int secondApprovedPageSize,
            final DataExportTransport approvedTransport,
            final long maxResponseBytes,
            final ContractEntityWindows coletasWindows,
            final ContractEntityWindows fretesWindows,
            final boolean sourceTimezoneFormallyConfirmed,
            final Map<DataExportTemplate, ContractTemplateConfirmation> confirmationsByTemplate,
            final Optional<Contract4924Configuration> auxiliary4924) {
        this.dataExportBaseUri = dataExportBaseUri;
        this.dataExportToken = dataExportToken;
        this.graphQlEndpoint = graphQlEndpoint;
        this.graphQlToken = graphQlToken;
        this.sourceZone = sourceZone;
        this.requestTimeout = requestTimeout;
        this.maxCallsByTemplate = Map.copyOf(maxCallsByTemplate);
        this.firstApprovedPageSize = firstApprovedPageSize;
        this.secondApprovedPageSize = secondApprovedPageSize;
        this.approvedTransport = approvedTransport;
        this.maxResponseBytes = maxResponseBytes;
        this.coletasWindows = coletasWindows;
        this.fretesWindows = fretesWindows;
        this.sourceTimezoneFormallyConfirmed = sourceTimezoneFormallyConfirmed;
        this.confirmationsByTemplate = Map.copyOf(confirmationsByTemplate);
        this.auxiliary4924 =
                Objects.requireNonNull(
                        auxiliary4924, "A configuração auxiliar 4924 é obrigatória.");
    }

    public static ContractTestConfiguration fromEnvironment() {
        return from(System.getenv());
    }

    public static boolean hasAnyRemoteEnvironmentConfiguration() {
        return hasAnyRemoteEnvironmentConfiguration(System.getenv());
    }

    static boolean hasAnyRemoteEnvironmentConfiguration(final Map<String, String> environment) {
        Objects.requireNonNull(environment, "As variáveis de ambiente são obrigatórias.");
        return environment.entrySet().stream()
                .anyMatch(
                        entry ->
                                entry.getKey().startsWith("CONTRACT_")
                                        && !("CONTRACT_4924_ENABLED".equals(entry.getKey())
                                                && entry.getValue() != null
                                                && "false"
                                                        .equalsIgnoreCase(
                                                                entry.getValue().trim())));
    }

    static ContractTestConfiguration from(final Map<String, String> environment) {
        Objects.requireNonNull(environment, "As variáveis de ambiente são obrigatórias.");
        final URI dataExportBaseUri = requiredEndpoint(environment, "CONTRACT_DATAEXPORT_BASE_URL");
        final String dataExportToken = required(environment, "CONTRACT_DATAEXPORT_TOKEN");
        final URI graphQlEndpoint = requiredEndpoint(environment, "CONTRACT_GRAPHQL_URL");
        final String graphQlToken = required(environment, "CONTRACT_GRAPHQL_TOKEN");
        final ZoneId sourceZone = requiredZone(environment, "CONTRACT_SOURCE_TIMEZONE");
        final Duration requestTimeout =
                Duration.ofSeconds(
                        optionalPositiveInteger(
                                environment,
                                "CONTRACT_REQUEST_TIMEOUT_SECONDS",
                                DEFAULT_TIMEOUT_SECONDS));
        final Map<DataExportTemplate, Integer> maxCallsByTemplate = maxCallsByTemplate(environment);
        final int firstPageSize = requiredPositiveInteger(environment, "CONTRACT_PAGE_SIZE_A");
        final int secondPageSize = requiredPositiveInteger(environment, "CONTRACT_PAGE_SIZE_B");
        final DataExportTransport approvedTransport = requiredTransport(environment);
        if (firstPageSize == secondPageSize) {
            throw new IllegalArgumentException(
                    "CONTRACT_PAGE_SIZE_A e CONTRACT_PAGE_SIZE_B devem ser distintos.");
        }
        final long maxResponseBytes =
                optionalPositiveLong(
                        environment, "CONTRACT_MAX_RESPONSE_BYTES", DEFAULT_MAX_RESPONSE_BYTES);
        return new ContractTestConfiguration(
                dataExportBaseUri,
                dataExportToken,
                graphQlEndpoint,
                graphQlToken,
                sourceZone,
                requestTimeout,
                maxCallsByTemplate,
                firstPageSize,
                secondPageSize,
                approvedTransport,
                maxResponseBytes,
                entityWindows(environment, "6908"),
                entityWindows(environment, "6389"),
                optionalBoolean(environment, "CONTRACT_SOURCE_TIMEZONE_FORMALLY_CONFIRMED", false),
                confirmationsByTemplate(environment),
                Contract4924Configuration.optionalFrom(environment));
    }

    public DataExportProperties dataExportProperties() {
        return new DataExportProperties(
                dataExportBaseUri,
                dataExportToken,
                sourceZone,
                requestTimeout,
                approvedTransport,
                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                maxResponseBytes);
    }

    public URI graphQlEndpoint() {
        return graphQlEndpoint;
    }

    public String graphQlToken() {
        return graphQlToken;
    }

    public Duration requestTimeout() {
        return requestTimeout;
    }

    /** Teto total compartilhado entre Data Export e GraphQL para a entidade do template. */
    public int maxCallsFor(final DataExportTemplate template) {
        final Integer maximumCalls =
                maxCallsByTemplate.get(
                        Objects.requireNonNull(template, "O template Data Export é obrigatório."));
        if (maximumCalls == null) {
            throw new IllegalArgumentException(
                    "O template Data Export não pertence à suíte de contrato.");
        }
        return maximumCalls;
    }

    public int firstApprovedPageSize() {
        return firstApprovedPageSize;
    }

    public int secondApprovedPageSize() {
        return secondApprovedPageSize;
    }

    public DataExportTransport approvedTransport() {
        return approvedTransport;
    }

    public long maxResponseBytes() {
        return maxResponseBytes;
    }

    public ContractEntityWindows windowsFor(final DataExportTemplate template) {
        return switch (Objects.requireNonNull(template, "O template Data Export é obrigatório.")) {
            case COLETAS -> coletasWindows;
            case FRETES -> fretesWindows;
            default -> throw new IllegalArgumentException("FIRST_WAVE_PROBE_ONLY");
        };
    }

    public boolean sourceTimezoneFormallyConfirmed() {
        return sourceTimezoneFormallyConfirmed;
    }

    public ContractTemplateConfirmation confirmationsFor(final DataExportTemplate template) {
        final ContractTemplateConfirmation confirmation =
                confirmationsByTemplate.get(
                        Objects.requireNonNull(template, "O template Data Export é obrigatório."));
        if (confirmation == null) {
            throw new IllegalArgumentException(
                    "O template Data Export não pertence à suíte de contrato.");
        }
        return confirmation;
    }

    /** Sonda 4924 transitória, presente apenas quando toda a configuração auxiliar está válida. */
    public Optional<Contract4924Configuration> auxiliary4924() {
        return auxiliary4924;
    }

    @Override
    public String toString() {
        return "ContractTestConfiguration[redacted]";
    }

    private static URI requiredEndpoint(final Map<String, String> environment, final String key) {
        final String configuredValue = required(environment, key);
        try {
            return validateEndpoint(URI.create(configuredValue));
        } catch (final IllegalArgumentException exception) {
            throw new IllegalArgumentException("Configuração de endpoint inválida: " + key + ".");
        }
    }

    private static ZoneId requiredZone(final Map<String, String> environment, final String key) {
        final String configuredValue = required(environment, key);
        try {
            final ZoneId zoneId = ZoneId.of(configuredValue);
            if (!ZoneId.getAvailableZoneIds().contains(zoneId.getId())) {
                throw new IllegalArgumentException("Timezone sem identificador IANA.");
            }
            return zoneId;
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException("Configuração de timezone inválida: " + key + ".");
        }
    }

    private static DataExportTransport requiredTransport(final Map<String, String> environment) {
        final String configuredValue = required(environment, "CONTRACT_DATAEXPORT_TRANSPORT");
        try {
            return DataExportTransport.parse(configuredValue);
        } catch (final RuntimeException exception) {
            throw new IllegalArgumentException(
                    "Configuração de transporte inválida: CONTRACT_DATAEXPORT_TRANSPORT.");
        }
    }

    private static int requiredPositiveInteger(
            final Map<String, String> environment, final String key) {
        return parsePositiveInteger(required(environment, key), key);
    }

    private static Map<DataExportTemplate, Integer> maxCallsByTemplate(
            final Map<String, String> environment) {
        final Map<DataExportTemplate, Integer> maximumCalls =
                new EnumMap<>(DataExportTemplate.class);
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            final String key = "CONTRACT_" + template.templateId() + "_MAX_CALLS";
            maximumCalls.put(template, requiredPositiveInteger(environment, key));
        }
        return Map.copyOf(maximumCalls);
    }

    private static int optionalPositiveInteger(
            final Map<String, String> environment, final String key, final int defaultValue) {
        final String configuredValue = environment.get(key);
        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultValue;
        }
        return parsePositiveInteger(configuredValue.trim(), key);
    }

    private static long optionalPositiveLong(
            final Map<String, String> environment, final String key, final long defaultValue) {
        final String configuredValue = environment.get(key);
        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultValue;
        }
        try {
            final long parsed = Long.parseLong(configuredValue.trim());
            if (parsed <= 0) {
                throw new IllegalArgumentException("Configuração deve ser positiva: " + key + ".");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Configuração numérica inválida: " + key + ".");
        }
    }

    private static boolean optionalBoolean(
            final Map<String, String> environment, final String key, final boolean defaultValue) {
        final String configuredValue = environment.get(key);
        if (configuredValue == null || configuredValue.isBlank()) {
            return defaultValue;
        }
        final String normalized = configuredValue.trim();
        if (!"true".equalsIgnoreCase(normalized) && !"false".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Configuração booleana inválida: " + key + ".");
        }
        return Boolean.parseBoolean(normalized);
    }

    private static Map<DataExportTemplate, ContractTemplateConfirmation> confirmationsByTemplate(
            final Map<String, String> environment) {
        final Map<DataExportTemplate, ContractTemplateConfirmation> confirmations =
                new EnumMap<>(DataExportTemplate.class);
        for (final DataExportTemplate template :
                java.util.List.of(DataExportTemplate.COLETAS, DataExportTemplate.FRETES)) {
            final String prefix = "CONTRACT_" + template.templateId() + "_";
            confirmations.put(
                    template,
                    new ContractTemplateConfirmation(
                            optionalBoolean(
                                    environment, prefix + "ORDER_TOTAL_OR_CURSOR_CONFIRMED", false),
                            optionalBoolean(environment, prefix + "COVERAGE_CONFIRMED", false)));
        }
        return Map.copyOf(confirmations);
    }

    private static ContractEntityWindows entityWindows(
            final Map<String, String> environment, final String templateId) {
        final String prefix = "CONTRACT_" + templateId + "_";
        return new ContractEntityWindows(
                ContractDateWindow.parse(
                        required(environment, prefix + "POPULATED_WINDOW"),
                        prefix + "POPULATED_WINDOW"),
                ContractDateWindow.parse(
                        required(environment, prefix + "EMPTY_WINDOW"), prefix + "EMPTY_WINDOW"),
                ContractDateWindow.parse(
                        required(environment, prefix + "LATE_CHANGE_BUSINESS_WINDOW"),
                        prefix + "LATE_CHANGE_BUSINESS_WINDOW"),
                ContractInstantWindow.parse(
                        required(environment, prefix + "LATE_CHANGE_UPDATED_AT_WINDOW"),
                        prefix + "LATE_CHANGE_UPDATED_AT_WINDOW"));
    }

    private static int parsePositiveInteger(final String value, final String key) {
        try {
            final int parsed = Integer.parseInt(value);
            if (parsed <= 0) {
                throw new IllegalArgumentException("Configuração deve ser positiva: " + key + ".");
            }
            return parsed;
        } catch (final NumberFormatException exception) {
            throw new IllegalArgumentException("Configuração numérica inválida: " + key + ".");
        }
    }

    private static String required(final Map<String, String> environment, final String key) {
        final String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Configuração obrigatória ausente: " + key + ".");
        }
        return value.trim();
    }

    private static URI validateEndpoint(final URI value) {
        if (!value.isAbsolute() || value.getHost() == null || value.getHost().isBlank()) {
            throw new IllegalArgumentException("Endpoint deve ser absoluto e conter host.");
        }
        if (value.getUserInfo() != null
                || value.getRawQuery() != null
                || value.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Endpoint não pode conter credenciais, query ou fragmento.");
        }
        if ("https".equalsIgnoreCase(value.getScheme())) {
            return value;
        }
        if ("http".equalsIgnoreCase(value.getScheme()) && isLoopbackHost(value.getHost())) {
            return value;
        }
        throw new IllegalArgumentException("Endpoint deve usar HTTPS fora de loopback.");
    }

    private static boolean isLoopbackHost(final String host) {
        final String normalizedHost = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "[::1]".equals(normalizedHost);
    }
}
