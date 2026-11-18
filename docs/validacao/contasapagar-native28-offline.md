# Contas a Pagar 8636 — conferência semanal completa

**Concluído: três de três semanas completas.** A conferência histórica foi aprovada com os arquivos e o código vinculados às provas abaixo.

Perfil `CONTAS_A_PAGAR_8636_ISSUE_DATE_NATIVE28_V1`: o intervalo é definido por **Emissão**. Data criação e os demais sete filtros ficam explicitamente vazios. Cadastro anterior à semana não exclui uma emissão pertencente ao intervalo. Este perfil não reproduz os pedidos legados com criação e emissão simultaneamente preenchidas.

| Emissão inclusiva | Linhas V2 | Linhas ESL | Arquivo oficial |
| --- | ---: | ---: | ---: |
| 12–18/10/2026 | 1.174 | 1.174 | 1772913 |
| 19–25/10/2026 | 1.256 | 1.256 | 1772917 |
| 26/10–01/11/2026 | 1.005 | 1.005 | 1772921 |
| Total de ocorrências físicas | 3.435 | 3.435 | 3 semanas/21 dias |

Cada semana é a concatenação literal de três partes disjuntas de2+2+3dias. As nove capturas completas somaram53HTTP e têm paginação contígua e terminal `[]` real. Não se inventou uma captura semanal única. Os três arquivos independentes foram baixados pela interface da ESL e lidos integralmente comCRC/EOF. Os0HTTP dos recibos de ingestão se referem somente à cópia e integridade locais: o tráfego do navegador não foi medido.

Não há chave raiz demonstrada. Parcela/documento são valores para conferência; as3.435linhas não são quantidade de contas distintas. Não houve deduplicação, ordenação por candidata ou descarte de rateios. Bags de28colunas, tuplas completas, multiplicidades e sete totais são comparados em Java e referência Python independente. Quatro projeções completas preservam os valores originais e os valores apresentados em ambos os lados.

## Correções no código

O coletor finito, leitor Native28, tabela sem identidade, comparador, diagnóstico e qualificador foram incorporados ao V2. O coletor retém os bytes originais, respeita a paginação/terminal e trata cada registro como ocorrência física. Per100 não recebe interpretação de contagem de raízes.

O mapper específico de `ant_ces_value` aceita os valores observados com escala9..14, precisão34,14 e no máximo20dígitos inteiros, usando `UNNECESSARY`. Não corta nem arredonda o valor; original, wiretype e presença ficam preservados. Os seis demais decimais e o parser genérico28,8 não foram ampliados.

P3 `expansion-8636-observed-wire-20261107-p3.json` adiciona somenteNULL ao campo textual `ant_ces_acr_name`, observado em5ocorrências. P2 permanece byte a byte intacto. Os nove RAWs capturados pelo Producer07 foram requalificados pelo Consumer11 final: zero recusas. As flagsfalse de tipo nos dois últimos recibos Producer permanecem históricas e não foram reescritas.

## Apresentação observada da ESL

O catálogo permanece candidato, com autoridade operacional/identidadefalse. A revisão Root externa e o join concedem somente a conferência histórica associada aos pins efetivos. O catálogo não substitui a origem: não houve edição de valores ESL/RAW para obter igualdade.

Somente Rateio (`ant_ces_value`) usa a regra Source exata binary64→decimal16significativos HALF_EVEN→binary64. O Native e os seis demais campos Source usam codificação direta. As3.435células deRateio coincidiram pela regra;21encodings originais diferem da codificação direta, distribuídos1/19/1pelas semanas. O algoritmo conserva os originais e não usa epsilon, duas casas ou arredondamento do total. O comportamento é observado; não identifica qual biblioteca a ESL usa.

`created_at` apresenta o relógio civil Source truncando somente a fração, sem conversão UTC. A regra explicou3.435/3.435ocorrências; arredondar a fração explicaria apenas1.723. Os onze códigos de tipo e três classificações têm mapas de rótulo funcionais obtidos das ocorrências completas; valor novo não observado recusa até revisão. Classificação continua texto com mapa explícito, sem converter todo texto em enum.

