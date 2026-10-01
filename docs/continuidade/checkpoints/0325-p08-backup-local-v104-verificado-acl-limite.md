# Checkpoint 0325 — P08 backup copy-only local V104, verificação SQL PASS, SHA físico pendente

## Identificação, autoridade e limite

- 29/09/2026 03:54 UTC, Builder Banco e Persistência. Anterior:
  [0324](0324-p08-v105-dependencias-validador-v104-readonly.md) SHA-256
  `0DE7F10BC886188AC9C27563CCE289890CE78095ED39181DB1B24EA89CE1A515`.
- Supervisor autorizou **somente um gate físico de BACKUP** local do banco
  `localhost/ETL_SISTEMA_V2_SHADOW`, com verificação separada. Migrate V105,
  RESTORE DATABASE, DDL adicional, JDBC físico, replay, remoto e produção
  permaneceram não autorizados. P08 e P01–P33 seguem abertos.
- Banco foi único executor SQL e editor de STATES/TRILHA/RETOMADA/checkpoint;
  worktree e ledgers anteriores preservados. Runtime repina pacote offline.

## Preflight, reserva e comando único

`master` e alvo passaram em conexões `sqlcmd -S lpc:localhost -E -C` novas,
Windows NTLM/shared memory, máquina `LUCAS`, instância `MSSQLSERVER`, banco
online, 105 linhas Flyway/104 SQL/zero falhas e snapshot agregador SHA-256
`78ED7BD652FD28452C861AEC5A36CF3877422AAC8AC1096C2F5A8136FE892B7B`.
`064` reproduziu SHA de saída
`27169AF1E65F589A3E4F81FA685181262E126D992F464473C51D8C7B21760314`.
Serviço `NT Service\MSSQLSERVER` PID 20404, listeners só `127.0.0.1:1433` e
`::1:1433`, zero consumidores de shadow. `InstanceDefaultBackupPath` coincidiu
com o diretório nativo. `Test-Path` retornou falso para o destino absoluto
proposto no runbook:

`C:\Program Files\Microsoft SQL Server\MSSQL17.MSSQLSERVER\MSSQL\Backup\ETL_SISTEMA_V2_SHADOW_preV105_20260929_001.bak`

**Limite do preflight:** após o backup validado, a mesma conta também vê
`Test-Path` falso, pois não consegue atravessar/ler o arquivo pela ACL. Assim,
o falso anterior **não prova ausência prévia**; `msdb` só contém a entrada
atual nesse caminho, mas um arquivo órfão anterior não pode ser descartado.
`WITH INIT` poderia ter substituído mídia inacessível, sem evidência de que
isso ocorreu. A pré-condição de arquivo novo não foi provada e fica para
avaliação do Supervisor; nenhum segundo backup será feito.

Arquivos do banco somavam 100.663.296 bytes (dois arquivos), C: tinha
718.730.690.560 bytes livres no instante da reserva. ACL do diretório retornou
`UnauthorizedAccessException`; não foi alterada. V105/063/064/inventário
tinham os hashes 0324:

| Artefato | SHA-256 |
| --- | --- |
| V105 | `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17` |
| 063 | `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4` |
| 064 | `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533` |
| inventário | `4E242D9EFA5641E5C9C078FC7166BD8E1FF6F854A42851D00589D46FF792A711` |

Reserva física anterior ao efeito em
`target/shadow-local-rebuild-20260928-01/p08-v105-0325-backup-ledger.jsonl`,
SHA-256 final `8D3743D1D70F403C65087266EC7DE5C5CC021D7D5279D7425B82FD50F8870318`.
Registrou alvo, caminho, um comando, impacto de I/O/arquivo/`msdb`, limite
sem retry e recuperação por inspeção autoritativa. Uma linha final corrige a
inferência `destination_absent=true` da reserva sem apagar a evidência original.
SQL do backup SHA-256
`50F84BFAB298D59C5228142DA438CB00F00F27BFB65F7BFBDD0A7582C38FD908`.
Houve **uma** invocação `BACKUP DATABASE [ETL_SISTEMA_V2_SHADOW] TO DISK =
N'<caminho exato acima>' WITH COPY_ONLY, INIT, CHECKSUM` em `master` local.
`sqlcmd` exit 0; SQL Server informou 1.698 páginas processadas. Recibo
`p08-v105-0325-backup-once.out` SHA-256
`E44E6CBB1F797088641CE368C1247C63E75E7F707B51B8629C39C9A19200BB7A`.

