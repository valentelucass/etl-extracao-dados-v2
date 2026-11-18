# Sinistros 6392: captura finita e conferência Native44

O fechamento 0750 comprovou **3/3 semanas completas**, 21 dias, **38 linhas e 23 números distintos de Sinistro**, com 44 colunas e dez totais iguais entre V2 e os três arquivos independentes da ESL. Java e referência Python independente coincidiram nas tuplas completas, associações por `sequence_code`, multiplicidades e seis eixos originais. A conclusão vale para estes artefatos históricos; adoção operacional permanece pendente.

| Período inclusivo de 2026 | Linhas V2 = ESL | Sinistros distintos | Resultado |
| --- | ---: | ---: | --- |
| 12–18/10 | 13 | 8 | Igual |
| 19–25/10 | 10 | 9 | Igual |
| 26/10–01/11 | 15 | 6 | Igual |

## Pedido e captura

Perfil `SINISTROS_6392_NATIVE44_V1`, entidade `SINISTROS`, relatório6392, zona `America/Sao_Paulo`. O filtro `insurance_claims[opening_at_date]` é inclusivo. Os outros cinco filtros são explicitamente vazios: `insurance_claims[corporation_id]`, `scopes[by_freight_payer_id]`, `scopes[by_invoice_number]`, `scopes[by_freight_document_number]`, `insurance_claims[status]`. A data de abertura foi conferida em cada linha. Um plano não concede autorização de Source.

`SinistrosIndependentRequest`, `SinistrosTransport`, `SinistrosIntervalCapture` e `SinistrosSourceCaptureMain` implementam o pedido e a captura finita. Cada semana inteira teve uma INFO, uma DATA com registros e uma DATA terminal `[]`: três capturas/nove HTTP. Página curta não substitui terminal. O payload retém todas as ocorrências, ordem, tipos e lexemas numéricos originais; não há truncamento, ordenação ou deduplicação. Native foi lido com CRC/EOF, uma planilha, epoch1900, cabeçalhos44 exatos e zero fórmulas.

Limites preservados: Source210s, até17HTTP, DATA até16páginas/per100, 8MiB, 12.000linhas, retry0/redirect0; Original1GiB/300s, worker270s e Java512m. Um executor pesado por vez. A qualificação e todas as comparações finais usaram Source0/HTTP0/SQL0.

## Correção no mapper e armazenamento

O mapper antigo recusou17 linhas (2/2/13) porque `insurance_claim_commentary` excedia1.024 unidades UTF16; os máximos observados foram1.281/1.524/1.557. Os RAWs completos e recibos Producer747 permanecem intactos, com suas recusas históricas.

O helper específico em `SinistroDataExportMapper` preserva o comentário literal em `raw` e `value`, até o limite já existente de4.000 UTF16 em `ExpansionValue`. Ausente/NULL/não textual/até1.024 continuam pelo parser existente.4.001 recusa `EXP_RAW_BOUND`. Não há trim, corte, normalização Unicode, coerção de número ou mudança de whitespace. `ExpansionFieldParser.text` e os outros18 textos permanecem limitados a1.024.

`SinistrosMapperQualificationMain`/`SinistrosRawMapperQualifier` releram os mesmos três RAWs com Consumer753:38 invocações reais,38 linhas até EOF, tipos qualificados, **zero recusas**. A apresentação Native não foi usada para qualificar o mapper. O resultado posterior não reescreve o fato Producer.

V038 define rawNVARCHAR4000 e tipadoNVARCHAR1024; o bind JDBC existente não corta `raw`/`value`. Foram incorporados como código `database/migrations/V106__widen_sinistro_commentary_typed_storage.sql` e `database/validation/104_validate_sinistro_commentary_typed_storage.sql`. V106 amplia somente o comentário tipado paraNVARCHAR4000 no SHADOW exato, com guardas/transação e preservação de NULL/collation/demais colunas. **Não houve execução SQL**. Aplicação física, check104, inventário/validator global paraepoch106, JDBC/Main e deploy continuam pendentes; `063_validate_epoch_v104_v105.sql` mantém104/105.

## Contrato de apresentação observado

Catálogo `contracts/reconciliacao/sinistros-native44-observed-20261107-v1.json`, SHA21AEC2F661B11267FF2AA1F2B69C7352C9A2AAD2E80FD80DCD620CB33A2D057C. O recurso mantém natureza de candidato e marcadores internos false. A revisão Root externa e o join concedem somente a conferência histórica ligada a esse pin.

