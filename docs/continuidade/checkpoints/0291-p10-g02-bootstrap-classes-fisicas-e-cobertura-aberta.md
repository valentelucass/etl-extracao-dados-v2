# Checkpoint 0291 — P10/G02, classes físicas exatas e bootstrap aberto — 25/09/2026

## Autoridade, alvo e recuperação

- Anterior: [0290](0290-p10-g02-dois-gates-cobertura-bootstrap-aberto.md), SHA-256 `f3586e6b55e3e44804bcb63d7d5e05d7e1d888427457ac96836d5c6b2f5355f2`.
- HEAD local/remoto observado: `b9dac416737ac69c98d0e63715411b7c62fa03f7`; checks remotos desse SHA permanecem `verify`/`secret-scan` failure e `dependency-audit` skipped. Não houve push, merge, deploy, credencial, banco ou cutover.
- Efeitos desta unidade: somente código, POM, testes, catálogo de escopo e continuidade locais. Espelho indexado e logs privados estão em `target/ci-p10-20260925-01/`. Recuperação: revisar e reverter apenas o diff desta unidade; índice Git real, backups e manifests históricos foram preservados.

## Evidência observada

| Critério | Camada | Resultado |
| --- | --- | --- |
| Classificação causal | Fonte/POM/manifesto | 118 fontes mistas por path/hash; 19 externas de `bootstrap` e uma de `qualificacao` com rota SQL classificada, mais três classes internas exatas. `physical-sources.txt` tem 56 fontes. Os três pacotes JDBC permanecem no gate shadow, sem excluir pacotes mistos inteiros. |
| Separação de caminhos | Código/teste focal | `QualificationTemporalPolicyCatalog` preserva leitura das cinco políticas no Ubuntu. `LocalArtifactSequence` delega execução SQL à classe interna e valida relatório, fronteira, pins e sweep sem SQL. `DeclaredAnalyticSupport` delega aplicação SQL à classe interna e resolve termos financeiros pinados sem SQL. |
| Testes fracos | Revisão/teste | `SyntheticCaptureObserverTest` removido; fechamento observável de statements no teste de raster. Oráculo de localização fixa quatro raízes e hashes históricos. Teste de escopo usa mutantes reais de fonte, manifesto, classe interna, POM e limiar; teste focal passou. |
| Maven v19 | Espelho Git próprio | `verify` exit 1; 2.303 Surefire + uma Failsafe offline, zero falhas/erros, cinco skips históricos. Apenas JaCoCo `bootstrap` reprovou: após exclusões exatas, 3.422/6.703 linhas (0,51) e 1.489/3.241 ramos (0,46), abaixo de 0,80/0,60. |
| Scanner/Gitleaks v19 | Espelho limpo, indexado só nele | 3.995 arquivos indexados; scanner PASS em 3.995 candidatos, 3.994 textos e um binário verificado, zero achado/não inspecionado. Gitleaks 8.29.1 zero achados. |
| Graphify | AST local | `graphify update . --no-cluster` concluiu 801 fontes, 36.831 nós e 93.972 arestas; `graph.json` atualizado. |

O gate shadow continua com 80% linhas/60% ramos para três pacotes JDBC e classes físicas exatas. `V2_SHADOW_JDBC_URL` não está configurada no processo; IT físico não foi executado nem declarado PASS. O teto Surefire segue `-Xmx512m`. O teste Windows de symlink usa `pwsh` portátil local; o workflow Ubuntu continua sem mudança de PowerShell. O v1 de pins e o lock histórico não foram reescritos.

## Próximas ações executáveis

1. Separar SQL de `QualificationScenarioVerifier` e `QualificationCaseExecutor`, preservando e testando os invariantes puros no Ubuntu; classificar as demais classes descobertas do XML por rota concreta.
2. Cobrir os déficits puros de `bootstrap`, repetir `verify` integral nos bytes finais até cumprir 80/60, sem afrouxar JaCoCo ou o teto de heap.
3. Repetir scanner e Gitleaks em espelho indexado após as últimas mudanças; atualizar `STATES.md` antes do próximo checkpoint e trilha.

P10/G02 permanece **aberto**: falta `verify` local verde e, para aceite externo, novo SHA remoto com checks verificáveis e owner nominal. Não há CI verde em novo SHA.
