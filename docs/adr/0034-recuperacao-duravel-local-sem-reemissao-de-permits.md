# ADR 0034 — Recuperação durável local sem reemissão de permits

Status: aceito para implementação e prova sintética local de P02R; SQL preparado.

## Contexto

ADR 0033 integra Coletas/Fretes, mas `recoverPromotion()` conserva permits da sessão.
O control plane persiste fingerprints e auditoria de páginas, sem comprovar que o
guard terminou com metadata, resposta populada, terminalidade e auditoria aprovadas.
PROMOTED não supre essa evidência. Start é atomicamente EXTRACTING; recuperação
stale converte leases expiradas em terminais e não pode preceder o readback.

## Decisão

`DataExportRuntimeWorkload` fecha o guard e `RuntimeExecutionSession` confere a
auditoria antes de chamar `sealTraversal`. A porta exige o `ContractPromotionPermit`
real, cujo construtor continua encapsulado. O selo V015 é append-only, vinculado à
identidade integral e à configuração/contrato, ao resumo e digest das páginas e à
policy esperada. Nenhum booleano público representa aprovação. SQL compara a
identidade calculada dos registros persistidos com a expectativa Java usando wire
v1 com comprimentos UTF-16; digests detectam contradições, não substituem os gates.
Um administrador capaz de desabilitar triggers e reescrever toda a cadeia permanece
fora do modelo de integridade, como nos ADRs anteriores.

Optamos por **não reemitir permits**. `JdbcSqlServerRuntimeRecovery` chama o comando
V015 que valida selo, estado corrente, lease, revisão e DQ sob o mesmo application
lock e row fences do protocolo. Ele usa as transições e os entrypoints reais de
prepare, DQ e apply/reconcile/publish de Coletas/Fretes. Os gates SQL de policy,
thresholds/SLA, candidate set, contagens, lease e relógio continuam obrigatórios.
Os dois permits e gateways Java existentes continuam no caminho normal; nenhum
construtor/factory de aprovação foi aberto para recuperação.

READ é transacional e limitado a uma linha. A revisão inclui estado/sequência,
lease, selo, candidato e avaliação; RESUME refaz toda a leitura e a elegibilidade.
Não há renovação ou aquisição de lease pela recuperação. Ack incerto de comando
gera READ posterior, sem repetição automática do comando. Recibo confirmado usa
eventos, reconciliação, aplicações e DQ históricos, independentemente do pointer
atual e de revogação posterior de policy. Recibo contraditório não é sucesso.

O dispatcher mantém ordenação/namespace/janela e gera novo agregado. Recusas de
recuperação não gravam FAILED/CANCELLED. Terminais persistidos são preservados;
parciais exigem outra ocorrência/chave. Replay mantém origem explícita. A ausência
de um registro não autoriza automaticamente start. Não há checkpoint de extração.

JDBC exige login timeout já configurado entre 1 e 30 segundos, query timeout e
network timeout explícitos entre 1 e 30 segundos. Cada chamada possui dois workers
daemon para network deadline e `Statement.cancel`, fecha statement/conexão e
encerra o executor em `finally`. O token é observado a cada 50 ms durante SQL;
connect depende do login timeout do DataSource, não do query timeout. Cancelamento
não interrompe a consulta final limitada que pode confirmar um commit anterior.
A resposta de uma query não transforma incerteza de commit em terminal conhecido.

## Consequências e limites

Coletas aplica COL-03 depois do kernel e suas disposições podem divergir do recibo
genérico. V015 evolui aditivamente seu entrypoint para persistir o recibo **tipado**
e seu digest na mesma transação dos efeitos, antes do ack. Retry confirmado devolve
esse registro sem recomputar COL-03 a partir do current; o reader não substitui suas
contagens pelas genéricas. Um teste remove apenas os três trechos P02R e compara o
corpo restante com V010, preservando as regras de negócio. V010 permanece intacta.
V015 não amplia os grants do entrypoint existente. Publicações de Coletas anteriores
a V015 sem esse recibo não podem ter suas contagens exatas reconstruídas: retornam
EVIDENCE_MISSING. Fretes já devolve o recibo comum. Ausência de selo de contrato não
impede reconhecer um recibo histórico íntegro disponível, mas impede continuação.

O construtor local do dispatcher com porta durável habilita selagem; o construtor
anterior conserva compatibilidade e não promete retomada durável de extração.
Ocorrências anteriores sem selo são recusadas para continuação. Não há migração
retroativa fabricando evidência. V015 não concede grants e não altera a composição
oficial deny-all. Aplicação/validação SQL física e autorização operacional seguem
pendentes, separadas da prova sintética de novos objetos/processos Java.

Rollback local: retirar a composição da porta, restaurar apenas as mudanças deste
bloco a partir do diff revisado; V001–V014 permanecem intactas. Não editar/apagar
V015 se aplicada no futuro: usar migration compensatória aprovada, preservando os
selos e recibos. Os exercícios SQL preparados são rollback-only e não foram
executados no Bloco 52. Testes e comandos estão no runbook de P02R.
