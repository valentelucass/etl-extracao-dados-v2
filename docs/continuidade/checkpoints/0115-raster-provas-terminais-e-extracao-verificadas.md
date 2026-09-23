# 0115 — Raster: comprovantes terminais e última extração

EM_EXECUCAO A–N. Construção 32/45; aceite histórico 67/115.
Predecessor: 0114, SHA256
055fcb1cd652782f6974adc9d596ffb3354cc4215c166dbb76b3dfd8d87de442.
Request e diretório privado: target/macrobloco-analitico-20260912-01.

Reconciliadas duas campanhas:

- physical-analytic-observation-modes-02/session6850: 9 IT, nenhuma falha,
  erro ou skip, 1min53. Usuários continua BACKFILL/REPLAY; os ciclos globais
  BOOTSTRAP/INCREMENTAL usam observação BACKFILL explícita. Cotações admite os
  quatro modos com fronteira registrada pelo control plane. ANA-32 registra.
- physical-raster-proofs-01/session87684: 14 IT, nenhuma falha, erro ou skip,
  1min55. RasterIT4, TransitIT6, ProofsIT4. Antes/depois agregados preservados.
  Os relatórios físicos e exits estão no diretório privado, sem processo ativo.

V093 qualificada com rollback (raster-proofs-qualify-01), depois instalada
(raster-proofs-install-01). As duas reservas e resultados são confirmados.
V001–V093 são agora imutáveis. Próxima V094 livre, ainda não criada.
O baseline estava incompleto em V088; foram acrescentadas inclusões V089–V093.

V093 adiciona ledger imutável por folha Raster com receipt original, janela,
escopo e contagens. O selo confere contiguidade, ordinais, volumes físicos e
chamadas=2*folhas-1. Uma folha conserva o receipt recebido na captura; múltiplas
folhas indicam synthetic-window-ledger-v1 e mantêm cada receipt no ledger.
Aplicação exige ledger e conserva gates de origem, quarentena e binding.
No-op/stale válidos avançam extração/last_observation_id, preservando o snapshot
de conteúdo e o histórico de mudanças. SQL13 acrescenta dois IDs técnicos de
última observação; 37 colunas de negócio permanecem. Metadata total passou
51→53 e o teste dirigido foi atualizado por essa mudança legítima.

AnalyticRasterFixtures é uma fixture main empacotada de 51 declarações
(27 campos de viagem, dois contêineres, 20 de parada, dois de rota).
Source gera uma resposta limitada, três duplicatas por raiz e binding lateral
por chave explícita. Intervalo fechado 2036-04-01..04, raízes0..4096; cap500
provoca bisseção e, no dia mínimo, recusa. A prova170raízes produziu340entidades,
680duplicatas,3chamadas e2folhas; cap501raízes recusou e reverteu a captura.
Prova positiva conferiu49atributos tipados não nulos, dois contêineres, nano9,
offset e37colunas de negócio; vazio, receipt ausente/janela lacunosa, replay,
stale e linhagem de extração passaram. O parser agora valida a data inteira
antes de classificá-la como sentinela; suas novas asserções unitárias ainda
precisam do Directed/verify (Physical não executa unitários).

Os hashes dos manifests históricos orientação/expansão foram reconferidos:
a63cdfe48d17078ef5369d086a88cbf461df9e9624eaf1073b216b5626d58340;
c3318c87bf46bd63f43ec12e31b09b2ab4bee3e0a5a0b50fe319d68a22edc057.

Próximas ações:

1. Provar contenção em duas sessões físicas nas procedures efetivas Raster e
   MAT01/02/05, com consumo após rollback. Avaliar XACT_ABORT ON das cargas
   (V058 MAT01, V067 MAT02, V078 MAT05) e preservar a sessão após recusa.
   Nenhum teste de concorrência ou V094 foi criado ainda.
2. Completar J: fixtures main e composição 11 entradas/5fatos/19SQL, hidratação,
   correção/delta/replay, modos e fronteira local, JAR e degradação independente.
   AnalyticScenarioRuntime/AnalyticLaboratoryMain ainda não existem.
3. Completar variantes C–I/K, L–N: escala/planos, verify/233IT+novas/JAR,
   diff inicial, sucessão exata, matriz/relatório/estado e entrega integral.

Não há impedimento externo que encerre a construção local. Não finalizar cedo.
