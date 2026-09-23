# 0099 — Relações explícitas de Fretes/Manifestos

EM_EXECUCAO A–N; continuar até entrega integral adotada. Sem perguntas/continue.
Construção32/45 e aceites67/115 intactos. Mesmos request, before/snapshots,
autorizações e limites locais dos checkpoints0098/0097. Nenhum aceite agregado.
Predecessor0098-analitico-coletores-e-relogio-manifestos.md,
SHA256 6161af7224af8453aefe6a76b0e01ac15044c7aa173b3d7084071598ff2090e5.

V001–V070 instaladas/imutáveis; baseline acompanha; próxima livre V071.
Qualify-manifest-freight-paths-01/02 e install-manifest-freight-paths-01 passaram.
Physical-manifest-freight-paths-01/session85967 reconciliada:3IT passou sem
falhas/erros/skips, antes/depois agregados iguais. Nenhum processo ativo conhecido.
Logs/exit/failsafe no target/macrobloco-analitico-20260912-01/.

V070 adiciona:

- stg.analytic_lab_freight_relation: CROSSWALK relFrete→expFrete e DIRECT
  MAN→expFrete, com origem/destino e suas capturas. FK/trigger validam scope,
  fonte/captura completa, ausência de quarentena e raiz Frete válida.
- Crosswalk um-para-um; revisão/rekey explícito. DIRECT é por aresta canônica.
  core.analytic_lab_freight_relation_current seleciona a revisão declarada.
- ctl.analytic_manifest_composition: conjunto direto sintético completo,
  cardinalidade conferida, bloqueio de alteração na mesma revisão selada.
  Consumidor MAT05 ainda precisa recusar selo ultrapassado por bindings novos.
- AnalyticFreightRelationBinding e JdbcAnalyticFreightRelations: TVP64, bindBatch
  e sealComposition. AnalyticLaboratoryFreightPathsIT3 prova chaves rel300101
  versus exp300001 (valores exclusivamente sintéticos), idempotência, rekey com
  alvo anterior, cardinalidade recusada e nova aresta em selo antigo recusada.
- ADR0050 ANA18 registra o contrato. Essas relações ainda não produzem MAT05.

Referências de Manifestos em preparação, ainda sem consumidor/importador de frota:

- src/main/resources/analytic-laboratory/fleet-references.synthetic.json:
  família futura OWNED_FLEET reutilizará tabelas V008 existentes.25linhas:
  1documento,14aliases,8combinações,2exceções. Token sintético SHA256 UTF16LE,
  normalização ascii-trim-upper-v1. Não é identificador canônico nem segredo.
- manifest-references.synthetic.json: cópia de base75 mais28labels, total103
  (81labels/13registry/8exclusions). Adiciona rótulos MAN e documento do
  proprietário do trator em registry com sourcePath VEICULO_OWNER. A proveniência
  é TRACTOR_BINDING_REGISTRY_DOCUMENT, separada do payload6399.
- manifesto-referencias.json registra os dois perfis/sourceSHA e estado em curso.
  O gerador privado build-manifest-reference-fixtures.cjs tem guard de existência;
  não repetir. Após geração os rótulos MAN_STATUS/MAN_MDFE_STATUS foram corrigidos
  contra as linhas470–485 do loader legado: status closed→encerrado,
  in_transit→em trânsito, pending→pendente; MDF-e pending/closed/issued/rejected
  têm traduções respectivas. Demais brutos deverão ser preservados sem invenção.
- JdbcAnalyticReferences já possui importFixture limitado32768bytes/100linhas
  por coleção; acrescentar importManifestPackaged para o novo perfil, preservando
  importPackaged básico e as provas anteriores. Não foi alterado ainda.
- Para OWNED_FLEET reutilizar ref.frota_propria_documento,
  ref.classificacao_frota_alias, ref.classificacao_frota_matriz,
  ref.classificacao_frota_excecao_token e ref.usp_register_reference_release/
  reference_import_receipt existentes. O receipt já confere essas quatro tabelas.
  Ainda faltam importador TVP, seleção por run/revisão/vigência e consumidor MAT05.

Regras investigadas/limites:

- MAN01–07 constam agora em STATES linhas2268–2274 (usar rg porID). MAN04 não
  permite MAX de competência: saída válida, fallback criação somente ausente/nula.
  MAN07 métricas únicas/complementares no cohort; zero+nãozero é conflito.
- SQL08 e09 legados compartilham o resultado preparado do fato; escrever MAT05
  antes de ambos. 105/106colunas, a diferença é Local de Descarregamento.
- Proprietário no legacy usa aliases em metadata ausentes do DTO92 e regras
  por CNPJ/nome hardcoded; o cenário usará registro/binding explícito e a
  referência OWNED_FLEET, preservando o gate externo de identidade.
- Receita: ainda decidir/aplicar reconciliação do manifest_freights_total
  capturado com o conjunto direto selado e dedupe do mesmo Frete vindo por MC/CF.
  Não somar produto pick×MDF-e nem somar duas vezes fonte direta e via Coleta.
- core.relational_lab_link existente traz MC/CF ativos com binding_id/revision,
  origin/target/component. core.ufn_expansion_freight_terms(@exp) fornece termos
  canônicos/estado READY/CONFLICT, ativo, moeda/unidade. Fonte de receita base é
  core.expansion_lab_dependency→stg.frete_record, com pontes V070 explícitas.

Próximas ações:

1. Implementar/importar/provar OWNED_FLEET com tabelas existentes e seleção local;
   MAT05 sobre raiz tipada, DIRECT+MC/CF/crosswalk, frota/capacidade e partições.
2. SQL08/09 e demais contratos pendentes, leitor JDBC limitado; completar casos
   F/C/D/E/PUB, cenário JAR11verticais/5fatos/19consultas e SweepK.
3. L–M (concorrência, escalas/plano, verify/233IT/JAR), matrizes673colunas e N
   (diff before, revisão, estados funcionais e sucessão hash exata).

Pendências0098 permanecem: equivalência TIME por UTC com offsets distintos,
semântica de no-op versus lineage e preservação por savepoint em falhas SQL.
Nada foi declarado corrigido sem prova. Não executar geradores de migrations
instaladas; preserve todas as tentativas e snapshots. Continuar após compactações.
