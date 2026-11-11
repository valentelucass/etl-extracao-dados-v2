IF DB_NAME() <> N'ETL_SISTEMA_V2' THROW 52010,'AUXILIARY_PRODUCTION_DATABASE_REQUIRED',1;
GO
CREATE TABLE ctl.dashboard_auxiliary_capture (
    capture_id bigint IDENTITY NOT NULL PRIMARY KEY,
    source_name varchar(12) NOT NULL CHECK(source_name IN ('USERS','RASTER')),
    business_date date NOT NULL,
    observed_utc datetime2(3) NOT NULL,
    payload_hash binary(32) NOT NULL,
    row_count int NOT NULL CHECK(row_count>0),
    CONSTRAINT UQ_dashboard_auxiliary_capture UNIQUE(source_name,business_date,observed_utc,payload_hash)
);
CREATE INDEX IX_dashboard_auxiliary_current
    ON ctl.dashboard_auxiliary_capture(source_name,business_date,observed_utc DESC,capture_id DESC)
    INCLUDE(row_count);
CREATE TABLE stg.dashboard_auxiliary_record (
    capture_id bigint NOT NULL REFERENCES ctl.dashboard_auxiliary_capture(capture_id),
    source_key bigint NOT NULL CHECK(source_key>0),
    record_json nvarchar(max) NOT NULL CHECK(ISJSON(record_json)=1),
    CONSTRAINT PK_dashboard_auxiliary_record PRIMARY KEY(capture_id,source_key)
);
GO
CREATE PROCEDURE ctl.usp_store_dashboard_auxiliary
    @source_name varchar(12), @business_date date, @observed_at datetimeoffset(3),
    @row_count int, @records nvarchar(max)
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    IF @source_name IS NULL OR @source_name NOT IN ('USERS','RASTER')
       OR @business_date IS NULL OR @observed_at IS NULL
       OR @row_count IS NULL OR @row_count<1 OR @row_count>12000
       OR (@source_name='RASTER' AND @row_count>=500)
       OR @records IS NULL OR ISJSON(@records,ARRAY)<>1 OR DATALENGTH(@records)>20971520
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
CREATE VIEW stg.dashboard_auxiliary_latest
AS
WITH ranked_captures AS (
    SELECT *,ROW_NUMBER() OVER (
        PARTITION BY source_name,CASE WHEN source_name='RASTER' THEN business_date END
        ORDER BY observed_utc DESC,capture_id DESC) AS row_rank
    FROM ctl.dashboard_auxiliary_capture
)
SELECT c.source_name,c.business_date,c.observed_utc,c.capture_id,r.source_key,r.record_json
FROM ranked_captures c JOIN stg.dashboard_auxiliary_record r ON r.capture_id=c.capture_id
WHERE c.row_rank=1;
GO
CREATE FUNCTION core.dashboard_raster_time(@raw nvarchar(100))
RETURNS datetimeoffset(3)
AS
BEGIN
    DECLARE @text nvarchar(100)=NULLIF(TRIM(@raw),N'');
    IF @text IS NULL OR LEFT(@text,10)=N'1900-01-01' RETURN NULL;
    IF RIGHT(@text,1)=N'Z' OR @text COLLATE Latin1_General_100_BIN2 LIKE N'%[+-][0-9][0-9]:[0-9][0-9]'
        RETURN TRY_CONVERT(datetimeoffset(3),@text);
    RETURN TRY_CONVERT(datetime2(3),@text,126) AT TIME ZONE 'E. South America Standard Time';
END;
GO
