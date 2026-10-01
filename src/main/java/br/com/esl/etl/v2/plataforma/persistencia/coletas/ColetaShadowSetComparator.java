package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaAttributePresence;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ObservedRow;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.SQLException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** SQL set comparison of one bounded observed 6908 traversal, scoped to a shadow execution. */
public final class ColetaShadowSetComparator {
    private static final int MAXIMUM_ROOTS = 1000;
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final String BINDING_SQL =
            "SELECT p.source_instance,p.tenant_scope,p.partition_start_utc,"
                    + "p.partition_end_exclusive_utc,a.contract_fingerprint,a.execution_id,"
                    + "(SELECT COUNT_BIG(*) FROM ctl.execution_page_audit audit"
                    + " WHERE audit.execution_id=a.execution_id AND audit.terminal_empty_page=1"
                    + " AND audit.terminal_evidence_kind=N'DATA_EXPORT_EMPTY_PAGE'),"
                    + "(SELECT MAX(audit.page_number) FROM ctl.execution_page_audit audit"
                    + " WHERE audit.execution_id=a.execution_id AND audit.terminal_empty_page=1"
                    + " AND audit.terminal_evidence_kind=N'DATA_EXPORT_EMPTY_PAGE')"
                    + " FROM ctl.execution_attempt a JOIN ctl.execution_partition p"
                    + " ON p.partition_id=a.partition_id"
                    + " WHERE a.execution_id=? AND p.entity_name=N'coletas'";
    private static final String SQL =
            "WITH expected AS (SELECT source_key COLLATE Latin1_General_100_BIN2 source_key,"
                    + " physical_rows FROM OPENJSON(?) WITH (source_key nvarchar(256) '$.sourceKey',"
                    + " physical_rows bigint '$.physicalRows')) ,"
                    + " scope AS (SELECT p.environment_name,p.source_instance,p.tenant_scope,p.entity_name"
                    + " FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id"
                    + " WHERE a.execution_id=? AND p.source_instance=? AND p.tenant_scope=?"
                    + " AND p.entity_name=N'coletas'),"
                    + " staged AS (SELECT s.source_key,COUNT_BIG(*) physical_rows"
                    + " FROM stg.coleta_record s WHERE s.execution_id=? GROUP BY s.source_key),"
                    + " published AS (SELECT c.source_key FROM core.coleta c JOIN scope p"
                    + " ON c.environment_name=p.environment_name AND c.source_instance=p.source_instance"
                    + " AND c.tenant_scope=p.tenant_scope AND c.entity_name=p.entity_name"
                    + " WHERE c.last_seen_execution_id=?),"
                    + " missing_stage AS (SELECT source_key,physical_rows FROM expected"
                    + " EXCEPT SELECT source_key,physical_rows FROM staged),"
                    + " extra_stage AS (SELECT source_key,physical_rows FROM staged"
                    + " EXCEPT SELECT source_key,physical_rows FROM expected),"
                    + " missing_core AS (SELECT source_key FROM expected EXCEPT SELECT source_key FROM published),"
                    + " extra_core AS (SELECT source_key FROM published EXCEPT SELECT source_key FROM expected)"
                    + " SELECT (SELECT COUNT_BIG(*) FROM scope),(SELECT COUNT_BIG(*) FROM expected),"
                    + " (SELECT COALESCE(SUM(physical_rows),0) FROM expected),"
                    + " (SELECT COUNT_BIG(*) FROM staged),"
                    + " (SELECT COALESCE(SUM(physical_rows),0) FROM staged),"
                    + " (SELECT COUNT_BIG(*) FROM published),"
                    + " (SELECT COUNT_BIG(*) FROM missing_stage),(SELECT COUNT_BIG(*) FROM extra_stage),"
                    + " (SELECT COUNT_BIG(*) FROM missing_core),(SELECT COUNT_BIG(*) FROM extra_core),"
                    + " (SELECT COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=?),"
                    + " (SELECT COUNT_BIG(*) FROM stg.execution_record"
                    + " WHERE execution_id=? AND validation_disposition=N'QUARANTINE')";
    private static final String OBSERVED_ROWS_SQL =
            "SELECT r.input_batch_number,s.source_key,s.field_presence_json FROM stg.coleta_record s"
                    + " JOIN stg.execution_record r ON r.stage_record_id=s.stage_record_id"
                    + " WHERE s.execution_id=? ORDER BY r.input_batch_number,r.input_record_ordinal";

    private ColetaShadowSetComparator() {}

