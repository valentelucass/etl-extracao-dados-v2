# 0120 — Escalas verificadas e janelas de publicação reforçadas

EM_EXECUCAO A–N até entrega integral; construção32/45, aceite histórico67/115.
Predecessor0119 SHA256 6ea6a8892374e1578c48b08fa3aeb0241b9e5e4645212d7acb1142a413e0c69d.
Prompt integral adotado e inventário2704 em target/macrobloco-analitico-20260912-01.
Autorização: somente localhost/ETL_SISTEMA_V2_SHADOW, migrations aditivas após
master, DML sintético rollback-only; sem produção/remoto/V1executado/subagentes.

Arquitetura03 falhou compilação pelo acesso aos dois hashes canônicos SweepScope
após mover fixture para plataforma/analitico. Métodos puros tornados públicos,
sem modificar validações/kernel/6tipos. directed-analytic-architecture04 exit0,
27unit/0falhas/erros/skips. Catálogos/páginas/bounds e imports compilados.

LocalRasterRuntime separa captura/processamento de uma página da recursão, evitando
reter resposta pai ao buscar filhos. Observer opcional e bytes reais/contadorbatch.
AnalyticScenarioObserver observa nove capturas DE e Raster, com ManagedPageGauge.
physical-analytic-scale01/session54331 reconciliada exit0,23IT/0falhas/erros/skips,
4m40 total. Scale4/16/64/16 levou17.09/29.52/63.65/39.47s;149.8s na classe,
3cargas/cincofatos/consultas pesadas,1página e1lote em voo/liberação/rollback.
284planos reais dedupe/join/dimensão/MAT01/02/05. actual-plan-review-01.json:
zero spills/zero MissingIndex,4planos com conversões explícitas OPENJSON key/value;
avisos de grants em temporários pequenos/estatísticas da massa transitória.
Sem alteração de grants/índices por hipótese; sem platô ou SLO declarado.
Monitoring3/Raster16IT passaram na mesma campanha.

V097 instalada após monitoring-complete-qualify/install01: SQL10 acrescenta
Raster/cinco cargas/ciclo. Tempo ausente não é inventado; somente momento real
disponível, Data coalesce início/fim. Nenhum payload/mensagem livre.
V098 instalada após source-window-qualify/install01: modo + janela efetiva das
entradas; daily sources iguais à intenção, Raster cobre a janela. Fronteira INC
já consumida recusa com SOURCE_UNPROVEN antes dos fatos. Runtime declara entrada
[START,START+1) para9DE/Usuários; recomposiçãoFULL5fatos continua3dias completos.
Migrations atéV098 imutáveis e baseline/listas estáticas sincronizados;V099livre.

physical-analytic-plan01/session42457 EM_EXECUCAO,900s,heap512MiB. Seleção:
AnalyticScenarioPlanIT3,RuntimeIT3,IsolationIT4,ScaleIT#rasterReleases...1.
Confirmação desconhecida: reconciliar antes de repetir/novo DDL.
PlanIT testa intenção/retry divergentes, modo/fonte/janela sem prova, fronteira,
dois SPIDs na procedurePLAN e cenário completo após rollback do primeiro dono.
Runtime permite UUID técnico explícito somente na API sintética para essa prova;
CLI continua sem opção de ID/fonte/path externos.

Test-AnalyticLaboratoryJar agora compara fontes main/resources/test do snapshot
com workspace e todas classes/resources empacotadas com target/classes, inventário
exato e SHA; ainda não executado sem JAR aprovado. Build aceita BudgetSeconds60..1800,
default900; não alterou orçamento da campanha ativa. Próxima regressão integral
pode reservar1800s devido unit+233ITanteriores+novas e escalas; não estender processo.

Próximas ações:
1. Reconciliar plan01, corrigir falhas e revisar contraprovas C–I por inventário.
2. VerifyPhysical integral Java17 (orçamento prévio1800s), JAR40processos e identidade;
   conferir novas escalas/plans da revisão final, scanner/schema/validadores.
3. Fechar matrizA–N, relatório/inventários/diff inicial/sucessão exata, STATES/trilha/
   RETOMADA preservando história e entregarN. Não declarar completo antes disso.
Sem bloqueio externo, nenhum aceite final ou processo JAR presumido.
