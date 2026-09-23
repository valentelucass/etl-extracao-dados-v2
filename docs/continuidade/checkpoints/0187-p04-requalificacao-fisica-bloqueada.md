# Checkpoint 0187 — P04: requalificação física bloqueada

## Fotografia

- Data: 20/09/2026 UTC. Anterior: [0186](0186-p04-p05-apos-0185-execucao-limitada.md), preservado.
- Ordem efetiva: `P04-REQUALIFICACAO-20260919-01`; ledger novo:
  `target/macrobloco-p04-requalificacao-20260919-01/ledger.json`.
- Alvo único: `localhost/ETL_SISTEMA_V2_SHADOW`, dados sintéticos, Windows
  integrado, rollback-only e commit de domínio bloqueado. Sem DDL, migração,
  Flyway, fonte real, V1, produção, P05–P08, deploy ou cutover.
- P03/B–H segue `ACEITO_NO_ESCOPO` só como predecessor técnico local. I/J não
  foram aceitos; P05/K não iniciou.

## Pré-condições e tentativa

- Consulta read-only no `master` confirmou exatamente
  `ETL_SISTEMA_V2_SHADOW`; não havia processo da campanha anterior.
- Preflight isolado em JDK17/512 MiB: `QualificationSupervisorTest` 1,
  `QualificationContractTest` 7 e `QualificationJournalTest` 4, todos PASS;
  Enforcer, Spotless e Checkstyle verdes. `Test-TrilhaPreparation.ps1` PASS
  somente para o mapa documental.
- `p04-requalificacao-physical-01` reservou 3.600 s e usou o controlador
  existente. `Physical` montou JAR e bibliotecas runtime antes da seleção
  Failsafe, com as duas travas Maven.

## Evidência observada

- Unidades: 12/12 PASS; `IntegralSweepEdgesIT`: 5/5 PASS;
  cancelamento: 3/3 PASS; concorrência: 1/1 PASS.
- Retomada: 2/6 PASS e 4 casos com saída 2. A sequência não emitiu recibo antes
  do teto de 240 s por etapa. Não houve coleta recursiva de fixture.
- A árvore comprovadamente própria (controlador, Maven e Failsafe) foi encerrada
  no teto. O recibo final do controlador não foi gravado por essa contenção.
- Leitura agregada antes/depois foi idêntica; rollback confirmado. Não restou
  processo próprio da tentativa.

## Decisão e próximas ações

O exit 2 e o timeout não tiveram causa local segura e proporcional delimitada
sem a coleta proibida. A segunda tentativa condicional não foi reservada ou
executada. P04 é `IMPLEMENTADO_NAO_QUALIFICADO/BLOQUEADO_POR_INPUT`; não houve
promoção de I/J nem execução de P05.

1. Somente sob nova autorização explícita, delimitar a causa do exit 2/timeout
   com método autorizado e validar offline uma correção proporcional antes de
   reservar outra P04.
2. Reservar P05 somente após aceite integral futuro de I/J, em campanha própria.
3. Preservar os recibos, ledgers e checkpoints; P06–P08 seguem fora do escopo.
