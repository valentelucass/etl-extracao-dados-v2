SET NOCOUNT ON;
-- Mesmo sinistro, duas versões. Cada campo tem um valor próprio para detectar trocas.
DECLARE @antigo nvarchar(max) = N'{"sequence_code":17,"icm_crn_psn_nickname":"Filial A",
 "icm_dvr_iil_name":"Motorista B","icm_ttt_ore_description":"Avaria na entrega","icm_ttt_ore_code":21,
 "customer_communication_contact_name":"Cliente C","customer_communication_date":"2026-09-04",
 "customer_communication_time":"17:10","policy_number":"00047","icm_fis_ioe_number":"00015",
 "occurrence_at_time":"2000-01-01T23:59:58.1234567-03:00","finished_at_time":"01:02:03",
 "rctrc":["B","A","B"],"rctac":[],"invoices_weight":"17.12345678","invoices_value":"1219.90",
 "icm_ttt_dealing_type":"send_to_incidents_dealing","icm_ttt_solution_type":"regra_futura"}';
DECLARE @atual nvarchar(max) = JSON_MODIFY(@antigo,'$.customer_communication_time',NULL);
SET @atual=JSON_MODIFY(@atual,'$.insurer_communication_contact_name',N'Seguradora D');
SET @atual=JSON_MODIFY(@atual,'$.insurer_communication_date','2026-09-06');
SET @atual=JSON_MODIFY(@atual,'$.icm_ire_cpy_nickname',N'Empresa E');
DECLARE @cabecalho nvarchar(max) =
    N'Nº do Sinistro|Data abertura|Data ocorrência|Hora ocorrência|Previsão de solução|Local do sinistro|Informado por|RCTRC|RCTAC|RCFDC|'
  + N'Comentário do sinistro|Relação dos produtos sinistrados|Responsável da Seguradora|Data de Comunicação a Seguradora|'
  + N'Responsável do Cliente|Data de Comunicação ao Cliente|Seguradora/Nome|Nº da apólice|Nº do processo da corretora|Nº do BO|'
  + N'Data finalização|Hora finalização|Comentário da finalização|Descrição interna|Quantidade de documentos|Minuta|Nota Fiscal/Número|'
  + N'Volumes|Peso|Valor NF|Pagador do frete/Nome fantasia|valor a pagar ao cliente|Valor nota de crédito ao cliente|'
  + N'Valor a receber do responsável|Valor nota de débito do responsável|Valor a receber da seguradora|Resultado final|'
  + N'Filial/Nome|Motorista/Nome|Usuário/Nome|Veículo/Placa|Ocorrência|Ocorrência/Código|Tratativa/Data tratativa|Tratativa/Tipo tratativa|Tratativa/Solução';
IF ISNULL((SELECT STRING_AGG(CONVERT(nvarchar(max), name), N'|') WITHIN GROUP (ORDER BY column_id)
           FROM sys.columns WHERE object_id=OBJECT_ID('pub.dashboard_sinistros')), N'') COLLATE Latin1_General_100_BIN2 <> @cabecalho
    THROW 52303,'Cabecalho atual de Sinistros divergente.',1;
IF NOT EXISTS (
    SELECT 1 FROM core.ufn_dashboard_sinistro(@antigo)
    WHERE [Responsável da Seguradora] IS NULL AND [Data de Comunicação a Seguradora] IS NULL AND [Seguradora/Nome] IS NULL
      AND [Responsável do Cliente]=N'Cliente C' AND [Data de Comunicação ao Cliente]='20260904'
      AND [Filial/Nome]=N'Filial A' AND [Motorista/Nome]=N'Motorista B' AND [Ocorrência]=N'Avaria na entrega'
      AND [Ocorrência/Código]=21 AND [Nº da apólice]=N'00047' AND [Nota Fiscal/Número]=N'00015'
      AND [Hora ocorrência]='23:59:58.1234567' AND [Hora finalização]='01:02:03'
      AND [RCTRC]=N'B, A, B' AND [RCTAC] IS NULL AND [RCFDC] IS NULL AND [Peso]=17.12345678
      AND [Valor NF]=1219.90 AND [Tratativa/Tipo tratativa]=N'Enviar para tratativas de sinistros'
      AND [Tratativa/Solução]=N'regra_futura'
) THROW 52304,'Conversao do formato historico divergente.',1;
IF NOT EXISTS (SELECT 1 FROM core.ufn_dashboard_sinistro(@atual)
    WHERE [Responsável da Seguradora]=N'Seguradora D' AND [Data de Comunicação a Seguradora]='20260906'
      AND [Seguradora/Nome]=N'Empresa E' AND [Responsável do Cliente]=N'Cliente C')
    THROW 52305,'Comunicacoes de seguradora e cliente divergentes.',1;
