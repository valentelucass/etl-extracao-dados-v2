-- Confere todas as competências, inclusive rateios do mesmo título em vários centros.
IF OBJECT_DEFINITION(OBJECT_ID('core.usp_dashboard_publicar_fatos_fretes')) NOT LIKE '%EXEC core.usp_dashboard_preparar_dre_pagar;%'
    THROW 52422,'Preparacao do DRE ausente da rotina regular.',1;
IF EXISTS (SELECT 1 FROM pub.dashboard_dre_pagar WHERE linha_dre IN ('D24','D110','D185') AND conta<>N'FGTS')
    THROW 52423,'Conta indevida no FGTS.',1;
SELECT p.[Lançamento a Pagar/N°] numero, p.[Emissão] emissao, m.linha, j.conta,
    MIN(j.valor) minimo,MAX(j.valor) maximo INTO #dre_validar
FROM stg.production_export_latest l CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH
    (numero bigint '$.ant_ils_sequence_code',conta nvarchar(255) '$.ant_ils_pas_ant_name',
     valor decimal(19,2) '$.ant_ils_pas_value',usuario nvarchar(255) '$.ant_uer_name') j
JOIN core.dashboard_contas_leitura p ON p.[Lançamento a Pagar/N°]=j.numero AND p.[Emissão]=l.business_date
JOIN (VALUES (N'FGTS','FOLHA'),(N'SALÁRIOS','FOLHA'),(N'HORAS EXTRAS','FOLHA'),(N'AJUDA DE CUSTOS','FOLHA'),
    (N'GRATIFICAÇÕES','FOLHA'),(N'INCENTIVO DE MOTORISTAS','FOLHA'),(N'PRÊMIOS','FOLHA'),(N'INCENTIVO/PREMIOS','FOLHA'),
    (N'PLR','FOLHA'),(N'INSS','FOLHA'),(N'INSS - (SENAT, SEST, SAL EDUC,INCRA,SEBRAE,RAT)','FOLHA'),
    (N'FGTS - MULTA 40%','FOLHA'),(N'FÉRIAS','FOLHA'),(N'RESCISÕES','FOLHA'),(N'13º SALÁRIO','FOLHA'),(N'13º','FOLHA'),
    (N'CESTA BÁSICA','FOLHA'),(N'VALE ALIMENTAÇÃO','FOLHA'),(N'VALE COMBUSTÍVEL','FOLHA'),(N'VALE TRANSPORTE','FOLHA'),
    (N'ICMS A RECOLHER','D8'),(N'PIS A RECOLHER','D9'),
    (N'COFINS A RECOLHER','D9'),(N'CONTRIBUIÇÃO PREVIDENCIARIA A RECOLHER','D10'),
    (N'ISS A RECOLHER','D11'),(N'ABASTECIMENTOS ( TRIVALE E POSTOS )','D36'),
    (N'SOFTWARES E LICENÇAS','D130'),(N'SOFTWARE E LICENÇAS','D130'),(N'PLANO DE SAÚDE','D143')) m(conta,linha)
    ON m.conta=j.conta COLLATE Latin1_General_100_CI_AI
WHERE l.entity_name='CONTAS_A_PAGAR'
  AND l.source_scope=(SELECT MIN(source_scope) FROM ctl.production_ingestion_coverage WHERE entity_name='CONTAS_A_PAGAR' HAVING COUNT(DISTINCT source_scope)=1)
  AND (m.linha<>'D36' OR j.usuario IN (N'Elivaldo - Controladoria',N'Elivaldo - Controladoria (MTZ)'))
GROUP BY p.[Lançamento a Pagar/N°],p.[Emissão],m.linha,j.conta;
SELECT numero,conta,CASE WHEN linha_dre IN ('D8','D9','D10','D11','D36','D130','D143') THEN linha_dre ELSE 'FOLHA' END linha,SUM(valor) valor
INTO #dre_obtido FROM pub.dashboard_dre_pagar WHERE linha_dre IS NOT NULL
GROUP BY numero,conta,CASE WHEN linha_dre IN ('D8','D9','D10','D11','D36','D130','D143') THEN linha_dre ELSE 'FOLHA' END;
IF EXISTS (SELECT numero,conta,linha,maximo FROM #dre_validar WHERE minimo=maximo
           EXCEPT SELECT numero,conta,linha,valor FROM #dre_obtido)
    OR EXISTS (SELECT numero,conta,linha FROM #dre_obtido EXCEPT SELECT numero,conta,linha FROM #dre_validar)
    THROW 52424,'O DRE diverge do ESL para folha, impostos ou combustivel.',1;
IF EXISTS (SELECT numero,ordem FROM pub.dashboard_dre_pagar GROUP BY numero,ordem HAVING COUNT_BIG(*)>1)
    THROW 52425,'Apropriacao repetida no DRE.',1;
SELECT COUNT_BIG(*) titulosConferidos,(SELECT COUNT_BIG(*) FROM pub.dashboard_dre_pagar) parcelasDre FROM #dre_validar FOR JSON PATH, WITHOUT_ARRAY_WRAPPER;
