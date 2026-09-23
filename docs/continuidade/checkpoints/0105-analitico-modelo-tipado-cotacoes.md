# 0105 — Modelo tipado e transporte sintético de Cotações

EM_EXECUCAO A–N, prosseguir integralmente sem perguntas/continue. Predecessor
0104 SHA2563a702f2650f9a3165ba095923dc9bcb08f4f74df6a95a024a46e1d6ff484d99b.
Mesmos request/before2704/limites. Construção32/45 e aceites67/115 intactos.
V001–V082 instaladas; baseline acompanha. V083 ainda livre/não criada.

directed-quote-model-02/session27670 reconciliada exit0:2unitários,0falhas/erros/
skips,1,976s testes,1min32build. Sem processo próprio ativo conhecido. Não há
prova física Cotações nem SQL05. Dezesseis contratos anteriores permanecem.
Tentativa01/session19491 preservada:2testes/1falha no esperado de raw. O parser
existente preserva texto bruto sem aspas e Wire.STRING separado; corrigido
esperado e adicionado assertwire. Compilação/estilo passaram na revisão02.

Arquivos novos compilados:

- plataforma/analitico/AnalyticQuoteAttributes.java:36campos ExpansionValue
  com String/BigDecimal/Instant, lista fechada36 para persistência e valid().
- plataforma/fonte/dataexport/AnalyticQuoteMapper.java:36 parsers existentes
  ExpansionFieldParser; decimal28,8, instante9nanos, text1024/raw4000.
- plataforma/fonte/dataexport/AnalyticQuotesSyntheticSource.java:profile36+
  synthetic_fixture, release synthetic-analytic-quotes-v1; gatewaybundle real
  metadata/resposta, JSONestrito, sem rede,64KiB/página e métricas. bundle() ainda
  não atravessou dispatcher emIT; compilação não substitui essa prova.
- bootstrap/AnalyticQuotesFixtures.java:resource de entrada lazy3repetições por
  raiz, page1..16,até2048raízes. quote-fields.synthetic.json e quote.synthetic.json
  emsrc/main/resources/analytic-laboratory. Não são resultados finais prontos.
- AnalyticLaboratoryQuoteMapperTest.java:tipos/precisão/presença e recusa de
  decimal9casas/text1025. Gerador privado build-quote-model.cjs tem guard.

Inventário público cotacao-campos-captura.json36 seguePENDING até integração;
shaDTOlegado4bb1516403d44cd9ba2ba8c922a4a3f75d949e22699a2914581b92ad18290d29.
Fonte da tarifa legada tem25pares; ausência de combinação nunca default0.

Próxima implementação concreta:

1. Persistência auxiliar tipada36 por stg.execution_record(stage_record_id),
   lookup execution_id/input_batch_number/input_record_ordinal, ligada a
   stg.cotacao_record e capturas/escopo analítico. TVP16/256KiB e comparação
   completa. WrapperCotacaoStagingGateway deve validar/mapear antes da publicação
   efetiva e usar a mesma conexão/savepoint. Depois, snapshot analítico por
   run/chave e lineage da versão efetivamente publicada, evitando fanout/stale.
2. LocalAnalyticQuotesRuntime via RuntimeWorkloadRegistry.of(definition).plan,
   RuntimePlanningRequest/RuntimeExecutionRequest, RuntimeDispatcher e
   LocalFiveVerticalRuntime.cotacoes. FontesLOCAL_V2/tenantLOCAL_V2, qualidade
   JdbcAnalyticQuality, sourcebundle/guardreal, promoção tarifária original.
   Recursos não aceitamURL/payload real. Acrescentar ramoCOT emsource_current.
3. Tarifa local versionada eSQL05/54colunas +provas; depoisSQL03/04+K, JDBC/JAR,
   demais casos C–I/L–N e entrega final integral (pendências anteriores intactas).

Cuidados descobertos para a implementação:

- core.cotacao.last_seen_execution_id avança emNO_OP eSTALE_NO_OP (V011linha410),
  embora payload só mude com freshness_at_utc maior. Não usar indiscriminadamente
  a última captura como fonte dos atributos. Guardar pointer próprio para a
  versão efetiva e linha de auditoria da observação antiga; ABSENT preserva.
- core.cotacao é escopadaLOCAL_V2/tenant/chave, semrunanalítico. Resultados de
  umrun não podem ser reescritos pela captura de outrorun; snapshots imutáveis
  locais e binding explícito são necessários, sem inventar identidade por nome.
- Modelo originalCOT mantém totalDEC19,4 e relógio3ms; novos atributos guardam
  precisão8/9, mas não relaxar silenciosamente os gates originais nem promover
  empate como nova versão. Contraprovas físicas devem caracterizar esse limite.
- ref.tarifa_rota_uf (V008) já éconsumida na promoçãoV011. Exige release
  QUOTE_TARIFF ratificadaSHADOW, não revogada, coberturaPRICED unívoca, valor>0,
  currency/unit/rounding. Alookup usa freshness_business_date, não arrival.
- Ratificação V008 exige receipt, autor/importador/aprovador distintos e datas
  ordenadas. Roles exclusivamente sintéticos e evidenciaFIXTURE_ONLY_ROLLBACK
  no escopo/run local não alegam pessoa/aceite real. Seleção do wrapper deve
  conferirrun/revisão/vigência além do gate antigo que só confere família.
- Ratificações de mesmafamília/escopo comjanelas sobrepostas são recusadas;
  não adulterarrelease antiga para testar revisão. Usarscope local explícito
  porrun/revisão e validar a seleção exata. Nenhum grant/cutover é autorizado.
- RuntimeWorkloadRegistry cria plano peloAPIpúblico; não há necessidade de
  alterarRuntimeLaboratoryContract/runtime-laboratory.properties históricos.
  DataExportHttpGatewayBundle.contractBound temacessopackage e a fonte nova
  jáestá no pacote correto. DataExportTemplateInfo tem construtor(template,200,
  Optional.empty,true,List<DataExportMetadataField>,filtros); metadados gerados
  correspondem ao mesmo profile da resposta.
- buildscriptTests com vírgula precisa uma string emaspas noPowerShell.
  DDLqualificar/instalar antes daIT, baseline acompanha, nunca editarinstaladas.
