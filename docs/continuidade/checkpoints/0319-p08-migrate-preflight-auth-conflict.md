# Checkpoint 0319 — preflight P08 passou; V001–V104 não migradas

## Identificação e objetivo

- 28/09/2026, Builder Banco e Persistência, após [0318](0318-p08-jdbc-flyway-info-handoff.md)
  SHA-256 `461D091A7E9EC9DC5055D9A27BBEE1444EC6465887AA23484C8E7543434EC15D`.
- Objetivo: qualificar P08 depois de `flyway:info` e migrar V001–V104 somente
  quando autorização, alvo, bytes, impacto e recuperação conferissem, com
  gates separados e readback. Estado: `BLOQUEADO_POR_INPUT` para o efeito
  `migrate`; preflight local `TESTADO_NA_CAMADA` read-only.
- O usuário designou este terminal como único executor SQL e editor de
  `STATES.md`, trilha, `RETOMADA.md`, `pom.xml` e checkpoints compartilhados.
  Os outros Builders trabalharam sem SQL. Nenhum aceite integral foi assumido.

## Autorização, alvo, impacto e recuperação

- Alvo único: `LUCAS/MSSQLSERVER`, `localhost/ETL_SISTEMA_V2_SHADOW`, Windows
  auth. As consultas usaram `lpc:localhost` e database explícito. `-C` aceita
  o certificado apenas neste teste local; TLS P12 continua aberto. Sem
  produção, remoto, fonte real, credencial, deploy ou cutover.
- V001–V104 ativos: 104 nomes contíguos, 3.322.636 bytes, byte a byte iguais
  às 104 cópias do espelho v4 do checkpoint 0318; baseline referencia os 104
  nomes uma vez. `pom.xml` ativo e espelho SHA-256
  `9D1313FEBF940EE4617547AF9F8452D169C9A12350E5937D4D7700E691215A98`.
  JDK 17, JAR e DLL 12.8.2 mantêm os hashes do recibo anterior.
- Impacto esperado de `migrate`: schemas, roles, objetos SQL e histórico
  Flyway duráveis. `V002__create_v2_database_roles.sql:30` também executa
  `CREATE USER v2_schema_owner WITHOUT LOGIN` e transfere os sete schemas a
  esse usuário. A regra de sombra em `AGENTS.md` §1 proíbe criar usuário.
  Esta incompatibilidade não foi resolvida pela autorização condicional da
  rodada; nenhum `flyway:migrate` foi invocado.
- Recuperação: não há efeito de migration a reverter. Se houver decisão
  expressa que permita V002 ou migration versionada revisada, repetir
  preflight/reserva novos. Ante falha futura de `migrate`, preservar o banco,
  histórico e recibos; consultar catálogo/histórico antes de qualquer ação,
  sem `clean`, `repair`, `drop` ou retry automático.

## Execução e evidência

| Passo | Camada | Resultado observado | Recibo |
| --- | --- | --- | --- |
| Preflight v1 | cliente ODBC | exit 1 antes da sessão SQL: certificado local não confiado sem `-C`; nenhum SQL enviado | `target/shadow-local-rebuild-20260928-01/p08-migrate-0319-master-preflight.out` |
| Preflight v2 | parser SQL | exit 1 por alias reservado `clustered` na consulta read-only de `master`; alvo não consultado | `p08-migrate-0319-master-preflight-v2.out` |
| Preflight v3 | serviço/socket + SQL | exit 0/0: Express local sem cluster, alvo online, dois arquivos, collation esperada, `ctl` ausente, zero objetos de usuário/histórico; PID 20404, listeners só `::1`/`127.0.0.1:1433` | `p08-migrate-0319-master-preflight-v3.out`, `p08-migrate-0319-target-preflight-v3.out` |
| Bytes | offline | 104/104 iguais ao espelho; baseline 104 referências únicas; `Test-SchemaFoundationManifest.ps1` PASS | `p08-migrate-v001-v104-hashes-0319.txt` SHA-256 `A1C0C03F59772EDCE804733DBDFBB065517F6ADD04322D99740662332A2C6F21` |
| Checker progressivo | Windows PowerShell 5.1 | sem PASS: parser recusou concatenação em `Test-GovernedReferencesManifest.ps1`; `pwsh` 7 ausente neste ambiente | `p08-migrate-0319-static-validators.out` SHA-256 `982BEC2B9443B6441C44ACF3CF2A9D5F799FD3D711A8B70E9CB6E73746A3C40` |
| Readback final | SQL read-only | `master`/alvo exit 0/0; banco ainda vazio, sem `ctl`/histórico; PID e dois listeners loopback inalterados | `p08-migrate-0319-target-readback.out` SHA-256 `0A238C21EB3C518C7EC025037B7CE04F12DCD9DA8A88F9CAAF00A8FDF3C81F62` |

