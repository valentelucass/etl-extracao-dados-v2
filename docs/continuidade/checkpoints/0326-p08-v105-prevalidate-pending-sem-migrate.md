# Checkpoint 0326 — P08 V105: validate prévio FAIL pendente; nenhum migrate

## Identificação, autoridade e limite

- 29/09/2026, Builder Banco e Persistência. Anterior:
  [0325](0325-p08-backup-local-v104-verificado-acl-limite.md) SHA-256
  `FC31D1ED2A1BFA57E2C4D4785372DA8500BA5A7175DF6E7DF34735405AF7DDA2`.
- O Supervisor aceitou, **para este shadow e uma tentativa V105 somente**, o
  backup 0325 com `VERIFYONLY`/header/filelist/`msdb` concordantes. Continuam
  sem prova ausência prévia do `.bak`, comprimento/SHA físico e restauração;
  `WITH INIT` poderia ter substituído arquivo órfão. Nenhum aceite de backup
  restaurado foi inferido.
- Autorização exigiu master/alvo/socket/consumidores/histórico/064/bytes e
  `flyway:validate` prévio verdes antes de reservar e invocar **um** migrate.
  Divergência obriga parada sem retry/fallback. Não cobre restore, DDL adicional,
  JDBC, replay, smoke, produção ou remoto. Banco é único executor SQL/editor
  compartilhado; worktree e ledgers anteriores preservados.

## Preflight observado

- Hashes fixados conferiram exatamente: V105
  `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`,
  063 `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4`,
  064 `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533`,
  inventário `4E242D9EFA5641E5C9C078FC7166BD8E1FF6F854A42851D00589D46FF792A711`,
  baseline `291871CE9B1CFF3C524CCAD8C8C4C68A67FAE3BC0672A49B803A04038CBA4126`.
  104 migrations V001–V104 coincidiram com os hashes do manifesto 0319;
  diretório tinha 105 migrations. POM e DLL 12.8.2 mantiveram bytes do 0320;
  JDK local `FileVersion=17.0.20.1`.
- A checagem inicial `java -version` foi tratada pelo PowerShell 5.1 como erro
  ao receber stderr, antes de qualquer SQL; foi distinguida por metadado do
  executável, sem alterar Java/PATH. Não é a causa do FAIL Flyway.
- `sqlcmd -S lpc:localhost -E -C`, conexões novas master/alvo/064 exit 0.
  Máquina `LUCAS`, instância `MSSQLSERVER`, NTLM/shared memory, serviço PID
  20404 e apenas `127.0.0.1:1433`/`::1:1433`, zero consumidores. Histórico
  105 linhas = SCHEMA + 104 SQL, zero falhas, V105 ausente; 1.819 objetos,
  247 tabelas, 146 linhas agregadas, sete principals e sete schemas owned.
  `064` reproduziu contagens/bytes, quatro collations CI persistidas, TVP CI,
  quatro definições/opções revisadas = 1, bloqueadores extras zero.
- `RESTORE VERIFYONLY WITH CHECKSUM` no backup 0325 saiu 0 novamente. Não foi
  feito RESTORE DATABASE nem acesso por outra identidade ao arquivo.

| Recibo privado em `target/shadow-local-rebuild-20260928-01/` | SHA-256 |
| --- | --- |
| `p08-v105-0326-pre-master.out` | `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762` |
| `p08-v105-0326-pre-target.out` | `17B94EEBDA713DE1D4B3628C1FFD71932774E8791583F082E556ABA997C1E6F3` |
| `p08-v105-0326-pre-064.out` | `27169AF1E65F589A3E4F81FA685181262E126D992F464473C51D8C7B21760314` |
| `p08-v105-0326-pre-backup-verify.out` | `6DCEFC727169F3950FC50455FD57D908C75593D90E7FE5A94B2C6BE74C805B0D` |

## Gate `flyway:validate` prévio e parada

Reserva física `PRE_VALIDATE_V105` antecedeu Maven offline em JDK17, perfil
`shadow-migrations-windows-auth`, URL local apenas no ambiente do processo,
integrated security, sem usuário/senha e DLL `target/native`. Uma invocação
`flyway:validate` saiu **1**. Log privado
`p08-v105-0326-prevalidate.private.log` SHA-256
`D47D2CB198B2E08E53CB7A5C866ECBB5CF2014DED6B33BA6F30B618D3E28CD1B`:
`FlywayValidateException` informa `Detected resolved migration not applied to
database: 105`. A linha de histórico falha permanece **zero**; o FAIL decorre
do candidato V105 pendente. O Flyway sugeriu `ignoreMigrationPatterns='*:pending'`,
mas isso mudaria a regra do gate após o FAIL. Sob a condição de parada expressa,
não houve retry, override ou fallback, **nem reserva/chamada `flyway:migrate`**.

Readback diagnóstico com reserva própria: master/alvo/064 exit 0, hashes
idênticos à tabela acima, PID/listeners estáveis, V105 ausente e zero delta.
Ledger físico `p08-v105-0326-migrate-ledger.jsonl` SHA-256
`34C2EABEA6C9EB6C590C34DFB3CF7000F5375E8A433BCD9BB6EE45AA4E99B164`
registra reserva, FAIL, diagnóstico e parada. Não houve clean, repair, drop,
restore, DDL, JDBC, replay, 063/064 pós-migrate ou validate pós-migrate.

Runtime entregou por handoff pacote offline A/B repinado para bytes 0324,
ZIP idêntico SHA-256
`80CC18349268E07002E3E395FCE0239EDD95F1639F7AC81E7046C78877483499`,
27 guardas, quatro mutantes, 21 extraídos, sete Maven, config/dry-run PASS.
Banco não editou scripts do pacote nem executou smoke; P08 permanece aberto.

## Retomada imediata — até três ações

1. Supervisor decidir explicitamente um critério novo para o validate prévio
   que preserve a prova das V001–V104 e trate V105 pendente, com autorização
   de novo gate se quiser reavaliar. Não inferir que o FAIL autoriza override.
2. Só após novo gate verde, refazer preflight/reserva e avaliar uma chamada
   V105 conforme autoridade então vigente; não recuperar tentativa inexistente.
3. Se V105 for aplicada futuramente, executar readback autoritativo e gates
   064, 063 e Flyway validate separados. Replay/IT/smoke seguem fora desta
   unidade; P08/P01–P33 sem aceite integral.
