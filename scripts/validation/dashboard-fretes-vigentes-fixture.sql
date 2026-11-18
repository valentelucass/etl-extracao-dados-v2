-- Mesmo esquema mínimo de recebimento do ensaio de Coletas em base descartável.
SET NOCOUNT ON;
DECLARE @scope char(64)=REPLICATE('f',64), @one date='20261101', @two date='20261102',
    @r1 uniqueidentifier=NEWID(),@r2 uniqueidentifier=NEWID(),@empty uniqueidentifier=NEWID();
INSERT ctl.production_ingestion_run VALUES
    (@r1,@scope,'FRETES',@one,@one,'STORED',2),(@r2,@scope,'FRETES',@two,@two,'STORED',3),
    (@empty,@scope,'FRETES',@two,@two,'STORED',0);
INSERT stg.production_export_occurrence VALUES
    (@r1,1,N'{"record":{"id":10,"corporation_sequence_number":100}}'),
    (@r1,2,N'{"record":{"id":11,"corporation_sequence_number":100}}'),
    (@r2,1,N'{"record":{"id":10,"fit_fhe_cte_created_at":"2026-11-02T10:00:00Z"}}'),
    (@r2,2,N'{"record":{"id":10,"fit_nse_issued_at":"2026-11-02T12:00:00Z"}}'),
    (@r2,3,N'{"record":{"id":12}}');
INSERT ctl.production_ingestion_coverage VALUES
    (@scope,'FRETES',@one,@r1,'2026-11-01'),(@scope,'FRETES',@two,@r2,'2026-11-02');
EXEC core.usp_dashboard_preparar_fretes_vigentes 'FRETES',@scope,@two;
EXEC core.usp_dashboard_preparar_fretes_vigentes 'FRETES',@scope,@one;
IF (SELECT COUNT(*) FROM core.dashboard_fretes_vigentes_preparada)<>3 OR NOT EXISTS
    (SELECT 1 FROM core.dashboard_fretes_vigentes_preparada WHERE freight_id=10 AND run_id=@r2 AND occurrence_ordinal=2)
    THROW 52067,'FRETES_WINNER_OR_GRAIN_MISMATCH',1;
UPDATE ctl.production_ingestion_coverage SET latest_run_id=@empty WHERE latest_run_id=@r2;
EXEC core.usp_dashboard_preparar_fretes_vigentes 'FRETES',@scope,@two;
IF NOT EXISTS(SELECT 1 FROM core.dashboard_fretes_vigentes_preparada WHERE freight_id=10 AND run_id=@r1)
 OR NOT EXISTS(SELECT 1 FROM core.dashboard_freight_winner WHERE freight_id=12 AND em_publicacao=0)
    THROW 52068,'FRETES_EMPTY_REPLACEMENT_FAILED',1;
UPDATE ctl.production_ingestion_coverage SET latest_run_id=@r2 WHERE latest_run_id=@empty;
EXEC core.usp_dashboard_preparar_fretes_vigentes 'FRETES',@scope,@two;
EXEC core.usp_dashboard_preparar_fretes_vigentes 'FRETES',@scope,@two;
IF (SELECT COUNT(*) FROM core.dashboard_freight_occurrence_index)<>5 OR NOT EXISTS
    (SELECT 1 FROM core.dashboard_fretes_vigentes_preparada WHERE freight_id=10 AND occurrence_ordinal=2 AND run_id=@r2)
    THROW 52069,'FRETES_REPLAY_FAILED',1;
