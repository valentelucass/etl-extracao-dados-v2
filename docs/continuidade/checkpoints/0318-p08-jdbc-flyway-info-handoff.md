# Checkpoint 0318 — JDBC local e Flyway info passaram; shadow sem schema

## Identificação, autoridade e limite

- 28/09/2026, após [0317](0317-p08-loopback-sql-pass-v3.md), SHA-256
  `8F5AB4370F8CC083902D1C377FD04182F393683303892EE092FDE6B11EB1E3A6`.
- O usuário autorizou gates locais sequenciais de JDBC/Flyway após loopback,
  com parada sem retry ante falha. O Supervisor autorizou um reattempt JDBC
  distinto após corrigir o assert, testar guarda ASCII/rollback e refazer
  preflight. O último direcionamento do usuário encerra este chat após JDBC
  e, se seguro, `flyway:info`: **nenhuma migration ou nova frente aqui**.
- Alvo único `LUCAS/MSSQLSERVER`, transporte `localhost` somente
  `::1`/`127.0.0.1:1433`, banco `ETL_SISTEMA_V2_SHADOW`. Sem produção,
  remoto, fonte real, credencial, cutover, COMMIT ou DDL nesta unidade.
  `trustServerCertificate=true` foi apenas opção explícita do teste local,
  sem aceite TLS P12. P07/P08 integrais e P01–P33 continuam abertos.

## Decisões, efeitos e evidência

| Gate | Resultado verificado | Recibo privado |
| --- | --- | --- |
| Launcher direto inicial | Falhou antes de iniciar Java por `ProcessStartInfo.ArgumentList` indisponível no PowerShell 5.1; só copiou a DLL 12.8.2 para `target/native`. Launcher v2 foi rejeitado pelo Supervisor por contornar Maven/JDK17/URL de ambiente; v2 teve apenas self-test pré-conexão. Nenhum deles abriu JDBC. | `pin-1282-jdbc-flyway-ledger.jsonl` |
| Toolchain | JDK 17.0.20.1+1 ZIP Adoptium oficial em `LOCALAPPDATA`, 190.817.615 bytes, SHA-256 `E53A79C3C3D86865BD7E787903884331068E71321714FFD44F145785AFFC7CB0`; extraído isoladamente, sem instalador/UAC/PATH global. | mesmo ledger |
| Código e perfil | `pom.xml` inclui nova `ShadowJdbcTransportReadOnlyIT` apenas no perfil `shadow-local-integration`. IT exige as duas travas, lê URL somente de `V2_SHADOW_JDBC_URL`, chama `QualificationConfiguration.validatedJdbcUrl` antes de JDBC, confirma DB/máquina ASCII/TCP/loopback/porta/objetos/histórico e faz `rollback()` em `finally`. | POM SHA `9D1313FEBF940EE4617547AF9F8452D169C9A12350E5937D4D7700E691215A98`; IT SHA `53C27AE18823D91AE94841123DE891A53ECE63CCE1604115853AF3369B57F1A6` |
| Offline final | Espelho Maven v4/JDK17 com perfil e flag: `QualificationJdbcTargetTest` 3/3 (recusas porta, `encrypt=false`, certificado ausente); IT 1 teste ASCII passou e teste de conexão pulou sem URL. Aceita `Lucas`/`LUCAS`, recusa Unicode parecido. Bytecode tem rollback no caminho normal e no handler de exceção. JAR/DLL 12.8.2 com hashes esperados; V001–V104 no espelho byte-idênticas. | `pin-1282-jdbc-ascii-guard-offline-v1.log` SHA `AEB4EDBA61745026D1E5EBC434F007EADB42A85A10D19073EEE03BEDADFC717D`; bytecode SHA `0BCC8F505E70F8F7D47FF512215C3A17BA0142BD43A1786CDFA71F01DC5E4FBD` |
| JDBC físico 1 | Maven abriu Windows auth no banco exato; IT falhou no assert `LUCAS` versus `Lucas` antes da prova de transporte. Readback master/alvo/socket sem delta. Tentativa preservada, sem retry. | log SHA `FC56FE7215524117F8047BEC707B79194F85900A1CCA360BE5680AF1285DEDE6`; report SHA `6AC7624156F6C68B2572045DB38D8EF450042D9CED2CEA7DEB80620119231B28` |
| JDBC físico 2 | Após guarda ASCII e reserva/preflight novos: Maven JDK17/IT selecionada 2/2, zero falhas/skips; URL P08 no ambiente do processo, `localhost` sem porta, Windows auth, `encrypt=true`, certificado local explícito, sem fallback. JDBC confirmou TCP loopback, alvo vazio e rollback. Readback independente: PID 20404, só dois listeners locais, master/alvo exatos, zero objetos/histórico. | log SHA `0A02A4D46304A3D48F613ADFE48CEC93DDFFD36C692B19D34581BCEF07865322`; report SHA `05DA7F43368AA01BC909719E9984013382AFD07CDCCBA0B87CBDB2C8EA768B18` |
| Flyway info | Gate distinto com preflight SQL/socket, DLL/JDK e 104 migrations byte-idênticas: Maven Flyway 9.22.3 Windows auth exit 0, 104 `Pending`, histórico ausente. Readback SQL/serviço/socket: `ctl` ausente, zero U/V/P, sem tabela Flyway e PID/listeners inalterados. Nenhum `migrate`. | `pin-1282-flyway-info-v1.log` SHA `F36DE463C300CF067D4C8F91043FD6C4F532C5166865CF55676414E6C16D3880` |

