# 0122 — Regressão reconciliada e limites em nova prova

EM_EXECUCAO A–N; construção32/45; aceites67/115. Nenhum gate real promovido.
Sucessor de0121; manter hashes dos checkpoints/manifests anteriores imutáveis.
Prompt990e481060eefd1c1ee01c168d46ba85c7d384edd5d61ce746ef9974d8d01a33.
Somente localhost/ETL_SISTEMA_V2_SHADOW, rollback sintético e nenhuma ação remota.

verify-physical-analytic01 reconciliado exit1:1804unit/0falhas/0erros/4skips
históricos e361IT/0falhas/1erro/0skips,21classes antigas233casos preservados,
39classes analíticas128casos. Único erro físico: socket Read timed out em
JdbcAnalyticScenario.complete na escala64. Todos os agregados antes/depois
idênticos. CivilTime5/Plan3/Isolation4/Runtime3 passaram. JaCoCo recusou branches
em quatro pacotes (analitico, fretes.domain, raster.aplicacao, raster.domain).

Somente leituras SQL de catálogo/stats feitas após preflight master,20s/query,
delivery-sql-diagnostics01. Exportados37views e31cached statements. Não há prova
do plano real do statement interrompido; tempos posteriores não justificam índice
por hipótese. ADR ANA40 replana escala final4/16/32/16 sem ampliar timeouts.
Teste separado Raster256 continua obrigatório. V098 permanece última migration.

FieldGateTest acrescenta142casos:90campos Frete,49Raster isolados, DTO inválido,
escopos e metadata. Directed-field-gates01 exit0/142PASS. Fontes formatadas de
volta somente por identidade. Nova reserva verify-physical-analytic02: Java17,
heap512MiB,1800s, verify integral+perfil físico sem filtro; reconciliar processo
e exit.json antes de repetir. JAR40 depende de aprovação e identidade dessa revisão.

Scanner: guardas analíticas02 PASS5, selftest02 PASS11, extensions01 PASS4,
offline-scan02 PASS2954arquivos/0findings. schema-static01 e progressive-static01
PASS. runtime-static01 falhou somente CheckGuidance/STATES ainda sem sucessor
analítico selado; demais cinco checks passaram. Corrigir por sucessão exata N,
sem modificar contrato histórico de runtime nem snapshots anteriores.

Matriz final gerada com lexer delimitador de SELECT e metadata SQL real:
matriz-colunas-final.json19/673negócio/971físicas; nomes/tipos/precisão/escala/
nulidade conferidos contra catálogo JDBC. Definições37views em snapshot público
separado, não instalador. Testes diretos/consumidor/origem/expressão/grão/regra
por contrato e linhagem por coluna; summary final ainda não existe.

Próximas ações:
1. Reconciliar verify02, corrigir somente falhas observadas e executar JAR40.
2. Consolidar prova/regressão233, planos/escalas finais, catálogo e relatório,
   estados funcionais/45unidades; não usar prefácio para substituir capacidade.
3. Qualificar sucessão exata17arquivos, snapshots/inventários/diffs e todos os
   validadores de continuidade/runtime; entregar N com nenhum falso concluído.
