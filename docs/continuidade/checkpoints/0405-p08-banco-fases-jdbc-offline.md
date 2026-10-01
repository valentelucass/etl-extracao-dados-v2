# 0405 — P08: fases JDBC internas, somente offline

- Data: 2026-09-30. Anterior: [0404](0404-p08-runtime-fases-timeouts-offline.md), SHA-256 `B2CD7BF3DF2F79B9653A1B128777683300A4E6EA7BCF5DF2B5A78CE3B5CF000A`.
- Instrução efetiva: instrumentar somente `JdbcExpansionLaboratory.registerContracts` no `executeBatch()` e `JdbcAnalyticReferences.importFixture` no `executeQuery()`, testar com statement falso, sem SQL/JDBC físico, perfil shadow ou reserva física. Ownership dos dois Java JDBC e índices compartilhados nesta unidade é Banco e Persistência; Runtime 0404 foi revisado e permanece intacto.
- Estado: **TESTADO_NA_CAMADA offline**. Nenhum aceite físico ou agregado P08.

## Alteração e limite

`JdbcStatementEvidence` no módulo de persistência tem exatamente dois consumidores. `contractBatch` envolve só o batch de contratos e emite `CONTRACT_BATCH`; `referenceExecute` envolve só a execução do statement de referências e emite `REFERENCE_EXECUTE`. A linha `P08_PHASE` contém duração de `System.nanoTime()` em ms, outcome e, na falha, somente tipo simples validado, SQLState de cinco caracteres validado ou `UNKNOWN`/`NONE` e vendor code numérico ou `NONE`. Não lê nem emite SQL, parâmetros, contrato, payload, identificador, mensagem, stack, URL ou segredo. O sink é contido até quando falha; resultado e exceção original retornam pela mesma referência. Assinaturas, statement, timeout, `LOCK_TIMEOUT`, retry, transação, rollback, resultado e ordem das chamadas foram preservados. As fases externas `EXPANSION_START` e `REFERENCE_IMPORT` de 0404 continuam distintas.

Arquivos da unidade e SHA-256 finais:

| Arquivo | SHA-256 |
| --- | --- |
| `src/main/java/br/com/esl/etl/v2/plataforma/persistencia/JdbcStatementEvidence.java` | `5508C45F0CDA2E5238CBE01522950B44DF253788E6414DEAF744F79F7BE9D072` |
| `src/main/java/br/com/esl/etl/v2/plataforma/persistencia/expansao/JdbcExpansionLaboratory.java` | `649BDF2FB075E72A4E158280513A6D70223699AB32958B09678F4502AC758623` |
| `src/main/java/br/com/esl/etl/v2/plataforma/persistencia/analitico/JdbcAnalyticReferences.java` | `B84AEF6468703BF50DD99C84E452D0C56CD6742036D4A5B0588D77917C93CC49` |
| `src/test/java/br/com/esl/etl/v2/plataforma/persistencia/JdbcStatementEvidenceTest.java` | `72E50DE8B473DF359B162CA9857C7B04E79F46F355CF773BAB088DD806BE845B` |

## Evidência e preservação

- O inventário `target/p08-runtime-0404-offline/source-integrity.json` foi revalidado: os cinco Java Runtime têm hashes idênticos. Os dois Java JDBC nele listados mudaram **somente** pelo import e invocação do helper nos statements autorizados; a divergência de hash é intencional. Recibos/FAILs 0404 e histórico 0354 não foram reescritos.
- Primeira chamada Maven offline falhou apenas em Spotless nos dois Java novos. `spotless:apply` formatou **somente esses dois**; a falha instrumental foi preservada na saída do turno. Chamada final JDK17 `mvn --offline -Dtest=JdbcStatementEvidenceTest test`: exit 0; Enforcer, Spotless e Checkstyle PASS; 658 fontes principais e 556 de teste compiladas; Surefire **5/0/0/0**, XML SHA `E71959492375C5AB046D2D8EF38B16812DB3798B01FBAAA41DBA01DBAE765FC3`.
- Tests com `PreparedStatement` falso exercem sucesso, `SQLException` e `SQLTimeoutException`/lock timeout nos **dois** pontos, uma emissão e uma chamada por caso, resultado/exceção por identidade, SQLState inválido e mensagens sensíveis ausentes, sink falho sem trocar resultado ou exceção. A prova delimita os dois statements; não executa procedure, batch real ou rollback físico.
- `Invoke-OfflineSecretScan.ps1`: PASS, 4162 candidatos, 4161 textos, um binário aprovado, zero achados; log SHA `7A384F9CBDEE57ED44B890D9D7ADA3533C15AAF1CEE672BEE37A1B4C0560A995`. Recibo sanitizado `target/p08-banco-0405-offline/receipt.json`.
- `graphify update .` com `PYTHONHASHSEED=0`: exit 0, grafo reconstruído com 38217 nós/90157 relações; `graphify explain JdbcStatementEvidence` mostra imports dos dois consumidores. Sem variável explícita, o launcher Windows havia saído 0 sem reconstruir o grafo; essa tentativa inócua não foi tratada como PASS.
- Fonte Java atual **1214** (658+556), duas acima de 0404. Os pins candidatos 0383 e espelhos/resultados 0395–0402 referem revisões históricas; qualquer efeito posterior exige inventário, revisão, pacote, request, reserva e preflight novos. Não foi criado pin de pacote nesta unidade.
- Validação final dos índices: `Test-TrilhaPreparation.ps1 -SelfTest` PASS (um positivo, 24 negativos, 33 etapas/48 IDs abertos), log SHA `4721067C08D845B7B866D6D36C6990883685B881FCC27205E0D393255B8671F3`; `Test-ContinuidadeAgentes.ps1` mantém **FAIL `HANDOFF_PATH`** histórico de 0383/0404, log SHA `88CFE478783F293F0FF42C06BB04D53F73BC8C411041AFA3FA8123F025993A52`. Scanner após documentos PASS, 4163 candidatos/4162 textos/zero achados, log SHA `C63C4AF222A6C1E77502CFE96D33D2E01FE3275808B94FBEF4C57E83D531975F`. `git diff --check` exit 0 e UTF-8 estrito confirmado nos quatro Java e quatro documentos da unidade.

Zero SQL/sqlcmd/JDBC físico, IT, Maven shadow, URL/DLL, UAC/OS elevado, serviço, Flyway, migration, fonte real, remoto ou produção. Nenhum ledger físico foi reservado ou alterado. Oito FAILs/74 classes faltantes de 0354, STOPs 0400/0402 e falhas instrumentais 0404 permanecem; P08, Gate 1, JaCoCo, 107 ITs e A/B físicos seguem abertos. A instrumentação prospectiva não identifica blocker, statement interno da procedure, plano ou causa histórica das esperas.

## Retomada

1. Supervisor revisa diff, XML, scanner e limite de evidência desta unidade, sem promover aceite físico.
2. Qualquer nova execução física P08 requer autoridade própria, fonte/pacote/pins atuais, guardas, reserva e readback novos; método 5 permanece sem PASS após 0402/0403.
