# 0114 — Snapshot de Usuários e fronteira de Cotações

EM_EXECUCAO A–N; construção32/45/aceites67/115. V092 última instalada.
Predecessor0113 SHA25631ee11683dd61e89e6970051213f9d33e912f31a5a3d3fad00780d7be466913d.

physical-analytic-observation-modes01/session79748 reconciliado:9IT/0falhas/
2erros/0skips,1min48. Usuários2+Cotações5 anteriores passaram. Testesnovos:
UsuáriosfalhouRUNTIME_AUDIT_BINDING_INVALID;COTINCREMENTALfalhou51438semfronteira.

Decisão após conferircontratos/gates: Usuários é snapshotdeobservação,sem
filtro temporal de origem. RuntimeExecutionSession e V024mantêm BACKFILL/REPLAY
eFULL. Nãoalterar esses gatespara chamarumaobservaçãodeincremental.
Ocenário globalBOOTSTRAP/INCREMENTAL observará Usuários emBACKFILLexplícito;
REPLAYusaREPLAY. Modeindividualéregistradodeformaverdadeira,semavançar
watermarkdeUsuários. Issoatendecomposição dosciclos compipelinesexistentes.

Removidaaampliaçãotentativa deRuntimeUsersRequest (readAnalyticLaboratory/
privateflag);readcomumvoltouàimplementaçãoanterior. LocalAnalyticUsersRuntime
conserva apenasanovaatomicidadepublicação+attach emsavepoint e acrescenta
observationMode(cycleMode):BOOTSTRAP/INCREMENTAL/BACKFILL→BACKFILL,REPLAY→REPLAY,
outrosrecusados. captureaceitasóBACKFILL/REPLAY. QualityJava/SQLimpõemesmarestrição
paraUSUARIOS;COTACOESadmite4modoscomengineDQual e gatesoriginaisintactos.

TesteCotações registra fronteira porJdbcSqlServerControlPlane.
registerIncrementalFrontier(ExecutionPartitionKey(LOCAL_SHADOW/LOCAL_V2/
LOCAL_V2/cotacoes/INCREMENTAL/DATE..DATE+1),DATEstartUTC,Instant.now()).
Nãousainsertdiretodewatermarkenemremovepré-condição51438. Teste deUsuários
agorausaobservationModeexplicitamenteparaos4ciclos;materialcontrolecontinua
nomeandoBACKFILL/REPLAY. Expectativas4publicações/2usuários;COT4pub/1snapshot.

physical-analytic-observation-modes02/session6850 EM_EXECUCAO:mesmas3classes/
9ITesperadas. Reconciliarantesderepetir. Nenhumoutroprocessoativo.

Próximas ações:

1. Reconciliarmodes02/session6850,corrigir/provar; acrescentar assertfronteira
   COTavançasomenteINCREMENTALcontíguo e registrarsemântica ciclo/snapshot noADR.
2. ComposiçãoJ/JAR11inputs/5fatos/19SQL efixturesmain,delta/correção/hidratação/
   replay/oráculo/statusdegradado. Índicestécnicos0112/0113continuamúteis.
3. CompletarC–I/Kdegradado/L–N:adversariais/concorrência/escalas/planos/verify/
   233IT/JAR/scanners/sucessão/diff/report/estadoatéentregaintegral.

Nenhum código deAnalyticScenarioRuntime/CLI foi criadoainda. PróximaV093livre.
PendênciasRasterterminalconstant/PUB08/full51 físicas continuamabertas.
