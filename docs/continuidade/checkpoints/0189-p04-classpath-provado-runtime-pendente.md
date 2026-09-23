# 0189 — P04: classpath provado, vínculo do oráculo pendente

## Identificação e objetivo

- Data UTC:2026-09-20T03:13:03Z. Objetivo: resolver o bloqueio e requalificar I/J.
- Anterior: `0188-p04-correcao-classpath-offline.md`, SHA-256
  `b2da624b14e50cc31654d3337a1654dc8d9e8b05c3502bbef6d0b486e5e8d035`.
- Estado: I/J IMPLEMENTADO_NAO_QUALIFICADO; classpath TESTADO_NA_CAMADA física.
- Critérios: ordem P04-REQUALIFICACAO-20260919-01 e SEQ-01/03/CP-01.

## Autorização e limites

- Pedido efetivo: resolver o bloqueio lendo a documentação, em continuação da
  requalificação original com segunda tentativa somente após correção offline.
- Duas tentativas consumidas;7200 s reservados, nenhuma terceira. Não confundir
  orçamento reservado com duração real: Maven da segunda02:52:08Z–03:07:19Z.
- Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW, sintético, integrado,
  rollback-only, commit de domínio bloqueado e duas travas Maven.
- Limites preservados:3600/1800/240/60 s,512 MiB. Sem P05/K, P06–P08, DDL,
  migration, Flyway, fonte, segredo, produção, commit ou alteração de índice.
- Ledger histórico preservado; adendo fechado:
  `target/macrobloco-p04-requalificacao-20260919-01/continuacao-0188.json`.
- Nova execução física exige nova autoridade finita; não reutilizar estes saldos.

## Alterações e decisões

- Inventário: `target/p04-desbloqueio-0188/baseline.json`,3566 arquivos e2990
  entradas anteriores. Nenhum arquivo preexistente removido; índice intacto.
- Código: QualifiedPackage verifica classpath exato selado; quatro testes novos.
- Docs: STATES, trilha, CONTRATO/SEQ-CP-01, matriz A–N, reconciliação P04 e índices.
- Causa inicial comprovada: rejeição de classpath do próprio supervisor.
  Corrigida sem reduzir integridade; quatro workers bootstrap PASS_LOCAL.
- Nova causa delimitada read-only: primeiro oráculo de sequência aponta o
  fingerprint EXPLODED_LOCAL_TEST_CLASSES_V1, não o JAR. Input/schema conferem.
  Fixture gerada na JVM de teste e copiada para o pacote cruza bindings distintos.
- Rejeitado: relaxar guard, repinar histórico ou fazer terceira tentativa.
  Não implementada segunda correção. Não alegar timeout: timedOut=false.

## Execução e evidência

| Passo/critério | Camada/limites | Observado | Evidência |
| --- | --- | --- | --- |
| Preflight | Offline JDK17/512 MiB |16/16 PASS e gates verdes | p04-fix0188-offline/result.json |
| Unidades físicas | Mesmas quatro classes |16/16 PASS | physical-02/build/target/surefire-reports |
| Sweep/preview | SQL sintético/60 s |5/5 PASS;33 previews; apply Java/SQL recusado | IntegralSweepEdgesIT |
| Cancelamento/concorrência | SQL rollback-only |3/3 e1/1 PASS | QualificationCancellationIT/QualificationConcurrencyIT |
| Retomada | Workers JAR/recibos/journal |6/6 PASS, quatro bootstrap PASS_LOCAL | QualificationResumeAdmissionIT |
| Sequência | Worker JAR |FAILED, testPassed=false, exit2, rollback=true; LOCAL_SCENARIO_ORACLE_BINDING; sem etapas concluídas | receipt da fixture privada registrada no WORKLOG/observação |
| Contenção | PID/start/ancestralidade verificados |Árvore Maven encerrada; controlador preservado | stop-observed.json |
| Reconciliação | Readback agregado local |OBSERVED/exit-1; rollback=true; before/after iguais; logs íntegros; sem remanescente | physical-02/result.json |
| Governança | Offline |Preparação/JSON/UTF-8/diff PASS; trilha FAIL histórico; scanner oito ausências anteriores | verification-final.json e WORKLOG.md |

Raiz physical-02:
`target/macrobloco-campanhas-integrais-20260915-01/p04-requalificacao-physical-02/`.
Raiz da manutenção: `target/p04-desbloqueio-0188/`.
Result SHA-256: `5669ea55e2cc92dcf129daf9ba04cc99dbbad8af1b537ac571bc205378da4038`.
Readbacks SHA-256: `3e7eb885e665e829b77133b12d33fa6816ff41d09321aee2547f111342ef6e97`.

- Efeito de resultado desconhecido: nenhum. Família de sequência interrompida;
  não há relatório JUnit final dela e isso não representa teste aprovado.
- Aceites fechados: nenhum agregado.39/45 construção e67/115 aceites preservados.
- P03/B–H somente predecessor técnico local; P05/K não elegível.
- Pendências históricas: STATES_SUCCESSION_HASH de RETOMADA e oito
  MISSING_CANDIDATE do scanner, sem novos achados de conteúdo; sem repin.
- Recuperação: delta cirúrgico contra before, sem reset/checkout/limpeza ou DDL.

## Retomada imediata — até três ações

1. Preparar correção proporcional da geração/vinculação dos oráculos ao JAR
   executado, sem enfraquecer LOCAL_SCENARIO_ORACLE_BINDING; validar offline
   positivo JAR, negativo adulterado e distinção das classes de teste.
2. Somente com nova autorização física finita e correção offline comprovada,
   criar campanha nova P04; não repetir a tentativa02 nem reutilizar os ledgers.
3. Se e somente se I/J forem integralmente aceitos, declarar P05/K elegível;
   não executar P05 sem escopo próprio autorizado.

Parada atual: duas tentativas consumidas e sequência não qualificada. Falta
correção da fixture mais autorização física nova, não “diagnóstico recursivo”.
Conclusão futura exige todos os critérios P04, não apenas workers de bootstrap.
