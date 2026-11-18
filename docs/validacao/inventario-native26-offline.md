# Inventário 10633: captura finita e conferência Native26

O fechamento 0749 comprovou **3/3 semanas**, com **36.618 linhas**, 26 colunas e seis totais iguais entre Source e Native, em Java e referência Python independente. A cobertura reúne nove partes disjuntas de 21 dias, com tuplas completas, associações por `sequence_code`, multiplicidades e eixos originais verificados. A aprovação é da conferência histórica vinculada aos artefatos finais; não concede adoção operacional.

Os componentes administrativos ficam em `plataforma/reconciliacao/exportacao`. O perfil `INVENTARIO_10633_NATIVE26_V1` compara o relatório ESL 10633 com 26 colunas. O Native independente é exportado pela interface ESL com o mesmo intervalo e os mesmos filtros do pedido Source, preservando o arquivo original. No caso 19–20/10, foram observados 2.000 na lista da interface e 5.158 linhas no arquivo efetivamente lido; a causa dessa diferença de contagem não foi demonstrada. A conferência usa o arquivo lido até EOF.

## Pedido e contrato da API

`InventarioIndependentRequest` exige exatamente `schema`, `profile`, `entity`, `reportId`, `attemptId`, `periodStart`, `periodEnd`, `zone` e `filters`. Os valores fixos são:

| Campo | Valor |
| --- | --- |
| `schema` | `INVENTARIO_INDEPENDENT_REQUEST_V1` |
| `profile` | `INVENTARIO_10633_NATIVE26_V1` |
| `entity` | `INVENTARIO` |
| `reportId` | inteiro `10633` |
| `attemptId` | UUID da tentativa |
| `zone` | `America/Sao_Paulo` |
| `periodStart` / `periodEnd` | datas ISO civis, início e fim inclusivos, de 1 a 7 dias |

Todos os sete filtros devem estar presentes como strings. `check_in_orders[started_at]` vincula o período, com forma canônica `YYYY-MM-DD - YYYY-MM-DD`. Os outros seis permanecem vazios: `check_in_orders[check_in_type]`, `check_in_orders[corporation_id]`, `check_in_orders[sequence_code]`, `check_in_orders[updated_at]`, `scopes[from_invoice_number]` e `scopes[from_volume_code]`. Filtros auxiliares preenchidos exigem qualificação própria e são recusados neste perfil.

O transporte admitido é `GET_WITH_QUERY`: `/api/analytics/reports/10633/info`, seguido de `/api/analytics/reports/10633/data`. O plano mantém os controles vazios explícitos; a consulta serializa o filtro principal não vazio, `page` e `per`, sem `order_by`. Página admitida: 1 a 16; `per`: 1 a 100. Gerar uma URI ou um plano não autoriza uma chamada à fonte.

O contrato histórico de wire é `contracts/dataexport/expansion-10633-observed-wire-20261025-v1.json`, SHA-256 `03e0e446ea19bb2d70b33056d05a36fef96981f710dfa216e1a8e41e1f3e5794`. Ele permanece separado do catálogo de apresentação Native. Em particular, Nº Minuta admite `INTEGER`, `NUMBER` e `NULL`; não deve ser convertido em string ou sujeito ao requisito de inteiro positivo da chave N° Ordem.

## Captura e limites

`InventarioSourceCaptureMain` recebe `plan --input MANIFESTO --sha256 SHA` ou `capture --input MANIFESTO --sha256 SHA`. O plano usa `INVENTARIO_SOURCE_PLAN_INPUT_V1`, com `schema`, `requestPin` e `sourceSettings`, e retorna `INVENTARIO_FINITE_SOURCE_PLAN_V1`.

A captura real exige `ROOT_INVENTARIO_SOURCE_EXECUTION_INPUT_V1`, com `schema`, `requestPin`, `sourceSettings`, `physicalContextPin`, `codeUnitPin`, `outputDirectory`, `journalDirectory` e `resultPath`. O contexto físico contém `inventarioGrantPin`; o grant `ROOT_INVENTARIO_FINITE_SOURCE_GRANT_V1` vincula o pedido, o intervalo, a unidade testada, a configuração e a admissão Root. O plano não substitui esse grant nem as guardas de processo, privacidade, journal e encerramento.

