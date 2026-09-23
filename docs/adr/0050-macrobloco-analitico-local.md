# ADR 0050 — Contratos sintéticos da camada analítica

Status: ACEITO_NO_ESCOPO_LOCAL, pela adoção do pedido A–N em 12/09/2026.
Não ratifica identidade do fornecedor, negócio nominal ou consumidor externo.

ANA-01: uma sessão rollback-only compõe os pipelines existentes. Cada laboratório
mantém seu source/tenant/contrato; vínculos entre runs são declarados, nunca
inferidos por igualdade de nomes, documentos, placas ou ordinais.

ANA-02: MAT-01 tem chave (run, Frete canônico, indicador PE/CB). A data é atributo
mutável e partição; correção recompõe origem e destino sem duplicar o fato.
MAT-02 tem (run, dia de negócio, filial vinculada, Geral); filial múltipla exige
atribuição explícita. MAT-05 tem (run, Manifesto canônico), com competência
reduzida sobre versões coerentes e receita sobre Fretes distintos antes da soma.

ANA-03: Raster captura observações tipadas separadas do binding lateral. Revisão
sintética explícita define frescor; extração é linhagem, sem provar atualização.
Parada possui chave sintética própria sob a viagem; Ordem é atributo opcional.
Campo ausente preserva corrente, NULL limpa atributo por política; ausência de
filho não o remove. Datas sem offset usam ZoneId injetado com DST não ambíguo;
sentinela 1900-01-01 preserva texto e recebe disposição própria. O intervalo
local mínimo é um dia civil, cuja completude exige receipt sintético explícito.

ANA-04: referências/releases e registros dimensionais têm scope, vigência e
identidade declarados. Normalização não funde identidades. Frota requer binding
por papel e vigência; lacuna ou sobreposição bloqueia apenas dependentes.

ANA-05: os 19 contratos são objetos internos neutros no schema pub, sem grants.
Colunas técnicas adicionais explicitam run, identidade, disposição e linhagem.
As colunas legadas são inventariadas antes da construção. Ausência sem fonte
permanece auditável; não se usa NULL constante para simular implementação.

ANA-06: política fiscal sintética mantém CT-e antes de NFS-e e a regra de
documento real de MAT-04, compartilhada com SQL-01. Placeholders não comprovam
faturamento. Política nominal permanece pendente.

ANA-07: ausência de Coletas é exclusivamente opt-in de laboratório, com kernel
existente e evidências independentes. Primeira candidata é Excluída na principal;
auditoria exige segunda confirmação. Reaparecimento limpa a ausência. Full de
materialização não possui capacidade de aplicar ausência.

ANA-08: processamento SQL em conjuntos, batches limitados, consulta JDBC com
filtro de run/janela, ordem e paginação. Concorrência disputa a procedure efetiva.
O cenário recompõe seus dados em cada processo e reverte integralmente.

Alternativas recusadas: grão dependente da data; filial MIN/TOP1 sem decisão;
identidade por nome/placa/posição; somas sobre produto de filhos; wrappers de
outro banco; fixtures de fatos finais; sucesso de captura sem terminalidade.

Evidências e testes serão vinculados na matriz A–N durante a implementação.

ANA-09: o inventário da tabela Fretes contém108atributos. Dezoito possuem captura,
termos tipados ou origem técnica existentes;90 dependem de campos do GraphQL
legado fora do6389 conhecido. Esses90 são um snapshot sintético de atributos,
separado do payload Data Export, com presença/wire/raw/tipo/erro e binding à
observação6389. Seu modelo não contém indicadores, performance calculada,
agregados ou fatos finais. Versão, conflito, precisão e replay são decididos
antes da materialização. Uma captura-base diferente exige novo vínculo; replay
equivalente precisa preservar a equivalência física da base. O canal local não
ratifica esses campos no contrato do fornecedor nem abre um transporte GraphQL.

