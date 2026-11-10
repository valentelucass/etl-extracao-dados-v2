package br.com.esl.etl.v2.plataforma.producao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.JSON;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Parameterized, least-privilege receiving entrypoints and byte-exact independent SQL readback. */
final class ProductionIngestionStore implements AutoCloseable {
    private final Connection connection;

    static String targetUrl(final String base) {
        require(
                base.startsWith("jdbc:sqlserver://127.0.0.1:1433;")
                        && base.contains("encrypt=true;")
                        && base.contains("trustServerCertificate=false;")
                        && !base.toLowerCase(java.util.Locale.ROOT).contains("databasename="),
                "PRODUCTION_VALIDATED_LOCAL_TLS_REQUIRED");
        return base + ";databaseName=ETL_SISTEMA_V2;";
    }

    ProductionIngestionStore(final JsonNode configuration) throws Exception {
        final String url = targetUrl(configuration.required("jdbcBaseUrl").asText());
        final var properties = new Properties();
        require(
                "etl_v2_runtime_prod".equals(configuration.required("sqlUser").asText()),
                "PRODUCTION_DEDICATED_IDENTITY_REQUIRED");
        properties.setProperty("user", configuration.required("sqlUser").asText());
        properties.setProperty("password", configuration.required("sqlPassword").asText());
        properties.setProperty("applicationName", "ETL-V2-Production-Receiving-0759");
        properties.setProperty("loginTimeout", "10");
        connection = DriverManager.getConnection(url, properties);
        properties.clear();
        boolean accepted = false;
        try {
            try (var statement = connection.createStatement()) {
                statement.setQueryTimeout(10);
                try (var row =
                        statement.executeQuery(
                                "SELECT DB_NAME(), ORIGINAL_LOGIN(), "
                                        + "CONVERT(varchar(32),SERVERPROPERTY('ProductVersion')),IS_ROLEMEMBER('db_owner')")) {
                    require(
                            row.next()
                                    && "ETL_SISTEMA_V2".equals(row.getString(1))
                                    && "etl_v2_runtime_prod".equals(row.getString(2))
                                    && "16.0.1000.6".equals(row.getString(3))
                                    && row.getInt(4) == 0,
                            "PRODUCTION_TARGET_IDENTITY_VERSION_REFUSED");
                }
            }
            try (var lock =
                    connection.prepareCall(
                            "DECLARE @r int; EXEC @r=sys.sp_getapplock "
                                    + "@Resource=N'ETL_V2_PRODUCTION_RECEIVING',@LockMode='Exclusive',"
                                    + "@LockOwner='Session',@LockTimeout=0; SELECT @r")) {
                lock.setQueryTimeout(10);
                try (var row = lock.executeQuery()) {
                    require(
                            row.next() && row.getInt(1) >= 0,
                            "PRODUCTION_OVERLAPPING_WRITER_REFUSED");
                }
            }
            accepted = true;
        } finally {
            if (!accepted) {
                connection.close();
            }
        }
    }

    void start(final UUID run, final String scope, final LocalDate day, final String requestSha)
            throws Exception {
        try (var command =
                connection.prepareCall("EXEC ctl.usp_production_ingestion_start ?,?,?,?")) {
            command.setQueryTimeout(20);
            command.setString(1, run.toString());
            command.setString(2, scope);
            command.setDate(3, java.sql.Date.valueOf(day));
            command.setString(4, requestSha);
            command.execute();
        }
    }

    void storeAndVerify(final UUID run, final List<String> rows, final String captureSha)
            throws Exception {
        try (var command =
                connection.prepareCall("EXEC stg.usp_production_ingestion_store ?,?,?,?")) {
            command.setQueryTimeout(30);
            command.setString(1, run.toString());
            command.setNString(2, JSON.writeValueAsString(rows));
            command.setInt(3, rows.size());
            command.setString(4, captureSha);
            command.execute();
        }
        try (var command = connection.prepareCall("EXEC ctl.usp_production_ingestion_observe ?")) {
            command.setQueryTimeout(30);
            command.setString(1, run.toString());
            try (var state = command.executeQuery()) {
                require(
                        state.next()
                                && "STORED".equals(state.getString("run_state"))
                                && state.getInt("row_count") == rows.size()
                                && captureSha.equals(state.getString("capture_sha256")),
                        "PRODUCTION_SQL_COMMIT_RECONCILIATION_FAILED");
            }
            require(command.getMoreResults(), "PRODUCTION_SQL_OCCURRENCES_REQUIRED");
            try (var stored = command.getResultSet()) {
                int index = 0;
                while (stored.next()) {
                    require(
                            index < rows.size()
                                    && stored.getInt(1) == index + 1
                                    && rows.get(index).equals(stored.getNString(2))
                                    && MessageDigest.isEqual(
                                            stored.getBytes(3),
                                            MessageDigest.getInstance("SHA-256")
                                                    .digest(
                                                            rows.get(index)
                                                                    .getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_16LE))),
                            "PRODUCTION_SQL_OCCURRENCE_VALUE_MISMATCH");
                    index++;
                }
                require(index == rows.size(), "PRODUCTION_SQL_OCCURRENCE_COUNT_MISMATCH");
            }
        }
    }

    void missing(
            final String scope,
            final LocalDate start,
            final LocalDate end,
            final java.util.function.Consumer<LocalDate> consumer)
            throws Exception {
        if (end.isBefore(start)) {
            return;
        }
        try (var command = connection.prepareCall("EXEC ctl.usp_production_ingestion_plan ?,?,?")) {
            command.setQueryTimeout(10);
            command.setString(1, scope);
            command.setDate(2, java.sql.Date.valueOf(start));
            command.setDate(3, java.sql.Date.valueOf(end));
            try (var rows = command.executeQuery()) {
                while (rows.next()) {
                    if (!rows.getBoolean("stored")) {
                        consumer.accept(rows.getDate("business_date").toLocalDate());
                    }
                }
            }
        }
    }

    void fail(final UUID run, final String code) throws Exception {
        try (var command = connection.prepareCall("EXEC ctl.usp_production_ingestion_fail ?,?")) {
            command.setQueryTimeout(10);
            command.setString(1, run.toString());
            command.setString(2, code);
            command.execute();
        }
    }

    @Override
    public void close() throws java.sql.SQLException {
        connection.close();
    }
}
