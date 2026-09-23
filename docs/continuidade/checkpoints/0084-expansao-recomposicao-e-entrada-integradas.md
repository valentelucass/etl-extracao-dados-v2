# Checkpoint0084 — recomposição e entrada integradas

EM_EXECUCAO A–N. Predecessor0083 SHA256 d6936f654f32d5408c6554a245156144c8af3d2cc5486c7ec40710dab862c56c.
Pedido/inventário2549/universo45:target/macrobloco-expansao-20260912-01/. Sem ampliação de autorização.
V049 instalada/imutável:planos por modo/revisão/partição,seis slots de execução,
recibos I/J pré-alocados,falhas sanitizadas,attach de captura completa,
conclusão COMPLETE/DEGRADED e primeira lacuna preservada. Próxima migrationV050.
JdbcExpansionRecomposition/Executor reabrem adapters e leem SQL. Os runtimes
agora aceitam UUID reservado;se a captura terminou antes de attach,reutilizam-na.
Falha de captura reverte savepoint;falha do plano permanece na transação externa.
Não houve COMMIT/crash durável;prova chamada reidratação SQL,rollback-only.

Entrada ExpansionLaboratoryMain companheira ao Main padrão dormente,one-shot:
scenario/hydrate/replay/status/query com seis seleções. Cada invocação cria e
reverte cenário próprio;status expõe propositalmente a dependência pendente.
A captura atravessa4 novas+Fretes/LOC,auditoria/staging/bindings/fila/hidratação,
referências e duas cargas. JdbcExpansionStatus agrega equações sem massa Java.
Gerador lateral em TVP<=98;fixtures lazy aceitam recorte firstRoot/revisão.
Referências cobrem janela da execução menos/mais7 dias de forma explícita;
datas financeiras das fixtures não se confundem com partição de captura.

physical-recomposition-main-02:22 testes,0 falhas/erros/skips;MainIT10/22.46s,
RecompositionIT12/26.81s. BOOTSTRAP/INCREMENTAL/BACKFILL/REPLAY,ordem inversa,
lacuna,novarevisão,9 fronteiras de falha,SQL reaberto,recibos exatos,uma hidratação,
seis consultas e resultados manuais200/240. Agregados preservados por rollback.
Tentativas falhas preservadas:qualificação V049collation2vezes;physical-recomposition-01
bindingKeymaiúsculo;02linhalonga;physical-recomposition-main-01catchtimeoutordem.
Os defeitos correspondentes corrigidos;última bateria passou. Nenhum processo ativo.
MainTest19 casos de flags está escrito mas NÃO executado em Directed/verify ainda.
JAR em processo NÃO testado;fullverify desta revisão NÃO executado.

Próximas ações:
1. M:matriz adversarial das4,reativação/frescor/presença/limites/recursos;revisar
redução de valores quando há componentes inativos e corrigir se contraprova falhar.
2. Concorrência efetiva em duas sessões,escala3+repetição,planos reais/JDBC,
JARprocessos/recusas/degradados,regressões/verify/scanners/validadores.
3. N:contratos151campos+6queries,matrizA–N,relatório/diffs/manifesto/inventários,
quadrofuncional45 antes/depois,STATES funcional/trilha/RETOMADA/checkpointfinal.
Sem A–N concluídas ou aceite real. SQLsomente localhost/shadow exato/Windows;
DMLrollback-only,DDLforaIT;sem API/.env/grants/reset/V1/dashboard/produção/
serviço/commit/push. Prossiga autonomamente após compactação.
