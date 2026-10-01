# Checkpoint 0329 — P08 V105: IT selecionada falhou antes de JDBC; zero delta

## Identificação, autorização e limite

- 29/09/2026, Builder Banco e Persistência. Anterior:
  [0328](0328-p08-v105-migrada-gates-estruturais-pass.md) SHA-256
  `8ED4E9BE1437A4BD19F8A2188E4724FE10078F2F075A4B58AA7D9DAE7B37D8FF`.
- Supervisor autorizou **uma** execução física apenas do método
  `ExpansionLaboratoryReferencesIT#caseOnlyLabelReplayUsesContentComparisonAndRollsBack`
  em `localhost/ETL_SISTEMA_V2_SHADOW`, após preflight/reserva novos, JDK17,
  Maven offline e as duas travas do perfil. Falha ou incerteza exige log/report,
  readback e parada sem retry/fallback. Sem outras ITs, replay SQL, smoke,
  restore, DDL, fonte real, remoto ou produção.
- Ledger físico exclusivo
  `target/shadow-local-rebuild-20260928-01/p08-v105-0329-selected-it-ledger.jsonl`
  SHA-256 `EB3CCC1E6B067D964166E3AFDC1666497F852C1F37127BC1DF996CDA07C2E8D9`.
  O worktree e ledgers anteriores foram preservados.

## Preflight e impacto

- IT SHA-256 `55E2607D34C6CC84034A3F7828B00C62D9F14A2034C3A1293F8245382D67AC71`,
  V105 `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`,
  063 `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4`,
  064 `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533`.
  Recibos 0328 de 063 PASS `6CE5EF46A0AA267F9AC7085E2FC3BF73D943E619643F97626D2DCB359AED967E`
  e Flyway validate normal PASS
  `AEF5660134F3E88605A1300D93B92C9F936E0AEF1E3B250344C00B86022EF603`
  conferidos. JDK17.0.20.1 e DLL 12.8.2 SHA-256
  `02DB7B0053C4A65B622EF53ECCB8B55687FDA16AE808F96DC73ACC7340C89C6E`.
- Consulta agregada privada somente leitura, sem payload/ID,
  `p08-v105-0329-selected-it-counts.sql` SHA-256
  `8025F3A739477ACFE20145ABD09628508EAFB1B30EBAA3D68372193F549CDBC4`.
  Preflight `sqlcmd -S lpc:localhost -E`, master e banco exatos, shared
  memory/NTLM, serviço PID 20404, listeners apenas `::1`/`127.0.0.1`,
  zero consumidores. Histórico 106 = SCHEMA+105 SQL, zero falhas, V105 sucesso.
  Quatro recibos read-only:

| Camada | Antes SHA-256 | Resultado |
| --- | --- | --- |
| master | `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762` | alvo/consumidores/listeners locais corretos |
| alvo | `CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8` | 106/105/zero falhas, 1.819 objetos, 247 tabelas, sete principals/schemas |
| contagens independentes | `3C1015B95C285C7926C5A10F6F7C22666DB6FECC4D28E4338B544C57066B1BC6` | releases, receipts, labels, selections, calendar/branch/payer, auditorias e staging sintético = zero |
| 064 snapshot | `EF6C39570B7E6D4B1C881B205E07A8EF92BFF4001E9B783AF1AA86D1B8DED1E4` | V105/BIN2 e agregados sem desvio |

Impacto planejado: inserir dados sintéticos na transação de uma conexão
compartilhada, provocar `EXP_REF_CONTENT_DIVERGENT`/erro 53437 em replay
com diferença só de caixa, verificar `XACT_STATE`, e fechar com `ROLLBACK`.
Em resultado incerto, consultar estado autoritativo sem repetir o método.

## Execução única, falha e readback

- Com reserva física distinta, **uma** chamada Maven offline/JDK17,
  `-Pshadow-local-integration`,
  `-Dshadow.local.integration.enabled=true` e
  `-Dit.test=ExpansionLaboratoryReferencesIT#caseOnlyLabelReplayUsesContentComparisonAndRollsBack`,
  compilação de testes e Failsafe selecionados, saiu **1**. URL local foi
  montada só no ambiente privado do processo, sem literal no comando/log e
  sem alteração do PATH global. Failsafe: **1 teste, 0 failures, 1 error,
  0 skipped**; nenhuma outra IT executada. Log privado SHA-256
  `49DD848CE6BD23559904A919AA6B4BD61965F73AC1368C0EB71B3FA6598CDAA4`.
- Falha causal em `ShadowStorageProperties.validateJdbcUrl`, antes de
  `DriverManager.getConnection`, ao iniciar o método na linha 142.
  Diagnóstico offline da mesma expressão PowerShell: propriedades foram
  agrupadas em **um segmento de 134 caracteres**, em vez de seis; o valor
  inicial de `databaseName` continha texto adicional. O banco aprovado
  apareceu no prefixo, mas a igualdade exigida pelo guard falhou. Não houve
  conexão JDBC nem execução do corpo SQL do teste. **Erro 53437,
  `XACT_STATE` e rollback não foram observados**; não reivindicar PASS.
- Logs/report preservados em `target/shadow-local-rebuild-20260928-01/`:
  TXT SHA-256 `121ECE2AF5FF076EA00C840EBAAAD2217492D3B60DF8E630571F3885FDD99B27`,
  XML `3EE47A8C5B1F1F28EA3BA4B21BC6346966505729AEA5785F1228EB166E67C1C2`,
  summary `46FE5B2ABBAD8C22D311BF6557AFB16CEA5EBE919538FBC6480046421122D451`.
  Summary anterior copiado antes da execução, SHA-256
  `4A1C7C8F9E093D6D03A543ACD5755688818946AF8D0F3FC7B2BADCFF30E3E061`.
  Checagem textual confirmou ausência de URL JDBC literal nesses recibos.
- Readback após FAIL, com reserva própria: master/alvo/contagens/064 exit 0 e
  **os mesmos quatro SHA-256 da tabela**, PID/listeners inalterados.
  Histórico V105 106/105/zero falhas; todas as contagens zero. V105/063/064/IT
  mantêm os pins acima; recibo Flyway validate 0328 íntegro. Não houve
  segundo teste, validação Flyway nova, retry ou fallback. Nenhum efeito SQL
  detectado, mas a prova funcional de rollback **não foi exercitada**.
  Validação documental `Test-TrilhaPreparation.ps1` em PowerShell 7.6.6
  saiu 0 (33 etapas, 48 IDs abertos, nove pacotes), recibo SHA-256
  `B631C3CF43CFD377A3CB36774F1CF30171075154BF66E01D6B783E164225D75C`.

## Retomada imediata — até três ações

1. Supervisor revisar o FAIL e uma montagem de ambiente com seis segmentos
   explícitos antes de decidir se autoriza **nova** tentativa selecionada;
   esta unidade não autoriza retry.
2. Se houver autoridade nova, Banco refaz preflight, contagens, 064 e reserva
   antes de qualquer efeito; Runtime não executa SQL.
3. Preservar logs/ledgers/FAILs 0326/0327, backup 0325 com limites de
   ausência/tamanho/SHA físico/restore e pacote Runtime offline. P08/P01–P33
   seguem abertos; sem aceite integral, smoke ou produção.
