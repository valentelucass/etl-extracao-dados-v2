# V2-047 — Fundação offline de planejamento de bootstrap

Este runbook executa somente `Q-BST-01`, Bloco 41, fatia
`FUNDACAO_PLANEJAMENTO_OFFLINE` de V2-047. O único resultado positivo permitido é
`FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED`.

Ele não executa `Q-*-01`, `Q-*-02`, fonte, banco, API, migration ou bootstrap. O modo é
exclusivamente local e documental; `BOOTSTRAP` aparece como namespace/contrato futuro, não como uma
operação realizada neste bloco.

## Preflight local

1. Confirmar o repositório e preservar a árvore suja existente.
2. Confirmar que V2-017 e V2-019 estão concluídas no `STATES.md`.
3. Confirmar os fingerprints dos contratos e identidades das seis entidades elegíveis.
4. Recusar qualquer tentativa de obter `T0`, `Tcut`, `source_instance`, `tenant_scope`, endpoint,
   principal ou horizonte reais durante esta fatia.

Não ler `.env`, não inspecionar credenciais e não usar rede ou banco nem mesmo para “preflight”.

## Gate vermelho e gate verde

Antes dos artefatos, o validator deve falhar porque o manifesto não existe:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-V2047BootstrapPlanningCatalog.ps1
```

Depois de criar ou alterar o catálogo, executar o mesmo comando até obter `PASS`. O gate valida
UTF-8 estrito, JSON sem duplicatas, SHA-256, bindings canônicos, 17 linhas de matriz, vocabulários
fechados e todos os casos sintéticos.

## Invariantes obrigatórias

- Modelo interno `[start,endExclusive)`; snapshot até `T0`; delta `(T0,Tcut]`; `Tcut > T0`.
- Bordas da fonte continuam `Q01_REQUIRED`; nenhuma tradução inclusiva é inventada.
- Partições-base positivas/adjacentes; gap e overlap silencioso são recusados.
- Late data usa replay separado e limitado.
- Ledger e modo futuros são `BOOTSTRAP`; watermark incremental não sofre efeito.
- Tuple escopada, source key type-tagged e canonical ID preservado pelo registry.
- Sem surrogate legado como canonical ID, rekey silencioso, filho órfão ou relação V2-046 antecipada.
- Cotações usa somente referência explícita do mesmo escopo; Usuários não inventa temporalidade nem
  desativa por ausência.
- Reconciliação emite somente contagens/digests agregados sanitizados.
- Rollback transacional por partição; sem hard delete, down migration ou alteração do legado.

## Manutenção da matriz

Uma linha só muda de bloqueada para planejamento elegível quando contrato e identidade aplicáveis
estiverem aceitos. Planejamento elegível nunca muda sozinho para execução pronta. Uma execução
posterior exige cumulativamente a vertical quando aplicável, seu `Q-*-01`, fonte histórica
autorizada, horizonte, consistência, limites e destino de evidência aprovados.

Ao alterar contrato, identidade, ADR, manifesto de dados ou topologia ancorados, atualize o binding,
reavalie todas as linhas afetadas e recalcule os hashes. Não edite apenas o fingerprint para fazer o
gate passar.

## Gates finais locais

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-V2047BootstrapPlanningCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-Gpt56ChatTrail.ps1
pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1
pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
git diff --check
```

Maven, SQLCMD e o gate progressivo não são aplicáveis: esta fatia não altera Java, POM, SQL,
baseline ou migration. Registre essa limitação, o red inicial, os gates verdes e a ausência de I/O
externo primeiro no `STATES.md`; somente depois feche Q-BST-01 na trilha. V2-047 agregada e todas as
rotas reais `Q-*-02` permanecem abertas.
