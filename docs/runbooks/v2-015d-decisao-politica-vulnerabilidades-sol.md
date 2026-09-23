# V2-015d — decisão local fail-closed de vulnerabilidades

## Identidade da execução

- Data local: `2026-09-06` (`America/Sao_Paulo`).
- Rota/bloco: `G05A`, Bloco 42.
- Modelo: GPT-5.6 Sol Ultra.
- Resultado: `POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING`.
- Branch: `main`.
- HEAD de referência preservado: `0b910432f12d81a306072e24aa44885da94c62a1`.
- Árvore: amplamente suja antes deste bloco; todas as alterações preexistentes foram preservadas.

## Escopo e decisão

Este bloco congela somente o contrato local de segurança. O threshold permitido é exclusivamente `0.0`; `11`, valores acima de `10`, negativos, vazios, não numéricos, `NaN` simbólico e overrides não governados são contraprovas bloqueadas. Qualquer vulnerabilidade não exceptuada bloqueia inclusive com score `0`; score ausente, severidade ausente/desconhecida, erro do scanner, relatório ausente/malformado/inesperado e resultado parcial também bloqueiam.

JSON é o contrato de máquina e HTML é somente o artefato humano. `failOnError=true`, ausência de artefato como erro, nenhuma continuação permissiva e chave NVD somente via ambiente são obrigatórios. O arquivo de exceções começa vazio e está ligado ao fingerprint da policy; exceções sintéticas exercitam match exato, justificativa, owner-papel, expiração UTC, escopo e fingerprint.

Fingerprint da política congelada:

```text
sha256:83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056
```

## RED obrigatório antes do catálogo

Comando:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly
```

Resultado legítimo antes da materialização, `exit code 1`:

```text
DEPENDENCY_VULNERABILITY_POLICY status=FAIL reason=POLICY_CATALOG_OR_MANIFEST_MISSING files=README.md,politica-v01.json,excecoes-v01.json,fixtures/casos-v01.synthetic.json,manifesto.json,manifesto.sha256
```

Uma primeira invocação anterior a esse RED encontrou `ParserError` na linha 971 do próprio validator (`You must provide a value expression following the '-and' operator.`), também com `exit code 1`. O agrupamento da chamada booleana foi corrigido; esse defeito do harness não foi usado como contraprova da policy.

## GREEN da decisão

Depois da materialização do catálogo, o mesmo comando terminou com `exit code 0`:

```text
PASS: dependency vulnerability policy catalog validated; fingerprint=sha256:83c8698663f33b0e79ac987d72c7853fb59b12af42f31b07d1809377a454e056 synthetic_cases=33 passing=3 blocking=30 effective_exceptions=0.
```

Durante o red/green do próprio harness foram encontrados e corrigidos, sem afrouxar o contrato:

- `FIXTURE_IDENTITY_INVALID`: o parser convertia timestamps ISO em `DateTime`; a leitura passou a preservar datas JSON como strings.
- `The property 'Name' cannot be found on this object.`: enumeração vazia sob `StrictMode`; a coleta de nomes passou a ser explícita.
- `FIXTURE_INPUT_SCHEMA_NOT_CLOSED`: colisão com a variável automática PowerShell `$input`; o parâmetro passou a se chamar `EvaluationInput`.
- a chave estrutural `nvdApiKeySource` acionaria a regra genérica de atribuição sensível do scanner offline; foi renomeada para `nvdCredentialSource`, sem allowlist e sem valor de credencial.

Todos esses ensaios terminaram com `exit code 1`; somente o resultado acima é o GREEN aceito.

## RED físico reservado ao Bloco 43

Comando executado ainda sem alterar `pom.xml` ou workflow:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DependencyVulnerabilityPolicy.ps1 -VerifyImplementation
```

Uma primeira execução revelou a falha interna `Cannot bind argument to parameter 'Failures' because it is an empty collection.`, `exit code 1`; o parâmetro recebeu suporte explícito à coleção inicialmente vazia. A nova execução produziu o RED físico válido, `exit code 1`, com exatamente 11 divergências reais:

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
```

Fecho do comando:

```text
DEPENDENCY_VULNERABILITY_POLICY status=FAIL reason=IMPLEMENTATION_VERIFICATION_FAILED count=11
```

Esse RED é a entrada de implementação de `G05T`, não uma falha do Bloco 42.

## Gates documentais de fechamento

- `Test-DependencyVulnerabilityPolicy.ps1 -PolicyOnly`: `exit code 0`, 33 casos, três PASS, 30 BLOCK, zero exceção efetiva.
- `Test-Gpt56ChatTrail.ps1`: `exit code 0`, 45 marcos históricos, 205 fatias abertas, 45/102 checkboxes e uma única rota `AGORA`, G05T/Bloco 43.
- `Test-OfflineSecretScan.ps1`: `exit code 0`, nove casos.
- `Invoke-OfflineSecretScan.ps1 -Source .`: `exit code 0`, 878 candidatos, 877 textos, um binário verificado e zero finding.
- Auditoria UTF-8 estrita sem BOM: 11 arquivos da campanha aprovados.
- `git diff --check`: `exit code 0`.
- Diff delimitado de `pom.xml`, `security.yml` e `ci.yml`: vazio durante todo o Bloco 42.

## Artefatos entregues

- `docs/catalogos/vulnerabilidades-v2-015d/README.md`
- `docs/catalogos/vulnerabilidades-v2-015d/politica-v01.json`
- `docs/catalogos/vulnerabilidades-v2-015d/excecoes-v01.json`
- `docs/catalogos/vulnerabilidades-v2-015d/fixtures/casos-v01.synthetic.json`
- `docs/catalogos/vulnerabilidades-v2-015d/manifesto.json`
- `docs/catalogos/vulnerabilidades-v2-015d/manifesto.sha256`
- `docs/runbooks/v2-015d-decisao-politica-vulnerabilidades-sol.md`
- `scripts/validation/Test-DependencyVulnerabilityPolicy.ps1`

## Limites e handoff

Não houve rede, internet, API, `curl`, NVD/feed, Dependency-Check, perfil `security-audit`, dependência real, finding real, credential, token, valor de secret, leitura de `.env`, banco, SQLCMD, migration, produção, CI remoto, release ou deploy. Nenhuma suppression efetiva foi criada. O catálogo não é baseline aceita e não representa aceite nominal. Maven não foi executado neste bloco.

O handoff permitido é somente `G05T`, Bloco 43: aplicar mecanicamente a decisão no POM/workflow e tornar verde a verificação física com fixtures sintéticas. Feed/NVD, findings, exceções reais, aceite e primeira baseline permanecem em `G05/EXTERNAL_HOLD`.
