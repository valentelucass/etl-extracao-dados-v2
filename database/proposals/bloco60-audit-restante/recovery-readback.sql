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
('36a19835-a25c-4067-8451-5eae666b7cf9'),
('8cdd2253-ab04-49b2-9114-acc5b81eb39b'),
('7e0041e4-b4a9-4f4c-9437-fca1b538dfbc'),
('f97c42d8-e6a9-4cfa-8ea7-96c368fb8ce1'),
('7482601b-c47f-426c-8c62-b9a57e21e678'),
('d90997ed-ce71-40a1-9141-b1b66e21450d'),
('e089ee7d-bdc2-44cb-95dd-96baa4c3af88'),
('d038e77a-4625-478c-a393-fa316667e9cb'),
('aa533c67-10d7-4b68-90e8-e9cb042b945f'),
('5cd27034-fd70-43b5-8282-a03f589db451'),
('4fca911c-9cb1-4cd4-b153-22059750623e'),
('652f2a5f-da8f-4972-b0c6-dd906f38773c'),
('32e4216b-f055-4bee-b42f-a5341f282727'),
('555386bc-18ac-4639-9361-a671af2343d4'),
('1ad85f14-b848-4f00-99bd-a6a2d7137ad0'),
('03a139b2-1f52-4c6d-9b4a-73df5b808dd5'),
('4e677275-15b1-44e4-a2e0-07bac3301468'),
('eeaff971-5ffe-44a8-a58b-b795b525975c'),
('cf082549-819a-4ed9-9ff1-de309f9bd403'),
('58693c3e-c418-4104-a3c9-97e8654a5bb5');
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