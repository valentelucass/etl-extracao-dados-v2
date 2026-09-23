# 0106 — Cotações: tarifa, runtime e projeção em prova

EM_EXECUCAO A–N; continuar integralmente sem perguntas/continue. Predecessor
0105 SHA25640d4995dad0e2727aa1bafcda87a4946fa0d92fcc03e5fad8142eb59c73078b1.
Request/before2704/autorizações permanecem; construção32/45, aceites67/115.
DDL exclusivamente localhost/ETL_SISTEMA_V2_SHADOW com master preflight; fixtures
em uma sessão rollback-only, sem efeito produtivo, commit/push ou exclusão.

V083–086 instaladas, baseline acompanha; qualificação e instalação separadas,
ledgers/execution.log sob target/macrobloco-analitico-20260912-01. V084qualify01
reconciliada após saída perdida: exit0, installed=false, contagens preservadas;
install-quote-tariffs01 depois passou. V085qualify01 falhou por observed_at_utc
inexistente em stg.execution_record; corrigido para staged_at_utc antes de
qualify02/install01 passarem. V086qualify/install-quote-query01 passaram.
Migrations instaladas são imutáveis; próxima candidataV087, conferir antes.

compile-quote-staging01/session83774 reconciliada BUILD SUCCESS,1min37;
compile-quote-runtime01/session17033 passou exit0,1min24,554fontes/392testfontes.
physical-quotes01/session66022 em curso neste checkpoint: reconciliar processo,
logs/exit antes de nova tentativa. O teste inicial tinha erro sintático no nome
do terceiro método; já corrigido no canônico, junto do registrykey branch-a para
synthetic-branch-a e esperado customerName real da fixture. A cópia isolada da
tentativa preserva sua revisão. Ainda não afirmar SQL05 comprovado fisicamente.

Implementação nova:

- V083 tipa36campos por registro original de staging, presence/wire/raw/valor,
  epoch/nano, TVP16/256KiB e retry completo; JdbcAnalyticQuoteStaging valida
  antes de chamar staging original e grava o suplemento na mesma transação.
- V084 seleciona/importa QUOTE_TARIFF existente por run/revisão/vigência,
  referência selada/ratificada apenas para o laboratório sintético. Fixture25
  pares direcionais; BRL/PER_WEIGHT/KG/HALF_UP explícitos, sem tarifa default0.
  JdbcAnalyticQuoteTariffs usa recurso<=32KiB/TVP100 e savepoint/retry exato.
- V085 snapshots imutáveis36campos efetivos e pointer run/chave, observação
  por aplicação original, ABSENT preserva/NULL limpa. Preflight PROMOTED exige
  tarifa/escopo/atributos; contexto operacional de outra captura não vinculada
  é recusado; empate de relógio com atributo divergente também. Stale mantém
  snapshot e atualiza somente a observação/extração mais recente. Novo ramoCOT
  em core.analytic_lab_source_current habilita binding explícito dimensional.
- LocalAnalyticQuotesRuntime usa RuntimeWorkloadRegistry/Dispatcher, contrato
  completo36+marker, gateway/metadata/response reais, LocalFiveVerticalRuntime,
  staging original, DQ original e promoção original com preflight adicional.
  Modos BACKFILL/REPLAY conservam os gates antigos; J ainda deve caracterizar
  todos os modos do cenário sem relaxar silenciosamente o contrato original.
- V086 SQL05 tem54colunas +lineage/disposição, snapshot tipado, filial vinculada,
  tarifa vigente, status/emissão e metadata somente de raw/presença/wire. Ainda
  depende das provas físicas; compilação/DDL não são prova de consumo.

Próximas ações:

1. Reconciliar physical-quotes01, corrigir falhas e executar nova revisão dos
   três IT; ampliar status/emissão, referência/replay adversarial conforme casos.
2. Completar SQL03/04 e Sweep opt-in pelo kernel existente, depois leitor JDBC
   tipado/limitado e cenário JAR11entradas/5fatos/19consultas com hidratação/delta.
3. Concluir pendências C–I e L–N: concorrência física, três escalas+repetição,
   verify/233IT anteriores/JAR, diff contra before e sucessão exata/relatório.

SQL01/02/06–19 mantêm provas anteriores16contratos. Todas as frentes restantes
fazem parte do pedido; este checkpoint não encerra nem reduz o escopo.
