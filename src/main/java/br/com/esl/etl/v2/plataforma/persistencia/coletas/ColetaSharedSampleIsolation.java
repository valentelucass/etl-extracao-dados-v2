package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

/** Transaction-owned object X locks conflict with ordinary DML, including on empty tables. */
final class ColetaSharedSampleIsolation {
    static final int OBJECTS = 11;
    private static final long ADMISSION_NANOS = TimeUnit.SECONDS.toNanos(60);

    private ColetaSharedSampleIsolation() {}

    static void acquire(final ColetaTemporalLaboratorySession session) throws SQLException {
        final long started = session.sharedAdmissionStartedNanos();
        for (int slot = 0; slot < OBJECTS; slot++) {
            final long remaining = ADMISSION_NANOS - (System.nanoTime() - started);
            if (remaining < TimeUnit.SECONDS.toNanos(1)) {
                throw new SQLException("COL_SAMPLE_SHARED_ADMISSION_DEADLINE");
            }
            final String object = object(slot);
            try (var connection = session.getConnection();
                    var statement =
                            connection.prepareStatement(
                                    "SELECT TOP (1) 1 FROM "
                                            + object
                                            + " WITH (TABLOCKX,HOLDLOCK)")) {
                // LOCK_TIMEOUT=0 also covers TABLOCKX; no reliance on NOWAIT hint composition.
                statement.setQueryTimeout(
                        (int) Math.min(10, TimeUnit.NANOSECONDS.toSeconds(remaining)));
                try (var rows = statement.executeQuery()) {
                    // No domain value is returned; closing the cursor preserves transaction locks.
                    if (rows.next() && rows.next()) {
                        throw new SQLException("COL_SAMPLE_SHARED_LOCK_RESULT_INVALID");
                    }
                    if (System.nanoTime() - started >= ADMISSION_NANOS) {
                        throw new SQLException("COL_SAMPLE_SHARED_ADMISSION_DEADLINE");
                    }
                }
            } catch (final SQLException failure) {
                final String state = failure.getSQLState();
                throw new SQLException(
                        "COL_SAMPLE_SHARED_FOOTPRINT_UNAVAILABLE",
                        state != null && state.matches("[A-Z0-9]{5}") ? state : null,
                        failure.getErrorCode());
            }
        }
    }

    static void verifyAdmissionDeadline(final ColetaTemporalLaboratorySession session)
            throws SQLException {
        if (System.nanoTime() - session.sharedAdmissionStartedNanos() >= ADMISSION_NANOS) {
            throw new SQLException("COL_SAMPLE_SHARED_ADMISSION_DEADLINE");
        }
    }

    static String transactionStateSql() {
        return "SELECT @@TRANCOUNT, XACT_STATE(), @@SPID,"
                + " (SELECT transaction_id FROM sys.dm_tran_current_transaction),"
                + " (SELECT COUNT(DISTINCT resource_associated_entity_id) FROM sys.dm_tran_locks"
                + " WHERE request_session_id=@@SPID AND resource_database_id=DB_ID()"
                + " AND resource_type=N'OBJECT' AND request_mode=N'X' AND request_status=N'GRANT'"
                + " AND request_owner_type=N'TRANSACTION' AND request_owner_id="
                + " (SELECT transaction_id FROM sys.dm_tran_current_transaction)"
                + " AND resource_associated_entity_id IN ("
                + " OBJECT_ID(N'ctl.execution_attempt'),OBJECT_ID(N'ctl.execution_cycle'),"
                + " OBJECT_ID(N'ctl.execution_lease'),OBJECT_ID(N'ctl.execution_page_audit'),"
                + " OBJECT_ID(N'ctl.execution_partition'),OBJECT_ID(N'ctl.execution_source_protocol'),"
                + " OBJECT_ID(N'ctl.execution_state_event'),OBJECT_ID(N'ctl.source_catalog'),"
                + " OBJECT_ID(N'ctl.source_protocol_binding'),OBJECT_ID(N'stg.coleta_record'),"
                + " OBJECT_ID(N'stg.execution_record'))), @@LOCK_TIMEOUT";
    }

    private static String object(final int slot) {
        return switch (slot) {
            case 0 -> "ctl.execution_attempt";
            case 1 -> "ctl.execution_cycle";
            case 2 -> "ctl.execution_lease";
            case 3 -> "ctl.execution_page_audit";
            case 4 -> "ctl.execution_partition";
            case 5 -> "ctl.execution_source_protocol";
            case 6 -> "ctl.execution_state_event";
            case 7 -> "ctl.source_catalog";
            case 8 -> "ctl.source_protocol_binding";
            case 9 -> "stg.coleta_record";
            case 10 -> "stg.execution_record";
            default -> throw new IllegalArgumentException("COL_SAMPLE_SHARED_FOOTPRINT_SLOT");
        };
    }
}
