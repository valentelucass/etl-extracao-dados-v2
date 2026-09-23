:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte' THROW 52970,N'B60_EXACT_READBACK_TARGET',1;
DECLARE @owned TABLE(execution_id UNIQUEIDENTIFIER PRIMARY KEY);
INSERT @owned VALUES
('55290ed3-5262-4dfc-833d-d23003a8b4cc'),
('8d2fffea-18aa-4ca8-89ba-0bd05d82322d'),
('dbd9a862-b9c9-404b-93e2-8cd4338209d1'),
('4f75e1fa-379f-438d-909d-0640775b6393'),
('1c7db0e0-4993-430e-ad2e-3d5dc9f9c340'),
('d1265b2d-679b-4a44-b979-444597fe14eb'),
('7751f665-750b-40c8-b36d-2889d34d3c8d'),
('e0918f4f-ae0f-40ed-bd06-f8bd44bf1ed1'),
('71c4bc23-aeee-4fee-b8a9-1df99ecca20d'),
('31f1a03e-be14-4ba9-8670-bc81841cd524'),
('852261f4-d9f2-4131-8255-4aaf56527a2b'),
('9f23f5ea-d999-4139-84c2-d8cb6c41d19a'),
('0f24ea37-1c20-46fd-a6e6-0aec0c743e41'),
('da216432-3a76-48fb-8d64-a185c9e8debc'),
('ec0beb79-d46e-4a38-87a2-5f56240e95ab'),
('f34c7020-1bd5-443f-9319-17909b955a0a'),
('68eb494e-8ca9-4528-8b52-2c192fad640d'),
('2a3212a1-f164-4531-b25a-2eb22ce010e3'),
('c31014a4-2c25-483c-aa42-c5285cba0aca'),
('5d6ffc5b-1f50-487a-b3a4-2936eaee3694'),
('b9d13720-81d2-40b0-bb60-6a6401976662'),
('bfd00783-c668-442b-a084-ddb221ed2b46'),
('3c164266-6c23-42b2-9557-40480a3c6a49'),
('d9c71ca0-cf09-4ac0-a6b8-53dba4517495'),
('79b74264-1247-404c-b9fd-449e8071892b'),
('c37daccc-7c29-431c-9aa1-3d2c74020de5'),
('d2b5ad97-e286-4a0a-8e62-7e4e9d01ce3d'),
('1b5bb5cf-6dc0-4141-8840-a65fa0a28c08'),
('24df706c-1af1-4d73-917e-bedfaf397cf9'),
('8663ba14-4057-40dd-987b-64a887e2563c'),
('0e8c30cc-6011-4818-827f-bc17f383fd78'),
('15db9db5-5a36-49c6-8f1a-bdbe0565e966'),
('4c458e8c-38db-45c6-8cbf-2713ea69f221'),
('1ab16b90-f613-433c-adc4-395d682b0455'),
('9b362e67-2cfb-4efb-9642-dea076cdb3dc'),
('60fd1054-7d86-4144-87cc-c4404cf73733'),
('88b0b981-7b24-4cf6-b1d3-9989f18839c3'),
('3de25733-5e1b-418b-8059-7598815ae870'),
('1c88f41d-fe4b-4f1e-86e0-0f9f60a7bc8c'),
('284b6e53-e805-4e9e-a4c5-5d70c22a9bb6'),
('5d29ca52-0b4b-497b-b7ae-ff326fda0adf'),
('3e1d89a7-5ac1-4589-8d33-b90b08e68378'),
('f4d36538-a48a-42df-900c-0c78a4dc0c50'),
('4e304e8e-cd53-43f6-bb3a-185c41287e1f'),
('5484450b-d53d-4ee6-9aab-22dedff21b31'),
('f7375404-9fc3-497e-adee-f12fa2024e7c'),
('52002b83-f50f-4c87-943b-ef44ca8f2ed0'),
('75c0ed52-a176-4700-b563-0ef2d8fea9e7'),
('33d6e8ab-c8ea-42f0-acd7-8b459518cb6f');
INSERT @owned VALUES ('b787bfd8-b157-4fb0-a4a8-e777945abed7');
SELECT (SELECT COUNT_BIG(*) FROM ctl.execution_attempt a JOIN @owned o ON o.execution_id=a.execution_id) attempts,
 (SELECT COUNT_BIG(*) FROM ctl.execution_publication_event a JOIN @owned o ON o.execution_id=a.execution_id) publications,
 (SELECT COUNT_BIG(*) FROM ctl.execution_page_audit a JOIN @owned o ON o.execution_id=a.execution_id) auditedPages,
 (SELECT COALESCE(SUM(physical_rows),0) FROM ctl.execution_page_audit a JOIN @owned o ON o.execution_id=a.execution_id) auditedRows,
 (SELECT COUNT_BIG(*) FROM ctl.runtime_contract_evidence a JOIN @owned o ON o.execution_id=a.execution_id) seals,
 (SELECT COUNT_BIG(*) FROM core.usuario_history a JOIN @owned o ON o.execution_id=a.execution_id) usersHistory,
 (SELECT COUNT_BIG(*) FROM sys.dm_exec_sessions WHERE is_user_process=1 AND original_login_name IN(N'RTR-SVW-002\etl_v2_exec',N'RTR-SVW-002\etl_v2_view')) restrictedSessions,
 (SELECT COUNT_BIG(*) FROM ctl.source_protocol_binding WHERE source_instance=N'LOCAL_V2') sourceProtocols
 FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
IF EXISTS(SELECT 1 FROM ctl.execution_source_protocol b JOIN ctl.execution_attempt a ON a.execution_id=b.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=a.partition_id JOIN ctl.source_catalog s ON s.source_instance=p.source_instance
 WHERE b.binding_origin=N'LEGACY' AND (b.source_instance<>p.source_instance OR b.source_kind<>s.source_kind))
 THROW 52970,N'B60_LEGACY_REKEY_OR_REMAP',1;
IF EXISTS(SELECT 1 FROM ctl.execution_attempt a JOIN @owned o ON o.execution_id=a.execution_id
 JOIN ctl.execution_partition p ON p.partition_id=a.partition_id JOIN ctl.execution_source_protocol b ON b.execution_id=a.execution_id
 WHERE p.source_instance<>N'LOCAL_V2' OR p.tenant_scope<>N'LOCAL_V2' OR p.environment_name<>N'LOCAL_SHADOW'
 OR b.source_kind<>CASE WHEN p.entity_name=N'usuarios' THEN N'GRAPHQL' ELSE N'DATA_EXPORT' END)
 THROW 52970,N'B60_OWN_PROTOCOL_OR_NAMESPACE_MISMATCH',1;
SELECT N'B60_EXACT_OWN_OCCURRENCES_READ_BACK';