ANA-10: Usuários reutiliza LocalUsuariosRuntime, parser/gate Relay, staging,
promotion/current/history e DQ existentes. O laboratório registra política
sintética de tolerância zero com fingerprint calculado a partir do material
real; o mecanismo DQ emite o permit. BACKFILL identifica uma observação do
snapshot; REPLAY referencia a execução anterior. O relógio técnico permanece
sincronizado com SQL Server, separado da janela de negócio sintética. A sessão
compartilhada agora configura e reporta loginTimeout5s, preservando rollback-only.

ANA-11: MAT-01 conserva cada decisão tipada em observação imutável e um ponteiro
corrente por Frete canônico/indicador. Datas nulas e gates recusados permanecem
auditáveis; a consulta de fatos válidos filtra indicator_valid. Repetir receipt
exige igualdade exata do conjunto de entradas e do pedido; outro receipt sobre
as mesmas entradas é NOOP. O ranking legado por minuta (pesos4/3/2/1, extração,
id) fica caracterizado, sem determinar a identidade local. A data civil usa o
fuso persistido do run, com truncamento dos nanos somente na projeção SQL.
Performance usa vínculo por papel, depois destino e emissora explicitamente
vinculados. Um vínculo presente e não resolvido bloqueia o dependente.

ANA-12: a release sintética original da expansão permanece imutável. O perfil
analítico declara apenas os dois campos adicionais de finalização já suportados
pelo mapper de Fretes: fit_dpn_performance_finished_at e finished_at. O run
analítico registra o fingerprint calculado dessa release fixa; a associação ao
run de expansão é explícita. A validação SQL admite a extensão somente nesse
vínculo, conservando todos os gates de execução, escopo, partição, página terminal,
contagens e replay. Nenhuma coleção genérica de paths ou contrato arbitrário é
aceita. A ausência desse vínculo foi observada como EXP_CAPTURE_SCOPE_OR_COMPLETENESS.

ANA-13: a previsão de Localização vem do path documentado localmente
/fit_dpn_delivery_prediction_at; predicted_delivery_at é o nome da coluna legada,
não o path do payload8656. V059 corrige V058 por sucessão aditiva e recusa o
arredondamento de valores fonte com escala acima de8. SQL-02 conserva a observação
de Localização usada pelo fato e consulta a atribuição PE para a responsabilidade
de performance. SQL-07 usa labels de status e alias de região por release explícita.
O atributo legado status_branch_nickname é UNSOURCED_LEGACY no mapper existente;
seu NULL tipado não autoriza inferir a filial a partir do nome da localização.
V063 acrescenta CURRENT_BRANCH e UNLOADING_BRANCH aos papéis de binding. SQL07
consome CURRENT_BRANCH quando o atributo da fonte está ausente; mantém a recusa
se o vínculo existe e sua política/registro não resolve.

ANA-14: o perfil fechado analytic-manifest-capture-v1 declara 92 campos brutos
de 6399, derivados do DTO legado e dos dois arrays lidos pelo mapper. A família
synthetic-relational-v1 permanece; cada run registra o fingerprint concreto antes
de capturar. A release original não muda. A preparação usa stg.manifesto_observation
efetivamente escrita pelo pipeline, com colunas SQL tipadas e auditoria de presença,
wire e rejeição. Nenhum campo analítico final entra na fixture da fonte.
Somente o cohort de frescor corrente participa dos atributos da raiz; as linhas
do mesmo instante em capturas distintas são comparadas. COHERENT admite ABSENT
com valor único, mas NULL+VALUE e valores diferentes são conflitos. As sete métricas
MAN07 admitem NULL com valor numérico único, sem somar a expansão. Status mantém
closed > in_transit > pending. Campos de filhos com valores múltiplos não escolhem
representante; conservam as observações/arestas e deixam a projeção singular nula.
Essa nulidade possui caso positivo singular e motivo de cardinalidade verificável.
Os timestamps preservam texto/offset/nanos na auditoria; DATETIMEOFFSET(7) trunca
apenas os dígitos excedentes na projeção. Preparação, captura e snapshots ficam
na mesma sessão rollback-only. Provas ainda em execução; não constitui aceite real.

