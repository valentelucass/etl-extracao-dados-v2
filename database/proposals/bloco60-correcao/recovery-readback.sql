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
('29558f9e-f8f8-4a1c-b248-d510c9426c1c'),
('0a806a0d-7c53-47ad-9465-4a2d0ea701c1'),
('cb7f6e75-a7f9-4c62-ac34-3b349f8c0a21'),
('71023c82-bc4c-415e-b8c8-623633e0bea8'),
('7651ae56-10b9-4d80-aa0f-e7c5a6ef59cd'),
('4afd68d1-1568-45fe-bbd8-25309ae10cc7'),
('3d9023f8-9537-4ece-92e8-5b688d7a39e1'),
('784bb4a3-436a-4d57-ba0d-8ff87fb83fc8'),
('bf6db700-2727-4c0b-808a-baaecfbc2100'),
('67df8d50-87da-4566-b3e4-c41f8c2f88e0'),
('59092799-0274-495f-90a7-d70220d0ab48'),
('d548bd45-eb1b-42a7-88cf-a281bcc8cbd4'),
('9220e1de-98cb-4498-9595-4db1b4f460e3'),
('1ca5edc7-4041-479b-85d8-84e1fa241350'),
('2952f7af-f2d3-40ff-97f2-1fdfdfa2c95d'),
('ce9468f3-4085-45a1-b418-a3aa0a8fade9'),
('1eecf8ad-b9ea-4d4a-a081-5a0ee466daf0'),
('4482831f-0f0d-42be-bc7b-9dd3af69780c'),
('155e7554-4f68-4d41-82f2-b13a2c01de51'),
('3de77869-08f0-479d-a046-f3151ad135f1'),
('96223e24-53e2-4950-b893-7474311854da'),
('a1528ab0-3e8e-4424-a1b1-c40bd762cfb4'),
('c7ff958b-8cb5-4b75-9462-0e8938d5bf82'),
('752bb298-b1a9-4e30-9573-7fbc6a24114d'),
('de1fe27a-c098-44db-a598-dfb868ce4b1e'),
('a5029996-7f41-4b67-be28-aa9e747b81f0'),
('e64e34a2-5e1b-46fb-8a4e-421ac18c0e5e'),
('044bbcc7-d809-46c3-90c6-c615eb6800c3'),
('f7c8050b-c021-47d3-a67d-2607bd22dbbe'),
('204e80a9-b426-4094-b81d-69ab39b6961a'),
('1f957249-9dbe-45b8-8752-9c5010931a12'),
('884ed823-50ae-496e-8f3f-cb0766ac4a07'),
('ca79a55b-1ee4-4aa4-b3a1-a0b53d03dfe3'),
('e4bf6355-9c8a-4f15-9cbf-b9b49202f34b'),
('aaad879b-4033-43e1-9d28-41ba3ae4dc37');
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