# 0119 — Cenário e isolamento verificados; regressão em correção

EM_EXECUCAO A–N. Construção32/45 e aceite histórico67/115 não avançados.
Predecessor0118 SHA256 48df1bc0d52a22f2a512c62899d9374f5273a56277cf7c8f510c7256d7b227a9.
Adoção integral do prompt analítico pelo usuário continua vigente até entrega N.
Inventário2704 e todas as tentativas preservados em target/macrobloco-analitico-20260912-01.
Alvo exclusivo localhost/ETL_SISTEMA_V2_SHADOW; DML sintético rollback-only;
DDL aditiva versionada e qualificação separada, sem produção/fonte remota/V1 executado.
V096 instalada imutável; V097 livre; nenhum efeito DDL desconhecido.

physical-analytic-scenario-03:3IT/1falha replayMAT01 por novas dimensões idênticas.
scenario04:3IT/1falha expectativaMAT02; isolation4/1erro nomecoluna no oráculo.
scenario05:3IT/1erro comparaçãoMAT02; isolation4IT passou,86.51s.
scenario06:exit0,3IT/0falhas/erros/skips,69.39s; onze entradas/cinco fatos,
quatro modos,delta/data/filial,replay,fronteira contígua/status SQL reidratado,
19queries/673colunas,d duas ausências independentes e reaparecimento.
A correção evita novos bindings/suplementos/selos no REPLAY. MAT02 preserva
observações técnicas; oráculo independente compara o estado de negócio histórico
por corte imutável de observação em cada recibo, incluindo partições não reemitidas.
Não foi reduzido o contrato de integridade nem alterada migration instalada.
IsolationPASS4 comprova Raster incompleto/frota sem binding/referência financeira
fora da vigência/snapshotCOL parcial: degradação global, CAP independente e rollback.

Comandos AnalyticLaboratoryMain/Options e Test-AnalyticLaboratoryJar.ps1 existem;
JAR empacotado ainda não qualificado. Main geral intacto. Options/RasterParser:
directed-analytic-options01 PASS20unit. verify-analytic01:1804unit/5falhas/0erros/
4skips históricos; falhas reais preservadas (arquitetura, listas schema, kernel).
Correção: catálogos finitos fora da persistência; páginas JDBC com TOP parametrizado
+limites6/64; cópias só depois das cardinalidades; SyntheticCollectionSnapshot
movida de reconciliacao/sweep para plataforma/analitico, mantendo kernel6tipos.
Listas estáticas migration/baseline agora V001–096, sem autodiscovery permissivo.
ArchitectureRulesTest ganhou11 assinaturas exatas com justificativa de limite,
sem remover scanner, regra de kernel ou contraprovas. directed-architecture01
falhou4checkstyle; corrigidos espaços de switch e3literais longos.

Processo próprio directed-analytic-architecture02/session52039,900s,heap512MiB,
5classes dirigidas. Conferir exitJSON e surefire antes de repetir.
M próxima ação registrada: medir4/16/64/16raízes, página16, orçamento240s por escala,
9capturasDE observadas + Raster +3cargas e consultas pesadas/planos reais.
Justificativa: cenário11entradas/5fatos mais amplo que escala anterior6entradas;
sem promessa de platô/SLO. Reutilizar ManagedPageGauge e MeasurementDiagnostics.
Revisão apontou escopo recursivo Raster mantendo resposta pai durante filhos;
isolar processamento da página antes da recursão e provar liberação/bytes reais.

Próximas ações:
1. Reconciliar architecture02; corrigir gates sem afrouxar e provar refatores físicos.
2. Medir escalas/planos, concluir contraprovas J–L/monitoramentoSQL10/JAR real.
3. Executar verify completo/233IT anteriores+novas, revisar C–I, fechar matriz,
   sucessão exata, STATES/trilha/RETOMADA e relatório/diff N até entrega integral.
Nenhum bloqueio externo nem aceite final declarado.
