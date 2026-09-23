# 0113 — Modos analíticos em qualificação

EM_EXECUCAO A–N; continuar até entrega integral sem perguntas/continue.
Predecessor0112 SHA2562a3eebd3e64d4230691df362b397cd369bf9e633c3d12097e070c9036630272f.
V092 continua última instalada;nenhuma migration nova. Construção32/45 e
aceites67/115 preservados. Cenário/JAR11/5/19 e demaiscritériosJ–Nabertos.

Pré-condição:leitorJdbcAnalyticQueries2IT/19queriespassou, sempendêncialocal
conhecida naquelacampanha. Preparação da composição J revelouque COT/USUARIO
analíticos sóaceitavamBACKFILL/REPLAY. EscopoJexige4modos com partições explícitas.

Alterações emqualificação (NÃO marcarPASS ainda):

- RuntimeUsersRequest.read(config,root) continua comregrasmodaisanteriores;
  delegaaprivate read(...,false),preservandofingerprint/requisiçãoantiga.
  Novaentrada package-private readAnalyticLaboratory(config,root) exige
  environmentLOCAL_SHADOW eGraphQLsourceInstance/tenantLOCAL_V2exatos;
  privateflagtrue permiteBOOTSTRAP/INCREMENTALalémBACKFILL/REPLAY.
  Intervalocontinuadeobservação, nuncapassaasersource-timefilterGraphQL.
- LocalAnalyticUsersRuntime.capture agora envolve registro/pipeline/publicação/
  attach emsavepointúnico;captureWithinusaentradaanalíticaespecífica e4modos.
  EmException/cancel,reverteatéstateanterior antesderepropagar.
- LocalAnalyticQuotesRuntime admite4modoslocais;planejamentocontinuacom
  RuntimeExecutionRequest, FULL epartiçãodataUTC explícita,replayOfcoerente,
  tarifaDQV087/contratos/promoção/qualityexistentes.
- JdbcAnalyticQuality e recurso quality-policy.sql permitem4modossomenteno
  scope jáfechadoanarun/localhostSHADOW/USUARIOSouCOTACOES. Checks/thresholds
  zero mantidos;apenasengineDQexistenteemitepermits. Nenhumincrementalremoto,
  produção,sourcecompletenessougategenéricodeUsuáriosfoirelaxado.
- AnalyticLaboratoryObservationModesIT2novas:USUARIOS4modosPUBLISHED+2pessoas
  efetivas/NULLdelta;COT4modosPUBLISHED/1snapshotnegócioinvariável.
  RepetemseUserIT2eQuotesIT5 pararegessãodirigida das entradasalteradas.

physical-analytic-observation-modes01/session79748 EM_EXECUCAO. Reconciliar
<attempt>-exit.json,<attempt>-failsafe/retornoprocesso antesderepetir. Nenhum
outroprocessoativo;nenhumnovoDDL. Runnerselecionou3classes/9ITesperadas.

Próximas ações:

1. Reconciliarmodes01/session79748,corrigirfalhaseprovar4modos+regressão;
   atualizarADR/matriz/estado. Maisadiante rodartestesRuntimeUsersRequest/Main
   antigosparaassegurarreadcomumcontinualimitado;suíteintegra aindaobrigatória.
2. Criarfonte/AnalyticScenarioRuntime eCLI/JAR11/5/19,inputsdeorigem via
   pipelinesexistentes e dependênciainicialmenteausente hidratada. Delta,
   correçãodatafilial,replaycomparado,publicaçãolocalterminal/frontiercontígua.
3. CompletarpendênciasC–I,Kdegradado,L–N:adversariais/concorrência,escalas/
   planos/verify233IT/JAR/scanners/sucessão/diff/report/estadosatéentregafinal.

Índiceparacomposição:

- Recursosmainfreight-attributes.synthetic.json jácontêm90valores tipados
  envelope attributes +provenance/version. UsaFreightAnalyticAttributesMapper
  eJdbcFreightAnalyticAttributes.captureBatch(run,freightExecution,List<=16,
  token) comFreightSupplementObservation(key,revision,attrs,evidence).
- ExpansionDependencyFixtures.data(FRETES,index) defineid300000+index e
  corporation_sequence_number600000+index; LOCmesmocorpalias. financialTerms(key)
  leepackagedrevision/data/BRL/MAJOR/classification/courtesy/eligible/volume/payer.
  Fontecom.withFinancialBindings(...).withAnalyticFreightPerformance();
  JdbcAnalyticSourceContracts.freightPerformance(run)antesdacaptura.
- FonteMAN92mainperfilJSON, masfixturecomvaloreshojeestáemteste
  AnalyticLaboratoryManifestCaptureIT.manifest():INTEGER1/BOOLEANtrue/ARRAY2texts,
  STRINGtime9para*_at,DEC100.125paracampossubtotal/total/cost/weight/...senãoSYN;
  chave44ones/statusclosed/mdfeauthorized/opSYNTHETIC NORMAL/branchA.
  MAT05setupoverride total120/contractaggregate/drivercompany/calculationprice_table/
  cargofractioned/pick100001;activateMANporbindingexplícito;roles4/branch;
  DIRECT1→FRETE300001 e sealComposition(expected1).
- ExpansionINV/SIN/FAT/CAPsourcefillhelper emInventoryIncidentQueriesIT.filled
  lêdocs/catalogos/macrobloco-expansao/campos-tipados.json (specs/code/fields),
  preenchefaltantes integer7/dec7.125/boolfalse/instant9/date/time9/array2/text,
  INVtypeCheckIn::Order::Loading,FATfit_nse_number7/nfse_numberSTRING7.
  ParaJARempacotarfixturedefonte; não ler docs externos nemimportartestes.
- Raster não temfixturemain ainda. TesteROWéfontesintéticaminimalde9campos;
  docs/catalogos/macrobloco-analitico/raster-campos.json lista51entidade/aliases/
  tipos; criarfixturecompleta favorecependênciaD51. Parser/RasterFieldParser
  listamcamposreais. Gatewaymainfechadocomterminal(binding/counters/window)
  sólocal. Resolverterminalreceiptconstant/PUB08NOOPextracaoaindaobrigatório.
- ExpansionLaboratoryMainéentradaClasspath; Mainbootstrap não temdispatch
  delaboratóriosanteriores. Menorextensão compatívelpodeadicionarentrada
  AnalyticLaboratoryMain(classpathJAR),masvalidarinterpretaçãoderequisitojar.
  CLIOptionsparseantesconn/flagsynthetic/budgets,semarbitrarypaths/payload/URL.
