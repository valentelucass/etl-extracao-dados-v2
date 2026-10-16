package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure preparation. Trust approval is separate from a new Root effect dispatch/reservation. */
public record C78DevCatalogPlan(UUID invocationId, C78LocalSqlTarget target, Mode mode) {
    public enum Mode {
        ORIGINAL12,
        CONTRACT_COVERAGE12
    }

    public static final int MAX_SELECTS = 12;
    public static final int QUERY_SECONDS = 10;
    public static final int UNIT_SECONDS = 60;
    public static final int MAX_ROWS_PER_QUERY = 512;
    public static final int MAX_QUERY_CHARS = 65536;
    public static final int MAX_REPORT_CHARS = 262144;
    public static final int MAX_COVERAGE_REPORT_CHARS = 1_500_000;

    public C78DevCatalogPlan(final UUID invocationId, final C78LocalSqlTarget target) {
        this(invocationId, target, Mode.ORIGINAL12);
    }

    public C78DevCatalogPlan {
        Objects.requireNonNull(invocationId);
        Objects.requireNonNull(target);
        Objects.requireNonNull(mode);
    }

    public static C78DevCatalogPlan prepareCoverage(
            final UUID invocationId, final C78LocalSqlTarget target) {
        return new C78DevCatalogPlan(invocationId, target, Mode.CONTRACT_COVERAGE12);
    }

    public List<Query> queries() {
        return List.of(
                Query.CONTEXT_PRE,
                Query.TABLE_PRESENCE,
                Query.COLUMNS,
                Query.PROCEDURE_PRESENCE,
                Query.PARAMETERS,
                Query.INDEXES,
                Query.FOREIGN_KEYS,
                Query.CHECKS,
                Query.TRIGGERS,
                mode == Mode.ORIGINAL12 ? Query.DQ_METADATA : Query.MODULE_CONTRACTS,
                Query.WRITER_SNAPSHOT,
                Query.CONTEXT_POST);
    }

    public String sql(final Query query) {
        if (!queries().contains(query)) {
            throw new IllegalArgumentException("C78_CATALOG_QUERY_OUTSIDE_PLAN");
        }
        return mode == Mode.ORIGINAL12
                ? query.sql()
                : query.sql()
                        .replace(TABLES, COVERAGE_TABLES)
                        .replace(PROCEDURES, COVERAGE_PROCEDURES);
    }

    public int reportCharacters() {
        return mode == Mode.ORIGINAL12 ? MAX_REPORT_CHARS : MAX_COVERAGE_REPORT_CHARS;
    }

    public static int queryCharacters(final Query query) {
        return query == Query.MODULE_CONTRACTS ? MAX_COVERAGE_REPORT_CHARS : MAX_QUERY_CHARS;
    }

    public static int cellCharacters(final Query query) {
        return query == Query.MODULE_CONTRACTS ? 512 : 256;
    }

    public static C78DevCatalogPlan prepare(
            final UUID invocationId, final C78LocalSqlTarget target) {
        return new C78DevCatalogPlan(invocationId, target);
    }

    static List<String> moduleContractNames() {
        return List.of(
                "ctl.usp_observe_platform_health",
                "ctl.usp_runtime_recovery",
                "ctl.usp_runtime_status",
                "ctl.usp_runtime_temporal_gaps",
                "ctl.usp_runtime_temporal_plan",
                "recon.usp_raise_observability_alert",
                "recon.usp_record_execution_metric");
    }

    private static final String CONTEXT =
            """
SELECT CASE WHEN DB_NAME() COLLATE Latin1_General_100_BIN2 = N'ETL_SISTEMA_V2_SHADOW' THEN 1 ELSE 0 END,
  CONVERT(varchar(32), SERVERPROPERTY('ProductVersion')),
  CASE WHEN ORIGINAL_LOGIN() = N'sa' AND SUSER_SNAME() = N'sa' THEN 1 ELSE 0 END,
  CONVERT(varchar(32), c.auth_scheme),
  CONVERT(varchar(20), SERVERPROPERTY('ProcessID')),
  CONVERT(varchar(20), @@SPID), CONVERT(varchar(20), @@TRANCOUNT)
FROM sys.dm_exec_connections AS c WHERE c.session_id = @@SPID
""";

    private static final String TABLES =
            """
WITH expected(schema_name, object_name) AS (SELECT * FROM (VALUES
  ('ctl','source_catalog'),('ctl','execution_cycle'),('ctl','execution_partition'),
  ('ctl','execution_attempt'),('ctl','execution_state_event'),('ctl','execution_lease'),
  ('ctl','execution_page_audit'),('ctl','execution_count'),
  ('ctl','source_watermark_observation'),('ctl','incremental_publication_watermark'),
  ('ctl','runtime_contract_evidence'),('ctl','runtime_coleta_publication_receipt'),
  ('ctl','coleta_promotion_result'),('ctl','execution_audit'),('ctl','page_audit'),
  ('ctl','data_quality_policy'),('ctl','data_quality_check_policy'),
  ('stg','coleta_record'),('stg','coleta_exact_time'),('core','coleta'),
  ('recon','execution_data_quality_evaluation'),
  ('recon','execution_data_quality_check_result'),('recon','execution_metric_snapshot'),
  ('recon','observability_alert')) AS v(schema_name, object_name))
""";

