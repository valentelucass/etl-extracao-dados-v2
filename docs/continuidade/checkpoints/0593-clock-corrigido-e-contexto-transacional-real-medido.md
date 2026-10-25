# Checkpoint0593 — Clock corrigido e comportamento transacional real medido

25/10/2026. Continuidade0592, último commit anterior
7f1f288ba20f26ef209259eb3080d2497ef391d2. ESL exclusivamente consultas,
leitura, filtros e download: zero alteração, aplicação, upload ou mensagem.
Fretes Data Export6389 continua distinto de Fretes GraphQL. Raster não foi
identificado como conjunto equivalente na ESL. Comparação final exige o mesmo
relatório, recorte, campo de data, fuso, filtros e granularidade.

## Clock: causa reproduzida, correção qualificada em agregado próprio

A factory recebia Clock, mas criava DataExportRetrySchedule com Clock.systemUTC().
Assim, o horário de recebimento ignorava o relógio fornecido. Root confirmou os
bytes q06 e a chamada no bytecode. O sucessor altera somente o encaminhamento
desse relógio; não arredonda nanossegundos nem relaxa a precisão SQL.

Diagnósticos próprios: metadata4 PASS; contracts9 PASS; original12 reproduziu
0PASS/12FALHAS e continua falha, embora o wrapper diagnóstico termine0;
corrected12 PASS com as mesmas expectativas. O agregado r05 executou novamente
os32métodos/111invocações:111PASS,0falha/skip/abort/unexpected/containerfail.
Não somar99históricos+12novos. RootP3ClockAggregate111Qualification0592.json.

Manifesto r05:
3730904AAE6EA5A2B0BAAC0CB7EEC759C5CB78C43C46E00B65657BC439175BC3.
Root montou JAR de laboratório AB607BCD979AA0D7C0EC97A36FD25832AC51FDD0B7C76DF3D73B0E3B689F9236,
com1546entradas e somente3classfiles da famíliafactory alterados; demais bytes,
recursos e8libs preservados. Nesse JAR: agregado111 PASS e contraprovasclock4 PASS,
origem estrita e0classfiles sobrepostos. São duas execuções próprias, não222casos
distintos. Produto canônico/E05 não foi alterado, adotado ou implantado.
Regressão Maven447+7 e produto51+MainV3+factory continuam pendentes nesta edição.

O wrapper Root do primeiro agregado terminou1 por variável LASTEXITCODE ausente
após o Job original já ter fechado0; recibos,111resultados e pins foram conferidos
sem repetir os testes. O revisor genérico posterior inicialmente recusou um
ExitCode Int64 válido por exigir Int32. Correção limitada ao tipo inteiro aceito;
revisão do mesmo recibo original, sem repetir a construção do JAR.

## Banco: medição real de contexto C78 concluída, sem gravação

Probe físicoV1 compilou, mas o validador de classes recusou classes internas
legítimas com dois sufixos. Falha preservada;0casos executados nesseV1.
RootV2 corrige somente a whitelist ancorada nos quatro tipos locais e permite
sufixos internos recursivos.8contraprovas de escopo e54Java+14modelosnativos PASS.

Root revisou/aplicou a inscrição única102500c3 em duas registries do sucessorV3,
com caminho literal e recusa de cópia/repetição. Manifesto:
E52DC558CF6E4FC7B9783374B29D9F43F5E562ADACE0D578E2BEA7F99052B21D.
Qualificação própria offline102500c4:54Java+35modelosnativos=89PASS,
recibo original0/1GiB/4min. V1/V2 e suas falhas permanecem preservados.

Uma única unidade real102500c3 mediu localhost/ETL_SISTEMA_V2_SHADOW,
somente ctl.execution_partition:1conexão C78/1provider/12SELECT concluídos,
query10s/work60s/cleanup30s/total90s; executor original1GiB/4min,
12.979ms/pico286842880B/Exit0.0Source/DML/DDL/commit/retry.
SQL16.0.1000.6, PID/início nativos preservados; mesmo socket comprovado antes
do LOGIN, filho fechado0 e socket original ausente ao final.

Resultado observado: em autocommittrue, depth0 e XACT_STATE1. Depois da mudança
de modo, a transação protegida teve depth1 e o mesmo dono em dois cortes.
O rollback original retornou e seu dono/locks ficaram ausentes. A consulta de
conferência em sys.dm_tran_session_transactions abriu uma NOVA transação de
leitura persistente: depth1/local1/open1, propriedade comprovada. O segundo
rollback de limpeza retornou e a conexão fechou. Diferença de IDs isolada não
foi usada como prova. postReadKind=NEW_PERSISTENT_READ_TRANSACTION.

Revisão somente arquivos102500c5: issues=[], fechamento original comprovado.
RootContextC3PhysicalQualification0592.json adota somente essa medição.
Isso não comprova escrita/readback dos dados das entidades, proteção de funções
ou de tabelas de outros footprints. Banco revisa os guards contra os fatos
medidos, sem relaxar proprietário, rollback único ou recusas UNKNOWN.

## Manifestos INFO e V10: resultado parcial preservado

V10 D77C3B3E80F674EE7512A5F21B884789F2F9CA2D768B0A803C4E31D54C6ACEE2:
81modelos+50carregador PASS; falhou no javac dos novos testes antes do
diagnóstico20/sanitizador7 e dos2preflights. Recibo original Exit1 fechado,
31.867ms/pico715354112B;0Source/SQL/provider. Não158PASS.
RootBankV10PartialCompileStop0592.json. Saída do compilador não foi retida pelo
runner antigo; Contratos identificou estaticamente chamada a método inexistente
DataExportProperties.fromSettings e prepara sucessor com API existente e
diagnóstico limitado. Não atribuir essa falha local à ESL.

O stop real ManifestosV9 segue ContractDriftException após1reserva INFO6399,
0DATA/SQL, sem reason/statusHTTP retidos. Projeção histórica perdeu table.field;
Fretes tem16/16filtros qualificados, Manifestos0/18 e Cotações0/6. Ainda não
prova a causa do wire atual. Não prefixar nomes por suposição ou alterar
parser/baseline/gate. Coletor INFO-only estrutural executável em preparo,
default-denied, nova unidade finita de1INFO, sem DATA/SQL, qualificada antes
de qualquer consulta. Nenhuma chamada nova foi feita nessa investigação.

## Continuidade

Users54540/54534mapeados/6quar/hasNext=true permanece interrompido sem EOF,
cursor ou closure original. Não repetir, renovar ou retomar a página seguinte
automaticamente; contadores históricos continuam parciais. A prova C78 nova
não fabrica encerramento Users. Três chats trabalham somente em arquivos;
Root é único executor e editor canônico. Etapas3/4 continuam abertas.

Próximo: concluir diagnóstico INFO, incorporar guards com base na medição real,
provar fonte→staging→leitura independente integral→rollback/ausência por entidade,
integrar pacote e conferir os dados com ESL no recorte correto. Preservar
SQL16.0.1000.6/instalação, contas/credenciais/identidades/permissões/ACL,
serviços/listeners/certificados/trustglobal, V1/WMS/produção. Sem DDL,
commit de negócio retido, deploy ou corte produtivo nesta rodada.
