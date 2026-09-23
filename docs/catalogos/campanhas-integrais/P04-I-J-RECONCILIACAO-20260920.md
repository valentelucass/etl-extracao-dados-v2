# P04 — I/J: reconciliação após segunda tentativa

Ordem: P04-REQUALIFICACAO-20260919-01; continuação solicitada para resolver
o bloqueio lendo a documentação. Duas tentativas consumidas, sem terceira.
Escopo: localhost/ETL_SISTEMA_V2_SHADOW, sintético, integrado, rollback-only,
duas travas Maven;512 MiB,3600 s tentativa,1800 s sequência,240 s etapa,60 s SQL.

## Correção e evidência

QUAL_PACKAGE_RUNTIME_CLASSPATH era causal: supervisor usa JAR mais lib/*;
verificador recusava todo separador. SEQ-CP-01 admite apenas o conjunto exato
selado, sem omissão/extra/duplicata/diretório. Preflight16/16 e gates PASS.
Workers de bootstrap passaram fisicamente após a correção. Não foi alterado
o wildcard correto do supervisor, o schema, a dependência ou contrato de domínio.

A interpretação histórica de timeout por240 s de classe foi retificada em0188;
o stderr preservado apontava classpath. Ledger/checkpoints históricos imutáveis.

| Critério | Prova atual | Resultado/limite |
| --- | --- | --- |
| Worker | Quatro bootstrap PASS_LOCAL/rollback=true | Superou a recusa de classpath |
| Journal/contrato | Journal4, contrato7, supervisor1, classpath4 |16 unidades PASS |
| Retomada | QualificationResumeAdmissionIT6/6 | Recibo selado/idempotência; adulteração, evidência insuficiente e owner vivo recusados |
| Cancelamento/concorrência | Cancellation3/3; Concurrency1/1 | Driver/recursos, travas de alvo/commit e exclusão mútua; não substitui cancelamento de sequência |
| Preview/apply | IntegralSweepEdgesIT5/5 |33 previews; incompletude/owner inválido recusados; apply Java/SQL recusado |
| Sequência/recibos parciais | Primeiro receipt FAILED, exit2, testPassed=false |LOCAL_SCENARIO_ORACLE_BINDING; sem evidência de etapas concluídas; demais testes interrompidos |
| Rollback/agregados | Worker rollback=true; controlador rollbackConfirmed=true | before/after idênticos |
| Integridade/processos | Controlador logUtf8Integrity=true, sem excesso/timeout | Nenhum processo próprio remanescente |

Resultado: I/J IMPLEMENTADO_NAO_QUALIFICADO, não aceitos integralmente. P05/K
não elegível. P03/B–H permanece somente predecessor técnico local;39/45 e67/115.

## Novo impedimento delimitado

Leitura direta do primeiro oráculo e seus arquivos fixos, sem varredura recursiva:
inputSha256 confere; schemaSha256 confere; runtimeSha256 não corresponde ao JAR
e corresponde exatamente ao algoritmo EXPLODED_LOCAL_TEST_CLASSES_V1.
LocalArtifactScenario.runtimeFingerprint distingue intencionalmente classes
descompactadas e JAR. QualificationPackageFixture.includeSequenceCases gera
IntegralCampaignPackageFixtures dentro da JVM de teste e copia os membros.
O guard recusa corretamente esse cruzamento de camadas.

Próximo delta deve gerar/vincular os exemplos ao artefato executado, com provas
offline positivas/negativas. Não liberar qualquer fingerprint nem repinar
histórico para contornar o guard. Nenhuma segunda correção foi implementada.
Qualificação física exige nova autorização finita; o orçamento atual terminou.

## Evidências e preservação

- Controlador: `target/macrobloco-campanhas-integrais-20260915-01/p04-requalificacao-physical-02/`;
  Maven02:52:08Z, contenção03:07:19Z; OBSERVED/exit-1, não timeout.
- Result SHA-256: `5669ea55e2cc92dcf129daf9ba04cc99dbbad8af1b537ac571bc205378da4038`.
- before/after SHA-256: `3e7eb885e665e829b77133b12d33fa6816ff41d09321aee2547f111342ef6e97`.
- Fixture identificada no registro privado; receipt em control/case-sequence-a,
  sem payload/identidade de negócio transcritos.15 ITs concluídos; não declarar
  a família de sequência aprovada nem transformar interrupção em teste verde.
- Inventário, before e stop-observed: `target/p04-desbloqueio-0188/`.
- Adendo: `target/macrobloco-p04-requalificacao-20260919-01/continuacao-0188.json`.
- Trilha mantém falha histórica de sucessão de RETOMADA; scanner mantém oito
  MISSING_CANDIDATE preexistentes. Nenhum manifesto histórico foi repinado.
- Recuperação do delta: revisão cirúrgica contra before; não desfazer o worktree
  preexistente. Sem alteração de índice, DDL, fonte, segredo, produção ou P05.
