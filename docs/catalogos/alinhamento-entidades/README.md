# Alinhamento das entidades V2: construção, origem e consumidores

Esta é a entrada para conferir se cada entidade implementada corresponde ao contrato de
construção e às evidências da origem. A V1 é uma referência de comportamento e de defeitos
conhecidos. Copiar seu resultado não basta para aceitar a V2: uma diferença deliberada da
construção deve ser preservada; uma divergência acidental deve ter reprodução e correção.

Revisão local de 14/09/2026. Nenhuma API autenticada foi chamada nesta revisão. V2-041 mantém
`EXTERNAL_HOLD` por falta de atestado de rotação. A implementação local e os testes sintéticos
continuam permitidos. Não houve aceite de identidade, completude, produção ou cutover.

## Como conferir uma entidade

1. Localize a entidade na tabela abaixo e consulte seu contrato de fonte. Nome parecido,
   coluna da V1 e campo usado em `order_by` não demonstram identidade na API.
2. Use [campos.csv](campos.csv) para procurar o path/coluna. O índice deriva dos mesmos
   2.437 IDs da [matriz de construção](../macrobloco-integracao-funcional/rastreabilidade-2437-campos.json).
   `declaredTarget` conserva o destino originalmente planejado; `implementedTarget` só contém
   vínculo local registrado na matriz. Um destino planejado não é prova de tabela existente.
   Destino implementado vazio neste índice pede consulta ao vínculo completo; não significa
   automaticamente falta de implementação. `rowKind` distingue entrada, seleção, saída e metadado.
3. Confira tipo **no JSON**, presença `ABSENT/NULL/VALUE`, escopo, grão, conversão, consumidor
   e teste. Tipo Java de DTO legado não comprova tipo transmitido. Um campo financeiro sem
   unidade/moeda/precisão aprovadas não ganha esses valores por dedução.
4. Compare entradas equivalentes e alternativas com expectativa independente do mapper.
   Para uma relação, conferir chaves e cardinalidade antes de somar evita multiplicação de valores.
   Uma amostra ou terminal de paginação não prova snapshot completo nem exclusão na origem.

As regras de negócio são as 75 linhas de
[regras-negocio.csv](../portabilidade/regras-negocio.csv). Este documento organiza a consulta;
não altera suas classificações, responsáveis, aceites ou contagens: **39/45 e 67/115**.
Os 2.437 IDs incluem entradas, dimensões, fatos, publicação e metadados físicos; não representam
2.437 campos distintos da API.

## Mapa das 11 entradas

Filtros da tabela descrevem o contrato local de requisição. A tradução das bordas civis da fonte
para `[início,fimExclusivo)` deve obedecer à política versionada da entidade. Não subtrair dia ou
segundo por conveniência. `scopes.by_updated_at` é complementar em 6908/6389, não substitui
o filtro de negócio nem comprova watermark.

