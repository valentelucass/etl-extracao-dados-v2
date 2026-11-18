# Organização para implementar as entidades em Java

## Padrão das nove entidades existentes

O catálogo `DataExportTemplate` possui nove entradas. Os extratores de cada módulo usam `DataExportStagingPipeline`, com `DataExportPageStreamer`, `ContractRunGuard`, limites e cancelamento. As cinco entidades base usam registros de estágio próprios. As quatro expansões usam DTO de fronteira, mapper tipado, `ExpansionObservation` e `ExpansionCaptured`, preservando o envelope. Os caminhos independentes de qualificação com Excel têm requisição, transporte, captura finita, diagnóstico e comparação próprios. A presença desses componentes não prova adoção produtiva de todo o Main.

| Entidade | Template | Pasta do módulo | Extrator existente |
| --- | --- | --- | --- |
| Coletas | 6908 | `modulos/coletas` | `ExtrairColetasDataExport` |
| Fretes | 6389 | `modulos/fretes` | `ExtrairFretesDataExport` |
| Manifestos | 6399 | `modulos/manifestos` | `ExtrairManifestosDataExport` |
| Cotações | 6906 | `modulos/cotacoes` | `ExtrairCotacoesDataExport` |
| Localizador | 8656 | `modulos/localizacaocargas` | `ExtrairLocalizacaoCargasDataExport` |
| Contas a Pagar | 8636 | `modulos/contasapagar` | `ExtrairContaPagarDataExport` |
| Faturas por Cliente | 4924 | `modulos/faturasporcliente` | `ExtrairFaturaClienteDataExport` |
| Inventário | 10633 | `modulos/inventario` | `ExtrairInventarioDataExport` |
| Sinistros | 6392 | `modulos/sinistros` | `ExtrairSinistroDataExport` |

Os caminhos da tabela são relativos a `src/main/java/br/com/esl/etl/v2`. Não copiar o contrato genérico sintético no lugar dos contratos independentes já qualificados. Em particular, os históricos de filtros adicionais, arrays e campos longos de CAP/Faturas/Sinistros têm decisões próprias.

## Arquivos propostos por entidade

Estes nomes organizam a implementação futura; os arquivos Java ainda não foram criados.

| Camada | Consolidação | Ocorrências |
| --- | --- | --- |
| DTO de fronteira | `modulos/consolidacoes/aplicacao/ConsolidacaoDataExportDto.java` | `modulos/ocorrencias/aplicacao/OcorrenciaDataExportDto.java` |
| Mapeamento estrito | `modulos/consolidacoes/aplicacao/ConsolidacaoDataExportMapper.java` | `modulos/ocorrencias/aplicacao/OcorrenciaDataExportMapper.java` |
| Observação tipada | `modulos/consolidacoes/domain/ConsolidacaoObservation.java` | `modulos/ocorrencias/domain/OcorrenciaObservation.java` |
| Regras do domínio | `modulos/consolidacoes/domain/ConsolidacaoRules.java` | `modulos/ocorrencias/domain/OcorrenciaRules.java` |
| Extração com pipeline | `modulos/consolidacoes/aplicacao/ExtrairConsolidacaoDataExport.java` | `modulos/ocorrencias/aplicacao/ExtrairOcorrenciaDataExport.java` |
| Requisição qualificada | `plataforma/reconciliacao/exportacao/ConsolidacoesIndependentRequest.java` | `plataforma/reconciliacao/exportacao/OcorrenciasIndependentRequest.java` |
| Captura e transporte | `ConsolidacoesTransport`, `ConsolidacoesIntervalCapture`, `ConsolidacoesSourceCaptureMain` | `OcorrenciasTransport`, `OcorrenciasIntervalCapture`, `OcorrenciasSourceCaptureMain` |
| Comparação independente | Adapter/diagnóstico específico e referência Python própria | Adapter/diagnóstico específico e referência Python própria |
| Recursos | `src/main/resources/contracts/dataexport/consolidacoes-11067-...json` e catálogo de apresentação correspondente | `src/main/resources/contracts/dataexport/ocorrencias-12072-...json` e catálogo de apresentação correspondente |
| Testes | Espelho em `src/test/java` e fixtures sintéticas sem negócio real | Espelho em `src/test/java` e fixtures sintéticas sem negócio real |

Preservar o namespace da captura, reportId, tenant/origem, filtro, fuso, período, versão de contrato e request fingerprint. Validar antes de qualquer chamada externa. Plano de requisição e flag de teste não concedem autorização física.

## Adaptações compartilhadas a revisar