Ledger físico novo e terminal:
`target/shadow-local-rebuild-20260928-01/p08-migrate-0319-ledger.jsonl`,
SHA-256 `AC1DFAA91EC56A1C701D29F59144FE1860F82F1075D5AAF9701C80A98223F12C`.
O ledger 0318 SHA-256
`53E780FEE03643FD752812F8DC9AA04923E1C48C42F0497FAE327923C6485EE3`
e os logs falhos permanecem intactos. O `CONTEXTO_GLOBAL.md` não existia no
relativo `../` prescrito; `STATES.md`, `AGENTS.md`, `RETOMADA.md`, runbook e
checkpoint anterior foram lidos. Nenhuma migration, `flyway:validate`, DDL,
DML, validação sintética pós-schema ou IT de auditoria foi executada.

## Handoffs paralelos e preservação

- Fontes e Contratos: read-only, sem trabalho local elegível P09–P15;
  G01/G03 externos permanecem.
- Regras de Negócio: `LigarReferenciaTemporalColeta.java` e
  `ColetaTemporalLinkTest.java` retêm tempo nativo Data Export quando
  `statusAtUtc` da referência é nulo. Handoff RED `REFERENCE_INVALID`, GREEN
  56 focados/prova sintética; XML da classe no checkout: 29 testes, zero
  falhas/erros. P16 permanece aberto.
- Runtime e Qualificação: `QualificationLaboratoryMain.java` e novo
  `QualificationCommandOptInTest.java` exigem duas travas antes do pacote em
  `run`/`resume`/`worker`. Handoff RED `QUAL_PACKAGE_PIN`, GREEN nove focados,
  Spotless/Checkstyle offline; XML da nova classe: um teste, zero falhas/erros.
  P08 permanece aberto.
- Diffs estreitos e `git diff --check` conferidos. Nenhum Java, migration,
  baseline, `pom.xml`, ledger histórico ou código de outro Builder foi editado
  por este terminal. O worktree sujo foi preservado. Essas provas locais não
  compõem gate integrado nem aceite de P08/P16.
- `Test-TrilhaPreparation.ps1` recusou antes de validar por exigir PowerShell
  7; o terminal só dispõe de Windows PowerShell 5.1. Log privado
  `target/shadow-local-rebuild-20260928-01/p08-0319-trilha-validator.out`.
  `graphify update .` saiu 0 após os handoffs de código.

## Retomada imediata — até três ações

1. Usuário/owner resolve explicitamente o conflito `CREATE USER ... WITHOUT
   LOGIN` de V002 versus `AGENTS.md` §1, ou fornece migration versionada
   revisada. Sem isso, não executar V001–V104.
2. Após decisão e checker progressivo válido em PowerShell 7, congelar bytes,
   conferir `master`/alvo, reservar novo gate e executar uma vez
   `flyway:migrate`; readback independente de histórico/catálogo antes do
   `flyway:validate` separado.
3. Se schema e validação passarem, classificar `database/validation/` e IT de
   auditoria, provar dados sintéticos rollback-only com contagens agregadas
   antes/depois; integrar os handoffs sem promover aceite integral.
