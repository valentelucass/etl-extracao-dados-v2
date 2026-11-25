SET NOCOUNT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2' OR @@TRANCOUNT=0 THROW 52321, 'Executar na transacao de instalacao.', 1;
DECLARE @dia date=(SELECT MAX(data_particao) FROM ctl.dashboard_performance_ocorrencias_particao);
DECLARE @esperados bigint=(SELECT COUNT_BIG(*) FROM core.dashboard_performance_ocorrencias WHERE data_particao=@dia);
EXECUTE AS USER='usuario_etl';
BEGIN TRY
    IF (SELECT COUNT_BIG(*) FROM pub.dashboard_ocorrencias_tratativas WHERE data_particao=@dia)<>@esperados
        THROW 52322,'A leitura mudou a quantidade de ocorrencias.',1;
    IF EXISTS (SELECT entidade,dia FROM pub.dashboard_cobertura_sinistros GROUP BY entidade,dia HAVING COUNT(*)>1)
        THROW 52323,'Cobertura duplicada.',1;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()='usuario_etl' REVERT;
    THROW;
END CATCH;
IF EXISTS (SELECT p.escopo,p.data_particao,r.occurrence_ordinal,TRY_CONVERT(datetimeoffset(3),JSON_VALUE(r.source_envelope,'$.record.ice_ttt_solution_deadline')),
                TRY_CONVERT(datetimeoffset(3),JSON_VALUE(r.source_envelope,'$.record.ice_ttt_solution_at'))
           FROM ctl.dashboard_performance_ocorrencias_particao p JOIN stg.production_export_occurrence r ON r.run_id=p.captura WHERE p.data_particao=@dia
           EXCEPT SELECT escopo,data_particao,ordem,prazo,solucao FROM pub.dashboard_ocorrencias_tratativas WHERE data_particao=@dia)
    THROW 52324,'Os horarios publicados divergem da captura.',1;
SELECT @dia AS dia, @esperados AS ocorrencias FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
