package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageTargetKind;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** No credentials or executable connection text are accepted from a configuration document. */
public record QualificationConfiguration(
        int querySeconds,
        int socketMillis,
        int caseSeconds,
        int campaignSeconds,
        int heapMiB,
        int maximumRows,
        int maximumJdbcCalls,
        int maximumBytes) {
    public static QualificationConfiguration read(final Path path) throws IOException {
        return parse(QualificationJson.read(path, 8192));
    }

    public static QualificationConfiguration parse(final JsonNode json) {
        QualificationJson.fields(
                json,
                "version",
                "host",
                "database",
                "schemaVersion",
                "syntheticOnly",
                "rollbackOnly",
                "profileActive",
                "integrationEnabled",
                "querySeconds",
                "socketMillis",
                "caseSeconds",
                "campaignSeconds",
                "heapMiB",
                "maximumRows",
                "maximumJdbcCalls",
                "maximumBytes");
        if (!"qualification-config-v1".equals(QualificationJson.text(json, "version", 40))
                || !"localhost".equals(QualificationJson.text(json, "host", 32))
                || !"ETL_SISTEMA_V2_SHADOW".equals(QualificationJson.text(json, "database", 64))
                || QualificationJson.number(
                                json,
                                "schemaVersion",
                                QualifiedPackage.SCHEMA_VERSION,
                                QualifiedPackage.SCHEMA_VERSION)
                        != QualifiedPackage.SCHEMA_VERSION
                || !QualificationJson.flag(json, "syntheticOnly")
                || !QualificationJson.flag(json, "rollbackOnly")
                || !QualificationJson.flag(json, "profileActive")
                || !QualificationJson.flag(json, "integrationEnabled")) {
            throw new IllegalArgumentException("QUAL_CONFIG_SCOPE");
        }
        final var result =
                new QualificationConfiguration(
                        QualificationJson.number(json, "querySeconds", 60, 60),
                        QualificationJson.number(json, "socketMillis", 2000, 120000),
                        QualificationJson.number(json, "caseSeconds", 60, 300),
                        QualificationJson.number(json, "campaignSeconds", 60, 3600),
                        QualificationJson.number(json, "heapMiB", 256, 512),
                        QualificationJson.number(json, "maximumRows", 2, 4096),
                        QualificationJson.number(json, "maximumJdbcCalls", 100, 100000),
                        QualificationJson.number(json, "maximumBytes", 1024, 67108864));
        if (result.socketMillis() <= result.querySeconds() * 1000
                || result.caseSeconds() * 1000 <= result.socketMillis()
                || result.campaignSeconds() < result.caseSeconds()) {
            throw new IllegalArgumentException("QUAL_CONFIG_TIMEOUT_ORDER");
        }
        return result;
    }

    /** Only the process environment can supply a physical target; never persist or print it. */
    public String jdbcUrl() {
        return validatedJdbcUrl(System.getenv("V2_SHADOW_JDBC_URL"));
    }

    public String validatedJdbcUrl(final String candidate) {
        if (candidate == null || candidate.length() > 512 || candidate.isBlank()) {
            throw new IllegalArgumentException("QUAL_SHADOW_URL_REQUIRED");
        }
        final String prefix = "jdbc:sqlserver://localhost;";
        if (!candidate.startsWith(prefix)
                || candidate.endsWith(";")
                || candidate.indexOf('\n') >= 0
                || candidate.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("QUAL_SHADOW_URL_INVALID");
        }
        final Set<String> allowed =
                Set.of(
                        "databasename",
                        "integratedsecurity",
                        "encrypt",
                        "trustservercertificate",
                        "logintimeout",
                        "sockettimeout");
        final Map<String, String> values = new HashMap<>();
        for (final String segment : candidate.substring(prefix.length()).split(";", -1)) {
            final int equals = segment.indexOf('=');
            if (equals <= 0
                    || equals == segment.length() - 1
                    || segment.indexOf('=', equals + 1) >= 0) {
                throw new IllegalArgumentException("QUAL_SHADOW_URL_INVALID");
            }
            final String key = segment.substring(0, equals).toLowerCase(Locale.ROOT);
            final String value = segment.substring(equals + 1);
            if (!allowed.contains(key)
                    || !segment.substring(0, equals).matches("[A-Za-z]+")
                    || !value.matches("[A-Za-z0-9_]+")
                    || values.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("QUAL_SHADOW_URL_INVALID");
            }
        }
        if (!"ETL_SISTEMA_V2_SHADOW".equals(values.get("databasename"))
                || !"true".equals(values.get("integratedsecurity"))
                || !"true".equals(values.get("encrypt"))
                || !("true".equals(values.get("trustservercertificate"))
                        || "false".equals(values.get("trustservercertificate")))) {
            throw new IllegalArgumentException("QUAL_SHADOW_URL_INVALID");
        }
        final int login = boundedTimeout(values.get("logintimeout"), 1, 5);
        final int socket = boundedTimeout(values.get("sockettimeout"), 2000, socketMillis());
        final String validated =
                prefix
                        + "databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;"
                        + "trustServerCertificate="
                        + values.get("trustservercertificate")
                        + ";loginTimeout="
                        + login
                        + ";socketTimeout="
                        + socket;
        // The broader runtime policy is applied only to our canonical, bounded value.
        ShadowStorageProperties.enabled(ShadowStorageTargetKind.LOCAL_EPHEMERAL, validated, null);
        return validated;
    }

    private static int boundedTimeout(final String value, final int minimum, final int maximum) {
        if (value == null || !value.matches("[1-9][0-9]{0,5}")) {
            throw new IllegalArgumentException("QUAL_SHADOW_URL_INVALID");
        }
        final int parsed = Integer.parseInt(value);
        if (parsed < minimum || parsed > maximum) {
            throw new IllegalArgumentException("QUAL_SHADOW_URL_INVALID");
        }
        return parsed;
    }
}
