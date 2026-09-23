# 0108 — Coletas: contrato e suplemento tipado

EM_EXECUCAO A–N; continuar até entrega integral sem perguntas/continue.
Predecessor0107 SHA256cd9bef5ba61f420542267a8208665ff33df0092a96660d36d254bb9f1109e06d.
Request/before2704/limites vigentes; construção32/45, aceites67/115. V088
instalada e baseline sincronizado. Próxima candidataV089, conferir antes.

compile-collection-contract01/session4597 passou1min25:perfil31 e extensão
opt-in compilados. directed-collection-contract01/session5634 passou2unitários,
0falhas/erros/skips,2,559s/1min35. compile-collection-supplement01/session29197
passou1min27,559fontes/394fontesdeteste. Essa cópia precedeu o novo IT.

physical-collection-supplement01/session24458 em curso nestecheckpoint; consultar
reports/log/exit da rodada antes de repetir. Sem outros processos ativos conhecidos.
V088 qualify/install-collection-supplement01 passaram comcontagens preservadas.
Nenhuma SQL03/04 implementada ainda; dezesseteSQL anteriores permanecem físicos.
metadata-seventeen-contracts01 registrou17contratos/883colunas incluindo técnicas.

Implementação nesta unidade:

- AnalyticCollectionContract e recurso collection-fields.synthetic.json31
  usam os nomes de DataExportColetasContractCatalogB62. Tipos mais estritos
  INTEGER/STRING são política de fixture local, não garantia do fornecedor.
- RelationalSyntheticSource.withAnalyticCollectionDetails() e analyticContracts()
  acrescentam perfilCOL e conjuntoMANcompleto/COLcompleto/FRTantigo. Métodos
  defaults/analyticManifestContracts continuam comfingerprints anteriores.
  observed() conserva os dois flags; usar perfilCOL comoutrotemplate érecusado.
- AnalyticCollectionSupplement record12campos ExpansionValue, mapper separado
  fonte/graphql/AnalyticCollectionSupplementMapper: envelopeproveniênciaversionado,
  pathsGraphQLcamelCase e parentABSENT/NULL distintos; TIME9/INSTANT9, raw e wire.
  Campos não são injetados no payload6908 nem alegam crosswalk GraphQL/DE.
- V088 stg.analytic_collection_supplement com12campos concretos+presence/wire/raw,
  TIME nanoOfDay/BIGINT, instanteepoch+nano, texto1024/raw4000,inteiroVARCHAR40
  validado paraBIGINTnão negativo; FK(execução,chave)→stg.relational_lab_root;
  UNIQUE(run,key,exec,revision),triggerimutável,referências opcionaisusuário.
  TVP16/256KiB; procedure verificarealassociaçãorun/COLcaptura, valores, usuário
  anexadomesmorun; retrycompletoEXCEPT+bytes,savepoint/XACT_ABORTOFF.
  Motivos53731bound/53732source/53733value/53734user/53735retry.
- JdbcAnalyticCollectionSupplements.Binding(sourceKey,execution,revision,
  attributes,cancellationUserKey,destroyUserKey), bind(run,List<=16,token):
  TVP/comparaçãolenprefix12campos+userkeys,bytes256KiB,savepoint/query10s/receipt.
- AnalyticCollectionsFixtures.data()/supplement()/source(first,roots,pageSize)
  leemapacotados<=16KiB;sourcegera3repetições lazy, id200000+i/alias100000+i,
  roots<=4096/page<=16 e perfilCOL explícito. Datasbase2036-04-01.
- Testhelper AnalyticLaboratoryDimensionsIT.start(session,contracts) foi
  acrescentado; sobrecargabooleananterior delegasemmudardefaults.
- AnalyticLaboratoryCollectionSupplementIT2provas aindaemexecução:3físicas→1raiz,
  suplemento12positivo/time9/Unicode/semcamposGraphQLnopayloadDE, retryimutável,
  scopedivergente/usuárioausente preservandoestado. Confrontar retorno real.

Catálogos novos: coleta-campos-captura.json31 e coleta-suplemento-campos.json12.
DTOGraphQL legado SHA25617bc3b2e05ac6bd11f8581471842064720aeea4985a2757d5b04812ff177eb53.
12camposlaterais emordemDB/Java:request_hour,vehicle_type_id,customer_name,
customer_document,address_line,address_number,address_complement,branch_source_id,
cancellation_user_id,destroy_reason,destroy_user_id,status_updated_at.
Registrodeusuárioébindingexplícito opcional; semusuárioativo oconsumidor deverá
usar IDbruto conformePUB02, nunca inferir identidadepornome ouporcoocorrência.

Próximas ações:

1. Reconciliar ITsuplemento; corrigir. Preparar V089paraobservações tipadas31
   sobrestg.coleta_record e snapshots/currentanalíticos; usarexecuções reais
   deLocalRelationalRuntime, perfil esperadoexato, captura+prepareatômicos.
   Não reconstruir pipeline. NormalizarCPU/massa noSQL, semuniversoemJava.
2. SQL03/04 eSweepK:fonte31+suplemento12,filialbinding,MCatualparanúmerodoMAN,
   releaseLOGISTICS_REGION CEPantescidade/UF,ColetaStatusCOL11existente,
   Usuáriossemfanout. Ausênciacandidata/confirmada/reappearance sólabkernel+SQL.
3. Completar J–N ependências C–I conforme0107/matriz:leitorJDBCtipado/JAR11/5/19,
   modos/hidratação/delta/concorrência/escalas/verify233IT/diffs/sucessão/relatório.

Descobertas para preparação, ainda decisões a implementar/testar:

- stg.coleta_exact_time (V027) éaorigemcorretaepoch_second/nano/statusRaw;
  stg.coleta_recordtemstage_record_id/exec/key,payload_json/statusraw/code/label,
  terminal,occurrence_action/attempt_count. stg.execution_record.staged_at_utc
  fornece extração (não observed_at_utc). Captura seladaemctl.relational_lab_capture.
- core.relational_lab_root usa(run,entity,key),exec,alias,epoch_second,nano,
  status_code,terminal;stg.relational_lab_root guarda cadaexec/key. V077mantém
  terminalprioritário paraCOL:aberto→terminal atualizamesmoantigo;terminal→aberto
  éNOOP;depoiscompareepoch/nano. PointerexecsóavançaemINSERT/UPDATE, nãoNOOP.
  Preparaçãoanalíticadeve seguiraversãoefetiva ecaracterizaratributosnovos em
  empate; não deixarúltimapáginasubstituir campos silenciosamente.
- Os31campos incluemaliases de itens/MAN/veículo quepodemdiferirentrelinhas
  físicas; não transformá-los ematributosunívocos daraiz nememrelaçãocanônica.
  Guardarporobservação e usarMC explícitoatualnoconsumidor;complementarporcampo
  somentedepoisdeprovarcoerência,recusar doisvalores/NULLconflitantes.
- V064modeloMANmostraOPENJSON31/92declarações,tabelaSQLtipada eissuesantesde
  conversãolimitada;stg.ufn_analytic_decimal_exact ecore.ufn_analytic_iso_time
  existem. Esteúltimo truncafração>7; para31novos validar máximo9antesdeuso.
  core.ufn_analytic_time_comparison conservaepoch+nano9viaMANclock (V074).
- SuplementoV088 guardaobservação, ainda não fazmergeABSENTnemosnapshotefetivo.
  Oconsumidor precisaresolver presença/lineage/revisões explicitamente, sem
  apresentarNULLconstanteouresultadofinalpreenchidonafixture.
