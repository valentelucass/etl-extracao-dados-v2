# Entidades, contratos e comparação de resultados

Estado: revisão local e regressão final concluídas, com JAR extraído e recibos vinculados
em provas.json. Os contratos históricos e seus aceites não foram reescritos. Não houve
execução da V1 ou chamada de fornecedor.

## Universo preservado

`Test-EntityAlignmentCatalog.ps1 -SelfTest` confirmou nesta rodada 11 entidades,
2.437 campos, seis contraprovas e zero reclassificação. O inventário anterior
[entidades.json](../alinhamento-entidades/entidades.json) conserva filtros,
extratores, grãos, contratos de construção e limites de autorização. A matriz de
campos continua em [campos.csv](../alinhamento-entidades/campos.csv).

As mudanças desta rodada ligam entradas aos consumidores existentes. Não
transformam os 2.437 registros do catálogo em novos campos implementados, nem
somam os mesmos resultados aos numeradores39/45 e67/115.

| Família | Entrada e consumidor integral | Resultado conferido e decisão preservada |
| --- | --- | --- |
| COL | Páginas declaradas → ExtrairColetasDataExport/LocalRelationalRuntime; suplementos e quatro observações sweep. | Aliases, presença, contagem por entidade e múltiplas linhas; SQL03, MAT02 e preview33. Ausência não provoca desativação no modo integral. |
| FRE | Uma release declarada → captura relacional e dependência das expansões. | Dois caminhos da mesma revisão; total143,25/187,50 chega aos fatos e SQL02. FRE-02/FRE-03, performance oficial/alternativa e finalização parcial V099 preservados. |
| MAN | Páginas declaradas → extrator/reducer SQL e composição explícita com Coletas/Fretes. | Raiz, pick e MDF-e conservam grãos distintos. MAT05 e SQL08/09 conferem métricas e relação; não se copia MAX para resolver conflito. |
| COT | Páginas declaradas → runtime de Cotações, release tarifária e dimensão recebidas. | SQL05 confere Valor frete176,50/291,25 e Min. Frete/KG1,53/2,17 para SP→RJ; vigência/release incompatível não publica. |
| LOC | Páginas declaradas → pipeline de dependência com relações explícitas. | corporation_sequence_number tipado e vínculos recebidos; datas/valores e linhagem conferidos. sequence_number, posição ou hash de exibição não viram identidade. |
| USER | Páginas GraphQL declaradas → parser/streamer e runtime current/history. | ConjuntoB tem24 usuários em20+4; SQL19 e nomes consumidores conferidos. enabled=true e modos BACKFILL/REPLAY; sem incremental inventado nem desativação por ausência. |
| CAP | ExpansionArtifact recebido → captura/staging/recomposição existentes. | Raiz/parcela/componentes17/93 explícitos; SQL06 confere163,75/277,25. Decisão local de identidade não ratifica identidade real. |
| FAT | ExpansionArtifact, relações, termos e suplemento fiscal recebidos. | SQL01/MAT04 por componente; NFS-e/Série vem do arquivo explícito. Não existe preenchimento para série ausente. |
| INV | ExpansionArtifact e relações recebidas → composição existente. | SQL11 por componente explícito, com IDs, datas e valores diferentes. Hash legado não resolve raiz/filho. |
| SIN | ExpansionArtifact e referências recebidos → composição existente. | SQL12 e dimensões reais do cenário local. Ausência de veículo/referência continua bloqueando só o dependente conforme os gates existentes. |
| RAS | RasterArtifact recebido → gateway/parser/captura e SQL13. | Três observações por pai, uma parada declarada, janela alternativa, duração97/125min e placas distintas. Ordem ausente ou incompletude não recebem posição como identidade. |

## Divergências demonstradas e corrigidas

- O parser0154 aceitava o controle antigo e recusava famílias adicionais/período
  alternativo: `red-results.json` registra QUAL_JSON_MEMBERS e LOCAL_SCENARIO_SCOPE.
  O schema v2 agora exige todas as entradas, contexto e apoios; não há fallback.
- A captura de Fretes relacional recusava a release comum ao caminho de expansão.
  V101 admite somente o fingerprint previamente ligado ao mesmo cenário/escopo.
