package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.SampleFinish;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.SampleReadback;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Same-connection SAMPLE12 owner readback. No authority, connection creation or commit. */
final class C78Sample12Owner {
    private static final String UNIT =
            """
WITH unit AS (SELECT CAST(? AS uniqueidentifier) execution_id,CAST(? AS uniqueidentifier) cycle_id,
CAST(? AS nvarchar(32)) COLLATE Latin1_General_100_BIN2 environment_name,
CAST(? AS nvarchar(128)) COLLATE Latin1_General_100_BIN2 source_instance,
CAST(? AS nvarchar(128)) COLLATE Latin1_General_100_BIN2 tenant_scope,
CAST(? AS datetime2(3)) start_utc,CAST(? AS datetime2(3)) end_utc)
""";
    private static final List<String> OCCURRENCE_TABLES =
            List.of(
                    "ctl.execution_attempt",
                    "ctl.execution_page_audit",
                    "ctl.execution_count",
                    "ctl.execution_state_event",
                    "ctl.execution_lease",
                    "ctl.execution_source_protocol",
                    "ctl.runtime_contract_evidence",
                    "ctl.runtime_coleta_publication_receipt",
                    "ctl.execution_promotion_result",
                    "ctl.execution_publication_event",
                    "ctl.coleta_promotion_result",
                    "stg.execution_record",
                    "stg.coleta_record",
                    "stg.coleta_exact_time");
    private static final String BASELINE =
            UNIT
                    + "SELECT "
                    + OCCURRENCE_TABLES.stream()
                            .map(
                                    table ->
                                            "(SELECT COUNT_BIG(*) FROM "
                                                    + table
                                                    + " WHERE execution_id=unit.execution_id)")
                            .collect(java.util.stream.Collectors.joining(","))
                    + ",ISNULL((SELECT c.* FROM ctl.execution_cycle c WHERE"
                    + " c.cycle_id=unit.cycle_id FOR JSON"
                    + " PATH,INCLUDE_NULL_VALUES),N'[]'),ISNULL((SELECT p.* FROM"
                    + " ctl.execution_partition p WHERE p.environment_name=unit.environment_name"
                    + " AND p.source_instance=unit.source_instance AND"
                    + " p.tenant_scope=unit.tenant_scope AND p.entity_name=N'coletas' AND"
                    + " p.execution_mode=N'BACKFILL' AND p.partition_start_utc=unit.start_utc AND"
                    + " p.partition_end_exclusive_utc=unit.end_utc FOR JSON"
                    + " PATH,INCLUDE_NULL_VALUES),N'[]'),ISNULL((SELECT s.* FROM ctl.source_catalog"
                    + " s WHERE s.source_instance=unit.source_instance FOR JSON"
                    + " PATH,INCLUDE_NULL_VALUES),N'[]'),ISNULL((SELECT b.* FROM"
                    + " ctl.source_protocol_binding b WHERE b.source_instance=unit.source_instance"
                    + " AND b.source_kind=N'DATA_EXPORT' FOR JSON PATH,INCLUDE_NULL_VALUES),N'[]'),"
                    + " (SELECT COUNT_BIG(*) FROM sys.dm_tran_locks WHERE request_session_id=@@SPID"
                    + " AND resource_database_id=DB_ID() AND resource_type=N'OBJECT' AND"
                    + " request_mode=N'X' AND request_owner_type=N'TRANSACTION') FROM unit";
    private static final String BINDING =
            """
SELECT p.environment_name,p.source_instance,p.tenant_scope,p.entity_name,p.execution_mode,
p.partition_start_utc,p.partition_end_exclusive_utc,a.contract_version,a.contract_fingerprint,
a.configuration_version,a.configuration_fingerprint,a.cycle_id,c.plan_version,c.plan_fingerprint,
a.idempotency_key,a.window_strategy,a.current_state,
(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit e WHERE e.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit e WHERE e.execution_id=a.execution_id AND
 e.page_number=1 AND e.page_attempt=1 AND e.requested_page_size=1 AND e.physical_rows=?
 AND e.distinct_root_keys=1 AND e.terminal_empty_page=0),
(SELECT COUNT_BIG(*) FROM ctl.execution_lease l WHERE l.execution_id=a.execution_id AND l.released_at_utc IS NULL),
(SELECT COUNT_BIG(*) FROM ctl.runtime_contract_evidence r WHERE r.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.runtime_coleta_publication_receipt r WHERE r.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.execution_promotion_result r WHERE r.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.execution_publication_event r WHERE r.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.coleta_promotion_result r WHERE r.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.execution_count r WHERE r.execution_id=a.execution_id),
(SELECT COUNT_BIG(*) FROM ctl.execution_source_protocol b WHERE b.execution_id=a.execution_id
 AND b.source_instance=p.source_instance AND b.source_kind=N'DATA_EXPORT' AND b.binding_origin=N'V024'),
CASE WHEN a.terminal_at_utc IS NOT NULL AND p.current_execution_id=a.execution_id
 AND p.current_state=N'FAILED' THEN 1 ELSE 0 END
FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
JOIN ctl.execution_cycle c ON c.cycle_id=a.cycle_id WHERE a.execution_id=?
""";
    private static final String SETS =
            """
WITH expected AS (SELECT source_key COLLATE Latin1_General_100_BIN2 source_key,
DATALENGTH(source_key) key_bytes,physical_rows FROM OPENJSON(?) WITH
(source_key nvarchar(256) '$.sourceKey',physical_rows bigint '$.physicalRows')),
staged AS (SELECT source_key COLLATE Latin1_General_100_BIN2 source_key,
DATALENGTH(source_key) key_bytes,COUNT_BIG(*) physical_rows FROM stg.coleta_record
WHERE execution_id=? GROUP BY source_key COLLATE Latin1_General_100_BIN2,DATALENGTH(source_key)),
missing_stage AS (SELECT * FROM expected EXCEPT SELECT * FROM staged),
extra_stage AS (SELECT * FROM staged EXCEPT SELECT * FROM expected)
SELECT (SELECT COUNT_BIG(*) FROM expected),(SELECT SUM(physical_rows) FROM expected),
(SELECT COUNT_BIG(*) FROM staged),(SELECT SUM(physical_rows) FROM staged),
(SELECT COUNT_BIG(*) FROM missing_stage),(SELECT COUNT_BIG(*) FROM extra_stage),
(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=?),
(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=? AND validation_disposition<>N'VALID'),
(SELECT COUNT_BIG(*) FROM stg.coleta_record s LEFT JOIN stg.execution_record r
 ON r.stage_record_id=s.stage_record_id WHERE s.execution_id=? AND
 (r.stage_record_id IS NULL OR r.execution_id<>s.execution_id OR r.source_key IS NULL OR
 r.source_key COLLATE Latin1_General_100_BIN2<>s.source_key COLLATE Latin1_General_100_BIN2
 OR DATALENGTH(r.source_key)<>DATALENGTH(s.source_key))),
(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=? AND
 (input_batch_number<>1 OR input_record_ordinal<1 OR input_record_ordinal>?)),
(SELECT COUNT(DISTINCT input_record_ordinal) FROM stg.execution_record WHERE execution_id=?),
(SELECT COUNT_BIG(*) FROM stg.coleta_exact_time WHERE execution_id=?)
""";