| Limite | Valor máximo |
| --- | --- |
| Source por execução | 210 segundos |
| HTTP por Source | 17: 1 INFO e até 16 DATA |
| `per` | 100 |
| Linhas físicas retidas por parte | 12.000 |
| Corpo de resposta e payload retido | 8 MiB, cada limite |
| Retry / redirecionamento | 0 / 0 |
| Original físico | 1 GiB / 300 segundos |

A captura termina apenas ao observar uma página DATA `[]` real. Página curta não é terminal. Estouro de prazo, bytes, linhas ou páginas deixa resultado parcial; a captura não pode ser declarada completa por aproximação. O journal distingue admissão, conclusão e resultado HTTP desconhecido.

O payload completo é JSONL UTF-8, com LF final e envelope `{"entity":"INVENTARIO","templateId":10633,"record":{...}}`. O coletor retém todas as ocorrências e preserva ordem, lexemas numéricos, escapes e conteúdo das strings; compacta apenas espaços JSON externos às strings. Arquivos parciais e completos têm nomes distintos. Nenhuma qualificação posterior reescreve o RAW.

Captura completa, tipos de wire qualificados e mapper qualificado são fatos distintos. Um registro recusado pelo mapper continua no RAW, e `mapperQualified=false` permanece no recibo histórico. `sequence_code` é uma chave de agrupamento positiva e integral para os diagnósticos; isso não prova identidade global ou snapshot simultâneo.

Para as três semanas do caso 0749, cada semana foi dividida em partes disjuntas de 2 + 2 + 3 dias para manter os limites. O join final comprovou cobertura exata das três partes por semana, Native correspondente e ausência de sobreposição de chaves entre partes. A união foi lógica, sem concatenação física dos RAWs, ordenação, deduplicação ou reserialização.

| Semana inclusiva de 2026 | Linhas iguais | Chaves distintas | Partes | Resultado |
| --- | ---: | ---: | ---: | --- |
| 12–18/10 | 10.546 | 495 | 3 | Igual |
| 19–25/10 | 13.577 | 632 | 3 | Igual |
| 26/10–01/11 | 12.495 | 588 | 3 | Igual |

As chaves distintas são contagens por semana; não estabelecem identidade global entre períodos. O consolidado verificou os nove pares em todas as 26 colunas, seis totais, tuplas, associações e multiplicidades.

## Catálogo Native26 explícito

`InventarioContentAdapter.compare(request, sourcePin, workbookPin, catalogPin, budget)` exige o catálogo como pin explícito. O recurso candidato `contracts/dataexport/inventario-native26-v1.json` usa o schema `INVENTARIO_NATIVE26_CANDIDATE_CATALOG_V1`, natureza `OBSERVED_NATIVE_CANDIDATE`, 26 colunas, `idColumnIndex=8` e seis colunas de total. Ele conserva `presentationQualified=false` e `contentAccepted=false`; o recurso sozinho não qualifica suas políticas.