    private static final String PROCEDURES =
            """
WITH expected(schema_name, object_name) AS (SELECT * FROM (VALUES
  ('ctl','usp_control_plane_start_execution'),('ctl','usp_control_plane_register_source'),
  ('ctl','usp_control_plane_start_cycle'),('ctl','usp_control_plane_heartbeat_lease'),
  ('ctl','usp_control_plane_record_page'),('ctl','usp_control_plane_record_counts'),
  ('ctl','usp_control_plane_transition_execution'),
  ('ctl','usp_control_plane_register_incremental_frontier'),
  ('ctl','usp_control_plane_recover_stale_executions'),('ctl','usp_runtime_recovery'),
  ('ctl','usp_runtime_status'),('ctl','usp_runtime_observation'),
  ('ctl','usp_audit_execution_started'),('ctl','usp_audit_page_read'),
  ('ctl','usp_audit_execution_completed'),('ctl','usp_audit_execution_failed'),
  ('stg','usp_stage_coleta_record'),('stg','usp_stage_coleta_exact_time'),
  ('stg','usp_complete_coleta_temporal_data_export'),
  ('core','usp_prepare_staged_execution'),('core','usp_apply_reconcile_publish_coletas'),
  ('recon','usp_evaluate_execution_data_quality'),
  ('recon','usp_observe_execution_data_quality'),('recon','usp_runtime_raise_alert'))
  AS v(schema_name, object_name))
""";

    private static final String COVERAGE_TABLES =
            TABLES.replace(
                    "('recon','observability_alert'))",
                    """
('recon','observability_alert'),('stg','execution_record'),
('ctl','source_protocol_binding'),('ctl','execution_source_protocol'),
('ctl','execution_publication_event'),('ctl','execution_promotion_result'))
"""
                            .strip());
    private static final String COVERAGE_PROCEDURES =
            PROCEDURES.replace(
                    "('recon','usp_runtime_raise_alert'))",
                    """
('recon','usp_runtime_raise_alert'),('ctl','usp_runtime_temporal_plan'),
('ctl','usp_runtime_temporal_gaps'),('recon','usp_record_execution_metric'),
('recon','usp_raise_observability_alert'),('ctl','usp_observe_platform_health'))
"""
                            .strip());

