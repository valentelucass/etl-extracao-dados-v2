CREATE OR ALTER VIEW pub.fato_gestao_vista_fretes
AS
WITH documentos_filiais_operacionais AS (
    SELECT document_hash AS documento FROM ref.dashboard_frete_regras_documento WHERE rule_type='BRANCH'
), pagadores_excluidos_cubagem AS (
    SELECT document_hash AS documento FROM ref.dashboard_frete_regras_documento WHERE rule_type='CUBAGE'
), localizacoes AS (
    SELECT [N° Minuta] AS sequence_number,
           [Volumes] AS invoices_volumes,
           [Previsão Entrega/Previsão de entrega] AS predicted_delivery_at,
           [Responsável pela Região de Destino] AS destination_branch_nickname,
           NULLIF(LOWER(TRIM([Responsável pela Região de Destino])),N'') AS destination_branch_key,
           [Região Destino] AS destination_location_name,
           CONVERT(datetime2(0),SWITCHOFFSET([Data de extracao] AT TIME ZONE 'E. South America Standard Time','+00:00')) AS data_extracao,
           ROW_NUMBER() OVER (PARTITION BY [N° Minuta] ORDER BY [Data de extracao] DESC,
               [Data do frete] DESC,[Hash Localização] DESC) AS rank_in_minute
    FROM pub.vw_localizacao_cargas_powerbi
),
fretes_fonte AS (
    SELECT
        f.id AS frete_id_origem,
        COALESCE(f.cte_issued_at,f.cte_created_at,f.nfse_issued_at) AS documento_em,
        f.updated_at AS frete_atualizado_em,
        f.corporation_sequence_number AS numero_minuta,
        f.servico_em AS data_frete,
        CAST(f.servico_em AS DATE) AS data_frete_date,
        COALESCE(CAST(f.data_previsao_entrega AS DATE), CAST(lc.predicted_delivery_at AS DATE)) AS data_previsao_entrega,
        CAST(COALESCE(f.fit_dpn_performance_finished_at, f.finished_at) AS DATE) AS data_finalizacao_performance,
        COALESCE(NULLIF(LTRIM(RTRIM(f.filial_nome)), N''), N'Nao informado') AS filial_emissora,
        NULLIF(LOWER(LTRIM(RTRIM(f.filial_nome))), N'') AS filial_emissora_key,
        COALESCE(
            NULLIF(LTRIM(RTRIM(lc.destination_branch_nickname)), N''),
            NULLIF(LTRIM(RTRIM(f.filial_nome)), N'')
        ) AS filial_performance,
        COALESCE(lc.destination_branch_key, f.filial_nome_key) AS filial_performance_key,
        COALESCE(
            NULLIF(LTRIM(RTRIM(lc.destination_branch_nickname)), N''),
            NULLIF(LTRIM(RTRIM(f.filial_nome)), N'')
        ) AS responsavel_regiao_destino,
        COALESCE(lc.destination_branch_key, f.filial_nome_key, N'sem_responsavel') AS responsavel_regiao_destino_key,
        COALESCE(NULLIF(LTRIM(RTRIM(lc.destination_location_name)), N''), f.destino_uf, N'SEM_REGIAO') AS regiao_destino,
        NULLIF(LTRIM(RTRIM(f.pagador_nome)), N'') AS pagador_nome,
        NULLIF(LTRIM(RTRIM(f.pagador_documento)), N'') AS pagador_documento,
        docs.pagador_documento_key,
        NULLIF(LTRIM(RTRIM(f.remetente_documento)), N'') AS remetente_documento,
        NULLIF(LTRIM(RTRIM(f.destinatario_documento)), N'') AS destinatario_documento,
        NULLIF(LTRIM(RTRIM(f.destino_cidade)), N'') AS destino,
        NULLIF(LTRIM(RTRIM(f.destino_cidade)), N'') AS destino_cidade,
        NULLIF(LTRIM(RTRIM(f.destino_uf)), N'') AS destino_uf,
        LOWER(NULLIF(LTRIM(RTRIM(
            CASE f.status
                WHEN N'pending' THEN N'pendente'
                WHEN N'finished' THEN N'finalizado'
                WHEN N'in_transit' THEN N'em transito'
                WHEN N'standby' THEN N'aguardando'
                WHEN N'manifested' THEN N'registrado'
                WHEN N'occurrence_treatment' THEN N'tratamento de ocorrencia'
                ELSE f.status
            END
        )), N'')) AS status_norm,
        CASE
            WHEN f.cte_id IS NOT NULL
              OR NULLIF(LTRIM(RTRIM(f.chave_cte)), N'') IS NOT NULL
              OR f.numero_cte IS NOT NULL
              OR f.serie_cte IS NOT NULL THEN N'CT-E'
            WHEN f.nfse_number IS NOT NULL
              OR NULLIF(LTRIM(RTRIM(f.nfse_series)), N'') IS NOT NULL
              THEN N'NFS-E'
            ELSE N'PENDENTE/NAO EMITIDO'
        END AS documento_oficial_tipo,
        UPPER(NULLIF(LTRIM(RTRIM(REPLACE(COALESCE(f.tipo_frete, N''), N'Freight::', N''))), N'')) AS tipo_frete,
        f.modal,
        f.valor_total,
        f.taxed_weight AS peso_taxado,
        f.real_weight AS peso_real,
        f.cubages_cubed_weight AS peso_cubado,
        f.total_cubic_volume AS total_m3,
        COALESCE(lc.invoices_volumes, f.invoices_total_volumes, 0) AS volumes,
        CASE
            WHEN COALESCE(CAST(f.data_previsao_entrega AS DATE), CAST(lc.predicted_delivery_at AS DATE)) IS NULL
              OR COALESCE(f.fit_dpn_performance_finished_at, f.finished_at) IS NULL THEN NULL
            ELSE DATEDIFF(
                DAY,
                COALESCE(CAST(f.data_previsao_entrega AS DATE), CAST(lc.predicted_delivery_at AS DATE)),
                CAST(COALESCE(f.fit_dpn_performance_finished_at, f.finished_at) AS DATE)
            )
        END AS performance_diferenca_dias,
        CONVERT(bit,0) AS excluido_na_origem,
        CAST(CASE WHEN COALESCE(f.cortesia, 0) = 1 THEN 1 ELSE 0 END AS BIT) AS is_cortesia,
        data_atualizacao.data_extracao
    FROM core.dashboard_fretes_linhas AS f
    LEFT JOIN localizacoes AS lc
        ON lc.sequence_number = f.corporation_sequence_number
       AND lc.rank_in_minute=1
    CROSS APPLY (
        SELECT UPPER(NULLIF(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
            LTRIM(RTRIM(COALESCE(f.pagador_documento, N''))),
            N'.', N''), N'-', N''), N'/', N''), N' ', N''), CHAR(9), N''), N'')) AS pagador_documento_key
    ) AS docs
    CROSS APPLY (
        SELECT MAX(valor) AS data_extracao
        FROM (VALUES (f.data_extracao), (lc.data_extracao)) AS atualizacoes(valor)
    ) AS data_atualizacao
    WHERE f.corporation_sequence_number IS NOT NULL
      AND UPPER(NULLIF(LTRIM(RTRIM(REPLACE(COALESCE(f.tipo_frete, N''), N'Freight::', N''))), N'')) <> N'COMPLEMENTAR'
),
fretes_regras AS (
    SELECT
        ff.*,
        CAST(CASE WHEN ff.documento_oficial_tipo IN (N'CT-E', N'NFS-E') THEN 1 ELSE 0 END AS BIT) AS is_documento_emitido,
        CAST(CASE WHEN ff.status_norm IN (N'cancelado', N'cancelada', N'canceled', N'cancelled') THEN 1 ELSE 0 END AS BIT) AS is_cancelado,
        CAST(CASE WHEN COALESCE(ff.total_m3, 0) <> 0 THEN 1 ELSE 0 END AS BIT) AS is_cubado,
        CAST(CASE WHEN COALESCE(ff.peso_real, 0) > 0 THEN 1 ELSE 0 END AS BIT) AS is_peso_real_informado,
        CAST(CASE WHEN EXISTS (
            SELECT 1
            FROM pagadores_excluidos_cubagem p
            WHERE p.documento = HASHBYTES('SHA2_256',CONVERT(nvarchar(50),ff.pagador_documento_key))
        ) THEN 1 ELSE 0 END AS BIT) AS is_pagador_excluido_cubagem,
        CAST(CASE
            WHEN ff.excluido_na_origem = 1 THEN 0
            WHEN ff.is_cortesia = 1 THEN 0
            WHEN ff.documento_oficial_tipo NOT IN (N'CT-E', N'NFS-E')
             AND EXISTS (
                SELECT 1
                FROM documentos_filiais_operacionais d
                WHERE d.documento = HASHBYTES('SHA2_256',CONVERT(nvarchar(50),ff.pagador_documento_key))
             ) THEN 0
            WHEN ff.documento_oficial_tipo NOT IN (N'CT-E', N'NFS-E')
             AND COALESCE(ff.valor_total, 0) <= 0.01 THEN 0
            WHEN ff.documento_oficial_tipo NOT IN (N'CT-E', N'NFS-E')
             AND ff.tipo_frete LIKE N'%SUBSTITUTE%'
             AND ff.status_norm LIKE N'%pendente%' THEN 0
            ELSE 1
        END AS BIT) AS is_elegivel_operacional,
        CAST(CASE
            WHEN ff.excluido_na_origem = 1 THEN 0
            WHEN ff.is_cortesia = 1 THEN 0
            WHEN ff.documento_oficial_tipo NOT IN (N'CT-E', N'NFS-E')
             AND EXISTS (
                SELECT 1
                FROM documentos_filiais_operacionais d
                WHERE d.documento = HASHBYTES('SHA2_256',CONVERT(nvarchar(50),ff.pagador_documento_key))
             ) THEN 0
            WHEN ff.documento_oficial_tipo NOT IN (N'CT-E', N'NFS-E')
             AND COALESCE(ff.valor_total, 0) <= 0.01 THEN 0
            WHEN ff.documento_oficial_tipo NOT IN (N'CT-E', N'NFS-E')
             AND ff.tipo_frete LIKE N'%SUBSTITUTE%'
             AND ff.status_norm LIKE N'%pendente%' THEN 0
            WHEN COALESCE(ff.valor_total, 0) <= 0.01 THEN 0
            ELSE 1
        END AS BIT) AS is_elegivel_operacional_com_valor
    FROM fretes_fonte AS ff
),
fretes_ranked AS (
    SELECT
        fr.*,
        ROW_NUMBER() OVER (
            PARTITION BY fr.numero_minuta
            ORDER BY
                fr.documento_em DESC,
                fr.frete_atualizado_em DESC,
                CASE WHEN fr.data_finalizacao_performance IS NOT NULL THEN 4 ELSE 0 END
              + CASE WHEN fr.performance_diferenca_dias IS NOT NULL THEN 3 ELSE 0 END
              + CASE WHEN fr.filial_performance_key IS NOT NULL THEN 2 ELSE 0 END
              + CASE WHEN fr.data_extracao IS NOT NULL THEN 1 ELSE 0 END DESC,
                fr.data_extracao DESC,
                fr.frete_id_origem DESC
        ) AS rn
    FROM fretes_regras AS fr
),
fretes_deduplicados AS (
    SELECT *
    FROM fretes_ranked
    WHERE rn = 1
),
indicadores AS (
    SELECT
        indicador.indicador_codigo,
        indicador.data_referencia,
        YEAR(indicador.data_referencia) * 100 + MONTH(indicador.data_referencia) AS data_referencia_yyyymm,
        fd.numero_minuta,
        fd.frete_id_origem,
        fd.data_frete,
        fd.data_frete_date,
        fd.data_previsao_entrega,
        fd.data_finalizacao_performance,
        fd.filial_emissora,
        fd.filial_emissora_key,
        fd.filial_performance,
        fd.filial_performance_key,
        fd.responsavel_regiao_destino,
        fd.responsavel_regiao_destino_key,
        fd.regiao_destino,
        fd.pagador_nome,
        fd.pagador_documento,
        fd.pagador_documento_key,
        fd.remetente_documento,
        fd.destinatario_documento,
        fd.destino,
        fd.destino_cidade,
        fd.destino_uf,
        fd.status_norm,
        fd.documento_oficial_tipo,
        fd.tipo_frete,
        fd.modal,
        fd.valor_total,
        fd.peso_taxado,
        fd.peso_real,
        fd.peso_cubado,
        fd.total_m3,
        fd.volumes,
        TRY_CONVERT(SMALLINT, fd.performance_diferenca_dias) AS performance_diferenca_dias,
        CASE
            WHEN fd.performance_diferenca_dias IS NULL THEN N'EM ABERTO'
            WHEN fd.performance_diferenca_dias <= 0 THEN N'NO PRAZO'
            ELSE N'FORA DO PRAZO'
        END AS performance_status,
        CASE
            WHEN fd.performance_diferenca_dias IS NULL THEN 0
            WHEN fd.performance_diferenca_dias <= 0 THEN 1
            ELSE 2
        END AS performance_status_codigo,
        CAST(CASE WHEN fd.performance_diferenca_dias <= 0 THEN 1 ELSE 0 END AS BIT) AS is_no_prazo,
        CAST(CASE WHEN fd.performance_diferenca_dias > 0 THEN 1 ELSE 0 END AS BIT) AS is_fora_prazo,
        fd.is_cubado,
        fd.is_peso_real_informado,
        fd.is_cancelado,
        fd.is_cortesia,
        fd.is_documento_emitido,
        fd.is_elegivel_operacional,
        fd.is_elegivel_operacional_com_valor,
        fd.is_pagador_excluido_cubagem,
        CAST(CASE
            WHEN fd.excluido_na_origem = 1 THEN 0
            WHEN indicador.indicador_codigo = 'PE'
             AND fd.data_previsao_entrega IS NOT NULL
             AND fd.filial_performance_key IS NOT NULL
             AND fd.is_cancelado = 0
             AND fd.is_elegivel_operacional = 1 THEN 1
            WHEN indicador.indicador_codigo = 'CB'
             AND fd.data_frete_date IS NOT NULL
             AND fd.is_cancelado = 0
             AND fd.is_elegivel_operacional_com_valor = 1
             AND fd.is_pagador_excluido_cubagem = 0 THEN 1
            ELSE 0
        END AS BIT) AS is_linha_valida_indicador,
        fd.excluido_na_origem,
        CONVERT(datetime2(0),NULL) AS data_exclusao_origem,
        fd.data_extracao AS ultima_reconciliacao_origem_em,
        fd.data_extracao,
        fd.data_extracao AS snapshot_em
    FROM fretes_deduplicados AS fd
    CROSS APPLY (VALUES
        (CAST('PE' AS CHAR(2)), fd.data_previsao_entrega),
        (CAST('CB' AS CHAR(2)), fd.data_frete_date)
    ) AS indicador(indicador_codigo, data_referencia)
    WHERE indicador.data_referencia IS NOT NULL
),
origem AS (
    SELECT
        i.*,
        CONVERT(BINARY(32), HASHBYTES('SHA2_256', CONCAT_WS(N'|',
            COALESCE(i.indicador_codigo, N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(30), i.data_referencia, 126), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(20), i.data_referencia_yyyymm), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(30), i.numero_minuta), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(30), i.frete_id_origem), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(48), i.data_frete, 127), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(30), i.data_frete_date, 126), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(30), i.data_previsao_entrega, 126), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(30), i.data_finalizacao_performance, 126), N'__NULL__'),
            COALESCE(i.filial_emissora, N'__NULL__'),
            COALESCE(i.filial_emissora_key, N'__NULL__'),
            COALESCE(i.filial_performance, N'__NULL__'),
            COALESCE(i.filial_performance_key, N'__NULL__'),
            COALESCE(i.responsavel_regiao_destino, N'__NULL__'),
            COALESCE(i.responsavel_regiao_destino_key, N'__NULL__'),
            COALESCE(i.regiao_destino, N'__NULL__'),
            COALESCE(i.pagador_nome, N'__NULL__'),
            COALESCE(i.pagador_documento, N'__NULL__'),
            COALESCE(i.pagador_documento_key, N'__NULL__'),
            COALESCE(i.remetente_documento, N'__NULL__'),
            COALESCE(i.destinatario_documento, N'__NULL__'),
            COALESCE(i.destino, N'__NULL__'),
            COALESCE(i.destino_cidade, N'__NULL__'),
            COALESCE(i.destino_uf, N'__NULL__'),
            COALESCE(i.status_norm, N'__NULL__'),
            COALESCE(i.documento_oficial_tipo, N'__NULL__'),
            COALESCE(i.tipo_frete, N'__NULL__'),
            COALESCE(i.modal, N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(40), i.valor_total), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(40), i.peso_taxado), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(40), i.peso_real), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(40), i.peso_cubado), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(40), i.total_m3), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(20), i.volumes), N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(20), i.performance_diferenca_dias), N'__NULL__'),
            COALESCE(i.performance_status, N'__NULL__'),
            COALESCE(CONVERT(NVARCHAR(10), i.performance_status_codigo), N'__NULL__'),
            CONVERT(NVARCHAR(1), i.is_no_prazo),
            CONVERT(NVARCHAR(1), i.is_fora_prazo),
            CONVERT(NVARCHAR(1), i.is_cubado),
            CONVERT(NVARCHAR(1), i.is_peso_real_informado),
            CONVERT(NVARCHAR(1), i.is_cancelado),
            CONVERT(NVARCHAR(1), i.is_cortesia),
            CONVERT(NVARCHAR(1), i.is_documento_emitido),
            CONVERT(NVARCHAR(1), i.is_elegivel_operacional),
            CONVERT(NVARCHAR(1), i.is_elegivel_operacional_com_valor),
            CONVERT(NVARCHAR(1), i.is_pagador_excluido_cubagem),
            CONVERT(NVARCHAR(1), i.is_linha_valida_indicador),
            CONVERT(NVARCHAR(1), i.excluido_na_origem),
            COALESCE(CONVERT(NVARCHAR(30), i.data_extracao, 126), N'__NULL__')
        ))) AS hash_linha
    FROM indicadores AS i
)
SELECT
    CONVERT(char(2),[indicador_codigo]) AS [indicador_codigo],
    CONVERT(date,[data_referencia]) AS [data_referencia],
    CONVERT(int,[data_referencia_yyyymm]) AS [data_referencia_yyyymm],
    CONVERT(bigint,[numero_minuta]) AS [numero_minuta],
    CONVERT(bigint,[frete_id_origem]) AS [frete_id_origem],
    CONVERT(datetimeoffset(7),[data_frete]) AS [data_frete],
    CONVERT(date,[data_frete_date]) AS [data_frete_date],
    CONVERT(date,[data_previsao_entrega]) AS [data_previsao_entrega],
    CONVERT(date,[data_finalizacao_performance]) AS [data_finalizacao_performance],
    CONVERT(nvarchar(255),[filial_emissora]) AS [filial_emissora],
    CONVERT(nvarchar(255),[filial_emissora_key]) AS [filial_emissora_key],
    CONVERT(nvarchar(255),[filial_performance]) AS [filial_performance],
    CONVERT(nvarchar(255),[filial_performance_key]) AS [filial_performance_key],
    CONVERT(nvarchar(255),[responsavel_regiao_destino]) AS [responsavel_regiao_destino],
    CONVERT(nvarchar(255),[responsavel_regiao_destino_key]) AS [responsavel_regiao_destino_key],
    CONVERT(nvarchar(255),[regiao_destino]) AS [regiao_destino],
    CONVERT(nvarchar(255),[pagador_nome]) AS [pagador_nome],
    CONVERT(nvarchar(50),[pagador_documento]) AS [pagador_documento],
    CONVERT(nvarchar(50),[pagador_documento_key]) COLLATE Latin1_General_CI_AS AS [pagador_documento_key],
    CONVERT(nvarchar(50),[remetente_documento]) AS [remetente_documento],
    CONVERT(nvarchar(50),[destinatario_documento]) AS [destinatario_documento],
    CONVERT(nvarchar(255),[destino]) AS [destino],
    CONVERT(nvarchar(255),[destino_cidade]) AS [destino_cidade],
    CONVERT(nvarchar(10),[destino_uf]) AS [destino_uf],
    CONVERT(nvarchar(50),[status_norm]) AS [status_norm],
    CONVERT(nvarchar(50),[documento_oficial_tipo]) AS [documento_oficial_tipo],
    CONVERT(nvarchar(100),[tipo_frete]) AS [tipo_frete],
    CONVERT(nvarchar(50),[modal]) AS [modal],
    CONVERT(decimal(18,2),[valor_total]) AS [valor_total],
    CONVERT(decimal(18,3),[peso_taxado]) AS [peso_taxado],
    CONVERT(decimal(18,3),[peso_real]) AS [peso_real],
    CONVERT(decimal(18,6),[peso_cubado]) AS [peso_cubado],
    CONVERT(decimal(18,6),[total_m3]) AS [total_m3],
    CONVERT(int,[volumes]) AS [volumes],
    CONVERT(smallint,[performance_diferenca_dias]) AS [performance_diferenca_dias],
    CONVERT(nvarchar(50),[performance_status]) AS [performance_status],
    CONVERT(tinyint,[performance_status_codigo]) AS [performance_status_codigo],
    CONVERT(bit,[is_no_prazo]) AS [is_no_prazo],
    CONVERT(bit,[is_fora_prazo]) AS [is_fora_prazo],
    CONVERT(bit,[is_cubado]) AS [is_cubado],
    CONVERT(bit,[is_peso_real_informado]) AS [is_peso_real_informado],
    CONVERT(bit,[is_cancelado]) AS [is_cancelado],
    CONVERT(bit,[is_cortesia]) AS [is_cortesia],
    CONVERT(bit,[is_documento_emitido]) AS [is_documento_emitido],
    CONVERT(bit,[is_elegivel_operacional]) AS [is_elegivel_operacional],
    CONVERT(bit,[is_elegivel_operacional_com_valor]) AS [is_elegivel_operacional_com_valor],
    CONVERT(bit,[is_pagador_excluido_cubagem]) AS [is_pagador_excluido_cubagem],
    CONVERT(bit,[is_linha_valida_indicador]) AS [is_linha_valida_indicador],
    CONVERT(bit,[excluido_na_origem]) AS [excluido_na_origem],
    CONVERT(datetime2(0),[data_exclusao_origem]) AS [data_exclusao_origem],
    CONVERT(datetime2(0),[ultima_reconciliacao_origem_em]) AS [ultima_reconciliacao_origem_em],
    CONVERT(datetime2(0),[data_extracao]) AS [data_extracao],
    CONVERT(datetime2(0),[snapshot_em]) AS [snapshot_em],
    CONVERT(binary(32),[hash_linha]) AS [hash_linha]
FROM origem;
