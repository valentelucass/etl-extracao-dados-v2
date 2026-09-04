# Como contribuir

Este repositório contém a implementação V2 em sombra. Uma contribuição não autoriza uso produtivo, acesso externo, alteração de credencial, mudança de banco, deploy, cutover ou desligamento do legado.

## Antes de alterar

1. Leia `AGENTS.md`, `STATES.md` e `../CONTEXTO_GLOBAL.md` por inteiro.
2. Escolha somente uma tarefa liberada pela ordem de execução em `STATES.md` e respeite seus gates.
3. Trate `../etl-extracao-dados` como referência read-only. Não abra nem leia repositórios de dashboards durante este projeto.
4. Preserve mudanças preexistentes e mantenha o diff pequeno, rastreável e sem formatação incidental.

Qualquer rede, segredo, banco, migration aplicada, job, serviço, remote, publicação, commit ou ação produtiva exige a autorização descrita nos documentos de governança. Perguntas e pedidos de análise não concedem essa autorização.

## Regras da mudança

- Nunca inclua segredo, payload, dado de negócio, URL sensível, cursor ou identificador real em código, teste, documentação, comando ou log.
- Separe DTOs, domínio e persistência; mantenha integrações nas bordas e cubra regras alteradas com testes.
- Registre contrato, risco, rollback e evidência aplicável. Não declare revisão, paridade, CI, deploy ou aceite sem execução real.
- Atualize `STATES.md` no mesmo bloco, marcando apenas o que foi comprovado.
- Não use `spotless:apply` como passo automático: ele é deliberado e deve ficar restrito aos arquivos realmente alterados.

## Validação local

No Windows, execute no mínimo os gates aplicáveis ao bloco:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress verify
.\scripts\security\Test-OfflineSecretScan.ps1
.\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
gitleaks dir --config .gitleaks.toml --redact=100 --exit-code=1 --no-banner .
```

Revise também links com casing exato, UTF-8, arquivos candidatos ao Git e o diff completo. Artefatos de `target/`, caches, IDEs, arquivos locais de ambiente e relatórios não entram no baseline.

## Baseline e colaboração remota

O primeiro commit só pode ser criado após autorização inequívoca. Nessa ocasião, depois de adicionar o conjunto autorizado ao index, preserve o bit executável de `mvnw`. Após criar o commit, execute imediatamente a varredura histórica:

```powershell
git update-index --chmod=+x mvnw
gitleaks git --config .gitleaks.toml --redact=100 --exit-code=1 --no-banner --log-opts="--all" .
```

Remote, push, pull request, branch protection, CODEOWNERS e qualquer afirmação de CI ativo pertencem a V2-016b e também exigem autorização explícita e evidência do provedor.
