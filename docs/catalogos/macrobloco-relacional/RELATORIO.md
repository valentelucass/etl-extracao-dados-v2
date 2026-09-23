# Macrobloco relacional — relatório da construção local

RELATIONAL_LOCAL_COMPLETE. As dez frentes A–J foram implementadas, integradas
e verificadas no escopo local adotado; entrega e continuidade conferidas.
Escopo adotado: A–J, sem B64 ou aceite externo novo.

Manifestos e Fretes capturados deixam uma Coleta órfã em backlog SQL. O executor
reclama o menor recorte de fixture, captura a Coleta pelo pipeline existente e
resolve MC/CF sem multiplicar raízes. Histórico, delta, backfill e replay usam
o control plane/planner existentes; reabrir o adapter lê o progresso do SQL.
O runner fornece quatro comandos concretos e status degradado quando há pendência.

[Matriz A–J](MATRIZ-A-J.md), [contratos](CONTRATO.md), [comandos](COMANDOS.md) e
[ADR0048](../../adr/0048-nucleo-relacional-sintetico-e-recomposicao-local.md)
descrevem o comportamento, as invariantes e a fronteira de autorização.

## Provas executadas

| Camada | Resultado observado | Evidência na rodada |
| --- | --- | --- |
| Maven verify completo, Java17, offline, heap512MiB | 1.582 testes,0 falhas,0 erros,4 skips preexistentes; formatter, Checkstyle, arquitetura e cobertura aprovados | logs/verify-04.log; verify-04-surefire/ |
| Perfil shadow-local-integration, JDBC/SQL real | 95 IT,0 falhas/erros/skips; inclui39 IT anteriores de auditoria/Coletas temporal | verify-04-failsafe/ |
| JAR em processo | Dez casos passaram: scenario/hydrate/replay0, status10, recusas20; nenhum timeout; rollback confirmado | jar-01/result.json |
| Escala e planos |16/256/1024 raízes por entidade e repetição256 passaram;12 planos SQL reais | scale-verification.json |
| Schema e resíduos |37 migrations no baseline; constraints/FKs/índices/revisões conferidos;14 tabelas novas sem resíduos e129 preexistentes com contagens preservadas | schema-validation-final.json e SQL061 |
| Fontes/pacote |797 fontes Java finais conferidas com o build; 645 classes produtivas do JAR conferidas byte a byte | java-verification.json; packaged-class-verification.json |
| Segredos | Scanner offline integral:2527 candidatos,2526 textos,1 binário verificado,0 findings; 11 contraprovas e delta candidato de67 arquivos também passaram | security-offline-01.json e logs/security-offline-01.log |

O verify final inclui a matriz física completa com40 testes, inclusive CF1:N e MDF-e com80 dígitos, coortes independentes e conflito. Os quatro skips continuam skips. Prova Java/proxy, SQL físico, JAR e origem real são camadas distintas.

## Escala observada

| Raízes por entidade | Linhas físicas totais | Links ativos | Duração | Pico de heap observado |
| --- | --- | --- | --- | --- |
|16 |96 |32 |1,195s |160,099MiB |
|256 |1.536 |512 |10,279s |166,099MiB |
|1.024 |6.144 |2.048 |40,077s |166,751MiB |
|256, repetição |1.536 |512 |9,686s |165,960MiB |

Cada raiz aparece duas vezes; página17 faz duplicatas atravessarem páginas.
Medição pelos objetos reais de página e pelo início/fim do staging síncrono:
máximo1 página e1 lote em voo, até17 registros por lote observado, zero páginas
gerenciadas ao final. O limite geral do pipeline continua100 por lote.
ManagedPageGauge/MeasurementDiagnostics da fundação V2-050 foram reutilizados
sem modificar os planos históricos. A variação da repetição mostra ruído/custo
de execução; estes números de heap não comprovam platô ou SLO produtivo.

Os12 planos aprovados usam seeks nos índices de captura/componente, raiz/link
e elegibilidade de backlog. Há sorts de dedupe/ordenação e joins selecionados
pelo SQL, incluindo Index Scan no dedupe da escala16. Não foram observados spills ou PlanAffectingConvert nesses12 planos.
As consultas usam run e chaves tipadas; não há função sobre a coluna de
elegibilidade no predicado. O resolver faz operações sobre conjuntos em SQL.