ANA-15: o estado ativo/inativo de Manifestos é um envelope lateral imutável,
vinculado à raiz capturada e à revisão explícita. Ausência do envelope recusa os
fatos dependentes; reativação exige declaração própria. Isso não converte ausência
em uma extração incremental em exclusão do fornecedor. V065 implementa o contrato
com TVP de64 itens, unicidade, origem capturada e recusa de revisão conflitante.

ANA-16: MAT02 conserva contribuições ISSUED/UNLOADED por Manifesto e SCANNED por
Inventário, antes da agregação por dia civil/filial/Geral. A contribuição SCANNED
leva o indicador de incompletude. O conjunto antigo e o novo determinam as
partições recompostas; partição esvaziada ganha observação inativa com zeros.
V067 nunca escolhe uma filial por ordem de texto. UNLOADING_BRANCH é obrigatório
quando a fonte declara descarregamento; o fallback de INV admite exatamente um
Frete canônico por arestas resolvidas e binding de filial. Atributo de filial
presente em INV exige seu próprio binding. Tipos de Inventário usam INV_TYPE
da release governada, com o prefixo legado CheckIn::Order:: caracterizado.
Percentual DECIMAL(28,8) representa pontos percentuais, com zero no denominador
zero. Histórico de contribuições e recibos preserva causas e origem das decisões.

ANA-17: a preparação analítica não herda a perda de precisão da coluna histórica
freshness_at_utc(3). V068 extrai segundo UTC+nano do campo temporal selecionado
pela precedência já declarada; stg.usp_capture_relational_laboratory, cohort raiz,
MDF-e e preparação/aplicação usam o mesmo par. O timestamp histórico permanece
como representação limitada, com raw/offset e clock exato disponíveis. A mudança
é restrita ao caminho de Manifestos da composição relacional; contratos/gates
de captura não são relaxados. mdfe_status permanece escalar da raiz, conforme
MAN04, corrigindo sua classificação analítica inicial como CHILD.
V069 liga cada snapshot imutável a todas as observações físicas do cohort
vencedor. A execução de preparação pode ser uma captura stale; esse vínculo
explícito identifica as observações que de fato forneceram seus valores.
Physical-manifest-clock-01 passou9IT, incluindo diferença de1ns entre páginas,
offset, stale, conflito de mdfe_status e regressão Coletores/preparação.

ANA-18: V070 representa separadamente CROSSWALK (Frete relacional → Frete da
expansão) e DIRECT (Manifesto → Frete da expansão). Endpoints incluem suas
capturas/escopos; igualdade numérica nunca cria a relação. O crosswalk local é
um-para-um, com revisão/rekey explícitos; DIRECT permite múltiplos Fretes por
Manifesto e mantém cada aresta canônica. Um selo sintético declara a completude
do conjunto direto, confere sua cardinalidade e impede acrescentar aresta na
mesma revisão selada. Mudanças posteriores precisam de nova revisão/selo e
serão conferidas pelo consumidor. O selo é entrada de construção sintética,
não prova de cardinalidade/completude de dados reais. Fretes pela Coleta continuam
sob MC/CF existentes; MAT05 consumirá os dois caminhos e deduplicará o alvo.
Physical-manifest-freight-paths-01 passou3IT: chaves distintas, replay/rekey,
recusas de cardinalidade/rekey sem prova e alteração de conjunto selado.

ANA-19: OWNED_FLEET reutiliza as quatro tabelas e o selo de V008, com seleção
local por run/revisão/vigência em V071. O contrato sintético fixa tokens SHA256
sobre UTF16LE e normalização ASCII trim/upper; isso é compatibilidade governada,
nunca identidade de veículo/motorista. Importador limitado32768bytes/100linhas
por coleção, recibo com bytes reais, matriz8 e2exceções explícitas. Ausência de
referência/ownership/contrato tem disposição própria, sem default. Documento de
proprietário no cenário vem do registry do trator vinculado, provenance
TRACTOR_BINDING_REGISTRY_DOCUMENT; não se inventa path6399. Prova física3IT em
physical-owned-fleet-01; o uso em MAT05 ainda exige suas provas integradas.

