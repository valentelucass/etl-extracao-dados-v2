# Checkpoint0085 — provas adversariais, concorrência e escala

EM_EXECUCAO A–N. Predecessor0084 SHA256 b88c246655ff5d4b286f382c2f4567751d5cb160c61bd794357f36ed3f1df0db.
Pedido/inventário2549/universo45:target/macrobloco-expansao-20260912-01/. Sem mudança dos limites locais.
V050 instalada,imutável;próximaV051. Redução corrente exclui valores de
componentes inativos,preservando histórico e comprovante cumulativo.
physical-adversarial-01:3 falhas de redução+1 fixture FAT incorreta (não4falhas
de redução como ação inicial descreveu;correção factual em adversarial-fixture-correction.json).
physical-adversarial-02:15 passam,1 fixture FAT falha. Ajuste fixa vencimentos
abaixo do timestamp controlado,sem mudar regra de máximo. Depois16 adversariais
passaram em physical-adversarial-concurrency-01;4 concorrências falharam porque
segunda sessão não tinha transação iniciada. Preparação corrigida paraBEGIN
na própria sessão antes da procedure;nenhum proxy/trava isolada substitui disputa.

physical-scale-concurrency-01:8/0/0/0. Quatro procedures efetivas:claim/aplicação/
MAT04/MAT03;segundo dono recebe busy1200ms,primeirorollback,segundorecria inputs
na sessão própria econsome corretamente;semCOMMIT. Escalas16/64/256/64:
6.946/13.208/84.590/13.912s,preparaçõesJDBC78/207/725/207;heapamostral
27.07/164.23/168.65/167.91MiB. Uma página/um lote emvoo;retençãogerenciada0,
páginas<=64KiB/lotes<=16. Quatro pipelines+Fretes/LOC+hidratação mínima+duascargas;
oráculos100*títulos e120*Fretes porBRL/MAJOR emSQL. Agregados preservados.
148planosreais:37porcaso,3relacionais+17MAT04+17MAT03. Inspeção automática XML
semSpillToTempDb/HashSpillDetails/SortSpillDetails nemPlanAffectingConvert.
Evidências preservadas em physical-scale-concurrency-01-measurements e
actual-plan-inspection-initial.json. Sem alegação deplatô/SLO.

physical-edges-02:14/0/0/0,10.24sIT. DST2018-11-04:23hporcaptura/4verticais,
frescor exatoepoch/nano;arrays FAT/INV preservamordem/duplicatas/padding/caixa,
reordenaçãoempatadaquarentena/33itensquarentena;INVcomprovantestale cumulativo;
duasescolhasfiscais explícitas preservamduasnúmeros;5estadospagamentoMAT04.
physical-edges-01falhou checkstyle(emptydefault),corrigido e preservado.

Baseline revisado para inclusõesSQLCMDV001–V050,sem SQLduplicado. Validadores
SchemaFoundation/ProgressiveDataGate/ColetasBaseline -InstalledReceipts passaram
na revisão;logsdevemserconsolidadosnocontrolefinal. SchemaFoundationSqlContractTest
agora declara nomes exatosV038–V050. SQL062 de validação está escrito,nãoexecutado.
Scripts Test-ExpansionLaboratoryJar.ps1 (21casos) escrito,nãoexecutado.
ADR0049EXP19–23 cobreMAT03/recomposição/entrada/redução/medição.

Verifycompletoemexecução:verify-01,PhaseVerifyPhysical,sessiontool15246,
recibos verify-01-action.json/process.txt/exit.json e logs/verify-01.log.
Conferir resultado/processo próprio antes de repetir. Teto900s,heap512MiB,
Java17,offline,perfil eprop opt-in. Não fazerDDL nem outroDML enquantoativo.
Não há verifyverde nem JARprocesso comprovados ainda.

Próximas ações:
1. Conferirverify-01,investigar/corrigir falhas e repetir apenasafetado;
JARemprocesso após package,SQL062/aggregates e validadores/scannerscomlogs.
2. N:matriz151campos e6queries,matrixA–N,relatório/verification-summary,
manifesto/snapshots/diffs/hashes/sucessãoexata econtraprovasvalidadores.
3. Quadrofuncional45 antes/depois ligado aoSTATES. Revisar marcações iniciais
degovernança/documentação V2-016/017/048 sem alteraruniverso nem snapshots;
registrar correção transparente se não hácapacidadeexecutável. Atualizar
linhasfuncionaisV2-029–32 eMAT03/04,sem checkbox deaceitereal;sincronizar
STATES→trilha/RETOMADA/checkpointfinal eentregar tudo.

SemconclusãoA–N/aceitereal;somente localhost/shadowexato/Windows,DMLrollback-only,
DDLforaIT. Sem API/.env/grants/reset/V1/dashboard/produção/serviço/commit/push.
Prossiga apóscompactação;nenhum pedido decontinue.
