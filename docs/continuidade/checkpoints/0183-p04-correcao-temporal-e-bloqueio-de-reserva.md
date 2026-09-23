# Checkpoint0183 — correção temporal P04 e bloqueio de reserva

## Identificação e objetivo

- Data: 2026-09-20T00:25:00Z (19/09/2026 em America/Sao_Paulo).
- Anterior: `0182-p04-diagnostico-temporal-e-progresso.md`, SHA-256
  `584209c1f25cf4e428bc942961fba559e6741aa760ca977c58bf610ed6466380`.
- Objetivo do usuário: corrigir a fixture temporal, reconciliar P03/B–H e só
  qualificar P04/I–J depois da admissão, sem iniciar P05.
- Estado: `IMPLEMENTADO_NAO_QUALIFICADO` para a correção; B–H
  `ACEITO_NO_ESCOPO` como predecessores técnicos locais; P04/I–J
  `BLOQUEADO_POR_INPUT` de reserva.

## Autorização e limites

- A instrução efetiva autoriza correção e regressão offline, e limita qualquer
  físico a localhost/`ETL_SISTEMA_V2_SHADOW`, dados sintéticos, perfil opt-in,
  rollback obrigatório e os tetos de 512MiB/240s/1800s/3600s/60s.
- Não houve DDL, migration, conexão SQL/JDBC, worker, fonte, commit de domínio,
  deploy, alteração de índice Git, limpeza ou nova reserva nesta unidade.
- `p04-supervisor-sql-01..06` estão todos `OBSERVED`, com
  `rollbackConfirmed=true` e `budgetSeconds=3600` por `reservation.json`; o
  ledger/autorização não registra saldo cumulativo ou vigência para outra prova.
  Não é seguro inferir saldo nem criar `p04-supervisor-sql-07`.
- Recuperação do bloqueio: conferir um ledger/autorização novo e explícito que
  identifique vigência e saldo disponíveis para uma tentativa P04 serial.

## Alterações e decisões

- Worktree já estava extensamente modificado e com arquivos não rastreados;
  nenhuma alteração preexistente foi descartada, movida ou restaurada.
- `QualificationPackageFixture.sequenceCampaign` trocou somente o prazo lógico
  sintético de86400 para259200 segundos. A decisão vem do planner: a janela de
  11/08 termina em12/08T03:00Z e, no tick14/08T12:00Z com catch-up1, precisa de
  prazo até15/08T03:00Z. Não muda timeout físico.
- `QualificationContractTest` passa a reproduzir o bloqueio anterior, admitir os
  três cenários P04, verificar ambas as fronteiras e recusar tick posterior.
- `P03-B-H-RECONCILIACAO-20260919.md` e `matriz-a-n.json` ligam B–H aos recibos
  e pins; os11 hashes de classes do `code-readback.json` permanecem idênticos.
  Isso admite somente a dependência técnica de P04, sem aceitar P03 agregado.

## Execução e evidência

| Passo/critério | Camada | Comando sanitizado e limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Correção temporal | Offline | Sem rede/SQL | Prazo259200 na fixture | `QualificationPackageFixture.java` |
| Regressão temporal | Surefire/JDK17 | `mvn --offline -Dtest=QualificationContractTest test` | 7 testes, 0 falhas/erros/skips; Enforcer, Spotless e Checkstyle PASS | `target/surefire-reports/TEST-br.com.esl.etl.v2.bootstrap.QualificationContractTest.xml` |
| Build do delta | Maven/JDK17 | `mvn --offline -DskipTests package` | BUILD SUCCESS; JAR criado; Enforcer, Spotless e Checkstyle PASS | saída Maven desta unidade; `target/etl-dataexport-v2.jar` |
| P03/B–H | Leitura de recibos/pins | Sem efeito | 11/11 pins atuais iguais; campanha07 e contraprova02 PASS | `P03-B-H-RECONCILIACAO-20260919.md` e `p03-corrections-20260919/` |
| Admissão física P04 | Ledger | Nenhuma reserva | BLOQUEADO_POR_INPUT: saldo/vigência ausentes | `p04-supervisor-preview-20260919-01/LEDGER.md`; `p04-supervisor-sql-01..06/reservation.json` |

Não há efeito com confirmação pendente ou processo próprio ativo desta unidade.
As falhas históricas de scanner/sucessão e `p03-regression-sql-01` permanecem
preservadas, sem reclassificação. Contadores permanecem39/45 e67/115.

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Obter e conferir saldo/vigência para P04 | Autorização/ledger explícito | saldo positivo e tentativa permitida | Nenhuma prova física P04 |
| 2 | Reservar e executar P04 serial | 1, alvo/limites/pins conferidos | I/J completos: journals, filhos, recibos, rollback e previews | Parar na primeira falha e reconciliar |
| 3 | Atualizar I/J ou bloqueio | Resultado observado | Critério integral ou nova causa delimitada | Não iniciar P05 |

- Bloqueio externo: a fonte é o ledger/autorização da campanha; o artefato
  necessário declara vigência, saldo e permissão para uma nova reserva local.
- Condição de parada: ausência desse artefato, teto atingido, falha ou resultado
  desconhecido interrompe a frente física.
- P04 só conclui com todos os critérios I/J na mesma revisão; PASS do planner ou
  a admissão de B–H não substitui worker, rollback interno ou preview transacional.
