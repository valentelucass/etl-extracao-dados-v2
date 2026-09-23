# V2-015d — implementação local fail-closed

## Identidade e estado

- Data local: `2026-09-06` (`America/Sao_Paulo`).
- Rota/bloco: `G05T`, Bloco 43.
- `MODELO_RECOMENDADO=TERRA_XHIGH | MODELO_EXECUTADO=SOL_ULTRA | MOTIVO=LOTE_UNICO_AUTORIZADO_PELO_OWNER`.
- Branch: `main`.
- HEAD de referência preservado: `0b910432f12d81a306072e24aa44885da94c62a1`.
- Estado final da retomada: **concluído localmente; gate Maven final verde**.
- Resultado concedido à subfatia local: `LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING`.

## Implementação aplicada

- `pom.xml` liga o perfil ao fingerprint bruto `83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056`.
- O threshold versionado e o valor consumido pelo plugin são `0.0`.
- Três regras do Maven Enforcer recusam drift do threshold versionado, override do valor consumido e drift do fingerprint.
- `failOnError=true`, relatórios JSON/HTML e `NVD_API_KEY` exclusivamente por ambiente foram preservados.
- O workflow agendado/manual valida policy/configuração antes do Maven, usa `clean verify`, valida o JSON depois com `if: always()` e preserva o diretório também com `if: always()`.
- `if-no-files-found` passou de `warn` para `error`; `continue-on-error`, `-D` de threshold, skip e suppression não governada são recusados pelo validator.
- O parser confina o path ao relatório canônico, exige HTML não vazio, UTF-8 sem BOM, JSON/schema fechado, coleções reais, ausência de analysis exceptions/suppressed vulnerabilities, PURL Maven única, score válido e decisão fail-closed pelas exceções versionadas.
- O README principal passou a descrever o perfil como opt-in e fail-closed, sem alegar execução real ou baseline aceita.

## RED físico herdado do Bloco 42

Antes da correção, o comando abaixo terminou com `exit code 1` e 11 divergências. Depois do endurecimento adicional do validator, ainda antes de tocar no POM/workflow, terminou com `exit code 1` e 12 divergências:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DependencyVulnerabilityPolicy.ps1 -VerifyImplementation
```

As 12 razões finais do RED foram:

```text
POM_THRESHOLD_PROPERTY_NOT_ZERO
POM_THRESHOLD_OVERRIDE_GUARD_MISSING
POM_POLICY_FINGERPRINT_MISMATCH
POM_FAIL_BUILD_ON_CVSS_NOT_ZERO current=11
POM_ENFORCER_GUARD_MISSING property=dependency.vulnerability.failBuildOnCVSS
POM_ENFORCER_GUARD_MISSING property=failBuildOnCVSS
POM_ENFORCER_GUARD_MISSING property=dependency.vulnerability.policy.sha256
WORKFLOW_IMPLEMENTATION_VALIDATION_STEP_MISSING
WORKFLOW_MACHINE_REPORT_VALIDATION_STEP_MISSING
WORKFLOW_MACHINE_REPORT_VALIDATION_NOT_ALWAYS
WORKFLOW_ARTIFACT_MISSING_BEHAVIOR_NOT_ERROR current=warn
WORKFLOW_DEPENDENCY_AUDIT_COMMAND_INVALID
```

Fecho exato: `IMPLEMENTATION_VERIFICATION_FAILED count=12`.

## GREEN físico e harness sintético

Após a correção, policy e implementação terminaram com `exit code 0`:

```text
PASS: dependency vulnerability policy catalog validated; fingerprint=sha256:83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056 synthetic_cases=33 passing=3 blocking=30 effective_exceptions=0.
PASS: dependency vulnerability policy and physical implementation validated; synthetic_cases=33 blocked=30 effective_exceptions=0.
```

O modo de relatório foi exercitado somente com arquivos temporários inequivocamente sintéticos sob `target/dependency-check/`:

1. JSON limpo sem HTML: `exit code 1`, `HUMAN_REPORT_MISSING`.
2. JSON+HTML limpos: `exit code 0`, zero dependências, vulnerabilidades e exceções.
3. Uma vulnerabilidade `SYNTH-VULN-0001`, PURL `synthetic.example`, score `0.0/NONE`: `exit code 1`, `MACHINE_REPORT_BLOCKED reason=UNEXCEPTED_VULNERABILITY`.
4. Restauração do relatório limpo: `exit code 0`.

O `clean` Maven posterior removeu esses arquivos temporários; eles nunca foram findings ou evidência real.

## Primeira suíte Maven final — RED histórico

O Java global era 25.0.2. Foi selecionado explicitamente o JDK local já configurado `17.0.20.1`, sem download. Executou-se exatamente uma vez, offline e sem o perfil `security-audit`:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
```

O Enforcer passou Java, Maven, as três novas regras `requireProperty`, convergência e upper bounds. Spotless passou 528 arquivos e Checkstyle registrou zero violações. A suíte, porém, terminou `BUILD FAILURE`, `exit code 1`, com:

