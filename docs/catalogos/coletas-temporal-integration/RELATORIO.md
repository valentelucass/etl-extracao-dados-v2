# Coletas — integração temporal completa em laboratório shadow

11/09/2026. COLETAS_TEMPORAL_INTEGRATION_LOCAL_COMPLETE.
Novo escopo explicitamente adotado após B63; nenhum B64 oficial ou aceite do
roadmap. B63 e suas evidências continuam como fotografias históricas imutáveis.

## Resultado implementado

LocalColetasTemporalRuntime compõe os pipelines existentes de Data Export e
GraphQL sintéticos, auditoria JDBC, staging tipado e exato, referência temporal,
binding explícito, qualificação SQL, consumo tipado e reconciliação. A execução
usa uma conexão física compartilhada; os commits internos dos adaptadores são
suprimidos e os dados são revertidos. O cancelamento após staging físico invalida
a captura e faz rollback imediato, conservando a causa original.

V025 restaura apenas os metadados de auditoria necessários ao adaptador existente.
V026 adota a proposta observacional sem alterar seu arquivo histórico. V027
introduz epoch second/nano, presença/bruto/parse/fallback, decisão SQLv2 e destino
sintético. V028 corrige a collation de presença demonstrada no primeiro consumo
físico. As quatro migrations foram instaladas separadamente da IT, após preflight
do alvo no master. Nenhuma migration aplicada foi reescrita.

A comparação usa o instante exato: offsets equivalentes deduplicam, 1ns diferente
conflita. O SQL agrupa a projeção tipada da raiz e conserva as linhas detalhadas;
nenhum payload arbitrário, hash ou ordem de chegada escolhe o vencedor. O destino
core.coleta_temporal_laboratory possui status, alias, terminalidade, instante e
proveniência. A reconciliação registra inserção, atualização ou no-op por raiz.
COL-TIME-11–14 e compatibilidade estão na ADR0047.

O consumidor exige opt-in, transação externa, localhost/ETL_SISTEMA_V2_SHADOW,
fonte e tenant sintéticos fixos, marcador de fixture e binding sintético explícito.
Não escreve em core.coleta ou publicação, nem avança watermark. Main,
RuntimeCompositionRoot, permits e GraphQL OBSERVATION_ONLY permanecem intactos.
O gate GraphQL continua recusando promoção, inclusive após o fluxo bem-sucedido.

## Provas executadas

Diretório privado próprio: target/coletas-temporal-integration-20260910/.
Java17, Maven3.9.14, offline, heap512MiB, build isolado sem clean.
Os 783 arquivos Java entregues são idênticos ao build verificado por SHA256.

| Camada | Resultado observado | Evidência relativa ao diretório próprio |
| --- | --- | --- |
| Dirigidos Java, contratos e JDBC simulado | 60 testes, zero falhas/erros/skips | logs/directed-03.log |
| Writer exato, JDBC simulado | 7 testes novos, zero falhas/erros/skips | logs/physical-03.log; incluídos no verify |
| Suíte Surefire | 1563 executados: 1559 aprovados, 4 pulados, zero falhas/erros | physical-06-surefire/, logs/physical-06.log |
| Java/JDBC/SQL real opt-in | 39 aprovados: 38 Coletas novos e1 auditoria existente; zero skips | physical-06-failsafe/ |
| Concorrência de duas sessões | stage53008, qualify1222, consume53111; após rollback da primeira, segunda executou fluxo completo | teste twoSessionsExerciseActualStagingQualificationAndConsumerThenFullFlowAfterRollback |
| Formatter, Checkstyle, Enforcer, arquitetura e cobertura configurada | PASS no verify | logs/physical-06.log |
| SQL físico somente leitura | fundação001, Coletas038, integração057 PASS; 20 objetos conferidos | logs/schema-readonly-*.log |
| Migrations/baseline | V001–V028 em ordem, hashes anteriores/aplicados preservados | logs/baseline-02.log |
| Gate progressivo e contratos estáticos existentes | PASS; sem executar runner de reset | logs/progressive-static-01.log, contratos/vertical nos demais logs |
| Inputs representativos sintéticos | 16 casos, 15 contraprovas PASS | logs/representative-04.log |
| Inputs reais não fornecidos | EXTERNAL_INPUT_MISSING, exit2 esperado | logs/representative-missing-01.log |
| Scanner offline e suas contraprovas |2494 arquivos,zero achados;11 contraprovas PASS | logs/secrets-02.log, secrets-selftest-01.log |
| Continuidade e integridade |10 contraprovas novas e12 B63; continuidade/trilha/contrato B60 PASS | logs/integration-integrity-*.log, documentary-*.log; manifesto.json |

Os quatro skips históricos são três testes de symlink indisponível no ambiente
Windows e o comando externo de Cotações sem propriedade configurada. Nomes e
motivos ficam em skipped-tests.json; nenhum foi contado como aprovado.
Gitleaks não está instalado nesta máquina; o scanner offline existente e seu
self-test foram executados. Não houve instalação de ferramentas ou permissões.

