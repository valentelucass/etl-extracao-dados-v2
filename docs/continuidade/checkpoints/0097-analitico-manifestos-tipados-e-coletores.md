# 0097 — Manifestos tipados e Coletores em qualificação

EM_EXECUCAO A–N; prosseguir até a entrega integral adotada pelo usuário, sem
perguntas nem pedidos de continue. Não é encerramento. Construção32/45 e
aceites67/115 permanecem. Request congelado no diretório privado do macrobloco.
Predecessor:0096-analitico-mat01-e-consultas-fretes-localizacao.md,
SHA256 c477c37ebf1500527e2b95c809faab4b99d50ebb48dcb778bf9772d2a49397ee.

Autorização vigente: mensagem do usuário adotando integralmente
target/preparacao-macrobloco-analitico-20260912-01/PROMPT-MACROBLOCO-ANALITICO-RASTER-CONSUMO.md.
DDL somente additive/versionado no localhost/ETL_SISTEMA_V2_SHADOW, Windows;
DML sintético em sessão rollback-only. Sem produção, V1 executado, dashboard,
API de negócio, grants, commit/push ou limpeza. Sem subagentes. Leituras Java/SQL
dirigidas de Manifestos/Inventário/Coletores do V1 sustentam as novas regras.

V001–V067 instaladas e imutáveis, baseline acompanha. Próxima livre V068.
Scripts Invoke-AnalyticLaboratorySchema/Build reservam efeitos e preservam
tentativas. Todos os antes/depois das campanhas encerradas conferiram agregados.
Não executar novamente os geradores privados de migrations já instaladas: versões
qualificadas podem conter correções posteriores ao gerador preservado.

Alterações/provas desde0096:

- V063: CURRENT_BRANCH/UNLOADING_BRANCH. SQL07 usa binding explícito quando
  falta status_branch_nickname. Physical-manifest-profile-02 passou4IT: consultas
  Fretes/Localização3 e captura Manifestos1. Session82367 reconciliada exit0.
- Perfil fechado analytic-manifest-capture-v1 declara92 campos da fonte6399;
  catalog/resource registram sourceSHA do DTO legado. RelationalSyntheticSource
  usa fingerprint ampliado registrado no início do run; contrato original intacto.
  LocalRelationalRuntime consome a release da instância, mantendo pipeline/gates.
- V064: colunas tipadas92, auditoria presença/wire/raw/issue por observação,
  snapshots da raiz e ponteiro atual. A preparação lê stg.manifesto_observation
  escrita de fato pelo pipeline; não stg.manifesto_reduced_candidate (não produzida
  nesse runtime). Decimal28,8 exato, textos limitados, arrays32x256, timestamps
  com raw nanos/offset e projeção DATETIMEOFFSET(7). Reducer por campo/cohort.
- V065: estado lateral MAN ativo/inativo/revisão/reativação por TVP64, ligado
  ao snapshot capturado; ausência é recusa dependente. Fonte6399 não recebe flag
  de exclusão fabricada. JDBC/domain adicionados.
- V066: corrige collations das tabelas temporárias da preparação. Qualificação
  DDL não compilava todas as ramificações temporárias; IT revelou o conflito.
- physical-manifest-preparation-01:4 erros SQL de collation, preservados.
  physical-manifest-preparation-02:4IT,3 passaram/1 falhou na preservação do
  primeiro capture após recusa de associação inexistente. Passaram92campos,
  precisão/nanos brutos, replay, conflito entre capturas do mesmo instante,
  correção posterior, métricas NULL complementares e filhos distintos entre páginas.
  O caso de decimal/array inválidos também passou até a asserção final de rollback.
- Causa da última falha: THROW com XACT_ABORT ON tornava a transação toda
  irrecuperável ao tentar preparar run sem associação. LocalAnalyticManifestRuntime
  agora confere a associação por SELECT limitado antes de capturar; a recusa Java
  mantém o código53601 e preserva as capturas anteriores. A correção está na
  campanha ativa. Outros caminhos de falha SQL/savepoint exigem revisão L.
- V067: MAT02 sobre contribuições canônicas e fatos diários; emitidos, descarga
  por binding, Inventário deduplicado/cohort, incompletos, exclusões via release,
  fallback por um único Frete vinculado, percentuais e recomposição antiga/nova.
  JdbcAnalyticMaterializations.collectors usa o mesmo controle limitado de receipt.
  Qualify-collectors-01/02 e install-collectors-01 passaram. Ainda sem PASS físico.
- CollectorsIT2 cenários escritos:2MAN físicos/1raiz +6INV físicos/2raízes,
  duas filiais/100%, idempotência; correção de dia/filial, total2/percentual0,
  partições antigas inativas, exclusão/reativação. Teste só usa pipelines,
  referências e bindings, sem inserir fatos finais.
- physical-collectors-01 falhou testCompile: import de FiscalPolicy errado;
  corrigido para FaturaClienteRules.FiscalPolicy. Session84114 reconciliada exit1.
- ADR0050 ANA14–16, MATRIZ-A-N e MAT02-REGRAS registram mecanismo/pendências.

Processo ativo ao checkpoint: physical-collectors-02, sessão exec19009. Phase
Physical, selector AnalyticLaboratoryCollectorsIT,AnalyticLaboratoryManifestPreparationIT.
Reconciliar exit/log/failsafe/before/after no diretório
target/macrobloco-analitico-20260912-01 antes de repetir ou instalar outro DDL.
Build isolado e heap512MiB, limite900s. Não há outro processo pendente conhecido.

Pendência técnica descoberta na revisão: V064/V066 escolhem cohort MAN pelo
freshness_at_utc(3) herdado. Raw9nanos está preservado, mas alterações abaixo de
1ms precisam de contraprova e preparação por segundo+nano exatos, sem copiar
a perda de precisão para MAT05. Resolver por sucessor e prova, não editar V064/066.

Próximas ações:

1. Reconciliar19009; corrigir erros físicos restantes, provar MAT02 e state gates,
   tipos/exclusões/fallback, full/vazio/incompletude/replay/partições e precisão MAN.
2. Implementar MAT05 e SQL restantes (01/03–06/08–12), bindings de relações/frota,
   leitor JDBC limitado e cenário composto JAR11verticais/5fatos/19consultas.
3. Concluir Raster/C/PUB contraprovas, SweepK, concorrência/escalas/verify/233IT
   anteriores/JAR, matriz673colunas, diff real before e sucessão finalN.

Manter demais pendências0096, especialmente lineage terminal Raster/extração
mais recente, casos positivos XMLNFS/Inventário em SQL02, perfis de contrato,
regras avançadas de dimensões. Cadeias hash antigas e snapshots preservados;
validadores de sucessão ainda precisam dos novos elos exatos no fechamentoN.
