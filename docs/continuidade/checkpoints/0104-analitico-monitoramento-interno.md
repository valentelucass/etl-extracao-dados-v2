# 0104 — Monitoramento interno e início do inventário Cotações

EM_EXECUCAO A–N. Prosseguir integralmente sem perguntas/continue. Predecessor
0103 SHA256fa11cd7b0ee16d9f887bd01807de3ce8c9f27d2fa5ac3427de59069204e922d4.
Mesmos request/before2704/autorizações/snapshots. Construção32/45,aceites67/115
intactos. V001–V082 instaladas e imutáveis; baseline acompanha. PróximaV083.

physical-monitoring-02/session78686 reconciliada exit0:2IT,0falhas/erros/skips,
6,746s testes,1min33build; contagens antes/depois preservadas. Nenhum processo
próprio ativo conhecido. Agora16contratos SQL com consumo físico; faltam03/04/05.
Último exportmetadata quinze contratos798colunas inclui técnicas; export final
ainda obrigatório. Leitor JDBC limitado e JAR não foram implementados.

V081 SQL10 une capturas da expansão, relacional/dependências e execuções
anexadas do runtime. Exibe timestamp/contagens/reason codes fechados e não
projeta payload/segredo/texto livre. Qualify-monitoring-01 falhou457 por collation
UNION; corrigido antes de instalar. Qualificação02 e instalação01 confirmadas.
physical-monitoring-01/session96105 executou2IT com1falha: o filtro igualdade
QUARANTINE perdia os motivos QUARANTINE_FIELD/etc da expansão. V082 corrige
para LIKE QUARANTINE[_]%, sem modificarV081. Qualificação/instalação confirmadas;
campanha02 passou9colunas positivas/degradação/canário e isolamento/não-fanout.
Todos os logs/tentativas anteriores ficam preservados.

Cotações: novo catálogo cotacao-campos-captura.json inventaria36JsonProperty
do DTO legado autorizado, com SHA da fonte, tipos locais e statusPENDING.
Eles sustentam SQL05/54colunas. Não há V083, novo runtime/parser/fixture de
Cotações, nem prova física dessa frente. Não contar inventário como código.

Achados para próxima implementação:

- LocalFiveVerticalRuntime.cotacoes já compõe extrator, parser9campos, staging,
  promoção efetiva vinculada à tarifa e DQ. Core.cotacao guarda payload/presença,
  totalDEC19,4 e timestamps3; leitura analítica precisa atributos36 tipados sem
  perdas, com raw/precisão original, por staging/captura e consumidor54colunas.
- RuntimeWorkloadRegistry.of(definition).plan(RuntimePlanningRequest) constrói
  RuntimeExecutionPlan legítimo; RuntimeDispatcher/LocalFiveVerticalRuntime
  permitem usar o pipeline sem fabricar permit nem mexer em profiles históricos.
  SourceContractRelease local36+synthetic_fixture deve passar pelo gate real.
  DataExportHttpGatewayBundle.contractBound tem acesso package, então transporte
  fica em plataforma.fonte.dataexport, com JSONestrito/bytes/paginação limitados.
- JdbcAnalyticQuality já suportaEntityCOTACOES mas sóBACKFILL/REPLAY; SQLresource
  quality-policy.sql também limita esses modos e source/tenantLOCAL_V2.
  JdbcAnalyticDimensions.attachExecution aceitaCOT, mas V056 source_current não
  tem ramoCOT: necessário acrescentar. Fonte de tarifa existenteQUOTE_TARIFF,
  referência obrigatória na promoção, jamais default0 para combinação ausente.
- ExpansionFieldParser está em plataforma/fonte/dataexport (não expansao),
  preservaExpansionValue raw/wire/presença; decimal28,8/text1024/INSTANToffset9.
  Reutilizar nos atributos se adequado; não transportar massa na JVM.
- Kernel Sweep existente exige quatro Proofs distintos (duas travessias e duas
  ausências), ordinal1..4, fingerprints técnicos independentes para ocorrências/
  runs; policy/scope/snapshot/binding iguais ao escopo. É apenas preview, aplicar
  somente via novo caminho sintético opt-in com duas observações físicas reais.

Próximas ações:

1. SQL05 Cotações36atributos/54colunas, referência tarifária, runtime existente,
   positivos/recusas e consumidor real. Próxima migrationV083 livre.
2. SQL03/04+K, leitorJDBC e composiçãoJAR11entradas/5fatos/19saídas; concluir
   todos os casos e mecanismos pendentes registrados0103 e anteriores.
3. Provas L–N completas: concorrência/escalas/planos/verify233IT/JAR, diff
   inicial, matriz673, estados funcionais, sucessão e relatório final.