Ledger terminal privado `target/shadow-local-rebuild-20260928-01/pin-1282-jdbc-flyway-ledger.jsonl`,
SHA-256 `53E780FEE03643FD752812F8DC9AA04923E1C48C42F0497FAE327923C6485EE3`.
O log privado Flyway contém a URL local; não copiá-lo para relatório público.
Os attempts UAC/JDBC anteriores e seus recibos permanecem intactos.

## Arquivos tocados e conflitos

- Código: `pom.xml` (somente include da nova IT nesta unidade) e
  `src/test/java/br/com/esl/etl/v2/plataforma/configuracao/ShadowJdbcTransportReadOnlyIT.java`
  (novo). O restante das mudanças já sujas no repositório precedia esta
  unidade; não fazer reset, limpeza ou sobrescrita para integrar agentes.
- Estado/handoff: `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md`, este
  checkpoint, `docs/continuidade/RETOMADA.md` e atualização corrente de
  `docs/runbooks/p08-shadow-12.8.2-preflight-20260928.md` (histórico anterior
  preservado). `graphify update .` saiu 0.
- Privado/ignorado: `target/shadow-local-rebuild-20260928-01/` guarda mirrors
  v3/v4, ledgers/logs/readbacks/report/snapshot do teste falho; `target/native`
  guarda a DLL copiada; `LOCALAPPDATA/etl-v2-shadow-toolchain-20260928/`
  guarda JDK17 portátil. Não mover/apagar sem reconciliação.

## Retomada por sucessor — até três ações

1. Reconciliar `STATES.md`, working tree sujo, ledger/hash acima e
   `master`/alvo/listeners em leitura. Não repetir UAC, JDBC ou `flyway:info`
   por duração do chat; preservar os dois attempts físicos.
2. Se a autorização local ainda cobrir exatamente o passo, preparar gate
   **separado** de `flyway:migrate` V001–V104 com alvo, bytes, impacto,
   recuperação, preflight `master`/alvo vazio e reserva novos; nenhuma
   migration foi executada neste checkpoint.
3. Somente após schema realmente comprovado, qualificar `flyway:validate`,
   inventário e validações sintéticas/IT de auditoria com contagens antes/depois
   e rollback. Aceites externos P09+/P11/P12, release, remoto e cutover seguem
   independentes.