| Coluna | Cabeçalho Native | Campo Source |
| --- | --- | --- |
| 1 | Nº Minuta | `cnr_c_s_fit_corporation_sequence_number` |
| 2 | Pagador | `cnr_c_s_fit_pyr_nickname` |
| 3 | Remetente | `cnr_c_s_fit_sdr_nickname` |
| 4 | Origem | `cnr_c_s_fit_sdr_ads_cty_name` |
| 5 | Destinatário | `cnr_c_s_fit_rpt_nickname` |
| 6 | Destino | `cnr_c_s_fit_rpt_ads_cty_name` |
| 7 | Região Entrega | `cnr_c_s_fit_dyn_name` |
| 8 | Filial Entregadora | `cnr_c_s_fit_dyn_drt_nickname` |
| 9 | N° Ordem | `sequence_code` |
| 10 | Filial | `cnr_crn_psn_nickname` |
| 11 | Tipo | `type` |
| 12 | Data/Hora início | `started_at` |
| 13 | Data/Hora fim | `finished_at` |
| 14 | Status | `status` |
| 15 | Conferente | `cnr_cis_eoe_psn_name` |
| 16 | Notas Fiscais | `cnr_c_s_fit_invoices_mapping` |
| 17 | Valor de NF | `cnr_c_s_fit_invoices_value` |
| 18 | Peso Real | `cnr_c_s_fit_real_weight` |
| 19 | M³ | `cnr_c_s_fit_total_cubic_volume` |
| 20 | Peso Taxado | `cnr_c_s_fit_taxed_weight` |
| 21 | Volumes | `cnr_c_s_fit_invoices_volumes` |
| 22 | Volumes Lidos | `cnr_c_s_read_volumes` |
| 23 | Previsão de Entrega | `cnr_c_s_fit_dpn_delivery_prediction_at` |
| 24 | Finalização da Performance | `cnr_c_s_fit_dpn_performance_finished_at` |
| 25 | Data Última Ocorrência | `cnr_c_s_fit_fte_lce_occurrence_at` |
| 26 | Ocorrência | `cnr_c_s_fit_fte_lce_ore_description` |

Os vínculos de 7 e 8 foram revisados com as demais 24 colunas e sustentados pela comparação integral dos nove pares. O catálogo pinado foi mantido byte a byte, inclusive seus marcadores históricos de candidato. A revisão Root externa `ROOT_INVENTARIO_NATIVE26_OBSERVED_CATALOG_REVIEW_V1` registra a qualificação das políticas observadas; a construção do catálogo e seus marcadores internos não substituem essa revisão nem o join.

O leitor verifica coordenadas e cabeçalhos, ZIP/CRC e XML até EOF, epoch 1900, uma planilha e ausência de fórmulas conforme os artefatos observados. As colunas 1, 9 e 17–22 são numéricas com estilo 0. As datas 12, 13 e 23–25 usam o carrier exato `XLSX:1900:DD/MM/YYYY HH:MM:SS:apply=1:base=0:fmtId=164`. O carrier numérico declarado é `XLSX:1900:builtin:0:apply=null:base=0:fmtId=0`. Mudança de carrier exige nova investigação.

Nas sete colunas numéricas de apresentação, 1 e 17–22, a regra é `DIRECT_BINARY64_NEAREST_EVEN_V1`: cada lexema é codificado em binary64 nearest-even e comparado pelo valor dyadic exato. Não há epsilon, arredondamento a duas casas ou política G16 herdada de outra entidade. Os seis totais são calculados sobre 17–22 por soma inteira exata das unidades binary64 de cada célula; não são totais lidos de um rodapé Native. A coluna 1 não é somada.

Datas e horários são comparados pela chave civil apresentada em segundos inteiros. A codificação serial original e a contagem de codificações não canônicas permanecem no eixo original, sem tolerância numérica. O escopo do pedido é verificado em `started_at` para cada linha Source e Native.

As políticas de nulidade são locais ao campo. No catálogo candidato, Source `NULL` corresponde a Native `BLANK` nas colunas 13 e 23–25; corresponde a `ABSENT` em 1–8, 15–22 e 26. Demais nulidades ficam estritas. String vazia permanece estrita em todas as colunas. Zero, `NULL`, `ABSENT`, `BLANK` e string vazia não são uma única categoria.

O campo 16 apresenta a sequência de notas fiscais com separador explícito `", "`; preserva ordem e repetições no eixo original. Essa apresentação pode não ser injetiva, portanto a igualdade da string Native não substitui a prova do array Source. Os rótulos são vinculados por campo: `finished` → Finalizada e `pending` → Pendente; `CheckIn::Order::Loading` → Carregamento, `CheckIn::Order::Unloading` → Descarregamento, `CheckIn::Order::Receipt` → Recebimento, `CheckIn::Order::Picking` → Picking e `CheckIn::Order::Return` → Retorno. Um código não observado é recusado.

