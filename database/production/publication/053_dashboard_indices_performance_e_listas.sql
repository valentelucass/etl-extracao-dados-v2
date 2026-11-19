CREATE INDEX IX_dashboard_entregas_previsao
ON core.dashboard_detalhe_fretes_leitura([Previsão de Entrega],[Nº Minuta])
INCLUDE([Data de Finalização],[Finalização da Performance],[Data de extracao],[Pagador],
    [Responsável pela Região de Destino],[Responsável Região Destino Key],[Filial Emissora],[Filial],
    [Região Destino],[UF Destino],[Cidade Destino],[Destino],[Kg Taxado],[Valor NF],[Status],
    [Comprovante Anexado],[Performance Diferença de Dias])
WITH (DATA_COMPRESSION=PAGE);

CREATE INDEX IX_dashboard_faturamento_clientes
ON core.dashboard_faturamento_leitura(excluido_na_origem,filial_nome)
INCLUDE(pagador_nome,remetente_nome,destinatario_nome)
WITH (DATA_COMPRESSION=PAGE);

CREATE INDEX IX_dashboard_manifestos_pagina
ON core.dashboard_manifestos_leitura(data_criacao DESC,sequence_code DESC);
CREATE INDEX IX_dashboard_contas_pagina
ON core.dashboard_contas_leitura([Emissão] DESC,[Lançamento a Pagar/N°] DESC);
