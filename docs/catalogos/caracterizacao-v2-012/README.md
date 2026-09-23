# Fundação offline de caracterização V2-012

Este catálogo materializa `Q-FND-01`, Bloco 39, como fundação local `test-only`,
provider-neutral e estritamente offline. O único resultado agregado positivo desta fatia é
`FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES`: ele confirma a estrutura local, não uma
caracterização do fornecedor.

## Perfis independentes

| Entidade | Fonte | Oráculo futuro | Contrato | Chave da fonte | Estado |
|---|---|---|---|---|---|
| Coletas | Data Export 6908 | `DATA_EXPORT_PROVIDER` | `dataexport-6908` | `/id`, inteiro type-tagged | `PREPARED_NOT_EXECUTED` / `ORACLE_REQUIRED` |
| Manifestos | Data Export 6399 | `DATA_EXPORT_PROVIDER` | `dataexport-6399` | `/sequence_code`, inteiro positivo type-tagged | `PREPARED_NOT_EXECUTED` / `ORACLE_REQUIRED` |
| Cotações | Data Export 6906 | `DATA_EXPORT_PROVIDER` | `dataexport-6906` | `/sequence_code`, inteiro positivo type-tagged | `PREPARED_NOT_EXECUTED` / `ORACLE_REQUIRED` |
| Usuários | GraphQL `individual(enabled=true)` | `GRAPHQL_PROVIDER` | `graphql-individual` | `/node/id`, inteiro ou texto type-tagged | `PREPARED_NOT_EXECUTED` / `ORACLE_REQUIRED` |

Os quatro perfis declaram `source_instance` e `tenant_scope` como
`REQUIRED_AT_EXECUTION`. As fixtures usam apenas `SYNTH_SOURCE_INSTANCE` e
`SYNTH_TENANT_SCOPE`; `DEFAULT`, `GLOBAL` e `SINGLETON` são sentinels recusados.

## Desenho local

O núcleo em `src/test/java/br/com/esl/etl/v2/contratos/caracterizacao/` separa contrato esperado,
observação sanitizada, comparação pura, regra por entidade, resultado independente, resumo
agregado, adapters sintéticos por tipo de fonte e receipt determinístico. Os quatro perfis e os
quatro bundles de fixtures ficam em `src/test/resources/contracts/v2-012/`.

O carregamento é UTF-8 estrito, rejeita BOM, JSON duplicado ou trailing, propriedades ausentes,
tipos desconhecidos e limites estruturais antes da materialização. O evaluator acumula motivos
fail-closed sem transformar aceitação parcial em sucesso global. O writer recebe `Clock` e ID
sintético por injeção, publica por substituição de arquivo e restringe toda escrita a `target`.
Receipts contêm somente status, contagens e fingerprints allowlisted.

As fixtures exercitam presença `ABSENT`/`NULL`/`VALUE`, chaves type-tagged, raiz e grão,
filtros, paginação/terminalidade, ordenação, temporalidade/timezone/tradução, status, expansão,
filhos, cardinalidade e tetos de 65.536 bytes, 1.000 linhas, 100 páginas, profundidade 16, 256
paths e 4.096 nós. Há 166 cenários: 13 estruturas sintéticas aceitas e 153 contraprovas
fail-closed. Nenhuma aceitação sintética prova snapshot, completude, terminalidade do fornecedor,
paridade real ou autorização externa.

## Proveniência e integridade

O [manifesto](manifesto.json) registra os SHA-256 dos contratos, identidades, decisões, perfis e
fixtures usados. Para cada fixture há um hash bruto de arquivo e um fingerprint normalizado,
recomputado pelo loader Java depois da aplicação determinística dos overrides. Para cada perfil há
também o hash bruto e o fingerprint do objeto imutável normalizado.

As decisões permanecem específicas:

- Coletas conserva `/id` como source key e `/sequence_code` somente como alias; o filtro
  `scopes.by_updated_at` é complementar e nunca watermark; expansão física não cria nova raiz.
- Manifestos conserva raiz por `/sequence_code`, Pick por `/mft_pfs_pck_sequence_code` e MDF-e
  por `/mft_mfs_key`; `/mft_mfs_number` é atributo e `/mdfe_status` é escalar da raiz. Nenhuma
  relação Manifesto→Coleta é inferida.
- Cotações não recebe `/id`, `/updated_at` ou filho inventado; `sequence_code asc` permanece regra
  de paridade, nunca identidade ou cursor.
- Usuários permanece no documento GraphQL individual com `enabled=true`, máximo 20, sem
  `updatedAt`, incremental Data Export ou desativação por ausência.

## Gates preservados

`Q-USR-01`, `Q-COL-01`, `Q-MAN-01` e `Q-COT-01` continuam abertos; `Q-MAN-01` continua em
`EXTERNAL_HOLD`. Cada futura execução exige autorização explícita do owner, escopos reais
fornecidos fora desta evidência, adapter de oráculo autorizado e receipt sanitizado em uma fatia
posterior independente.

Esta fundação não inclui rede, API, credencial, payload real, banco, runtime, publicação ou
cutover. Bootstrap, R01, R02, paridade real, sweep e E2E permanecem fora do escopo.

## Validação

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
pwsh -NoProfile -File .\scripts\validation\Test-V2012CharacterizationFoundation.ps1
```

O procedimento completo e exclusivamente local está no
[runbook da fundação](../../runbooks/v2-012-fundacao-caracterizacao-sol.md).
