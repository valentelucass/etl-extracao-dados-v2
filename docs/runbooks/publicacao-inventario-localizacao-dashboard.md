# Inventário e Localização de Cargas para o dashboard

As duas publicações foram instaladas na V2 e lidas com a conta atual do portal. O teste JDBC comparou nomes, tipos, tamanhos e escalas com as consultas do V1. Os 26 nomes do portal continuam ligados ao V1.

## Inventário

A consulta de 34 colunas usa o último lote completo por dia. A chave conserva ordem, minuta, notas e início da conferência conforme o mapeador V1. A escolha da linha segue a data de atualização usada no repositório V1: finalização da performance, fim da conferência ou início. Quando a data é igual, a publicação usa a data do lote e a ordem preservada no relatório para desempatar. O indicador de comprovante conserva um positivo observado para a mesma chave em capturas completas. A filial emissora vem dos Fretes vigentes quando há uma única filial para a minuta. Nos demais casos, a consulta usa a filial da ordem de conferência.

O recorte da V2 tem 15.446 ocorrências e 14.600 chaves. O teste reproduziu a escolha dos 14.600 registros por consulta independente e confirmou que as 846 ocorrências repetidas não foram somadas. O valor de NF foi de R$ 206.431.801,55, com 220.380 volumes, 67.814 volumes lidos e 7.726 chaves com comprovante. A leitura histórica encontrou 1.201 chaves presentes nos dois bancos. Origem, destino e conferente bateram em 716 delas. Os lotes dos dois bancos foram capturados separadamente, e a prova de escolha e totais usa os dados vigentes da V2.

## Localização de Cargas

A consulta de 29 colunas lê o template 8656, conserva os 4.314 registros e usa os oito aliases de região conferidos no V1. Foram reconciliados R$ 2.122.505,04 de frete, R$ 80.718.597,70 em notas e 171.764 volumes. A coluna `Filial Atual` recebe o valor ausente da origem, que também foi observado nas 73.058 linhas ativas do V1. O dashboard tem regras para resolver a filial pelo nome de localização e pela filial emissora. O campo técnico `Hash Localização` na V2 é calculado sobre o registro do template publicado e tem valor próprio da V2.

## Execução

```powershell
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Inventario -ReceiptPath C:\caminho\inventario.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Localizacao -ReceiptPath C:\caminho\localizacao.json
```

Cada ensaio usa um recibo novo e reverte a transação. Com `-Apply`, o mesmo instalador valida e registra o hash da view na V2. Para Inventário, o teste divide a leitura e a reconciliação em comandos separados dentro da mesma transação. O vínculo do portal só pode ser alterado depois da validação dos demais contratos.
