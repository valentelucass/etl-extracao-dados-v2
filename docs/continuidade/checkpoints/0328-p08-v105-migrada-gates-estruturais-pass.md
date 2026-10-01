# Checkpoint 0328 — P08: V105 aplicada uma vez; 064/063/validate passaram

## Identificação, autorização e limite

- 29/09/2026, Builder Banco e Persistência. Anterior:
  [0327](0327-p08-v105-prevalidate-teams-required-sem-migrate.md) SHA-256
  `63054E6D2CE7CEADF73551FCE4F932023DCDA025138F5881B2BD15E9D07C1396`.
- Supervisor revogou prevalidate por `ignoreMigrationPatterns`, autorizou
  espelho temporário de V001–V104 em `target/` e uma tentativa V105 **somente**
  após info/validate normal nesse espelho e preflight completo novo.
  Pós-migrate: readback autoritativo, 064, 063 e validate original em gates
  separados. Nenhum JDBC, replay, IT, smoke, restore, produção ou remoto.
- Alvo físico único: `lpc:localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth,
  instância `MSSQLSERVER` PID 20404, listeners somente `::1` e
  `127.0.0.1:1433`, zero consumidores. Ledger físico desta unidade:
  `target/shadow-local-rebuild-20260928-01/p08-v105-0328-ledger.jsonl`
  SHA-256 `7FE5E104EF9353DC0A029ABC60C1F61CAC3C5CF3D4BA2B76AE187D8ED3C7507D`.

## Espelho e prevalidate

- Diretório `target/shadow-local-rebuild-20260928-01/p08-v105-0328-mirror-v001-v104`:
  exatamente 104 cópias byte-idênticas V001–V104; V105 ausente. Manifesto
  0319 SHA-256 `A1C0C03F59772EDCE804733DBDFBB065517F6ADD04322D99740662332A2C6F21`.
  Inventário antes/depois do gate SHA-256
  `C47BF957E799EE8FC6CBB48A747D22734AFE13CBACAA2AF71AF71CCF46C1D618`.
  Originais permaneceram V001–V105, sem edição.
- O descritor local Flyway Maven Community 9.22.3 declara
  `flyway.locations` como System Property. Com `-Dflyway.locations=filesystem:<espelho>`,
  `flyway:info` exit 0 listou SCHEMA + V001–V104 `Success`, zero
  Pending/Missing/Failed: prova de que o espelho foi localização efetiva.
  Log privado SHA-256
  `48C07B4FD470CE34C684917BB4592A6E9FACCC85E5F036EC4DC4F1C1743E63BF`.
  `flyway:validate` normal, sem ignore, exit 0 validou 105 registros
  (SCHEMA+104 SQL); log SHA-256
  `33EB5948E3E7F9C3379A497D9683AEA16D60062C72E3FE2EE344709D0FA5CC39`.
  Esse gate **não** validou V105 aplicada. Readback SQL/socket sem delta.

## Preflight da chamada única e efeito

- Master/alvo/064 pré, backup 0325 `RESTORE VERIFYONLY WITH CHECKSUM`,
  serviço/socket, zero consumidores, manifesto 0319, diretório original com
  exatamente 105 migrations e cinco pins 0324 conferiram. Hashes de saída
  iguais a 0327: master
  `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762`,
  alvo `17B94EEBDA713DE1D4B3628C1FFD71932774E8791583F082E556ABA997C1E6F3`,
  064 `27169AF1E65F589A3E4F81FA685181262E126D992F464473C51D8C7B21760314`,
  VERIFYONLY `6DCEFC727169F3950FC50455FD57D908C75593D90E7FE5A94B2C6BE74C805B0D`.
  Um comparador local tinha literal master truncado, mas o próprio resultado
  SQL já correspondia ao hash esperado; a comparação read-only corrigida de
  todos os recibos passou. Nenhum efeito dependente ocorreu antes da correção.
- `flyway:info` **original sem override** exit 0: SCHEMA e V001–V104
  `Success`, somente V105 `Pending`, log SHA-256
  `EC0CABE65365A3564AF66C408FB5F09AAF357A37ECF228A438D2CFFA2E2C2180`.
  Cinco pins pós: V105
  `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`,
  063 `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4`,
  064 `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533`,
  inventário `4E242D9EFA5641E5C9C078FC7166BD8E1FF6F854A42851D00589D46FF792A711`,
  baseline `291871CE9B1CFF3C524CCAD8C8C4C68A67FAE3BC0672A49B803A04038CBA4126`.
- Com reserva distinta, **uma** chamada `flyway:migrate` offline/JDK17,
  diretório original e POM normal (`validateOnMigrate=true`), sem override
  locations/ignore, exit 0, log SHA-256
  `363AF1C287FCA25A586810B3F11A29BF3104078208AE56FE2A22CD0DBE00CD6C`.
  **Antes de interpretar o exit**, readback SQL independente confirmou
  106 linhas = SCHEMA+105 SQL, V105 sucesso único, zero falhas, 1.819 objetos,
  247 tabelas, 147 linhas globais (146 + uma linha Flyway), sete principals
  e sete schemas owned, serviço/socket estáveis. Recibo alvo SHA-256
  `CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8`.

## Gates após V105

| Gate separado | Observado | Recibo privado SHA-256 |
| --- | --- | --- |
| 064 snapshot read-only | Exit 0; 106/105/zero falhas; quatro collations persistidas BIN2, TVP label BIN2, agregados de auditoria/labels/receipts zero antes/depois, definições/opções revisadas iguais, bloqueadores zero | `p08-v105-0328-post-064.out` `EF6C39570B7E6D4B1C881B205E07A8EF92BFF4001E9B783AF1AA86D1B8DED1E4` |
| 063 estrutural read-only | Exit 0; `EPOCH_V105_STRUCTURAL_PASS`, zero achados | `p08-v105-0328-post-063.out` `6CE5EF46A0AA267F9AC7085E2FC3BF73D943E619643F97626D2DCB359AED967E` |
| `flyway:validate` normal original | Exit 0; 106 registros validados | `p08-v105-0328-post-validate.private.log` `AEF5660134F3E88605A1300D93B92C9F936E0AEF1E3B250344C00B86022EF603` |

Cada gate teve reserva anterior e readback SQL independente após, idêntico a
`CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8`.
Inventário final de 104 originais/cópias e cinco pins SHA-256
`A2B0E6CEA7851E39B98DC7AC61C78F0147B569926D50C4478FBC5258FAF8BFCE`.
Nenhum arquivo migration/versionado, POM ou baseline foi editado nesta unidade.
Validação documental `Test-TrilhaPreparation.ps1` em PowerShell 7.6.6 portátil
saiu 0 (33 etapas, 48 IDs abertos, nove pacotes), recibo SHA-256
`B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C`.
`git diff --check` dos três documentos rastreados e UTF-8 estrito sem BOM
dos quatro documentos tocados passaram.

## Limites e retomada imediata — até três ações

- FAILs 0326/0327 permanecem. Backup 0325 é contingência SQL verificada,
  sem prova de ausência anterior, comprimento/SHA físico ou RESTORE testado;
  `INIT` poderia ter sobrescrito órfão. Flyway avisou que SQL Server 17
  supera a versão SQL Server 16 testada por esse Flyway. Nenhum restore,
  clean/repair/drop, retry, JDBC, replay, IT, smoke, fonte real, remoto ou
  produção. Pacote Runtime ZIP
  `80CC18349268E07002E3E395FCE0239EDD95F1639F7AC81E7046C78877483499`
  é apenas evidência offline. **P08/P01–P33 seguem abertos**.
1. Supervisor revisar estes recibos e autorizar separadamente replay/IT
   JDBC sintéticos se desejar qualificação física entre componentes.
2. Banco coordenar com Runtime por handoff qualquer próxima prova SQL, com
   preflight/reserva/readback novos.
3. Preservar espelho, logs, ledger e backup; não inferir prontidão produtiva
   nem aceite integral P08 do gate estrutural.
