# Documentação ESL de referência

O owner indicou [TMS ESL CLOUD no Postman](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5)
como documentação principal da ESL. Usar esta fonte antes de tratar um endpoint
como desconhecido. O [índice](endpoints.csv) registra as 191 requisições publicadas
na coleção recebida (39 GET, 146 POST, um PATCH e cinco DELETE), com links por seção.
São entradas de documentação, não 191 APIs distintas nem as 191 rotas do roadmap.
IDs de exemplos nas URLs foram substituídos por `{id}`; nenhum request foi executado.

A própria coleção aponta à [documentação GraphQL](https://demonstracao.eslcloud.com.br/graphql_docs).
O [índice complementar](graphql-operacoes.csv) contém seus 180 anchors de operações.
[fonte.json](fonte.json) registra integridade, limites e lacunas. `publishDate` da
coleção é metadado de publicação; não data a revisão de todos os contratos.

## Evidência pertinente às pendências

| Frente | Documento localizado | Limite da conclusão |
| --- | --- | --- |
| 8636 / P09 | GET `/api/accounting/debit/billings`, `/installments` e itens por fatura | Distingue fatura, parcela e item; não declara o crosswalk com o export 8636 nem resolve rateio/grão. |
| 4924 / P10 | Rotas equivalentes sob `/api/accounting/credit`; query `creditCustomerBilling` | Origem documental para investigar título/itens. Não prova que o ID da linha 4924 seja o título ou seu vínculo com Frete. |
| 10633 / P08 | Tipo GraphQL `CheckInOrder` | Não relaciona a sequência do template aos IDs de raiz/Frete/documentos. |
| 6392 / P11 | [Listagem de ocorrências](https://documenter.getpostman.com/view/20571375/2s9YXk2fj5#1843cd33-1848-438c-875b-6262107c5e94), com exemplos de campos invoice/Freight/occurrence | Ocorrência não equivale automaticamente à raiz Sinistro. Nenhum vínculo com 6392 foi comprovado. |
| 6906 / Q-COT-01 | Listagem de templates, estrutura `/info`, consulta `/data` | Documentação geral complementa o contrato; caracterização representativa da origem ainda não executada. |

Os exemplos de faturas possuem IDs de parcelas; itens de fatura a pagar incluem
`freight_id`. Isso orienta a comparação futura, sem provar estabilidade, identidade
de rateio ou cardinalidade dos templates personalizados. Nenhum valor de exemplo
foi promovido a dado observado da conta do usuário. FAT-02 continua pendente.

## Compatibilidade a conferir

A seção Data Export declara intervalo de dois segundos por IP e restrições à
reextração histórica: uma hora para períodos entre 31 dias e seis meses; 12 horas
acima de seis meses. Seus exemplos incluem GET com body. O projeto mantém o
`GET_WITH_QUERY` já observado; investigar divergências antes de mudar transporte.
A query financeira documenta paginação GraphQL de até 20 registros. Não aplicar
esse limite automaticamente a todos os contratos. Tokens de usuário, cliente e
Data Export aparecem em seções distintas; não presumir intercâmbio de credenciais.

## Continuidade e recuperação

P08–P11 receberam referência nova, mas não prova integral de identidade. Consultar
primeiro os links exatos do índice e então o [pacote de lacunas ESL](../bloco56-continuacao/README.md).
Preservar as observações remotas anteriores. Nenhuma mutation, exportação FTP,
consulta de dados, alteração de template, SQL, grant ou nova campanha decorre desta
leitura. Raster pertence a outro fornecedor e conserva seu contrato próprio.

O registro não fecha checkbox: 67/115, 48 pendentes. Java e migrations não mudaram.
O manifesto e `Test-EslDocumentation.ps1` verificam somente registro/sucessão e
preservação, nunca a autenticidade de um exemplo ou um aceite da vertical.
Revisões anteriores em `docs/continuidade/historico/bloco56-raster/`;
inventário, documentos públicos originais e diff próprio em
`target/esl-documentacao-postman/`. Os originais públicos ficam fora do Git.
Para recuperar, comparar hashes e restaurar somente deltas próprios; não apagar
evidências ou reescrever manifests/ledgers históricos. Não há rollback de banco.