| Entidade / fonte | Filtro e ordenação locais | Grão / identidade que precisa bater | Consumidor efetivo e prova local |
|---|---|---|---|
| Coletas / 6908 | `picks.request_date`; `sequence_code asc`; complemento `scopes.by_updated_at` | `id` com tag de tipo e escopo; sequência é alias. Linhas físicas podem expandir a mesma coleta. | `ExtrairColetasDataExport` → staging/promoção Coletas; integração temporal e `LocalAnalyticCollectionSweep`. `ColetasTemporalLocalIntegrationIT`, `AnalyticLaboratoryCollectionsIT`. |
| Fretes / 6389 | `freights.service_at`; `corporation_sequence_number asc`; complemento `scopes.by_updated_at` | `id` escopado; sequência corporativa não é identidade técnica. CT-e, performance e relações têm presença própria. | `ExtrairFretesDataExport` → `JdbcSqlServerFretePromotionGateway` → core e recibo; SQL 063 reproduz terminal com CT-e omitido. |
| Usuários / GraphQL `individual` | Query estática `id/name/pageInfo`, `enabled=true`, até 20 nodes; cursor durante a execução | ID integral ou textual não vazio, com tag e escopo. `name` não é chave. | `LocalUsuariosRuntime` → `ExtrairUsuariosGraphQl` → promoção Usuários; `RuntimeUsersOperationalRequestTest`, `RuntimeUsersSessionTest`. |
| Manifestos / 6399 | `manifests.service_date`; `sequence_code asc` | `sequence_code` candidata à raiz; MDF-e e Coletas são filhos/relações. `mft_pfs_pck_sequence_code` é path de origem; `pick_sequence_code` é nome interno legado. | `ExtrairManifestosDataExport` → reducer SQL → captura de relações/composição; `AnalyticManifestCompositionsIT`. |
| Cotações / 6906 | `quotes.requested_at`; `sequence_code asc` | Sequência candidata, relações com Usuários e tarifas com versão/vigência explícitas | `ExtrairCotacoesDataExport`, `LocalAnalyticQuotesRuntime` → snapshots/tarifas; `AnalyticLaboratoryQuotesIT`. |
| Localização de cargas / 8656 | `freights.service_at`; `sequence_number asc` | `corporation_sequence_number` candidata. O campo de ordenação `sequence_number` não foi publicado na observação histórica. | `ExtrairLocalizacaoCargasDataExport` → staging/promoção → relação analítica; testes de mapper e runtime de cinco verticais. |
| Contas a pagar / 8636 | `accounting_debits.issue_date`; `issue_date desc` | Ocorrência física; `ant_ils_sequence_code` candidata de parcela, sem ID comprovado da raiz. Rateio não é outra conta por inferência. | `ExtrairContaPagarDataExport` → `JdbcExpansionStaging` → bindings e aplicação local; `ExpansionLaboratoryFieldCoverageIT`, `AnalyticExpansionCaptureIT`. |
| Faturas por cliente / 4924 | `freights.service_at`; `unique_id asc` | `id` candidata da linha; título lógico exige vínculo próprio. `unique_id` ausente não vira chave. | `ExtrairFaturaClienteDataExport` → captura/aplicação de expansão → fatos de faturas/faturamento com política fiscal explícita; testes de expansão. |
| Inventário / 10633 | `check_in_orders.started_at`; `sequence_code asc` | Raiz candidata por sequência; componentes de Frete/invoice separados. Hash de campos da V1 não prova identidade da fonte. | `ExtrairInventarioDataExport` → componentes/aplicação SQL → Coletores; `ExpansionLaboratoryFieldCoverageIT`, `AnalyticExpansionCaptureIT`. |
| Sinistros / 6392 | `insurance_claims.opening_at_date`; `sequence_code asc` | Sequência candidata e componentes/listas vinculados por contrato | `ExtrairSinistroDataExport` → expansão tipada → projeção SQL-12; testes de cobertura de expansão e captura. |
| Raster / viagens e paradas | Janela explícita e fuso IANA; envelope limitado pelo contrato local | `CodSolicitacao` candidata da viagem e `Ordem` candidata da parada. Posição no array não é substituto de identidade. | Artefato → `RasterResponseParser`/mapper → `LocalRasterRuntime` → JDBC; `AnalyticRasterCivilTimeIT`. Transporte de loopback não é integração homologada com fornecedor. |

Os nomes de classes desta tabela indicam uso local real. A composição do cenário ainda fornece
fixtures internas para seis famílias; ela não configura uma carga produtiva completa. As quatro
expansões e Raster aceitam artefatos alternativos, mas o binding sintético não ratifica identidade
do fornecedor. Os 19 contratos `pub.analytic_lab_sql_XX` continuam publicações de laboratório.
Nas quatro expansões, `capture_occurrence` do catálogo executável é um marcador interno de
ocorrência. Não é campo exigido da API, identidade da raiz ou prova de completude por `per`.

## Contratos de fonte e evidência

