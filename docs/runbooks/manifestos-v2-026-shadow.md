# Manifestos 6399 — V2-026 em sombra

V04 materializa somente a vertical local de Manifestos do template 6399. Ela não chama Data Export,
não lê credenciais, não executa HTTP, não resolve Coleta, não cria objeto `pub` e não altera o
legado. A execução real permanece fora deste runbook e depende do oráculo autorizado Q-MAN-01.

## Grão e redução

- Raiz: `source_instance`, `tenant_scope`, `manifestos`, `INTEGER:sequence_code` positivo.
- Pick: `root_canonical_id`, `INTEGER:mft_pfs_pck_sequence_code` positivo.
- MDF-e: `root_canonical_id`, `STRING:mft_mfs_key`, exatamente 44 dígitos ASCII; `mft_mfs_number`
  é atributo correlacionado, não identidade.
- `mdfe_status` é presença/valor escalar da raiz; nunca compõe o filho MDF-e.
- MAN-01, MAN-02, MAN-04 e MAN-07 reduzem por frescor
  `finished_at → closed_at → departured_at → created_at`. Empate fresco com atributo divergente
  gera `EQUAL_FRESHNESS_CONFLICT`; não há desempate por página, ordem ou hash.
- Competência usa `departured_at`; `created_at` só é fallback quando o primário é ausente/nulo.
  Tempo local ou inválido bloqueia a promoção e preserva o raw para quarentena.

## Segurança e ausência

As entradas exigem `source_instance` e `tenant_scope` explícitos: `DEFAULT`, `GLOBAL` e
`SINGLETON` são recusados. Os 27 paths textuais são medidos em unidades UTF-16; overflow vai para
quarentena, sem truncamento. Ausência jamais provoca delete, sweep, desativação ou publicação.

`mft_pfs_pck_sequence_code` produz apenas `recon.manifesto_coleta_relation_candidate`, com
presença e proveniência append-only. A resolução, FK ou lookup para Coletas pertence a V2-046a.

## Prova local autorizada

Antes de escrever, confirme o único alvo permitido a partir de `master`:

```powershell
sqlcmd -E -S localhost -d master -b -h -1 -W -Q "SET NOCOUNT ON; SELECT DB_NAME(), CASE WHEN DB_ID(N'ETL_SISTEMA_V2_SHADOW') IS NOT NULL THEN N'ETL_SISTEMA_V2_SHADOW' ELSE N'MISSING' END;"
```

Do diretório `database/validation`, execute apenas o exercício revertido:

```powershell
sqlcmd -E -S localhost -d ETL_SISTEMA_V2_SHADOW -b -f 65001 -i 043_exercise_manifestos_shadow_vertical_rollback.sql
```

Ele reproduz o baseline, valida V04, testa duas expansões físicas, replay, candidatos de Pick/MDF-e,
isolamento Manifesto–Coleta e overflow de `operational_comments`; a transação final é revertida.
Não use esta prova contra qualquer outro banco.
