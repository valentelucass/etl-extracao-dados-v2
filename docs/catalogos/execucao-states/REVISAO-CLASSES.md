# Revisão de classes

Estado: revisão Java, regressão global e bytecode finais conferidos. Não houve revisão humana nem segundo agente.

A análise lexical remove comentários e strings e confere consumidores de produção/teste. Ela orienta a revisão; zero referência sozinho não determina remoção. Nenhuma classe de produção foi criada nesta execução. Cinco arquivos de produção existentes foram corrigidos, com consumidores reais. Os três suportes Raster já movidos para testes e as nove remoções anteriores permanecem preservados.

| Classe | Decisão | Consumidores diretos main/test |
| --- | --- | --- |
| LocalColetasTemporalRuntime | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 0/2 |
| ColetaDataExportPageRequestFactory | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| ColetaGraphQlTemporalMapper | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 2/3 |
| ColetaTemporalReferenceStore | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 3/2 |
| ExtrairPaginaColetasDataExport | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| ExtrairReferenciasTemporaisColetas | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 1/1 |
| LigarReferenciaTemporalColeta | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 0/2 |
| PersistirReferenciasTemporaisColetas | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 1/2 |
| ColetaAbsencePolicy | Preservado como modelo puro do contrato. O consumidor de massa permanece SQL; referências de teste indicam a regra isolada, não alcance operacional. Não foi demonstrado defeito que exija fundir a política ao runtime. | 0/1 |
| ColetaTemporalIdentityBinding | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 4/5 |
| ColetaTemporalObservation | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 5/4 |
| ExtrairPaginaFretesDataExport | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| FreteDataExportPageRequestFactory | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| ExtrairPaginaLocalizacaoCargaDataExport | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| LocalizacaoCargaDataExportGateway | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| LocalizacaoCargaDataExportPageRequest | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| LocalizacaoCargaShadowCapability | Preservado como modelo puro do contrato. O consumidor de massa permanece SQL; referências de teste indicam a regra isolada, não alcance operacional. Não foi demonstrado defeito que exija fundir a política ao runtime. | 0/1 |
| LocalizacaoCargaAbsencePolicy | Preservado explicitamente como documentação executável pura de ABSENT/NULL/VALUE e bloqueio de completude. Zero consumidor de produção/teste encontrado; não é alegado como comportamento integrado. O contrato segue exercitado no mapper/SQL existentes. Não há regra nova, uso forçado ou quota de remoção. | 0/0 |
| LocalizacaoCargaFieldReducer | Preservado como modelo puro do contrato. O consumidor de massa permanece SQL; referências de teste indicam a regra isolada, não alcance operacional. Não foi demonstrado defeito que exija fundir a política ao runtime. | 0/1 |
| LocalizacaoCargaFreshnessPolicy | Preservado como modelo puro do contrato. O consumidor de massa permanece SQL; referências de teste indicam a regra isolada, não alcance operacional. Não foi demonstrado defeito que exija fundir a política ao runtime. | 0/1 |
| ExtrairPaginaManifestosDataExport | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| ManifestoDataExportGateway | Remoção anterior preservada; tipo substituído antes de0154. Nenhuma nova remoção contada. | removida no predecessor |
| ManifestoReductionResult | Preservado como modelo puro do contrato. O consumidor de massa permanece SQL; referências de teste indicam a regra isolada, não alcance operacional. Não foi demonstrado defeito que exija fundir a política ao runtime. | 1/1 |
| ManifestoRootReducer | Preservado como modelo puro do contrato. O consumidor de massa permanece SQL; referências de teste indicam a regra isolada, não alcance operacional. Não foi demonstrado defeito que exija fundir a política ao runtime. | 0/1 |
| DataExport422ErrorCategory | Preservado como suporte de classificação/perfil do contrato, com regressão unitária existente. Não apresentado como cliente HTTP da cadeia nem ampliado para rede. | 1/1 |
| DataExport422FailureClassifier | Preservado como suporte de classificação/perfil do contrato, com regressão unitária existente. Não apresentado como cliente HTTP da cadeia nem ampliado para rede. | 0/1 |
| DataExportPayloadFieldProfile | Preservado como suporte de classificação/perfil do contrato, com regressão unitária existente. Não apresentado como cliente HTTP da cadeia nem ampliado para rede. | 1/2 |
| DataExportPayloadProfile | Preservado como suporte de classificação/perfil do contrato, com regressão unitária existente. Não apresentado como cliente HTTP da cadeia nem ampliado para rede. | 0/3 |
| GraphQlColetasTemporalContractCatalog | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 0/1 |
| RasterHttpBodyHandler | Movimento byte-idêntico para src/test já entregue no0162 e preservado nesta revisão: o único caminho consumidor é AnalyticLaboratoryRasterTransportTest. Os três tipos referenciam-se entre si; nenhum entry point de produção foi removido. Scanner e contraprova acompanham o caminho exato. | suporte em src/test |
| RasterLoopbackTransport | Movimento byte-idêntico para src/test já entregue no0162 e preservado nesta revisão: o único caminho consumidor é AnalyticLaboratoryRasterTransportTest. Os três tipos referenciam-se entre si; nenhum entry point de produção foi removido. Scanner e contraprova acompanham o caminho exato. | suporte em src/test |
| RasterTransportConfiguration | Movimento byte-idêntico para src/test já entregue no0162 e preservado nesta revisão: o único caminho consumidor é AnalyticLaboratoryRasterTransportTest. Os três tipos referenciam-se entre si; nenhum entry point de produção foi removido. Scanner e contraprova acompanham o caminho exato. | suporte em src/test |
| IdentityConflictPolicy | Preservado como política pura de conflito de identidade, exercitada isoladamente. Identidade integral continua explícita/type-tagged; a revisão não inventa um consumidor operacional. | 0/1 |
| ExecutionMetricsAccumulator | Preservado como biblioteca de telemetria e sanitização com testes existentes. Não conectado artificialmente à cadeia para justificar sua existência. | 0/1 |
| SafeThrowableSummary | Preservado como biblioteca de telemetria e sanitização com testes existentes. Não conectado artificialmente à cadeia para justificar sua existência. | 0/1 |
| SensitiveValueRedactor | Preservado como biblioteca de telemetria e sanitização com testes existentes. Não conectado artificialmente à cadeia para justificar sua existência. | 0/1 |
| RuntimeExecutionControl | Preservado como contrato de controle de execução testado isoladamente. Não criado wrapper equivalente no caminho novo. | 0/1 |
| JdbcColetaTemporalLaboratory | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 1/2 |
| JdbcSqlServerColetaTemporalGateway | Preservado como biblioteca de integração temporal exercitada por ColetasTemporalLocalIntegrationIT e sua fixture física. Sem novo wiring no JAR integral; não substituir a prova temporal pela projeção analítica. | 1/2 |
| ShadowAuditFactory | Preservado como factory de auditoria testada; o caminho integral usa os gateways JDBC existentes. Sem expansão de responsabilidades. | 0/1 |
| JdbcSqlServerStagingPromotionKernel | Preservado como kernel de integração limitado e seus tipos, consumidor de prova física existente. A cadeia usa staging específico por entidade e TVPs/set-based; não migra massa para uma estrutura Java genérica. | 0/1 |
| StagingBatch | Preservado como kernel de integração limitado e seus tipos, consumidor de prova física existente. A cadeia usa staging específico por entidade e TVPs/set-based; não migra massa para uma estrutura Java genérica. | 2/3 |
| StagingDisposition | Preservado como kernel de integração limitado e seus tipos, consumidor de prova física existente. A cadeia usa staging específico por entidade e TVPs/set-based; não migra massa para uma estrutura Java genérica. | 1/2 |
| StagingPromotionKernel | Preservado como kernel de integração limitado e seus tipos, consumidor de prova física existente. A cadeia usa staging específico por entidade e TVPs/set-based; não migra massa para uma estrutura Java genérica. | 1/1 |
| StagingRecord | Preservado como kernel de integração limitado e seus tipos, consumidor de prova física existente. A cadeia usa staging específico por entidade e TVPs/set-based; não migra massa para uma estrutura Java genérica. | 2/2 |
| CheckpointDecisionPolicy | Preservado como regra pura de avanço de checkpoint, testada isoladamente; leases e publicação integrais passam pelo control plane SQL existente. | 0/1 |

## Tipos novos

Nenhum. Os caminhos e hashes dos cinco arquivos de produção alterados e dos testes constam na prova de bytecode.

Caminhos, hashes e listas integrais: [revisao-classes.json](revisao-classes.json). O levantamento privado está em target/execucao-states-20260915-01/consumer-review-03/. A presença de uma política pura preservada não constitui aceite operacional nem nova contagem de construção.
