# 0096 — MAT01 e consultas de Fretes/Localização em integração

EM_EXECUCAO A–N. Predecessor0095; construção32/45, aceites67/115 sem alteração.
V001–V062 instaladas; baseline acompanha. Nenhum fechamento integral ou JAR
composto foi declarado. Continuar o pedido sem perguntas após compactações.

Conferido: physical-freight-attributes-01 passou3IT. A prova máxima Unicode
adicionada passou na tentativa physical-mat01-02 (FreightAttributesIT4/4),
sem truncamento/overflow físico dos90atributos. Comparação por campos agora é
AnalyticFieldComparison.bytesLimited(maximum), limitada a256KiB.

O runner de build agora cria build-<attempt>, preservando todos os diretórios
anteriores. Isso removeu a contaminação por classe RasterComparison antiga após
o rename do fonte. ArchitectureRulesTest passou17testes em directed-boundaries-03,
mais3testes do mapper de atributos. O selector anterior usou nomes inexistentes
para os testes Raster: seus nomes corretos são AnalyticLaboratoryRasterParserTest
e AnalyticLaboratoryRasterTransportTest. O body handler havia passado2testes
na rodada directed-boundaries-01; repetir os afetados na regressão consolidada.

V058: receipt MAT01/02/05; MAT01 com observações tipadas imutáveis, ponteiro
corrente por Frete/indicador e partição mutável; PE/CB, documentos, gates, ref,
bindings e proveniência. V059: path correto8656/fit_dpn_delivery_prediction_at,
validação de tipo/data e precisão defensiva. V060: SQL02(123colunas legadas)
e SQL07(29), a partir de capturas e dados preparados. V061/062: extensão de
contrato sintético de Fretes vinculada ao run analítico, com fingerprint real
da release fixa; checks originais de completude/escopo permanecem intactos.

Novos componentes: AnalyticMaterializationRequest, JdbcAnalyticMaterializations,
JdbcAnalyticSourceContracts. ExpansionDependencySource tem opt-in explícito para
os dois campos de finalização; LocalExpansionDependencyRuntime consome a release
da instância. A release antiga/fingerprint padrão não foi substituída.

Evidências reconciliadas:
- physical-mat01-queries-05: FreightOperationalIT4passou, QueriesIT3passou;
  FallbackIT2passou/1erro no oráculo de escala, preservado.
- physical-freight-fallback-06: FallbackIT3/3passou, zero skips; oráculo corrigido
  confirma que escala>8 é recusada pelo mapper antes do suplemento, captura com
  quarantine1 e sem fatos publicados. Sessão46486 terminouexit0.
- Gates16casos PE/CB; fonte120/0/nulo/negativo/0,01/0,01000001; documentos,
  cortesia/inativo/cancelamento/complementar/substituto/blacklists; mudança de
  data; replay exato/NOOP/receipt divergente; finalização civil com nanos;
  NFS-e, cubagem negativa, fallback de Localização e múltiplos vínculos.
- SQL02: metadata123colunas e valores, performance nula e9faixas de-4a+4.
  SQL07: metadata29colunas, status/terminal/cancelamento distintos, SEM_MAP e
  alias positivo em release2; filial atual permanece UNSOURCED_LEGACY.
- Export-AnalyticSqlMetadata.ps1 gerou metadata-nine-contracts-02-sql-metadata.json:
  9objetos/307colunas totais, incluindo técnicas. Inventário legado continua673.

Tentativas falhas preservadas: estilo; path/contrato não declarado; duas fences
SQL de fingerprint; collations SQL02/07; oráculo de escala. Antes/depois agregados
foram iguais nas provas físicas. Nenhuma DML sintética persistiu.

Documentos vivos: ADR0050 ANA11–13, MATRIZ-A-N.md e MAT01-REGRAS.md. As matrizes
ainda são parciais e listam pendências obrigatórias. Atualizar a referência à
tentativa06 pendente nesses documentos, pois passou após sua redação.

Próximas ações necessárias:
1. MAT01/SQL02: finished_at fallback, XML NFS-e, comprovante Inventário ativo,
   binding/ref ausente, correção de filial e scopes; testes do opt-in/fences.
2. MAT02 e MAT05 e demais SQL01/03–06/08–12. Loader MAT02 foi lido integralmente;
   grão dia+filial+Geral, soma de entidades canônicas e fallback INV→Frete explícito.
   Manifestos precisa ampliar o perfil sintético de captura com campos aprovados
   do mapper existente, conservando contrato padrão e regras do reducer.
3. Regressão afetada pela extensão: ExpansionDependencySource/runtime e procedures
   scope/apply; repetir arquitetura e testes Raster com nomes corretos.
4. J–N integral:11fontes/5fatos/19consultas/JAR, Sweep opt-in, concorrência/escala,
   verify/233ITanteriores, matrizes completas, diff inicial e sucessão exata.

Não há processos ativos deste checkpoint. Próxima migration livre:V063.
Não repetir geradores privados de V057–V062: migrations instaladas são imutáveis.
