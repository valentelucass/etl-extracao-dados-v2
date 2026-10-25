# Checkpoint0594 — INFO real de Manifestos comprova catálogo de filtros incompleto

25/10/2026. Continuidade0593, commit anterior
881b401c88fa66d809a690aeac128d9b95b6eda7. ESL somente consultas/leitura/filtros/
download, sem aplicar, salvar, importar, enviar dados ou mensagens.
Esta unidade consultou somente INFO6399: nenhum DATA ou SQL.

## Evidência atual: HTTP200,91campos,18filtros qualificados,36breaking

Root executou uma consulta INFO-only nova, root-manifestos-info-0594-02,
com fábrica, gateway, parser e gate finais do E05. A resposta recebida foi200.
Projeção técnica anterior ao gate reteve91campos e18filtros, sem corpo bruto,
labels/options/valores de negócio/token. Todos os filtros têm table e field;
name está ausente. Exemplos reais: manifests.service_date,
scopes.by_updated_at, mft_delivery_manifest_items.mobile_validated e
mft_dns_mft_aggregate_manifest.aggregate_id. Não prefixar todos com manifests.

O gate recusou com reason=BREAKING_CHANGE,breakingChanges=36,
compatibleChanges=0. Frames tipados:ContractValidator.enforce/validateMetadata,
ContractRunGuard.validateMetadata e DataExportContractGate. Não é429 ou timeout
nesta consulta. O diagnóstico concluído não aceita o contrato nem libera DATA.

Root comparou o catálogo P1B com a estrutura atual: conjuntos dos91nomes de
campos iguais; os18nomes antigos dos filtros são exatamente os field atuais
sem table;0filtros antigos qualificados,18atuais qualificados,interseção0.
Isso explica as18remoções+18adições detectadas. A representação do catálogo
local não corresponde ao nome técnico que o parser atual produz do INFO.
Não atribuir uma alteração histórica da ESL a partir do wire antigo não retido.
91objetos de campo atuais não declaram type/data_type/dataType.

Provas privadas:
RootManifestosInfoSchema0594-02.json e
RootManifestosInfoFilterNameComparison0594.json, no diretório quatro-etapas.
A primeira fixa hashes/caminhos do resultado técnico, controller, native e
recibo original. A comparação não reencena o wire completo e não substitui o
replay de estrutura explícito com parser/gate reais. Contratos prepara esse
replay e a correção versionada de baseline/fingerprint/bindings, mantendo as
validações. Cotações precisa de INFO próprio; não presumir suas tabelas.

## Qualificação e fechamento da consulta

V11 BB0C748DB2231B587F9AA7AA90E10B09183F86FDBB64FC1863E5DA458DDF22A8
executou206modelos+stdinVALID/EMPTY=208PASS parciais. Parou após EMPTY porque a
atribuição if/else enumerou byte[0] para null e o finally acessou Length.
Não209PASS;0preflights e0Source/provider/SQL. Falha/recibo preservados.

RootV12 corrigiu somente essa atribuição de fixture offline, sem mudar Java,
controles live, parser, gate, baseline ou perfis. Manifesto
795919049A5933DE0B86E8C405458522D1D36ABFACB27E2EC1AC65137F889EAC.
Own81+50+20+7+48+3=209PASS e2preflights reais PASS, recompilados sob executor
original1GiB/4min, recibo0 fechado. RootBankV12Own209Qualification0592.json.
Revisão estática independente confirmou97arquivos e nenhuma outra mudança.

Root emitiu código novo separado. A primeira coorte0594-01 foi abandonada
ANTES de Native/Java/provider: o reviewRoot sofreu conversão automática do
horário JSON e depois recusou a margem da janela original. Corrigiu-se apenas
o leitor Root com DateKind String, sem mudar/renovar a janela. Intent, reserva
Java e recibos Source ausentes; ROOT_SOURCE_STOP bloqueia essa coorte inutilizada.
RootInfoUnused0594-01.json registra0Source/provider/SQL.

A coorte0594-02 foi emitida/compilada em Job original próprio, sem Source.
Após revisar código/bootstrap/pins e duas entradas do JAR emissor, Root liberou
a consulta única. Limites: INFO6399=1,DATA0,SQL0,attempt1,redirectNEVER,
intervalo>=3s,Java30s/2MiB,Joboriginal1GiB/4min. Provider existente só após READY
e reserva Java real; token apenas por stdin, sem argv/env/log/artefato.

