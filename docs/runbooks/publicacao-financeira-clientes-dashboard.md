# Faturas, Contas a Pagar e Clientes no dashboard

As quatro consultas publicadas na V2 seguem a interface usada pelo portal. O ensaio conferiu as colunas com o V1, leu os registros com a conta do portal e reconciliou os resultados com os relatórios vigentes. A leitura entre bancos encontrou chaves em comum em capturas feitas em momentos diferentes.

## Faturas por Cliente

As duas consultas de Faturas compartilham a mesma seleção de documentos do template 4924. A chave segue a precedência do mapeador V1 por NFS-e, número de CT-e, documento de fatura, identificador de cobrança e combinação de envolvidos, notas, pedidos e valores. A seleção dá preferência às datas de negócio mais recentes. Em empate, usa a data de armazenamento e a ordem preservada no relatório.

- O detalhe tem 42 colunas, incluindo os valores e o JSON original da linha selecionada.
- O fato tem 54 colunas e usa os mesmos documentos. O valor operacional segue a precedência valor financeiro, valor da fatura e valor do frete. A competência usa a emissão da fatura ou do CT-e. O status de atraso é calculado na data do lote para que a mesma captura produza o mesmo resultado.
- O recorte de 29/10 a 10/11/2026 tem 6.984 ocorrências e 6.372 documentos publicados. Foram reconciliados R$ 3.146.616,65 em frete, R$ 21.387.451,80 em faturas e R$ 22.624.232,38 em valor operacional. São 3.928 documentos faturados, 6.089 com número de CT-e e 71 com número de NFS-e. A interseção com o V1 é de 2.087 chaves.

## Contas a Pagar

A consulta de 31 colunas publica um lançamento por `ant_ils_sequence_code`, a chave usada no V1. A escolha considera criação, liquidação e transação. Para registros com a mesma data de atualização, usa a data de armazenamento e a ordem preservada no relatório. A conciliação tem três estados, conforme a origem.

Entre 02 e 10/11/2026, 831 ocorrências produziram 793 lançamentos. A prova conferiu os valores e as datas de cada lançamento selecionado, com R$ 1.593.582,35 a pagar, R$ 594.751,15 pagos e 511 lançamentos com status pago. O relatório deste recorte tem 0 lançamentos marcados como conciliados. O total publicado conserva o grão de lançamento do V1. As ocorrências de rateio continuam disponíveis no recebimento.

## Clientes

A consulta preserva a coluna `Nome` com a normalização de espaços, maiúsculas e unicidade do V1. As fontes são os nomes de cliente em Coletas e os nomes de pagador, remetente e destinatário em Fretes e Faturas por Cliente. Para Coletas e Fretes, usa a última ocorrência armazenada de cada ID. Na amostra real, as repetições por ID tinham os mesmos nomes.

A prova reproduziu os 5.347 nomes distintos a partir dos três conjuntos de origem. Desses, 3.773 também aparecem na dimensão V1.

## Ensaio e aplicação

```powershell
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Faturas -ReceiptPath C:\caminho\faturas.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group ContasAPagar -ReceiptPath C:\caminho\contas.json
.\scripts\operations\Install-DashboardCompatibilityViews.ps1 -Group Clientes -ReceiptPath C:\caminho\clientes.json
```

Cada comando sem `-Apply` usa um novo recibo e reverte a transação. Com `-Apply`, valida e publica o grupo com o hash registrado na V2. Faturas deve estar publicada antes de Clientes. Os 26 nomes em `DASHBOARDS` seguem no V1. A troca depende dos outros 13 contratos e do ensaio integrado.