## Diagnóstico offline de conteúdo

`InventarioDiagnosticMain` recebe `compare --input MANIFESTO_ABSOLUTO --sha256 SHA --output PRIVADO_NOVO`. O manifesto `INVENTARIO_OFFLINE_DIAGNOSTIC_INPUT_V1` contém exatamente `schema`, `privateRoot`, `requestPin`, `sourcePin`, `workbookPin` e `catalogPin`. Cada pin contém exatamente `path`, `bytes` e `sha256`; o caminho é absoluto e normalizado, e o SHA-256 usa hexadecimal minúsculo. A saída e seu recibo irmão precisam ser novos, dentro da raiz privada, sem colisão com entradas.

O diagnóstico privado `INVENTARIO_CONTENT_DIAGNOSTIC_V1` compara todas as ocorrências: 26 bags de coluna, tuplas completas, associações e multiplicidades por N° Ordem, chaves exclusivas de cada lado e seis totais exatos. As projeções `fullValueProjection` e `originalValueProjection` guardam provas canônicas e digests; `completeAllValues=true` significa que todos os valores participaram, sem exigir sua reprodução textual no arquivo. As duas fontes originais permanecem retidas e pinadas.

A saída padrão leva apenas o recibo limitado `INVENTARIO_OFFLINE_DIAGNOSTIC_EXECUTED_RECEIPT_V1`. Código 0 indica igualdade sob a apresentação candidata, 3 indica diferenças e 2 indica recusa de admissão ou erro. Igualdade vazia não é aprovada automaticamente pelo adapter atual. O orçamento compartilhado é de 150 segundos, 1.200.000 visitas lógicas a células, 8 MiB por entrada e 12.000 linhas por lado; o limite por linha JSONL é 262.144 caracteres. Uma recusa anterior por orçamento continua possível.

Mesmo com saída 0, o diagnóstico mantém `contentAccepted=false`, `entityQualified=false`, `sourceCompletenessProven=false` e `snapshotIdentityProven=false`. A aprovação externa exige juntar as capturas, seus encerramentos, o Consumer exato, o Native independente, o catálogo e a referência independente.

## Requalificação Source0 com o mapper corrigido

`InventarioMapperQualificationMain` recebe `qualify --input MANIFESTO_ABSOLUTO --sha256 SHA --output PRIVADO_NOVO`. O schema de entrada é `ROOT_INVENTARIO_RAW_REQUALIFICATION_INPUT_V1`, com exatamente `schema`, `privateRoot`, `consumerTestPin`, `codeUnitPin`, `catalogPin` e `captures`. Cada uma das 1 a 3 entradas de `captures` contém exatamente `producerSummaryPin`, `requestPin` e `sourcePin`. O consumidor só é admitido com recibo de testes encerrado e restaurado, vinculado à mesma unidade de código.

Para cada captura, o qualificador verifica os pins e o recibo do Producer, as respostas INFO/DATA contíguas, HTTP 200, contagem de ocorrências e o `[]` terminal real. Relê integralmente o mesmo RAW UTF-8 até EOF e, para cada ocorrência, reconstrói o envelope tipado para chamar o `InventarioDataExportMapper` real. Revalida wire, escopo e mapper, sem deduplicar ou reserializar os arquivos.

A saída privada `INVENTARIO_SOURCE_REQUALIFICATION_V1` contém entradas `INVENTARIO_RAW_REQUALIFICATION_ENTRY_V1`, contadores de linhas e recusas, pins anteriores e posteriores e as qualificações atuais. `producerMapperQualified` copia o fato histórico; `historicalProducerFactRewritten=false` impede substituir o histórico do Producer por um resultado Consumer posterior. O catálogo é contexto pinado, sem usar a apresentação Native para aprovar o mapper.

O recibo limitado tem schema `INVENTARIO_OFFLINE_REQUALIFICATION_EXECUTED_RECEIPT_V1`. Código 0 significa requalificação sem recusas, 3 significa linhas recusadas e 2 significa falha de admissão ou execução. O encerramento físico é demonstrado pelo wrapper Root, não fabricado pela CLI Java. Requalificar o RAW não prova, isoladamente, igualdade Native ou cobertura das três semanas.