Ausente, NULL, vazio, célula ausente e blank continuam distintos por padrão. As exceções de apresentação são específicas por coluna/carrier. Somente `icm_ttt_dealing_type` e `icm_ttt_solution_type` têm mapas de enum explícitos; localização e comentário são textos livres, sem dicionários por registro. Novos valores não observados exigem revisão.

`rctrc`, `rctac`, `rcfdc`: apenas `[]` usa `EMPTY_ARRAY_NATIVE_ABSENT`; NULL e [""] não se tornam array vazio. Ordem/duplicatas são preservadas. Os casos reais têm0/1item; o separador declarado não qualifica empiricamente múltiplos itens.

Horas de ocorrência/finalização: `SOURCE_OFFSET_DATE_CLOCK_SECOND_V1` / `NATIVE_DATE_TIME_NEAREST_SECOND_V1`, conservando data e relógio civil. Não converte para UTC nem descarta a data Native. Fração Source não zero ou arredondamento que atravesse o dia é recusado. Literal/offset Source e encoding Native permanecem no eixo original. `customer_communication_time` foi NULL nas38 linhas; o primeiro valor nãoNULL exige observação/revisão própria.

Numéricos: `DIRECT_BINARY64_NEAREST_EVEN_V1`, referência racional independente e totais em unidades binary64 exatas. Sem G16, epsilon, arredondamento para moeda ou soma float como atalho de igualdade. Dez colunas de total:23,26,27,28,30,31,32,33,34,35. Isso não valida regras financeiras operacionais.

## Testes, provas e reprodução

FORMAT05 e compilação05 fechados. TESTV5 passou **753JUnit/68classes, zero falhas/erros/skips**. Unidade182Java/32controles/214inputs. Foram três subconjuntos disjuntos351+298+104 e uma auditoria Original posterior, com nomes de classe/método exatamente uma vez e artefatos/XMLs efetivos verificados. Não foi uma única chamada com753 testes. Os limites permaneceram os mesmos.

Referência independente:30fixtures; join:16; guardas dos subconjuntos:21. Tentativas antigas com deadline, fixture inválida e duas expectativas obsoletas de recusa a1.024 permanecem falhas históricas, sem promoção para aceite. A correção das duas expectativas foi recompilada e está incluída no TEST final.

`SinistrosContentAdapter`/`SinistrosDiagnosticMain` e referência Python leram os três pares completos. O join reabriu diagnósticos efetivos, seis eixos, requalificação final, pedidos, RAWs/terminais, Native/acquisição e fechamentos. `success=true` isolado de um diagnóstico não concede igualdade.

O Excel tem abas **Quantidades** e **Amostras**, com38linhas/23sinistros distintos e nove ocorrências reais (três/semana), campos V2/ESL lado a lado e arquivo/linha ESL. Amostras foram pareadas pela tupla completa44 e filas de ordinais, conservando repetições. Não se inferiu quantidade de documentos distintos pela soma de `invoices_count`. Fórmulas recalculadas e testadas por alterações restauradas, arquivo reaberto, zero erros e duas abas inspecionadas visualmente pelo Root.

Workspace das provas: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade.

| Prova | SHA-256 |
| --- | --- |
| outputs/sinistros-3periodos0750/sinistros-3-de-3-concluido.json | 9433FDDB2E661571178F7B41EEE098BEE29887CEF297A08F0C7138D5DF73E2A1 |
| work/s750-join21/result.json | 73CFDD5B0F231A5486598044FBEC8BC4805B72F5550346300EFF81B4CD481CF2 |
| work/independent-export-sinistros0750-test-union28/root-executed-binding.json | 536589C8A78076EF152E40B708AAB6F0FDD4471B705C5F73FC4E830B66DB26E7 |
| work/s750-qualify20/java-summary.json | D56D22C64A804AEEEAF5EC2009AB2F0608C12528A3199B8500511A4CEBDB32C6 |
| work/s750-graph30/graph-executed.json | D25B4A96F7B4E1D5B83068193AAB9E5F7E07D036D7E8541E5C0C871C03ABDB43 |
| outputs/sinistros-3periodos0750/Provas_Sinistros_ETL_V2.xlsx | 84249B9F0C3D5748607E7750AF543F1967A25765DE940904EB1BD707C670C57D |

Grafo AST atualizado para os214inputs da unidade final, com evidências alheias preservadas e0LLM/HTTP/SQL. Originals próprios fechados/restaurados e slot vazio. CanônicoFB923CC5/781028B, ACLB4 e AUX28/49A25567 preservados. SemSQL/deploy/V1. Uma aba ESL. Não há garantia de zero erro futuro nem prova de snapshot simultâneo.
