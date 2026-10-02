# 0419 — PowerShell portátil e validadores locais

- Data: 2026-10-02. Anterior: [0418](0418-vm-qualificacao-offline-integrada-primeira-leitura.md), SHA `ACA02EB89B59E1F8B7FF3803DD4582AAD3DDDBA4ACEA432042BE3021D32FAC08`.
- Objetivo autorizado: concluir qualificações locais possíveis e preparar primeira extração. Correção ambiental independente de fonte/SQL: disponibilizar interpretador portátil para os scripts existentes e executar seus gates. Estado: `TESTADO_NA_CAMADA`; continuidade histórica `BLOQUEADO_POR_INPUT` do recibo original, sem impacto na autorização da sonda isolada.
- Antes do efeito: reserva privada `target/qualification-vm-20261002/pwsh-reservation.json` declarou diretório novo, limite 110 MiB/120 s, origem oficial, hash, ausência de SQL/PATH/serviço e recuperação sem overwrite/retry. Download/extract em alvo novo fora do Git; ZIP path traversal e expansão máxima conferidos. Não instalou serviço, alterou PATH, autenticação, credencial, banco, código ou dependência do POM.
- Fonte oficial: API pública `https://api.github.com/repos/PowerShell/PowerShell/releases/latest`, release estável `v7.6.6`; asset `https://github.com/PowerShell/PowerShell/releases/download/v7.6.6/PowerShell-7.6.6-win-x64.zip`. Nenhuma API ESL consultada.
- ZIP: **106328873 bytes**, SHA `02FE458BE20493FBDF43F61EA20610B811EE6C738AB1676C61B9CFCD1A33C860`, igual ao digest publicado. Exe: Authenticode **Valid**, signatário **Microsoft Corporation**, SHA `BFB46AF89433268872DDB43D1CA7A3F433452EE91ED356A9786940F90118E285`. Só então foi executado `-NoLogo -NoProfile`, versão observada **7.6.6**. Caminho: `C:\Users\suporte\.codex\tools\powershell-7.6.6-v2\pwsh.exe`.
- Recibo: `target/qualification-vm-20261002/pwsh-signature-receipt.json`, SHA `B3946FF20E6F61450756AAC726AD0EF87D9A3A9013FC197F42EF4A0143E9AE0E`; download e reserva adjacentes.

| Gate | Observado | Evidência |
| --- | --- | --- |
| `Test-TrilhaPreparation.ps1` | PASS, exit 0; 33 estágios, 48 IDs abertos, nove inputs; `executionAuthorized=false`, sem SQL/network/Maven | `target/qualification-vm-20261002/trail-ps7.log` |
| Scanner autoteste PS7 | PASS, exit 0, 20 casos; complementa PS5.1 de 0418 | `target/qualification-vm-20261002/scanner-selftest-ps7.log` |
| `Test-ContinuidadeAgentes.ps1` | FAIL, exit 1, `HANDOFF_PATH`; não aceito | `target/qualification-vm-20261002/continuity-ps7.log` |

O manifesto `docs/continuidade/tres-etapas/handoff/manifesto.json` exige `target/tres-etapas-20260922-01/closed-receipt.json`; verificação de presença confirmou **ausente**, predecessor versionado presente. A recusa histórica já aparecia em sessões anteriores; o input não foi recuperado nem reconstruído. Guard/pins/ledgers imutáveis não mudaram. O FAIL não impede por si só a primeira sonda 6908, que tem autorização e limites próprios.

Modificados nesta unidade somente STATES/trilha/este checkpoint/ponteiro RETOMADA. O estado e falhas 0418 foram preservados como fotografia anterior; nenhum teste Java/SQL foi repetido. Recuperação: não há estado global ou efeito SQL a reverter; artefato portátil privado permanece verificado. Nenhum processo próprio remanescente do provisionamento/gates.

Próximas ações, sem nova pergunta de rotina: (1) receber origem/tenant/dia/G01 e ratificação de teto para primeira sonda; PowerShell verificado já disponível. (2) receber escopo/local seguro da credencial sa para unidade Banco distinta; preservar Windows/integratedSecurity até decisão explícita aplicável. (3) se o owner disponibilizar o recibo histórico original, verificar seu hash e reavaliar continuidade; não inventar recibo ou repetir esse FAIL com o mesmo input. P08/produção/cutover, budgets e checkboxes permanecem inalterados.
