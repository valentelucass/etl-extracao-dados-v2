# ADR0048 — núcleo relacional sintético e recomposição local

11/09/2026. Escopo A–J explicitamente adotado pelo usuário. Decisão técnica
local; responsável e aceite de negócio reais permanecem pendentes.

## Contrato e fronteira

REL-LAB-01: origem LOCAL_SHADOW/SYNTHETIC_RELATIONAL_LAB e tenant sintético
explícitos. Um run isola os dados de cada cenário. Capturas usam os parsers,
guards, streamers, casos de uso e staging existentes, com release adicional
synthetic-relational-v1. Esse release não substitui contratos do fornecedor.
O runner aceita somente fixtures empacotadas e sessão rollback-only existente.
Main e a autorização operacional continuam dormentes.

REL-LAB-02: Manifesto /sequence_code é raiz, /mft_pfs_pck_sequence_code é pick
candidato, MDF-e conserva sua própria chave. /id de Coletas e Fretes é raiz;
aliases não são identidade. Os campos synthetic_item_key de Coletas e
synthetic_pick_item de Fretes são extensões declaradas de fixture. No caminho
real, identidade de item e seu binding continuam UNSOURCED/BLOCKED; o nome
pickItemId do sidecar GraphQL não autoriza promoção.

REL-LAB-03: cada binding contém componentes tipados originais, janela, versão,
evidência e cardinalidade explícita (ONE_TO_ONE, ONE_TO_MANY, MANY_TO_MANY).
Igualdade de alias, número, hash ou proximidade temporal nunca cria vínculo.
Repetição equivalente não multiplica vínculo; divergência na mesma revisão é
conflito. Nova revisão explícita pode substituir vínculo, com recibo histórico.
Ausência incremental não remove raiz, componente ou vínculo.

REL-LAB-04: preservam-se ABSENT/NULL/VALUE, INTEGER/STRING e zero de componente
sintético. Nanos de Coletas vêm de stg.coleta_exact_time da ADR0047; milissegundos
não são fonte de reconstrução de nanos. A projeção relacional não cria métricas
financeiras. Manifestos preserva coortes e reducers declarados; conflitos não
são resolvidos por TOP1 ou ordem de chegada. V004/V010/51428 ficam intactos.

REL-LAB-05: backlog, claims, tentativas e reconciliação são SQL. Clock injetado
governa somente o relógio lógico do laboratório; o control plane conserva seu
relógio técnico. Claims têm lease, exclusão transacional, teto de tentativas,
adiamento e quarentena. Hidratação usa o menor recorte contratável da fixture,
com limite de janela/páginas/linhas. Nenhum default é SLA de negócio.

REL-LAB-06: BOOTSTRAP/BACKFILL/REPLAY usam namespaces do control plane existente.
O consumidor de laboratório mantém recibos próprios, sem fabricar permit,
estado promocional ou avanço do watermark incremental. Reabrir o adapter na
mesma transação prova reidratação SQL, não durabilidade após commit ou crash.
O schema é instalado antes da IT; todos os dados novos são revertidos.

## Verificação e recuperação

Migrations são aditivas a partir de V029. V025 precisa de extensão aditiva da
allowlist de auditoria para 6389/6399. Nenhuma migration aplicada será editada.
Baseline recebe deltas ordenados, manifests históricos continuam imutáveis.
Recuperação estrutural é forward-fix; código é revisável pelo diff da rodada.

Resultados efetivos, tentativas e limites serão registrados no catálogo
macrobloco-relacional e em target/macrobloco-relacional-20260911-01. Este ADR
registra decisões, não declara testes aprovados ou aceite de R01/R02/V2-046.

## Refinamentos comprováveis na implementação

REL-LAB-07 (V034): filhos Pick conservam observações de todas as coortes; MDF-e
reduz pelo próprio par/chave/frescor, com conflito de número explícito. O erro51428
é verificado antes da precedência terminal dentro de uma captura. A data-alvo de
MC/CF exige captura no mesmo run/entidade/data. Testes: MatrixIT,
manifestoChildrenSurviveOlderRootCohortAndMetricsAreNotSummed,
exactConflictIsRejectedBeforeTerminalPrecedenceWithinCapture e
 equalAliasesNeverCreateBindingsAndCaptureDateMustBeDemonstrated.

REL-LAB-08 (V035): ausência de captura difere de captura vazia completa. Resumo
integral exige três entidades capturadas, sem órfão/quarentena/candidato sem
binding. Partições só concluem com recibo/evidência e backlog consistentes;
tentativa RESOLVED exige captura do alvo. Hidratação vazia é falha temporária.
Teste: absenceOfCaptureDiffersFromThreeCompleteEmptyCaptures e
hydrationEmptyResultDefersInsteadOfReportingSuccess.

REL-LAB-09 (V036): a contraprova SQL mostrou igualdade entre STRING:item e
STRING:item com espaço final mesmo em BIN2. Validamos a codificação da chave
em Java e SQL, preservando o candidato bruto inválido sem normalizá-lo para
outro identificador. Testes: paddedSqlStringsRemainInvalidCandidatesInsteadOfCollapsingIdentity
 e typedKeysPreserveZeroAndDoNotCollapseTextIntoNumbers.

A máquina de estados operacional continua sendo o control plane existente.
Captura de laboratório termina STAGED→DEGRADED/REL_LAB_CAPTURE_ONLY, sem permit
ou promoção. Savepoint delimita erro recuperável; falha que invalida a transação
é propagada e exige rollback do dono. O driver JDBC inicia a transação por
savepoint antes do primeiro comando SQL do laboratório, inclusive em sessão nova.

Composição dedicada foi escolhida porque LocalColetasFretesRuntime e os runtimes
operacionais exigem permits que este recorte não autoriza fabricar. O novo
compositor usa seus componentes reais de extração/staging e o planner existente;
não implementa outro parser, mapper, streamer ou gateway SQL de domínio. O DAG
local é MC antes de CF; CF só resolve com MC resolvida. Serviços, agenda e Main
padrão não são ativados.

A medição reutiliza ManagedPageGauge/MeasurementDiagnostics da fundação V2-050,
com contadores independentes do plano histórico fixo. Adicionou-se leitura de
contadores sem alterar os limites e contraprovas históricas. A campanha4096
excedeu seu teto240s; encerramento e rollback foram reconciliados. A campanha
qualificadora declara16/256/1024 e repetição256, heap512MiB e deadline cooperativo.
O custo de staging físico por registro e um spill no plano exploratório4096
permanecem evidência, não um SLO ratificado ou platô de heap.
REL-LAB-10 (V037): a revisão comparou a projeção nova de MDF-e com V012 e
identificou um estreitamento indevido de NVARCHAR(128) para BIGINT. A migration
aditiva restaura o inteiro canônico de até128 dígitos, com collation/presença e
constraint de dígitos; os pares conservam a coorte do filho. Provas físicas:
mdfePairsHaveTheirOwnCohortsAndPreserveArbitraryPrecisionNumbers (80 dígitos)
e divergentMdfePairAtSameChildFreshnessIsRejected. Nenhuma migration anterior
foi reescrita; o alargamento ocorre no laboratório vazio após rollback.