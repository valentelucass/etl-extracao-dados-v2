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

    private ShadowStorageProperties(
            final boolean auditEnabled,
            final ShadowStorageTargetKind targetKind,
            final String jdbcUrl,
            final String approvalReference) {
        this.auditEnabled = auditEnabled;
        this.targetKind = targetKind;
        this.jdbcUrl = jdbcUrl;
        this.approvalReference = approvalReference;
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
        if (!EXPECTED_DATABASE.equalsIgnoreCase(parameters.get("databasename"))) {
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
            final String key = segment.substring(0, separator).trim().toLowerCase(Locale.ROOT);
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
        if (value != null && !"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
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