ANA-20: MAT05 usa observações imutáveis com FK para snapshot MAN tipado92 e
ponteiro canônico por run/Manifesto. Arestas DIRECT e MC/CF/CROSSWALK ficam em
linhagem separada; receita soma cada Frete uma vez. Totais por caminho preservam
interseção explícita; Receita Total=Direto+Coleta−Compartilhado. A política
sintética exige BRL/MAJOR e valor tipado; ausente/conflito bloqueiam, zero e
negativo não são ausência. Soma direta reconcilia exatamente o agregado6399 e
o conjunto declarado completo. Capacidade KG usa trator/reboques vinculados,
zero permitido e nulo/unidade/conflito recusados. Receipts conferem conjuntos;
NOOP de negócio pode avançar proveniência para nova observação física. Regras
e diferenças do legado em MAT05-REGRAS.md. As cinco IT de fato/consumo passaram
em physical-mat05-time-alias-02 e foram repetidas sobre V078.

ANA-21: instantes equivalentes por offset são comparados por segundo+nano em
sete campos MAN (V074); snapshot temporal normaliza UTC, atributo/field_audit
conservam raw/offset. A V075 corrige leitura do wrapper real {value: scalar} de
sequence_code_json em Coletas. Valor e tipo do alias são atributos preservados;
não viram identidade ou criam MC/CF. As mudanças dependem das provas físicas
atuais e regressão relacional integral antes do fechamento.

ANA-22: relações históricas não definem participação atual. V076 cruza MC com
picks da lineage da coorte MAN e CF com itens da captura corrente da Coleta;
retirada de pick atual retira somente a contribuição/apresentação correspondente.
V077 completa igualdade de instante na competência da captura; campanha
physical-mat05-time-alias02 passou13IT, incluindo essas contraprovas. V078
recusa o mesmo veículo canônico em dois papéis e permite desativação explícita
de reboques opcionais quando a fonte atual também não declara suas placas.
Troca de papel/zero/nulo/retirada passaram em physical-manifest-fleet-gates-01:
quatro IT de gates e cinco IT repetidas de Manifestos, sem falhas/erros/skips.

ANA-23: SQL11/12 consomem componentes correntes de Inventário/Sinistros, com
captura completa, branch binding e referência aplicável por run/revisão/data.
Identificadores conservam tipo e chave canônica; números exibidos não criam
relações. Inventário usa filial emissora do Frete somente por INV_FREIGHT
resolvido e unívoco; sem relação, usa a filial vinculada da própria ordem, com
proveniência. Sinistros exige veículo explicitamente vinculado. Views não
somam valores do produto de componentes. Arrays pequenos são apresentados em
ordem física; raw/presença/wire preservam precisão além do TIME(7) SQL. Todas
as 68 colunas tiveram valor físico positivo e metadata/ordem conferidos, além
de replay, ausência de veículo, filial por vínculo e exclusão lógica, nos três
IT de physical-inventory-incidents-03. A consulta filtra a revisão explicitamente.

ANA-24: SQL01/06 reutilizam pub.ufn_expansion_fat/cap e a decisão fiscal do run
da expansão, sem segunda política para placeholders em MAT04/PUB04. Carteira
e instrução usam campos tipados já capturados, preenchendo lacunas do SQL legado.
A série NFS-e não tem path comprovado: V080 oferece binding sintético explícito
por run/componente/captura/revisão, com NULL declarado ou valor limitado50.
Sem esse vínculo, uma linha com NFS-e fica NFSE_SERIES_UNRESOLVED; outro ramo
segue independente. Não se adiciona path fictício ao payload do fornecedor.
O vínculo é imutável, TVP64, replay exato e recusa divergente recuperável por
savepoint; nova captura exige nova declaração. CAP mantém conciliadoNULL como
NULL e consome classificação/tipo do release financeiro existente. Remetente
e destinatário FAT conservam o mapeamento efetivo dos DTOs legados, com sua
divergência nominal documentada. V080 passou três IT financeiros em
physical-financial-queries-01, junto de três IT repetidos SQL11/12:73colunas
positivas/metadata, placeholders MAT04/PUB04, pagamento/conciliadoNULL, série
ausente/NULL/Unicode, replay exato e divergente sem perder o estado anterior.