IF NOT EXISTS (SELECT 1 FROM core.ufn_dashboard_sinistro(N'{"insurer_communication_contact_name":"  ","icm_ire_cpy_nickname":""}')
    WHERE [Responsável da Seguradora] IS NULL AND [Data de Comunicação a Seguradora] IS NULL AND [Seguradora/Nome] IS NULL)
    THROW 52306,'Campos opcionais vazios foram preenchidos.',1;

-- Exercita as duas consultas com o leitor do painel, inclusive clientes sem histórico associado.
SELECT TOP (0) * INTO #base_nova FROM pub.dashboard_sinistros;
SELECT TOP (0) * INTO #historico FROM pub.dashboard_sinistros_ocorrencias_documento;
BEGIN TRY
    EXECUTE AS USER=N'usuario_etl';
    INSERT #base_nova SELECT * FROM pub.dashboard_sinistros;
    INSERT #historico SELECT * FROM pub.dashboard_sinistros_ocorrencias_documento;
    REVERT;
END TRY
BEGIN CATCH
    IF USER_NAME()=N'usuario_etl' REVERT;
    THROW;
END CATCH;
DECLARE @linhas bigint=(SELECT COUNT_BIG(*) FROM #base_nova);
SELECT l.source_scope, j.numero,j.minuta,j.nota,j.codigo,j.valor,j.resultado
INTO #origem_sinistros
FROM stg.production_export_latest AS l
CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
    numero decimal(38,0) '$.sequence_code', minuta decimal(38,0) '$.icm_fis_fit_corporation_sequence_number',
    nota nvarchar(128) '$.icm_fis_ioe_number', codigo decimal(38,0) '$.icm_ttt_ore_code',
    valor decimal(28,8) '$.invoices_value', resultado decimal(28,8) '$.insurance_claim_total') AS j
WHERE l.entity_name='SINISTROS'
  AND (SELECT COUNT_BIG(DISTINCT source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name='SINISTROS')=1;
-- Preserva multiplicidade por documento, inclusive valores nulos e sinistros com várias linhas.
IF @linhas<>(SELECT COUNT_BIG(*) FROM #origem_sinistros) OR EXISTS (
    SELECT numero,minuta,nota,codigo,valor,resultado,COUNT_BIG(*) FROM #origem_sinistros
    GROUP BY numero,minuta,nota,codigo,valor,resultado
    EXCEPT
    SELECT [Nº do Sinistro],[Minuta],[Nota Fiscal/Número],[Ocorrência/Código],[Valor NF],[Resultado final],COUNT_BIG(*)
    FROM #base_nova GROUP BY [Nº do Sinistro],[Minuta],[Nota Fiscal/Número],[Ocorrência/Código],[Valor NF],[Resultado final]
) THROW 52307,'Sinistros, documentos, multiplicidades ou valores divergentes.',1;

SELECT DISTINCT s.numero,s.minuta,s.nota,s.codigo,o.escopo,o.data_particao,o.ordem
INTO #eventos_esperados
FROM #origem_sinistros s JOIN core.dashboard_performance_ocorrencias o
  ON o.escopo=s.source_scope AND o.minuta=TRY_CONVERT(bigint,s.minuta)
 AND o.nota_fiscal=s.nota AND o.codigo=TRY_CONVERT(int,s.codigo)
WHERE s.numero IS NOT NULL AND NULLIF(TRIM(s.nota),N'') IS NOT NULL;
IF (SELECT COUNT_BIG(*) FROM #historico)<>(SELECT COUNT_BIG(*) FROM #eventos_esperados) OR EXISTS (
    SELECT numero,minuta,nota,codigo,escopo,data_particao,ordem FROM #eventos_esperados
    EXCEPT
    SELECT [Nº do Sinistro],[Minuta],[Nota Fiscal/Número],[Ocorrência/Código],escopo,data_particao,ordem FROM #historico
) THROW 52308,'Historico por documento e codigo divergente.',1;
SELECT @linhas linhas, COUNT(DISTINCT [Nº do Sinistro]) sinistros,
       (SELECT COUNT_BIG(*) FROM #historico) ocorrencias_do_documento FROM #base_nova
FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
