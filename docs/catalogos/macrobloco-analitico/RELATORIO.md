# Macrobloco analítico local — relatório da entrega

**CONSTRUÇÃO_LOCAL_CONCLUÍDA — A–N.** A entrega captura onze entradas sintéticas,
resolve referências/relacionamentos, hidrata dependência inicialmente ausente,
produz cinco fatos e consulta dezenove contratos. Acrescenta Raster, MAT01/02/05,
as dimensões/ref necessárias, ausência sintética de Coletas e comandos one-shot.
Tudo que escreve dados sintéticos executa em localhost/ETL_SISTEMA_V2_SHADOW e
reverte a transação; produção, fontes remotas e execução do V1 permaneceram fora
da rodada.

## Comportamento entregue

Raster mantém51declarações do inventário, presença/tipo/bruto, tempos e offsets,
pai/paradas atômicos e disposição auditável para conflito, cap, incompletude ou
binding ausente. Transit Time aceita diretamente0..43200minutos e aplica fallback
temporal com proveniência; cap na menor janela nunca vira sucesso vazio.

MAT01 deduplica o Frete antes de produzir PE/CB; documento,0,01, cortesia,
cancelamento, volume, previsão, finalização e responsável têm regras explícitas.
MAT02 conta entidades MAN/INV uma vez, aplica exclusões e bindings, calcula
issued+unloaded e scanned*100/total, preserva zero/nulo e recompõe data/filial.
MAT05 deduplica as arestas diretas/Coleta antes da receita; competência/status,
capacidade KG de trator e duas carretas e motorista vêm de referências vigentes.

Filiais, Clientes, Veículos, Motoristas, Plano de Contas e Usuários são consumidos
com linhagem. Homônimos/placas repetidas não fundem identidades. Releases e
atribuições preservam vigência, papel, conflito e evidência de rekey.

BOOTSTRAP, INCREMENTAL, BACKFILL e REPLAY operam com partições explícitas. O
cenário inclui registro de2036-04-01 sob relógio lógico2036-04-15, fora dos últimos
três dias. As entradas diárias cobrem o dia declarado; os cinco fatos recompõem
o intervalo FULL de três dias, inclusive recortes antigos/novos após correção.
Somente publicação local terminal do incremental contíguo avança a fronteira.

Os dezenove contratos pub.analytic_lab_sql_01..19 têm673colunas de negócio e971
colunas físicas com linhagem. A leitura JDBC valida tipos, ordem, precisão,
escala e nulidade reais e limita a saída. SQL04 nasce vazia, mostra somente
ausência confirmada e limpa após reaparecimento; SQL03 já apresenta a candidata.
Os processos de query recompõem sua própria fixture e exercitam resultado
positivo para cada seleção. SQL10 é monitoramento interno com instantes reais:
um evento sem início não recebe duração inventada.

Raster incompleto, frota sem binding, referência financeira inválida e snapshot
Coletas inválido produzem degradação explícita. CAP e demais ramos independentes
podem concluir. Cada processo faz rollback; reabrir adapter na mesma transação
é reidratação SQL, sem alegação de recuperação de dados revertidos entre processos.

## Verificações e revisão efetivamente testada

- Verify integral Java17: **1946 testes unitários**,0falhas/erros e quatro
  skips históricos preservados pelo inventário de casos; formato, Checkstyle,
  arquitetura e cobertura aprovados.
- Perfil JDBC real:233IT anteriores preservadas exatamente. Suíte integral e
  campanha dirigida posterior totalizam **378 IT únicas**,0falhas/erros/
  skips na seleção final. A campanha posterior altera somente dois arquivos de
  teste e cobre integralmente suas classes; fontes main/resources são idênticas
  às do verify aprovado.
- **40 processos JAR**: cenário, recomposição, replay, status,19queries,
  quatro degradações e recusas de flags/escopo/alvo, além do Main sem argumentos.
  Fontes, testes qualificados e todas as classes/resources empacotadas conferem
  por SHA e inventário; agregados antes/depois confirmam rollback.
- Concorrência: SPIDs distintos nas procedures efetivas de claim, PLAN, Raster
  e MAT01/02/05, com recusa/timeout e consumo depois do rollback do primeiro dono.
- Escala final4/16/32/16:21,124/23,009/32,142/32,394s;94/382/766/382linhas DE e
 24/96/192/96Raster. Uma página/lote em voo e liberação final; caso Raster256
  verifica liberação da página pai antes de recursão.
- 284 planos reais: zero spills ou MissingIndex. Quatro planos registram conversões
  explícitas de OPENJSON.key/value;56 contêm avisos, incluindo estatísticas e
  grants em temporários pequenos. Nenhuma evidência observada justificou mudar
  índice/grant por hipótese. Heap é diagnóstico; não prova platô nem SLO.
- 98 migrations/baseline conferidos;47novas versões qualificadas em rollback e
  instaladas, com contagens preexistentes preservadas. Scanner offline,
  contraprovas, contratos estáticos, runtime e continuidade final aprovados.

O primeiro verify físico preserva um timeout da escala64 e quatro gates de
cobertura reprovados. As142contraprovas por campo/limites e a escala final
4/16/32/16 resolveram a qualificação; a execução dirigida anterior de64 que
passou também foi preservada. Falhas de fixture/asserção, estilo e tentativas
anteriores não foram apagadas nem convertidas em PASS.

## Construção, aceites e limites

O mesmo universo de45unidades passa de32para37 (**82,2%**): somente MAT01, MAT02,
MAT05, V2-034 e V2-037 entram no numerador. Seis dimensões aprofundam V2-035;
dezenove views contam uma unidade V2-037; ausência aprofunda V2-013.
**67/115 aceites históricos permanece separado e inalterado.**

Permanecem pendentes identidade/completude/frescor reais de Raster, binding/frota
nominal, decisões empresariais de filial/fiscal/financeiro e manifesto consumidor
aprovado. Nenhum pai V2-034/036/037, Frota, V2-012/038/041/050 ou gate produtivo
fecha por estas fixtures. Não houve ensaio de banco novo físico versus upgrade,
COMMIT/crash durável, audit de feed externo, deploy, commit Git ou cutover.

## Artefatos para revisão

- [Matriz A–N](MATRIZ-A-N.md), [matriz por coluna](matriz-colunas-final.json),
  [regras MAT01](MAT01-REGRAS.md), [MAT02](MAT02-REGRAS.md), [MAT05](MAT05-REGRAS.md).
- [Resumo verificável](verification-summary.json), [estrutura](estrutura-local.json),
  [quadro45](quadro-construcao.json), [manifesto final](manifesto.json).
- [Diff completo](../../../target/macrobloco-analitico-20260912-01/diff.patch) e
  [diff de revisão](../../../target/macrobloco-analitico-20260912-01/diff-review.patch),
  ambos contra inventário inicial2704, com17arquivos preexistentes alterados.
- [Comandos](COMANDOS.md), [STATES](../../../STATES.md) e
  [retomada/checkpoint vigente](../../continuidade/RETOMADA.md).

Snapshots históricos, migrations anteriores, orientação de entrada única e
manifestos predecessores mantêm os hashes originais. Hash prova integridade,
sem representar aprovação humana ou aceite nominal.