ANA-25: SQL10 lê capturas de expansão, relações, dependências e execuções
anexadas pelo runtime, com timestamps/contagens efetivos. A mensagem é um código
de motivo fechado derivado do estado; não lê texto livre, payload ou segredo.
Quarentena terminal continua DEGRADED, mesmo que a travessia tenha sido completa.
V082 corrige a leitura para os motivos QUARANTINE_* existentes na expansão;
V081 fica preservada. physical-monitoring-02 passou2IT de nove colunas reais,
quarentena/tempo, canário de payload, isolamento por run e ausência de fan-out.

ANA-26: Cotações usa os36 atributos candidatos do DTO6906 por contrato local
fechado, metadata/resposta reais e staging/promoção/DQ existentes. V083–086
acrescentam armazenamento tipado, tarifa QUOTE_TARIFF selada, snapshots efetivos
por run/chave e SQL05 com54colunas. A tarifa tem25pares direcionais da regra
legada, com BRL/PER_WEIGHT/KG/HALF_UP explícitos; combinação não coberta é recusa,
sem default0. A ratificação desta fixture não representa aprovação nominal.
ABSENT preserva atributo, NULL limpa e zero permanece zero; no-op/stale não
substituem snapshot por conteúdo antigo. Empate conflitante é recusado antes
da promoção; contexto corrente de outra captura não vinculada não é adotado.
O modelo original mantém relógio3ms/totalDEC19,4; o suplemento preserva9nanos e
outros decimais28,8 sem relaxar esses gates. Usuário usa trim/NFC existente,
sem equivalência canônica por nome ou remoção automática de acentos.

physical-quotes03 revelou que o selo de recuperação também exige referência
explícita e verifica um escopo próprio. V087 adiciona binding imutável por
execução/run/revisão/release e seleção ratificada/vigente antes da captura.
ctl.fn_runtime_tariff_valid conserva o ramo anterior e as verificações de
privilégio/consumo; reconhece adicionalmente somente esse binding analítico
LOCAL_SHADOW/LOCAL_V2/LOCAL_V2/cotacoes, família/fingerprint/escopo sintético
exatos. O mesmo release é passado ao recovery e à promoção. Não há grants,
novos usuários, permit fabricado ou habilitação remota. V087 qualificada e
instalada; prova física seguinte em curso, sem declaração antecipada de PASS.

physical-quotes04 passou5IT,0falhas/erros/skips,15,78s:54colunas positivas,
metadata, replay, presença/zero/stale/empate, estados de conversão, ausência de
filial/rota, tarifa direcional/retry/vigência e isolamento de contexto por run.

ANA-27: Coletas usa os31 nomes Data Export conhecidos do contratoB62 com um
perfil sintético estrito, sem estender garantia do fornecedor. V088 mantém12
campos laterais GraphQL tipados e vinculados à captura DE por declaração
explícita; não infere equivalência entre o IDString GraphQL e o IDINTEGER DE.
V089 prepara31 atributos por observação e26 por raiz. Os cinco aliases de filho
pck_mik_ permanecem na linhagem física. A preparação segue a precedência COL11
existente: terminal antes do relógio, depois epoch/nano exatos. Empate com
atributo divergente recusa; ABSENT preserva, NULL limpa, replay/stale avança
extração/último visto sem regredir o snapshot. DuasIT de suplemento e três de
preparação passaram nas campanhas collection-supplement01/preparation02.

