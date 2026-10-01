# Checkpoint 0324 — P08 V105 revisada e 063 provado em V104, sem backup/migrate

## Identificação, autoridade e limite

- 29/09/2026 03:39 UTC, Builder Banco e Persistência. Anterior:
  [0323](0323-p08-v105-preparado-sem-sql.md) SHA-256
  `A7794576BF6076824F8A500D59F08C4C9C79CBA79A842860B3A6749A31FE3F18`.
- Supervisor recusou V105 física por lacunas em estatísticas/dependências,
  definição da procedure, checks, opções do índice e recuperação. Autorizou
  apenas guardas/revisão e gates **somente leitura** 063/064 no alvo local
  `lpc:localhost/ETL_SISTEMA_V2_SHADOW` com `sqlcmd -E`, preflight/reserva/
  readback novos. Backup, restore, Flyway migrate, remoto, produção e fonte real
  ficaram fora da unidade. Nenhum aceite P08/P01–P33 foi promovido.

## Alterações e decisões

| Artefato | SHA-256 final | Decisão |
| --- | --- | --- |
| `database/migrations/V105__align_audit_and_expansion_label_bin2.sql` | `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17` | Recusa antes do DROP estatísticas/dependências extras; exige SHA/bytes V042 e checks V025/V042, opções físicas V025; pós-condições repetem. |
| `database/validation/063_validate_epoch_v104_v105.sql` | `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4` | SET options SQL Server e inventário corrigidos; V104 FAIL esperado sem falso inventário. |
| `database/validation/064_snapshot_epoch_v105.sql` | `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533` | Contagens de estatísticas, dependências, hashes de definições e opções, sem payload/corpo. |
| `database/manifest/epoch-v104-v105-inventory.json` | `4E242D9EFA5641E5C9C078FC7166BD8E1FF6F854A42851D00589D46FF792A711` | 569 objetos, 23 TVPs, 1.019 constraints nomeadas, 224 sem nome e 100 índices. |
| `database/manifest/epoch-v105-collation-repair.json` | `6D71EBD264E1C4178FAE120FE93385A413E6856DB791456B75337D5986AC339D` | Política de recusa, digests revisados e contrato JDBC/TVP. |
| `docs/runbooks/p08-v105-preparacao-20260928.md` | `323E06BE4024315B286F81ACE4EA0D962C5FA6E3D551A7E9F3DB0E577B2AC04D` | Backup nativo local, verificação e restore condicional sem execução. |

`Build-EpochSchemaInventory.mjs`, `epoch-validator-template.sql` e
`Test-EpochV105Offline.mjs` mudaram para corrigir constraints sem nome,
requerer SET options e testar guardas novas. Baseline permanece SHA-256
`291871CE9B1CFF3C524CCAD8C8C4C68A67FAE3BC0672A49B803A04038CBA4126`.
V001–V104, os scripts históricos 003/005/030, seus FAIL e ledgers anteriores
não foram editados. O worktree compartilhado de outros Builders foi preservado.

`sys.sql_modules.definition` físico da procedure tinha 9.342 bytes UTF-16LE e
SHA-256 `3E94EF77EF79F58BBE38E68BDD965270F5F6A13DFF1A0AC9F3D2EC58869110AA`,
iguais à definição extraída de V042. Os checks físicos são formas normalizadas
de V025/V042, 136/450 bytes, digests registrados no manifest de reparo. Índice
de status tem opções físicas padrão, uma partição sem compressão. V105 agora
recusa drift nessas definições/opções em vez de recopiá-lo. Toda estatística
autônoma nas quatro colunas, inclusive auto-criada, é recusada antes de DDL;
a estatística do único índice reconstruído é a exceção explícita. O 064 conta
as classes de dependência sem expor payload. Não houve prova de migrate.

## Gates SQL e evidência observada

Todos os gates fizeram preflight novo `master`/alvo exit 0/0, Windows auth,
serviço local PID 20404 e só `127.0.0.1:1433`/`::1:1433`; reservaram em
`target/shadow-local-rebuild-20260928-01/p08-v105-0324-readonly-ledger.jsonl`
SHA-256 `2C0168D265557E27E897E57FF4C54193F57AEAD84F07E3894D4F5BED3445339A`
antes do SQL. Cada readback autoritativo repetiu SHA-256
`78ED7BD652FD28452C861AEC5A36CF3877422AAC8AC1096C2F5A8136FE892B7B`
sem delta.

