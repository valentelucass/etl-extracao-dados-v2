# Localização de Cargas 8656 — shadow V2-028

Estado local: `IMPLEMENTADA_EM_SHADOW`. Este bloco não autoriza fonte, relação com Fretes,
fallback de volume, publicação, paridade, sweep, deativação, bootstrap ou cutover.

## Contrato fechado

- `/corporation_sequence_number` é INTEGER type-tagged e escopado; `/sequence_number` é proibido;
- presença usa root `fields`/`policy`, 17 fields e sete metadados exatos, inclusive `rawWireLexeme`;
- somente `/service_at` válido determina frescor; inválido ou ausente é quarentena sem promoção;
- datas civis usam `America/Sao_Paulo`; gap/overlap histórico falha fechado e cada scalar tipado
  precisa corresponder ao raw do mesmo envelope antes de qualquer staging;
- `ABSENT` conserva, `NULL` limpa e `VALUE` aplica, inclusive zero;
- a quarentena persiste payload/raw/presença no sidecar, inclusive léxico numérico longo/overflow,
  sem candidate;
- ausência não prova completude e nunca executa prune/sweep.

Validação estática:

```powershell
pwsh -File scripts/validation/Test-LocalizacaoCargasV2028ShadowVertical.ps1
```

Validação SQL local, explicitamente autorizada e rollback-only:

```powershell
pwsh -File scripts/validation/Invoke-LocalizacaoCargasV2028ShadowValidation.ps1 -ExecuteLocalShadow
```

O runner aceita apenas `localhost/ETL_SISTEMA_V2_SHADOW`, compara o estado antes/depois e executa
o exercício 047 e a prova concorrente. O probe confirma contenção no mesmo escopo, isolamento por
`environment_name` e por `tenant_scope`, além da liberação do lock no rollback. Relação/fallback
com Fretes continuam deferidos.
