# 0116 — Concorrência física e isolamento das cargas

EM_EXECUCAO A–N. Construção32/45; aceite histórico67/115.
Predecessor0115 SHA256
cde990c0a51d49705af5a94e42b7f0b94c434e3c69856e2b1b97d8e6d479bc1b.
Request e evidências: target/macrobloco-analitico-20260912-01.

V094 instalada e baseline sincronizado. V001–V094 são imutáveis; V095 livre.
analytic-savepoints-qualify-01 e analytic-savepoints-install-01 confirmadas;
reservas, preflight master/alvo e contagens antes/depois preservadas.

physical-analytic-concurrency-01/session45781: 4IT/4falhas/0erros/0skips.
Raster sem transação explícita no segundo dono recusou53501 antes da disputa.
A montagem compartilhada de fixtures recapturava Frete depois do selo de
Manifesto e bloqueava corretamente MAT05; MAT01 também ficou sem consumo
nessa montagem. A prova foi separada por carga, reutilizando seus casos positivos.
MAT02 atingiu contenção e expôs perda da transação inteira no segundo dono.

physical-analytic-concurrency-02/session15625: 4IT/3falhas/0erros/0skips.
Raster passou após savepoint explícito; MAT01/02/05 recusaram53502 corretamente,
mas XACT_STATE era0 em vez1. O THROW sob XACT_ABORT ON encerrava a transação.

V094/ANA-33 conserva as procedures efetivas V059/MAT01 (não V058), V067/MAT02
e V078/MAT05, acrescentando XACT_ABORT OFF, savepoint SQL e TRY/CATCH com rollback
ao savepoint. Preserva gates, equações, cálculos, recibos e índices. O rollback
Java continua presente. Erros irrecuperáveis não são promovidos a sucesso.

physical-analytic-concurrency-03/session1141: PASS17IT/0falhas/erros/skips,
2min37. Concorrência4 em duas conexões físicas/SPIDs distintos, contenção53502,
transação preservada e consumidor positivo após rollback do primeiro dono.
Cada segundo dono recompôs a mesma chave de run com dados revertidos do primeiro;
isso prova retomada local na transação, não durabilidade após COMMIT/crash.
Regressões: Coletores2, Fretes4, Manifestos5; RasterIdentity2 também passaram.
Nenhum processo dessa campanha permanece ativo.

RasterIdentity prova chaves laterais de parada independentes de posição/Ordem:
reordenação, OrdemNULL preservado, atributo alterado, captura parcial sem remover
filho, inativação, reativação recusada sem flag e recuperação explícita. Sentinela
malformada fica em quarentena física, bloqueia SQL13 e revisão válida recupera.

Frente J iniciada, ainda não integrada ao JAR:

- AnalyticScenarioFixtures + cinco recursos main manifest/cap/fat/inv/sin.
  Gerados de campos de origem, não de consumidores. Fontes lazy com3observações
  por raiz, page1..16, raízes0..1024, binding lateral/revisão explícitos.
  Manifesto declara92campos, pick100000+raiz e MDF-e44dígitos; opção correction
  muda competência/filial. CAP/FAT/INV/SIN usam preenchimento tipado já provado
  nos testes dirigidos anteriores; o cenário passa esses dados pelos pipelines.
- AnalyticExpansionCapture compõe seis pipelines atuais, plano persistido
  JdbcExpansionRecomposition, sete relações por raiz, hidratação via fila existente
  e MAT03/MAT04. Mantém3dias de escopo de run e partição explícita de1dia do plano
  anterior; o selo antigo exige a mesma janela de1dia nos dois recibos. O cenário
  analítico global ainda precisará da recomposição full explícita de3dias.
  BOOTSTRAP deixa um Frete ausente e hidrata pelo adapter existente; modos seguintes
  usam plano/execuções próprios. REPLAY vincula o BOOTSTRAP1 e os mesmos parâmetros
  de origem, sem relaxar53492. O resultado retém apenas6Step, nunca universo.
- AnalyticExpansionCaptureIT testa quatro modos, uma hidratação, valores manuais
  MAT04=200/MAT03=240 para2raízes e fontes4porvertical; segundaIT testa fixture
  main de Manifesto com2raízes/6linhagens e replay sem novos snapshots.

physical-analytic-expansion-composition-01/session59847 falhou antes das IT:
o teste chamava TestRuntime.capture com assinatura do runtime real. Corrigido
para desembrulhar .runtime(); não alterado código de produção para contornar.
physical-analytic-expansion-composition-02/session43526 está EM_EXECUCAO.
Reconciliar exit/relatórios ou sessão antes de repetir. Único processo ativo.

Próximas ações:

1. Reconciliar composition02; corrigir/provar. Unitários novos Raster sentinel
   ainda aguardam Directed/verify. Números recentes são de IT, não de unitários.
2. Completar J: ligar6entradas a MAN/COL/COT/Usuários/Raster, referências/bindings,
   suplementosFrete90/COL12/FATseries, relaçõesMC/CF+DIRECT/CROSSWALK, três novas
   cargas e19queries; delta/correção/replay/oráculos, fronteira local, degradação,
   cenário/replay/status/19seleções pelo JAR. Main continua sem alteração; não
   existe AnalyticScenarioRuntime/AnalyticLaboratoryMain ainda.
3. Completar C–I/K restantes e L–N: claim/recomposição concorrentes adicionais,
   escalas/planos, verify/233IT+novas/JAR, scanners/sucessão/diff/report/estado.

Usar até6fontes por lote de bindings (Frete9papéis→54linhas≤64), SQL keyset em
core.analytic_lab_source_current por run/entity/source_key. Fonte por chave deve
ser execução atual real (Frete hidratado tem execução distinta), sem inferir
identidade por nome/placa. Qualquer seletor técnico só após univocidade provada.
As fórmulas de relações são declarações exclusivas da fixture empacotada.
As ideias deste parágrafo são proposta de implementação, não código pronto.

Prosseguir até entrega integral, sem perguntas/continue nem encerramento parcial.