    /** Binds the observed side to the persisted execution, not to caller-copied metadata. */
    public static Binding readObservedBinding(
            final ColetaTemporalLaboratorySession session, final UUID executionId)
            throws SQLException {
        Objects.requireNonNull(session);
        Objects.requireNonNull(executionId);
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(BINDING_SQL)) {
            statement.setQueryTimeout(10);
            statement.setString(1, executionId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("COL_SHADOW_BINDING_UNOBSERVABLE");
                }
                final var utc = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                final var start = rows.getTimestamp(3, utc);
                final var end = rows.getTimestamp(4, utc);
                if (start == null || end == null) {
                    throw new SQLException("COL_SHADOW_BINDING_UNOBSERVABLE");
                }
                final var date = start.toInstant().atZone(SOURCE_ZONE).toLocalDate();
                if (!start.toInstant().equals(date.atStartOfDay(SOURCE_ZONE).toInstant())
                        || !end.toInstant()
                                .equals(date.plusDays(1).atStartOfDay(SOURCE_ZONE).toInstant())) {
                    throw new SQLException("COL_SHADOW_WINDOW_NOT_ONE_CIVIL_DAY");
                }
                if (rows.getLong(7) != 1 || rows.getInt(8) < 2 || rows.wasNull()) {
                    throw new SQLException("COL_SHADOW_TERMINAL_AUDIT_REQUIRED");
                }
                final var binding =
                        new Binding(
                                rows.getString(1),
                                rows.getString(2),
                                date,
                                rows.getString(5),
                                UUID.fromString(rows.getString(6)),
                                0);
                if (!executionId.equals(binding.comparisonCohort()) || rows.next()) {
                    throw new SQLException("COL_SHADOW_BINDING_UNOBSERVABLE");
                }
                return binding;
            } catch (final IllegalArgumentException failure) {
                throw new SQLException("COL_SHADOW_BINDING_INVALID", failure);
            }
        }
    }

    public static Result compare(
            final ColetaTemporalLaboratorySession session,
            final UUID executionId,
            final List<ExpectedRoot> expectedRoots)
            throws SQLException {
        Objects.requireNonNull(session);
        Objects.requireNonNull(executionId);
        final List<ExpectedRoot> roots = List.copyOf(Objects.requireNonNull(expectedRoots));
        if (roots.isEmpty() || roots.size() > MAXIMUM_ROOTS) {
            throw new IllegalArgumentException("COL_SHADOW_EXPECTED_ROOT_LIMIT");
        }
        final var first = roots.get(0).identity();
        final var seen = new HashSet<>();
        final var json = new ObjectMapper().createArrayNode();
        long declaredPhysicalRows = 0;
        for (final var root : roots) {
            final var identity = root.identity();
            if (!first.sourceInstance().equals(identity.sourceInstance())
                    || !first.tenantScope().equals(identity.tenantScope())
                    || !root.physicalRows().isPresent()
                    || !seen.add(identity.sourceKey())) {
                throw new IllegalArgumentException("COL_SHADOW_EXPECTED_SCOPE_OR_MULTIPLICITY");
            }
            final var row = json.addObject();
            row.put("sourceKey", identity.sourceKey().storageValue());
            row.put("physicalRows", root.physicalRows().getAsInt());
            declaredPhysicalRows += root.physicalRows().getAsInt();
        }
        if (declaredPhysicalRows > 1000) {
            throw new IllegalArgumentException("COL_SHADOW_EXPECTED_ROW_LIMIT");
        }
        final String expectedJson = json.toString();
        if (expectedJson.length() > 1024 * 1024) {
            throw new IllegalArgumentException("COL_SHADOW_EXPECTED_BYTES_LIMIT");
        }
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(SQL)) {
            statement.setQueryTimeout(20);
            statement.setNString(1, expectedJson);
            statement.setString(2, executionId.toString());
            statement.setString(3, first.sourceInstance());
            statement.setString(4, first.tenantScope());
            statement.setString(5, executionId.toString());
            statement.setString(6, executionId.toString());
            statement.setString(7, executionId.toString());
            statement.setString(8, executionId.toString());
            try (var rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("COL_SHADOW_COMPARISON_UNOBSERVABLE");
                }
                final var result =
                        new Result(
                                rows.getLong(1),
                                rows.getLong(2),
                                rows.getLong(3),
                                rows.getLong(4),
                                rows.getLong(5),
                                rows.getLong(6),
                                rows.getLong(7),
                                rows.getLong(8),
                                rows.getLong(9),
                                rows.getLong(10),
                                rows.getLong(11),
                                rows.getLong(12));
                if (rows.next()) {
                    throw new SQLException("COL_SHADOW_COMPARISON_UNOBSERVABLE");
                }
                return result;
            }
        }
    }

    /** Resolves each staged microbatch through the page audit map captured at staging time. */
    public static List<ObservedRow> readBoundedBatchRows(
            final ColetaTemporalLaboratorySession session,
            final UUID executionId,
            final ScopedSourceIdentity scope,
            final int maximum,
            final String batchPages)
            throws SQLException {
        Objects.requireNonNull(session);
        Objects.requireNonNull(executionId);
        Objects.requireNonNull(scope);
        final String pages = Objects.requireNonNull(batchPages);
        if (scope.entity() != FirstWaveIdentityContract.Entity.COLETAS
                || maximum < 1
                || maximum > 1000
                || pages.isEmpty()
                || pages.length() > 1000) {
            throw new IllegalArgumentException("COL_SHADOW_READER_SCOPE_OR_LIMIT");
        }
        final var observed = new ArrayList<ObservedRow>();
        final var mapper = new ObjectMapper();
        mapper.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        try (var connection = session.getConnection();
                var statement = connection.prepareStatement(OBSERVED_ROWS_SQL)) {
            statement.setQueryTimeout(20);
            statement.setString(1, executionId.toString());
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    if (observed.size() == maximum) {
                        throw new SQLException("COL_SHADOW_READER_ROW_LIMIT");
                    }
                    final int batch = rows.getInt(1);
                    if (batch < 1 || batch > pages.length()) {
                        throw new SQLException("COL_SHADOW_READER_PAGE_UNBOUND");
                    }
                    final int page = pages.charAt(batch - 1) - '0';
                    if (page < 1 || page > 4) {
                        throw new SQLException("COL_SHADOW_READER_PAGE_UNBOUND");
                    }
                    final var sourceKey =
                            new ScopedSourceIdentity.SourceKey(
                                    ScopedSourceIdentity.WireType.INTEGER, rows.getString(2));
                    final String presenceJson = rows.getString(3);
                    if (presenceJson == null || presenceJson.length() > 16384) {
                        throw new SQLException("COL_SHADOW_READER_PRESENCE_INVALID");
                    }
                    final Map<String, ColetaAttributePresence> presence = new java.util.HashMap<>();
                    try {
                        final var json = mapper.readTree(presenceJson);
                        if (!json.isObject()) {
                            throw new SQLException("COL_SHADOW_READER_PRESENCE_INVALID");
                        }
                        json.properties()
                                .forEach(
                                        field -> {
                                            if (!field.getValue().isTextual()) {
                                                throw new IllegalArgumentException(
                                                        "COL_SHADOW_READER_PRESENCE_INVALID");
                                            }
                                            presence.put(
                                                    field.getKey(),
                                                    ColetaAttributePresence.valueOf(
                                                            field.getValue().textValue()));
                                        });
                    } catch (final java.io.IOException | IllegalArgumentException failure) {
                        throw new SQLException("COL_SHADOW_READER_PRESENCE_INVALID", failure);
                    }
                    observed.add(
                            new ObservedRow(
                                    new ScopedSourceIdentity(
                                            scope.sourceInstance(),
                                            scope.tenantScope(),
                                            FirstWaveIdentityContract.Entity.COLETAS,
                                            sourceKey),
                                    page,
                                    presence));
                }
            }
        }
        return List.copyOf(observed);
    }

    public record Result(
            long scopeRows,
            long expectedRoots,
            long expectedPhysicalRows,
            long stagedRoots,
            long stagedPhysicalRows,
            long publishedRoots,
            long expectedOnlyStageTuples,
            long stageOnlyTuples,
            long expectedOnlyCoreRoots,
            long coreOnlyRoots,
            long genericStagedRows,
            long quarantineRows)
            implements java.io.Serializable {
        private static final long serialVersionUID = 1L;

        public String provenance() {
            return "SCOPED_SQL_SET";
        }

        public boolean windowCompletenessProven() {
            return false;
        }

        public boolean childCompletenessProven() {
            return false;
        }

        public boolean matches() {
            return scopeRows == 1
                    && expectedRoots > 0
                    && expectedRoots == stagedRoots
                    && expectedRoots == publishedRoots
                    && expectedPhysicalRows == stagedPhysicalRows
                    && expectedPhysicalRows == genericStagedRows
                    && expectedOnlyStageTuples == 0
                    && stageOnlyTuples == 0
                    && expectedOnlyCoreRoots == 0
                    && coreOnlyRoots == 0
                    && quarantineRows == 0;
        }
    }

    public static final class Mismatch extends SQLException {
        private static final long serialVersionUID = 1L;
        private final Result result;

        public Mismatch(final Result result) {
            super("COL_SHADOW_SET_DIVERGENCE");
            this.result = Objects.requireNonNull(result);
        }

        public Result result() {
            return result;
        }
    }
}