Consulta:17.429ms,pico301117440B,4processos,originalExit0/TimedOut=false.
JavaExit0/childClosedKnown,outerControllerExit0/closedKnown,stdin fechado e
futureSettled conhecidos. INFO1/transportSends1/0DATA/0SQL/0credencialSQL.
Marcador CLOSED é técnico; não inferir fechamento de socket ou contagem wire
independente. Root revisou todos os recibos e encerrou a unidade sem retry.
O PAR SOURCE MAIS RECENTE agora é
info-only-root-cohorts/manifestos-info-0594-02/controller-receipt.json +
original-memory.json. Futuro emissor não deve voltar ao parV9 anterior.

Status200 atual não reconstrói o status numérico ausente do V9. Contadores
históricos continuam parciais: reservar separadamente esteINFO1 e oINFO1 do0592,
sem alegar total final. Nenhum filtro ou contrato canônico foi alterado ainda.

## Pacote e usuários: resultados reais e falhas preservados

LABClock passou43classes/447testes +2classes/9testes de política,0falhas/skip.
Compilação ampla passou. StyleCLI direto usou sun_checks.xml em vez da execução
check-style/config/checkstyle.xml do projeto;24124violações da chamada incorreta.
Spotless passou. A falha foi preservada, sem alterar regras ou fontes.
R07 C2BB5AA2322F20317796EEF6E8E76BF5E3A660E9FEDB745EEABC1E05B58F24F3
prepara style-project e vínculo estrito aos quatro resultados/recibos originais;
não repetir447/9/compilação por erro de orquestração. Pacote/7IT ainda pendentes.

PRODUCTr05 foi materializado/formatado em snapshot externo:15paths permitidos/
5deltas CI exatos, classificações mantidas,Root/CIM ausentes. A compilação parou
no validate correto:3NeedBraces no Bridge, antes do javac. R07 acrescenta somente
os3blocos e exige stage novo e sua qualificação. Sem adoção/código canônico/deploy.

Usersr05 falhou antes de JUnit por importstatic assertNull ausente. Root corrigiu
somente o import em overlay separado:77executados,54PASS/23FALHAS. Diagnóstico
limitado próprio,3selfchecks PASS, reproduziu54/23 sem mensagens/filenames/payload.
20falhas são ContractResponsePathBoundary.requireAllowed para namearray;
3são GraphQlResponseParser.parse por JSON incompleto no helper da fixture.
R08 corrige uma chave no helper sem tocar77corpos/expectativas e especifica perfil
Users fechado de namefolha polimórfica, limites originais,shape real e lexema
preservado, sem caminhos privados dinâmicos. Esse perfil precisa de implementação,
fingerprint/binding novos e testes; first-wave estrito permanece default.
Users continua não qualificado, sem extração/replay novo.

## Próximas ações e restrições

Corrigir o catálogo Manifestos com os fatos atuais; qualificar Source→staging→
leitura independente integral→rollback/ausência e fechamento. Banco prepara
correção de rollback repetido/incerto, propriedade da leitura pósrollback e
literal GRANT na consulta de locks, com plano finito separado se acrescentar
SELECT. A medição real C78 do0593 cobre somente ctl.execution_partition.
RasterR5 ainda não tem prova contínua de proteção dos módulos, sem admissão.

Concluir pacote/integração e comparar ESL no relatório/período/fuso/filtros/
granularidade corretos. Fretes Data Export6389 não é Fretes GraphQL;
Localizador8656 não comprova Raster. Users54540/6quar/hasNext=true permanece sem
EOF/cursor/closure original; não fabricar encerramento nem retomar automaticamente.
Etapas3/4 abertas. Root único executor/editor canônico, três chats somente arquivos.
Preservar SQL16.0.1000.6/instalação, contas/credenciais/identidades/permissões/ACL,
serviços/listeners/certificados/trustglobal,V1/WMS/produção. Sem DDL, commit de
negócio retido, deploy ou cutover. Índice antigo preservado e ainda pendente.
