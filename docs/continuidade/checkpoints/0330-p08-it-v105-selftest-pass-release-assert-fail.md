# Checkpoint 0330 — P08: URL self-test PASS; IT falhou no assert de releases

## Identificação, autoridade e limite

- 29/09/2026, Builder Banco e Persistência. Anterior:
  [0329](0329-p08-it-v105-url-guard-fail-sem-delta.md) SHA-256
  `033031B8317D40319FCADADBD0F542948B8F77B033EE7A256C830727129BF8CD`.
- Supervisor autorizou corrigir **apenas** a montagem de
  `V2_SHADOW_JDBC_URL` no ambiente privado do processo; self-test offline
  obrigatório no mesmo `ShadowStorageProperties` sem conexão; após PASS,
  uma nova execução física somente do método
  `ExpansionLaboratoryReferencesIT#caseOnlyLabelReplayUsesContentComparisonAndRollsBack`
  no `localhost/ETL_SISTEMA_V2_SHADOW`, com perfil e duas travas. Falha exige
  log/report, readback e parada sem retry/fallback. Banco não editou código,
  IT, migration, baseline ou POM. Nenhum package smoke, replay SQL adicional,
  restore, DDL, remoto ou produção.
- Ledger físico
  `target/shadow-local-rebuild-20260928-01/p08-v105-0330-selected-it-ledger.jsonl`
  SHA-256 `26473FAF433E01E71BA7B6E7C094FACBBAAD5A7B3EA4C57E32B313A9F5998B2F`.
  FAIL 0329 e recibos anteriores preservados.

## Self-test offline e pins

- IT SHA-256 `55E2607D34C6CC84034A3F7828B00C62D9F14A2034C3A1293F8245382D67AC71`,
  V105 `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`,
  DLL 12.8.2
  `02DB7B0053C4A65B622EF53ECCB8B55687FDA16AE808F96DC73ACC7340C89C6E`,
  JDK17.0.20.1. Scripts privados em `target/shadow-local-rebuild-20260928-01/`:
  montagem `p08-v105-0330-process-env.ps1` SHA-256
  `00821C72C15434CEB27F856EAF8A0CEC189DB8B955431BAA6CFA5130839DF497`,
  autoensaio Java `P08ShadowUrlGuardSelfTest.java` SHA-256
  `6A004F32874DA9F8A4B1CE3009BA22654DA81F0B0D3B7CF023B2FF778A1B5613`,
  runner `p08-v105-0330-run-gate.ps1` SHA-256
  `53ECA8EEE8EC3CC0604AC47E3BD08E946A3D05F006C16C5E1F83B3E0D7B307AD`.
- A primeira chamada offline não iniciou Java: Windows PowerShell 5.1 negou
  carregar o script temporário por política de execução. Sem JDBC/SQL.
  O runner portátil PowerShell 7.6.6 já verificado executou **o mesmo**
  script de ambiente. Self-test exit 0, log sanitizado SHA-256
  `9C097EB46D1DE56FC5B1F4B9CAF77D1873DEFFE931B1751B1361CBDF6A663F22`:
  **seis segmentos separados por `;`**, chaves exatamente
  `databaseName,integratedSecurity,encrypt,trustServerCertificate,loginTimeout,socketTimeout`,
  host `localhost`, banco exato, autenticação integrada e nenhum campo
  usuário/senha/domínio. `ShadowStorageProperties.enabled(LOCAL_EPHEMERAL)`
  passou; nenhum `DriverManager` ou conexão foi chamado. URL literal não
  apareceu no recibo. Só após esse PASS ocorreu preflight físico.

## Preflight, execução única e readback

- Com reserva, `sqlcmd -S lpc:localhost -E` em master/alvo/consulta global/064,
  serviço PID 20404, listeners somente `::1`/`127.0.0.1:1433`,
  zero consumidores. Histórico 106 = SCHEMA+105 SQL, zero falhas, V105
  sucesso; releases/receipts/labels/selections/auditorias globais zero.
  Quatro SHA-256 pré e pós idênticos:

| Consulta | SHA-256 |
| --- | --- |
| master | `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762` |
| alvo | `CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8` |
| contagens independentes | `3C1015B95C285C7926C5A10F6F7C22666DB6FECC4D28E4338B544C57066B1BC6` |
| 064 | `EF6C39570B7E6D4B1C881B205E07A8EF92BFF4001E9B783AF1AA86D1B8DED1E4` |

- Reserva física distinta antecedeu **uma** chamada Maven offline/JDK17 pelo
  runner privado, `-Pshadow-local-integration`,
  `-Dshadow.local.integration.enabled=true` e `-Dit.test` do método exato.
  Failsafe executou **1 run, 1 failure, 0 errors, 0 skipped**. Passou do
  guard de URL e abriu JDBC; `importPackaged` retornou em duas chamadas,
  mas o assert `ExpansionLaboratoryReferencesIT.java:149` esperava quatro
  releases em `referenceState` e encontrou zero. Log privado SHA-256
  `272E32ED8204452F55CD12944155CD31BFCE8C27E566A4A9BEFC34A84E222F10`.
  Report TXT SHA-256
  `BAF3160B6F9FC8E6CD23B53DC3A338BE2E504392BEC15415C609D060FB23F044`,
  XML `479E1CFAB389F5984712F0FA63B46FCF32AD3FD4F4AFF2DE871CED2749ACDC56`,
  summary `9C7CF9D2659E0A3D0ABFC897105A53B38BF1FC61B13E602BA07124AD2B587C69`.
  Logs/report sem URL literal. **Replay com diferença só de caixa, erro
  53437 e assert de `XACT_STATE` não foram alcançados.**
- Readback reservado após o FAIL: as quatro consultas da tabela tiveram
  exit 0 e hashes **idênticos** ao pré, histórico 106/105/zero falhas,
  contagens globais zero, PID/listeners e pins estáveis. Nenhum delta
  persistido. A sessão fechou no caminho de exceção, mas a prova funcional
  de rollback pretendida permanece pendente. Sem nova IT/retry/fallback.
- Hipótese **não comprovada**, derivada só da leitura de V042/V105 e da IT:
  `scope_code` tem collation BIN2 e é montado de `uniqueidentifier` no
  SQL, enquanto a consulta do teste concatena o UUID Java como texto.
  Diferença de caixa na representação poderia explicar os zero releases.
  Runtime/Supervisor devem investigar; Banco não alterou a IT nem fez
  consulta SQL extra após o readback.
- Validador documental offline `Test-TrilhaPreparation.ps1` exit 0:
  33 etapas, 48 IDs abertos, 9 pacotes, `executionAuthorized=false`;
  log SHA-256 `B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C`.

## Retomada imediata — até três ações

1. Supervisor/Runtime revisar causalmente o assert da IT e a hipótese de
   representação UUID/BIN2; qualquer correção de teste exige diff/teste
   offline próprios, sem alterar V105 aplicada.
2. Só com nova autoridade, Banco refaz preflight/reserva antes de outra prova
   física selecionada; esta unidade consumiu sua única execução.
3. Preservar FAILs 0329/0330, logs/ledger, limitações do backup 0325 e
   pacote Runtime offline. P08/P01–P33 abertos; sem aceite integral,
   package smoke, fonte real ou produção.