ANA-28: SQL03/04 preservam41/13 nomes e ordens de negócio. Filial e usuários
dependem de vínculos explícitos. Usuário inativo/ausente usa ID bruto no display;
nome não vira identidade. NumeroManifesto usa MC atual, coorte e lifecycle MAN;
a escolha escalar segue a ordem nominal legada de sequência/ID decrescentes.
RegiãoColeta deriva de cidade/UF conforme o mapper legado, mantendo separados
os aliases DE de região. V090 reutiliza LOGISTICS_REGION com release/receipt,
vigência e ratificação SHADOW sintéticos; CEP precede cidade/UF e depois há
fallback textual. Normalização trim-upper-nfc-v1 exige NFC na importação Java.
V091 conserva os12 suplementos efetivos por presença, inclusive a origem de
cada campo; não usa extração antiga para substituir a raiz vigente.
physical-collection-queries02 passou6IT (3consumo+3preparação), todas41colunas
positivas, metadata31+12/coortes, prioridade/fallback de região, suplemento
ABSENT/NULL e replay. Metadados19views/969colunas incluindo técnicas conferem
os673 nomes/ordens de negócio congelados. SQL04 ainda aguarda a confirmaçãoK.

ANA-29: V092 e LocalAnalyticCollectionSweep limitam a aplicação de ausência
ao universo sintético fechado de Coletas e à data da fixture. Cada observação
usa quatro capturas reais independentes para satisfazer os quatro proofs do
kernel existente; primeira observação é candidata, segunda confirma. O SQL
valida conjunto de raízes, volume, contrato, janela, páginas/terminal e recibos.
O término local Data Export continua LOCAL_TERMINAL_UNVERIFIED: a prova adicional
é exclusiva da fixture, não uma mudança de completude do fornecedor. Reuso de
capturas, receipt divergente e observação fora de ordem são recusados. Um novo
snapshot completo com reaparecimento zera os campos de ausência sem DELETE.
Histórico/receipts são imutáveis; não há mudanças nos gates operacionais ou nas
demais entidades. A aplicação usa o resultado de preview do kernel como regra,
sem alegar que esse resultado é uma credencial ou autorização produtiva.
physical-collection-sweep03 passou5IT,0falhas/erros/skips: candidata/confirmada,
13colunasSQL04positivas, reaparecimento/limpeza, replay e captura reutilizada,
receipt divergente/não provado, parcial/vazio/idNULL, observação fora de ordem,
fullMAT01 e outro run isolados. A contraprova idNULL usa a recusa anterior do
validador de paginação (IllegalStateException); não modifica o gate de contrato.
Os cenários de degradação cruzada Raster/frota/financeiro ainda dependem deJ.

ANA-30: JdbcAnalyticQueries usa enum fechado dos19contratos e catálogoempacotado
de673colunas de negócio. Tabela/ordem técnica vêm doenum,nomes dascolunas têm
quoting e osvalores de run/revisão/limite são parâmetros. SELECT TOP(max+1)
detecta cap sem sucesso truncado; ResultSetforwardonly/fetch16 entrega umalinha
porvez, textos porReader limitado131072caracteres/célula e524288/linha.
AnalyticSqlValue distingueNULL,texto,decimalexato,inteiro,bit,data,horário,
datetimecivil,offset eUUID. O leitor confere contagem/nome/tipo de metadata e
precisão/escala decimal antes de entregarvalores. Não recebe SQL ouURL livre.
compile-analytic-query-reader01 passou; physical-analytic-query-reader01 passou
2IT,0falhas/erros/skips,15.91s:19queries,673campos,tipos nativos,metadados,
NULL explícito,cap1recusado,requestinválido,isolamentooutro run e recuperação.
As19consultas foram preparadas/executadas, mas nem todas tiveramlinhaspositivas
nessa campanha; os oráculos positivos dirigidos anteriores continuamreferenciados.
OcenárioJAR integral ainda é obrigatório.

