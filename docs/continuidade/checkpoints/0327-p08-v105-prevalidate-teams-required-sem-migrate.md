# Checkpoint 0327 — P08 V105: padrão `versioned:pending` requer Teams; sem migrate

## Identificação, autoridade e limite

- 29/09/2026, Builder Banco e Persistência. Anterior:
  [0326](0326-p08-v105-prevalidate-pending-sem-migrate.md) SHA-256
  `DAED6D084D27003754939F9C2FB6EC92C886C58BF84B54A713FA889B95B3BF41`.
- Supervisor autorizou um gate read-only novo: `flyway:info` exato e uma
  `flyway:validate` com somente
  `-Dflyway.ignoreMigrationPatterns=versioned:pending`. Uma chamada normal
  `flyway:migrate` V105 dependia de PASS desse gate e preflight novo.
  Falha exigia parada sem fallback, retry ou POM editado. Banco é único executor
  SQL e editor de ledger/shared; worktree e ledgers anteriores preservados.
- Alvo único `lpc:localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth, sem remoto,
  produção, fonte real, restore, JDBC, replay, IT ou smoke nesta unidade.

## Evidência antes do gate

- Recibo estático privado `target/shadow-local-rebuild-20260928-01/p08-v105-0327-static.out`
  SHA-256 `68F904A6632CDEA1159FDE1443C2E85AFD6101B0EA7B75904FCA45565A563140`:
  104 hashes V001–V104 iguais ao manifesto 0319; 105 migrations locais; cinco
  pins 0324 iguais (V105 `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`,
  063 `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4`,
  064 `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533`,
  inventário `4E242D9EFA5641E5C9C078FC7166BD8E1FF6F854A42851D00589D46FF792A711`,
  baseline `291871CE9B1CFF3C524CCAD8C8C4C68A67FAE3BC0672A49B803A04038CBA4126`).
  POM/DLL/JDK17.0.20.1 mantidos.
- SQL master/alvo/064 e `RESTORE VERIFYONLY WITH CHECKSUM` tiveram exit 0;
  hashes iguais a 0326: master `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762`,
  alvo `17B94EEBDA713DE1D4B3628C1FFD71932774E8791583F082E556ABA997C1E6F3`,
  064 `27169AF1E65F589A3E4F81FA685181262E126D992F464473C51D8C7B21760314`,
  backup VERIFYONLY `6DCEFC727169F3950FC50455FD57D908C75593D90E7FE5A94B2C6BE74C805B0D`.
  Primeiro 064 teve formato `sqlcmd` diferente; repetido somente para
  paridade de apresentação, exit 0 e hash original idêntico. Serviço PID
  20404, apenas `::1`/`127.0.0.1:1433`, zero consumidores.
- Histórico 105 linhas = SCHEMA+104 SQL, zero falhas, V105 ausente; 1.819
  objetos, 247 tabelas, 146 linhas agregadas, sete principals e schemas.
  Backup 0325 segue sem prova de ausência prévia, comprimento/SHA físico ou
  RESTORE; `INIT` poderia ter sobrescrito arquivo órfão.

## Gate e parada

| Passo | Resultado | Recibo privado SHA-256 |
| --- | --- | --- |
| `flyway:info`, plugin Community 9.22.3 | Exit 0; SCHEMA e V001–V104 `Success`, somente V105 `Pending`, zero estados anômalos | `p08-v105-0327-info.private.log` `546CEF029765FD804CF842EAE45237A5FDD705350EDE0B665EF20B11DF7D6001` |
| Uma `flyway:validate` com propriedade de processo `versioned:pending` | Exit 1; `FlywayTeamsUpgradeRequiredException`: esse tipo de padrão não é suportado por Community 9.22.3 | `p08-v105-0327-prevalidate-pending.private.log` `5861E96153C5013CC808A077C5E8B75544C792D9499200CF24A20F16DB2A38FE` |
| Readback SQL/socket independente | Master/alvo/064 hashes acima, exit 0, PID/listeners e contagens idênticos; V105 ausente | `p08-v105-0327-after-*-original-format.out` |

A propriedade Maven foi reconhecida, mas o validate aplicado não passou;
V105 não foi validada. **Zero reservas/chamadas migrate**: não houve fallback
`*:pending`, alteração do POM, upgrade/licença, clean, repair, drop, restore,
DDL, JDBC, replay ou smoke. Primeira saída master do readback usou formatação
distinta; a consulta no formato original deu hash idêntico. Ledger físico
`target/shadow-local-rebuild-20260928-01/p08-v105-0327-ledger.jsonl`
SHA-256 `873CC70977E1F2D4F9E6DD45729A1B7266D2CBFF3999F62295C44893A7DE00F8`.
O FAIL original 0326 segue preservado. Pacote Runtime A/B ZIP SHA-256
`80CC18349268E07002E3E395FCE0239EDD95F1639F7AC81E7046C78877483499`
é somente evidência offline, sem execução Banco. P08/P01–P33 abertos.

## Retomada imediata — até três ações

1. Supervisor decidir critério e ferramenta/licença de prevalidate compatíveis
   com Flyway Community 9.22.3, ou aprovar mudança explícita; autorizar gate
   novo antes de qualquer V105. Não usar `*:pending` como fallback implícito.
2. Só após gate novo PASS, refazer preflight master/alvo/064/backup, pins,
   serviço/listeners e reserva distinta antes de uma possível chamada V105.
3. Se V105 futuramente comitar, readback autoritativo e gates 064, 063 e
   validate normal separados; replay/IT/smoke pertencem a outra unidade.
