package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Pure preparation. Trust approval is separate from a new Root effect dispatch/reservation. */
public record C78DevCatalogPlan(
        UUID invocationId,
        C78LocalSqlTarget target,
        Mode mode,
        OwnedRecoveryScope ownedRecoveryScope) {
    public enum Mode {
        ORIGINAL12,
        CONTRACT_COVERAGE12,
        SAMPLE12_CURRENT12,
        OWNED_SAMPLE12_RECOVERY12
    }

    /** Only typed occurrence coordinates. This description grants no connection or effects. */
    public record OwnedRecoveryScope(UUID executionId, int mainPID, int clientPort) {
        public OwnedRecoveryScope {
            Objects.requireNonNull(executionId);
            if (executionId.equals(new UUID(0, 0))
                    || mainPID < 1
                    || clientPort < 1024
                    || clientPort > 65535
                    || clientPort == 1433) {
                throw new IllegalArgumentException("C78_OWNED_RECOVERY_SCOPE");
            }
        }
    }

    public static final int MAX_SELECTS = 12;
    public static final int QUERY_SECONDS = 10;
    public static final int UNIT_SECONDS = 60;
    public static final int MAX_ROWS_PER_QUERY = 512;
    public static final int MAX_QUERY_CHARS = 65536;
    public static final int MAX_REPORT_CHARS = 262144;
    public static final int MAX_COVERAGE_REPORT_CHARS = 1_500_000;
    public static final int MAX_CURRENT_REPORT_CHARS = 1_900_000;
    public static final int MAX_CURRENT_MODULE_ROWS = 2048;
    public static final int CURRENT_MODULE_CHUNK_BYTES = 1024;

    public C78DevCatalogPlan(final UUID invocationId, final C78LocalSqlTarget target) {
        this(invocationId, target, Mode.ORIGINAL12);
    }

    public C78DevCatalogPlan(
            final UUID invocationId, final C78LocalSqlTarget target, final Mode mode) {
        this(invocationId, target, mode, null);
    }

    public C78DevCatalogPlan {
        Objects.requireNonNull(invocationId);
        Objects.requireNonNull(target);
        Objects.requireNonNull(mode);
        if ((mode == Mode.OWNED_SAMPLE12_RECOVERY12) != (ownedRecoveryScope != null)) {
            throw new IllegalArgumentException("C78_OWNED_RECOVERY_MODE_SCOPE");
        }
    }

    public static C78DevCatalogPlan prepareCoverage(
            final UUID invocationId, final C78LocalSqlTarget target) {
        return new C78DevCatalogPlan(invocationId, target, Mode.CONTRACT_COVERAGE12);
    }

    public static C78DevCatalogPlan prepareSample12Current(
            final UUID invocationId, final C78LocalSqlTarget target) {
        return new C78DevCatalogPlan(invocationId, target, Mode.SAMPLE12_CURRENT12);
    }

    public static C78DevCatalogPlan prepareOwnedSample12Recovery(
            final UUID invocationId,
            final C78LocalSqlTarget target,
            final UUID executionId,
            final int mainPID,
            final int clientPort) {
        return new C78DevCatalogPlan(
                invocationId,
                target,
                Mode.OWNED_SAMPLE12_RECOVERY12,
                new OwnedRecoveryScope(executionId, mainPID, clientPort));
    }

    public List<Query> queries() {
        return List.of(
                Query.CONTEXT_PRE,
                Query.TABLE_PRESENCE,
                Query.COLUMNS,
                Query.PROCEDURE_PRESENCE,
                Query.PARAMETERS,
                currentMetadata() ? Query.CURRENT_INDEXES : Query.INDEXES,
                Query.FOREIGN_KEYS,
                Query.CHECKS,
                Query.TRIGGERS,
                switch (mode) {
                    case ORIGINAL12 -> Query.DQ_METADATA;
                    case CONTRACT_COVERAGE12 -> Query.MODULE_CONTRACTS;
                    case SAMPLE12_CURRENT12 -> Query.CURRENT_MODULES;
                    case OWNED_SAMPLE12_RECOVERY12 -> Query.OWNED_RECOVERY_SUMMARY;
                },
                Query.WRITER_SNAPSHOT,
                Query.CONTEXT_POST);
    }

    public String sql(final Query query) {
        if (!queries().contains(query)) {
            throw new IllegalArgumentException("C78_CATALOG_QUERY_OUTSIDE_PLAN");
        }
        if (mode == Mode.ORIGINAL12) {
            return query.sql();
        }
        if (query == Query.OWNED_RECOVERY_SUMMARY) {
            return ownedRecoverySql();
        }
        final String selected =
                query.sql()
                        .replace(TABLES, COVERAGE_TABLES)
                        .replace(PROCEDURES, COVERAGE_PROCEDURES);
        return currentMetadata() && query == Query.PARAMETERS
                ? selected.replace(COVERAGE_PROCEDURES, currentExpectedSql())
                        .replace("sys.procedures p", "sys.objects p")
                : selected;
    }

    private boolean currentMetadata() {
        return mode == Mode.SAMPLE12_CURRENT12 || mode == Mode.OWNED_SAMPLE12_RECOVERY12;
    }

    public int reportCharacters() {
        return switch (mode) {
            case ORIGINAL12 -> MAX_REPORT_CHARS;
            case CONTRACT_COVERAGE12 -> MAX_COVERAGE_REPORT_CHARS;
            case SAMPLE12_CURRENT12, OWNED_SAMPLE12_RECOVERY12 -> MAX_CURRENT_REPORT_CHARS;
        };
    }

    public static int queryCharacters(final Query query) {
        if (query == Query.CURRENT_MODULES) {
            return MAX_CURRENT_REPORT_CHARS;
        }
        return query == Query.MODULE_CONTRACTS ? MAX_COVERAGE_REPORT_CHARS : MAX_QUERY_CHARS;
    }

    public static int rowsPerQuery(final Query query) {
        if (query == Query.OWNED_RECOVERY_SUMMARY) {
            return ownedRecoveryMetricNames().size();
        }
        return query == Query.CURRENT_MODULES ? MAX_CURRENT_MODULE_ROWS : MAX_ROWS_PER_QUERY;
    }

    public static int cellCharacters(final Query query) {
        if (query == Query.CURRENT_INDEXES) {
            return 512;
        }
        if (query == Query.CURRENT_MODULES) {
            return CURRENT_MODULE_CHUNK_BYTES * 2;
        }
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

    // The 29 reviewed procedures plus the finite V020/V015/V004 dependencies used by SAMPLE12.
    // Tariff NULL returns before its descendants; this route never promotes or publishes.
    static List<String> currentModuleNames() {
        return List.of(
                        "ctl.usp_control_plane_start_execution",
                        "ctl.usp_control_plane_register_source",
                        "ctl.usp_control_plane_start_cycle",
                        "ctl.usp_control_plane_heartbeat_lease",
                        "ctl.usp_control_plane_record_page",
                        "ctl.usp_control_plane_record_counts",
                        "ctl.usp_control_plane_transition_execution",
                        "ctl.usp_control_plane_register_incremental_frontier",
                        "ctl.usp_control_plane_recover_stale_executions",
                        "ctl.usp_runtime_recovery",
                        "ctl.usp_runtime_status",
                        "ctl.usp_runtime_observation",
                        "ctl.usp_audit_execution_started",
                        "ctl.usp_audit_page_read",
                        "ctl.usp_audit_execution_completed",
                        "ctl.usp_audit_execution_failed",
                        "stg.usp_stage_coleta_record",
                        "stg.usp_stage_coleta_exact_time",
                        "stg.usp_complete_coleta_temporal_data_export",
                        "core.usp_prepare_staged_execution",
                        "core.usp_apply_reconcile_publish_coletas",
                        "recon.usp_evaluate_execution_data_quality",
                        "recon.usp_observe_execution_data_quality",
                        "recon.usp_runtime_raise_alert",
                        "ctl.usp_runtime_temporal_plan",
                        "ctl.usp_runtime_temporal_gaps",
                        "recon.usp_record_execution_metric",
                        "recon.usp_raise_observability_alert",
                        "ctl.usp_observe_platform_health",
                        "ctl.fn_runtime_consumed_scope",
                        "ctl.fn_runtime_recovery_identity",
                        "ctl.fn_runtime_recovery_field",
                        "ctl.fn_runtime_tariff_valid",
                        "stg.usp_stage_record")
                .stream()
                .sorted()
                .toList();
    }

    private static String currentExpectedSql() {
        final String names =
                currentModuleNames().stream()
                        .map(
                                name -> {
                                    final int dot = name.indexOf('.');
                                    return "('"
                                            + name.substring(0, dot)
                                            + "','"
                                            + name.substring(dot + 1)
                                            + "')";
                                })
                        .collect(java.util.stream.Collectors.joining(",\n"));
        return "WITH expected(schema_name,object_name) AS (SELECT * FROM (VALUES\n"
                + names
                + ") v(schema_name,object_name))\n";
    }

    private static String currentModulesSql() {
        return currentExpectedSql().stripTrailing()
                + """
,
digit(n) AS (SELECT * FROM (VALUES (0),(1),(2),(3),(4),(5),(6),(7)) v(n)),
chunks(n) AS (SELECT a.n+8*b.n+64*c.n FROM digit a CROSS JOIN digit b
CROSS JOIN (SELECT n FROM digit WHERE n<2) c)
SELECT e.schema_name,e.object_name,n.n,COALESCE(DATALENGTH(m.definition),0),
COALESCE(CONVERT(varchar(64),HASHBYTES('SHA2_256',m.definition),2),'UNAVAILABLE'),
COALESCE(CONVERT(varchar(2048),SUBSTRING(CONVERT(varbinary(max),m.definition),n.n*1024+1,1024),2),'UNAVAILABLE')
FROM expected e LEFT JOIN sys.schemas s ON s.name=e.schema_name
LEFT JOIN sys.objects o ON o.schema_id=s.schema_id AND o.name=e.object_name
AND o.type IN ('P','FN','IF','TF')
LEFT JOIN sys.sql_modules m ON m.object_id=o.object_id
CROSS JOIN chunks n WHERE n.n=0 OR n.n*1024<DATALENGTH(m.definition)
ORDER BY e.schema_name,e.object_name,n.n
""";
    }

    private static List<String> ownedExecutionTables() {
        return List.of(
                "ctl.coleta_promotion_result",
                "ctl.execution_attempt",
                "ctl.execution_audit",
                "ctl.execution_count",
                "ctl.execution_lease",
                "ctl.execution_page_audit",
                "ctl.execution_promotion_result",
                "ctl.execution_publication_event",
                "ctl.execution_source_protocol",
                "ctl.execution_state_event",
                "ctl.page_audit",
                "ctl.runtime_coleta_publication_receipt",
                "ctl.runtime_contract_evidence",
                "recon.execution_data_quality_check_result",
                "recon.execution_data_quality_evaluation",
                "recon.execution_metric_snapshot",
                "stg.coleta_record",
                "stg.execution_record");
    }

    private static String rowMetric(final String table) {
        return "ROWS_" + table.toUpperCase(java.util.Locale.ROOT).replace('.', '_');
    }

    static List<String> ownedRecoveryMetricNames() {
        final var names = new java.util.ArrayList<String>();
        ownedExecutionTables().forEach(table -> names.add(rowMetric(table)));
        names.addAll(
                List.of(
                        "ROWS_STG_COLETA_EXACT_TIME",
                        "ROWS_CTL_EXECUTION_CYCLE_LINKED",
                        "ROWS_CTL_EXECUTION_PARTITION_LINKED",
                        "OWNED_ACTIVE_EXECUTION_LEASES",
                        "OWNED_SESSIONS",
                        "OWNED_OPEN_TRANSACTION_COUNT",
                        "OWNED_ACTIVE_REQUESTS",
                        "OWNED_SESSION_TRANSACTIONS",
                        "OWNED_DATABASE_LOCKS"));
        return names.stream().sorted().toList();
    }

    private String ownedRecoverySql() {
        final var measures = new java.util.ArrayList<String>();
        for (final String table : ownedExecutionTables()) {
            measures.add(
                    "SELECT '"
                            + rowMetric(table)
                            + "' metric,COUNT_BIG(*) total FROM "
                            + table
                            + " WHERE execution_id=(SELECT execution_id FROM unit)");
        }
        measures.addAll(
                List.of(
                        "SELECT 'ROWS_STG_COLETA_EXACT_TIME',COUNT_BIG(*) FROM"
                                + " stg.coleta_exact_time t JOIN stg.coleta_record r ON"
                                + " r.stage_record_id=t.stage_record_id WHERE r.execution_id=(SELECT"
                                + " execution_id FROM unit)",
                        "SELECT 'ROWS_CTL_EXECUTION_CYCLE_LINKED',COUNT_BIG(*) FROM"
                                + " ctl.execution_cycle c WHERE EXISTS(SELECT 1 FROM"
                                + " ctl.execution_attempt a WHERE a.cycle_id=c.cycle_id AND"
                                + " a.execution_id=(SELECT execution_id FROM unit))",
                        "SELECT 'ROWS_CTL_EXECUTION_PARTITION_LINKED',COUNT_BIG(*) FROM"
                                + " ctl.execution_partition p WHERE p.current_execution_id=(SELECT"
                                + " execution_id FROM unit) OR EXISTS(SELECT 1 FROM"
                                + " ctl.execution_attempt a WHERE a.partition_id=p.partition_id AND"
                                + " a.execution_id=(SELECT execution_id FROM unit))",
                        "SELECT 'OWNED_ACTIVE_EXECUTION_LEASES',COUNT_BIG(*) FROM"
                                + " ctl.execution_lease WHERE execution_id=(SELECT execution_id FROM"
                                + " unit) AND released_at_utc IS NULL",
                        "SELECT 'OWNED_SESSIONS',COUNT_BIG(*) FROM owned_sessions",
                        "SELECT"
                                + " 'OWNED_OPEN_TRANSACTION_COUNT',COALESCE(SUM(CAST(s.open_transaction_count"
                                + " AS bigint)),0) FROM sys.dm_exec_sessions s JOIN owned_sessions o ON"
                                + " o.session_id=s.session_id",
                        "SELECT 'OWNED_ACTIVE_REQUESTS',COUNT_BIG(*) FROM sys.dm_exec_requests r"
                                + " JOIN owned_sessions o ON o.session_id=r.session_id",
                        "SELECT 'OWNED_SESSION_TRANSACTIONS',COUNT_BIG(*) FROM"
                                + " sys.dm_tran_session_transactions t JOIN owned_sessions o ON"
                                + " o.session_id=t.session_id",
                        "SELECT 'OWNED_DATABASE_LOCKS',COUNT_BIG(*) FROM sys.dm_tran_locks l"
                                + " JOIN owned_sessions o ON o.session_id=l.request_session_id"
                                + " WHERE l.resource_database_id=DB_ID()"));
        return "WITH unit AS (SELECT CAST('"
                + ownedRecoveryScope.executionId()
                + "' AS uniqueidentifier) execution_id),\n"
                + "owned_sessions AS (SELECT s.session_id FROM sys.dm_exec_sessions s JOIN"
                + " sys.dm_exec_connections c ON c.session_id=s.session_id WHERE"
                + " s.is_user_process=1 AND s.database_id=DB_ID() AND s.host_process_id="
                + ownedRecoveryScope.mainPID()
                + " AND c.client_tcp_port="
                + ownedRecoveryScope.clientPort()
                + " AND c.client_net_address=N'127.0.0.1' AND c.local_net_address=N'127.0.0.1'"
                + " AND c.local_tcp_port=1433 AND c.net_transport=N'TCP'),\nmeasures AS ("
                + String.join("\nUNION ALL\n", measures)
                + ")\n"
                + "SELECT metric,total FROM measures ORDER BY metric COLLATE"
                + " Latin1_General_100_BIN2\n";
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
        CURRENT_INDEXES(
                TABLES
                        + """
SELECT e.schema_name,e.object_name,i.name,i.is_unique,i.is_primary_key,
  i.is_disabled,i.has_filter,x.key_ordinal,c.name,x.is_included_column,
  COALESCE(DATALENGTH(i.filter_definition),0),
  COALESCE(CONVERT(varchar(512),CONVERT(varbinary(max),i.filter_definition),2),'')
FROM expected e JOIN sys.schemas s ON s.name=e.schema_name
JOIN sys.tables t ON t.schema_id=s.schema_id AND t.name=e.object_name
JOIN sys.indexes i ON i.object_id=t.object_id AND i.index_id>0
JOIN sys.index_columns x ON x.object_id=i.object_id AND x.index_id=i.index_id
JOIN sys.columns c ON c.object_id=x.object_id AND c.column_id=x.column_id
ORDER BY e.schema_name,e.object_name,i.name,x.index_column_id
""",
                12),
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
        CURRENT_MODULES(currentModulesSql(), 6),
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
                6),
        OWNED_RECOVERY_SUMMARY("SELECT 'OWNED_RECOVERY_TYPED_PLAN_REQUIRED',0 WHERE 1=0", 2);

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
