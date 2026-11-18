# 0680 — Fretes: 114 colunas comparadas; 109 integralmente iguais

Em 04/11/2026, a comparação efetiva das **114 colunas** de Fretes foi concluída para o par completo do dia 19/10. Foram conferidos conteúdo, presença, multiplicidade, associação das linhas completas e totais. **109 colunas coincidiram integralmente nos 850 grupos de Fretes. Cinco colunas têm diferenças concentradas em três grupos; nenhuma coluna ficou com conversão pendente.** Fretes continua com **0/3 semanas integralmente aprovadas**; Coletas mantém3/3. Anterior: [0679](0679-fretes-tres-atualizacoes-e-timeout-na-nova-captura.md).

## Dados e resultado

Source: execução `a8190d89-1369-44a6-8e93-8640a84e9747`, 946 ocorrências completas, 850 IDs, todos os membros e lexemas conservados. Excel: oficial ESL1763163, dia19/10, gerado04/11 às13:24, 946×114, sem cache. São os arquivos privados D07B/FF9E já vinculados nas evidências anteriores.

| Critério efetivamente executado | Resultado |
| --- | --- |
| Ocorrências de ambos os lados | 946/946; mesmos850 IDs e multiplicidades |
| Colunas com conteúdo integralmente igual | 109/114 |
| Colunas com diferenças | 5/114 |
| Colunas com conversão pendente | 0 |
| Associação conjunta das114 colunas por ID | 847 grupos iguais;3 diferentes |
| Totais numéricos por ocorrência | Todos os50 campos numéricos confrontados prontos e coincidentes na comparação requerida |
| Nulos/vazios | Contabilizados separadamente; nenhum convertido em zero |
| Dados descartados/deduplicados | Nenhum |

Os totais de inteiros usam o valor inteiro exato. Nos decimais, as representações originais e suas somas continuam preservadas; o eixo que representa cada célula numérica do Excel usa sua codificação discreta, sem tolerância ou arredondamento em casas decimais. Não foi alegada execução da função SUM do Microsoft Excel. Informações adicionais da API, como frações de segundo e a estrutura ordenada das notas fiscais, permanecem no original.

As cinco diferenças são:

| Coluna | Grupos diferentes | Grupos iguais |
| --- | --- | --- |
| Status | 2 | 848 |
| Previsão de entrega | 1 | 849 |
| Finalização de performance | 1 | 849 |
| Comprovante de entrega | 1 | 849 |
| Data de atualização | 3 | 847 |

Essas diferenças conservam os três registros já identificados pelo diagnóstico causal: a Data de atualização do Excel é posterior à da Source em todos eles. Os valores diferentes **não foram corrigidos manualmente, ignorados nem aprovados**. O novo par precisa ser capturado com intervalo menor entre API e Excel para conferir novamente.

## Prova de implementação e execução

O novo pacote passou **34 testes causais**:13 do sucessor de comparação,13 do verificador físico de Source/XML e8 do driver. Incluem inteiros adjacentes acima de2^53, NULL versuszero, vazios e campos omitidos, duplicação, troca de associação, falsificação de átomo/span/célula e payloadXML duplicado/opaco. Original0,2.912ms,pico82.767.872bytes. Qualificação privada: `work\fretes-proof-stack-synthetic0678-run01\diagnosis.json`, SHA `486156E98FC5ED4B0F23CA30445E36528BB98449E201105F4F69F3084F8C9B4D`.

O Root selecionou regras de apresentação explícitas e limitadas ao arquivo/campo: política `50A18B1B7C066F8A94C760C72848454241ED321D3797EBC424A30A2A9A6E0795`, decisão `CA95F71F38E0F0A594A5743178DB6B9406DA653C2BB9C65D59D121D6562A7FDF`. Nenhuma regra é escolhida automaticamente por maximizar igualdade. Regras de nulos/vazios preservam cada átomo original, excluindo espaço, zero e membro ausente não comprovado. A precisão adicional da API fica retida.

A execução real verificou independentemente todos os cabeçalhos, coordenadas, tipos, payloadsXML, máscaras de omissão, estilos/época e spans/átomos da Source. O verificador reteve198.013 fatos/células e36.323.831bytes lógicos limitados. Os flags de prova são derivados desses fatos, não de declarações do chamador. Nenhum frame antigo de935MB foi relido.

- Original0:43.368ms, pico406.380.544bytes.
- Python14620:0,41.436ms, stdin fechado e stdout/stderrEOF; todos os processos próprios encerrados.
- Guarda Root consumida exatamente uma vez, com relógio e débitos reais do bootstrap.
- Source/HTTP/SQL0 na comparação.
- Resultado privado de metadados: `C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\work\fretes-actual1140678-run01\audit\final-result.json`,30.488bytes, SHA `E6C894C971FF53886453F205F8D79CCC50BFED26D9C10E79E944F4528A958281`.
- Detalhes com valores e referências ficam em `raw-esl.jsonl.gz` privado, três registros; não copiar para Git/chat.
- Relatório seguro das114 colunas: `outputs\fretes-114-colunas-comparacao-19out2026-0680.csv`, fora do repositório,16.057bytes, SHA `893921B8AFA09540F5DE84D3618151E59F32956EBE630824B3B9CA1FAB864EB0`. Contém nomes/campos/contagens/veredictos, sem dados de negócio.

## Estado e próximas ações

Canônico permanece **849BA769268BDCD0CDC9F5306280CC67604146652B58F9A3975F3B7B9D9CB09D**,712.108bytes,ACL B4,Root0658,vaga vazia,17pares completos+3fatos parciais. A tentativa causal0678-01/per60 continua encerrada por422timeout/2HTTP/0reg, sem retry. Não restaurar98CA nem reutilizar grants.

1. Terra implementa um candidato completo para páginas menores, com orçamento finito e assinado, preservando o padrão16DATA/17HTTP, pausas/deadlines e STOP. A proposta47 não foi admitida nem executada. Root qualificará o código e o host antes de qualquer nova chamada.
2. Correções prepara a reutilização do comparador com contagens/períodos realmente derivados dos próximos arquivos e o fechamento semanal por dias, preservando toda ocorrência e limites de memória.
3. Capturar novo par próximo, repetir114 colunas/tuplas/totais até nenhuma diferença, completar e validar as três semanas; só depois escolher outra entidade.

Root é o único executor pesado/API/browser. Apoios na seção ETL V2 apenas produzem código estático. V1 pausado; SQL, produção, contas e permissões preservados. Consumo/publicação real do dashboard continuam pendentes.
