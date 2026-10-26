# 0617 — Suíte completa com 624 aprovados; auxiliar de catálogo com 51 aprovados

Continuação de0616 em26/10/2026. Etapas3 e4 continuam abertas: ainda é preciso
concluir a gravação e a leitura de conferência das entidades e confrontar os
valores com a ESL ou os Excel no mesmo recorte. Nenhuma nova chamada à fonte ou
conexão SQL foi realizada nesta continuação.

## Resultados reais e limites

| Unidade | Resultado observado | Alcance |
| --- | --- | --- |
| sk56/full624-ful1 | 624 encontrados, iniciados e aprovados; zero falhas, skips, abortos ou falhas de containers | Original1/193.000ms/pico606.932.992bytes; **a rodada permanece não qualificada** |
| Preservação e restauração de g1 | Os 4.546 arquivos originais foram verificados por SHA antes e depois; zero alterados ou ausentes | Cinco JSON de medição sintética preservados fora do stage; conjunto original restaurado exatamente |
| Names8/11, integração Root02 | 51 casos distintos aprovados:45 anteriores reexecutados e seis causais; 14 classes, compilação estrita sem diagnósticos | Original0/17.476ms/pico503.156.736bytes; zero HTTP, SQL e leitura de credenciais; não prova o catálogo instalado |

O helper EFA1 de descoberta de classes no JAR permitiu passar também os26 testes
de arquitetura. A recusa posterior foi `R56_G1_STAGE_FILE_SET_CHANGED`:
`RuntimeFiveVerticalPipelineTest.java:169` escreve medições em
`target/bloco55/measurement/{uuid}` relativo ao diretório de trabalho. Como esse
diretório era o próprio SourceStage, cinco arquivos novos apareceram ali.
Nenhum arquivo original mudou. A execução e seu recibo Original1 permanecem
preservados; a restauração não os transforma em aprovação.

O sucessor preparado usa um diretório work novo por unidade, com cópia byte-exata
somente dos1.778 inputs de fonte. Mantém JAR153 e classes de teste de g1,
helper EFA1,29 classes de overlay exclusivamente de teste, corpos/assertions,
planos624/9 e limites originais. O stage continua sendo verificado integralmente,
sem excluir os arquivos gerados pelos testes. É preciso executar e fechar novas
unidades624/9 com os processos atuais admitidos antes dos bindings finais.
As qualificações próprias200 e ColetasFinal8 continuam aplicáveis ao produto
inalterado; não há motivo para reconstruí-lo por mudança dos processos PM2.

## Consulta ao catálogo do banco

O auxiliar Names8/11 continua com o bootstrap operacional V3, legado16
preservado, limite8MiB e Original1GiB/4min. A integração01 foi recusada em revisão,
antes de execução: hashes em maiúsculas eram incompatíveis com a comparação
estrita dos leitores Java. A integração02 preservou os33 payloads e corrigiu
somente a serialização dos hashes do envelope, sem relaxar leitores.

Manifesto02: `85EB697620581FAD5457CDE4E53820094231A8C804D8F5227E6947C4D194BA3B`.
O auxiliar conserva o JAR histórico E05B e suas oito bibliotecas; seus51 testes
não qualificam o JAR153 do ETL. A próxima unidade física, separada, deve executar
11 SELECT de metadados para oito nomes exatos de P3/usuários. Ausência histórica
ou nome não coberto não vale como observação atual. Nenhuma admissão dessa
consulta, criação de estrutura, DDL ou captura foi emitida pelos testes locais.

## Valores reais para conferência externa

A revisão dos readbacks de ColetasV16 e dos auxiliares efetivamente usados em
MFT0604/COT0606 confirmou comparação completa em memória, seguida de rollback
e descarte dos buffers. Os recibos retêm contagens e estados técnicos; não retêm
as315 e287 linhas para comparação posterior. Não reconstruir esses valores a
partir de contagens, hashes ou da Source.

Foi delegado um sucessor mínimo do auxiliar para reter, em arquivo privado,
os valores efetivamente retornados pelo JDBC, sem mudar JAR153 nem acrescentar
consultas. A proposta deve conservar ausente/null, tipos/datas, ocorrências
repetidas e identificadores técnicos; os23 binds enviados ficam separados dos
valores relidos. Deve ter tetos próprios sem truncamento silencioso, distinguir
captura completa/parcial e preservar rollback/close inclusive se a escrita falhar.
Essa alteração ainda não foi compilada, executada ou ligada a uma unidade real.

O critério do usuário continua sendo banco versus ESL/Excel por registro e campo,
com entidade, chave, grão, campo de data, intervalo, fuso e filtros correspondentes.
Os636 códigos de viagem dos Excel não estabelecem automaticamente identidade
com Raster ou Coletas. Contagens iguais e Source=SQL não encerram a fidelidade.

## Continuação autorizada

1. Qualificar os processos atuais sem encerrar PM2 nem admitir processos por
   nome/pai; executar624 eClock9 no work novo.
2. Concluir os bindings atuais no mesmo JAR153. A proposta de adoção canônica
   cobre68 caminhos (67 exatos de g1 e o helper EFA1 completo) e foi revisada;
   ainda não foi aplicada. A proposta anterior de sete caminhos era insuficiente.
3. Executar o catálogo físico somente de leitura, concluir os testes do auxiliar
   de readback privado e a unidade real de Coletas com leitura completa/rollback.
4. Concluir as demais entidades e Usuários até EOF, e comparar os valores reais
   com a referência externa correspondente. Não declarar todas as entidades
   extraídas, banco validado ou paridade com a ESL antes dessas provas.

## Evidências privadas

- `B\RootScopeR56full624OriginalClosure0616.json`: Original1 conhecido,
  624 aprovados mas unidade não qualificada.
- `B\RootR56StageMeasurementDeltaBefore0617.json` e
  `B\RootR56StageMeasurementRecovery0617.json`:
  delta preservado e4.546 arquivos originais verificados por hash;
  SHA da recuperação `DD45539577809B3BBA764DB9B450D6D48D20216E16B32DA13DCA9BC77A0DF7CE`.
- `B\wk56\preparation-seal.json`: preparo do sucessor de working directory;
  `00F734F7022ECF93F14C5639727561017AEE3F8765F488679CBF805C563B8818`.
- `B\RootNames8Own51Qualification0617.json` e
  `B\RootNames8Own51QualificationAudit0617.json`:51 reais e auditoria literal
  dos hashes de models/compiler; uma tentativa de auditoria Root teve erros de
  construção de caminho e os checks foram refeitos com erro fatal habilitado,
  sem repetir os testes ou promover resultado ausente.
- `B\names8-root-integration-review-files-only-0617b\review.json`:
  `D5DBE11299A899391308363F3583E24E7A02E01F3BAA906A0AA5C3A286964D56`.
- `B\adoption68-review-files-only-0617\review.json`: proposta canônica pronta
  somente após qualificações finais; nenhum código canônico foi adotado.

Root continua único executor pesado e responsável por Source/SQL/browser/Git.
ESL permanece exclusivamente consulta/filtro/download. Sem DDL, promoção,
retenção de negócio, implantação, alteração de PM2 ou encerramento de processos.
A última Source continua INFO Coletas0607, delta1; etapas3/4 não encerradas.