ANA-31: Raster guarda o receipt original, escopo, janela e contagens de cada
folha de captura em ledger imutável. O selo SQL exige cobertura contígua sem
lacuna ou sobreposição, ordinais completos, contagens físicas e a equação da
bisseção (chamadas = 2*folhas-1). Isso prova somente a fixture fechada local.
No-op e stale válidos atualizam última observação/extração, preservando conteúdo
e histórico de alterações. PUB-08 expõe os IDs de última observação do pai e
filho, separados dos IDs do conteúdo corrente. Datas inválidas com prefixo
1900-01-01 são inválidas, em vez de serem convertidas em sentinela.
Implementação em qualificação; baseline corrigido para incluir V089–V093.

ANA-32: um ciclo BOOTSTRAP/INCREMENTAL observa Usuários em BACKFILL explícito;
REPLAY usa REPLAY. A origem GraphQL permanece snapshot FULL sem filtro temporal
nem avanço de watermark de Usuários. Cotações usa os quatro modos existentes,
com fronteira incremental registrada pelo control plane. A captura e o attach
de Usuários são atômicos na sessão compartilhada. A campanha
physical-analytic-observation-modes-02 passou 9 IT, sem falhas, erros ou skips.

ANA-33: a contraprova física de dois donos encontrou que THROW com XACT_ABORT ON encerrava a transação do segundo dono nas três cargas. V094 conserva integralmente a lógica corrente V059/MAT01, V067/MAT02 e V078/MAT05 e acrescenta savepoint SQL com TRY/CATCH e XACT_ABORT OFF. Recusas deixam o trabalho anterior na transação compartilhada; erro irrecuperável continua impedindo prosseguimento. A origem da mudança é physical-analytic-concurrency-02 (Raster passou, três falhas de estado transacional). Qualificação/instalação confirmadas em analytic-savepoints-qualify/install-01; contraprova de recuperação em execução.

ANA-34: o cenário usa fixtures main e referências do executor anterior, incluindo a vigência necessária à competência financeira2036-04-05. AnalyticExpansionCapture reutiliza seis pipelines, plano/attach/fila/hidratação e MAT03/04; composition04 passou2IT/quatro modos. Snapshot de preparação MAN é observação append-only por captura: replay acrescenta observações, preservando duas raízes correntes e conteúdo; não é criação de outra entidade de negócio. V095 acrescenta TVP até64 selos para a composição MAN, com seleção real de captura e cardinalidade, retry exato e rollback parcial. A tabela e as travas existentes permanecem. A identidade das relações do cenário é declaração da fixture, nunca inferência de placa/nome/alias do fornecedor. Prova física do novo lote em execução.

ANA-35: o integrador main usa onze pipelines e cinco cargas na sessão reversível.
JdbcAnalyticFixtureBindings pagina seis identidades capturadas, prova execução
unívoca antes de selecionar o UUID e persiste atribuições explícitas da fixture.
Os suplementos, vínculos DIRECT/CROSSWALK, lifecycle e selos usam lotes limitados.
Usuário lateral usa a chave STRING canônica produzida pelo mapper existente.
A primeira integração expôs ausência da seleção de referência expansão/revisão2
exigida por SQL06; corrigida importação explícita. scenario02 passou1IT com18
consultas positivas e SQL04 vazio, cinco fatos, hidratação e caminhos sem fanout.
V096 registra intenção imutável, onze entradas, cinco recibos e19 contagens
obtidas nas views SQL. Conclusão confere recibos e modo real das capturas;
Usuários usa BACKFILL para os ciclos não REPLAY. Somente conclusão INCREMENTAL
contígua avança a fronteira própria do laboratório. Não altera publicação
operacional nem cria permit. Qualificação01 falhou por collation do TVP;02/03
passaram após alinhamento BIN2 e reforço de modo/imutabilidade; instalação01
confirmada. A prova completa de V096 e quatro modos está em execução.

