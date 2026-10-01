# Checkpoint 0295 — P10/G02, cadeia SQL do Supervisor; gate aberto — 25/09/2026

## Autoridade, alvo e limites

- Anterior: [0294](0294-p10-g02-supervisor-cli-offline-cobertura-aberta.md), SHA-256 `2417405cf0d216616d43674c5e2bfd65393d69ce387b3b12e0fa76c0f3a2f4fc`.
- Instrução: rastrear chamadas/linhas SQL, extrair apenas colaboradores físicos de classes mistas, manter as decisões puras no Ubuntu, exigir pin exato, inclusão shadow e contraprova fail-closed, medir delta e repetir gates.
- Base remota: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. Alvo de escrita: apenas worktree do projeto e espelhos privados em `target/ci-p10-20260925-01/`; índice Git real, backups, banco e provedor não alterados. Recuperação: revisar/reverter apenas os deltas desta unidade; sem push, merge ou deploy.

## Mudança causal

O XML limpo v23 localizou `QualificationSupervisor.runChild` em 0/85 linhas e 0/28 ramos; a rota chama `QualificationSqlEvidence.master` (0/17, 0/12) e `snapshot` (0/22, 0/14), alcançando `DriverManager`, sessão JDBC e worker empacotado. A classe interna exata `QualificationSupervisor$SqlChildExecution` passou a conter essa execução. O Supervisor externo conserva admissão, journal, status, recibo, validação e retomada no gate Ubuntu; `reconcile` ainda mistura uma rota SQL e não foi excluído.

O guarda puro de duas travas saiu de `QualificationSqlEvidence.master` para `QualificationSqlOptIn.require`, testado nas quatro combinações. Os métodos úteis restantes de `QualificationSqlEvidence` abrem conexão SQL ou consultam metadata/sessão; essa classe exata foi pinada no manifesto. O POM exclui as duas classes físicas somente do gate Ubuntu e as inclui no check shadow. O teste de política reprovou mutantes que retiram, separadamente, cada include shadow, além dos mutantes já existentes de classe nova/alterada sem classificação. O catálogo [bootstrap-v21-ranking](../../catalogos/ci-coverage-scope/bootstrap-v21-ranking.md) contém o traço por método e consumidor. O teste offline do Supervisor continuou passando, sem alegação de rollback SQL real.

## Evidência executada

| Critério | Camada | Observado e limite |
| --- | --- | --- |
| Foco e política | Maven JDK 17, heap 512 MiB | Testes focais, duas ITs offline e contraprovas de POM passaram após correção de Spotless apenas no teste de política. |
| Build final v25 | Espelho fresco do HEAD+overlay final, Git indexado somente nele | `clean verify` exit 1 **somente JaCoCo bootstrap**: 2.314 Surefire + duas Failsafe offline, zero falhas/erros, cinco skips históricos. Demais gates Maven passaram. Cobertura filtrada 3.642/5.966 linhas (0,610) e 1.594/2.964 ramos (0,538), abaixo de 80/60. Log privado `target/ci-p10-20260925-01/clean-verify-v25-private.log`. |
| Delta v23→v25 | XML JaCoCo limpo | +3 linhas/+4 ramos cobertos e -128 linhas/-52 ramos no denominador. Supervisor externo 175/348 linhas, 70/212 ramos; filho SQL 0/89, 0/28; `QualificationSqlOptIn` 3/3, 4/4; `QualificationSqlEvidence` 0/48, 0/24. Faltam 1.131 linhas e 185 ramos no bootstrap filtrado. |
| Segurança final | Mesmo espelho indexado, 4.004 arquivos | Scanner PASS: 4.004 candidatos, 4.003 textos, um binário, zero achados/não inspecionados; Gitleaks 8.29.1 exit 0, zero achados. Logs privados `scanner-v25-private.log` e `gitleaks-v25-private.log`. |
| Shadow físico | Perfil opt-in separado | **Não executado**: `V2_SHADOW_JDBC_URL` ausente. O check estrutural preserva as travas localhost/`ETL_SISTEMA_V2_SHADOW`, Windows integrado, sintéticos, rollback e limiares; não prova execução física. |

Não houve resposta perdida nem efeito externo. O crash anterior de `graphify update` permaneceu diagnóstico local e não foi reintentado sem causa nova. `STATES.md` foi sincronizado antes deste checkpoint. Nenhum critério de aceite G02 foi marcado: falta `verify` local verde e novo SHA/checks remotos verdes/owner nominal.

## Retomada imediata

1. Inspecionar `LocalAnalyticUsersRuntime`, `LocalRelationalRuntime` e oráculos por método/consumidor no XML v25; preservar `observationMode`, `entity` e decisões puras no Ubuntu, e separar somente corpos JDBC com pin exato, include shadow e mutantes.
2. Medir o delta por classe, executar `clean verify` fresco e scanners nos bytes finais; manter 80/60 e heap 512 MiB.
3. Sincronizar `STATES.md` antes do próximo checkpoint/trilha. Shadow IT segue não executada; G02 aberto, sem publicação.