- Namespaces, DQ, protocolos e janelas fixos impediam períodos/tenants explícitos.
  V100 e os consumidores Java recebem e conferem o contexto do run. Troca de
  origem/tenant é recusada antes de abrir ciclo; dois scopes com mesmas chaves
  e datas foram exercitados sem mistura.
- A linhagem de Coletas pressupunha uma linha por raiz. A prova agora usa o
  número de linhas declarado por raiz e o confronto SQL de staging/auditoria.
- O comparador wire tratava MissingNode como valor; a contraprova de ausência
  ficou vermelha antes da correção que distingue ABSENT/NULL/VALUE.
- Replay Raster confundia revisão da operação e revisão da fonte. O recibo agora
  conserva a revisão efetivamente capturada, e três ciclos com cinco fatos/19 SQL
  passaram. Evidência relacional nova declara outro evidenceId; retry do mesmo ID
  imutável continua recusando bytes/revisão divergentes.
- Suplementos consultavam/gravam por linha. Passaram a lookup parametrizado em
  conjuntos de até16 e aos batches/TVPs existentes. Uma chave MAN ou componente
  fiscal desconhecido recusa antes da materialização; não some silenciosamente.
- Flags antigas de falha que dependiam de fixtures foram recusadas no modo
  integral. Versões/campos internos desconhecidos das cinco referências também
  recebem recusa; ausência opcional/NULL e regras de negócio seguem o importador.

Correções nos esperados de teste foram tratadas separadamente: valores de frete,
datas de faturamento/criação, IDs de componentes, ordenação lexical de usuários,
eventos acumulados de monitoramento e timestamp CT-e avançado. Falhas anteriores
estão preservadas; SQL observado não foi convertido em oráculo.

## V1 como comparação

Foi lido o mapper V1 de Localização: exige sequence_number, converte campos e
calcula hash operacional. O contrato V2 fixa corporation_sequence_number e
recusa sequence_number como alias de identidade. Essa diferença é intencional.

O mapper V1 de Manifestos chama calcularIdentificadorUnico após metadata e cita
pick/mdfe/hash como alternativas. A V2 preserva raiz escopada e filhos tipados,
com conflito explícito e relações recebidas. O hash legado não foi transplantado.

O mapper V1 de Fretes mantém finishedAt, performance oficial, cte.createdAt e
cte.issuedAt como campos distintos. A V2 conserva a decisão FRE-02/FRE-03
comprovada por16 casos SQL V099, sem trocar frescor por updatedAt.

O estado V1 de08/09 documenta correção de filtro updatedAt para Usuários. Isso
não altera o contrato V2 USERS_SNAPSHOT. O mapper V1 mantém ativo=true; a V2
continua respeitando o snapshot enabled e a presença do nome, sem fonte temporal
deduzida da data técnica de extração.

## Oráculos e vazios legítimos

A e B são conjuntos autorados antes das consultas, com períodos2037-08-11 e
2039-02-05, IDs não sequenciais, relações permutadas, valores e páginas diferentes.
Os esperados são tuplas fixadas separadamente; `LocalFactOracle` compara grão e
valor por conjuntos SQL e `DeclaredSqlOracles` compara as19 saídas campo a campo.
Timestamps técnicos/UUIDs têm regras específicas; linhas de monitoramento não
são ignoradas. O teste de oráculo errado recusa valor, chave, cardinalidade,
precisão e fato, mantendo as saídas não afetadas verificadas.

SQL04 é vazio em A/B porque o modo integral só produz preview e não confirma
ausência. Sua contraprova positiva está no cenário histórico próprio
AnalyticLaboratoryCollectionSweepIT: duas observações completas confirmam a
ausência, geram a linha com13 campos, e presença posterior a retira. Esse aceite
histórico não habilita apply para o universo explícito integral (Java/SQL recusam).

As métricas de duas escalas medem a microescala sintética dentro dos tetos atuais.
Não equivalem a desempenho, completude ou aceitação de fonte produtiva. G01–G08
conservam suas parcelas nominais/materiais e a evidência externa faltante.
