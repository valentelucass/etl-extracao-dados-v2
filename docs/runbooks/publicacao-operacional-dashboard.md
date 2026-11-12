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

## Usuários

A implantação inicial migrou os 6.918 usuários ativos do cadastro operacional do V1 para a V2. O ensaio comparou todos os IDs, nomes e datas de atualização. A consulta publicada lê a cópia local da V2. A importação inicial é única e registra a procedência de cada linha.

```powershell
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Usuarios -SeedLegacyUserDirectory -ReceiptPath C:\privado\usuarios-seed.json
```

A leitura direta da fonte ainda precisa chegar ao fim da paginação. A captura anterior retornou 11.000 usuários em 550 páginas e indicou continuação. A capacidade do recebimento agora cobre até 100.000 usuários, mantendo o contrato de 20 por página. O teste isolado gravou e leu 55.000 linhas sintéticas e reverteu a transação. A próxima captura completa da origem pode atualizar a dimensão pelo grupo `Usuarios`, usando o recibo da captura em vez da importação inicial.

## Campos adicionais de Coletas e Fretes

O catálogo do ESL oferece campos que estão fora dos templates 6908 e 6389. Em Coletas, foram localizados Hora solicitação, ID da filial, Logradouro, Número e Complemento na associação Endereço de coleta, Documento na associação Cliente e ID na associação Usuário do cancelamento. Em Fretes, foram localizados IDs dos envolvidos e da filial, ID CT-e e Data criação na associação CT-e, além de base de cálculo, alíquotas e valores de PIS e COFINS na associação Fiscal.

O ESL bloqueou a criação de templates complementares no limite contratado de 30 relatórios. A alternativa é evoluir os templates existentes. O catálogo do Java e o catálogo do recebimento SQL exigem o conjunto exato de colunas. Durante a alteração dos templates, compare o que o Data Export anuncia com as colunas presentes em cada linha antes de versionar os novos catálogos. O editor do ESL grava cada coluna no momento em que ela é adicionada. Em Coletas, a conferência detectou as 31 colunas originais em todas as páginas e conjuntos adicionais diferentes entre o catálogo e os dados.

```powershell
.\scripts\validation\Test-DashboardSourceColumnAdditions.ps1 -ProbeReceipt @($sondaColetas, $sondaFretes)
```

O comando usa os recibos sanitizados de `scripts/probes/Invoke-DataExportContractProbe.ps1`. Ele verifica se as colunas originais estão presentes, se as páginas retornam os mesmos campos do catálogo e se cada campo está presente em todas as linhas amostradas. Para repetir a conferência de um mesmo relatório, passe os dois recibos no mesmo comando. A sonda também registra duração e bytes por requisição. Compare os dados de uma janela fechada com o Excel do ESL antes de publicar as consultas.

O Data Export confirmou 40 colunas de Coletas e 150 de Fretes. O Java e o recebimento SQL aceitam os conjuntos originais e os conjuntos expandidos completos. A lista adicional tem nove campos de Coletas e 36 de Fretes. Um campo desconhecido, uma revisão incompleta ou a mistura de revisões na mesma captura causa rejeição. Os dados originais e as chaves das capturas são preservados.

```powershell
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group CamposColetasFretes -ReceiptPath C:\privado\campos-ensaio.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group CamposColetasFretes -Apply -ReceiptPath C:\privado\campos-aplicacao.json
```

O segundo comando aplica o recebimento das novas colunas em `ETL_SISTEMA_V2`. O ciclo que lê os relatórios expandidos precisa do artefato Java com o catálogo correspondente. O template de Fretes atingiu o limite de 150 colunas. O modal do frete permanece disponível como campo próprio.

Em 06/11/2026, o extrator Java percorreu um dia inteiro de Fretes com 25 IDs por página. A extração chegou à página vazia após 177 segundos e recebeu 1.147 linhas, 972 IDs distintos e zero recusas. As 41 requisições, incluindo metadados, somaram 5.615.774 bytes de resposta. Entre os 691 IDs também encontrados no V1, 688 tinham o mesmo ID de CT-e e 690 tinham o mesmo valor de ICMS. Os três CT-es divergentes foram emitidos após a captura do V1. A divergência de ICMS estava em um desses fretes. O vínculo de destinatário do V1 usa o cadastro de recebedor, confirmado pelo mapeador Java legado e por 690 dos 691 IDs sobrepostos.

Uma coleta excluída aparece na lista com o status **Excluída** e fica fora dos totais e da série diária. O menu **Operacional → Log de Exclusões → Coletas** foi consultado e mostrou número, data, usuário e motivo. A forma de receber esse registro pelo ciclo de extração segue pendente. A data geral de atualização da Coleta tem significado próprio. No recorte comparado, ela não coincidiu com nenhum dos 916 horários de mudança de status do V1.

Quando houver reemissão, as consultas operacionais e de faturamento devem usar o CT-e atual da minuta. Na amostra conferida, o relatório principal trouxe duas linhas do mesmo frete e ambas indicaram o CT-e atual. A publicação precisa contar o frete uma vez. O relatório de Faturamento CT-e confirmou separadamente o cancelamento do documento anterior e a emissão do atual.

## Gestão à Vista de Fretes

O fato de entrega e cubagem usa os Fretes vigentes, a finalização disponível em Faturas por Cliente e os dados de destino de Localização de Cargas. A consulta escolhe um registro vigente por ID de frete e um frete por minuta. As regras de filiais operacionais e de exceção de pagadores são importadas do procedimento do V1 na primeira instalação. Depois da instalação, a leitura usa as regras copiadas para a V2. Os documentos dessas regras ficam fora do repositório.

```powershell
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group GestaoFretes -ReceiptPath C:\privado\gestao-fretes-ensaio.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group GestaoFretes -Apply -ReceiptPath C:\privado\gestao-fretes-aplicacao.json
```

A validação conferiu as 54 colunas do contrato do V1, a leitura com a conta do dashboard e a chave única por indicador e minuta. A consulta produziu 4.298 linhas de entrega e 4.298 de cubagem, com 4.215 e 3.115 linhas válidas, respectivamente. Uma segunda aplicação confirmou os mesmos objetos e regras.

As consultas de entrega e cubagem com filtro de período foram verificadas separadamente. A leitura das chaves do JSON como valores escalares reduziu a estimativa de linhas dos joins. O teste comparou todas as colunas e linhas da versão anterior com a versão otimizada, sem diferenças. A consulta de cubagem também aplicou a tabela de exceções usada pelo dashboard.

## Dependências para completar a troca

| Consulta | Evidência e dependência |
| --- | --- |
| `vw_coletas_powerbi` | O Log de Exclusões é visível no ESL. O catálogo atual de Data Export não encontrou template com a raiz **Coletas - Excluídas**. Para alimentar a lista automaticamente, é necessário disponibilizar essa origem para o ETL. O catálogo de Coletas oferece a data geral de atualização. O horário específico da mudança de status ainda precisa de uma fonte. |
| `vw_fretes_powerbi` e `fato_fretes_faturamento` | A soma dos 17 componentes presentes no relatório reproduziu o **Valor Frete** em todos os 3.260 registros comparáveis dos dois bancos. Data de criação do frete, CNPJ da filial emissora e peso das notas tinham valor nos 76.351 fretes ativos do V1. Falta qualificar a origem desses campos no Data Export. O template 6389 está com 150 colunas. |

Com os campos disponíveis, confira novamente a reemissão, a exclusão, as regras de elegibilidade e os totais em um período fechado. O ensaio de troca depende das 26 consultas e da cobertura do período que o dashboard vai exibir.
