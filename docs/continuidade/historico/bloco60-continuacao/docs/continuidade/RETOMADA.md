# Retomada — B60 corrigido offline; campanha corretiva pendente

Objetivo: concluir o B60 com provas na camada efetivamente exercida.
Checkpoint: [0042-bloco60-correcao-offline-pacote-corretivo.md](checkpoints/0042-bloco60-correcao-offline-pacote-corretivo.md).
SHA-256: e40a84e3b8c98047b557951680af3a28a0db72d412026ab8cfab2963607f91f8

Correção do verificador do manifesto e teste de regressão concluídos.
Maven verify offline/Java17: 1394/0/0/4; cobertura e estilo passaram.
JAR original reproduziu ENTRY_INVALID; bundle corrigido aceito sem SQL;
variantes expirada/inconsistente recusadas. ACL e SQL físicos não requalificados.

Pacote: database/proposals/bloco60-correcao/package.json.
SHA-256: cc4f84cb37f697248d8a096249480985b252a7303c2408fb4cc4614bc829a167.
224 arquivos,74 casos; alvo localhost/ETL_SISTEMA_V2_SHADOW; validade até
16/09/2026 00:00 UTC. Parte de V024/SERVICE19, sem DDL, mesmos limites.
SERVICE19→20→21; quatro Users scopes2→3→4; políticas v2 novas; v1 preservadas.
Detalhes, limites e recuperação em database/proposals/bloco60-correcao/README.md.

A pergunta sobre autorização adicional de uma campanha corretiva segue pendente.
Não houve novo OPEN, SQL ou renovação automática. Não inferir aprovação do silêncio.
Pacote antigo a3d28ade…db57e permanece fechado/NOT_QUALIFIED e compensado:
SERVICE19, replay/force0,32grants,Users scopes2 revogados,V024 preservada.
Seu recibo: target/execucao-b60-aprovada-20260909-2334/final/receipt.json.

Manifesto: docs/catalogos/bloco60-correcao/manifesto.json.
Consolidação e diff: target/b60-correcao-20260910/final/.
67/115=58,26%;48pendentes;191rotas;zero AGORA;zero novos aceites.

Próximo passo: obter/verificar aprovação específica do pacote corretivo e validade;
executar Invoke-Bloco60CorrectivePhysical com SHA aprovado e token administrativo.
Parar na primeira falha, compensar e verificar perfil/multiconjunto histórico.
Não reabrir ledger anterior, ampliar orçamento, renovar janela ou marcar B60 por
provas offline. Preservar todos os históricos e o legado como escritor produtivo.