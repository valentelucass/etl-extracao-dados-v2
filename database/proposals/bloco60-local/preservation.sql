-- Aggregate SHA-256 per table, never emits persisted row values.
SET NOCOUNT ON;
DECLARE @table_id INT,@schema SYSNAME,@table SYSNAME,@keys NVARCHAR(MAX),@query NVARCHAR(MAX);
DECLARE b60_tables CURSOR LOCAL FAST_FORWARD FOR
 SELECT t.object_id,SCHEMA_NAME(t.schema_id),t.name FROM sys.tables t WHERE t.is_ms_shipped=0
 AND t.name NOT IN(N'source_protocol_binding',N'execution_source_protocol') ORDER BY SCHEMA_NAME(t.schema_id),t.name;
OPEN b60_tables; FETCH NEXT FROM b60_tables INTO @table_id,@schema,@table;
WHILE @@FETCH_STATUS=0
BEGIN
 SELECT @keys=STRING_AGG(CONVERT(NVARCHAR(MAX),QUOTENAME(c.name)),N',') WITHIN GROUP(ORDER BY ic.key_ordinal)
 FROM sys.indexes i JOIN sys.index_columns ic ON ic.object_id=i.object_id AND ic.index_id=i.index_id
 JOIN sys.columns c ON c.object_id=ic.object_id AND c.column_id=ic.column_id
 WHERE i.object_id=@table_id AND i.is_primary_key=1 AND ic.key_ordinal>0;
 IF @keys IS NULL THROW 52970,N'B60_PRESERVATION_PRIMARY_KEY_REQUIRED',1;
 SET @query=N'SELECT N'''+REPLACE(@schema+N'.'+@table,N'''',N'''''')+N''' table_name,COUNT_BIG(*) row_count,'+
 N'(SELECT LOWER(CONVERT(CHAR(64),HASHBYTES(''SHA2_256'',(SELECT * FROM '+QUOTENAME(@schema)+N'.'+QUOTENAME(@table)+
 N' ORDER BY '+@keys+N' FOR JSON PATH,INCLUDE_NULL_VALUES)),2))) row_sha256 FROM '+QUOTENAME(@schema)+N'.'+QUOTENAME(@table)+N';';
 EXEC sys.sp_executesql @query;
 FETCH NEXT FROM b60_tables INTO @table_id,@schema,@table;
END;
CLOSE b60_tables; DEALLOCATE b60_tables;