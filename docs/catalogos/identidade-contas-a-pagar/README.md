# Identidade e grão — Data Export 8636 (Contas a Pagar)

Este catálogo registra o resultado offline `V2-009b/8636`. O legado foi lido somente como
evidência estática; não houve `V2-029`, regra financeira, schema, mapper, banco ou rede.

## Resultado: `BLOCKED_ROOT_IDENTITY_ABSENT_PHYSICAL_ROW_KEY_REFUTED_AND_GRAIN_UNRESOLVED`

Cada `/data/*` é uma linha física, mas nenhum identificador da raiz lógica `accounting_debit` foi
observado. `/ant_ils_sequence_code` continua somente candidata de parcela, com wire type
inconclusivo entre histórico parcial e coerção do legado: não pode ser promovida a source key da
raiz nem a identidade da linha. Sem source key da raiz, o futuro
`canonical_id BIGINT IDENTITY` não recebe binding. Documento, parcela e campos contábeis são
atributos/candidatos, não aliases ou rekeys.

Nenhum array/filho físico foi observado. Raiz, parcela, centro de custo e plano contábil não são
materializados como relação. O histórico parcial serve como contraexemplo: repete algumas
`ant_ils_sequence_code` com campo contábil divergente, refutando-a como chave da linha física;
ele não prova qual expansão produziu a multiplicidade nem sua cardinalidade universal.

## Replay, colisão e limite da prova

Sem raiz não existe replay canônico classificável. Todas as linhas devem ser preservadas; é
proibido aplicar `keep latest`, achatar pela candidata ou inferir rekey. Divergência bloqueia a
promoção. Tenant é externo e explícito, sem sentinel global. A janela sanitizada tem cinco linhas
e cinco candidatas; a fixture sintética apenas espelha essa forma. Nenhuma delas prova source
entities, raiz, estabilidade, cardinalidade, completude, sweep ou cutover.

P09 requer identificador versionado da raiz e observações representativas repetidas que provem o
grão raiz–parcela–rateio.

`V2-025d` permanece bloqueada pelo hold externo de `V2-041`.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport8636IdentityCatalog.ps1
```
