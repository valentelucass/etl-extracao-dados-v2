# Identidade e grão — Data Export 10633 (Inventário)

Este catálogo registra a tentativa offline `V2-009b/10633`. Ele usa o contrato 10633 e o legado
somente como evidência estática; não executa `V2-031`, parsing, reducer, mapper, schema, banco ou
rede.

## Resultado: `UNRESOLVED_ROOT_FREIGHT_AND_INVOICE_MAPPING_IDENTITY`

`/sequence_code` inteiro permanece somente candidato de source key da raiz lógica escopada: a
janela 27:3 e o hash composto legado não bastam para aceitar o grão. O futuro `canonical_id`
permanece surrogate `BIGINT IDENTITY`, mas seu binding está retido. O componente físico escalar
`/cnr_c_s_fit_corporation_sequence_number` é apenas referência candidata de Frete/minuta: não é
alias, identidade, filho nem crosswalk. Nenhum rekey está aprovado.

O subitem fica aberto porque `cnr_c_s_fit_invoices_mapping` é `Object` no DTO legado. A fixture
artificial mostra expansão, mas não prova que a forma remota seja array, nem fornece componentes
naturais, colisão zero ou cardinalidade. O hash legado inclui sequência, minuta, JSON do mapping e
`started_at`; por ser heurístico e mutável, não é source key, canonical key ou alias da V2.

## Replay, colisão e limite da prova

Sem grão de raiz aceito, a repetição de `sequence_code` não pode ser classificada como replay
canônico. Todas as variações de mapping devem ser preservadas como observações sem parsing,
colapso, relação ou promoção de filho. Divergência bloqueia a vertical; rekey sem prova é
quarentena. A janela 27:3 e as fixtures sintéticas não provam raiz, papel do componente
Frete/minuta, forma, identidade/cardinalidade de filho, relação com minuta/invoice/Frete,
estabilidade temporal, tenant, completude, sweep ou cutover.

P08 exige identidade versionada da raiz, papel versionado do componente Frete/minuta, shape/tipos
versionados do mapping e observações repetidas representativas que provem chave natural, colisões
e cardinalidade. `V2-025d` permanece bloqueada pelo hold externo de `V2-041`.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport10633IdentityCatalog.ps1
```
