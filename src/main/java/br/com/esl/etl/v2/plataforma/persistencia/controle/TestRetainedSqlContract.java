package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.util.Map;

/** Closed Coletas test footprint. Every name is code-owned, never supplied by a caller. */
final class TestRetainedSqlContract {
    static final String SOURCE = "TEST_RETAINED_SOURCE_20261005";
    static final String TENANT = "TEST_RETAINED_SCOPE_20261005";
    static final String[] WRITE_TABLES = {
        "ctl.execution_attempt", "ctl.execution_cycle", "ctl.execution_lease",
        "ctl.execution_page_audit", "ctl.execution_partition", "ctl.execution_source_protocol",
        "ctl.execution_state_event", "ctl.source_catalog", "ctl.source_protocol_binding",
        "stg.coleta_record", "stg.execution_record", "ctl.execution_count"
    };
    static final Map<String, Integer> CALLS =
            Map.of(
                    "ctl.usp_control_plane_register_source", 3,
                    "ctl.usp_control_plane_start_cycle", 4,
                    "ctl.usp_control_plane_start_execution", 18,
                    "ctl.usp_control_plane_heartbeat_lease", 4,
                    "ctl.usp_control_plane_record_page", 10,
                    "ctl.usp_control_plane_record_counts", 9,
                    "ctl.usp_control_plane_transition_execution", 5,
                    "stg.usp_stage_coleta_record", 23);
    static final String IDENTITY =
            "SELECT CASE WHEN DB_NAME()=N'ETL_SISTEMA_V2_SHADOW'"
                    + " AND ORIGINAL_LOGIN()=N'sa' AND SUSER_SNAME()=N'sa'"
                    + " AND CONNECTIONPROPERTY('auth_scheme')=N'SQL'"
                    + " AND CONNECTIONPROPERTY('net_transport')=N'TCP' AND @@TRANCOUNT=0"
                    + " AND CONVERT(nvarchar(128),SERVERPROPERTY('ProductVersion'))=N'16.0.1000.6'"
                    + " AND CONVERT(nvarchar(128),CONNECTIONPROPERTY('local_net_address'))"
                    + " IN(N'127.0.0.1',N'::1',N'::ffff:127.0.0.1')"
                    + " AND CONVERT(nvarchar(128),CONNECTIONPROPERTY('client_net_address'))"
                    + " IN(N'127.0.0.1',N'::1',N'::ffff:127.0.0.1')"
                    + " AND EXISTS(SELECT 1 FROM sys.dm_exec_connections"
                    + " WHERE session_id=@@SPID AND encrypt_option=N'TRUE') THEN 1 ELSE 0 END";
    static final String CLEAN = "SELECT @@TRANCOUNT,XACT_STATE(),@@OPTIONS & 2";
    static final String FRESH =
            "SELECT (SELECT COUNT_BIG(*) FROM ctl.execution_attempt"
                    + " WHERE execution_id=?),(SELECT COUNT_BIG(*) FROM ctl.source_catalog"
                    + " WHERE source_instance=?),(SELECT COUNT_BIG(*) FROM ctl.execution_partition"
                    + " WHERE source_instance=? OR tenant_scope=?)";
    static final String READBACK =
            "WITH b AS (SELECT CONVERT(uniqueidentifier,?) execution_id, CONVERT(nvarchar(128),?)"
                    + " source_instance,CONVERT(nvarchar(128),?) tenant_scope, CONVERT(bigint,?)"
                    + " physical_rows,CONVERT(bigint,?) mapped_rows, CONVERT(bigint,?) roots) SELECT"
                    + " (SELECT COUNT_BIG(*) FROM ctl.execution_attempt a JOIN ctl.execution_partition"
                    + " p ON p.partition_id=a.partition_id WHERE a.execution_id=b.execution_id AND"
                    + " a.current_state=N'CANCELLED' AND p.entity_name=N'coletas' AND"
                    + " p.source_instance=b.source_instance AND p.tenant_scope=b.tenant_scope), (SELECT"
                    + " COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=b.execution_id),"
                    + " (SELECT COUNT_BIG(*) FROM stg.coleta_record c JOIN stg.execution_record s ON"
                    + " s.stage_record_id=c.stage_record_id WHERE s.execution_id=b.execution_id),"
                    + " (SELECT COUNT_BIG(*) FROM ctl.execution_page_audit WHERE"
                    + " execution_id=b.execution_id AND page_number=1 AND page_attempt=1 AND"
                    + " requested_page_size=1 AND physical_rows=b.physical_rows AND"
                    + " distinct_root_keys=b.roots AND terminal_empty_page=0 AND"
                    + " terminal_evidence_kind=N'NONE'), (SELECT COUNT_BIG(*) FROM"
                    + " ctl.execution_page_audit WHERE execution_id=b.execution_id), (SELECT"
                    + " COUNT_BIG(*) FROM ctl.execution_count WHERE execution_id=b.execution_id AND"
                    + " count_phase=N'TEST_CAPTURE' AND physical_rows=b.physical_rows AND"
                    + " distinct_root_keys=b.roots AND duplicate_rows=b.physical_rows-b.roots AND"
                    + " valid_rows=b.roots AND quarantined_root_keys=0 AND"
                    + " unidentified_quarantine_rows=0), (SELECT COUNT_BIG(*) FROM"
                    + " ctl.execution_publication_event WHERE execution_id=b.execution_id) +(SELECT"
                    + " COUNT_BIG(*) FROM ctl.coleta_promotion_result WHERE"
                    + " execution_id=b.execution_id) +(SELECT COUNT_BIG(*) FROM"
                    + " ctl.execution_promotion_result WHERE execution_id=b.execution_id) +(SELECT"
                    + " COUNT_BIG(*) FROM recon.execution_data_quality_evaluation WHERE"
                    + " execution_id=b.execution_id) +(SELECT COUNT_BIG(*) FROM core.coleta WHERE"
                    + " first_seen_execution_id=b.execution_id OR last_seen_execution_id=b.execution_id"
                    + " OR last_changed_execution_id=b.execution_id), (SELECT COUNT_BIG(*) FROM"
                    + " ctl.execution_state_event WHERE execution_id=b.execution_id AND"
                    + " next_state=N'CANCELLED' AND reason_code=N'TEST_PAGE_LIMIT'), (SELECT"
                    + " COUNT_BIG(*) FROM ctl.source_protocol_binding WHERE"
                    + " source_instance=b.source_instance AND source_kind=N'DATA_EXPORT'), (SELECT"
                    + " COUNT_BIG(*) FROM stg.execution_record WHERE execution_id=b.execution_id AND"
                    + " validation_disposition<>N'VALID') FROM b";

    private TestRetainedSqlContract() {}

    static String checkpoint() {
        final var objects = new StringBuilder();
        for (final String table : WRITE_TABLES) {
            if (!objects.isEmpty()) {
                objects.append(',');
            }
            objects.append("OBJECT_ID(N'").append(table).append("')");
        }
        return "SELECT @@TRANCOUNT,XACT_STATE(),@@SPID,"
                + " (SELECT transaction_id FROM sys.dm_tran_current_transaction),"
                + " (SELECT COUNT(DISTINCT resource_associated_entity_id) FROM sys.dm_tran_locks"
                + " WHERE request_session_id=@@SPID AND resource_database_id=DB_ID()"
                + " AND resource_type=N'OBJECT' AND request_mode=N'X' AND request_status=N'GRANT'"
                + " AND request_owner_type=N'TRANSACTION' AND request_owner_id="
                + " (SELECT transaction_id FROM sys.dm_tran_current_transaction)"
                + " AND resource_associated_entity_id IN ("
                + objects
                + ")),@@LOCK_TIMEOUT";
    }
}
