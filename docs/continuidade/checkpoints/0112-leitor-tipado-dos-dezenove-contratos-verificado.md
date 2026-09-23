# 0112 — Leitor tipado dos dezenove contratos verificado

EM_EXECUCAO A–N; continuar até entrega integral sem perguntas/continue.
Predecessor0111 SHA256c64b2f755e70d6cba79b8a530f3fb4df52a2465a49d4571059abb644b701cc01.
V092 continua última instalada/baseline; nenhuma migration nestaunidade.
Construção32/45,aceites67/115. Nenhum processo ativo. J–Nobrigatóriosabertos.

compile-analytic-query-reader01/session80227 passou1min28:568classesmain,
399classesdeteste nafoto deentrada. physical-analytic-query-reader01/session22372
passou2IT,0falhas/erros/skips,15.91s/1min50build,before/after preservados.

Implementação:

- AnalyticSqlContract enum19,contagemcolunas,total673, flagreference_revision
  porcontratoeordemcanônica técnica. localName gera pub.analytic_lab_sql_XX.
- AnalyticSqlValue sealedinterface comrecordsMissing/Text/Decimal/IntegerValue/
  Flag/Date/Time/CivilDateTime/OffsetDateTimeValue/Identifier. Datas,números eUUID
  não são degradadosparaString ouObject genérico.
- JdbcAnalyticQueries(session).read(run,contract,revision,maxRows,token,consumer):
  enum/table/order fechados;colunasnominaisquotadasdecatálogoempacotado,
  valoresparametrizados;TOP(max+1),ResultSetforwardonly,fetch16,timeout30s,
  maxRows1..4096,revision1..100000,cancelantes/perrow/perchunk/depois.
  TextosviaReader combuffer4096,limite131072caracterescélula e524288linha.
  Falhaexcederows/cell/row,não retornasucessotruncado. EntregaumRowListtipado
  porvez; nãoacumulauniverso (consumeréresponsávelpeloqueguardaforadoreader).
  ReadReceipt(query,rows,columns,textCharacters,maximumRowCharacters,fetchSize).
  Metadata conferecontagem/nome/tipo,decimalprecision/scale;NULL inesperadonão
  nullable recusa. MapperdriverLocalTime/LocalDateTime/UUID foi exercitado.
- query-contracts.synthetic.json empacotado127507bytes/673colunas, gerado
  doinventáriocongelado+metadata-nineteen-contracts01 comnomes/ordensconferidos.
  Parserstrictduplicate/trailing,bound256KiB,19contratos/conteúdoenum/ordinais.
  Geradorprivadobuild-query-contract-resource.cjs recusasobrescrita.
- AnalyticLaboratoryQueryReaderIT:preparaCOLcomsuplemento eausênciaconfirmada;
  executa19queries/fixa673colunas eexercitatiposCOL+UUIDda confirmaçãoSQL04.
  Algumasoutrasviewsestavamvaziasnessefixture;não alegartodaslinhaspositivas
  nestacampanha. Oráculosdirigidosanteriorescontinuamválidosporfrente.
  Cap1SQL10recusaapós1row;requestinválidorecusa;runaleatórionãovaza;
  leiturasposterioresfuncionam. Não houvefalhanestafrente.

MatrizH/ADRANA30atualizados. Main ainda NÃO alterado, cenárioJARnãoimplementado.
Mainlocalizadoemb ootstrap/Main.java (sem espaço:bootstrap/Main.java), entradas
classpathanterioresRelationalLaboratoryMain/ExpansionLaboratoryMain.

Próximas ações:

1. Composição J main/fixtures:11verticais=noveDE(COT,MAN,COL,FRETE,LOC,CAP,FAT,
   INV,SIN)+USUARIO+RASTER. REL_FRT ésegunda captura domesmoFretepararelações,
   não12ªvertical. Reusar pipelines epublicações; prepararsomentefixturedefonte,
   nuncaresultado/tabelafinal. FonteprincipalAnalyticScenarioRuntime ainda falta.
2. J positivo:capturar/refs/arestas/hidratarinicialmenteausente/5fatos/19queries,
   delta+correçãodatafilial+replay+comparaçãoSQLindependente,rollbackinteiro.
   CLIone-shot scenario/replay/status/query,flags/budgetsantesconexão,empacotado
   semarbitraryURL/payload/permit;mesmoprocessoreconstróiprópriocenário.
3. CompletarC–Ipendentes/Kdegradado/L–N:oráculos/concorrênciareal,3escalas+
   repetição/planos,verify/233ITanteriores/JAR/scanners/schema/sucessão/diff/report.

Índice de composição existente:

- AnalyticLaboratoryDimensionsIT.start(session,RelationalSyntheticSource.
  analyticContracts()) criaanarun/exprun/relrun,JdbcRasterLaboratory.start
  (DATE,DATE+3,America/SaoPaulo,100000,100),ExpansionPolicy(DATE,DATE+3,DATE,
  page2,maxpages100,maxrows1000,FiscalPolicy.SYNTHETIC_CTE),relpolicy
  (DATE,DATE+2,1000,10,3,60,2,0,2,100),associate,refsrev1,Frete3/CAP2captures.
  Essehelperestánostestes;mainprecisacomposiçãoequivalenteprópria.
- LocalAnalyticUsersRuntime.capture aceita BACKFILL/REPLAYsomente, retorna
  StagingPublicationResult; usaGraphQlRelay real/contrato/DQ/recovery/attach.
  LocalAnalyticQuotesRuntime tambémBACKFILL/REPLAYcomtarifa explícita V087.
- LocalRelationalRuntime.capture recusaSWEEP,usaoutrosmodoscomreplayOfcoerente;
  current_statefinaléDEGRADED/motivoREL_LAB_CAPTURE_ONLY deliberadamente.
  Não confundircapturatécnicalocalcoma publicaçãooperacional. Cenárioanalítico
  precisareceiptlocal terminalpróprio comgatescompletos, semrelaxargatesold.
- ExpansionLaboratoryMain temOptions.parse(boundroots256/page16/days3/etc),
  parseantesabrirsessão/deadline240s/recusascategorizadas/umprocessorollback.
  execute criaeplanificaJdbcExpansionRecomposition/Executor; nãoimportartestes.
- JdbcAnalyticMaterializations.freight/collectors/manifests=request8params,
  query30s/savepoint/Receipt6(candidates,inserts,updates,noops,ready,blocked).
  JdbcExpansionMaterializations.invoices/revenue jáexistentesparaMAT04/03.
- FreteOperationalIT.capture helper392..usaExpansionSyntheticSource com
  withFinancialBindings(key->terms).withAnalyticFreightPerformance(); fornece
  exemploparafixturemain deFrete90attrs eMAT01. MANfixture92fonteemteste
  ManifestCaptureIT.manifest() derivaperfilJSON;novofixturemainpodeempacotar
  fontefechada/parametrizada,comidads/time/revision explícitos.

ReverRasterterminalreceiptconstant eNOOPlatestextractedPUB08 emC–I/L.
Rever savepoint/rollback em wrappers antigos secontraprovarevelardoomedTX;
nãorelaxar233IT/gatesoperacionaisparaobterstatusglobalpositivo.
