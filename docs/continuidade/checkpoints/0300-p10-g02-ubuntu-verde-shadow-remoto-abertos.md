# Checkpoint 0300 — P10/G02 Ubuntu verde; shadow e remoto abertos

## Identificação e objetivo

- Checkpoint: 0300, 2026-09-28 03:50 UTC, P01–P33 / P10-G02.
- Anterior: `docs/continuidade/checkpoints/0299-p10-g02-fechamento-documental-gate-aberto.md`, SHA-256 `89e3757674039dc0c3e1223702b66e53cd5118eddcf3f36ca6c1371b329a4d2e`.
- Objetivo do usuário: avançar P01–P33 e fechar a parcela técnica local CI/cobertura de P10 na revisão atual, prosseguindo nas frentes independentes autorizadas.
- Estado: `TESTADO_NA_CAMADA` para Ubuntu/JAR offline; `BLOQUEADO_POR_INPUT` para prova shadow física e G02 remoto. Nenhum P recebeu novo aceite integral.
- Critérios: `STATES.md`, matriz `docs/continuidade/qualificacao-p07-p33/matriz.json` e mapa `docs/continuidade/qualificacao-p07-p33/mapa-p01-p33-20260927.md` (33 linhas, critérios originais).

## Autorização e limites

- Instrução efetiva: Supervisor ETL nesta sessão autorizou o único Builder visível a modificar arquivos e avançar P01–P33, sem push, merge, deploy, SQL fora do shadow ou sonda externa fora da allowlist. As autorizações locais e restrições de `AGENTS.md` permanecem.
- Alvo: worktree `main...origin/main` com delta preexistente preservado; espelhos privados sob `target/ci-p10-20260925-01/`. O `../CONTEXTO_GLOBAL.md` citado não foi encontrado na busca segura sob `C:/Users/lucas/OneDrive/Documentos`.
- Orçamento: nenhuma sonda Data Export/GraphQL, SQL ou publicação nesta rodada; portanto nenhum teto de fonte foi consumido. Os tempos de Maven/IT são evidência local, não renovação de orçamento operacional.
- Recuperação: alterações reversíveis só nos arquivos desta sessão; logs RED e checkpoints anteriores preservados. Não se alteraram manifests/ledgers históricos.

## Alterações e decisões

- Código/testes: instrumentação do agente JaCoCo para JAR filho de IT offline; testes sintéticos de paginação, oráculos, reconciliação, barreira/logs, sweep, capturas integrais e mapeamento CLI. O Supervisor usa uma leitura agregada injetável somente em teste, mantendo seu construtor público físico.
- Classes SQL físicas exatas em `bootstrap` foram separadas e pinadas em `docs/catalogos/ci-coverage-scope/`; `pom.xml` conserva exclusão Ubuntu/inclusão shadow simétricas e limiares 80/60. `CiCoverageScopePolicyTest` valida fonte, `.class`, pins, POM e mutações. Nenhuma lógica de admissão/projeção pura foi excluída.
- O RED v50 por `pwsh.exe` fora do `PATH` do processo Maven permanece em `clean-verify-v50-private.log`; a correção foi passar o PowerShell local apenas ao processo de teste. O primeiro Gitleaks no espelho compilado varreu `target/classes` gerado; outra cópia de varredura incluiu `.env` por engano. A cópia foi removida por caminho exato e o `.env` original permaneceu; a árvore limpa saiu sem achados.
- Hipótese não comprovada: os testes sintéticos não ratificam contratos do fornecedor, equivalência de negócio real, schema/prova JDBC física ou prontidão produtiva.

## Execução e evidência