NULL/vazio/célula ausente/blank permanecem distintos por padrão. As políticas específicas de apresentação foram comprovadas por pares completos: NULLtextual/booleano→célula ausente; NULLdas duas datas de transação/liquidação→blank; vazio observado de três campos de comentário→célula ausente. Emissão permanece estrita, nuncaNULL/vazio. Pago usa os rótulosSim/Não; conciliado usa booleano Native. Carriers numéricos, datas e timestamps foram observados nas três semanas com epoch1900.

## Testes e fechamento

FORMAT09, compilação nova11 e TESTV5-11: **890JUnit/82classes, zero falhas/erros/skips**, unidade213Java/37controles/250inputs. Três grupos disjuntos241+351+298 e uma auditoria final cobriram todos os nomes exatamente uma vez. Maven448m permaneceu abaixo do tetoJava512m; Original1GiB/300s e demais prazos mantidos. Falhas anteriores de testes, memória e prazo estão preservadas; nenhum artefato parcial qualificou a versão final.

As12operações finais Java/Python/eixos/qualificador fecharam com sucesso. O join16 antigo recusou por TypeError ao esperar array para `allowedExitCodes`, enquanto o wrapper efetivo serializa o único código como inteiro0. A correção do consolidador aceita somente inteiro0 ou lista[0] em operação de exit único; compare exige lista[0,3]. Booleanos, strings e outros códigos recusam. O novo join reutiliza os12recibos válidos; não repete Source/HTTP. A tentativa falha e o diagnóstico incompleto69 permanecem históricos.

Excel: somente resumos dequantidades e nove ocorrências reais, três/semana, com seis campos V2/ESL lado a lado, diferença calculada, arquivo oficial e linha ESL. O pareamento usa a tupla completa28 e filas de ordinais para preservar repetições. Fórmulas são perturbadas/restauradas, recalculadas, exportadas, reabertas e verificadas; Root inspeciona as duas abas renderizadas antes da entrega.

## Limite de armazenamento e adoção

**Não houve SQL.** V038 ainda define o tipado `stg.expansion_lab_cap.ant_ces_value` comoDECIMAL28,8 e o cabeçalho compartilhado `allocation_amount` comoDECIMAL28,8. Precisão física/JDBC, migração/validator/epoch, persistência integral/Main, acionador, dashboard e deploy exigem qualificação própria. Não foi criada nem aplicadaV107. A precisão34,14 do mapper e a igualdade histórica não autorizam afirmar que uma gravação no banco atual preserva14casas.

Sem chave raiz, não há adoção canônica por parcela. A conclusão histórica não garante zeroerro futuro ou snapshot simultâneo. CanônicoFB923CC5/781028B/ACLB4 e AUX28 preservados, Originals próprios fechados/restaurados e slot vazio; uma aba ESL, um executor pesado por vez. SQL/deploy/V1/ACL0.

Workspace: C:\Users\suporte\Documents\Codex\2026-11-06\etl-v2-proxima-entidade

| Prova | SHA-256 |
| --- | --- |
| Relatório final0751 | 396618B644596AB554938AC061D1AE2FE9E27CD8D75C7C30DC78C9ED81550F1A |
| Join final | 02BE51F51DE723CC493ABC44EE89E306BFA485AB9CA2729F87945AF5F45EC61C |
| TEST11 completo | 37CB4CC03579A61EC46A1BC8DEDF5D4FB514193FD8A4952E3C8A2C084009AEB2 |
| Catálogo observado | 25C31DA22214FE486CE4085322508D2BEC5B830A0FCDF56C6A9E3F08BFA7D9D8 |
| Excel de provas | 12287745c342e2d7e6d3f91686260fe1c1770760b947e4cb7b1887eeaa04594a |
| Grafo AST final | F158C6E28CB511BEA92F4FDB4325FEB4FA7176F47E07ABFDE72B4EF48327E572 |

