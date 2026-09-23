# 0117 — Expansão composta e selos em lote

EM_EXECUCAO A–N. Construção32/45; aceite histórico67/115.
Predecessor0116 SHA256
8a4d145004ec8c44e974a39f941cd10953a75e204a54e9a45cfb78e5cc14a546.
Evidências: target/macrobloco-analitico-20260912-01.

V095 qualificada/instalada, baseline incluiV001–V095. Reservas confirmadas:
composition-batch-qualify-01 e composition-batch-install-01. V096 livre.
As migrations aplicadas são imutáveis; sem DML de domínio persistido.

AnalyticExpansionCapture/AnalyticScenarioFixtures agora têm prova executada:

- composition01/session59847: falha de compilação por chamada a TestRuntime
  em vez de desembrulhar .runtime(); corrigida, sem mudar produção para contornar.
- composition02/session43526:2IT/2falhas. Esperado incorreto no novo teste MAN
  confundia snapshots de preparação com raízes correntes; o contrato antigo
  (ManifestPreparationIT) já registra observações append-only por captura.
  A expansão ficou degradada; acrescentado diagnóstico agregado de motivos.
- composition03/session61809:2IT/1falha. MAN passou com4observações após replay
  e2raízes correntes. Expansão: INVOICE READY2, LINK RESOLVED14 e REVENUE
  REFERENCE_MISSING2. Os termos empacotados usam competência2036-04-05, fora da
  vigência curta usada pelo novo integrador. Reutilizada a janela do executor
  anterior: start-7..end+7; captura continua em partição explícita de1dia.
- composition04/session33712: PASS2IT/0falhas/erros/skips,14.76s/1min49.
  BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY, seis pipelines,25capturas incluindo
  hidratação real de um Frete,14arestas resolvidas, MAT04=200/MAT03=240 para
  duas raízes e4componentes por vertical. Source IDs continuam os do plano
  persistido; REPLAY usa o BOOTSTRAP1 conforme53492. DML todo revertido.

V095 adiciona ctl.analytic_manifest_composition_batch e procedure set-based,
até64declarações; conserva tabela e gates imutáveis existentes. Valida fonte
MAN capturada/chave, quantidade de DIRECT ativos, revisão, retry exato e rollback
ao savepoint. JdbcAnalyticManifestCompositions aplica uma TVP por lote.
physical-analytic-composition-batch-01/session67673 PASS1IT/0falhas/erros/skips,
6.201s/1min45:2selos, retry exato, divergência53649, quantidade53646, nenhum
registro parcial e transação preservada. O seletor incluiu nome inexistente
AnalyticLaboratoryFreightRelationsIT; somente a IT nova rodou. A regressão correta
se chama AnalyticLaboratoryFreightPathsIT e foi incluída na campanha seguinte.

Novos componentes main, compilados mas com integração ainda parcial:

- JdbcAnalyticFixtureBindings lê até6fontes correntes por keyset e prova execução
  unívoca por chave. Até54bindings por TVP (Frete9papéis). Fórmulas de registros
  são declarações fechadas da fixture; nenhuma identidade por nome/placa.
  bind(run,revision,correctedBranch,branchChanged,omitManifestFleet,token).
  Entidades: FRETE/LOC/MAN/COL/CAP/FAT/INV/SIN/COT. Correção muda BRANCH Frete/MAN
  paraB e usa previous=A apenas na transição; falta de frota MAN pode ser optada
  explicitamente no cenário degradado. Ainda não há chamador do cenário global.
- AnalyticUsersFixtures produz até256usuários, Relay20porpágina, IDs explícitos
  synthetic-analytic-{run}-{ordinal}, nomes homônimos NFC e NULL opcional no
  primeiro. O helper identifier(run,ordinal) é a declaração de vínculo, não
  lookup por nome. Ainda não integrado ao cenário global.
- AnalyticFixtureBindingsIT usa Frete3/CAP2/Usuários21, espera31bindings,
  retry e leitura positiva das seis dimensões pelo JdbcAnalyticQueries. Os21IDs
  distintos têm um nome normalizado. ContratoSQL19 usa coluna Nome (não NomeUsuario).

physical-analytic-fixture-bindings-01/session37600 está EM_EXECUCAO, com
AnalyticFixtureBindingsIT e AnalyticLaboratoryFreightPathsIT. Único processo ativo.
Reconciliar exit/relatórios ou sessão antes de repetir. Nenhuma outra execução
pendente, DDL ou efeito desconhecido.

metadata-after-raster-provenance-01 exportou19views/971colunas totais. SQL13 tem
dois IDs técnicos adicionais;673colunas de negócio permanecem no catálogo fechado.
MATRIZ-A-N foi atualizada com provas Raster/concorrência/composição e lacunas reais.
ANA-34 registra vigência financeira, observações de preparação e lote de selos.

Próximas ações:

1. Reconciliar fixture-bindings01; corrigir/provar. Qualificar unitários novos
   Raster no Directed ou verify (as campanhasPhysical não executam unitários).
2. Completar J: cenário global ainda não existe. Faltam ligação MAN/COL/COT/
   Usuários/Raster às seis fontes, enriquecimentos90/12/sérieNFSe, lifecycleMAN,
   MC/CF e DIRECT/CROSSWALK com selos em lote, três cargas/19queries, delta e
   correção de competência/filial, publicação/fronteira local, degradações e JAR.
   Main/AnalyticScenarioRuntime/AnalyticLaboratoryMain ainda não foram alterados
   ou criados. O programa atual é apenas uma composição parcial de6pipelines.
3. Completar variantesC–I/K e L–N: claim/recomposição,3escalas+repetição/planos,
   verify/233IT+novas/JAR, scanners/sucessão exata/diff/report/estado/entrega.

Notas úteis ao integrador:

- core.analytic_lab_source_current: run_id/entity/source_key/execution_id/
  business_date/usable. Frete hidratado tem execução diferente das demais raízes.
  JdbcAnalyticFixtureBindings.sources é privado; pode ser extraído/aberto de modo
  limitado para consumidores de enriquecimento, evitando repetir leituras SQL.
- Os novos recursos main manifest/cap/fat/inv/sin/raster são dados de origem,
  sem resultado de consumidor pronto. Freight90 já está em
  analytic-laboratory/freight-attributes.synthetic.json e tem mapper existente.
- JdbcAnalyticCollectionSupplements.Binding(key,exe,rev,attrs,cancelUserKey,
  destroyUserKey), bind(run,Listaté16,token). Usuário opcional explícito; bruto7/8
  do suplemento não é assumido igual ao IDGraphQL.
- Composição MAN não exige igualdade entre execução do snapshot preparado e a
  do selo; exige captura/chave, relações atuais, revisão e quantidade. Cada
  snapshot de preparação mantém sua execução/linhagem; replay não inventa raiz.
- V095 evita selo por registro. O relacionamentoDIRECT/CROSSWALK já admite TVP64,
  lifecycleMAN TVP64, dimensões TVP64. Não introduzir DML/JDBC por entidade em massa.

Continuar a execução integral. Checkpoint não constitui entrega final.