1. `plataforma/fonte/dataexport/DataExportTemplate.java`, `DataExportPageRequest`, `DataExportQueryEncoder`, parser INFO, validação de paginação e limites. Não herdar ordenação ascendente ou campo de entidade de outro relatório. Default 11067 é`issue_date`, sem direção explícita;12072 não declara default. Até haver contrato estável, a captura deve recusar falsa completude em caso de mudança ou limite.
2. Se adotado o caminho de expansão, revisar `ObservedExpansionCaptureTemplate`, `ObservedExpansionSchemaCatalog`, `ObservedExpansionSchemaPins`, `ExpansionLocalContract`, `ExpansionObservedPageMapper`, `DataExportObservedPage` e os recursos/pins correspondentes. O catálogo observado atual declara quatro perfis; adicionar só ao enum geral não integra essas entradas.
3. `ExpansionFieldParser` e `ExpansionValue` preservam presença/wire/raw. Textos têm limite1024 UTF-16, raw 4000; inteiros até 38 dígitos; decimais20 inteiros/8 casas; listas32 itens/256 UTF-16 por item. São limites internos, não garantias da ESL. Os cortes novos cabem nos tipos observados, mas campos sempre NULL e objetos precisam de contrato específico. Não ampliar globalmente limites para acomodar suposição.
4. `charged_value_details` exige tratamento próprio de objeto com chaves dinâmicas e BigDecimal. `ctd_fgs_invoices_mapping` preserva lista; sua concatenação é exclusiva da projeção Excel. Não usar o limite 110 de listas de Faturas por analogia.
5. Bootstrap/composição: conferir `RuntimeCompositionRoot`, registro de workloads, `LocalExpansionRuntime`, `ExpansionCharacterizer`, cenários e switches exaustivos. Preservar guardas e defaults de laboratório; não tornar rotas operacionais por simples inclusão de enum.
6. Persistência de laboratório: `plataforma/persistencia/expansao/JdbcExpansionLaboratory`, `JdbcExpansionStaging`, relações/recomposição e controles têm contratos próprios. Entidades novas precisam de staging tipado e tabelas filhas, chaves físicas, constraints e baseline correspondentes. Não reutilizar vertical SIN para Ocorrências.
7. Recebimento produtivo: `plataforma/producao/ProductionReceivingMain.java` monta Faturas 4924. `database/production/receiving/001_receiving.sql` exige `entity_name='FATURAS_POR_CLIENTE'`;002 também valida entidade/template4924 e data própria. Uma evolução deve ser aditiva, versionada e qualificada; não editar001/002 aplicados nem atualizar hashes do runtime rev2 para aceitar drift.
8. Publicação: separar fronteira STORED da fronteira PUBLISHED, preservar último conjunto válido e provar domínio/referências antes de core/mart/pub. Dashboard mantém V1; sinônimos, API/DTOs e KPIs só mudam em etapa própria. Não criar views vazias para aparentar prontidão.

## Relações candidatas com as entidades atuais

| Relação | Campos candidatos | Condição antes de usar |
| --- | --- | --- |
| Consolidação para Fretes | `ctd_fgs_corporation_sequence_number`, CT-e e filial | Provar identidade no escopo, cardinalidade e possível expansão por frete/documento. |
| Ocorrência para Fretes | minuta, CT-e, NF | Mesmo frete pode ter vários eventos e notas. Join não pode multiplicar totais do frete. |
| Ocorrência para Manifestos | filtro `scopes[from_manifest_sequence_code]` | O relatório não retorna esse campo como coluna. Filtro disponível não equivale a FK ou dimensão publicada. |
| Ocorrência para Consolidação | filtro `scopes[from_consolidation_document_number]` | Não há número de consolidação entre as 20 colunas. Qualificar associação na origem antes de criar vínculo. |
| Localização e datas relacionadas | campos `ice_fit_*`, `ctd_fgs_*` e Localizador | São snapshots relacionados; sem prova de equivalência temporal não sobrescrever histórico. |
| Fatura financeira da consolidação | `ctd_ant_document`, transportadora | SomenteNULL no corte. Não relacionar a Faturas por Cliente por coincidência do nome. |

As nove fontes atuais e as duas novas somam onze relatórios Data Export propostos. Isso não comprova que todo o sistema esteja coberto: Users/GraphQL e Raster são apoios com contratos próprios, e a suficiência funcional depende dos consumidores desejados.

## Sequência de implementação

1. Completar as provas de tipos não nulos, identidade, paginação e filtros pendentes; congelar os contratos de origem e apresentação por pin.
2. Implementar DTO/observação/mapper/extrator e requisição/captura independentes, com fixtures causais e regressão das nove entidades. Confrontar Java e referência independente contra os mesmos arquivos oficiais.
3. Planejar migrations/baseline próprios sem ocupar número de migration por suposição; integrar recebimento, readback, recuperação, publicação e operação somente após qualificação de cada fronteira. Atualizar catálogo, STATES e RETOMADA com o estado observado.
