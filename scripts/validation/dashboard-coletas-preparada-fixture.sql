-- Executar apenas numa base de teste com os contratos original e preparado instalados.
SET NOCOUNT ON;
DECLARE @scope char(64)=REPLICATE('a',64), @day1 date='2026-11-01', @day2 date='2026-11-02',
    @first uniqueidentifier=NEWID(), @second uniqueidentifier=NEWID(),
    @deleted uniqueidentifier=NEWID(), @replacement uniqueidentifier=NEWID(), @empty uniqueidentifier=NEWID();

INSERT ctl.production_ingestion_run
VALUES (@first,@scope,'COLETAS',@day1,@day1,'STORED',2),
       (@second,@scope,'COLETAS',@day2,@day2,'STORED',2),
       (@deleted,@scope,'COLETAS_EXCLUIDAS',@day2,@day2,'STORED',1),
       (@replacement,@scope,'COLETAS',@day2,@day2,'STORED',1),
       (@empty,@scope,'COLETAS_EXCLUIDAS',@day2,@day2,'STORED',0);

INSERT stg.production_export_occurrence
VALUES (@first,1,N'{"record":{"id":"A","sequence_code":1,"request_date":"2026-11-01","request_hour":"10:00:00","updated_at":"2026-11-01T13:00:00+00:00","status":"pending","invoices_value":1.50}}'),
       (@first,2,N'{"record":{"id":"B","sequence_code":2,"request_date":"2026-11-01","updated_at":"2026-11-01T13:00:00+00:00","status":"manifested"}}'),
       (@second,1,N'{"record":{"id":"A","sequence_code":1,"request_date":"2026-11-02","updated_at":"2026-11-02T13:00:00+00:00","status":"done","invoices_value":2.50}}'),
       (@second,2,N'{"record":{"id":"C","sequence_code":3,"request_date":"2026-11-02","updated_at":"2026-11-02T12:00:00+00:00","status":"draft"}}'),
       (@deleted,1,N'{"record":{"id":"B","sequence_code":2,"request_date":"2026-11-01","deleted_at":"2026-11-02T18:00:00+00:00","destroy_reason":"teste"}}'),
       (@replacement,1,N'{"record":{"id":"D","sequence_code":4,"request_date":"2026-11-02","updated_at":"2026-11-02T14:00:00+00:00","status":"done"}}');

INSERT ctl.production_ingestion_coverage
VALUES (@scope,'COLETAS',@day1,@first,'2026-11-01T19:00:00'),
       (@scope,'COLETAS',@day2,@second,'2026-11-02T19:00:00'),
       (@scope,'COLETAS_EXCLUIDAS',@day2,@deleted,'2026-11-02T20:00:00');

IF (SELECT COUNT(*) FROM pub.vw_dashboard_particoes_pendentes)<>3
    THROW 52050, 'INVALID_INITIAL_QUEUE', 1;

EXEC core.usp_dashboard_preparar_coletas 'COLETAS', @scope, @day2;
EXEC core.usp_dashboard_preparar_coletas 'COLETAS', @scope, @day1;
EXEC core.usp_dashboard_preparar_coletas 'COLETAS_EXCLUIDAS', @scope, @day2;

IF (SELECT COUNT(*) FROM pub.vw_coletas_powerbi_preparada)<>3
 OR NOT EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada WHERE [ID]='A' AND [Solicitacao]=@day2)
 OR NOT EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada WHERE [ID]='B' AND [Excluída na Origem]=1)
    THROW 52055, 'WRONG_PREPARED_INITIAL_STATE', 1;

SELECT * INTO #esperada_inicial
FROM pub.vw_coletas_powerbi OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 0.1);
IF EXISTS (SELECT * FROM #esperada_inicial EXCEPT SELECT * FROM pub.vw_coletas_powerbi_preparada)
 OR EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada EXCEPT SELECT * FROM #esperada_inicial)
 OR (SELECT COUNT(*) FROM pub.vw_coletas_powerbi_preparada)<>3
 OR EXISTS (SELECT * FROM pub.vw_dashboard_particoes_pendentes)
 OR NOT EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada WHERE [ID]='B' AND [Excluída na Origem]=1)
    THROW 52051, 'INITIAL_PARITY_FAILED', 1;

DECLARE @previousPublication datetime2(7)=
    (SELECT published_utc FROM ctl.dashboard_projection_partition WHERE applied_run_id=@second);
EXEC core.usp_dashboard_preparar_coletas 'COLETAS', @scope, @day2;
IF @previousPublication<>(SELECT published_utc FROM ctl.dashboard_projection_partition WHERE applied_run_id=@second)
    THROW 52052, 'REPLAY_REPUBLISHED_SAME_RUN', 1;

UPDATE ctl.production_ingestion_coverage SET latest_run_id=@replacement, stored_utc='2026-11-03T19:00:00'
WHERE latest_run_id=@second;
UPDATE ctl.production_ingestion_coverage SET latest_run_id=@empty, stored_utc='2026-11-03T19:00:00'
WHERE latest_run_id=@deleted;

IF (SELECT COUNT(*) FROM pub.vw_dashboard_particoes_pendentes)<>2
 OR (SELECT COUNT(*) FROM pub.vw_coletas_powerbi_preparada)<>3
    THROW 52053, 'LAST_PUBLISHED_STATE_NOT_PRESERVED', 1;

EXEC core.usp_dashboard_preparar_coletas 'COLETAS', @scope, @day2;
EXEC core.usp_dashboard_preparar_coletas 'COLETAS_EXCLUIDAS', @scope, @day2;

SELECT * INTO #esperada_reposicao
FROM pub.vw_coletas_powerbi OPTION (MAXDOP 1, MAX_GRANT_PERCENT = 0.1);
IF EXISTS (SELECT * FROM #esperada_reposicao EXCEPT SELECT * FROM pub.vw_coletas_powerbi_preparada)
 OR EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada EXCEPT SELECT * FROM #esperada_reposicao)
 OR (SELECT COUNT(*) FROM pub.vw_coletas_powerbi_preparada)<>3
 OR EXISTS (SELECT * FROM pub.vw_dashboard_particoes_pendentes)
 OR NOT EXISTS (SELECT * FROM core.dashboard_pickup_current WHERE [ID]='C' AND em_publicacao=0)
 OR NOT EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada WHERE [ID]='A' AND [Solicitacao]=@day1)
 OR NOT EXISTS (SELECT * FROM pub.vw_coletas_powerbi_preparada WHERE [ID]='B' AND [Excluída na Origem]=0)
    THROW 52054, 'REPLACEMENT_PARITY_FAILED', 1;

SELECT 'PARITY_41_COLUMNS_REPLAY_EMPTY_REPLACEMENT_AND_DELETION_PASS';