    record Baseline(List<Long> counts, List<String> snapshots, long ownX) {
        Baseline {
            counts = List.copyOf(counts);
            snapshots = List.copyOf(snapshots);
        }

        @Override
        public String toString() {
            return "C78SampleBaseline[redacted]";
        }
    }

    @FunctionalInterface
    interface Check {
        void run() throws SQLException;
    }

    private C78Sample12Owner() {}

    static Baseline baseline(Connection physical, C78Sample12Selection selection, Check before)
            throws SQLException {
        before.run();
        try (var query = physical.prepareStatement(BASELINE)) {
            query.setQueryTimeout(10);
            bindUnit(query, selection);
            try (var row = query.executeQuery()) {
                Baseline value = readBaseline(row);
                if (value.counts().stream().anyMatch(count -> count != 0L)) {
                    throw new SQLException("DEV_SAMPLE_FRESH_OCCURRENCE_REQUIRED");
                }
                return value;
            }
        }
    }

    static boolean compare(
            Connection physical, C78Sample12Selection selection, SampleFinish finish, Check before)
            throws SQLException {
        if (finish.expectation().isEmpty()) {
            return false;
        }
        var expected = finish.expectation().orElseThrow();
        var item = selection.item();
        var p = item.partition();
        if (!expected.executionId().equals(item.request().executionId())
                || !expected.expectedRoots()
                        .get(0)
                        .identity()
                        .sourceInstance()
                        .equals(p.sourceInstance())
                || !expected.expectedRoots()
                        .get(0)
                        .identity()
                        .tenantScope()
                        .equals(p.tenantScope())) {
            throw new SQLException("DEV_SAMPLE_EXPECTATION_SCOPE");
        }
        before.run();
        try (var query = physical.prepareStatement(BINDING)) {
            query.setQueryTimeout(10);
            query.setInt(1, expected.physicalRows());
            query.setString(2, expected.executionId().toString());
            try (var row = query.executeQuery()) {
                requireShape(row, 28);
                String[] texts = {
                    p.environment(), p.sourceInstance(), p.tenantScope(), "coletas", "BACKFILL"
                };
                for (int index = 0; index < texts.length; index++) {
                    if (!texts[index].equals(row.getString(index + 1))) {
                        mismatch();
                    }
                }
                if (!p.partitionStart().equals(row.getTimestamp(6, utc()).toInstant())
                        || !p.partitionEndExclusive()
                                .equals(row.getTimestamp(7, utc()).toInstant())) {
                    mismatch();
                }
                String[] identity = {
                    item.definition().contract().version(),
                    item.definition().contract().sha256(),
                    item.definition().configuration().version(),
                    item.definition().configuration().sha256(),
                    selection.plan().cycleId().toString(),
                    selection.plan().fingerprint().version(),
                    selection.plan().fingerprint().sha256(),
                    item.request().idempotencyKey(),
                    item.request().windowStrategy().name(),
                    "FAILED"
                };
                for (int index = 0; index < identity.length; index++) {
                    if (index == 4) {
                        if (!selection
                                .plan()
                                .cycleId()
                                .equals(java.util.UUID.fromString(row.getString(index + 8)))) {
                            mismatch();
                        }
                        continue;
                    }
                    if (!identity[index].equals(row.getString(index + 8))) {
                        mismatch();
                    }
                }
                for (int column = 18; column <= 28; column++) {
                    long required =
                            column == 18 || column == 19 || column == 27 || column == 28 ? 1 : 0;
                    if (scalar(row, column) != required) {
                        mismatch();
                    }
                }
                if (row.next()) {
                    mismatch();
                }
            }
        }
        final var json = new ObjectMapper().createArrayNode();
        var root = expected.expectedRoots().get(0);
        json.addObject()
                .put("sourceKey", root.identity().sourceKey().storageValue())
                .put("physicalRows", expected.physicalRows());
        String material = json.toString();
        if (material.length() > 4096) {
            throw new SQLException("DEV_SAMPLE_EXPECTATION_LIMIT");
        }
        before.run();
        try (var query = physical.prepareStatement(SETS)) {
            query.setQueryTimeout(10);
            query.setNString(1, material);
            for (int column : List.of(2, 3, 4, 5, 6, 8, 9)) {
                query.setString(column, expected.executionId().toString());
            }
            query.setInt(7, expected.physicalRows());
            try (var row = query.executeQuery()) {
                requireShape(row, 12);
                long[] required = {
                    1,
                    expected.physicalRows(),
                    1,
                    expected.physicalRows(),
                    0,
                    0,
                    expected.physicalRows(),
                    0,
                    0,
                    0,
                    expected.physicalRows(),
                    0
                };
                for (int index = 0; index < required.length; index++) {
                    if (scalar(row, index + 1) != required[index]) {
                        mismatch();
                    }
                }
                if (row.next()) {
                    mismatch();
                }
            }
        }
        return true;
    }

