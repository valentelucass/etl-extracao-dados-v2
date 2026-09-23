SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>1 THROW 52860,N'EXACT_TRANSACTION_TARGET_REQUIRED',1;
IF @template NOT IN(N'COLETAS',N'FRETES',N'MANIFESTOS',N'COTACOES',N'LOCALIZACAO_CARGAS') THROW 52860,N'CLOSED_TEMPLATE',1;
DECLARE @offset BIGINT=DATEDIFF(DAY,CONVERT(DATE,'20240101'),@date)*CONVERT(BIGINT,100);
CREATE TABLE #expected(kind VARCHAR(8),root_key NVARCHAR(256),child_key NVARCHAR(256),field_value NVARCHAR(256),number_value DECIMAL(19,4),presence_code VARCHAR(8),absent_code VARCHAR(8),aux NVARCHAR(256),tariff DECIMAL(19,4),reference_id BIGINT);
IF @template=N'COLETAS'
 INSERT #expected SELECT 'ROOT',CONCAT(N'INTEGER:',550690800+n+@offset),N'',N'pending',NULL,CASE n WHEN 1 THEN 'VALUE' ELSE 'NULL' END,'ABSENT',NULL,NULL,NULL FROM (VALUES(1),(2))v(n);
IF @template=N'FRETES'
 INSERT #expected SELECT 'ROOT',CONCAT(N'INTEGER:',550638900+n+@offset),N'',N'finished',NULL,CASE n WHEN 1 THEN 'VALUE' ELSE 'NULL' END,'ABSENT',NULL,NULL,NULL FROM (VALUES(1),(2))v(n);
IF @template=N'COTACOES'
 INSERT #expected SELECT 'ROOT',CONCAT(N'INTEGER:',550690600+n+@offset),N'',N'BRL',n*10,CASE n WHEN 1 THEN 'VALUE' ELSE 'NULL' END,'ABSENT',N'PER_SHIPMENT|HALF_UP',CASE n WHEN 1 THEN 5 ELSE 7 END,@reference FROM (VALUES(1),(2))v(n);
IF @template=N'LOCALIZACAO_CARGAS'
 INSERT #expected SELECT 'ROOT',CONCAT(N'INTEGER:',550865600+n+@offset),N'',CASE n WHEN 1 THEN N'finished' ELSE N'unmapped-synthetic' END,CASE n WHEN 1 THEN 0 ELSE NULL END,CASE n WHEN 1 THEN 'VALUE' ELSE 'NULL' END,'ABSENT',
 N'EXACT_SCOPED_ALIAS_CANDIDATE_UNRESOLVED|LOCAL_ROOT_SOURCE_KEY_ONLY_NO_RELATION|FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION',CASE n WHEN 1 THEN 1 ELSE 0 END,NULL FROM (VALUES(1),(2))v(n);
IF @template=N'MANIFESTOS'
BEGIN
 INSERT #expected VALUES('ROOT',CONCAT(N'INTEGER:',550639901+@offset),N'',N'closed',0,'NULL','ABSENT',NULL,NULL,NULL);
 INSERT #expected SELECT 'PICK',CONCAT(N'INTEGER:',550639901+@offset),CONCAT(N'INTEGER:',55000+n+@offset),N'UNRESOLVED_V2_046A',NULL,'VALUE',NULL,NULL,NULL,NULL FROM (VALUES(1),(2))v(n);
 INSERT #expected SELECT 'MDFE',CONCAT(N'INTEGER:',550639901+@offset),REPLICATE(CONVERT(NVARCHAR(1),n),44),NULL,n,'VALUE',NULL,NULL,NULL,NULL FROM (VALUES(1),(2))v(n);
