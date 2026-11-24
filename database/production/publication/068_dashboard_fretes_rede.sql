CREATE OR ALTER VIEW pub.dashboard_fretes_rede AS
SELECT [ID],[Nº Minuta],[Pagador],[NF],[UF Destino],[Data frete],[CT-e Emissão],[Tipo Frete],
       [Previsão de Entrega],[Data de Finalização],[Finalização da Performance],[Performance Diferença de Dias],
       [Responsável pela Região de Destino],[Responsável Região Destino Key],[Filial Emissora],[Filial],
       [Região Destino],[Cidade Destino],[Destino],[Kg Taxado],[Valor NF],[Comprovante Anexado],[Status],
       [Data de extracao],manifesto_entrega_consulta
FROM core.dashboard_detalhe_fretes_leitura;
