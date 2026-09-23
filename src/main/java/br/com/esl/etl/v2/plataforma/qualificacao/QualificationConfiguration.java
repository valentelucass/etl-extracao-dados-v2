package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;

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

    public String jdbcUrl() {
        return "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                + "integratedSecurity=true;encrypt=true;trustServerCertificate=true;"
                + "loginTimeout=5;socketTimeout="
                + socketMillis();
    }
}