| Gate | Observado | Recibo privado SHA-256 |
| --- | --- | --- |
| 063 inicial | Exit 1 SQL 1934: `QUOTED_IDENTIFIER` ausente no template. FAIL preservado. | `p08-v105-0324-063-gate.out` `92308DC68B09F66218DC4BADDF567CF4B0F04C1D50998067E83647760899D25E` |
| 063 após SET options | Exit 1 com nove falsos achados `UNNAMED_CONSTRAINT`: sete defaults de literal e duas FKs V093 omitidos pelo parser. FAIL preservado. | `p08-v105-0324-063b-gate.out` `4BD20D5AB5AC58CC5D61C1671412B84DD1F372135BA0DACD2F463892CB93672C` |
| 063 final, bytes atuais | Exit 1 esperado: cinco `COLLATION`, `TVP_SHAPE` CI e `MIGRATION_PENDING`; zero falso inventário. | `p08-v105-0324-063d-gate.out` `C87A4D7743FF3C0DD37A324BEC4F6A0AA15C08B6DDC9080428DB1DB4D6A58464` |
| 064 final, bytes atuais | Exit 0: 105/104/0 Flyway, 1.819 objetos, 247 tabelas, 146 linhas agregadas, sete principals/schemas, auditoria/labels 0, quatro colunas CI e TVP CI; quatro definições/opções = 1 cada; bloqueadores extras = 0. | `p08-v105-0324-064d-gate.out` `27169AF1E65F589A3E4F81FA685181262E126D992F464473C51D8C7B21760314` |

Diagnósticos extras de hash/definições tiveram reservas e readbacks próprios.
Gates 063/064 não fizeram DDL/DML durável. O V104 continua sem PASS estrutural
por escolha explícita do contrato V105.

## Offline, pacote e recuperação

- `Test-EpochV105Offline.mjs` PASS com três mutantes de ordem/BIN2, cinco de
  collation, quatro de dependência, cinco de definição/opções e quatro de
  inventário; procedure V042 idêntica. Recibo
  `p08-v105-0324-offline.out` SHA-256
  `09B3DCDFA0C3043803507C4F6E66B4701AFC6BB6D51F861C06AB6ACE75130612`.
  Checkers de fundação/progressivo PowerShell 7 PASS; scanner offline PASS
  (4.072 textos, zero achados) SHA-256
  `22608DCD6A92B883268A784AFF0D4C33DA7A4476D6B3C66C9C5933EBAF3D29BE`;
  trilha PASS, 33 etapas/48 IDs abertos/nove pacotes.
- Backup **proposto, não feito**: caminho nativo
  `C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\ETL_SISTEMA_V2_SHADOW_preV105_20260929_001.bak`.
  Diretório e `BackupDirectory` conferem, arquivo ausente, C: cerca de 719 GB
  livres; ACL não pôde ser lida, escrita pela conta do serviço sem prova.
  Gate futuro precisa confirmar ACL/espaço, `BACKUP ... COPY_ONLY, CHECKSUM`,
  `RESTORE VERIFYONLY`, header/filelist, hash e readback; restore com REPLACE
  é ação distinta e só sob autorização específica. Nenhum BACKUP/RESTORE ocorreu.
- Runtime entregou pacote 105 offline A/B idêntico SHA-256
  `802DF14E6300545B415E5FD43481A6D43C6550FCABC866DAD3BE39CC6264DA22`,
  27 guardas, quatro mutantes, 21 extraídos, sete Maven PASS,
  `PACKAGED_NOT_SMOKE_QUALIFIED`. Edições atuais V105/inventário mudaram bytes:
  pacote candidato ficou obsoleto e Runtime deve regenerar/retestar após revisão.
  Banco não editou scripts Runtime nem executou IT de replay.

## Retomada imediata — até três ações

1. Supervisor revisar bytes, guardas, prova física read-only, impacto de
   locks/TVP/procedure e mecanismo de backup; autorizar ou recusar gates físicos
   futuros de backup e migrate separadamente.
2. Runtime regenerar pacote/lock A/B para os hashes finais, testar smoke sem
   SQL sob sua responsabilidade e entregar handoff ao Supervisor.
3. Somente sob nova autoridade e com backup verificado, Banco faria preflight/
   reserva/readback de um migrate V105, 063/064/validate separados e replay
   rollback-only/IT com Runtime. Sem autoridade, não executar esses passos.
