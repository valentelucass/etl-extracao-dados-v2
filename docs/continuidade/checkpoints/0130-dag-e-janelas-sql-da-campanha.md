# 0130 — DAG e janelas SQL da campanha

EM_EXECUCAO A–N, 13/09/2026. Predecessor0129 SHA256
c5ffadbcb1c3fba629e9e08b6203b0e68ef8d6471d7f9ac63ab5b73f9c0100e0.
Continua o pedido integral adotado em target/preparacao-macrobloco-qualificacao-pacote-20260913-01/
PROMPT-MACROBLOCO-QUALIFICACAO-PACOTE-LOCAL.md. Sem perguntas, subagentes ou
encerramento parcial. Construção37/45 e aceites67/115 preservados.

Alvo físico permanece localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado,
duas travas, dados sintéticos e rollback. Não houve DDL/migration, produção,
fonte remota, V1/dashboard, COMMIT de domínio ou publicação.

Provas na rodada target/macrobloco-qualificacao-pacote-20260913-01:

| Tentativa | Evidência observada |
| --- | --- |
| verifier-sixteen-physical-01 | QualificationScenarioVerifierIT:1IT, zero falhas/erros/skips, rollback confirmado;16raízes,19saídas e35escopos únicos |
| window-executor-physical-01 | Falha de Checkstyle por linha longa no teste; tentativa preservada, nenhuma IT executada |
| window-executor-physical-02 | QualificationWindowIT:2IT, zero falhas/erros/skips,37,69s de IT; rollback confirmado |

QualificationScenarioVerifier integra oráculos tipados, metadata JDBC, linhagem,
monitoramento, equações de fontes/fatos e DAG. Uma contraprova de referência
falha bloqueou MAT05/SQL08, preservando o ramo CAP aprovado. A prova de16raízes
também verificou a ordenação de Usuários e os hashes LOC dessa escala.

QualificationWindowExecutor consome RuntimeTemporalPlanner e aplica recortes
nas cinco procedures existentes de materialização. Confere modo, datas civis,
full_scope=false, contagens e equações nos recibos SQL. BOOTSTRAP/INCREMENTAL/
BACKFILL/REPLAY com janela02–03/04 excluíram os manifestos de01/04; lookback de
24horas os incluiu. A correção de fonte BACKFILL moveu os manifestos para02/04,
e o recorte planejado os consumiu. Blackout não executou materializações.
Antes/depois da fronteira de fonte foram idênticos em todos os recortes.

Recorte de fato não se apresenta como captura nem publicação terminal da fonte.
O cenário completo existente continua exigindo cinco materializações full e
onze recibos válidos para selar sua ocorrência. Datas arbitrárias fora da fixture
são recusadas. DST, atraso, fronteira não contígua, quatro ciclos comparados e
variantes completas ainda precisam integração/prova no pacote.

Entrypoint QualificationLaboratoryMain, QualificationSupervisor, Worker,
CaseControl, CaseExecutor e SqlEvidence foram implementados, mas ainda não
qualificados. Compilação própria campaign-entrypoint-compile-01, sessão53880,
estava ativa na criação deste checkpoint. Consultar seu result.json/process.json
antes de repetir. Não editar Java já congelado enquanto o build estiver ativo.
O journal ganhou DEFERRED explícito para caso bloqueado antes de subprocesso.
Degradações, retomada adversarial, quatro barreiras e limites ainda exigem prova;
a implementação não autoriza declarar esses mecanismos concluídos.

Próximas ações:

1. Reconciliar a compilação do entrypoint, corrigir e exercitar pacote candidato
   real; integrar degradação, ausência, quatro modos e todos os caminhos de oráculo.
2. Provar journal/filhos/recibos, cancelamento JDBC, quatro barreiras, retomada,
   concorrência física e limites; completar metadata/variantes e agenda.
3. Completar SBOM/pacote, dois builds e dois smokes independentes, escalas,
   verify integral378IT anteriores+novas, scanners/continuidade e entrega selada
   com diffs contra o inventário inicial e sucessão exata, sem alterar selos antigos.
