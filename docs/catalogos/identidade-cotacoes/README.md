# Identidade e grão — Data Export 6906 (Cotações)

Este catálogo materializa a fatia `V2-009b/6906`. Ele aplica o registry de identidade já
ratificado à fonte Data Export `6906`, usando somente a evidência local, estática e sanitizada
do contrato `V2-025b/6906`. Não chama a ESL, não lê credencial, não acessa banco e não implementa
`V2-027`.

## Decisão no escopo caracterizado

- A raiz lógica é uma cotação por `(source_instance, tenant_scope, entity=cotacoes,
  source_key=INTEGER:sequence_code)`.
- `/sequence_code` é a source key técnica da raiz. Ela é um inteiro JSON, não um alias, não a
  ordenação e não o `canonical_id` da V2.
- O `canonical_id` futuro continua sendo um surrogate `BIGINT IDENTITY` do SQL Server. Nenhum
  ID da V1, `sequence_code`, hash, data ou ordem de página pode substituí-lo.
- Não há filho nem expansão física comprovados. As três linhas físicas e três `sequence_code`
  distintos observados formam apenas uma janela sanitizada de cardinalidade 1:1; não são prova
  global de uma linha física por raiz.
- Não existe alias de negócio, crosswalk entre transportes ou rekey comprovado para Cotações.
  Alteração de chave ou associação por outro campo requer evidência e versionamento posteriores.

## Escopo, colisão e tenant

`tenant_scope` não aparece como campo comprovado do payload 6906. Por isso, a decisão exige
valor explícito de configuração/controle e proíbe derivá-lo de filial, empresa, solicitante,
cliente ou outro campo de negócio. `DEFAULT`, `GLOBAL` e `SINGLETON` são sentinels proibidos:
a unicidade global não foi provada.

Não há dois candidatos de tenant em conflito; há ausência de candidato de payload. Ela é tratada
fail-closed como requisito de escopo explícito, sem inventar uma equivalência. Uma observação sem
`source_instance`, `tenant_scope`, `sequence_code` inteiro válido ou dentro do limite é
quarentenada. Repetições da mesma tuple não criam uma segunda raiz: a V2-027 deve resolver
frescor/reducer de forma determinística antes da promoção; enquanto isso não existir, repetição
divergente bloqueia a promoção e não vira filho, alias ou rekey por heurística.

## Limites preservados

A PK legada, o DTO `Long` e a janela sanitizada sustentam somente a hipótese escopada acima.
Eles não provam estabilidade temporal, unicidade global, semântica de `per`, snapshot,
paginação completa, completude, ausência ou cutover. `V2-009d` implementará constraints, testes
de colisão, nulo, concorrência e replay com a vertical V2-027; `V2-025d`, se autorizado após
V2-041, pode ampliar a evidência remota sem persistir dados reais.

## Revalidação

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6906IdentityCatalog.ps1
```

O validador confere UTF-8, a ligação exata ao fingerprint do contrato 6906, seis fixtures
sintéticas já versionadas, a forma observada de três raízes, os limites de escopo e os
fingerprints desta decisão. Nenhum código de produção, rede ou banco é executado.
