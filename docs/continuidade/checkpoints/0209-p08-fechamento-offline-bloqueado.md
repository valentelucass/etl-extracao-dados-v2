# Checkpoint0209 — P08 offline reproduzido, guard extraído bloqueado — 21/09/2026

## Estado

- Frente: P08/M e P08/N.
- Predecessor: [0208](0208-p08-preflight-auditoria-indisponivel.md); as provas
  físicas A/B, VALUE e COMMAND posteriores a 0208 foram preservadas e
  reconciliadas somente pelos seus recibos.
- Veredito: `M=BLOCKED`, `N=BLOCKED`.
- Relatório: [P08-FECHAMENTO-LOCAL-POS0216](../../catalogos/campanhas-integrais/P08-FECHAMENTO-LOCAL-POS0216.md).

## Evidência observada

- Dois novos pacotes offline foram byte idênticos entre si e ao pacote
  `archivelimit-11`: ZIP `ebd4d13d6e5acd0c3d92d259a8e797ffa1f9d36caf4e40b2aa2427892c3a19c9`,
  manifesto `c29621a64e96bfa7d448d87164a104b95d001efc82a5f067a0f1a2002c8ea5d4`,
  724 membros, 9 dependências e 2.116 fontes.
- `Test-QualificationPackage` validou ambos os payloads; o guard de envelope
  passou 25 casos.
- O guard extraído parou no primeiro caso: recusa antes de controle/JDBC,
  `exit=2`, `QUAL_JSON_ARRAY` observado versus `QUAL_JSON_MEMBERS` exigido.
  O recibo é
  `target/macrobloco-qualificacao-pacote-20260913-01/p08-extracted-guards-offline-20260921-16/result.json`.
- Não houve SQL/JDBC/JAR nesta unidade. Consequentemente, os agregados de
  `ctl.execution_audit`, `ctl.page_audit` e `ctl.execution_page_audit` não
  foram relidos; nenhuma igualdade antes/depois adicional é alegada.

## Limites e continuidade

- O ledger físico de 51.600 s está fechado depois do pré-flight de 1.200 s e
  não transfere seus 50.400 s. Os ledgers posteriores não comprovam uma nova
  autorização finita completa.
- `p08-offline-reconciliacao-20260921-16/ledger.json` consumiu 1.500 s em
  ações exclusivamente offline; não é autorização física.
- Não repetir esse guard, contraprova, SQL ou JAR nesta rodada. Preservar P07,
  V049, A/B, VALUE, COMMAND e todos os manifests/ledgers históricos.

Próximas ações, sob novo macrobloco autorizado: corrigir ou ratificar o contrato
do guard offline com prova nova; validar autorização/contrato de agregados `ctl`;
então reservar e executar a cadeia física completa desde o pré-flight. A/L ficam
abertas e 39/45, 67/115 não mudam.
