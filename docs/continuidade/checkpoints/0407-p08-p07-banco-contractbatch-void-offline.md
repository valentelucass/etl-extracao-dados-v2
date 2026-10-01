# 0407 — P08/P07: retorno void do batch JDBC, somente offline

- Data: 2026-09-30. Anterior: [0406](0406-p08-p07-base-offline-fail-sem-candidatos.md), SHA-256 `E502C0AD395DCA87D55CDD6DAAE01018293A3019BE69F290C472A5DC0FAE6257`.
- Instrução efetiva: corrigir somente as duas sobrecargas `JdbcStatementEvidence.contractBatch` e o teste falso sob ownership Banco; não alterar arquitetura, SQL, parâmetros, timeouts, transação, rollback, Runtime 0404 nem manifests Runtime 0406; zero efeito físico ou pacote A/B. Estado observado: **TESTADO_NA_CAMADA offline**; STOP_BASE 0406 preservado.

## Correção causal

O `clean verify` 0406 havia recusado as duas sobrecargas porque o `int[]` de retorno parecia uma coleção sem contrato de limite para `ArchitectureRulesTest.persistenceAdaptersMustNotMaterializeAKeyUniverse` e `productiveCollectionApisMustHaveAnExplicitBoundedContract`. O único callsite, `JdbcExpansionLaboratory.registerContracts`, já ignorava esse array. Ambas as sobrecargas agora retornam `void`; a operação temporizada ainda invoca `statement.executeBatch()` **uma vez** e descarta somente seu `int[]`. O statement, ordem, duração monotônica, linha sanitizada, outcome e exceção original permanecem. `referenceExecute` e sua identidade de `ResultSet` não mudaram. Nenhuma regra/allowlist/threshold foi editada.

Arquivos da unidade e SHA-256:

| Arquivo | SHA-256 |
| --- | --- |
| `src/main/java/br/com/esl/etl/v2/plataforma/persistencia/JdbcStatementEvidence.java` | `2F98F1AA95FC863B586E4BF60B4C5E50586CBC2AEFE1639F89975298A2A703AF` |
| `src/test/java/br/com/esl/etl/v2/plataforma/persistencia/JdbcStatementEvidenceTest.java` | `CA2AD230A37EF3EA04BC88C2320DC2AC7772FE5F57E5BCEE47853473C32F927A` |

## Provas e limites

- `mvn --offline -Dtest=JdbcStatementEvidenceTest,ArchitectureRulesTest,CiCoverageScopePolicyTest test` com JDK17, sem perfil shadow, URL, DLL ou conexão: **exit 0**, Enforcer/Spotless/Checkstyle PASS, 658 fontes principais e 556 testes compiladas. Surefire **25/0/0/0**: arquitetura classe inteira 17/0/0/0 (inclui os dois métodos que falharam em 0406), escopo CI 3/0/0/0, JDBC falso 5/0/0/0. Log SHA `9286F412CB5A3324903D1C65C759EE54B10AF8ADA595B0383DE7F0D90435D9DA`; XMLs respectivamente `1CFCAB48208FA687D69F936DF1BFDDE4DC5DBF183D31D2FDE955E73C21F12C08`, `21A00A455A372CA252ECE7B6160FDC034CB62B123477DB3114E8E1C23CB6DACE` e `C4130471D7E256C2EDFCB2BC67D9558197579DA541A9EE77FF17E456923FCB5E`.
- Statement falso prova uma chamada do batch no sucesso, emissão única e sanitizada, `SQLException`/`SQLTimeoutException` de lock timeout por identidade, e sink falho contido. O teste de referência mantém `ResultSet` por identidade; falhas e sink seguem testados. `javap` do bytecode compilado mostrou ambas as assinaturas `contractBatch` como `void` e ambas as `referenceExecute` como `ResultSet`.
- Scanner offline PASS: 4164 candidatos/4163 textos, um binário permitido, zero achados, log SHA `D58A1C4343141EB75B7A19DEE8630D4BBF14644C04E3568183D7B60B29A83123`. `graphify update .` com `PYTHONHASHSEED=0` exit 0: grafo 38233 nós/90188 relações; log SHA `E9046F51CC5FB867FA1CB977A44E150E41A0228DD738742D31439D3EA39C3585`.
- Recibo sanitizado `target/p08-banco-0407-offline/receipt.json`. `git diff --check` exit 0; UTF-8/índices conferidos ao final. O snapshot/revision diagnóstica de 0406 e os pins 0383 continuam históricos; o patch não produz candidato A/B nem permite reusar qualquer inventário anterior.
- Validadores finais: `Test-TrilhaPreparation.ps1 -SelfTest` PASS (um positivo/24 negativos, 33 etapas e 48 IDs abertos), log SHA `4721067C08D845B7B866D6D36C6990883685B881FCC27205E0D393255B8671F3`; `Test-ContinuidadeAgentes.ps1` mantém **FAIL `HANDOFF_PATH`** histórico, log SHA `88CFE478783F293F0FF42C06BB04D53F73BC8C411041AFA3FA8123F025993A52`. Scanner após os índices PASS, 4165 candidatos/4164 textos/zero achados, log SHA `E7F4523D31758A64A50B9E02F130AE831F2972E453A6A93E2EB6ECF61F375B47`. UTF-8 estrito dos dois Java e quatro documentos da unidade e `git diff --check` exit 0.

Zero SQL/JDBC físico, Maven perfil shadow, pacote A/B, `clean verify` integral, UAC, serviço, Flyway, migration, fonte real/remoto/produção ou ledger físico. O **FAIL/STOP_BASE 0406**, seus outputs e dois FAILs dirigidos não foram reescritos. Oito erros/74 classes faltantes 0354, STOP 0402 e método 5 sem PASS, P08/P07/Gate 1/JaCoCo/107 ITs/A-B físicos permanecem abertos. A prova focada não equivale ao novo gate integral Runtime.

## Retomada

1. Supervisor revisa este diff e as três suítes focadas sem promover aceite do gate.
2. Runtime, em unidade própria, congela novos bytes e executa o gate integral offline antes de avaliar pacote A/B, respeitando a decisão do Supervisor sobre uso do perfil na fase de pacote.