END;
SELECT * INTO #pristine FROM #expected;
SELECT TOP(0) * INTO #observed FROM #expected;
INSERT #observed SELECT row_kind kind,source_key root_key,child_key,
 CAST(CASE @template WHEN N'COLETAS' THEN JSON_VALUE(projection_json,'$.status') WHEN N'FRETES' THEN JSON_VALUE(projection_json,'$.status')
 WHEN N'COTACOES' THEN JSON_VALUE(projection_json,'$._typed.currency') WHEN N'LOCALIZACAO_CARGAS' THEN JSON_VALUE(projection_json,'$._typed.status')
 ELSE CASE row_kind WHEN 'ROOT' THEN JSON_VALUE(projection_json,'$.root.status') WHEN 'PICK' THEN JSON_VALUE(projection_json,'$.relation') END END AS NVARCHAR(256)) field_value,
 TRY_CONVERT(DECIMAL(19,4),CASE @template WHEN N'COTACOES' THEN JSON_VALUE(projection_json,'$._typed.totalAmount') WHEN N'LOCALIZACAO_CARGAS' THEN JSON_VALUE(projection_json,'$._typed.volumes')
 WHEN N'MANIFESTOS' THEN CASE row_kind WHEN 'ROOT' THEN JSON_VALUE(projection_json,'$.metrics.KM.value') WHEN 'MDFE' THEN JSON_VALUE(projection_json,'$.number') END END) number_value,
 CAST(CASE @template WHEN N'COLETAS' THEN JSON_VALUE(presence_json,'$.pck_mik_mft_sequence_code') WHEN N'FRETES' THEN JSON_VALUE(presence_json,'$.fit_p_m_pck_sequence_code')
 WHEN N'COTACOES' THEN JSON_VALUE(presence_json,'$.qoe_uer_name') WHEN N'LOCALIZACAO_CARGAS' THEN JSON_VALUE(presence_json,'$.fields.invoices_volumes.presence')
 ELSE CASE row_kind WHEN 'ROOT' THEN JSON_VALUE(presence_json,'$.operational_comments') ELSE JSON_VALUE(presence_json,'$.key') END END AS VARCHAR(8)) presence_code,
 CAST(CASE @template WHEN N'COLETAS' THEN JSON_VALUE(presence_json,'$.status_updated_at') WHEN N'FRETES' THEN JSON_VALUE(presence_json,'$.reference_number')
 WHEN N'COTACOES' THEN JSON_VALUE(presence_json,'$.qoe_crn_psn_nickname') WHEN N'LOCALIZACAO_CARGAS' THEN JSON_VALUE(presence_json,'$.fields.taxed_weight.presence')
 ELSE CASE row_kind WHEN 'ROOT' THEN JSON_VALUE(presence_json,'$.mft_uer_name') END END AS VARCHAR(8)) absent_code,
 CAST(CASE @template WHEN N'COTACOES' THEN CONCAT(JSON_VALUE(projection_json,'$._typed.unit'),N'|',JSON_VALUE(projection_json,'$._typed.rounding'))
 WHEN N'LOCALIZACAO_CARGAS' THEN CONCAT(JSON_VALUE(projection_json,'$._typed.freightRelation'),N'|',JSON_VALUE(projection_json,'$._typed.freightProvenance'),N'|',JSON_VALUE(presence_json,'$.policy.freightVolumeFallback')) END AS NVARCHAR(256)) aux,
 TRY_CONVERT(DECIMAL(19,4),CASE @template WHEN N'COTACOES' THEN JSON_VALUE(projection_json,'$._typed.tariffMinimum') WHEN N'LOCALIZACAO_CARGAS' THEN CASE JSON_VALUE(projection_json,'$._typed.terminal') WHEN N'true' THEN '1' WHEN N'false' THEN '0' END END) tariff,reference_release_id reference_id
 FROM recon.runtime_vertical_output WHERE execution_id=@execution AND entity_name=LOWER(@template);
