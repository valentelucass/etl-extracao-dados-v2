# B60 — Usuários SQL/JAR local

Estado: implementação e regressão offline concluídas; pacote físico em revisão
para aprovação única da seção 4. Não houve SQL físico ou JAR positivo Windows/SQL.

Ler o [pacote e limites](../../database/proposals/bloco60-local/README.md), a
[matriz de evidências](../catalogos/bloco60-local/README.md) e a
[ADR 0041](../adr/0041-origem-logica-multiprotocolo-e-usuarios-sql-local.md).
`database/proposals/bloco60-local/package.json` contém os hashes executáveis;
`docs/catalogos/bloco60-local/manifesto.json` contém a sucessão canônica exata.
Os manifestos e recibos B59 continuam imutáveis. O diff próprio e a recuperação
de arquivos estão em `target/bloco60-local/final` e nunca foram aplicados.

V024 mantém o catálogo original e acrescenta bindings explícitos por protocolo
e execução. Registrar origem existente não permite protocolo arbitrário. O
fingerprint histórico de recovery e os algoritmos SQL tipados permanecem.
Usuários ganha terminalidade preenchida, DQ tipado obrigatório e recovery
durável; páginas vazias/NULL ou ausência de auditoria/DQ impedem publicação.
O observer GraphQL conta cada tentativa efetivamente submetida ao transporte.
O contador não deriva de páginas concluídas e não registra cursor/payload/ID.

Comandos offline de conferência:

```powershell
pwsh -NoProfile -File scripts/validation/Test-Bloco60SqlContract.ps1 -SelfTest
pwsh -NoProfile -File scripts/validation/Test-Bloco60Controllers.ps1
pwsh -NoProfile -File scripts/validation/Test-Bloco60Assertions.ps1
pwsh -NoProfile -File scripts/validation/Test-Bloco60Package.ps1 -SelfTest
pwsh -NoProfile -File scripts/validation/Test-Bloco60Local.ps1 -IncludePrivateEvidence
pwsh -NoProfile -File scripts/validation/Test-Bloco60LocalGuards.ps1
```

Não executar o controlador físico, preflight, baseline ou migration antes da
aprovação do hash concreto. Após aprovação, seguir o comando fechado do pacote.
Desvio material interrompe somente o efeito dependente; readback precede
repetição. Nenhum unknown devolve saldo. O processo de encerramento restaura os
flags/grants temporários e preserva os efeitos de negócio e a evidência.

67/115 checkboxes, 48 pendentes, 191 rotas e zero AGORA permanecem. Este bloco
não fecha V2-022 pai, Q-USR-01, V2-012a/b/c, V2-047, V2-013, V2-050, V2-038 ou
cutover. Não reabre V2-033 nem atribui novo aceite às verticais históricas.
