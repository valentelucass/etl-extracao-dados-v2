# Checkpoint 0197 — P04/P05 pós-0196, preflight bloqueado

## Identificação e objetivo

- Checkpoint: 2026-09-20T21:06:48Z; ordem `P04-P05-POS0196-20260920-01`.
- Anterior: `0196-p02-diagnostico-causal-pos0194.md`, SHA-256
  `e375e230a7f73bf7c6fce923f8786b1fc0bdaa0866303442bcbf66af8c8d5edf`.
- Objetivo do usuário: corrigir/provar guard e asserção offline, qualificar I/J
  e executar K apenas depois do aceite integral.
- Estado: **PREFLIGHT_BLOCKED_NO_PHYSICAL_RESERVATION**; I/J continuam
  `IMPLEMENTADO_NAO_QUALIFICADO`; K/P05 não é elegível.
- Critérios: prompt efetivo do usuário, SEQ-03/SEQ-06 em
  `docs/catalogos/campanhas-integrais/CONTRATO.md` e matriz A–N.

## Autorização e limites

- A instrução efetiva autoriza duas P04 e uma P05, serialmente, somente após
  preflight e com P05 posterior ao aceite I/J. DDL, fonte, produção e P06–P08
  permanecem proibidos.
- Ledger criado antes da correção e dos testes: vigência
  2026-09-20T20:38:18Z–2026-09-22T20:38:18Z; máximo10800s; P04/P05 reservados
  **0s**. Alvo físico, se admitido, seria somente localhost/
  `ETL_SISTEMA_V2_SHADOW`; não houve conexão com ele nesta unidade.
- Tetos preservados: campanha3600s, sequência1800s, etapa240s, SQL60s e
  heap512MiB. O preflight offline do controlador observou o teto240s e retornou
  timeout conhecido; não houve continuação apresentada como PASS.

## Alterações e decisões

- Inventário Git inicial era amplamente alterado e não relacionado; foi
  preservado. Não houve reset, limpeza, commit ou push.
- `Invoke-Build.ps1`: única mudança funcional, a raiz de busca dos XMLs de
  `$sourceRoot` para `$build`.
- Novo `QualificationSequenceSupervisorAssertionTest`: suporte somente de teste
  que chama as asserções privadas reais por reflexão, sem fixture/supervisor/JDBC.
- A matriz efetiva do guard confirmou a correção sem aceitar XMLs antigos ou o
  exit do wrapper. O recibo histórico5XML continua falho no supervisor.
- Hipótese não comprovada: a causa interna da execução longa de
  `PackagedFixtureBindingIT`. Há evidência de seleção Failsafe com JAR como
  classe explodida e de timeout canônico, mas não se atribui a causa ao runtime
  sem diagnóstico dirigido. Nenhuma P04 é reservada por esperança.

## Execução e evidência

| Passo/critério | Camada | Comando sanitizado e limites | Observado | Evidência |
| --- | --- | --- | --- | --- |
| Mapa da trilha | offline | `Test-TrilhaPreparation.ps1 -SelfTest` | PASS: 1 positivo,24 negativos; sem SQL/rede/Maven | log do turno; ledger |
| Guard corrigido | offline | `Invoke-GuardContraprobes.ps1` | PASS:10 contraprovas; bloco exato; histórico recusa127 | `guard-contraprobes.json`, SHA `8e950e…92932` |
| Asserção SEQ-06 | offline/JDK17 | `jacoco:prepare-agent surefire:test -Dtest=QualificationSequenceSupervisorAssertionTest` | PASS:2/2; quatro mutações recusadas | Surefire XML; teste SHA `9e137b…80801` |
| Formato/compilação | offline/JDK17 | `spotless:check test-compile`, teto240s | PASS; Checkstyle e javac17 | log Maven do turno |
| Binding Failsafe | offline/JDK17 | seleção somente `PackagedFixtureBindingIT` | 2 testes,1 falha: fingerprint JAR=classe alegada explodida | `binding-stdout.log`, XML Failsafe |
| Preflight canônico | offline/JDK17 | controlador `ArtifactDirected`, heap512MiB,240s | `exit124`, `timedOut=true` durante binding; logs íntegros | `p04-p05-pos0196-preflight-01/result.json`, SHA `2a3796…cf5c3` |

O processo Surefire manual foi identificado como próprio e contido por árvore
após260.554s; nenhum processo desta ordem restou. A contenção não gera recibo
de teste nem reserva física. Logs e artefatos ficam em
`target/P04-P05-POS0196-20260920-01/`; o ledger SHA-256 é
`74e3eef075e57a0a1489f2c099ec6e66a042433f3f97d35d8abd53744eb002d2`.

Aceites fechados: nenhum. O guard e a asserção estão testados na camada offline,
mas não qualificam I/J; K não iniciou.

## Retomada imediata

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente autorizada |
| --- | --- | --- | --- | --- |
| 1 | Diagnosticar o binding/preflight com uma sonda offline delimitada | preservar recibos, teto e bytes atuais | causa reproduzida e correção proporcional | nenhuma campanha física |
| 2 | Reexecutar somente o preflight exato após correção | prova offline da correção PASS | exit0, XML íntegro e revisão congelada | encerrar sem P04 se falhar |
| 3 | Reservar P04-01 | somente se o passo2 passar, vigência/saldo conferidos | recibo físico integral e rollback | não executar P05 sem I/J |

Bloqueio: preflight exato não concluído; o desbloqueio é uma correção causal
provada offline. Condição de parada: timeout, resultado desconhecido, vigência
encerrada, deriva ou risco de integridade. P05 somente após todos os critérios
I/J, não por saldo ou prova parcial.
