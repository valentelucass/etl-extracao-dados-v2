SET NOCOUNT ON;
IF @@TRANCOUNT=0 THROW 52229,'O teste exige transacao com reversao.',1;
DECLARE @eventos nvarchar(max)=N'[
 {"CodSolicitacao":900000000000001,"DataHoraRealIni":"2026-10-31T20:30:00-03:00","DataHoraRealFim":"2026-11-01T02:00:00-03:00","ColetasEntregas":[{"Ordem":1},{"Ordem":2}]},
 {"CodSolicitacao":900000000000002,"DataHoraRealIni":"2026-11-01T14:30:00-03:00","DataHoraRealFim":"2026-11-01T19:00:00-03:00"},
 {"CodSolicitacao":900000000000003,"DataHoraRealIni":"2026-10-30T12:00:00-03:00","DataHoraRealFim":"2026-11-03T01:00:00-03:00"},
 {"CodSolicitacao":900000000000004,"DataHoraRealIni":"2026-11-01T15:00:00-03:00"},
 {"CodSolicitacao":900000000000005,"DataHoraRealIni":"1900-01-01T00:00:00-03:00","DataHoraRealFim":"2026-11-01T09:00:00-03:00"}
]';
DECLARE @t datetimeoffset(3)='2026-11-05T12:00:00Z';
DECLARE @replay datetimeoffset(3)=DATEADD(hour,2,@t);
DECLARE @rev TABLE(afetadas int);
INSERT @rev EXEC core.usp_dashboard_receber_raster_entre_dias '20261030','20261103',@t,5,@eventos;
IF (SELECT MAX(afetadas) FROM @rev)<>2 THROW 52230,'A virada de mes e a viagem de varios dias devem entrar uma vez.',1;
INSERT @rev EXEC core.usp_dashboard_receber_raster_entre_dias '20261030','20261104',@replay,5,@eventos;
IF (SELECT SUM(afetadas) FROM @rev)<>2 THROW 52231,'Janelas ou filiais sobrepostas duplicaram uma SM.',1;
DECLARE @mudanca nvarchar(max)=JSON_MODIFY(@eventos,'$[0].DataHoraRealFim','2026-11-01T03:00:00-03:00');
DECLARE @anterior datetimeoffset(3)=DATEADD(hour,1,@t);
INSERT @rev EXEC core.usp_dashboard_receber_raster_entre_dias '20261030','20261104',@anterior,5,@mudanca;
IF (SELECT SUM(afetadas) FROM @rev)<>2 THROW 52232,'Uma resposta atrasada alterou uma viagem ja reconferida.',1;
DECLARE @posterior datetimeoffset(3)=DATEADD(hour,1,@replay);
INSERT @rev EXEC core.usp_dashboard_receber_raster_entre_dias '20261030','20261104',@posterior,5,@mudanca;
IF (SELECT SUM(afetadas) FROM @rev)<>3 THROW 52233,'A correcao mais recente da origem nao foi publicada.',1;
IF (SELECT COUNT(*) FROM pub.raster_viagens_preparada WHERE cod_solicitacao BETWEEN 900000000000001 AND 900000000000005)<>2
 OR (SELECT COUNT(*) FROM pub.raster_viagem_paradas_preparada WHERE cod_solicitacao=900000000000001)<>2
 THROW 52234,'Viagens e paradas precisam refletir a mesma revisao da origem.',1;
IF NOT EXISTS(SELECT 1 FROM core.dashboard_raster_pendente) THROW 52235,'Preparo nao percebeu as viagens novas.',1;
DECLARE @corrigida nvarchar(max)=JSON_MODIFY(@mudanca,'$[0].DataHoraRealFim','2026-10-31T23:00:00-03:00');
DECLARE @ultima datetimeoffset(3)=DATEADD(hour,1,@posterior);
INSERT @rev EXEC core.usp_dashboard_receber_raster_entre_dias '20261030','20261104',@ultima,5,@corrigida;
IF (SELECT SUM(afetadas) FROM @rev)<>4 OR NOT EXISTS(
    SELECT 1 FROM core.dashboard_raster_vigente WHERE source_key=900000000000001
      AND JSON_VALUE(record_json,'$.DataHoraRealFim')='2026-10-31T23:00:00-03:00')
    THROW 52236,'A mudanca para chegada no mesmo dia nao substituiu a observacao anterior.',1;
