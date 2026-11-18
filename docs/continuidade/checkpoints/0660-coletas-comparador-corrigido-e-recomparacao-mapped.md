# 0660 — Coletas: comparador corrigido e conteúdo da saída extraída conferido

Em03/11/2026, Root executou uma comparação real completa de MAPPED_PAYLOAD com o Excel oficial de29/10 para19–25/10/2026:1561linhas,31colunas,1155raízes,40.199registros de diagnóstico. Foi usada a saída extraída fechada, cuja igualdade de48.391células com a RAW da API já foi comprovada no0659. Nenhuma nova extração, chamadaAPI ou SQL foi realizada.

Os seis subjects independentes das três grandezas decimais nas duas comparações foram emitidos após revisão do INFO, dos vínculos de campo e dos carriers reais, mais32testes sintéticos. A regra permite somente comparar strings numéricasSource de três campos declarados como decimais com números nativosExcel, usando o parser exato. Não altera os valores nem aceita arredondamento, tolerância, vazio→zero ou equivalência genérica de nulos.

O confronto real confirmou a correção: os três campos deixaram de ter pendência de carrier e mantiveram46,47e56raízes diferentes em Peso real, Peso taxado e Valor total. Cada campo ainda registra1155diferenças estritas de tipo, como esperado. Seis colunas são literalmente iguais, dois dos oito totais são iguais e seis permanecem diferentes. Volumes mantém12raízes diferentes. Tentativas e Número do Manifesto mantêm162raízes diferentes cada. A mudança entre os Excel28/29 explica aritmeticamente333dos335volumes de diferença posterior, mas não a causa de negócio nem os2iniciais. Isto não prova paridade externa e não autoriza alterar os dados para forçar igualdade.

Execução03: Original0/71.799ms/pico341.929.984B, uma passagem/1155chamadas de kernel, todos os inputs preservados e arquivos fechados. Orçamento observado:1.983.795unidades de parse,824.936de trabalho,52.594entradas de índice de pico; limites8milhões/16milhões/100mil preservados. Dados privados declarados18arquivos/26.232.089B. RAW/Source/produto não reexecutados; leitor nativo já aceito reutilizado. Native/Original/Python/EOF/handles/mesmo owner comprovados; ledger restaurado exatamente1D1B/701862B/ACL B4/slot0. Não há adoção canônica nem entidade qualificada.

## Causas de integração resolvidas

- Unidade01 fechou antes da comparação porque o kernel histórico99DDA era exigido como origem da configuração original, mas havia sido substituído na declaração de inputs. A unidade02 incluiu explicitamente esse artefato de proveniência, sem alias ou alteração do arquivo.
- Unidade02 fechou antes da comparação porque `source_view` importa o kernel histórico pelo nome original e verifica sua origem real. Substituir o módulo por A2 violava corretamente a admissão histórica.
- Unidade03 preservou99DDA e seus quatro vínculos de origem. A2 foi carregado no módulo separado `semantic_kernel` e usado explicitamente no comparador. Common e bootstrap exigem exatamente sete papéis, com identidades de kernel distintas. Os controles de origem, limites e fechamento permaneceram. Falhas01/02 e seus grants consumidos foram preservados; nenhuma repetição automática ou requisição foi feita.

## Restante e limite externo

Coletas segue0/3períodos100%. Datas civis precisam da época1900 e do estilo por célula, sem engolir timestamps, frações ou células presentes em branco. Ação eNULL/omissão exigem políticas específicas por campo e coordenada. O DRAFT04 foi lido pelo Root, ainda não executado ou admitido. Seu índice de omissão retém todas as coordenadas sem contabilizá-las: somado ao pico52.594, poderia exceder100mil. Antes da adoção, Fix prepara somente uma correção estática que conserva, por linha, ID e Ação, verifica todas as coordenadas temporariamente, registra as colunas efetivamente indexadas e cobra o orçamento compartilhado. As três datas podem usar a resolução direta das células sem esse índice.

STOP429 anterior continua sem evidência de liberação vinculada ao caller efetivo. Não afirmar que a API retorna429 hoje: não foi feita nova chamada. O navegadorDataExport está autenticado. Os registros200recentes doV1 não trazem o vínculo necessário; V1 foi informado pausado pelo usuário e não foi iniciado ou alterado. As três rodadas externas exigirão capturasAPI eExcel próximas no tempo e a liberação aplicável da origem. Não avançar outra entidade antes dos três100%.

## Provas e saída privada

Raiz de provas: `C:\Users\suporte\Documents\Armazenamento-Projetos\etl-extracao-dados-v2\quatro-etapas-20261024-01\supervisor-team0649-01`.

- `root-acceptance-coletas31-mapped-decimal0660-03.json`,25510B,SHA2568A8A5148DE8E295EEC4D763460B4BE30D981937942370676BBA810ECF5CD065A.
- Falha01: `root-acceptance-coletas31-semantic01-closed-stop0660-01.json`,1802B,SHA25627E1C96670A39E02F7492DF07082EC2FEFAA10673A21992489E6E46B12D157E5.
- Falha02: `root-acceptance-coletas31-semantic02-closed-stop0660-01.json`,1766B,SHA25655FBD550947D30F5FA6353A6F7D539854E2C572860ECB523D6F2220117C1F7E7.
- CSV por coluna: workspaceRoot `outputs/coletas-1561-31-colunas-mapped-excel29out-corrigido0660.csv`,4333B,SHA256985B0A0C33DBF096C6AF4F7FD789673F306CFD43AAA19CC08CEF2B577017DF3A.
- ContinuidadeRoot: `work/supervision-active0660-01.json`,3299B,SHA256EB8A938043B14A6CAB04C6CDDFEB1BD8B0B8B5C2B552BAE4ADC1C8E6017489D9.

Root continua único executor pesado; chat de correções somente Coletas. SQL16, PM2/V1, contas, permissões, produção e dados de negócio preservados. Documentos estão no checkout local; esta unidade não executou commit/push nem o hook de reconstruçãoGraphify.
