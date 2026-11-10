# Filtros de data e divergências remanescentes — 10/11/2026

## Comportamento efetivamente executado

O ciclo `ProductionAllReceivingCycle` usa `ReportSnapshotTemplate.query`, que envia somente o intervalo de data de negócio da entidade. Não envia um filtro de última atualização. Em modo ROLLING, consulta novamente hoje e os sete dias anteriores em America/Sao_Paulo, além de até quatro lacunas mais antigas por entidade a partir da âncora configurada. A última extração completa cobriu 02/11/2026 a 09/11/2026, inclusive. A espera de 60 minutos começa após terminar o ciclo; não significa consultar registros alterados na última hora. O supervisor de qualificação permanece desativado.

| Entidade | Campo do filtro |
| --- | --- |
| Coletas | `picks.request_date` |
| Fretes | `freights.service_at` |
| Manifestos | `manifests.service_date` |
| Cotações | `quotes.requested_at` |
| Localização de Cargas | `freights.service_at` |
| Contas a Pagar | `accounting_debits.issue_date` |
| Faturas por Cliente | `freights.service_at` |
| Inventário | `check_in_orders.started_at` |
| Sinistros | `insurance_claims.opening_at_date` |
| Consolidação Detalhada | `consolidations.issue_date` |
| Ocorrências | `invoice_occurrences.occurrence_at` |

Manifestos não devolve `service_date` entre suas 91 colunas, embora esse seja o filtro enviado. Não foi substituído por outra data.

## Limitação de atualização histórica

Um registro com data de negócio anterior à janela pode mudar hoje e ficar fora da próxima consulta. Recuperar datas sem cobertura não revisita automaticamente datas antigas que já possuem cobertura. Portanto, o recebimento completo das onze entidades nessa janela não comprova a captura de todas as alterações de todo o histórico.

Ocorrências oferece `search[invoice_occurrences][updated_at]`; esse filtro permaneceu omitido nas requisições qualificadas e seu uso preenchido ainda não está qualificado. Antes de adotar carga incremental, é necessário verificar limites, fuso, ordenação, atualização de dados relacionados, identificação dos registros, exclusões e recuperação após falha. Não se deve presumir que a atualização da ocorrência acompanhe toda mudança nos dados de frete e tratativa. Os filtros mapeados de Consolidação Detalhada não oferecem última atualização. Nenhuma nova estratégia incremental foi implementada nesta documentação.

## Diagnóstico complementar sem nova extração

As comparações integrais anteriores foram reproduzidas offline, preservando campos e multiplicidades:

- Consolidação: 209 linhas da extração e 212 do Excel; 208 iguais, um par com sete campos diferentes e três ocorrências adicionais no Excel.
- Ocorrências: 36.074 linhas da extração e 36.166 do Excel; 35.953 iguais, 119 pares com campos diferentes, duas ocorrências da extração e 94 do Excel ainda sem pareamento.
- Procurar pares adicionais com todas as demais colunas exatamente iguais, excluindo uma coluna por vez, não resolveu esses dois registros de Ocorrências.
- Das 94 ocorrências remanescentes no Excel, 77 têm data de ocorrência posterior à conclusão local da resposta terminal da extração desse dia. Duas datas de finalização de performance dos pares alterados e uma data de criação de comprovante de Consolidação também são posteriores à resposta da respectiva data.

A conclusão local de uma resposta não é um snapshot transacional do servidor. Essas observações dão suporte à hipótese de evolução da origem, mas não demonstram a causa de todas as diferenças. Mesmo datas de negócio antigas podem ter status e atributos relacionados alterados depois. Comparar o mesmo período em horários diferentes não garante comparar a mesma versão dos dados. A disponibilidade de uma versão comum ou referência temporal suportada pelo ESL ainda precisa ser comprovada. Não eliminar registros, inferir PK, deduplicar ou alterar valores para obter igualdade artificial.

O diagnóstico adicional não altera os resultados de aceite anteriores: a gravação e releitura no SQL estão conferidas; a equivalência integral com o Excel continua pendente. O recebimento completo ocorreu com v5; v6 qualificou o último campo por replay integral dos corpos HTTP das duas novas entidades, sem uma nova carga de rede.

## Publicação do código

Por instrução do responsável, as entregas de código devem terminar com commit e push HTTPS para as contas pessoal e empresarial já configuradas, preservando alterações alheias, verificações e histórico. Arquivos de dados, credenciais e binários privados permanecem fora do Git. Publicar código não ativa o supervisor nem publica os dados no dashboard.
