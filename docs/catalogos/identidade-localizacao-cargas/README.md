# Identidade e grão — Data Export 8656 (Localização de Cargas)

Este catálogo materializa `V2-009b/8656` usando somente evidência local, sanitizada e estática.
Ele não implementa `V2-028`, não relaciona Localização a Fretes e não acessa rede, credencial ou
banco.

## Resultado: `COMPLETE_LOCAL_BOUNDED`

A raiz física é cada item de `/data/*`. `/corporation_sequence_number` inteiro é a source key da
raiz lógica escopada por `(source_instance, tenant_scope, entity=localizacao_cargas, source_key)`
somente na decisão local delimitada. O tipo inteiro vem da observação histórica parcial e do
legado estático; não é tratado como garantia atual do provedor.
O futuro `canonical_id` é surrogate `BIGINT IDENTITY` e nunca o número-fonte. Não há business key,
alias, crosswalk ou rekey aprovado; `sequence_number` foi somente alias de desserialização e nome
de ordenação no legado, não campo publicado na observação, por isso é rejeitado como source alias.

Nenhum array, expansão ou filho físico foi observado. Os demais campos permanecem escalares da
raiz, sem vínculo implícito com Fretes. A mesma tuple reaproveita a raiz; mudança escalar é nova
observação, não rekey. Repetição divergente bloqueia promoção até `V2-028` decidir frescor/reducer;
troca de chave ou scope sem prova é quarentena.

## Limite da prova

Três linhas e três candidatos distintos, além da PK/dedupe legada, sustentam apenas a decisão
escopada. Não provam unicidade global, estabilidade temporal, cardinalidade 1:1 fora da janela,
tenant no payload, completude, sweep ou cutover. `source_instance` e `tenant_scope` são externos e
explícitos; `DEFAULT`, `GLOBAL` e `SINGLETON` são recusados.

`V2-025d` permanece bloqueada pelo hold externo de `V2-041`.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport8656IdentityCatalog.ps1
```