## Gate separado de verificação

Novo preflight `master`/alvo/064/socket repetiu os hashes e PID acima. A
primeira invocação do verificador foi rejeitada por `sqlcmd` **antes de
conectar**, pois `-W` e `-y/-Y` são incompatíveis; linha FAIL preservada no
ledger, sem segunda chamada BACKUP. Com somente a formatação do cliente
corrigida, as consultas de verificação foram executadas:

| Prova | Observado | Recibo privado SHA-256 |
| --- | --- | --- |
| `RESTORE VERIFYONLY ... WITH CHECKSUM` | exit 0, `The backup set on file 1 is valid` | `p08-v105-0325-verifyonly-v2.out` `6DCEFC727169F3950FC50455FD57D908C75593D90E7FE5A94B2C6BE74C805B0D` |
| `RESTORE HEADERONLY` | exit 0, database exato, posição 1, backup completo, copy-only/checksums 1, damaged 0 | `p08-v105-0325-headeronly-v2.out` `34B62B61410E7C345EBAC427AAE05B0171DECDB6390D10C2331E1264BBA087F1` |
| `RESTORE FILELISTONLY` | exit 0, MDF e LDF lógicos/físicos esperados | `p08-v105-0325-filelistonly-v2.out` `77B12B80E8405307D3E42A17CDC27305E30B4E71CECE85FDB16EDBA4D686914F` |
| `msdb` | exit 0, uma entrada exata: full/copy-only/checksums, `is_damaged=0`, `backup_size=13.983.744` bytes | `p08-v105-0325-msdb-v2.out` `10E8BDC21589BF29A824588C593EF0EA28447E3895E2EBF95581D08C038B261E` |
| Arquivo físico/SHA | `Get-Item`/`Get-FileHash` negados pela ACL; `OPENROWSET(BULK)` exit 1, SQL 4861/acesso negado | `p08-v105-0325-file_sha-v2.out` `AD5D85F0A078116266D702DEB329A3788AC58B813AE45FEA2FFB5C84B39E0634` |

O valor 13.983.744 é **tamanho do backup set no header/`msdb`**, não o
comprimento físico medido do `.bak`. `Test-Path` falso para a conta deste
terminal após BACKUP reflete acesso negado, não ausência do arquivo; a leitura
e checksum pelo SQL Server demonstram arquivo utilizável pela instância.
Comprimento físico e SHA-256 do arquivo seguem **não comprovados**, sem mudar
ACL, destino ou identidade. `VERIFYONLY` não substitui uma restauração real.

Readback independente final `master`/alvo/064 exit 0, hashes idênticos aos
anteriores e listeners/PID inalterados. Banco V104 mantém histórico, objetos,
principals, contagens e collations sem delta; `msdb` recebeu o histórico
esperado do BACKUP. Nenhum RESTORE DATABASE, migrate, DDL, JDBC ou replay.

## Retomada imediata — até três ações

1. Supervisor avaliar a pré-condição de ausência não comprovada, inclusive o
   risco teórico do `INIT`, e decidir se `VERIFYONLY`/header/filelist/`msdb`
   bastam ou se exige acesso **somente leitura** ao arquivo para comprimento/
   SHA antes de qualquer avaliação de migrate. Nenhuma mudança de ACL foi
   autorizada nesta unidade.
2. Runtime concluir repin/pacote offline contra bytes finais e entregar ao
   Supervisor; Banco não edita scripts Runtime.
3. V105 migrate, restore e replay permanecem gates futuros separados, apenas
   sob nova autorização de alvo/impacto/recuperação/preflight/reserva/readback.
