# Publicação operacional para o dashboard

As publicações de Raster, Coletores, Manifestos e Saúde do ETL foram ensaiadas em transação reversível, publicadas em `ETL_SISTEMA_V2.pub` e conferidas com a conta atual do portal. Os dados de origem, os registros de execução e os vínculos publicados ficam na V2. O portal em produção continua lendo o V1.

## Raster

O Raster é uma fonte própria. O recebimento privado consulta o fornecedor por dia e guarda apenas a lista de viagens, com as paradas de cada viagem. O SQL conserva a última captura por dia e a versão mais recente de cada viagem. Viagens e paradas usam a mesma versão. Quando o fornecedor repete uma ordem de parada, prevalece a última ocorrência daquela viagem, como no `MERGE` do V1. As ocorrências originais continuam no recebimento.

De 03 a 10/11/2026, foram recebidas 284 viagens e 829 ocorrências de paradas. O painel recebe 803 paradas e 803 linhas de tempo de trânsito. Entre as capturas recentes, 22 viagens e 66 paradas também aparecem no V1. Um ensaio adicional de 22/10/2026 conferiu 53 viagens e 179 paradas presentes no V1. Em 02/11/2026, o fornecedor respondeu com código 0 e sem a lista `Viagens`. Esse dia ficou sem cobertura confirmada. A prova de 22/10 foi revertida.

## Coletores

O fato cruza ordens de Inventário, filiais de Fretes e a composição de Manifestos. Usa a classificação e a normalização de filial do procedimento V1. Executar a lógica original do V1 com as mesmas entradas da V2 produziu os mesmos 84 grupos por data e filial, sem diferenças nas contagens e no percentual de utilização. Foram reconciliadas 762 ordens, 825 manifestos emitidos, 117 descarregamentos e 212 ordens incompletas.

## Manifestos

O detalhe e o fato preservam as 105 e 106 colunas do V1. Os componentes seguem a chave e o critério de atualização do repositório de Manifestos. O fato agrega por número de manifesto. Para compor a receita de Coletas, o número da coleta em Fretes reproduziu os 2.906 vínculos verificáveis entre V1 e V2. O cálculo foi refeito no teste com as ocorrências vigentes. A classificação por nome de proprietário foi usada no cenário em que o V1 e a V2 tinham zero regras cadastradas por documento.

Das 15.006 ocorrências do template 6399, o fato publicou 902 manifestos. Os totais conferidos foram R$ 4.252.314,58 em fretes, R$ 1.130.147,14 em coletas e R$ 5.382.461,72 em receita. Entre 103 números presentes nos dois bancos, 55 tinham o mesmo par de valores de frete e coleta. As capturas dos dois bancos têm períodos e momentos diferentes. A prova dos totais é sobre a entrada vigente da V2, e a comparação histórica continua registrada para o ensaio integrado.

## Saúde do ETL

O recebimento vincula o recibo de execução às páginas arquivadas e aos respectivos registros armazenados. Foram conferidos um ciclo real, 11 registros de extração e 37 páginas com dados, com 5.973 ocorrências. A repetição do recebimento usa o identificador do ciclo e o hash do recibo. O cálculo de ocorrências idênticas compara as duas capturas completas mais próximas da mesma entidade e dia, preservando multiplicidade.

## Execução

O ensaio sem `-Apply` reverte a transação. A aplicação registra os hashes, conserva a última captura válida e valida a leitura do consumidor. Para os dados de Raster, a captura via PowerShell 7.5 usa as configurações locais da origem e grava o recibo e os dados em diretório privado. Para Saúde, informe o recibo do ciclo e a pasta de capturas correspondente. A rotina de 60 minutos atual ainda não chama esses recebimentos auxiliares. Os comandos de instalação e publicação são manuais neste estágio.

```powershell
.\scripts\operations\Capture-DashboardAuxiliarySource.ps1 -Source Raster -RasterDate 2026-11-10 -Receipt C:\privado\raster.json -CaptureDirectory C:\privado\dados
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Raster -CaptureReceiptPath C:\privado\raster.json -CaptureDirectory C:\privado\dados -ReceiptPath C:\privado\raster-sql.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Coletores -ReceiptPath C:\privado\coletores.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Manifestos -ReceiptPath C:\privado\manifestos.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Saude -AuditReportPath C:\privado\reports\<ciclo>.json -AuditCaptureDirectory C:\privado\captures\<ciclo> -ReceiptPath C:\privado\saude.json
```

A consulta de Usuários tem código de instalação preparado. A fonte retornou 11.000 usuários únicos em 550 páginas e indicou que ainda havia páginas. A coleta incompleta não foi promovida. A publicação de Usuários depende do término da paginação e da validação em SQL com a captura completa.
