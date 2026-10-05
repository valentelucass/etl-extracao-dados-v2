package br.com.esl.etl.v2.plataforma.configuracao;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configuração opt-in para a auditoria em armazenamento de sombra.
 *
 * <p>Esta classe nunca abre conexão. O runtime permanece sem escrita até uma composição receber
 * explicitamente um {@code DataSource} aprovado.
 */
public final class ShadowStorageProperties {

    private static final String EXPECTED_DATABASE = "ETL_SISTEMA_V2_SHADOW";
    private static final String JDBC_SQL_SERVER_PREFIX = "jdbc:sqlserver://";
    private static final Set<String> ALLOWED_JDBC_PROPERTIES =
            Set.of(
                    "databasename",
                    "integratedsecurity",
                    "encrypt",
                    "trustservercertificate",
                    "applicationname",
                    "logintimeout",
                    "sockettimeout");

    private final boolean auditEnabled;
    private final ShadowStorageTargetKind targetKind;
    private final String jdbcUrl;
    private final String approvalReference;
    private final boolean sampleSaAuthentication;
    private final boolean sampleSqlPinAuthentication;

    private ShadowStorageProperties(
            final boolean auditEnabled,
            final ShadowStorageTargetKind targetKind,
            final String jdbcUrl,
            final String approvalReference) {
        this(auditEnabled, targetKind, jdbcUrl, approvalReference, false);
    }

    private ShadowStorageProperties(
            final boolean auditEnabled,
            final ShadowStorageTargetKind targetKind,
            final String jdbcUrl,
            final String approvalReference,
            final boolean sampleSaAuthentication) {
        this(auditEnabled, targetKind, jdbcUrl, approvalReference, sampleSaAuthentication, false);
    }

    private ShadowStorageProperties(
            final boolean auditEnabled,
            final ShadowStorageTargetKind targetKind,
            final String jdbcUrl,
            final String approvalReference,
            final boolean sampleSaAuthentication,
            final boolean sampleSqlPinAuthentication) {
        this.auditEnabled = auditEnabled;
        this.targetKind = targetKind;
        this.jdbcUrl = jdbcUrl;
        this.approvalReference = approvalReference;
        this.sampleSaAuthentication = sampleSaAuthentication;
        this.sampleSqlPinAuthentication = sampleSqlPinAuthentication;
    }

    public static ShadowStorageProperties disabled() {
        return new ShadowStorageProperties(false, null, null, null);
    }

    public static ShadowStorageProperties enabled(
            final ShadowStorageTargetKind targetKind,
            final String jdbcUrl,
            final String approvalReference) {
        Objects.requireNonNull(targetKind, "O tipo de alvo de sombra é obrigatório.");
        Objects.requireNonNull(jdbcUrl, "O JDBC do armazenamento de sombra é obrigatório.");
        final String normalizedJdbcUrl = jdbcUrl.trim();
        validateJdbcUrl(normalizedJdbcUrl, targetKind);
        if (targetKind == ShadowStorageTargetKind.APPROVED_NON_PRODUCTION
                && (approvalReference == null || approvalReference.isBlank())) {
            throw new IllegalStateException(
                    "A referência de aprovação do alvo de sombra é obrigatória.");
        }
        return new ShadowStorageProperties(
                true,
                targetKind,
                normalizedJdbcUrl,
                approvalReference == null ? null : approvalReference.trim());
    }

    public boolean auditEnabled() {
        return auditEnabled;
    }