    static SampleReadback rollbackAndClose(
            Connection physical,
            C78Sample12Selection selection,
            Baseline baseline,
            SampleFinish finish,
            boolean compared,
            Check cleanup,
            java.util.function.IntSupplier used)
            throws SQLException {
        Throwable primary = null;
        boolean readTransaction = false;
        boolean completed = false;
        try {
            physical.rollback();
            cleanup.run();
            zeroState(physical);
            cleanup.run();
            readTransaction = true;
            try (var query = physical.prepareStatement(BASELINE)) {
                query.setQueryTimeout(10);
                bindUnit(query, selection);
                try (var row = query.executeQuery()) {
                    Baseline after = readBaseline(row);
                    if (!baseline.counts().equals(after.counts())
                            || !baseline.snapshots().equals(after.snapshots())
                            || after.ownX() != 0L) {
                        throw new SQLException("DEV_SAMPLE_SCOPED_ROLLBACK_ABSENCE_NOT_PROVED");
                    }
                }
            }
            physical.rollback();
            readTransaction = false;
            cleanup.run();
            zeroState(physical);
            completed = true;
        } catch (SQLException failure) {
            primary = failure;
            throw failure;
        } catch (RuntimeException failure) {
            primary = new SQLException("DEV_SAMPLE_OWNER_READBACK_FAILED", failure);
            throw (SQLException) primary;
        } catch (Error failure) {
            primary = failure;
            throw failure;
        } finally {
            Throwable failure = null;
            if (readTransaction || !completed) {
                try {
                    physical.rollback();
                } catch (SQLException | RuntimeException | Error refused) {
                    failure = cleanupFailure(refused);
                }
            }
            try {
                physical.close();
            } catch (SQLException | RuntimeException | Error refused) {
                failure = C78OwnedCleanup.combine(failure, cleanupFailure(refused));
            }
            if (failure != null) {
                if (primary != null) {
                    C78OwnedCleanup.combine(primary, failure);
                } else {
                    C78OwnedCleanup.rethrow(failure);
                }
            }
        }
        if (!physical.isClosed()) {
            throw new SQLException("DEV_SAMPLE_PHYSICAL_CLOSE_NOT_PROVED");
        }
        int rows = compared ? finish.expectation().orElseThrow().physicalRows() : 0;
        return new SampleReadback(
                compared,
                true,
                true,
                true,
                true,
                rows,
                compared ? 1 : 0,
                compared ? 1 : 0,
                used.getAsInt());
    }

