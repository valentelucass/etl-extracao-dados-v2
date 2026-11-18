-- Primeira etapa da publicação 036: a ocorrência vencedora por ID de frete.
-- Os vínculos com faturas, localização, referências e comprovantes continuam no contrato 036/034.
INSERT ctl.dashboard_projection_dependency (projection_name, entity_name) VALUES (N'FRETES_VIGENTES','FRETES');

CREATE TABLE core.dashboard_freight_occurrence_index (
    run_id uniqueidentifier NOT NULL,
    occurrence_ordinal int NOT NULL,
    freight_id bigint NULL,
    document_at datetimeoffset(7) NULL,
    updated_at datetimeoffset(7) NULL,
    cte_id bigint NULL,
    CONSTRAINT PK_dashboard_freight_occurrence PRIMARY KEY(run_id, occurrence_ordinal)
);
CREATE INDEX IX_dashboard_freight_occurrence_id ON core.dashboard_freight_occurrence_index(freight_id,run_id)
    INCLUDE (document_at,updated_at,cte_id) WITH (MAXDOP=1);

CREATE TABLE core.dashboard_freight_winner (
    freight_id bigint NOT NULL PRIMARY KEY,
    run_id uniqueidentifier NOT NULL,
    occurrence_ordinal int NOT NULL,
    em_publicacao bit NOT NULL
);
GO
CREATE OR ALTER VIEW core.dashboard_fretes_vigentes_preparada
AS
SELECT w.freight_id, p.source_scope,p.business_date,CONVERT(datetime2(3),p.capture_utc) AS stored_utc,
       w.run_id,w.occurrence_ordinal,o.source_envelope
FROM core.dashboard_freight_winner w
JOIN ctl.dashboard_projection_partition p ON p.projection_name=N'FRETES_VIGENTES' AND p.applied_run_id=w.run_id
JOIN stg.production_export_occurrence o ON o.run_id=w.run_id AND o.occurrence_ordinal=w.occurrence_ordinal
WHERE w.em_publicacao=1;
GO
CREATE OR ALTER PROCEDURE core.usp_dashboard_preparar_fretes_vigentes
    @entity_name varchar(40), @source_scope char(64), @business_date date