```text
Tests run: 656, Failures: 1, Errors: 0, Skipped: 1
CharacterizationOfflineBoundaryTest.leavesEveryEntityQ01AndEveryRealCharacterizationStageOpen:103 expected: <false> but was: <true>
```

A causa era uma asserção documental preexistente que excluía `Q-FND-01` do conjunto de caracterizações por entidade, mas não excluía a rota administrativa `Q-BST-01` concluída no Bloco 41. O teste estava em arquivo não rastreado preexistente da árvore suja. A menor correção preservou todo o conteúdo e ampliou apenas o lookahead de `FND` para `FND|BST`.

Sem repetir Maven, o arquivo corrigido foi compilado diretamente com `javac` do JDK 17 e o método antes falho foi invocado por reflexão contra o classpath já produzido. A primeira compilação diagnóstica sem `-encoding UTF-8` distorceu o travessão e falhou numa asserção posterior; repetida com `-encoding UTF-8`, terminou:

```text
PASS: targeted characterization boundary regression.
```

Essa prova dirigida confirmou a correção, mas não transformou a suíte Maven final vermelha em verde naquele lote. Em obediência ao limite então vigente de exatamente uma suíte Maven, não houve segunda invocação e G05T/Bloco 43 permaneceu aberto até nova autorização.

## Retomada autorizada — GREEN e fechamento

Após a autorização explícita do owner para completar o bloco no mesmo chat, o estado corrigido foi novamente validado com o mesmo comando, ainda offline e sem o perfil `security-audit`, sob Temurin 17.0.20.1:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
```

A execução terminou às `2026-09-06T14:30:18-03:00`, com `exit code 0` e:

```text
Tests run: 656, Failures: 0, Errors: 0, Skipped: 1
BUILD SUCCESS
```

As sete regras Enforcer passaram, inclusive as três guardas `requireProperty`; Spotless manteve 528 arquivos limpos, Checkstyle registrou zero violações e todos os checks de cobertura JaCoCo foram atendidos. A regressão `CharacterizationOfflineBoundaryTest` passou dentro da suíte completa. Essa prova concede somente a G05T o resultado `LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING`; não executa nem aceita a baseline real G05.

## Gates adicionais

Antes da suíte Maven passaram: policy, implementação física, relatório sintético limpo, manifesto de schema, validator intermediário da trilha, self-test do scanner em nove casos, varredura offline de 878 candidatos/877 textos/um binário com zero finding, UTF-8 estrito sem BOM em 14 arquivos e `git diff --check`.

Antes da atualização documental de fechamento também passaram policy,
implementação física, manifesto de schema e a regressão Java dirigida. O
validator da trilha ainda refletia corretamente o estado histórico aberto, com 45
marcos, 205 fatias abertas, 45/102 checkboxes e G05T/Bloco 43 como única rota
`AGORA`. O self-test passou nove casos; a varredura offline passou com 879
candidatos, 878 textos, um binário verificado e zero finding; 16 arquivos passaram
UTF-8 estrito sem BOM e `git diff --check` passou.

Após sincronizar `STATES.md`, a trilha e seu validator, policy e implementação
física passaram novamente com 33 casos sintéticos, três PASS, 30 BLOCK e zero
exceção efetiva; o manifesto de schema também passou. O validator final da trilha
confirmou 46 marcos, 204 fatias abertas, 46/102 checkboxes e nenhuma rota `AGORA`,
com Bloco 44 não atribuído. O self-test final passou nove casos e a varredura
offline final examinou 879 candidatos, 878 textos e um binário verificado, sem
finding, oversized ou conteúdo não inspecionado; os 16 arquivos passaram UTF-8
estrito sem BOM e `git diff --check` passou.

## Arquivos da implementação

- `pom.xml`
- `.github/workflows/security.yml`
- `README.md`
- `scripts/validation/Test-DependencyVulnerabilityPolicy.ps1`
- `src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/CharacterizationOfflineBoundaryTest.java` (arquivo não rastreado preexistente; uma asserção corrigida)
- `docs/runbooks/v2-015d-implementacao-local-fail-closed-sol.md`
- `STATES.md`
- `docs/runbooks/trilha-de-chats-gpt-5-6.md`
- `scripts/validation/Test-Gpt56ChatTrail.ps1`

Os oito artefatos do Bloco 42 permanecem listados no runbook próprio.

## Limites e próximo passo

Não houve rede, internet, API, `curl`, NVD/feed, execução do Dependency-Check, perfil `security-audit`, dependência/finding/ID real, suppression efetiva, secret/credencial, leitura de `.env`, banco, SQLCMD, migration, produção, CI remoto, release ou deploy. Não houve instalação ou atualização de dependência, commit, push, reset, checkout, stash ou limpeza da árvore.

G05T está concluída no Bloco 43 e não se atribui Bloco 44. Nenhuma rota local permanece elegível para `STATUS=AGORA`; o próximo avanço exige nova evidência/autorização para um hold real. G05 integral continua `EXTERNAL_HOLD` para feed/NVD autorizado, aceite nominal, classificação de findings/exceções e primeira baseline real.
