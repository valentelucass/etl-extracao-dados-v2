SET NOCOUNT ON;
IF (SELECT COUNT(*) FROM ctl.production_export_field WHERE entity_name='COLETAS_EXCLUIDAS')<>10
   OR NOT EXISTS (SELECT 1 FROM ctl.production_export_template WHERE entity_name='COLETAS_EXCLUIDAS'
                  AND report_id=12899 AND row_date_field='deleted_at' AND expected_field_count=10)
    THROW 52104,'DASHBOARD_PICKUP_DELETION_CONTRACT_INVALID',1;

DECLARE @total bigint,@deleted bigint,@duplicates bigint;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    SELECT @total=COUNT_BIG(*),@deleted=COUNT_BIG(CASE WHEN [Excluída na Origem]=1 AND [Status]=N'Excluída' THEN 1 END)
    FROM pub.vw_coletas_powerbi;
    SELECT @duplicates=COUNT_BIG(*) FROM (
        SELECT [ID] FROM pub.vw_coletas_powerbi GROUP BY [ID] HAVING COUNT_BIG(*)>1
    ) duplicate;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
IF @total=0 OR @duplicates>0 THROW 52105,'DASHBOARD_PICKUP_VIEW_EMPTY_OR_DUPLICATED',1;

SELECT @total AS pickupRows,@deleted AS deletedRows,@duplicates AS duplicateKeys
FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;
