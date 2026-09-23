# Continuação temporal B54 — V021

Pacote aplicado em 08/09/2026 sob a autorização local aditiva do owner.
Alvo único: localhost/ETL_SISTEMA_V2_SHADOW. Grants adicionais: zero.
O manifesto preparado e os bytes efetivamente testados estão preservados em
`target/bloco54/completion-authorized-20260908/V021_SCHEMA_01`.

O [ADR 0037](../../../docs/adr/0037-retomada-do-plano-temporal-e-fronteira-incremental-local.md)
explica o conflito entre plano e execução e a separação dos consumidores.
Não executar `apply.sql` novamente: ele recusa V021 já instalada.

## Aplicação e verificação

`Invoke-Bloco54TemporalSchema.ps1` exige hash integral do pacote e contagem exata
antes de I/O. Confere o alvo no master, qualifica upgrade e cauda do baseline em
rollback, exige catálogos iguais e aplica somente V021 em uma transação. A
verificação seguinte usa outra conexão Windows com TLS, prazo de 20 segundos e
as duas contas/grants/mappings originais. Nenhum reset, seed de negócio ou renovação.

Para verificar o estado já instalado, no PowerShell administrativo autorizado:

```powershell
sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -N -l 10 -t 20 -b -i database/proposals/bloco54-temporal-continuation/verify.sql
```

## Recuperação concreta

Se a resposta após commit se perder, não reaplicar: executar o comando de
verificação acima e ler `schema-after.log`/`results.json` do controlador. O catálogo
V021 é `7a86e28ea68cecd8ba993bbea8df5c677e0848f27aa7b0412402165071736408`.
Com os três módulos, 25 grants, direitos e validade originais confirmados, iniciar
uma invocação nova sobre a ocorrência original. A repetição de plano publicado
foi exercitada por `V7_RECOVERY_01`, sem HTTP ou publicação duplicada.

Se o catálogo não conferir, manter a execução interrompida e preservar o diff
para uma correção versionada. Não fazer downgrade da V020/V021, retirar auditoria,
eliminar a função ou limpar dados. A falha não concede direito de renovar contas,
estender validade ou reabrir leases antigos. O fechamento operacional e os hashes
atuais pertencem ao manifesto B54, não à fotografia preparada deste pacote.