| Fonte | Documento da construção | Evidência e limite |
|---|---|---|
| 6908, 6389 e Usuários | [Primeira onda](../contratos-primeira-onda/README.md), [identidade](../identidade-primeira-onda/README.md) | Mistura explicitamente identificada de código, fixtures e observações históricas sanitizadas. Não há prova atual de completude. |
| 6399 | [Manifestos](../contratos-esl-6399/README.md) | Observação histórica: expansão de três sequências em quatro linhas; não equivale a PK global aceita. |
| 6906 | [Cotações](../contratos-esl-6906/README.md) | Contrato transitório; detalhes de regra, grão e atualização pertencem à vertical. |
| 8656 | [Localização](../contratos-esl-8656/README.md) | Campo de ordenação e candidato de identidade distintos; `429` histórico não define quota global. |
| 8636 | [Contas a pagar](../contratos-esl-8636/README.md) | Sem ID da raiz; `per` não comprova total de contas. |
| 4924 | [Faturas](../contratos-esl-4924/README.md) | `/info` histórico com 52 campos/17 filtros sem tipos versionados; subconjunto local não é schema completo da API. |
| 10633 | [Inventário](../contratos-esl-10633/README.md) | Evidências de campos não ratificam estabilidade da raiz/componentes. |
| 6392 | [Sinistros](../contratos-esl-6392/README.md) | Relações/listas exigem cardinalidade e identidade além da sequência observada. |
| Raster | [Contrato e identidade pendente](../raster-contrato-local/README.md) | Tipos/aliases legados são evidência estática; catálogo histórico precede a implementação local atual. |

Os catálogos de fonte descrevem a fatia e a data em que foram escritos. Frases como
“não implementa a vertical” delimitam aquele catálogo; o código local posterior consta do mapa
acima e da matriz funcional. Isso não transforma o catálogo antigo em prova remota atual.

## Diferenças da V1 que não devem ser copiadas automaticamente

- **Usuários:** a V2 foi construída como `USERS_SNAPSHOT` para `BACKFILL/REPLAY`. Datas identificam
  a observação, sem filtrar `individual`. A falta de `updatedAt` é uma decisão explícita da
  primeira onda, não um defeito demonstrado. A investigação anterior apontou uma diferença de
  estratégia; este contrato esclarece sua natureza. Incremental requer outro contrato completo.
- **Fretes — FRE-02/FRE-03:** frescor observado usa CT-e criado → CT-e emitido → criação → serviço.
  A [V099](../../../database/migrations/V099__preserve_freight_terminal_omission_freshness.sql)
  corrige a transição aberto → terminal que omite CT-e e ambas as datas, quando o frescor conhecido
  veio de CT-e. O SQL mantém o frescor efetivo conhecido e registra a origem da decisão no core;
  staging conserva o frescor e a presença observados. Nulo explícito no CT-e ou nas suas datas
  não recebe a exceção. Essa exceção preserva o grupo CT-e/finalizações conhecido, inclusive
  quando a observação parcial traz finalizações; o conteúdo observado permanece no staging.
  Ela não amplia a atualização de performance nem substitui a origem oficial por fallback.
  A decisão alimenta tanto a entidade quanto o recibo genérico, evitando contagem divergente.
- **Contas a pagar — CAP-01/CAP-03:** contador e travessia devem ter os mesmos filtros, incluindo
  segmentação por `created_at` quando contratada. Dedupe e promoção usam a mesma expressão de
  frescor; não copiar o desempate lexicográfico divergente do legado.
- **Faturas — FAT-02/FAT-07:** o mapper V1 e os consumidores dão precedências distintas a
  NFS-e/CT-e. Preservar ambas as evidências; saída do caso duplo exige política explícita.
  `serie_nfse` sem origem comprovada permanece sem fonte; não preencher por semelhança.
- **Inventário — INV-01/INV-03:** não copiar hash legado como identidade. Comprovante reconhecido
  é cumulativo por OR; resposta posterior sem a evidência não o apaga.
- **Manifestos:** preservar a decisão V03 de coorte e conflitos de métricas; um `MAX` do legado
  não é justificativa para escolher receita, peso ou capacidade sem regra.
- **Raster:** ordem ausente não vira índice da lista. A identidade precisa resistir a reordenação.
- **Publicação:** as diferenças de escala `TIME(0)`/`TIME(7)` nas saídas SQL-01, 05, 06 e 11
  continuam diferenças contratuais registradas, sem aceite de compatibilidade externa.

## Roteiro de teste da API quando o bloqueio for resolvido

No estado atual, **zero consultas remotas**. Antes de executar, o registro vigente de V2-041
precisa comprovar a liberação; uma credencial existente ou uma fixture não é esse atestado.
A allowlist local cobre somente as três sondas abaixo para 6908/6389/4924 e queries GraphQL
estáticas previstas nelas. Os outros templates e Raster precisam de escopo próprio.

- [Invoke-DataExportContractProbe.ps1](../../../scripts/probes/Invoke-DataExportContractProbe.ps1):
  `/info` e dados por `GET_WITH_QUERY`; conferir raiz, nomes, wire types, presença, filtros e
  quantidade de entidades distintas versus linhas físicas.
