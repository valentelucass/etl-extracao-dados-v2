-- A ordem da página permite ler as linhas pedidas antes de buscar o detalhe completo.
CREATE INDEX IX_dashboard_faturamento_pagina
ON core.dashboard_faturamento_leitura(excluido_na_origem,is_elegivel_faturamento,
    data_referencia_faturamento DESC,numero_minuta DESC,frete_id DESC);

CREATE INDEX IX_dashboard_faturamento_resumo
ON core.dashboard_faturamento_leitura(data_referencia_faturamento_date)
INCLUDE(excluido_na_origem,is_elegivel_faturamento,data_referencia_faturamento,
    receita_bruta,valor_frete,volumes,peso_taxado,filial_nome,responsavel_regiao_destino,
    responsavel_regiao_destino_key,pagador_nome,pagador_documento,pagador_documento_key,
    origem_uf,destino_uf,destino_cidade,status_frete,status_frete_norm,tipo_frete,modal,
    classificacao_nome,cte_id,nfse_number,snapshot_em)
WITH (DATA_COMPRESSION=PAGE);

CREATE INDEX IX_dashboard_gestao_indicador_data
ON core.dashboard_gestao_fretes_leitura(indicador_codigo,data_referencia)
INCLUDE(is_linha_valida_indicador,excluido_na_origem,filial_performance_key,filial_emissora_key,
    data_frete,numero_minuta,filial_performance,filial_emissora,responsavel_regiao_destino,
    regiao_destino,destino,destino_cidade,data_finalizacao_performance,performance_diferenca_dias,
    performance_status,is_no_prazo,is_fora_prazo,data_extracao,is_pagador_excluido_cubagem,
    pagador_nome,pagador_documento,pagador_documento_key,peso_taxado,peso_real,peso_cubado,
    total_m3,is_cubado,is_peso_real_informado)
WITH (DATA_COMPRESSION=PAGE);