AS
BEGIN
    SET NOCOUNT ON;
    SET XACT_ABORT ON;
    SET LOCK_TIMEOUT 3000;
    IF @@TRANCOUNT<>0 THROW 52060,'FRETES_OUTER_TRANSACTION_REFUSED',1;
    IF @entity_name IS NULL OR @entity_name<>'FRETES' OR @source_scope IS NULL OR @business_date IS NULL
        THROW 52061,'FRETES_PARTITION_REQUIRED',1;
    BEGIN TRY
        BEGIN TRANSACTION;
        DECLARE @lock int, @run uniqueidentifier, @before uniqueidentifier, @stored datetime2(7), @expected int;
        EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fretes:vigentes',@LockMode='Exclusive',
            @LockOwner='Transaction',@LockTimeout=0;
        IF @lock<0 THROW 52062,'FRETES_PUBLICATION_BUSY',1;
        IF (SELECT COUNT(DISTINCT source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name='FRETES')<>1
            THROW 52063,'FRETES_SINGLE_SOURCE_REQUIRED',1;

        SELECT @run=c.latest_run_id,@stored=c.stored_utc,@expected=r.row_count
        FROM ctl.production_ingestion_coverage c
        JOIN ctl.production_ingestion_run r ON r.run_id=c.latest_run_id AND r.run_state='STORED'
            AND r.source_scope=c.source_scope AND r.entity_name=c.entity_name
            AND r.period_start=c.business_date AND r.period_end=c.business_date
        WHERE c.source_scope=@source_scope AND c.entity_name=@entity_name AND c.business_date=@business_date;
        IF @run IS NULL OR @expected IS NULL OR @expected NOT BETWEEN 0 AND 100000
            THROW 52064,'FRETES_COMPLETE_BOUNDED_CAPTURE_REQUIRED',1;
        SELECT @before=applied_run_id FROM ctl.dashboard_projection_partition
        WHERE projection_name=N'FRETES_VIGENTES' AND source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date;
        IF @before=@run BEGIN COMMIT; RETURN; END;

        INSERT core.dashboard_freight_occurrence_index(run_id,occurrence_ordinal,freight_id,document_at,updated_at,cte_id)
        SELECT @run,occurrence_ordinal,TRY_CONVERT(bigint,JSON_VALUE(source_envelope,'$.record.id')),
            COALESCE(TRY_CONVERT(datetimeoffset(7),JSON_VALUE(source_envelope,'$.record.fit_fhe_cte_issued_at')),
                     TRY_CONVERT(datetimeoffset(7),JSON_VALUE(source_envelope,'$.record.fit_fhe_cte_created_at')),
                     TRY_CONVERT(datetimeoffset(7),JSON_VALUE(source_envelope,'$.record.fit_nse_issued_at'))),
            TRY_CONVERT(datetimeoffset(7),JSON_VALUE(source_envelope,'$.record.updated_at')),
            TRY_CONVERT(bigint,JSON_VALUE(source_envelope,'$.record.fit_fhe_cte_cte_id'))
        FROM stg.production_export_occurrence WHERE run_id=@run OPTION(MAXDOP 1);
        IF @@ROWCOUNT<>@expected THROW 52065,'FRETES_OCCURRENCE_COUNT_MISMATCH',1;
        CREATE TABLE #affected(freight_id bigint PRIMARY KEY);
        INSERT #affected SELECT DISTINCT freight_id FROM core.dashboard_freight_occurrence_index
            WHERE run_id IN (@run,@before) AND freight_id IS NOT NULL OPTION(MAXDOP 1, MAX_GRANT_PERCENT=0.5);
        DELETE FROM core.dashboard_freight_occurrence_index WHERE run_id=@before;
        UPDATE ctl.dashboard_projection_partition
        SET applied_run_id=@run,capture_utc=@stored,published_utc=SYSUTCDATETIME()
        WHERE projection_name=N'FRETES_VIGENTES' AND source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date;
        IF @@ROWCOUNT=0
            INSERT ctl.dashboard_projection_partition(projection_name,entity_name,source_scope,business_date,applied_run_id,capture_utc,published_utc)
            VALUES(N'FRETES_VIGENTES',@entity_name,@source_scope,@business_date,@run,@stored,SYSUTCDATETIME());

        ;WITH ranked AS (
            SELECT i.freight_id,i.run_id,i.occurrence_ordinal,
                ROW_NUMBER() OVER(PARTITION BY i.freight_id ORDER BY p.capture_utc DESC,i.document_at DESC,
                    i.updated_at DESC,i.cte_id DESC,p.business_date DESC,i.occurrence_ordinal) AS rn
            FROM #affected a JOIN core.dashboard_freight_occurrence_index i ON i.freight_id=a.freight_id
            JOIN ctl.dashboard_projection_partition p ON p.projection_name=N'FRETES_VIGENTES' AND p.applied_run_id=i.run_id
        )
        SELECT freight_id,run_id,occurrence_ordinal INTO #winning FROM ranked WHERE rn=1
            OPTION(MAXDOP 1, MAX_GRANT_PERCENT=0.5);
        UPDATE w SET run_id=n.run_id,occurrence_ordinal=n.occurrence_ordinal,em_publicacao=1
        FROM core.dashboard_freight_winner w JOIN #winning n ON n.freight_id=w.freight_id OPTION(MAXDOP 1);
        INSERT core.dashboard_freight_winner(freight_id,run_id,occurrence_ordinal,em_publicacao)
        SELECT n.freight_id,n.run_id,n.occurrence_ordinal,1 FROM #winning n
        WHERE NOT EXISTS(SELECT 1 FROM core.dashboard_freight_winner w WHERE w.freight_id=n.freight_id) OPTION(MAXDOP 1);
        UPDATE w SET em_publicacao=0 FROM core.dashboard_freight_winner w JOIN #affected a ON w.freight_id=a.freight_id
        WHERE w.em_publicacao=1 AND NOT EXISTS(SELECT 1 FROM #winning n WHERE n.freight_id=w.freight_id) OPTION(MAXDOP 1);

        IF NOT EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WITH(UPDLOCK,HOLDLOCK)
            WHERE source_scope=@source_scope AND entity_name=@entity_name AND business_date=@business_date AND latest_run_id=@run)
            THROW 52066,'FRETES_CAPTURE_SUPERSEDED',1;
        IF EXISTS(SELECT 1 FROM ctl.production_ingestion_coverage WITH(HOLDLOCK)
            WHERE entity_name='FRETES' AND source_scope<>@source_scope)
            THROW 52063,'FRETES_SINGLE_SOURCE_REQUIRED',1;
        COMMIT;
    END TRY
    BEGIN CATCH
        IF XACT_STATE()<>0 ROLLBACK;
        THROW;
    END CATCH;
END;
GO
