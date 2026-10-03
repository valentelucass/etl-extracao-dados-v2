package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** COL-SAMPLE-01: SQL readback of one populated page without terminal or publication evidence. */
final class ColetaShadowSampleComparator {
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String BINDING_SQL =
            "SELECT p.source_instance,p.tenant_scope,p.partition_start_utc,"
                    + "p.partition_end_exclusive_utc,a.contract_fingerprint,a.execution_id,"
                    + "(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit e WHERE e.execution_id=a.execution_id),"
                    + "(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit e WHERE e.execution_id=a.execution_id"
                    + " AND e.page_number=1 AND e.physical_rows>0 AND e.terminal_empty_page=0),"
                    + "(SELECT COALESCE(SUM(e.physical_rows),0) FROM ctl.execution_page_audit e"
                    + " WHERE e.execution_id=a.execution_id AND e.page_number=1),"
                    + "(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit e WHERE e.execution_id=a.execution_id"
                    + " AND e.terminal_empty_page=1)"
                    + " FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id"
                    + " WHERE a.execution_id=? AND p.entity_name=N'coletas'"
                    + " AND p.execution_mode=N'BACKFILL' AND a.current_state=N'FAILED'";
    private static final String SET_SQL =
            "WITH expected AS (SELECT source_key COLLATE Latin1_General_100_BIN2 source_key,physical_rows"
                    + " FROM OPENJSON(?) WITH (source_key nvarchar(256) '$.sourceKey',physical_rows bigint '$.physicalRows')),"
                    + " staged AS (SELECT s.source_key COLLATE Latin1_General_100_BIN2 source_key,COUNT_BIG(*) physical_rows"
                    + " FROM stg.coleta_record s WHERE s.execution_id=? GROUP BY s.source_key COLLATE Latin1_General_100_BIN2),"
                    + " missing_stage AS (SELECT source_key,physical_rows FROM expected"
                    + " EXCEPT SELECT source_key,physical_rows FROM staged),"
                    + " extra_stage AS (SELECT source_key,physical_rows FROM staged"
                    + " EXCEPT SELECT source_key,physical_rows FROM expected)"
                    + " SELECT (SELECT COUNT_BIG(*) FROM expected),(SELECT COALESCE(SUM(physical_rows),0) FROM expected),"
                    + "(SELECT COUNT_BIG(*) FROM staged),(SELECT COALESCE(SUM(physical_rows),0) FROM staged),"
                    + "(SELECT COUNT_BIG(*) FROM missing_stage),(SELECT COUNT_BIG(*) FROM extra_stage),"
                    + "(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=?),"
                    + "(SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=? AND validation_disposition=N'QUARANTINE'),"
                    + "(SELECT COUNT_BIG(*) FROM stg.coleta_record s LEFT JOIN stg.execution_record r"
                    + " ON r.stage_record_id=s.stage_record_id WHERE s.execution_id=?"
                    + " AND (r.stage_record_id IS NULL OR r.execution_id<>s.execution_id"
                    + " OR r.source_key IS NULL OR r.source_key COLLATE Latin1_General_100_BIN2"
                    + "<>s.source_key COLLATE Latin1_General_100_BIN2))";

    private ColetaShadowSampleComparator() {}

