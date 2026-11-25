DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID(N'stg.usp_production_ingestion_store',N'P'));
DECLARE @start int=CHARINDEX(N'DECLARE @additional_fields int=',@ddl), @finish int=CHARINDEX(N'IF @date_field IS NOT NULL',@ddl);
IF @start=0 OR @finish<=@start THROW 52142,'INSURANCE_CLAIMS_VALIDATOR_REQUIRED',1;
DECLARE @guard nvarchar(max)=SUBSTRING(@ddl,@start,@finish-@start);
DECLARE @throw nvarchar(100)=N'THROW 51925,''SOURCE_FIELD_CONTRACT_MISMATCH'',1';
IF (LEN(@guard)-LEN(REPLACE(@guard,@throw,N'')))/LEN(@throw)<>3 THROW 52142,'INSURANCE_CLAIMS_VALIDATOR_REQUIRED',1;
-- Run the installed predicate against temporary rows. Record refusals without aborting the installer transaction.
SET @guard=REPLACE(@guard,@throw,N'SET @blocked=1');
DECLARE @old nvarchar(max),@new nvarchar(max),@pay nvarchar(max);
SELECT @old=N'{'+STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(field_name,'"')+N':null'),N',') WITHIN GROUP (ORDER BY field_name)+N'}'
FROM ctl.production_export_field WHERE entity_name='SINISTROS';
SELECT @pay=N'{'+STRING_AGG(CONVERT(nvarchar(max),QUOTENAME(field_name,'"')+N':null'),N',') WITHIN GROUP (ORDER BY field_name)+N'}'
FROM ctl.production_export_field WHERE entity_name='CONTAS_A_PAGAR';
SET @new=REPLACE(@old,N'"customer_communication_time":null',N'"insurer_communication_contact_name":null,"insurer_communication_date":null,"icm_ire_cpy_nickname":null');
DECLARE @cases TABLE(id int IDENTITY PRIMARY KEY,entity varchar(40),first_row nvarchar(max),second_row nvarchar(max),refused bit);
INSERT @cases VALUES
 ('SINISTROS',@old,NULL,0),('SINISTROS',@new,@new,0),('SINISTROS',NULL,NULL,0),
 ('SINISTROS',@old,@new,1),('SINISTROS',@new,@old,1),
 ('SINISTROS',REPLACE(@new,N'"insurer_communication_date"',N'"customer_communication_time"'),NULL,1),
 ('SINISTROS',REPLACE(@new,N'"icm_ire_cpy_nickname"',N'"insurer_communication_date"'),NULL,1),
 ('SINISTROS',REPLACE(@new,N'"insurer_communication_contact_name":null,',N''),NULL,1),
 ('SINISTROS',REPLACE(@new,N'"insurer_communication_date"',N'"INSURER_COMMUNICATION_DATE"'),NULL,1),
 ('SINISTROS',REPLACE(@old,N'"customer_communication_time"',N'"unrecognized"'),NULL,1),
 ('CONTAS_A_PAGAR',@pay,NULL,0),('CONTAS_A_PAGAR',REPLACE(@pay,N'"ant_ces_value"',N'"unrecognized"'),NULL,1);
CREATE TABLE #recebimento_linhas(ordinal int PRIMARY KEY,[value] nvarchar(max) NOT NULL);
DECLARE @i int=1,@n int=(SELECT COUNT(*) FROM @cases),@entity varchar(40),@fields int,@expected_count int,
        @first nvarchar(max),@second nvarchar(max),@expected bit,@blocked bit;
WHILE @i<=@n
BEGIN
 SELECT @entity=entity,@first=first_row,@second=second_row,@expected=refused FROM @cases WHERE id=@i;
 SELECT @fields=expected_field_count FROM ctl.production_export_template WHERE entity_name=@entity;
 TRUNCATE TABLE #recebimento_linhas;
 IF @first IS NOT NULL INSERT #recebimento_linhas VALUES(1,N'{"record":'+@first+N'}');
 IF @second IS NOT NULL INSERT #recebimento_linhas VALUES(2,N'{"record":'+@second+N'}');
 SELECT @expected_count=COUNT(*) FROM #recebimento_linhas;
 SET @blocked=0;
 EXEC sys.sp_executesql @guard,N'@entity varchar(40),@fields int,@expected_count int,@blocked bit OUTPUT',@entity,@fields,@expected_count,@blocked OUTPUT;
 IF @blocked<>@expected THROW 52143,'INSURANCE_CLAIMS_FIELD_REGRESSION',1;
 SET @i+=1;
END;
SELECT @n AS casosValidados FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
