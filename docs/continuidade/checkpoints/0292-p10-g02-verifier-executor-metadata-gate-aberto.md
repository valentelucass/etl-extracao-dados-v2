# Checkpoint 0292 — P10/G02, separação de verifier/executor/metadata; JaCoCo aberto — 25/09/2026

## Autoridade, alvo e recuperação

- Anterior: [0291](0291-p10-g02-bootstrap-classes-fisicas-e-cobertura-aberta.md), SHA-256 `354d4d6c8d71f9b0a068aa5e10bbb9037a5c188dca47e67fba8a55c924078478`.
- HEAD local/remoto consultado: `b9dac416737ac69c98d0e63715411b7c62fa03f7`. Checks desse SHA: `verify` e `secret-scan` failure, `dependency-audit` skipped; log oficial HTTP 403. Não houve novo SHA remoto, push, merge, deploy, SQL, credencial ou cutover.
- Escopo autorizado: classes físicas exatas com fluxo SQL comprovado e pin fail-closed; classes mistas e caminhos puros no Ubuntu, 80/60 nos dois gates. Alvo dos efeitos: apenas código, POM, testes, catálogo e continuidade locais. Logs/espelhos ficam em `target/ci-p10-20260925-01/`. Recuperação: revisar/reverter apenas o diff local desta unidade; índice Git real, backups preexistentes, pins e recibos históricos foram preservados.

## Resultado observado

| Critério | Camada | Evidência e limite |
| --- | --- | --- |
| SQL separado por classe | Fonte/POM/manifesto | `QualificationScenarioVerifier$SqlVerification` e `$Evidence`, `QualificationCaseExecutor$SqlExecution` e `$AbsenceEvidence`, `QualificationPhysicalMetadata$SqlVerification` têm nomes compilados e SHA de fonte exatos no manifesto, exclusão Ubuntu e inclusão shadow. As classes externas continuam no Ubuntu. A metadata interna abre `session.getConnection()` e consulta `sys.views`/`sys.columns`; seu construtor puro permanece coberto. |
| Contratos puros | Testes Java focal | Resultado de 35 escopos/19 saídas, diferença `SQL-03` e escopo ausente; supressão seletiva de oráculos e limite de `SQL-16`; admissão de `QualificationLaboratoryMain`, `observationMode`, `entity`, cadeia de campanha alterada e comando `plan` empacotado com código/saída observáveis. Política fail-closed e cinco suítes focais passaram. |
| Maven v20 | Espelho indexado | `verify` exit 1 só JaCoCo `bootstrap`; 2.304 Surefire + uma IT offline, zero falhas/erros, cinco skips. Pós-exclusão 3.457/6.195 linhas e 1.494/3.058 ramos. |
| Maven v21 | Worktree, JDK 17, heap 512 MiB | `verify` exit 1 só JaCoCo `bootstrap`; 2.309 Surefire + uma IT offline, zero falhas/erros, cinco skips. Pós-exclusão 3.758/6.652 linhas (0,565) e 1.540/3.032 ramos (0,508), abaixo de 0,80/0,60. Não há `verify` verde. |
| Scanner/Gitleaks v21 | Espelho Git próprio, `core.longpaths=true` só ali | 3.996 arquivos indexados; scanner PASS: 3.996 candidatos, 3.995 textos, um binário verificado, zero achados/não inspecionados. Gitleaks 8.29.1 exit 0, zero achados. A árvore candidata inclui as alterações locais, sem mudar o índice real. |
| Graphify | Ferramenta local | Consulta do grafo existente funcionou; duas tentativas de `graphify update . --no-cluster` encerraram `-1073741819` sem recibo de atualização. O grafo não é evidência das mudanças v20/v21. |

`V2_SHADOW_JDBC_URL` não está no processo; o IT shadow físico **não foi executado**. O gate shadow continua configurado e exigente, sem alegação de PASS. Cinco skips são históricos. A primeira tentativa focal v21 encontrou linha Checkstyle com 141 caracteres e saiu 1; a correção mínima passou na repetição focal e no `verify` integral até o JaCoCo. O erro local foi preservado no log privado.

## Retomada imediata

1. Ranquear as classes ainda descobertas do XML v21; em `QualificationSupervisor`, runtimes mistos e CLI wrappers, testar saídas/decisões puras e separar apenas métodos/classes cuja rota SQL esteja comprovada. Não classificar por nome `Main` ou chamada isolada a `openFromEnvironment`.
2. Atualizar pins fail-closed de cada separação e medir delta por classe; repetir `verify` sem reduzir 80/60, 512 MiB ou transformar skips em PASS.
3. Repetir scanners no espelho indexado dos bytes finais; sincronizar `STATES.md` antes de novo checkpoint/trilha.

P10/G02 permanece **aberto**: falta `verify` local verde e, para aceite externo, novo SHA com checks remotos verificáveis e owner nominal. Sem autorização para publicar.