| Passo/critério | Camada | Limite/comando sanitizado | Observado | Evidência |
| --- | --- | --- | --- | --- |
| P10 gate Ubuntu | JDK 17 / Maven local | `mvnw clean verify`, heap Maven 512 MiB, espelho v56 | exit 0; 2.348 Surefire + cinco ITs offline, zero falhas/erros, cinco skips; Spotless/Checkstyle/Enforcer e pacote PASS | `target/ci-p10-20260925-01/clean-verify-v56-private.log`, SHA-256 `9a318cd9a219f58f865a7155b2d33331a561a2fb4422b83e23985072383455d1` |
| P10 cobertura | JaCoCo XML filtrado | 80% linhas / 60% ramos, limiares intactos | 4.384/5.457 linhas (80,34%); 1.942/2.853 ramos (68,07%); check PASS | `target/ci-p10-20260925-01/candidate-repo-mirror-v56/target/site/jacoco/jacoco.xml`, SHA-256 `92c918a44b69d48d31206ada9f7c7925c2efbaf362bdd437bd7a72e1824509e8` |
| Integridade de inputs | worktree × espelho | SHA-256 de `pom.xml` e `src/` | 1.313 fontes, zero faltantes/extras/divergentes; POM igual | saída de conferência desta rodada; `git diff --check` exit 0 |
| Segurança local | scanner e Gitleaks | scanner root após documentação final; Gitleaks espelho limpo sem `target`/`.env` | scanner 4.022 candidatos, 4.021 textos, zero achados; Gitleaks exit 0, zero achados | `target/ci-p10-20260927-01/offline-secret-scan-final-private.log`; `target/ci-p10-20260925-01/gitleaks-final-private.log` |
| Continuidade | Graphify AST | `update . --no-cluster` no Python do ambiente isolado | exit 0; 37.108 nós, 95.003 arestas; crash nativo anterior diagnosticado | `target/ci-p10-20260927-01/graphify-update-v56-private.log` |
| Trilha | validador read-only | `Test-TrilhaPreparation.ps1` após documentação final | PASS, 33 estágios, 48 IDs abertos, nove pacotes, sem autorização de execução; UTF-8 estrito sem BOM nos oito arquivos críticos e `git diff --check` exit 0 | `target/ci-p10-20260927-01/trilha-validator-final-private.log` |
| Shadow | preflight sem segredo | presença de URL/processo, `sqlcmd` e serviço local | URL ausente, última definição `.env` não vazia ausente, `sqlcmd` ausente, zero serviços SQL locais em execução; nenhuma conexão ou IT | checagem sanitizada desta rodada |

- PMD fixado: `Invoke-LocalStaticAnalysis.ps1` não completou porque os JARs pinados PMD core/java/plugin estão ausentes ou divergentes no cache (`STATIC_TOOL_CACHE_MISSING_OR_DRIFT`). Não se alegou SAST integral.
- Efeitos possíveis sem confirmação: nenhum; todos os processos próprios desta unidade saíram. Nenhum SQL, DDL/DML, rede de fonte, push, merge, deploy ou cutover.
- Preservação: delta preexistente de usuário, decisões rejeitadas, logs RED, manifests e ledgers históricos intactos. P07/P08 de 22/09 continuam fotografias de bytes anteriores.
- Aceites realmente fechados: nenhum checkbox P01–P33 novo. O gate Ubuntu de P10 fechou tecnicamente nesta camada; G02 e P07/P08 integrais seguem abertos.

## Retomada imediata — até três ações

| Ordem | Ação concreta | Pré-condição | Prova esperada | Alternativa independente |
| --- | --- | --- | --- | --- |
| 1 | Provar as classes físicas e rollback no perfil `shadow-local-integration` | Owner do ambiente fornece instância `localhost`/`ETL_SISTEMA_V2_SHADOW`, URL Windows integrada válida, `sqlcmd`; confirmar nome exato no `master` e contagens antes/depois | IT shadow opt-in com duas travas, sintéticos/rollback e contagens iguais | P11 baseline nominal de Segurança quando entregue |
| 2 | Fechar G02 no novo SHA autorizado | Owner do repositório fornece provider/remote/branch, conjunto aprovado, proteções/aprovadores e autorização de publicação | histórico escaneado após fetch autorizado e três checks reais CI/Gitleaks vinculados ao SHA | P12 ambiente/TTL/restore somente com artefatos dos owners |
| 3 | Requalificar P07/P08 físico e SAST aplicável nos bytes finais | Shadow local válido e cache dos artefatos PMD pinados; respeitar ledger/limites existentes | 492 ITs físicas/rollback, pacote A–N e scanner/SAST na mesma revisão | P13 contratos reais somente após G01 e autoridade de fonte |

- Bloqueios exatos: P10/G02 remoto — owner do repositório e artefatos acima; P07/P08 shadow — owner do ambiente local e URL/instância verificáveis; P11 — aceite de baseline por Segurança; P12 — ambiente/TTL/backup/restore por DBA, Operações, Segurança e Compliance; P13 — G01 e contrato/oráculo de fornecedor/negócio. Os detalhes por P estão no mapa, sem promover fixture a contrato real.
- Condição de parada: não executar SQL fora do alvo/duas travas, sonda fora da allowlist, publicação sem autorização ou cutover sem gates.
- Condição de conclusão integral: cada critério original P01–P33 com prova na camada exigida e aceite externo próprio; percentual de cobertura isolado não conclui P10.
