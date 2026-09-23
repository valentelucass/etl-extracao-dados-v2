:On Error exit
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR ORIGINAL_LOGIN()<>N'RTR-SVW-002\suporte' THROW 52970,N'B60_EXACT_READBACK_TARGET',1;
DECLARE @owned TABLE(execution_id UNIQUEIDENTIFIER PRIMARY KEY);
INSERT @owned VALUES
('99047fb3-7678-40b8-9c52-76c7eddd10b9'),
('af26581d-67e2-4603-9de3-793d7cacf01b'),
('9f0b2fff-3bf4-4abc-8d40-0263119a4a2c'),
('f9674fe5-db7f-4eb5-9abb-2215ac2868a4'),
('8369211b-2fee-42a1-b553-b8f33d73c5ad'),
('3a9a111e-6c9b-4f36-b092-9a168011f71f'),
('b08f2ac4-5741-4fd4-9c7f-54d571ab37e5'),
('389db3f4-4767-4acd-8172-064126e7c208'),
('a05c4284-d173-410f-9a6e-59e2ab23974b'),
('236dcb81-9d89-4bae-9b7c-3ccba63d540a'),
('d991a561-7d97-4d26-b0a0-d41caf704c2a'),
('a2bd2a35-94be-431b-b4da-89574ae48536'),
('a2151e34-f329-4047-933d-1ffc7513e2b6'),
('61a8337d-04ae-4dc7-9d18-3ed62c8e4f6e'),
('747a937f-d354-4b2c-ab13-a72c413533c2'),
('b5088062-331f-40c4-8ca7-71586b8a3503'),
('079ae906-0746-4a12-b955-7a54a6592c7a'),
('3d4c01ba-9564-417a-b4ac-ea9649da717f'),
('00749f47-e97d-4d87-9e52-76ad0d5a715d'),
('e1935ca9-17f1-4d71-b635-1e5f20f157d0'),
('1facb214-860d-4a12-a745-4acaac597922'),
('30323d35-f44f-478b-b9d3-968060431a4c'),
('65388c0c-f4a9-4c3f-968e-567b5891537b'),
('c3b5eafb-d26e-4014-a954-8dcf1b7121f0'),
('17cdd3d0-59f3-4ea4-b1b1-bef3bc9b3129'),
('b1013bd0-5e88-4d5d-ba12-53f0033d4a55'),
('a18459d6-7da3-4cca-a183-d01c0de8d45f'),
('8328705e-9a27-46ba-a80b-5c3a3541e1dd'),
('24f65fe8-799f-4a5e-aaa6-5940dbe4b363'),
('9bb37f06-0870-4797-9271-2d0a7e5deb6e'),
('3d9e21d9-8908-4263-8aaf-7572439dcd29'),
('118fc8be-7137-4b44-adc6-ef1c6c3aa0d3'),
('e8b30fb6-3277-4e90-8ad8-873c953ade4b'),
('be37bd94-96c1-4580-af53-851f73263e8f'),
('2c1dd6af-2586-41bc-ab32-521321313913'),
('f1f09f68-f790-4d48-917c-40750fbb706b'),
('9ad4c3e6-0683-4b61-8809-f0031f76641a'),
('2a746084-0a7d-4944-89e7-ee57f526a4b0'),
('3a52c825-cb61-4e73-aca4-9916fe2a77fc'),
('8e00c0d9-3e35-4d99-9d7c-e212bc5afaca'),
('26fec24a-8479-4a70-892d-b87cb836ff2b'),
('b9a4c952-8b5c-4a45-9f4a-205bc1f82faf'),
('20e6de8a-27f7-4a76-b29f-8cd7640c4c9f'),
('2dbe042d-a55f-49ce-9af9-f2b2897ffcd0'),
('5ffc982b-c54c-4ddf-b6e5-782ef1b34846'),
('0bf5a540-9e75-4f49-85d7-34a363581299'),
('3cd90ab7-5c7d-4e31-ba4c-1b8b2238f22c'),
('e0851173-9c75-44d0-9cc7-f38c6f1184a3'),
('61ac1c65-38dd-49b0-89cc-f6ee84187bc7');
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