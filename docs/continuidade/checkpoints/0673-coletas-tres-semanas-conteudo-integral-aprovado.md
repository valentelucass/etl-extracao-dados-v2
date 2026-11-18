# Checkpoint0673 — Coletas: três semanas com o conteúdo integral aprovado

A auditoria de conteúdo de Coletas atingiu o critério humano de três períodos distintos com 100% de concordância nas 31 colunas do relatório ESL6908. Cada ocorrência foi comparada com seus valores completos e as repetições foram preservadas. Não é um aceite baseado apenas na quantidade de linhas ou colunas.

| Data Solicitação | Linhas por lado | Coletas comparadas | Colunas | Decisões de células dos dois lados | Diferenças / pendências |
|---|---:|---:|---:|---:|---:|
| 19–25/10/2026 | 1.561 | 1.155 | 31 | 96.782 | 0 / 0 |
| 12–18/10/2026 | 1.277 | 937 | 31 | 79.174 | 0 / 0 |
| 26/10–01/11/2026 | 1.439 | 1.144 | 31 | 89.218 | 0 / 0 |

Total: 4.277 linhas por lado, 132.587 células por lado e 265.174 decisões de células. Foram executadas 131 verificações do mecanismo em cada confronto completo. Os aceites das primeiras duas semanas são RootB323905B e Root5982CC27. O aceite definitivo da terceira está indicado ao final deste documento.

## O que foi efetivamente conferido

As 31 colunas são associadas conjuntamente por coleta em tuplas completas, com contagem de cada repetição e referência à ocorrência original. Os valores não foram associados pela posição das linhas nem somente por hashes. Zero coletas faltantes em qualquer lado, zero diferenças de multiplicidade, zero células ou linhas pendentes, todas as ocorrências contabilizadas uma vez. Os oito campos numéricos/totais participam do confronto. Totais isolados não substituem a associação dos valores por coleta.

Datas civis, células vazias/ausentes, ações e números usam as decisões específicas já verificadas contra a representação nativa do Excel. Todas as 2.878 células de data/hora do terceiro arquivo foram conferidas por leitura independente e por prova Native-only separada, sem usar a concordância com a API como prova da regra. Números são comparados na representação nativa comprovada do Excel; a maior precisão original da API permanece preservada. Não houve tolerância genérica, descarte, deduplicação, alteração do workbook, substituição de valores ou mudança da API para forçar concordância. Informações e colunas extras da API continuam válidas e preservadas.

## Par definitivo da terceira semana

Source real0672-01, execuçãofeafefbb-b4f3-4737-95b6-2cf523d16ef2, Data Export6908, filtro exclusivo picks.request_date26/10–01/11. Foram1.439RAW=mapped, INFO1/DATA13/HTTP14 todos200, página13 terminal vazia. Original0/104161ms/pico513310720B; processos próprios e fluxos encerrados, histórico estrangeiro preservado. Aceite de capturaFB8BDA3A e publicação9414B650. A captura consumiu uma autoridade nova, sem reutilizar o grant anterior. Limites serial/per100/3s após completion/1tentativa/STOPprimeiro429ounão2xx/Source210s/Original300s1GiB preservados.

Excel oficial1760924 gerado em03/11/2026 às22:48BRT, imediatamente após o término da capturaSource às22:47:46BRT. Mesmo período, cinco demais filtros vazios, semcache,1.439linhas/31colunas. Download272078B/SHA5453BE648121F84C85DC543A890C00DFE4D8C864E0574AE043DF4048244E627A, stagingBDAF5D27. A leituraNative única do arquivo atual foi aceita48EA61FD. ProjeçãoSource atual196AAB19; definições31 e produtorE4BC inalterados. Leitura independente de exibição51ACA493, provaNative-onlyC946A955, política específica04F1B1C3. Todos os corpos e células foram lidos/verificados somente no processamento protegido correspondente.

Auditoria de intervaloC1B503FF examinou todos os1.439request_dates doRAW:269em26/10,350em27/10,287em28/10,280em29/10,249em30/10,4em31/10,0em01/11. Zero registros fora da semana, sem refiltrar, reescrever ou descartar.

## Diferenças anteriores e correções preservadas

Os pares anteriores da terceira semana continuam registrados como divergentes: Source0669-03/Excel1760850 tinha7coletas diferentes; duas mudanças de DataAtualização foram comprovadamente posteriores à capturaSource e anteriores à exportação. Source0671-02/Excel1760888 reduziu a diferença a uma coleta, somente emVolumes. O valor extraído dessa coleta era exatamente oRAW daAPI; o volume mudou entre as próprias capturasSource e entre os própriosExcel. Não foi provado o instante exato nem o autor da mudança, e nenhum defeito do mapper foi demonstrado nesse caso. Diagnóstico88B667AA e auditoria503C9D11 permanecem preservados.

Foi obtido um par novo e próximo no tempo, com identidade/autoridade próprias, e o confronto integral das31colunas foi repetido. Só esse novo resultado sem diferenças recebeu o terceiro aceite. Os resultados anteriores não foram reescritos como sucesso.

O defeito anterior que fixava19–25/10 no produto foi corrigido no sucessor privadoDFD451DE, já verificado por21casos causais e utilizado nas capturas das últimas duas semanas. O código de extração/mapeamento e o controle de velocidade permaneceram preservados. Tentativas interrompidas por mudança de identidade externa e preparos recusados antes da leitura também continuam no histórico.

## Estado e limites do aceite

Estado canônico atual86A86EEA/707552B/ACLB4/Root0658/vaga0, com16paresSource retidos. Não restaurar qualquer baseline anterior sobre os efeitos válidos destas capturas. Nenhum processamento pesadoRoot permanece ativo depois do aceite. V1 permanece pausado por decisão humana; SQL, produção, contas, permissões e versões não foram alterados.

Este aceite conclui os três testes semanais de conteúdo da extração de Coletas. Não é publicação produtiva, implantação automática do produto privado, prova de todos os períodos futuros nem prova do consumo real do dashboard. O consumidor mantém52testes locais aprovados e18campos diretos verificados em tipos/limites; seguem pendentes contexto de exclusão, região logística, horário de publicação, catálogo/semântica de status, compatibilidade deVolumes, ponte/publicaçãoSQL e consumo real pelos endpoints. Esses pontos devem ser tratados no aceite do consumidor, sem afirmar prontidão real por causa desta comparação.

Aceite final Root: C:\Users\suporte\Documents\Codex\2026-10-27\etl-v2-supervisor-finalizacao\outputs\root-acceptance-coletas-week-complete0673-01.json; 6720B; SHA-256 DE9B1A5954588E753A3A7AA120D573B1BEBF14F2D11F900DC0754FEEA953D6AE. Confronto final: Original0/117078ms/pico324308992B; resultado 756BED61163F907509AC5281051DC0B976A2363F2E0C535C7339C97C73885EBA; resumo D508FBAFCAAB52D2F4FE611B17A7F4265C1B141E5BDFEAEBDBB6198041F5F282; relatório completo A67034C719FD337E8129A24CDE49035B169E7E63DA66EE9ADF4341B93E1B8160; associação completa CAC8A7EF6F8C537E445015C37D1CC057FC083549E4F8DE4B8958DED02D6519C1.
