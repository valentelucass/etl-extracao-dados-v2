IF DB_NAME()<>N'ETL_SISTEMA_V2'
 OR NOT EXISTS(SELECT 1 FROM ctl.production_export_template WHERE entity_name='SINISTROS' AND report_id=6392 AND expected_field_count=44)
 OR (SELECT COUNT(*) FROM ctl.production_export_field WHERE entity_name='SINISTROS')<>44
 OR NOT EXISTS(SELECT 1 FROM ctl.production_export_field WHERE entity_name='SINISTROS' AND field_name='customer_communication_time')
 OR EXISTS(SELECT 1 FROM ctl.production_export_field WHERE entity_name='SINISTROS'
           AND field_name IN ('insurer_communication_contact_name','insurer_communication_date','icm_ire_cpy_nickname'))
 OR EXISTS(SELECT 1 FROM ctl.production_export_additional_field WHERE entity_name='SINISTROS')
 THROW 52140,'INSURANCE_CLAIMS_ORIGINAL_LAYOUT_REQUIRED',1;

DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_production_ingestion_store',N'P'));
DECLARE @guard nvarchar(150)=N'IF @actual_fields NOT IN (@fields,@fields+@additional_fields)';
DECLARE @next nvarchar(100)=N'IF @date_field IS NOT NULL';
DECLARE @header int=CHARINDEX(N'PROCEDURE stg.usp_production_ingestion_store',@ddl),
        @start int=CHARINDEX(@guard,@ddl), @finish int=CHARINDEX(@next,@ddl);
IF @ddl IS NULL OR @header=0 OR @start<=@header OR @finish<=@start
 OR CHARINDEX(@guard,@ddl,@start+1)<>0 OR CHARINDEX(@next,@ddl,@finish+1)<>0
 OR CHARINDEX(N'FROM #recebimento_linhas',@ddl)=0 OR CHARINDEX(N'insurer_communication_date',@ddl)<>0
 THROW 52141,'INSURANCE_CLAIMS_UNEXPECTED_RECEIVING_PROCEDURE',1;

-- Keep the existing validation intact for the 44-column layout and all other reports.
SET @ddl=STUFF(@ddl,@finish,0,N'END;
  ');
SET @ddl=STUFF(@ddl,@start,0,N'IF @entity=''SINISTROS'' AND @actual_fields=46
  BEGIN
   IF EXISTS (
       SELECT 1 FROM #recebimento_linhas r
       CROSS APPLY (
           SELECT COUNT(*) AS quantidade,
                  COUNT(DISTINCT j.[key] COLLATE Latin1_General_100_BIN2) AS unicos,
                  MAX(CASE WHEN f.field_name IS NULL AND j.[key] COLLATE Latin1_General_100_BIN2 NOT IN
                      (''insurer_communication_contact_name'',''insurer_communication_date'',''icm_ire_cpy_nickname'')
                      THEN 1 ELSE 0 END) AS desconhecido
           FROM OPENJSON(r.[value],''$.record'') j
           LEFT JOIN ctl.production_export_field f ON f.entity_name=@entity
               AND f.field_name<>''customer_communication_time''
               AND f.field_name=j.[key] COLLATE Latin1_General_100_BIN2
       ) teste
       WHERE teste.quantidade<>46 OR teste.unicos<>46 OR teste.desconhecido=1
   ) THROW 51925,''SOURCE_FIELD_CONTRACT_MISMATCH'',1;
  END
  ELSE
  BEGIN
  ');
SET @ddl=N'ALTER '+SUBSTRING(@ddl,@header,LEN(@ddl));
EXEC sys.sp_executesql @ddl;
