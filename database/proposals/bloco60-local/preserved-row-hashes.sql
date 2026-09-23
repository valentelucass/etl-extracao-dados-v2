-- One hash per complete row, no primary key or row value is emitted.
-- The one SERVICE mapping changed by the explicit activation/compensation has a separate oracle.
SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 52970,N'B60_PRESERVATION_TARGET',1;
DECLARE @schema SYSNAME,@table SYSNAME,@query NVARCHAR(MAX),@filter NVARCHAR(MAX);
DECLARE b60_rows CURSOR LOCAL FAST_FORWARD FOR
 SELECT SCHEMA_NAME(schema_id),name FROM sys.tables WHERE is_ms_shipped=0
 AND name NOT IN(N'source_protocol_binding',N'execution_source_protocol') ORDER BY SCHEMA_NAME(schema_id),name;
OPEN b60_rows;FETCH NEXT FROM b60_rows INTO @schema,@table;
WHILE @@FETCH_STATUS=0
BEGIN
 SET @filter=CASE WHEN @schema=N'ctl' AND @table=N'runtime_identity_mapping'
  THEN N' WHERE original_sid<>SUSER_SID(N''RTR-SVW-002\etl_v2_exec'')' ELSE N'' END;
 -- Each sqlcmd output row stays below its 65535-column line width (512 hashes).
 SET @query=N';WITH hashed AS (SELECT LOWER(CONVERT(CHAR(64),HASHBYTES(''SHA2_256'',(SELECT r.* FOR JSON PATH,WITHOUT_ARRAY_WRAPPER,INCLUDE_NULL_VALUES)),2)) h FROM '+
 QUOTENAME(@schema)+N'.'+QUOTENAME(@table)+N' r'+@filter+N' UNION ALL SELECT N''''),'+
 N'numbered AS (SELECT h,ROW_NUMBER() OVER(ORDER BY h) rn FROM hashed),'+
 N'grouped AS (SELECT (rn-1)/512 bucket,STRING_AGG(CONVERT(NVARCHAR(MAX),h),N'','') WITHIN GROUP(ORDER BY h) hashes FROM numbered GROUP BY (rn-1)/512) '+
 N'SELECT (SELECT N'''+REPLACE(@schema+N'.'+@table,N'''',N'''''')+N''' table_name,g.hashes hashes FOR JSON PATH,WITHOUT_ARRAY_WRAPPER) FROM grouped g ORDER BY g.bucket;';
 EXEC sys.sp_executesql @query;
 FETCH NEXT FROM b60_rows INTO @schema,@table;
END;
CLOSE b60_rows;DEALLOCATE b60_rows;
