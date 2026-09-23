# 0110 — Coletas: consumo verificado e ausência em prova

EM_EXECUCAO A–N; continuar até entrega integral, sem perguntas/continue.
Predecessor0109 SHA2567fe154816be7879399f5bc1296bcc053f9184103fd031e8a3c3596ebadc91171.
V092 instalada;baseline sincronizado;próxima candidataV093. Construção32/45,
aceites67/115 preservados. Request/before2704/limites da rodada vigentes.

physical-collection-queries02/session91750 passou6IT,0falhas/erros/skips:
CollectionQueriesIT3(12.68s)+CollectionsIT3(2.993s),1min45build. SQL03 com41colunas
positivas,metadata31+12/3linhagens,MC atual/filial/suplemento,regiãoCEP→cidade→texto,
releaseausentebloqueia eABSENT/NULLlateral. SQL04metadados/consulta vazia apenas.
Export metadata-nineteen-contracts01:19views/969colunas totais. Script
update-collection-catalogs.cjs conferiu nomes/ordem673colunasnegócio contra
inventário inicial imutável. Catálogos31/12atualizados TESTED_PHYSICAL_LOCAL;
coleta-colunas-consumo.json novo, SQL04 PHYSICAL_PENDING. MatrizHdezoito SQL.
ADR ANA27/28/29 acrescentados;ANA29aindaemprova.

K implementado mas não qualificado:

- V092 ajusta ausência para estado0 comcamposnulos (semDELETE), acrescenta
  ciclos/proofs/aplicações/histórico imutáveis. Ciclo temidentitycycle_order,
  run/data/universo/omitFirst,FPcontrato/snapshot/policy/scope/binding/receipt,
  volume/páginas. Proofs4exatos comexecution_idFKpreparaçãoCOL eUNIQUEglobal:
  uma captura não serve para dois ciclos/observações.
- recon.usp_prepare_analytic_collection_sweep valida associação/limites,
  snapshotFP SHA256UTF16LE do descriptor fechado,
  quatrocapturas reais completas,contrato/janela/volumes/preparação, auditoria
  ctl.execution_audit/ctl.page_audit comterminalvazio/páginas contínuas,
  conjunto fechado dasraízesINTEGER:200001..200000+universo,omitindoaprimeira
  quandodeclarado. Universo2..4096,roots*3<=max;pageceil(roots*3/per)+1.
  O terminal continua LOCAL_TERMINAL_UNVERIFIED; prova adicional é sófixture.
- prepare retorna receiptFP/roots/pages. apply exige receipt/escopo/kernel,
  proíbe ciclo antigo depoisdeaplicação maisnova, confirma sóumcontadorporciclo,
  candidata1→confirmada2, reaparecimento0/limpa. Preservahistóricoecontadores,
  altera somente raizescomrun/data/intervalo eDATALENGTHchave28 (seisdígitos).
  Fullmaterialization nãochamaessaaplicação; gatesoperacionais intactos.
- SyntheticCollectionSnapshot(run,date,universeRoots,omitFirst) puro,package
  plataforma.reconciliacao.sweep,usa hashes/canonicalPolicy/Binding existentes.
  assessVerifiedCaptures usa4UUIDs reais distintos/ordinais1–4 e contagens SQL,
  chamaFailClosedSweepPreviewKernel; não representa permissão/credencial.
- JdbcAnalyticCollectionSweep.prepare(snapshot,cycle,List4UUID,token) TVP4,
  query30s/savepoint, retornaPrepared comreceipt/roots/pages, avalia kernel;
  apply(Prepared,token) usa SQL e retorna candidatos/confirmações/reativados/noops.
- LocalAnalyticCollectionSweep.observe(snapshot,cycle,token):4capturas reais
  LocalAnalyticCollectionRuntime BACKFILL da mesmafixturefechada;sourcefirst1/2
  eexpectedRoots,3linhasfísicas/root. Umaobservação possuiesseconjunto de4provas;
  segundaobservaçãotem4UUIDs novos. Guardas:run/datafixadaàfixture2036-04-01,
  page<=16,volume<=policy. Capturas+prepare+apply na mesmasavepoint/TXrollback.
  Replay dePrepared passa pelosreceipts SQL e nãoincrementa confirmação.

Tentativas preservadas:

- qualify-collection-sweep01/02 passaram;02depoisguardsnulos/ordem/intervalo.
  install-collection-sweep01 passou; V092imutável a partirdaí.
- compile-collection-sweep01/session95287 falhou naformatação por sintaxe.
  Causa:helper privado split-own-literals.cjs sócasavaliteraislongos e podia
  tomaraspas de fechamentocomoinício entreexpressõescompactas. Corrigido para
  reconhecer todososliterais antesde decidir dividir; ignoraescapes. Umfragmento
  dePrepared foi reparado. Não rerodargeradoresdemigrations instaladas.
- physical-collection-sweep01/session67209 compilou e executou3IT,0fails/3errors:
  driverSQLServerDataTable nãoaceita GUID emTVP. Corrigido parametadataNVARCHAR
  comUUID.toString(),conversãoSQLmantémUNIQUEIDENTIFIER tipado.
- physical-collection-sweep02/session43297 EM_EXECUCAO:reconciliar retorno,
  <attempt>-exit.json e <attempt>-failsafe antesderepetir. Selecionada
  AnalyticLaboratoryCollectionSweepIT3. Não presumir PASS.
- AnalyticLaboratoryCollectionSweepIsolationIT2novo ainda NÃO rodou:fullMAT01
  eoutroanarunnãoalteramcandidata;observaçãoantigapreparadanãoaplicadepoisdanova.
  Helper SweepIT.captures passoupackage-private enquanto02rodava; mudança
  apenas deacessoparanovateste. Runnerprotegecopybackporhashdeentrada.

SweepIT3:primeiracandidataSQL03bitfalse,segundaSQL0413colunaspositivas,
replayexato/prepareidem,reativaçãozeracampos,12proofsreais;recusasreceiptfalso,
capturareusada,capturassemcycleprovado,mesmoUUIDduplicado;fixtureparcial/vazia
e idNULL devemmantercandidata/applicationcount. Corrigirresultados locais.

Próximas ações:

1. Reconciliar physical-collection-sweep02/session43297, corrigir até passar;
   executar IsolationIT2, atualizar statusSQL04/catálogo/matriz/ADR e checkpoint.
2. ImplementarleitorJDBCtipadobounded eJAR composto11inputs/5fatos/19SQL,
   modos/delta/hidratação/replay/independência degradada;completarpendênciasC–I.
3. L–N:oráculosadversariais/concorrênciareal,3escalas+repetição,planos,
   verify/233ITanteriores/JAR/scanners/schema/sucessãoexata/diff/report/estados.

Não há comandoJAR analítico/leitorgeral nem cenário11/5/19 ainda. Não encerrar
por checkpoint/compactação. MantertrabalhopeloescopoA–N até entregafinal.
