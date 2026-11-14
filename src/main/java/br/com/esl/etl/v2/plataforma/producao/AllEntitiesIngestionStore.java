package br.com.esl.etl.v2.plataforma.producao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.JSON;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLTimeoutException;
import java.time.LocalDate;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/** Parameterized, least-privilege receiving entrypoints and byte-exact independent SQL readback. */
final class AllEntitiesIngestionStore implements AutoCloseable {
    private static final int REFRESH_RECOVERY_TIMEOUT_SECONDS = 120;
    private final Connection connection;

    static String targetUrl(final String base) {
        return ProductionIngestionStore.targetUrl(base);
    }

    AllEntitiesIngestionStore(final JsonNode configuration) throws Exception {
        final String url = targetUrl(configuration.required("jdbcBaseUrl").asText());
        final var properties = new Properties();
        require(
                "etl_v2_runtime_prod".equals(configuration.required("sqlUser").asText()),
                "PRODUCTION_DEDICATED_IDENTITY_REQUIRED");
        properties.setProperty("user", configuration.required("sqlUser").asText());
        properties.setProperty("password", configuration.required("sqlPassword").asText());
        properties.setProperty("applicationName", "ETL-V2-All-Entities-Receiving-0762");
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

    void start(
            final UUID run,
            final String scope,
            final LocalDate day,
            final String requestSha,
            final String entity)
            throws Exception {
        br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate.valueOf(entity);
        try (var command =
                connection.prepareCall("EXEC ctl.usp_production_ingestion_start ?,?,?,?,?")) {
            command.setQueryTimeout(20);
            command.setString(1, run.toString());
            command.setString(2, scope);
            command.setDate(3, java.sql.Date.valueOf(day));
            command.setString(4, requestSha);
            command.setString(5, entity);
            command.execute();
        }
    }

    void storeAndVerify(final UUID run, final List<String> rows, final String captureSha)
            throws Exception {
        storeAndVerify(run, rows, captureSha, 30);
    }

    void storeAllAndVerify(final UUID run, final List<String> rows, final String captureSha)
            throws Exception {
        storeAndVerify(run, rows, captureSha, 120);
    }

    void storeRefreshAndVerify(final UUID run, final List<String> rows, final String captureSha)
            throws Exception {
        storeRefreshAndVerify(run, rows, captureSha, 30);
    }

    private void storeRefreshAndVerify(
            final UUID run, final List<String> rows, final String captureSha, final int timeout)
            throws Exception {
        try {
            writeRows(run, rows, captureSha, timeout);
        } catch (final SQLTimeoutException timedOut) {
            recoverWrite(run, rows, captureSha);
            verifyRefreshRows(run, rows, captureSha, REFRESH_RECOVERY_TIMEOUT_SECONDS);
            return;
        }
        try {
            verifyRows(run, rows, captureSha, timeout);
        } catch (final SQLTimeoutException timedOut) {
            verifyRefreshRows(run, rows, captureSha, REFRESH_RECOVERY_TIMEOUT_SECONDS);
        }
    }

    private void recoverWrite(final UUID run, final List<String> rows, final String captureSha)
            throws Exception {
        // A timeout can lose the response after COMMIT. Check the same run under the
        // existing session lock before deciding whether a second write is necessary.
        try (var command = connection.prepareCall("SELECT @@TRANCOUNT;")) {
            command.setQueryTimeout(10);
            try (var state = command.executeQuery()) {
                require(state.next() && state.getInt(1) == 0, "REFRESH_SQL_TRANSACTION_UNRESOLVED");
            }
        }
        final String status;
        try (var command = connection.prepareCall("EXEC ctl.usp_production_ingestion_observe ?")) {
            command.setQueryTimeout(10);
            command.setString(1, run.toString());
            try (var state = command.executeQuery()) {
                require(state.next(), "REFRESH_SQL_WRITE_STATE_REQUIRED");
                status = state.getString("run_state");
            }
        }
        if ("RUNNING".equals(status)) {
            try {
                writeRows(run, rows, captureSha, REFRESH_RECOVERY_TIMEOUT_SECONDS);
            } catch (final SQLTimeoutException timedOutAgain) {
                throw new IllegalStateException("REFRESH_SQL_STORE_TIMEOUT", timedOutAgain);
            }
        } else {
            require("STORED".equals(status), "REFRESH_SQL_WRITE_STATE_UNEXPECTED");
        }
    }

    private void verifyRefreshRows(
            final UUID run, final List<String> rows, final String captureSha, final int timeout)
            throws Exception {
        try {
            verifyRows(run, rows, captureSha, timeout);
        } catch (final SQLTimeoutException timedOutAgain) {
            throw new IllegalStateException("REFRESH_SQL_READBACK_TIMEOUT", timedOutAgain);
        }
    }

    private void storeAndVerify(
            final UUID run, final List<String> rows, final String captureSha, final int timeout)
            throws Exception {
        writeRows(run, rows, captureSha, timeout);
        verifyRows(run, rows, captureSha, timeout);
    }

    private void writeRows(
            final UUID run, final List<String> rows, final String captureSha, final int timeout)
            throws Exception {
        try (var command =
                connection.prepareCall("EXEC stg.usp_production_ingestion_store ?,?,?,?")) {
            command.setQueryTimeout(timeout);
            command.setString(1, run.toString());
            command.setNString(2, JSON.writeValueAsString(rows));
            command.setInt(3, rows.size());
            command.setString(4, captureSha);
            command.execute();
        }
    }

    private void verifyRows(
            final UUID run, final List<String> rows, final String captureSha, final int timeout)
            throws Exception {
        try (var command = connection.prepareCall("EXEC ctl.usp_production_ingestion_observe ?")) {
            command.setQueryTimeout(timeout);
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

    void missing(
            final String scope,
            final LocalDate start,
            final LocalDate end,
            final String entity,
            final java.util.function.Consumer<LocalDate> consumer)
            throws Exception {
        br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate.valueOf(entity);
        if (end.isBefore(start)) {
            return;
        }
        try (var command =
                connection.prepareCall("EXEC ctl.usp_production_ingestion_plan ?,?,?,?")) {
            command.setQueryTimeout(10);
            command.setString(1, scope);
            command.setDate(2, java.sql.Date.valueOf(start));
            command.setDate(3, java.sql.Date.valueOf(end));
            command.setString(4, entity);
            try (var rows = command.executeQuery()) {
                while (rows.next()) {
                    if (!rows.getBoolean("stored")) {
                        consumer.accept(rows.getDate("business_date").toLocalDate());
                    }
                }
            }
        }
    }

    LocalDate earliestCovered(final String scope, final LocalDate today, final String entity)
            throws Exception {
        try (var command =
                connection.prepareCall("EXEC ctl.usp_production_ingestion_plan ?,?,?,?")) {
            command.setQueryTimeout(10);
            command.setString(1, scope);
            command.setDate(2, java.sql.Date.valueOf(today.minusDays(3660)));
            command.setDate(3, java.sql.Date.valueOf(today));
            command.setString(4, entity);
            try (var rows = command.executeQuery()) {
                LocalDate first = null;
                while (rows.next()) {
                    if (rows.getBoolean("stored")) {
                        final var day = rows.getDate("business_date").toLocalDate();
                        if (first == null || day.isBefore(first)) {
                            first = day;
                        }
                    }
                }
                return first;
            }
        }
    }

    @Override
    public void close() throws java.sql.SQLException {
        connection.close();
    }
}
