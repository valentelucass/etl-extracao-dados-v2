# 0118 — Cenário integrado e publicação local em prova

EM_EXECUCAO A–N; construção32/45 e aceite histórico67/115 separados.
Objetivo e autorização: adoção integral pelo usuário de
target/preparacao-macrobloco-analitico-20260912-01/PROMPT-MACROBLOCO-ANALITICO-RASTER-CONSUMO.md.
Predecessor0117 SHA256
ba968d1810fc42d4e522cbfce845720a9a8ac97d6f4758dc8e99c215a0c69a9b.
Inventário antes2704 e evidências: target/macrobloco-analitico-20260912-01.
Sem perguntas, efeitos produtivos, remotos ou subagentes. DDL somente migrations
localhost/ETL_SISTEMA_V2_SHADOW após master; DML sintético com rollback integral.

fixture-bindings01/session37600 reconciliada: exit0,4IT/0falhas/erros/skips;
AnalyticFixtureBindingsIT1 e regressão AnalyticLaboratoryFreightPathsIT3.

AnalyticScenarioEnrichment e AnalyticScenarioRuntime main compõem11entradas,
relações/suplementos/lifecycle/selos,5cargas e referências. Páginas1..16,
raízes2..480, seletorSQL6, TVPs limitados, Usuários até256 e Raster até499/folha.
Sem listas do universo. IDs laterais declaram exclusivamente a fixture fechada.
SourceKey de Usuários confirmado pelo mapper: STRING:identificador.

scenario01/session51783:1IT/1falha, cinco fatos prontos mas SQL06 vazio.
A revisão2 exigida pelo consumidor CAP não havia sido importada na expansão.
Corrigida a seleção explícita; nenhuma mudança da expressão SQL para contornar.
scenario02/session30298: exit0,1IT/0falhas/erros/skips,18.87s, cinco fatos,
hidratação1,18queries positivas e SQL04 vazio; oráculos receita240/faturas200,
dois Fretes canônicos e quatro caminhos DIRECT/coleção; rollback conferido.

V096 instalada: ctl.analytic_scenario_cycle/step/query_receipt/frontier,
TVP11 fontes, begin/complete e view de recibos. Intenção/recibos protegidos;
SQL confere proveniência, modo real das11capturas e5recibos, calcula19contagens,
grava COMPLETE/DEGRADED e só avança fronteira INCREMENTAL contígua terminal.
Não modifica os estados operacionais das capturas relacionais/expansão.
schema scenario-plan-qualify01 falhou468/collation;qualify02 passou rollback;
reforço de modo/imutabilidade qualificado em03;install01 confirmado.
Baseline incluiV096; migration instalada imutável. V097 livre.
build-scenario-plan.cjs privado é gerador inicial, NÃO rerodar nem tomar como
versão final: ajustes SQL posteriores estão na migration instalada.

Processo próprio: physical-analytic-scenario03/session29390,900s,heap512MiB,
3IT selecionadas em AnalyticScenarioRuntimeIT. Resultado ainda desconhecido:
conferir exitJSON/failsafe e antes/depois antes de repetir. Prova inclui
quatro modos, correção data/filial, replay sem fatos novos, status reidratado,
duas ausências e reaparecimento. Novos resultados não são presumidos.

Próximas ações:
1. Reconciliar scenario03, corrigir falhas locais e repetir provas afetadas.
2. Completar isolamento Raster/frota/referência financeira/snapshotCOL, comandos
   JAR, contraprovas de publicação/fronteira/claim/recomposição e variantes C–I.
3. Executar escalas/planos/verify/233IT anteriores/JAR; revisar diff inicial e
   concluir sucessão documental exata, relatório e entrega N, sem aceite parcial.

Nenhum bloqueio externo novo. Nenhuma entrega final A–N declarada.
