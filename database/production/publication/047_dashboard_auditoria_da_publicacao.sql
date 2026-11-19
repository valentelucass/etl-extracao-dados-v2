-- A auditoria acompanha a troca de dados. Uma repetição usa o identificador e
-- o hash do relatório original para impedir a publicação de versões divergentes.
DECLARE @ddl nvarchar(max)=OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes','P'));
DECLARE @at int=CHARINDEX('AS',@ddl,CHARINDEX('usp_dashboard_publicar_fatos_fretes',@ddl)+LEN('usp_dashboard_publicar_fatos_fretes'));
IF @ddl IS NULL OR @at=0 OR CHARINDEX('EXEC core.usp_dashboard_preparar_leituras_operacionais;',@ddl)=0 OR CHARINDEX('        COMMIT;',@ddl)=0
    THROW 52094,'Coordenador de publicacao inesperado.',1;
SET @ddl=N'CREATE OR ALTER PROCEDURE core.usp_dashboard_publicar_fatos_fretes @audit nvarchar(max)=NULL '+SUBSTRING(@ddl,@at,LEN(@ddl));
SET @ddl=REPLACE(@ddl,'        COMMIT;',N'
        IF @audit IS NOT NULL
        BEGIN
            IF ISJSON(@audit)<>1 THROW 52095,''Auditoria de recebimento invalida.'',1;
            DECLARE @cycle uniqueidentifier=CONVERT(uniqueidentifier,JSON_VALUE(@audit,''$.cycle'')),
                    @start datetimeoffset(3)=CONVERT(datetimeoffset(3),JSON_VALUE(@audit,''$.started''),127),
                    @end datetimeoffset(3)=CONVERT(datetimeoffset(3),JSON_VALUE(@audit,''$.completed''),127),
                    @digest binary(32)=CONVERT(binary(32),JSON_VALUE(@audit,''$.sha256''),2),
                    @records nvarchar(max)=JSON_QUERY(@audit,''$.operations'');
            IF ISNULL(JSON_VALUE(@audit,''$.passed''),'''')<>''true'' THROW 52095,''Recebimento sem confirmacao.'',1;
            EXEC ctl.usp_store_dashboard_refresh_audit @source_cycle=@cycle,@started_at=@start,@completed_at=@end,
                 @passed=1,@failure_code=NULL,@report_hash=@digest,@operations=@records;
        END;
        COMMIT;');
EXEC sys.sp_executesql @ddl;