# 0111 — Coletas: ausência e dezenove consumos verificados

EM_EXECUCAO A–N; prosseguir até entrega integral, sem perguntas/continue.
Predecessor0110 SHA256a3b025f29e3bd02acd6429e1f5d0a6dd48cfbc45b7bb65d3d037f429babfba00.
V092 instalada; nenhuma migration nova desde0110. Construção32/45 eaceites67/115
preservados. Nenhum processo ativo. Próxima migrationV093 livre, conferir antes.

physical-collection-sweep02/session43297 reconciliada:3IT/1falha/0erros/skips,
13.70s,2min01build. Transições/recibos passaram; contraprova idNULL esperava
ContractDriftException,masgateanteriorDataExportPageEntityLimitValidatorrecusou
comIllegalStateException/campodepaginação ausente,nuloounãoescalar. Expectativa
corrigida paratipo/mensagem reais; gate não foi alterado.

physical-collection-sweep03/session39977 passou5IT,0falhas/erros/skips,
1min47build:CollectionSweepIsolationIT2/11.03s +CollectionSweepIT3/7.895s.
Before/after preservados. Prova executada:

- PrimeiraobservaçãocandidataSQL03Excluída/bitfalse/SQL04vazia;
  repetiçãoPrepared+prepareidempotentesnãoincrementa;segundaobservaçãodistinta
  confirmaeSQL04tem13colunaspositivas;terceirasnapshotcomreaparecimentolimpa
  campos/contador/flagsemDELETE. Dozeproofsde12capturas reais nos3ciclos.
- Reuso decapturas/outrocycle53774, receiptdivergenteounãoprovado53778,
  mesmoUUIDdeprova repetido recusado, parcial/vazio53775,idNULLgatedefonte;
  nenhumdesativaouavançaaplicaçãodeausência.
- FullMAT01 eoutraexecuçãoanalítica com mesmoIDfixture nãoalteram candidata.
  Ciclopreparadoantigodepoisdenovoaplicado recusa53780.

SQL01–19 agora têm consumo físico dirigido. SQL03 com41colunas positivas em
queries02, SQL04 com13 emsweep03. Metadados19views/969colunas totais e673negócio
conferidos por metadata-nineteen-contracts01/update-collection-catalogs.cjs.
Catálogo coleta-colunas-consumo.json atualizadoSQL04TESTED_PHYSICAL_LOCAL;
matriz H/K eADR ANA29 sincronizados. Regressãointegral econsumoJAR nãoexecutados.

Próximas ações:

1. Implementar leitorJDBCtipado/limitado para19contratos efixtureempacotada de
   metadata. SELECT fechado/parametrizado porrun/refrevision, ordenaçãotécnica
   canônica, streaminguma linha/limiterybytes/cancel/timeout, semSQLarbitrário
   e semuniverse JVM. Provar tipos, NULL,limite,recusas e19metadados.
2. CenárioJAR11inputs/5fatos/19SQL usando runtimeexistentes, orquestração
   single-shot rollback,delta/hidratação/mudançadatafilial/replay/full/inc;
   ramosdegradadosRaster/frota/referência/COLinválidoisoladoscomCAPindependente.
3. CompletarC–Ipendentes eL–N:adversariais/concorrênciafísica,3escalas+repetição,
   planos/verify/233ITanteriores/JAR/scanners/schema/sucessãodiffs/report/estados.

Índice técnico para leitor (apenasproposta,nãoimplementado):todos19views têm
run_id. reference_revision existeem01–09/11/12/14–18, não10/13/19. Tiposfísicos
negócio/técnicos:time,nvarchar,varchar,datetimeoffset,decimal,date,datetime2,
uniqueidentifier,bigint,int,char,bit,smallint,tinyint. Usar enum fechado de
identificadores/tabela e metadados empacotados;colunasnominais comquoting[...].
Ordemcanônica sugerida:01/06/11/12source_key+component_id;02–05/07–09source_key;
10entity+provenance+event_id;13trip_key+stop_key;14–18entity_key+valid_from+
reference_release_id;19usuario_id. Não escolheridentidadepornomededisplay.
TOP(maxRows+1) commax4096,fetch16 eResultSetforwardonly permitecaprefusalsem
acumularquery. TextogetCharacterStreamcomlimiteporcélula/linha;decimaisexactos,
SQLtime/offset/UUID preservados por valoresJavatipados, null explícito.

Main estáemsrc/main/java/br/com/esl/etl/v2/bootstrap/Main.java. Entradasexistentes:
RelationalLaboratoryMain eExpansionLaboratoryMain. Ainda não há comando/leitor
analíticogerais. Helpersdecomposiçãostart/capturehojesãotestes;runtimeJARprecisa
composiçãomainpróprialimitada, semimportarsrc/test ou fabricarresultadosprontos.