- [Invoke-DataExportGraphQlIdentityProbe.ps1](../../../scripts/probes/Invoke-DataExportGraphQlIdentityProbe.ps1):
  somente a consulta estática e o confronto de identidade autorizado, sem mutation.
- [Invoke-DataExportFinancialProbeMinimal.ps1](../../../scripts/probes/Invoke-DataExportFinancialProbeMinimal.ps1):
  relação auxiliar 4924, sem deduzir título lógico ou identidade do Frete por documento.

Rodada serial, teto declarado conservador, timeout até 30 s, resposta até 10 MiB, sem redirect
ou retry manual. Parar no primeiro HTTP não-2xx/429, limite não verificável, excesso ou teto.
Em 6908/6389, `per` limita IDs distintos verificados; expansão física acima de `per` só é
aceita com IDs escalares não nulos e contagem distinta dentro do limite. Em expansão sem raiz
comprovada, não converter ocorrência física em entidade. Não registrar tokens, URLs completas,
payloads, IDs, hashes de ID ou cursores no catálogo.

Para cada rodada, registrar versão/semântica, status, contagens, tipos e conclusão sanitizada.
Uma requisição alternativa (por exemplo, outro `per`, dentro do orçamento aprovado) deve chegar
ao mesmo parser/mapper/consumidor; comparar conjuntos equivalentes não prova unicidade global.
O esperado deve vir do contrato ou de oráculo independente, nunca da própria saída do mapper.
Datas ambíguas, campo ausente, nulo, tipo incompatível, chave repetida, expansão e página incompleta
devem ter resultados explícitos. Não continuar o sweep por uma travessia parcial.

## Correções e verificação desta revisão

Removidas nove abstrações antigas de extração de uma página, substituídas pelos extratores com
paginação completa e consumidor. Quatro arquivos de testes exclusivamente dessas abstrações
foram retirados; testes de capability e de contrato ainda úteis foram preservados. A capability
de Localização agora consulta o template do catálogo comum. O snapshot anterior conserva os
arquivos para revisão/recuperação. Os demais candidatos da investigação exigem análise própria.

A [regressão SQL 063](../../../database/validation/063_exercise_freight_terminal_omission_rollback.sql)
exercita as procedures instaladas em sombra com dados sintéticos e rollback: quatro terminais,
empate, CT-e emitido, nulos, observação antiga, estado não terminal, origem de frescor alternativa,
outro tenant, conflito e candidato mais novo; repete a promoção para verificar o recibo.
Inclui fallback de performance e finalização parcial com CT-e omitido: ambos reproduziram
perda de informação na primeira candidata e passaram após restringir a exceção.
Antes da V099, o caso `done` falhou por `STALE_NO_OP`; ao final, os 16 casos passaram.
Isso comprova a regra na camada SQL local, não uma resposta atual da ESL nem execução da V1.

O produtor e o leitor do pacote agora exigem as 99 migrations, incluindo V099. O leitor do JAR
rejeita índice incompleto mesmo com hashes recalculados. O validador de envelopes PowerShell
mantém a leitura dos arquivos históricos de versão 98, sem promover esses arquivos para a
revisão atual. Os metadados físicos continuam v098: esta correção não altera colunas.

O [validador do catálogo](../../../scripts/validation/Test-EntityAlignmentCatalog.ps1) confere
os 11 vínculos, os filtros com o enum usado pelo runtime e os 2.437 IDs sem reclassificação.
Resultados de build, regressões, revisão, diffs e pacote constam da continuidade desta revisão.

A [matriz A–N](../macrobloco-integracao-funcional/MATRIZ-A-N.md) e a
[matriz das 45 unidades](../macrobloco-integracao-funcional/MATRIZ-45-UNIDADES.md) conservam
a fotografia e as provas da entrega anterior. Esta revisão acrescenta correção de Fretes,
V099 ao schema, compatibilidade do novo pacote e documentação rastreável das entidades.
A referência V001–V098 da unidade V2-019 descreve aquela fotografia; a revisão atual inclui
V099, sem alteração de colunas. Construção **39/45** e aceites **67/115** permanecem iguais;
esses ajustes não são novas unidades concluídas nem novos aceites da fonte.
