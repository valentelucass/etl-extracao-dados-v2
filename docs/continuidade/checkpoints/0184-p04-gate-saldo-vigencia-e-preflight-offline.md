# Checkpoint0184 — gate P04 de saldo/vigência e preflight offline

## Identificação e objetivo

- Data: 2026-09-20T00:37:33Z (19/09/2026 em America/Sao_Paulo).
- Anterior: `0183-p04-correcao-temporal-e-bloqueio-de-reserva.md`.
- Objetivo: qualificar P04/I–J somente se o gate de saldo/vigência fosse
  integralmente atendido; executar P05/K apenas depois do aceite integral P04
  e de saldo próprio.
- Estado: P04/I–J e P05/K `BLOQUEADO_POR_INPUT`; nenhum aceite novo.

## Preflight de autorização e reconciliação

Antes de diretório novo, reserva, processo próprio, conexão JDBC ou SQL, foram
conferidos o ledger e os seis pares de recibos históricos em
`target/macrobloco-campanhas-integrais-20260915-01/`.

| Escopo | Estado observado | Conclusão |
| --- | --- | --- |
| `p04-supervisor-sql-01..06/result.json` | Todos `OBSERVED`, `exit=1`, sem timeout, `rollbackConfirmed=true` | Não há resultado desconhecido, processo pendente nem retry cego permitido. |
| `p04-supervisor-sql-01..06/reservation.json` | Cada tentativa declara `budgetSeconds=3600`, alvo local e rollback | Reserva histórica não demonstra saldo disponível para outra tentativa. |
| `p04-supervisor-preview-20260919-01/LEDGER.md` | Declara a tentativa06 e limites, mas não saldo cumulativo, vigência, quantidade permitida ou nova reserva | Gate falhou; nenhuma ação física foi autorizada. |

O input exato para reabrir P04 é ledger/autorização efetivo e vigente que declare
`localhost`/`ETL_SISTEMA_V2_SHADOW`, rollback-only, saldo cumulativo disponível,
quantidade e escopo de tentativas, vigência e tetos de sequência, etapa, tentativa
e SQL. P05 exige, além do aceite integral P04, saldo próprio explícito.

## Evidência independente executada

| Verificação | Camada | Observado | Limite/resultado |
| --- | --- | --- |
| Inicialização Maven | Offline/JDK | `JAVA_HOME` preexistente em JDK25 recusado pelo Enforcer `[17,18)` | Falha de ferramenta anterior a testes; ambiente global não foi alterado. |
| Regressão dirigida | Surefire offline/JDK17 | `QualificationContractTest`: 7 testes, 0 falhas, 0 erros, 0 skips | Enforcer, Spotless e Checkstyle PASS; nenhuma rede, JDBC ou SQL. |
| Empacotamento | Maven offline/JDK17 | `-DskipTests package` BUILD SUCCESS | Enforcer, Spotless e Checkstyle PASS; JAR local gerado. |

O JDK17 (`17.0.20.1`) foi selecionado somente nas variáveis do processo Maven.
Não houve DDL, migration, Data Export, GraphQL, fonte real, worker, processo
filho, conexão SQL/JDBC, commit de domínio, preview/apply, deploy, agenda,
credencial, alteração do índice Git ou alteração de recibos históricos.

## P04 e P05 — critérios e camada efetivamente exercitada

| Frente | Critério original | Camada desta unidade | Situação e recibo |
| --- | --- | --- | --- |
| I | manifesto/configuração/campanha/input/processo/owner/nonce; journal, retomada/retry, cancelamento, isolamento e rollback | Somente preflight documental e regressão offline da fixture temporal | `BLOQUEADO_POR_INPUT`; nenhum novo `process.json`, journal, worker ou recibo físico. |
| J | 33 responsabilidades de preview, savepoint/rollback, ausência de apply e SQL04 vazia somente no contrato permitido | Somente preflight documental e teste offline | `BLOQUEADO_POR_INPUT`; nenhum preview/savepoint/SQL foi exercitado nesta revisão. |
| K/P05 | quatro escalas com heap, lote, linhas/bytes em voo, SQL e duração | Não alcançada | `BLOQUEADO_POR_INPUT`: P04 não foi aceita e não há saldo P05 próprio. |

As provas B–H permanecem `ACEITO_NO_ESCOPO` apenas como predecessor técnico de
P04. A correção temporal anterior permanece implementada e a regressão dirigida
passou; ela não substitui a prova física I/J. Contadores preservados: construção
39/45 (86,7%) e aceites 67/115 (58,3%).

## Alterações desta unidade

`STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md` e
`docs/catalogos/campanhas-integrais/matriz-a-n.json` passaram a distinguir P04/I–J
e P05/K como `BLOQUEADO_POR_INPUT`, sem modificar critérios, hashes, manifests,
recibos ou contadores históricos.

## Retomada imediata — até três ações

1. Receber e conferir o ledger/autorização explícito de P04, antes de qualquer
   reserva, processo, conexão JDBC ou SQL.
2. Se e somente se o gate cobrir a tentativa, reservar uma nova pasta serial e
   executar o controlador existente sob todos os limites, interrompendo na
   primeira falha nova para reconciliação.
3. Somente após todos os critérios I/J aceitos e saldo P05 próprio, executar as
   quatro escalas P05; caso contrário, manter o bloqueio e não iniciar P06–P08.
