# Inventário 10633: causa da recusa com 33 notas fiscais

A correção específica do campo foi validada pelo Consumer TEST05 e pela requalificação Source0 dos nove RAWs históricos: 36.618 ocorrências, zero recusas. O consolidado 0749 fechou três semanas completas, com 26 colunas e seis totais iguais em Java e referência independente. A causa demonstrada nesta nota é a recusa tipada por 33 notas fiscais; as quatro divergências históricas posteriores nas colunas 25/26 têm causa ainda não estabelecida.

## Evidência observada

A captura ProducerTEST01 de 19–20/10/2026 reteve 5.158 ocorrências e o terminal `[]` real. O recibo histórico distingue `rowTypesQualified=true` de `mapperQualified=false`: houve uma recusa tipada, embora o RAW e as respostas originais tenham sido preservados.

O perfil pareado público `i749-pair-profile19-01/result.json`, SHA-256 `085b57add983ef3544d7fb733a27f97dad27033156ca0668cb984f28ecf75673`, reproduziu uma única constraint: `cnr_c_s_fit_invoices_mapping:MAPPING_ITEM_COUNT_GT32`. O máximo observado foi de 33 itens. O conjunto tinha 5.156 arrays e dois `NULL` nesse campo. Nenhum ID, número de nota ou valor empresarial é necessário para demonstrar a causa.

O contrato histórico de wire admite um array de strings; ele não estabelece o limite genérico de 32 itens. O mapper usava `ExpansionFieldParser.strings` e o tipo compartilhado `ExpansionStrings`, cujo limite local era 32. Assim, uma sequência real de 33 strings era retida pelo coletor e recusada pela representação tipada do Inventário. O máximo de uma observação anterior não deve ser tratado como limite normativo da API.

As observações de apresentação numérica e do separador Native servem à comparação independente. Elas não eliminam a recusa Java nem justificam truncar, ordenar, deduplicar ou converter o array em string no domínio.

## Correção específica

`InventarioInvoiceNumbers` é uma sequência imutável própria do campo `cnr_c_s_fit_invoices_mapping`. Implementa `OrderedExpansionStrings`, expõe `items()` e preserva cada string, sua ordem e suas repetições. Seus limites são 1.024 itens e 256 unidades UTF-16 por item, compatíveis com o bound já existente no adapter desse campo.

`InventarioInvoicesFieldParser` mantém as presenças `ABSENT`, `NULL` e `VALUE`, o wire e o raw do valor. O limite de raw continua em 4.000 unidades UTF-16, inclusive a representação JSON do array. Não há promessa de aceitar 1.024 strings de tamanho máximo simultaneamente: todos os limites continuam cumulativos. Tipo inválido, item não textual e excesso de itens ou tamanho são recusados; excesso de raw continua sendo `EXP_RAW_BOUND`.

Somente `InventarioObservation` e o campo correspondente de `InventarioDataExportMapper` recebem o novo tipo/parser. O contrato de `ExpansionStrings` permanece em 32 itens e 256 unidades UTF-16 por item para os demais consumidores. A interface compartilhada expressa uma sequência ordenada; não estabelece um novo limite global.

Os consumidores indispensáveis passam a tratar `OrderedExpansionStrings`: projeção observada, serialização JSON e encoding de staging. A restauração de observações DEV distingue o tipo genérico de 32 itens do tipo específico Inventário. Isso permite preservar o valor em projeção, sidecar e roundtrip sem fazer casts para o tipo antigo. O ajuste de encoding JDBC é estático; não exige executar SQL, abrir conexão ou alterar DDL.

Os limites Source/HTTP, memória, prazo, linhas, bytes, raw e retry permanecem os mesmos. A correção não muda a página terminal, o filtro do pedido, o catálogo Native ou o conteúdo capturado.

## Prova exigida do Consumer

O histórico ProducerTEST01 permanece literal, inclusive `mapperQualified=false` na parte afetada. `InventarioMapperQualificationMain` releu o mesmo RAW pinado, verificou o pedido e o terminal original e chamou o mapper real corrigido em cada ocorrência. Os recibos Consumer apontam o Producer e registram separadamente a qualificação atual, com zero recusas, EOF e pins verificados antes e depois.

