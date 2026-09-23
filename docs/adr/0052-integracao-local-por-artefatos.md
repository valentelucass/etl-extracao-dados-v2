# ADR 0052 — Composição local por artefatos e oráculos independentes

Estado: aceita no escopo técnico sintético local do pedido A–N adotado em
14/09/2026. Não constitui aceite de fornecedor, paridade nominal ou operação.

## Problema e decisão

LocalExpansionRuntime dependia de ExpansionSyntheticSource e a composição
analítica escolhia geradores fixos. Os oráculos de laboratório presumiam nomes
de componentes. A observação de Coletas usava a data interna do cenário. Isso
impedia trocar a entrada e sua identidade pelo pacote entregue.

ExpansionCaptureSource separa páginas, release e observação do caso de uso.
ExpansionSyntheticSource continua como adapter; ExpansionArtifact e
ExpansionPageSource fornecem páginas e bindings de arquivos limitados e pinados.
AnalyticExpansionSources e AnalyticRasterSources injetam a seleção explícita na
composição existente. Nenhuma seleção local amplia o dispatch operacional real.

Main oferece local-data, local-profile, local-raster, local-scenario e local-sweep.
LocalArtifactScenario é também consumido por ARTIFACT no supervisor existente.
O mesmo caminho executa captura, relações, prepare/apply, materialização, leitura
e comparação. Modos, origem sintética, alvo, janela, revisão e limites são
obrigatórios. Validação de bytes/contrato ocorre antes da conexão; supervisor e
worker validam os pins antes da reserva e do efeito, respectivamente.

DeclaredExpansionRelations e DeclaredWireRows recebem identidades tipadas com
tenant/papel/revisão. QualificationOracles consulta a identidade declarada no
Evidence, preservando o adapter histórico. Chaves alternativas foram exercitadas
em SQL e no JAR sem derivação por ordinal ou semelhança de nomes.

LocalFactOracle compara conjuntos e multiplicidade de cinco grãos declarados
por OPENJSON/EXCEPT no SQL Server. Os esperados são arquivos independentes; não
são produzidos pela consulta sob teste. Os 19 contratos e 971 metadados físicos
continuam no comparador existente. Os limites de parser/páginas/linhas/JVM são
preservados, incluindo o total cumulativo dos perfis de caracterização.

CollectionSweepInput vincula execução, dia, snapshot e fonte. O consumidor
observa quatro capturas e usa SweepResponsibilityPlanner para validar e apresentar
33 responsabilidades. Ciclos, órfãos, lease/escopo divergentes são recusados;
aplicação nominal de filhos continua bloqueada sem regra de presença aceita.

O planner e sua leitura do catálogo pertencem ao bootstrap; o pacote do kernel
mantém exatamente os seis tipos puros anteriores. Coleções da composição ficam
internas. PinnedLocalJson verifica uma página limitada e entrega os mesmos bytes
ao parser por consume, sem expor inventário ou massa pela API pública. O verify
detectou essas duas fronteiras antes da correção; os testes de arquitetura e suas
allowlists foram preservados integralmente.

## Consequências e provas

O produto não depende de JUnit, src/test ou workspace para consumir arquivos.
Os autores de exemplos permanecem em testes; os consumidores estão em src/main
e no JAR. Seis fontes de apoio/referências do cenário são explicitamente
PACKAGED_ANALYTIC_SUPPORT_V1, sem alegação de tradução nominal real.

As provas incluem duas entradas por expansão, dois perfis por família, duas
escalas completas, relações/identidades alternativas, 20 mutações dos cinco
grãos, divergência sanitizada com exit não zero, Raster/500 e cancelamento sem
publicação parcial, sweep em outros dias/conjuntos e cancelamento/lease inválido.
No pacote, as entradas passam pelo journal e suas barreiras de cancelamento.
Resultados e hashes ficam na rodada macrobloco-integracao-funcional-20260914-01.

Os efeitos físicos usam apenas localhost/ETL_SISTEMA_V2_SHADOW, autenticação
integrada, duas travas, confirmação no master, COMMIT bloqueado e rollback,
com agregados de todas as tabelas antes/depois. Não houve mudança de schema.
O runtime Raster passou a verificar cancelamento depois do último lote e antes
da publicação; o savepoint reverte esse trecho quando cancelado.

Integridade de artefato não comprova wire real, /info, identidade nominal,
conversão de aliases, regra fiscal, política de retenção ou autorização de
fonte/alvo. Esses parâmetros não foram inventados. Construção 39/45 e aceites
históricos 67/115 permanecem sob a medida anterior. Memória/tempo medidos são
observações locais limitadas, sem promessa de platô ou SLO produtivo.