    private static void zeroState(Connection physical) throws SQLException {
        try (var query = physical.createStatement()) {
            query.setQueryTimeout(10);
            try (var row = query.executeQuery("SELECT @@TRANCOUNT,XACT_STATE()")) {
                requireShape(row, 2);
                if (scalar(row, 1) != 0 || scalar(row, 2) != 0 || row.next()) {
                    throw new SQLException("DEV_SAMPLE_OWNER_TRANSACTION_ZERO_NOT_PROVED");
                }
            }
        }
    }

    private static Baseline readBaseline(ResultSet row) throws SQLException {
        requireShape(row, 19);
        var counts = new ArrayList<Long>(14);
        for (int column = 1; column <= 14; column++) {
            counts.add(scalar(row, column));
        }
        var snapshots = new ArrayList<String>(4);
        for (int column = 15; column <= 18; column++) {
            String value = row.getString(column);
            if (value == null || value.length() > 8192) {
                throw new SQLException("DEV_SAMPLE_BASELINE_LIMIT");
            }
            snapshots.add(value);
        }
        long ownX = scalar(row, 19);
        if (row.next()) {
            mismatch();
        }
        return new Baseline(counts, snapshots, ownX);
    }

    private static void bindUnit(PreparedStatement query, C78Sample12Selection selection)
            throws SQLException {
        var item = selection.item();
        query.setString(1, item.request().executionId().toString());
        query.setString(2, selection.plan().cycleId().toString());
        query.setNString(3, item.partition().environment());
        query.setNString(4, item.partition().sourceInstance());
        query.setNString(5, item.partition().tenantScope());
        query.setTimestamp(6, Timestamp.from(item.partition().partitionStart()), utc());
        query.setTimestamp(7, Timestamp.from(item.partition().partitionEndExclusive()), utc());
    }

    private static Calendar utc() {
        return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
    }

    private static void requireShape(ResultSet row, int columns) throws SQLException {
        if (row.getMetaData().getColumnCount() != columns || !row.next()) {
            throw new SQLException("DEV_SAMPLE_OWNER_ROW_SHAPE");
        }
    }

    private static long scalar(ResultSet row, int column) throws SQLException {
        long value = row.getLong(column);
        if (row.wasNull() || value < 0) {
            throw new SQLException("DEV_SAMPLE_OWNER_SCALAR");
        }
        return value;
    }

    private static void mismatch() throws SQLException {
        throw new SQLException("DEV_SAMPLE_OWNER_READBACK_MISMATCH");
    }

    private static Throwable cleanupFailure(final Throwable failure) {
        return C78OwnedCleanup.failure(failure, "DEV_SAMPLE_OWNER_CLEANUP_FAILED");
    }
}