    /** Closed SELECTs: each plan selects twelve; no caller-provided SQL or driver metadata. */
    public enum Query {
        CONTEXT_PRE(CONTEXT, 7),
        TABLE_PRESENCE(
                TABLES
                        + """
SELECT e.schema_name, e.object_name, CASE WHEN t.object_id IS NULL THEN 0 ELSE 1 END
FROM expected e LEFT JOIN sys.schemas s ON s.name=e.schema_name
LEFT JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
ORDER BY e.schema_name, e.object_name
""",
                3),
        COLUMNS(
                TABLES
                        + """
SELECT e.schema_name,e.object_name,c.column_id,c.name,y.name,
  c.max_length,c.precision,c.scale,c.is_nullable,c.is_identity,
  COALESCE(c.collation_name,'')
FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
JOIN sys.columns c ON c.object_id=t.object_id JOIN sys.types y ON y.user_type_id=c.user_type_id
ORDER BY e.schema_name,e.object_name,c.column_id
""",
                11),
        PROCEDURE_PRESENCE(
                PROCEDURES
                        + """
SELECT e.schema_name,e.object_name,CASE WHEN p.object_id IS NULL THEN 0 ELSE 1 END,
  COALESCE(CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2),'UNAVAILABLE')
FROM expected e LEFT JOIN sys.schemas s ON s.name=e.schema_name
LEFT JOIN sys.procedures p ON p.schema_id=s.schema_id AND p.name=e.object_name
LEFT JOIN sys.sql_modules m ON m.object_id=p.object_id
ORDER BY e.schema_name,e.object_name
""",
                4),
        PARAMETERS(
                PROCEDURES
                        + """
SELECT e.schema_name,e.object_name,a.parameter_id,a.name,y.name,
  a.max_length,a.precision,a.scale,a.is_output
FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
JOIN sys.procedures p ON p.schema_id=s.schema_id AND p.name=e.object_name
JOIN sys.parameters a ON a.object_id=p.object_id JOIN sys.types y ON y.user_type_id=a.user_type_id
ORDER BY e.schema_name,e.object_name,a.parameter_id
""",
                9),
        INDEXES(
                TABLES
                        + """
SELECT e.schema_name,e.object_name,i.name,i.is_unique,i.is_primary_key,
  i.is_disabled,i.has_filter,x.key_ordinal,c.name,x.is_included_column
FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
JOIN sys.indexes i ON i.object_id=t.object_id AND i.index_id>0
JOIN sys.index_columns x ON x.object_id=i.object_id AND x.index_id=i.index_id
JOIN sys.columns c ON c.object_id=x.object_id AND c.column_id=x.column_id
ORDER BY e.schema_name,e.object_name,i.name,x.index_column_id
""",
                10),
        FOREIGN_KEYS(
                TABLES
                        + """
SELECT e.schema_name,e.object_name,f.name,f.is_disabled,f.is_not_trusted,
  rs.name,rt.name,x.constraint_column_id,pc.name,rc.name
FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
JOIN sys.foreign_keys f ON f.parent_object_id=t.object_id
JOIN sys.foreign_key_columns x ON x.constraint_object_id=f.object_id
JOIN sys.tables rt ON rt.object_id=x.referenced_object_id
JOIN sys.schemas rs ON rs.schema_id=rt.schema_id
JOIN sys.columns pc ON pc.object_id=x.parent_object_id AND pc.column_id=x.parent_column_id
JOIN sys.columns rc ON rc.object_id=x.referenced_object_id AND rc.column_id=x.referenced_column_id
ORDER BY e.schema_name,e.object_name,f.name,x.constraint_column_id
""",
                10),
        CHECKS(
                TABLES
                        + """
                          SELECT e.schema_name,e.object_name,c.name,c.is_disabled,c.is_not_trusted,
                            CONVERT(varchar(64),HASHBYTES('SHA2_256',c.definition),2)
                          FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
                          JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
                          JOIN sys.check_constraints c ON c.parent_object_id=t.object_id
                          ORDER BY e.schema_name,e.object_name,c.name
                          """,
                6),
        TRIGGERS(
                TABLES
                        + """
SELECT e.schema_name,e.object_name,r.name,r.is_disabled,
  COALESCE(CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2),'UNAVAILABLE')
FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
JOIN sys.triggers r ON r.parent_id=t.object_id
LEFT JOIN sys.sql_modules m ON m.object_id=r.object_id
ORDER BY e.schema_name,e.object_name,r.name
""",
                5),
        DQ_METADATA(
                TABLES
                        + """
SELECT e.schema_name,e.object_name,COUNT(c.column_id)
FROM expected e LEFT JOIN sys.schemas s ON s.name=e.schema_name
LEFT JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
LEFT JOIN sys.columns c ON c.object_id=t.object_id
WHERE e.object_name IN ('data_quality_policy','data_quality_check_policy',
  'execution_data_quality_evaluation','execution_data_quality_check_result')
GROUP BY e.schema_name,e.object_name ORDER BY e.schema_name,e.object_name
""",
                3),
        WRITER_SNAPSHOT(
                """
                SELECT COUNT_BIG(*) FROM sys.dm_tran_locks
                WHERE resource_database_id=DB_ID() AND request_session_id<>@@SPID
                  AND request_mode IN ('X','IX','SIX','U','IU','SIU','UIX','Sch-M','BU')
                """,
                1),
        CONTEXT_POST(CONTEXT, 7),
        MODULE_CONTRACTS(
                """
WITH expected(schema_name,object_name) AS (SELECT * FROM (VALUES
('ctl','usp_runtime_temporal_plan'),('ctl','usp_runtime_temporal_gaps'),
('recon','usp_record_execution_metric'),('recon','usp_raise_observability_alert'),
('ctl','usp_observe_platform_health'),('ctl','usp_runtime_recovery'),
('ctl','usp_runtime_status')) v(schema_name,object_name)),
digit(n) AS (SELECT * FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7)) v(n)),
chunks(n) AS (SELECT a.n+8*b.n+64*c.n FROM digit a CROSS JOIN digit b CROSS JOIN digit c)
SELECT e.schema_name,e.object_name,n.n,COALESCE(DATALENGTH(m.definition),0),
COALESCE(CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2),'UNAVAILABLE'),
COALESCE(CONVERT(varchar(512),SUBSTRING(CONVERT(varbinary(max),m.definition),n.n*256+1,256),2),'UNAVAILABLE')
FROM expected e LEFT JOIN sys.schemas s ON s.name=e.schema_name
LEFT JOIN sys.procedures p ON p.schema_id=s.schema_id AND p.name=e.object_name
LEFT JOIN sys.sql_modules m ON m.object_id=p.object_id
CROSS JOIN chunks n WHERE n.n=0 OR n.n*256<DATALENGTH(m.definition)
ORDER BY e.schema_name,e.object_name,n.n
""",
                6);

        private final String sql;
        private final int columns;

        Query(final String sql, final int columns) {
            this.sql = sql;
            this.columns = columns;
        }

        public String sql() {
            return sql;
        }

        public int columns() {
            return columns;
        }
    }
}