## Uso protegido e fechamento 0749

As duas CLIs offline operam com `Source=0`, `HTTP=0` e `SQL=0`. Executar somente pelo Original protegido e com os limites vigentes; não ler, hashear ou divulgar RAW, XLSX, JAR, `.class` ou projeções privadas fora dele. Metadados públicos devem conter contagens, códigos de resultado e pins admitidos, sem IDs, valores financeiros ou datas de registros empresariais.

O Consumer final congelado é a unidade `FDC994204148993A18DDE104E4270C0DBE953F2076ECEC6DFB54F8FB15AAF3FA`, vinculada ao TEST05. Foram executados 662 testes em 58 classes, sem falhas, erros ou skips, com Original encerrado e estado restaurado. Os nove RAWs históricos ProducerTEST01 foram requalificados por esse Consumer em Source0/HTTP0/SQL0: 36.618 chamadas ao mapper real, zero recusas, EOF e pins verificados antes e depois. A parte originalmente recusada por conter 33 notas mantém `producerMapperQualified=false`; a aprovação Consumer é um novo fato, não uma reescrita do Producer.

O consolidado final combina os primeiros sete pares com duas novas capturas Source e dois Native independentes para 28–29/10 e 30/10–01/11. Esses dois Source também usam a unidade TEST05. As exportações Native 1772629 e 1772632, geradas pela interface às 12:45 de 07/11/2026, coincidiram integralmente com os Source correspondentes. Os quatro IDs divergentes anteriores, três na penúltima parte e um na última, permanecem no histórico com diferenças nas colunas 25/26. A causa dessas diferenças não foi estabelecida. Nenhum campo foi ignorado, normalizado ou remapeado para fechar a comparação.

Os pins públicos centrais, no workspace de execução `C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade`, são:

| Evidência | Referência | SHA-256 |
| --- | --- | --- |
| Join final fechado/restaurado | `work/i749-join-final09/result.json` | `544f30d9a4575e5c9eba1e3688296a7a52d81d2e3c5da36fb9e1649bd808a9c9` |
| Consumer TEST05 fechado/restaurado | `work/independent-export-inventario0749-test05/root-executed-binding.json` | `550463346e2c927ff6d1ba9fbce8d7f320026b52c3e8ac9b6a67c9ff03a7ed4a` |
| Unidade Consumer congelada | `outputs/independent-export-formatted-inventario0749-compile04/offline-unit.PREPARED.json` | `fdc994204148993a18dde104e4270c0dbe953f2076ecec6dfb54f8fb15aaf3fa` |
| Catálogo Native26 | recurso `contracts/dataexport/inventario-native26-v1.json` | `f5b680fdcc99f2674de0f4d652c7832f7e7b52340b996c03b446fcd61549cfa9` |
| Revisão Root das políticas observadas | `work/inventario0749/root-observed-catalog-review09.APPROVED.json` | `16caccf362d965e6e2e1dc6a8736287a7dc383d5e9faff5fd9a1a186cc512b30` |

O join é `ROOT_INVENTARIO_THREE_PERIOD_JOIN_REVIEW_V1`: `completedComparisons=3`, `comparisonPassed=3`, `historicalContentComparisonApproved=true`, `pinsVerifiedBeforeAndAfter=true` e nenhuma razão pendente. Os eixos originais foram preservados e reproduzidos independentemente. O histórico ProducerTEST01, os resultados de diferença e o TEST03 falho continuam preservados. Os indicadores operacionais do join, como `contentAccepted`, `entityQualified`, `mainResumeAllowed` e `productionAuthorized`, permanecem falsos; o resultado comprova a conferência histórica desses períodos.

Este fluxo não executa SQL nem autoriza DDL, persistência operacional, adoção do Main ou deploy. A correção específica do campo está descrita em [Inventário: causa da recusa com 33 notas fiscais](inventario-causa-33-notas-fiscais.md).