IF (SELECT COUNT_BIG(*) FROM #observed WHERE kind='ROOT')>64 OR (SELECT COUNT_BIG(*) FROM #observed WHERE kind<>'ROOT')>128 THROW 52860,N'COMPARISON_GRAIN_CAP',1;
DECLARE @bound BIT=CASE WHEN EXISTS(SELECT 1 FROM ctl.execution_publication_event pub JOIN ctl.execution_attempt a ON a.execution_id=pub.execution_id JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE pub.execution_id=@execution AND a.current_state=N'PUBLISHED' AND p.entity_name=LOWER(@template) AND p.environment_name=N'LOCAL_SHADOW' AND p.source_instance=N'LOCAL_V2' AND p.tenant_scope=N'LOCAL_V2' AND p.partition_start_utc=@start AND p.partition_end_exclusive_utc=@end) THEN 1 ELSE 0 END;
DECLARE @summary TABLE(case_id VARCHAR(32),observed_roots INT,observed_children INT,expected_rows INT,missing_rows INT,extra_rows INT,duplicates INT,code VARCHAR(32),passed BIT);
DECLARE @i INT=1;
WHILE @i<=6
BEGIN
 DELETE #expected; INSERT #expected SELECT * FROM #pristine;
 IF @i=2 UPDATE #expected SET field_value=N'INTENTIONAL_SYNTHETIC_MISMATCH' WHERE kind='ROOT';
 IF @i=3 INSERT #expected SELECT TOP(1) * FROM #pristine ORDER BY kind,root_key,child_key;
 IF @i=4 DELETE FROM #expected WHERE root_key=(SELECT MIN(root_key) FROM #pristine);
 DECLARE @expectedStart DATETIME2(3)=CASE WHEN @i=5 THEN DATEADD(DAY,1,@start) ELSE @start END,
 @inputComplete BIT=CASE WHEN @i=6 THEN 0 ELSE 1 END;
 IF @inputComplete=0 DELETE FROM #expected WHERE root_key=(SELECT MAX(root_key) FROM #pristine);
 DECLARE @windowBound BIT=CASE WHEN EXISTS(SELECT 1 FROM ctl.execution_attempt a JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
 WHERE a.execution_id=@execution AND p.partition_start_utc=@expectedStart AND p.partition_end_exclusive_utc=@end) THEN 1 ELSE 0 END;
 DECLARE @missing INT=(SELECT COUNT(*) FROM (SELECT * FROM #expected EXCEPT SELECT * FROM #observed)d),@extra INT=(SELECT COUNT(*) FROM (SELECT * FROM #observed EXCEPT SELECT * FROM #expected)d),
 @duplicates INT=(SELECT COUNT(*) FROM (SELECT kind,root_key,child_key FROM #expected GROUP BY kind,root_key,child_key HAVING COUNT(*)>1)d);
 DECLARE @code VARCHAR(32)=CASE WHEN @bound=0 OR @windowBound=0 OR @inputComplete=0 OR NOT EXISTS(SELECT 1 FROM #observed) THEN 'NOT_CONFIRMED' WHEN @duplicates>0 THEN 'DUPLICATE' WHEN @missing+@extra>0 THEN 'DIVERGENT' ELSE 'EQUAL' END;
 INSERT @summary SELECT CHOOSE(@i,'EQUAL','FIELD_MISMATCH','DUPLICATE','ABSENT','WINDOW_MISMATCH','INCOMPLETE'),(SELECT COUNT(*) FROM #observed WHERE kind='ROOT'),(SELECT COUNT(*) FROM #observed WHERE kind<>'ROOT'),(SELECT COUNT(*) FROM #expected),@missing,@extra,@duplicates,@code,
 CASE WHEN @bound=1 AND @code=CHOOSE(@i,'EQUAL','DIVERGENT','DUPLICATE','DIVERGENT','NOT_CONFIRMED','NOT_CONFIRMED') THEN 1 ELSE 0 END;
 SET @i+=1;
END;
SELECT case_id,observed_roots,observed_children,expected_rows,missing_rows,extra_rows,duplicates,code,passed FROM @summary FOR JSON PATH;
