# 0749 — Inventário: código e três períodos completos conferidos

Em 07/11/2026, o Inventário ESL10633 fechou 3/3 semanas, 21 dias e 36.618 ocorrências do relatório. A conferência integral Java e a referência Python independente coincidiram nas 26 colunas, seis totais calculados, tuplas completas, vínculos por N° Ordem, multiplicidades e eixos originais. Quantidades incluem repetições de linhas; chaves distintas são contagens por semana.

| Período de 2026 | Linhas V2 = ESL | Chaves distintas | Resultado |
| --- | ---: | ---: | --- |
| 12–18/10 | 10.546 | 495 | Igual |
| 19–25/10 | 13.577 | 632 | Igual |
| 26/10–01/11 | 12.495 | 588 | Igual |

Cada semana usa três partes disjuntas de 2+2+3 dias. O join verificou a união exata, ausência de sobreposição de chaves entre partes, paginação contígua e terminal [] real. Cada Native foi lido com CRC/EOF, uma planilha, epoch1900 e zero fórmulas. Não houve concatenação dos RAWs, deduplicação ou tolerância numérica.

## Correção e qualificação

O mapper antigo recusava uma sequência real de 33 notas fiscais por usar o tipo genérico ExpansionStrings, limitado a32. InventarioInvoiceNumbers/InventarioInvoicesFieldParser implementam o campo específico como sequência imutável e ordenada, sem perder duplicatas. Limites1024itens/256UTF16 por item e raw4000 são cumulativos; o tipo genérico mantém32. Projeção, JSON, encoding e roundtrip DEV preservam o tipo. Não houve execução SQL.

FORMAT04 e compilação nova04 fecharam. O Consumer TEST05 passou662JUnit/58classes, zero falhas/erros/skips, unidade181inputs. TEST03 com1falha/1erro e TEST04 comdeadline após testes verdes permanecem falhos no histórico. A correção do supervisor de testes manteve três pós-checagens e inventários completos, qualificando dez fixtures de segurança; nenhum teto foi ampliado.

Os nove RAWs originais foram requalificados Source0 pelo mapper final:36.618linhas/zero recusas. O recibo Producer que recusou33notas continua mapperQualified=false. O resultado Consumer é um fato posterior. Os dois Source novos selecionados usam o mesmo ConsumerTEST05, com mapper/tipos qualificados na própria captura.

## Seleção final e diferenças preservadas

Os primeiros sete pares foram reutilizados somente depois de provar os quatro pins exatos, os mesmos controles de seis campos byte a byte, diagnósticos Java/Python completos e qualificação atual. Para28–29/10 e30/10–01/11 foram usados Source novos e Native independentes1772629/1772632, gerados pela interface às12:45. Os novos dois pares coincidiram integralmente. A cobertura week26 foi executada novamente; week12/19 preservaram seus recibos exatos.

Nos pares anteriores, três ordens da penúltima parte e uma da última divergiam nas colunas25/26. As outras24colunas, seis totais e multiplicidades já coincidiam. As duas extrações Source novas, comparadas com os Native antigos, continuaram divergentes; a Source da última parte manteve SHA idêntico. Perfis integrais posteriores demonstraram que os Native novos diferem dos antigos na cauda e coincidem com os Source novos. Isso comprova a diferença observada entre artefatos; a causa sistêmica ou temporal não foi estabelecida. Não houve normalização, remapeamento nem exclusão das colunas25/26.

A lista ESL mostrou2000linhas no arquivo1772455, mas a leitura integral desse XLSX encontrou5158. Causa desconhecida; não inferir atraso/cache. A conferência usou o arquivo completo. Dois episódios de admissão sem despacho têm auditorias físicas Source0 preservadas; não foram convertidos em HTTPsucesso. As capturas completas históricas totalizam11/49HTTP; a seleção final de nove totaliza40HTTP.

## Evidências finais

Workspace: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade.

- Relatório: outputs/inventario-3periodos0749/inventario-3-de-3-concluido.json, SHA17DEEDF1B272BA41F655B804C1E4156F54103351FB41AAFDB8D7FBCA3261C3D8.
- Join: work/i749-join-final09/result.json, SHA544F30D9A4575E5C9EBA1E3688296A7A52D81D2E3C5DA36FB9E1649BD808A9C9, encerrado/restaurado.
- TEST05: work/independent-export-inventario0749-test05/root-executed-binding.json, SHA550463346E2C927FF6D1BA9FBCE8D7F320026B52C3E8AC9B6A67C9FF03A7ED4A.
- Unidade: outputs/independent-export-formatted-inventario0749-compile04/offline-unit.PREPARED.json, SHAFDC994204148993A18DDE104E4270C0DBE953F2076ECEC6DFB54F8FB15AAF3FA.
- Catálogo F5B680FDCC99F2674DE0F4D652C7832F7E7B52340B996C03B446FCD61549CFA9; revisão Root externa16CACCF362D965E6E2E1DC6A8736287A7DC383D5E9FAFF5FD9A1A186CC512B30. Marcadores internos de candidato preservados.
- Rebind7: work/i749-seven-pair-rebind09/result.json, SHA490712CDBE92D49CCDB60615738A51204499F13CE8A4DFBF6E9080C500A745B6.
- Perfis Native antigo/novo: work/i749-part2-native-change10/result.json F30CDD4909756FBEA01C9CFE50D5A50BE854E35B8ACABD77D6879FB519C494E0; part3 C37F717334ECFD3554DBA0BDA3C6BC397310DCEABC8C6CCC82FBCF33A34B2BFA. Casos empresariais retidos somente em provas privadas.
- Excel: outputs/inventario-3periodos0749/Provas_Inventario_ETL_V2.xlsx,8752B, SHAB8BA87E5C2FCBE0BC41F0AF18FD61CC9D0C9BAE191BB02A3184F8AF2A3D15BE2. Duas abas, três quantidades e nove registros reais, incluindo33NFs; fórmulas recalculadas, reabertura e inspeção visual realizadas.
- Grafo AST final: work/i749-graph-final05/graph-executed.json, SHA4E113381559B174664304776C8B5048BB4C98C238EB9ADE064189BC508B51659. Unidade181inputs, evidência alheia preservada,0LLM/HTTP/SQL.

Todos os Originals próprios encerraram e restauraram o estado/slot. Canônico FB923CC52CBB6740386AB1D62D9ADD4663C04675E6FCE645EEB08B36EC6DA7E6/781028B e AUX28 49A25567 preservados. Original1GiB/300s, Source210s/17HTTP, retry0 e um executor pesado por vez mantidos. Uma aba ESL permaneceu aberta. Sem SQL/deploy/ACL/V1.

A etapa fecha a comparação histórica e as correções no código. Não concede adoção operacional, retomada do Main, qualificação de entidade ou snapshot simultâneo. Coletas/Fretes0744, Manifestos0747 e Cotações0748 permanecem fechados em seus próprios escopos. Cinco entidades somam15períodos e96.781ocorrências conferidas; isso não é contagem global de entidades únicas.

Manuais: docs/validacao/inventario-native26-offline.md e docs/validacao/inventario-causa-33-notas-fiscais.md. Próximo trabalho requer escopo próprio; não iniciar outra entidade automaticamente.