    /** Separate SAMPLE-only SQL-auth target; the Windows factory retains its original policy. */
    public static ShadowStorageProperties enabledSampleSa(final String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "O JDBC do SAMPLE local é obrigatório.");
        final String normalized = jdbcUrl.trim();
        if (!normalized.matches("(?i)jdbc:sqlserver://localhost(?::1433)?;.*")) {
            throw new IllegalArgumentException("COL_SAMPLE_SA_LOCAL_TARGET_REQUIRED");
        }
        final Map<String, String> parameters = parseJdbcParameters(normalized);
        if (!EXPECTED_DATABASE.equals(parameters.get("databasename"))
                || !"false".equalsIgnoreCase(parameters.get("integratedsecurity"))
                || !"true".equalsIgnoreCase(parameters.get("encrypt"))
                || !"false".equalsIgnoreCase(parameters.get("trustservercertificate"))) {
            throw new IllegalArgumentException("COL_SAMPLE_SA_VERIFIED_TARGET_REQUIRED");
        }
        validateOptionalBoolean(parameters, "integratedsecurity");
        validateOptionalBoolean(parameters, "encrypt");
        validateOptionalBoolean(parameters, "trustservercertificate");
        for (final String key : Set.of("logintimeout", "sockettimeout")) {
            final String value = parameters.get(key);
            if (value != null && !value.matches("[0-9]{1,9}")) {
                throw new IllegalArgumentException("COL_SAMPLE_SA_DRIVER_SETTING_INVALID");
            }
        }
        final String application = parameters.get("applicationname");
        if (application != null && !application.matches("[A-Za-z0-9 _.\\-]{1,128}")) {
            throw new IllegalArgumentException("COL_SAMPLE_SA_DRIVER_SETTING_INVALID");
        }
        return new ShadowStorageProperties(
                true, ShadowStorageTargetKind.LOCAL_EPHEMERAL, normalized, null, true);
    }

    public boolean usesSampleSaAuthentication() {
        requireEnabled();
        return sampleSaAuthentication;
    }

    /** Manual leaf pin and DNS exception apply only to this separately selected SQL SAMPLE mode. */
    public static ShadowStorageProperties enabledSampleSqlPin(
            final String jdbcUrl, final String manualApprovalReference) {
        if (!"HERMES325fac56a833".equals(manualApprovalReference)) {
            throw new IllegalArgumentException("COL_SAMPLE_SQL_PIN_MANUAL_APPROVAL_REQUIRED");
        }
        final ShadowStorageProperties strict = enabledSampleSa(jdbcUrl);
        return new ShadowStorageProperties(
                true, strict.targetKind, strict.jdbcUrl, manualApprovalReference, true, true);
    }

    public boolean usesSampleSqlPinAuthentication() {
        requireEnabled();
        return sampleSqlPinAuthentication;
    }

    public ShadowStorageTargetKind targetKind() {
        requireEnabled();
        return targetKind;
    }

    /** Retorna a URL somente para a composição de infraestrutura; nunca a registre em log. */
    public String jdbcUrl() {
        requireEnabled();
        return jdbcUrl;
    }

    /** Indica se a URL usa autenticação integrada; não registra a URL em nenhum ponto. */
    public boolean usesIntegratedSecurity() {
        requireEnabled();
        return hasIntegratedSecurity(jdbcUrl);
    }

    public String approvalReference() {
        requireEnabled();
        return approvalReference;
    }

    @Override
    public String toString() {
        return "ShadowStorageProperties[auditEnabled="
                + auditEnabled
                + ", targetKind="
                + (targetKind == null ? "<disabled>" : targetKind)
                + ", jdbcUrl=<redacted>, approvalReference=<redacted>]";
    }

    private static void validateJdbcUrl(
            final String jdbcUrl, final ShadowStorageTargetKind targetKind) {
        final String normalized = jdbcUrl;
        if (!normalized.regionMatches(
                true, 0, JDBC_SQL_SERVER_PREFIX, 0, JDBC_SQL_SERVER_PREFIX.length())) {
            throw new IllegalArgumentException("Configuração de JDBC de sombra inválida.");
        }

        final Map<String, String> parameters = parseJdbcParameters(normalized);
        if (!EXPECTED_DATABASE.equals(parameters.get("databasename"))) {
            throw new IllegalArgumentException(
                    "O armazenamento de sombra deve usar o banco isolado aprovado.");
        }
        if (!"true".equalsIgnoreCase(parameters.get("integratedsecurity"))) {
            throw new IllegalArgumentException(
                    "O armazenamento de sombra exige autenticação integrada.");
        }
        validateOptionalBoolean(parameters, "encrypt");
        validateOptionalBoolean(parameters, "trustservercertificate");

        if (targetKind == ShadowStorageTargetKind.APPROVED_NON_PRODUCTION
                && (!"true".equalsIgnoreCase(parameters.get("encrypt"))
                        || !"false".equalsIgnoreCase(parameters.get("trustservercertificate")))) {
            throw new IllegalArgumentException(
                    "O alvo de sombra aprovado exige TLS com certificado validado.");
        }

        if (targetKind == ShadowStorageTargetKind.LOCAL_EPHEMERAL
                && !isLoopbackEndpoint(normalized)) {
            throw new IllegalArgumentException(
                    "O alvo local de sombra deve usar um endpoint de loopback.");
        }
    }

    private static Map<String, String> parseJdbcParameters(final String jdbcUrl) {
        final int authorityStart = JDBC_SQL_SERVER_PREFIX.length();
        final int parametersStart = jdbcUrl.indexOf(';', authorityStart);
        if (parametersStart <= authorityStart) {
            throw new IllegalArgumentException("Configuração de JDBC de sombra inválida.");
        }
        final Map<String, String> parameters = new HashMap<>();
        final String[] segments = jdbcUrl.substring(parametersStart + 1).split(";", -1);
        for (int index = 0; index < segments.length; index++) {
            final String segment = segments[index].trim();
            if (segment.isEmpty() && index == segments.length - 1) {
                continue;
            }
            final int separator = segment.indexOf('=');
            if (separator <= 0 || separator == segment.length() - 1) {
                throw new IllegalArgumentException("Configuração de JDBC de sombra inválida.");
            }
            final String rawKey = segment.substring(0, separator).trim();
            if (!rawKey.matches("[A-Za-z]+")) {
                throw new IllegalArgumentException("Configuração de JDBC de sombra inválida.");
            }
            final String key = rawKey.toLowerCase(Locale.ROOT);
            final String value = segment.substring(separator + 1).trim();
            if (!ALLOWED_JDBC_PROPERTIES.contains(key)
                    || value.isEmpty()
                    || parameters.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Configuração de JDBC de sombra inválida.");
            }
        }
        return Map.copyOf(parameters);
    }

    private static void validateOptionalBoolean(
            final Map<String, String> parameters, final String key) {
        final String value = parameters.get(key);
        if (value != null
                && (!value.matches("[A-Za-z]+")
                        || (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)))) {
            throw new IllegalArgumentException("Configuração de JDBC de sombra inválida.");
        }
    }

    private static boolean isLoopbackEndpoint(final String jdbcUrl) {
        final int authorityStart = JDBC_SQL_SERVER_PREFIX.length();
        final int parametersStart = jdbcUrl.indexOf(';', authorityStart);
        if (parametersStart <= authorityStart) {
            return false;
        }
        final String authority = jdbcUrl.substring(authorityStart, parametersStart);
        final String host;
        if (authority.startsWith("[")) {
            final int closingBracket = authority.indexOf(']');
            if (closingBracket < 0) {
                return false;
            }
            host = authority.substring(1, closingBracket);
        } else {
            final int portSeparator = authority.indexOf(':');
            host = portSeparator < 0 ? authority : authority.substring(0, portSeparator);
        }
        final String normalizedHost = host.trim().toLowerCase(Locale.ROOT);
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost);
    }

    private static boolean hasIntegratedSecurity(final String jdbcUrl) {
        return "true".equalsIgnoreCase(parseJdbcParameters(jdbcUrl).get("integratedsecurity"));
    }

    private void requireEnabled() {
        if (!auditEnabled) {
            throw new IllegalStateException("A auditoria de sombra não está habilitada.");
        }
    }
}