ANA-36: SQL10 inclui recibos reais de Raster, MAT01/02/03/04/05 e cenário.
O recibo de materialização registra conclusão, sem início próprio: Inicio e
Duracao ficam nulos e Data usa a conclusão. Raster registra início/extração,
sem inventar instante de término. Proveniência distingue essas semânticas.
V097 qualificada em rollback e instalada; MonitoringIT3 passou, incluindo cinco
kinds de materialização e valores reais. Não há payload ou mensagem livre.

ANA-37: o recorte de entrada declarado pelo cenário é [2036-04-01,2036-04-02)
para os nove Data Export e o snapshot de Usuários. Raster cobre os três dias do
run. A recomposição FULL dos cinco fatos usa [2036-04-01,2036-04-04), inclusive
partições antigas e novas afetadas pela correção; não se confunde essa janela
com cobertura de extração. O relógio lógico2036-04-15 prova a inclusão de dados
fora dos últimos três dias. V098 expõe as janelas efetivas dos recibos e confere
igualdade nas entradas diárias/cobertura no Raster. Recusa modo divergente,
janela maior sem prova e partição incremental cuja fronteira já foi consumida.
Qualificação/instalação source-window01 confirmadas; contraprovas em execução.

ANA-38: Raster termina o escopo da resposta antes de recursar em janelas filhas.
Instrumentação opcional recebe a única página ativa; bytes vêm da resposta real,
lotes do batch executado, nunca de estimativa de tamanho do universo. O medidor
V2-050 foi reutilizado no cenário4/16/64/16 com página16. scale01 passou23IT,
incluindo quatro escalas,149.8s de escala e rollback agregado preservado.
284planos reais: zero spill e zero recomendação MissingIndex. Conversões de
cardinalidade em quatro planos são OPENJSON key/value da projeção delimitada,
não conversão de chave indexada de negócio. Existem avisos de grant excessivo
em conjuntos temporários pequenos e de estatísticas ausentes na massa transitória;
os registros completos ficam privados em actual-plan-review-01.json. Esses avisos
não justificaram alterar índices ou grants sem defeito observado neste recorte.
Heap é diagnóstico amostral; não comprova platô, SLO ou aceite agregado V2-050.
# ANA-40 — Qualificação final dos limites e escala

A regressão integral verify-physical-analytic-01 executou1804unitários com quatro
skips históricos e361IT (233anteriores+128analíticas), sem falhas de asserção.
Houve um timeout de leitura JDBC ao concluir o cenário64; agregados antes/depois
foram idênticos. A mesma escala havia passado na campanha dirigida anterior.
Stats dos statements e planos em cache da procedure foram exportados apenas no
alvo local: os tempos observados depois não permitem atribuir o timeout a um
índice faltante. Não se alega plano real do statement interrompido. Não se elevam
timeout, socket ou grants para converter essa tentativa em sucesso.

A nova reserva final usa4/16/32/16 raízes por vertical, onze entradas e cinco fatos,
mantendo página16, heap512MiB e240s por caso. Raster ainda possui caso separado
de256raízes para cap/recursão/liberação. Preservam-se massa64 aprovada, massa64
falha e seus limites; nenhuma escala é SLO nem prova de platô.

O mesmo verify revelou quatro pacotes abaixo do gate de branches0,60. A prova
AnalyticLaboratoryFieldGateTest usa os inventários independentes90Frete/49Raster
para isolar um único campo inválido por vez, distinguindo ABSENT/NULL de valor
com tipo inadequado. Acrescenta recusas de DTO, metadata e janela de materialização.
Não reduz o gate de cobertura. directed-analytic-field-gates01 passou142casos.

O scanner permite somente seis tuplas exatas de regra/caminho/valor sintético.
V071 usa alias SQL normalized, sem literal de credencial; quatro hashes/versão
da fixture e senha fictícia loopback conservam seu escopo. Guardas analíticas5,
anteriores11 e extensões2positivas/2negativas passaram; scan02 zero findings.
Essa prova não equivale a auditoria de feed externo de vulnerabilidades.