A tentativa exploratória4096 excedeu o teto240s durante o custo crescente de
staging por registro. Chegou a produzir planos antes do encerramento do fork,
inclusive1 spill no dedupe; não produziu recibo final aprovado. O fork próprio
foi encerrado, a falha Maven preservada e os agregados reconciliados após rollback.
A campanha seguinte declarou16/256/1024/256 e acrescentou deadline cooperativo.
Não se afirma aprovação de4096, ausência de todo spill possível ou throughput
produtivo. O runner tem teto32 raízes por partição, dentro da faixa comprovada.

## Evolução e revisão

V029–V031 criaram o laboratório; V032 ligou partições; V033 corrigiu collation
de OPENJSON; V034 conservou coortes de filhos/data do alvo e o conflito51428;
V035 fechou reconciliação/vazio/hidratação; V036 bloqueou padding de chave; V037 conservou o número MDF-e canônico NVARCHAR128, sem estreitamento BIGINT.
Instalações em seis diretórios com preflight master/Windows e qualificação
com rollback. Nenhuma migration aplicada ou manifesto/checkpoint antigo foi
reescrito. O baseline inclui V001–V037 em ordem.

As falhas de build/IT/collation/binding de contrato, estilo, catálogo de APIs
limitadas e orçamento de escala permanecem em logs e ledgers. Foram corrigidas
sem reduzir gates. O driver SQL Server existente passou de runtime para compile
scope para usar TVP tipado; nenhuma versão de dependência foi alterada.

A base do diff é o inventário2494 desta tarefa e suas cópias `before/`, não HEAD.
Snapshots dos arquivos preexistentes alterados ficam em
`docs/continuidade/historico/macrobloco-relacional/`. O manifesto novo descreve
os deltas; os validadores predecessores leem os snapshots da sua fotografia.
O diff revisável e o inventário final ficam na rodada, com fontes e hashes.

## Limites que permanecem

Somente `localhost/ETL_SISTEMA_V2_SHADOW`, autenticação Windows já existente,
migrations separadas e DML sintético sempre revertido. Sem API de fornecedor,
leitura de .env/segredos, grants, reset, host/banco adicional, V1/dashboard,
scheduler, produção, deploy, publicação, commit ou push.

Não houve banco vazio/reset autorizado: equivalência física de fresh install
contra upgrade não foi executada; a equivalência estática é a inclusão ordenada
dos mesmos arquivos congelados. Reabertura de adapter na mesma transação não é
recovery durável após COMMIT/crash. gitleaks não estava disponível e nenhum feed
externo de vulnerabilidades foi consumido; scanner offline não substitui V2-041.

Identidade/cardinalidade reais de itens e bindings, oráculo independente,
correspondências Data Export/GraphQL, janela representativa e aceite nominal
continuam pendentes nos seus subgates. A fixture não aceita integralmente
R01/R02, V2-046a/b, V2-047, V2-012a/b/c, V2-050 ou V2-038. Fonte GraphQL
OBSERVATION_ONLY continua sem permit. Não se atribui B64 nem novo checkbox.

## Fechamento e continuidade

As dez frentes estão entregues na matriz. Test-RelationalLaboratory passou com
11 contraprovas e evidências privadas; o predecessor Coletas temporal passou
com10 contraprovas próprias e12 históricas. Test-RuntimeLocal passou nos seus
seis checks. Test-SchemaFoundationManifest, Test-ProgressiveDataGate e
Test-ColetasTemporalIntegrationBaseline também passaram; não houve relaxamento
de gates ou reescrita de manifests antigos. Logs de candidato preservados;
o selo final registra a nova conferência dos arquivos entregues.

O inventário de abertura2494 foi preservado:13 arquivos preexistentes alterados
com snapshots byte a byte; checkpoints0073–0077 e arquivos novos discriminados
no manifesto. O diff completo inclui as fotografias; diff-review.patch permite
revisar código/documentação sem repetir snapshots e manifesto volumoso. Ambos
passaram pela leitura sintática do Git; UTF-8 estrito conferido nos deltas.
A revisão técnica conferiu opt-in/alvo, rollback, contratos tipados, limites,
SQL por conjuntos, resultados sanitizados, temporalidade e ausência de lookup
por alias. Não se alega revisão humana.

Artefatos: docs/catalogos/macrobloco-relacional/manifesto.json e
verification-summary.json; na rodada, inventory-before.json, inventory-after.json,
diff.patch, diff-review.patch, delivery-summary.json e final-delivery-verification.json.
STATES, trilha e RETOMADA apontam para checkpoint0077; as fotografias anteriores
continuam imutáveis. Não resta frente local deste pedido para outro chat.