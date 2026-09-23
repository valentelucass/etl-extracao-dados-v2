# Checkpoint 0195 — P04/I-J pós-0194 fechado sem qualificação

Data: 20/09/2026. Ordem: `P04-P05-POS0194-01`.

## Fotografia

- Preflight ArtifactDirected: 18 testes, zero falhas/erros/skips; gates de
  build PASS. A primeira execução integral atingiu o teto de240s antes do
  relatório final; a continuação do mesmo snapshot concluída dentro do teto
  é a evidência de preflight, não uma tentativa Physical.
- Reserva Physical única: `p04-p05-pos0194-p04-01`, alvo local sombra exato,
  dados sintéticos, Windows integrada, rollback-only, heap512MiB; sem DDL,
  fonte, V1, produção, P05/K ou P06+.
- Resultado: controlador exit127, sem timeout, UTF-8/limite de log PASS,
  rollback agregado byte-a-byte igual, zero processo próprio após o término.
- XMLs no build isolado: sweep5/0/0/0, cancelamento3/0/0/0,
  concorrência1/0/0/0, retomada6/0/0/0 e supervisor5/1/0/0
  (testes/falhas/erros/skips). O supervisor levou2405.848s, além do limite de
  sequência1800s; o caso tardio esperava33 previews e observou0. O guard também leu o workspace, não o build isolado, e listou
  todos os XMLs como ausentes; não compensar essa falha pelo artefato posterior.
- Após o fechamento, a asserção test-only do terminal bloqueado foi corrigida
  para preview0 (e33 nos estágios anteriores). A tentativa de compile/format
  parou antes da fonte por incompatibilidade JVM25/formatter fixado. Não há
  segunda execução física nesta ordem.

## Decisão e limites

P04/I-J: **IMPLEMENTADO_NAO_QUALIFICADO**. A reserva está consumida e não se
repete. P05/K não tem reserva, não foi executado e não é promovido; P06+ segue
fora de escopo. Mantêm-se39/45 construção,67/115 aceites, oito
MISSING_CANDIDATE e a falha histórica
`STATES_SUCCESSION_HASH_docs/continuidade/RETOMADA.md`.

## Próximas ações

1. Não executar nova campanha P04 ou P05 sem nova autoridade explícita e
   finita, depois de uma correção causal reavaliada.
2. Se houver nova autoridade, corrigir separadamente a expectativa tardia e o
   caminho do guard, com preflight novo; não reutilizar esta reserva.
3. Preservar ledger, XMLs, logs e readbacks como evidência histórica.

Referências: `target/P04-P05-POS0194-01/`,
`target/macrobloco-campanhas-integrais-20260915-01/p04-p05-pos0194-p04-01/` e
`docs/catalogos/campanhas-integrais/P04-P05-POS0194.md`.
