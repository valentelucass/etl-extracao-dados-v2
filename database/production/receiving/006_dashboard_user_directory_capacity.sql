-- Extend the Users payload bound to the existing 5,000-page contract at 20 users per page.
IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52010,'AUXILIARY_PRODUCTION_DATABASE_REQUIRED',1;
GO
CREATE OR ALTER PROCEDURE ctl.usp_store_dashboard_auxiliary
    @source_name varchar(12), @business_date date, @observed_at datetimeoffset(3),
    @row_count int, @records nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @source_name IS NULL OR @source_name NOT IN ('USERS','RASTER')
       OR @business_date IS NULL OR @observed_at IS NULL
       OR @row_count IS NULL OR @row_count<1 OR @row_count>100000
       OR (@source_name='RASTER' AND @row_count>=500)
       OR @records IS NULL OR ISJSON(@records,ARRAY)<>1 OR DATALENGTH(@records)>CASE @source_name WHEN 'RASTER' THEN 20971520 ELSE 134217728 END
        THROW 52011,'AUXILIARY_COMPLETE_CAPTURE_REQUIRED',1;

    IF (SELECT COUNT(*) FROM OPENJSON(@records))<>@row_count
       OR EXISTS(SELECT 1 FROM OPENJSON(@records) WHERE [type]<>5)
        THROW 52012,'AUXILIARY_SOURCE_COUNT_OR_SHAPE_DIFFERS',1;

    DECLARE @key_path varchar(24)=CASE @source_name WHEN 'USERS' THEN '$.id' ELSE '$.CodSolicitacao' END;
    DECLARE @typed TABLE(source_key bigint, record_json nvarchar(max) NOT NULL);
    INSERT @typed(source_key,record_json)
    SELECT TRY_CONVERT(bigint,JSON_VALUE([value],@key_path)),[value] FROM OPENJSON(@records);
    IF EXISTS(SELECT 1 FROM @typed WHERE source_key IS NULL OR source_key<=0)
       OR (SELECT COUNT(DISTINCT source_key) FROM @typed)<>@row_count
        THROW 52013,'AUXILIARY_INVALID_OR_REPEATED_ID',1;
    IF @source_name='USERS' AND EXISTS (
        SELECT 1 FROM @typed t CROSS APPLY OPENJSON(t.record_json) j
        WHERE j.[key]='name' AND (j.[type] NOT IN (0,1) OR DATALENGTH(j.value)>510)
    ) THROW 52014,'AUXILIARY_USER_NAME_CONTRACT',1;
    IF @source_name='RASTER' AND EXISTS (
        SELECT 1 FROM @typed t CROSS APPLY OPENJSON(t.record_json) j
        WHERE j.[key]='ColetasEntregas' AND j.[type] NOT IN (0,4)
    ) THROW 52015,'AUXILIARY_RASTER_STOP_CONTRACT',1;

    DECLARE @capture_id bigint,
            @observed_utc datetime2(3)=CONVERT(datetime2(3),SWITCHOFFSET(@observed_at,'+00:00')),
            @hash binary(32)=HASHBYTES('SHA2_256',@records);
    BEGIN TRY
        BEGIN TRANSACTION;
        SELECT @capture_id=capture_id FROM ctl.dashboard_auxiliary_capture WITH (UPDLOCK,HOLDLOCK)
        WHERE source_name=@source_name AND business_date=@business_date
          AND observed_utc=@observed_utc AND payload_hash=@hash;
        IF @capture_id IS NULL
        BEGIN
            INSERT ctl.dashboard_auxiliary_capture(source_name,business_date,observed_utc,payload_hash,row_count)
            VALUES (@source_name,@business_date,@observed_utc,@hash,@row_count);
            SET @capture_id=CONVERT(bigint,SCOPE_IDENTITY());
            INSERT stg.dashboard_auxiliary_record(capture_id,source_key,record_json)
            SELECT @capture_id,source_key,record_json FROM @typed;
        END;
        COMMIT;
        SELECT @capture_id AS capture_id,@row_count AS rows_confirmed;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