A requalificação abrangeu todos os nove RAWs históricos, não apenas a ocorrência que revelou a falha: 10.546, 13.577 e 12.495 linhas, total de 36.618 chamadas ao mapper real. Os três recibos semanais registram `mapperQualified=true`, `rejectedRows=0`, `historicalProducerFactsRewritten=false`, `Source=0`, `HTTP=0` e `SQL=0`. O join final vincula os Source, Consumer, catálogo, diagnóstico Java, referência independente e encerramentos correspondentes. A qualificação atual não sobrescreve o resultado histórico do Producer.

## Cobertura de teste e histórico

A cobertura inclui preservação de 33 itens com ordem e duplicatas; manutenção da recusa de 33 itens no tipo genérico; `ABSENT`, `NULL` e array vazio; fronteiras 1.024/1.025 itens, 256/257 unidades UTF-16 e raw 4.000; tipos inválidos; imutabilidade; projeção e sidecar JSON; roundtrip DEV; encoding JDBC por proxy sem conexão; e requalificação Source0 pelo mapper real.

O TEST03 é histórico falho: 662 testes, uma falha e um erro. A falha de `InventarioDiagnosticMainTest` exigia o texto literal de um rótulo na saída privada, embora o diagnóstico armazene projeções e digests completos. A correção verifica igualdade de tuplas, participação integral dos valores e igualdade das provas, sem exigir dump textual. O erro de `ObservedExpansionPageProjectorTest` era um cast para `ExpansionStrings`; a correção usa `OrderedExpansionStrings` e conserva a lista esperada, a ordem, as repetições e as verificações do sidecar.

Essas duas correções de teste não afrouxam a igualdade nem modificam fixtures ou o catálogo. A execução final TEST05 passou 662 testes em 58 classes, sem falhas, erros ou skips, com Original encerrado e estado restaurado. A unidade Consumer congelada é `FDC994204148993A18DDE104E4270C0DBE953F2076ECEC6DFB54F8FB15AAF3FA`; seu recibo TEST05 tem SHA-256 `550463346e2c927ff6d1ba9fbce8d7f320026b52c3e8ac9b6a67c9ff03a7ed4a`. Esse resultado posterior não transforma o TEST03 em um teste aprovado.

| Evidência final | Resultado fechado |
| --- | --- |
| Consumer final | Unidade congelada acima, TEST05: 662 testes / 58 classes / zero falhas, erros ou skips |
| Requalificação Source0 dos nove RAWs históricos | 36.618 linhas, zero recusas, EOF/pins e fatos históricos preservados |
| Comparações finais Java e referência independente | Nove pares, 26 colunas, seis totais, tuplas e associações iguais |
| União e fechamento das três semanas | 10.546 / 13.577 / 12.495 linhas; 3/3 no join final09 fechado/restaurado |
| Catálogo observado mantido | `f5b680fdcc99f2674de0f4d652c7832f7e7b52340b996c03b446fcd61549cfa9` |

Os recibos públicos de requalificação estão em `work/i749-week12-requal-final05/java-summary.json`, `work/i749-week19-requal-final05/java-summary.json` e `work/i749-week26-requal-final05/java-summary.json` no workspace de execução. Seus SHA-256 são, respectivamente, `3bd39fb8e480ca1a39cf4d7a2f4a670301cabe56f35fbbfe44cd4d2666bba993`, `d4480dfed2e0baa6b81ab72a224ee32529f92837c5b728da166a350a5d9570af` e `2d3674d24b9ff6e61c8d046cda969bac66adfa588ffd3c93e623fc77d5148ca4`.

O consolidado final `work/i749-join-final09/result.json`, SHA-256 `544f30d9a4575e5c9eba1e3688296a7a52d81d2e3c5da36fb9e1649bd808a9c9`, usa os primeiros sete pares e os dois pares novos da última semana. As exportações Native independentes 1772629 e 1772632, geradas às 12:45 de 07/11/2026, coincidiram com as novas capturas Source sob TEST05. Os três e um IDs divergentes nos Native anteriores foram preservados. A causa dessas quatro diferenças não foi demonstrada; não houve atalho, normalização ou troca dos campos 25/26 para fechar a comparação.

Também permanece sem causa demonstrada a observação de 2.000 na lista da interface e 5.158 linhas no arquivo de 19–20/10. Não se atribui essa diferença a atraso ou cache. A contagem validada vem da leitura integral do artefato.

O resultado é uma conferência histórica dos três períodos. Não autoriza SQL, DDL, persistência operacional, adoção do Main, deploy ou afirmação de snapshot simultâneo.

O procedimento de execução está em [Inventário 10633: captura finita e conferência Native26](inventario-native26-offline.md).
