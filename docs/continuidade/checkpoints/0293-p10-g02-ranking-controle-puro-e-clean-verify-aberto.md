# Checkpoint 0293 — P10/G02, ranking causal e build limpo ainda vermelho — 25/09/2026

## Autoridade, alvo e recuperação

- Anterior: [0292](0292-p10-g02-verifier-executor-metadata-gate-aberto.md), SHA-256 `ac9774e66be1ed7c96667bcf69752abba71fe942d16e930974da83fc6c536038`.
- Instrução efetiva: ranquear classes `bootstrap` por perda de linhas/ramos, rota, consumidor e teste; cobrir comportamento puro observável, classificar SQL apenas com fluxo/pin/check shadow, preservar 80/60 e G02 aberto.
- HEAD local/remoto de base: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. Sem novo SHA remoto, push, merge, deploy, SQL, credencial ou cutover. Efeitos apenas em código, teste, POM, manifesto, ranking e continuidade locais. Logs e espelho isolado em `target/ci-p10-20260925-01/`; recuperação por diff da unidade, sem tocar índice Git real, backups e pins históricos.

## Evidência observada

| Critério | Camada | Observado e limite |
| --- | --- | --- |
| Ranking causal | XML v21 + fonte/chamadores/testes | [Catálogo](../../catalogos/ci-coverage-scope/bootstrap-v21-ranking.md) classifica as 12 maiores classes incluídas, distinguindo decisões puras de SQL físico. `QualificationSupervisor`, runtimes mistos e `QualificationLaboratoryMain` continuam no Ubuntu. |
| Cancelamento puro | Teste offline + JaCoCo limpo | Quatro testes de `QualificationCaseControlOfflineTest` provam barreira exata, nonce, motivo, prazo e falha fechada sem sessão. Classe passou de zero no v21 para 64/100 linhas e 18/34 ramos no v22; o ramo JDBC `DURING_CAPTURE` segue no IT shadow não executado. |
| Frete sintético vs JDBC | Fonte/teste/manifesto/POM | `AnalyticFreightScenarioData` carrega recurso limitado e aplica revisão/correção; teste passa pelo mapper tipado, confere datas/km e recaptura imutável. Classe pura 14/18 linhas, 6/8 ramos. `AnalyticScenarioEnrichment` restante apenas pagina e vincula via JDBC, tem pin exato, exclusão Ubuntu e inclusão shadow; 0/77 linhas sem SQL. Política fail-closed focal passou. |
| Build fresco v22 | Espelho criado do HEAD exato + overlay, JDK 17, 512 MiB | `clean verify` exit 1 exclusivamente JaCoCo `bootstrap`; 2.313 Surefire + uma Failsafe offline, zero falhas/erros e cinco skips históricos. Pós-exclusão: 3.587/6.094 linhas (0,589) e 1.564/3.016 ramos (0,519), abaixo de 0,80/0,60. Os números incrementais v21 não são comparador causal direto. |
| Segurança v22 | Mesmo espelho indexado antes do build | 4.001 arquivos indexados só no mirror com `core.longpaths=true`; scanner PASS 4.001 candidatos/4.000 textos/um binário, zero achado/não inspecionado; Gitleaks 8.29.1 exit 0, zero achados. |

O build focal inicialmente falhou por formatação Spotless no teste novo e no arquivo movido; ambos os ajustes foram feitos e a repetição focal passou. O perfil `shadow-local-integration` conserva localhost/`ETL_SISTEMA_V2_SHADOW`, Windows integrado, sintéticos e rollback; sem `V2_SHADOW_JDBC_URL`, o IT físico **não foi executado** nem declarado PASS. Graphify update não foi repetido após os dois crashes v21; a falha local não bloqueou Maven/scanners.

## Decisão e retomada imediata

O check Ubuntu ainda precisa de 1.289 linhas cobertas e 246 ramos com o denominador limpo atual. Testes superficiais não sustentariam o aceite. O desenho aprovado continua: preservar 80/60, testar decisões offline de campanha/CLI por saída e estado observáveis, e isolar apenas blocos SQL causais em classes exatas com pin fail-closed e obrigação no shadow. Nenhuma exclusão por nome `Main` ou chamada isolada a `openFromEnvironment`.

1. No XML limpo v22, atacar `QualificationSupervisor` por transições offline de `status`/`reconcilePending`/recibo e investigar `AnalyticLaboratoryMain`/wrappers mantendo rejeição pura no Ubuntu; medir delta por classe.
2. Separar execuções SQL dos runtimes mistos e oráculos somente com fluxo concreto, atualizar manifesto/POM/política, repetir teste focal e `clean verify` fresco sem reduzir 80/60/512 MiB.
3. Rodar scanners no espelho indexado dos bytes finais e sincronizar `STATES.md` antes do próximo checkpoint/trilha.

P10/G02 permanece **aberto**: falta `verify` local verde e, para aceite externo, novo SHA com checks remotos verificáveis e owner nominal. Sem autorização para publicar.