    static long verifyBinding(final ColetaTemporalLaboratorySession session, final Binding expected)
            throws SQLException {
        Objects.requireNonNull(session);
        Objects.requireNonNull(expected);
        if (expected.sourcePage() != 1) {
            throw new IllegalArgumentException("COL_SAMPLE_SINGLE_PAGE_BINDING_REQUIRED");
        }
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(BINDING_SQL)) {
            statement.setQueryTimeout(10);
            statement.setString(1, expected.comparisonCohort().toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("COL_SAMPLE_BINDING_UNOBSERVABLE");
                }
                final var utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                final var start = rows.getTimestamp(3, utc);
                final var end = rows.getTimestamp(4, utc);
                if (start == null
                        || end == null
                        || !start.toInstant()
                                .equals(
                                        expected.requestDate()
                                                .atStartOfDay(SOURCE_ZONE)
                                                .toInstant())
                        || !end.toInstant()
                                .equals(
                                        expected.requestDate()
                                                .plusDays(1)
                                                .atStartOfDay(SOURCE_ZONE)
                                                .toInstant())
                        || !expected.sourceInstance().equals(rows.getString(1))
                        || !expected.tenantScope().equals(rows.getString(2))
                        || !expected.contractFingerprint().equals(rows.getString(5))
                        || !expected.comparisonCohort().toString().equals(rows.getString(6))) {
                    throw new SQLException("COL_SAMPLE_BINDING_MISMATCH");
                }
                final long physicalRows = rows.getLong(9);
                if (rows.wasNull()
                        || physicalRows < 1
                        || physicalRows > 1000
                        || rows.getLong(7) != 1
                        || rows.getLong(8) != 1
                        || rows.getLong(10) != 0
                        || rows.next()) {
                    throw new SQLException("COL_SAMPLE_PAGE_AUDIT_REQUIRED");
                }
                return physicalRows;
            }
        }
    }

    static int compare(
            final ColetaTemporalLaboratorySession session,
            final Binding binding,
            final List<ExpectedRoot> expectedRoots,
            final long auditedPhysicalRows)
            throws SQLException {
        final List<ExpectedRoot> roots = List.copyOf(Objects.requireNonNull(expectedRoots));
        if (roots.isEmpty() || roots.size() > 5) {
            throw new IllegalArgumentException("COL_SAMPLE_EXPECTED_ROOT_LIMIT");
        }
        final var seen = new HashSet<>();
        final var json = new ObjectMapper().createArrayNode();
        long physicalRows = 0;
        for (final var root : roots) {
            final var identity = root.identity();
            if (!binding.sourceInstance().equals(identity.sourceInstance())
                    || !binding.tenantScope().equals(identity.tenantScope())
                    || !root.physicalRows().isPresent()
                    || !root.sourcePages().equals(java.util.Set.of(1))
                    || !seen.add(identity.sourceKey())) {
                throw new IllegalArgumentException("COL_SAMPLE_EXPECTED_SCOPE_OR_MULTIPLICITY");
            }
            physicalRows += root.physicalRows().getAsInt();
            final var row = json.addObject();
            row.put("sourceKey", identity.sourceKey().storageValue());
            row.put("physicalRows", root.physicalRows().getAsInt());
        }
        if (physicalRows > 1000 || physicalRows != auditedPhysicalRows) {
            throw new SQLException("COL_SAMPLE_AUDIT_MULTIPLICITY_MISMATCH");
        }
        final String expectedJson = json.toString();
        if (expectedJson.length() > 1024 * 1024) {
            throw new IllegalArgumentException("COL_SAMPLE_EXPECTED_BYTES_LIMIT");
        }
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(SET_SQL)) {
            statement.setQueryTimeout(20);
            statement.setNString(1, expectedJson);
            for (int parameter = 2; parameter <= 5; parameter++) {
                statement.setString(parameter, binding.comparisonCohort().toString());
            }
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("COL_SAMPLE_COMPARISON_UNOBSERVABLE");
                }
                if (rows.getLong(1) != roots.size()
                        || rows.getLong(2) != physicalRows
                        || rows.getLong(3) != roots.size()
                        || rows.getLong(4) != physicalRows
                        || rows.getLong(5) != 0
                        || rows.getLong(6) != 0
                        || rows.getLong(7) != physicalRows
                        || rows.getLong(8) != 0
                        || rows.getLong(9) != 0) {
                    throw new SQLException("COL_SAMPLE_STAGE_SET_DIVERGENCE");
                }
                if (rows.next()) {
                    throw new SQLException("COL_SAMPLE_COMPARISON_UNOBSERVABLE");
                }
                return (int) physicalRows;
            }
        }
    }
}