Cada caso físico usa finally/AutoCloseable e confirma contagens antes/depois.
A leitura posterior independente confirmou232 execution_attempt preexistentes
e zero linhas próprias em auditoria, exatos, referências, laboratório e
reconciliação (logs/physical-06-after.log). Schema permanece instalado; dados de
teste não. A instalação nova em banco vazio não foi executada: baseline foi
validado estaticamente e o upgrade fisicamente, sem alegar equivalência física.

## Matriz de critérios

| Critério | Estado | Prova e limite |
| --- | --- | --- |
| A — contrato, bruto/presença/parse/origem e nanos | COMPROVADO LOCAL | ADR0047, V025–V028, writer e IT |
| A — dedupe, precisão e empate51428 | COMPROVADO LOCAL | duplicatas equivalentes/conflitantes, 1ns, offset, conflito tipado |
| A — schema/baseline | COMPROVADO NA CAMADA | aplicação aditiva + verificações estáticas; instalação vazia NÃO EXECUTADA |
| B — captura até consumo tipado e reconciliação | COMPROVADO LOCAL | Java/JDBC/SQL, fontes sintéticas pelos pipelines existentes |
| B — opt-in e gate real fechado | COMPROVADO LOCAL | perfil/flag/URL, disabled e gate GraphQL recusado |
| C — referência válida/ausente/nula/inválida e nativo/fallback | COMPROVADO LOCAL | cenários parametrizados de sucesso e bloqueio |
| C — identidade, tenant, janela, contrato e binding | COMPROVADO LOCAL | rejeições por escopo/versão/chave e ambiguidade |
| C — expansão, terminais, antirregressão e ordem inversa | COMPROVADO LOCAL | raiz única, done/finished/cancel, 1ns reverso e51428 |
| C — retry/replay e falhas | COMPROVADO LOCAL | retry idêntico/divergente, após selo, captura interrompida e cancelamento após escrita |
| C — concorrência e rollback | COMPROVADO LOCAL | procedimentos efetivos sob duas conexões; fluxo completo após liberar trava |
| D — mecanismo de inputs/oráculo | COMPROVADO SINTÉTICO |16 casos e15 contraprovas, INPUTS.md |
| D — equivalência temporal/representatividade real | EXTERNAL_HOLD | oráculo independente e correspondência qualificada ligados às capturas ainda faltam |
| D — nova API condicional | NÃO EXECUTADA | zero chamadas; janela/casos e Segurança não satisfeitos; ausência nativa já provada anteriormente |
| E — build, checks, diff e continuidade | COMPROVADO LOCAL | recibos, inventário, manifest, relatório e checkpoint0072 |
| Ativação, produção, cutover e aceites do roadmap | NÃO AUTORIZADOS POR ESTE RESULTADO | V2-041 e aceite nominal continuam requisitos externos |

## Tentativas preservadas e correções

directed-01 falhou em quatro linhas Checkstyle; directed-02 revelou import de
helper de fixture inexistente; directed-03 passou. schema-qualification-01
reverteu DDL e mostrou largura do índice clustered; antes da instalação a PK
foi definida nonclustered e schema-qualification-02 passou sem aviso.

physical-01 revelou conversão de data na fixture (29 erros Coletas, auditoria
existente passou). physical-02 parou no Checkstyle, sem SQL. physical-03 revelou
collation no EXCEPT (15 erros), corrigida pela migration aditiva V028.
physical-04 consumiu dados corretamente, mas dez casos consultavam contexto já
invalidado pelo teste do gate e uma concorrência não iniciava transação externa.
physical-05 parou em duas allowlists antigas de migrations na suíte1563.
Esses controles foram atualizados explicitamente, mantendo a proteção das
migrations anteriores e restringindo a restauração da auditoria à V025.
physical-06 passou integralmente. Logs e recibos de todas as tentativas permanecem.

## Revisão e recuperação

Inventário inicial2452, snapshots anteriores e estado Git sujo preservados.
manifesto.json identifica somente deltas próprios, arquivos novos e hashes
preservados. Os validadores históricos resolvem alterações legítimas por snapshots
conferidos; seus manifests, checkpoints e recibos não foram regenerados.
Nenhum checkbox do roadmap mudou:67/115,191 rotas,zero AGORA.

Diff revisável: target/coletas-temporal-integration-20260910/diff-implementacao.patch.
O diff-completo.patch no mesmo diretório inclui também snapshots e manifest
gerado; diff-files.json enumera os arquivos e hashes de ambos os recortes.
A revisão local conferiu opt-in, SQL de conjunto, precisão, terminalidade,
cancelamento/rollback, recursos e ausência de alterações nos permits/contratos
reais. Não constitui revisão humana. Para reverter código, aplicar inversamente
somente esse delta após conferir drift; não usar reset/clean. Recuperação
estrutural exige nova migration; não apagar objetos/histórico ou reescrever V025–V028.

Escopo local encerrado. Próximo passo externo: montar o pacote descrito em
INPUTS.md com oráculo independente, correspondências e janela/casos reais,
atestação vigente de V2-041 e aceite nominal. O mecanismo valida os vínculos,
mas não autentica a independência material nem transforma hashes em autorização.
