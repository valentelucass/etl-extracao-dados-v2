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
('aa2ed757-043f-45a0-958d-868ea0e06ae2'),
('7ed814bc-a358-42eb-9baa-dd61d692748b'),
('1791220b-3675-4e2d-b838-f29efbdd7b63'),
('d537b185-cde8-4774-b644-3b583d5af8e0'),
('ca6ccb0a-b899-49db-96cd-85b9ccd246fe'),
('655e4d6d-fa98-4ce8-8267-e3cdd3d6680b'),
('db4d3ffa-52a8-4a48-86f7-aa75dba27374'),
('a0dec013-3138-4a04-9c21-fb71c3ff59f1'),
('73577987-29c5-4d03-843c-5337002edf01'),
('7d3dc3a5-1d31-4df2-a942-9fc8472aa779'),
('df4bb973-860e-4334-b4e9-ad8ca41dd48f'),
('d2091a9e-de0c-4913-b25b-8838ecf8ec9b'),
('55af3293-e232-4504-96e7-dd35e9889d1a'),
('f07baa5a-f8fd-4dfe-bbe9-d45c92285465'),
('1113c7a4-e554-4fa6-9926-55d548bde594'),
('e06d9187-bac5-45f9-91bc-56a65fec80e4'),
('556921ea-1cef-43b8-91cc-403dd60b811b'),
('8bdc4301-8ca0-47ad-8b98-6cbeeabffd07'),
('2831168f-6761-4d09-883b-8284b418a52f'),
('de4b5a58-dfb5-41da-adc5-e58532ee6ff7'),
('84bde945-3cab-4587-a4af-595f5f0df041'),
('95cb0f8c-99cd-4781-a78a-c7ec6ffc6a78'),
('ca0b7ec9-6790-41fc-bbcc-1625bda596ca'),
('c84b6fb7-867b-4efc-a5ac-72e634c6feb7'),
('cff19a3a-99fe-4652-80c7-c9355c8517b8'),
('689bab95-0ff0-44ff-8ea7-02086fa4003f'),
('0fac29f6-2901-46b9-9828-d30d8de20eab'),
('d75ea6ec-d324-4b1d-b36b-5fe0fed0c252'),
('cbee4180-14e9-4e05-b21c-88918ec7ac75');
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