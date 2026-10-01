# 0344 — P08: handoff Runtime pós-0342 integrado sem efeito físico

- Data: 2026-09-29 UTC; anterior: [0343](0343-p08-auto-stats-0342-revisao-offline.md), SHA-256 `03E2C81F16EB8DE92936D8BE956443C0A3DC15A036327FAE4711E2322B715326`.
- Autoridade desta unidade: integrar o handoff Runtime em `STATES.md`, trilha, retomada e checkpoint; conferir hashes dos dois arquivos Runtime e do teste novo sem editá-los. SQL, JDBC, IT, Flyway e smoke vedados. Runtime empacota novos bytes offline em paralelo.
- Estado: **prova diagnóstica offline 33/33; causa SQL das sete falhas 0342 ainda desconhecida; P08 e Gate 1/2 abertos.** Nenhum gate físico novo foi executado ou concluído.

## Alteração Runtime observada, sem edição pelo Banco

| Arquivo | SHA-256 observado | Escopo relevante |
| --- | --- | --- |
| `src/main/java/br/com/esl/etl/v2/bootstrap/LocalAnalyticQuotesRuntime.java` | `8E6169527043189297C8F516C2D07752A32C70960280CE2204CFD748D7337320` | `ANA_QUOTE_CAPTURE_*` passa a encadear a causa de falha preservada na sessão de recuperação quando disponível. |
| `src/main/java/br/com/esl/etl/v2/bootstrap/QualificationWorker.java` | `498BCEBA20D55182046108073BEF6EC9E2A2D96753E57AD119AEA5AFE422C627` | Recibo de falha extrai código fechado e número SQL positivo de cadeia de causas limitada, sem texto do driver. |
| `src/test/java/br/com/esl/etl/v2/bootstrap/QualificationWorkerFailureEvidenceTest.java` | `75A5C3DE4F448808228CC4553FBAC13894614FD9BFAC1814A6E5A817EF377764` | Dois testes verificam causa aninhada/número SQL **sintético** e omissão de texto privado ou número inexistente. |

Os arquivos Java têm outras mudanças acumuladas no worktree; estes são hashes dos arquivos inteiros, não atribuição de todo o diff à correção pós-0342. O número `53721` do teste é fixture, **não diagnóstico SQL do shadow**.

Três XML Surefire existentes, datados de 2026-09-29 07:28:51–52 UTC, selecionam `QualificationWorkerFailureEvidenceTest,RuntimeUsersSessionTest,LocalRuntimeIntegrationTest` em Java 17.0.20.1. Contagens observadas: 2 + 4 + 27 = **33 testes**, zero failures/errors/skips. O Banco leu relatórios e código; **não executou** Maven nem repetiu testes nesta unidade. Essa prova cobre a serialização sanitizada de erro e regressões adjacentes offline. Não demonstra qual exceção ou SQL error number ocorreu nos sete casos físicos 0342, nem PASS físico do diagnóstico, da composição ou do pacote A/B.

## Limites preservados e retomada

O Gate 1 0342 permanece **FAIL**: sete `ANA_QUOTE_CAPTURE_RECOVERY_REQUIRED`, `OUTCOME_UNKNOWN` não comprovado. O inventário geral 873→1088 grupos/215 autoestatísticas novas e o `P08_STATS_COUNT_MISMATCH` permanecem fatos, com as limitações de identidade e origem do [0343](0343-p08-auto-stats-0342-revisao-offline.md). O `064` estável não inventaria stats globais. Gate 2 A/B não foi iniciado; pacote novo ainda em preparação offline, sem pin ou qualificação física nesta unidade. Preservados FAIL 0336/0341, backup 0325 sem restore testado e todos os recibos anteriores.

1. Runtime conclui e entrega seus próprios pins, testes e pacote candidato ao Supervisor; Banco não edita esses arquivos.
2. Supervisor decide, com critério explícito para autoestatísticas e escopo/limites novos, se autoriza um gate físico diagnóstico; a prova offline 33/33 não o autoriza por si.
3. Se houver nova autorização, Banco reserva/preflight/readback próprios e obtém a causa sanitizada do caso selecionado antes de avaliar qualquer repetição de Gate 1 ou smoke A/B.

Nenhum ledger físico novo, consulta SQL/JDBC, IT, Flyway, smoke, DDL, reset, restore, fonte real, remoto ou produção nesta unidade. Nenhum aceite P08 promovido.
