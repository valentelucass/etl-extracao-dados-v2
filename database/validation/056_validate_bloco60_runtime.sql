-- Read-only catalog and legacy-binding assertions. Run only with the B60 physical package.
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52971,N'B60_TARGET_REQUIRED',1;
IF OBJECT_ID(N'ctl.source_protocol_binding',N'U') IS NULL OR OBJECT_ID(N'ctl.execution_source_protocol',N'U') IS NULL
 OR OBJECT_ID(N'ctl.trg_execution_attempt_bind_protocol',N'TR') IS NULL
 OR OBJECT_ID(N'ctl.trg_runtime_contract_evidence_protocol_gate',N'TR') IS NULL
 THROW 52971,N'B60_ADDITIVE_SCHEMA_REQUIRED',1;
IF EXISTS(SELECT 1 FROM ctl.source_catalog s WHERE NOT EXISTS(SELECT 1 FROM ctl.source_protocol_binding b
 WHERE b.source_instance=s.source_instance AND b.source_kind=s.source_kind AND b.registered_at_utc=s.registered_at_utc))
 THROW 52971,N'B60_ORIGINAL_SOURCE_KIND_PRESERVATION',1;
IF (SELECT COUNT_BIG(*) FROM ctl.execution_attempt)<>(SELECT COUNT_BIG(*) FROM ctl.execution_source_protocol)
 OR EXISTS(SELECT 1 FROM ctl.execution_source_protocol b JOIN ctl.execution_attempt a ON a.execution_id=b.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=a.partition_id JOIN ctl.source_catalog s ON s.source_instance=p.source_instance
 WHERE b.source_instance<>p.source_instance OR b.binding_origin=N'LEGACY' AND b.source_kind<>s.source_kind)
 THROW 52971,N'B60_EXECUTION_BACKFILL_PRESERVATION',1;
IF EXISTS(SELECT 1 FROM sys.foreign_keys WHERE parent_object_id IN(OBJECT_ID(N'ctl.source_protocol_binding'),OBJECT_ID(N'ctl.execution_source_protocol'))
 AND (is_disabled=1 OR is_not_trusted=1 OR delete_referential_action<>0 OR update_referential_action<>0))
 THROW 52971,N'B60_TRUSTED_NON_CASCADING_BINDINGS',1;
IF OBJECT_DEFINITION(OBJECT_ID(N'core.usp_apply_reconcile_publish_usuarios')) NOT LIKE N'%B60_APPLY_SEAL_BEGIN%'
 OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_runtime_recovery')) NOT LIKE N'%a.applied_at_utc>=u.published_at_utc%'
 OR OBJECT_DEFINITION(OBJECT_ID(N'ctl.usp_runtime_recovery')) NOT LIKE N'%terminal_evidence_kind=N''GRAPHQL_PAGE_INFO''%'
 THROW 52971,N'B60_RECOVERY_AND_TYPED_PUBLICATION_REQUIRED',1;
SELECT N'B60_SCHEMA_AND_LEGACY_BINDINGS_PASS';
