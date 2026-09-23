# Checkpoint 0186 — P04/P05 após 0185: execução limitada

## Identificação e objetivo

- Checkpoint: 0186, 2026-09-20 UTC, macrobloco `P04-P05-APOS-0185-01`.
- Anterior: [0185](0185-preparacao-integral-trilha.md), SHA-256 `7e4c6586ffbd914db6c55052fcc383c9e2263e38e4792d957f41b34c3bab4182`.
- Objetivo: admitir P04, qualificar I/J e, somente após aceite, executar P05/K.
- Estado: `BLOQUEADO_POR_INPUT` para nova reexecução física; I/J não aceitos e K não iniciado.

## Autorização e limites

- Ordem efetiva: pedido do usuário `P04-P05-APOS-0185-01`.
- Alvo: somente `localhost/ETL_SISTEMA_V2_SHADOW`, schema104 existente, dados sintéticos, Windows integrado, rollback-only e commit de domínio bloqueado.
- Exclusões observadas: sem DDL, Flyway, fonte real, segredos, V1, produção, recovery runtime, commit, deploy ou cutover.
- Ledger: `target/macrobloco-p04-p05-apos-0185-01/ledger.json`; vigência 2026-09-20T01:40:20.5555786Z a 2026-09-22T01:40:20.5555786Z; 7.200 s reservados/consumidos em duas tentativas P04; 3.600 s restantes não são transferíveis a P05 antes de I/J.

## Alterações e decisões

- Worktree anterior preservado conforme baseline0185; nenhuma limpeza, índice Git, migration ou alteração de banco estrutural.
- `Invoke-Build.ps1`: `Physical` agora monta JAR e bibliotecas runtime com objetivos Maven direcionados antes de Failsafe, evitando `PackagePhysical` e sua suíte unitária completa.
- `QualificationSupervisor`: wildcard do classpath é concatenado como string, não passado a `Path.resolve`, que é inválido no Windows.
- `QualificationSupervisorTest`: contraprova unitária da composição de classpath. Ela não equivale à prova física do filho/JAR.
- Abordagem rejeitada: terceira tentativa P04 ou campanha P05; ambas excederiam a autorização/precedência.

## Execução e evidência

| Passo/critério | Camada | Comando/limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Preparação | offline | `Test-TrilhaPreparation.ps1` | PASS; mapa, não aceite | `target/preparacao-trilha-20260919-01/final-01/verification.json` |
| Contrato temporal/journal | offline | JDK17 Maven, 512 MiB | 11/11 PASS | relatórios Surefire locais |
| P04-01 | SQL rollback-only | Physical, 3.600 s | exit 1; rollback igual; 4 journal + 3 cancelamento + 1 concorrência + 5 sweep PASS; 11 erros pré-worker sem JAR | `.../p04-0185-01/` |
| Correção do controlador | offline | JDK17 goals `process-test-classes`, `jar:jar`, dependency copy | PASS; JAR + 8 libs + DLL | build isolado de `p04-0185-01` |
| P04-02 | SQL rollback-only | Physical, 3.600 s | JAR criado; 4/6 retomadas com erro de wildcard; step excedeu 240 s; exit -1 após interrupção somente da árvore própria; rollback igual | `.../p04-0185-02/` |
| Correção wildcard | offline | JDK17 Maven dirigido | 12/12 PASS; Enforcer/Spotless/Checkstyle PASS | `QualificationSupervisorTest`, `QualificationContractTest`, `QualificationJournalTest` |

Nenhum processo próprio permanece ativo. Os dois readbacks de agregados são iguais antes/depois. P03/B–H segue ACEITO_NO_ESCOPO apenas como predecessor técnico. Não há novo aceite, nem mudança dos contadores 39/45 e 67/115.

## Retomada imediata

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Receber autorização nova e específica para uma reexecução P04 da revisão corrigida | novo saldo/tentativa, alvo e vigência explícitos | recibo físico I/J completo e rollback | nenhuma tentativa P05 |
| 2 | Só após I/J aceitos, reservar uma campanha P05 | aceite I/J e campanha própria | quatro escalas 2/4/8/16 | nenhuma |
| 3 | Preservar as lacunas P06–P08 | não aplicável neste bloco | não alegar pacote/gate final | documentação de input externo sem efeito |

Condição de parada atingida: as duas tentativas P04 foram consumidas e falta autoridade para reexecutar a correção física. P05 permanece bloqueado por precedência.
