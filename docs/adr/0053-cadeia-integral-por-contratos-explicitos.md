# ADR 0053 — Cadeia integral com entradas e contexto explícitos

Estado: decisão técnica local adotada no pedido A–N de 14/09/2026. Implementação e
provas locais concluídas; o selo externo vincula a revisão final e seu readback. Não concede
aceite nominal, credencial, autoridade de referência ou liberação operacional.

## Problema

O modo por artefatos anterior recebia quatro expansões e Raster, mantendo seis
fontes e apoios de laboratório internos. A janela, algumas revisões e a linhagem
de Coletas ainda pressupunham valores fixos. Isso impedia executar pelo mesmo
consumidor uma cadeia completamente fornecida pelo chamador e conferir sua
propagação até fatos e SQL em períodos e identidades diferentes.

## Decisão

`local-artifact-scenario-v2` exige onze entradas, relações, cinco conjuntos de
referências, nove grupos de suplementos, sweep, janela, origem/tenant, revisão,
relógio e política fiscal. `DeclaredIntegralInputs` valida o conjunto antes de
SQL; `PinnedLocalJson.consume` vincula a validação aos bytes efetivamente lidos.
Ausência de entrada recusa o modo completo. O modo v1 preserva sua declaração
anterior de apoios empacotados e seus consumidores.

Os adapters existentes recebem `RelationalCaptureSource`,
`AnalyticQuotesCaptureSource` e `AnalyticUsersCaptureSource`. Os seis arquivos
novos passam pelos mesmos parsers, gates de contrato, auditoria, staging,
control plane, DQ e reconciliação. `AnalyticScenarioRuntime` continua compondo
as onze famílias e cinco materializações. Nenhum supervisor ou pipeline paralelo
foi criado. A V1 permanece referência somente de leitura.

Fretes declara uma única release para seus dois consumidores. A V101 admite
essa release na captura relacional somente quando seu fingerprint está associado
ao mesmo cenário. A V100 liga os namespaces sintéticos ao run e às execuções
relacionadas; não muda os critérios financeiros, fiscais ou de identidade.
O registro dos protocolos e o fingerprint DQ usam a origem/tenant explícitos.

`DeclaredSqlOracles` fornece tuplas independentes e tipadas para 19 saídas;
`LocalFactOracle` confere os cinco grãos por conjuntos no SQL. IDs de domínio,
valores, datas e presença são esperados explícitos. As exceções técnicas são
fechadas: UUID da execução, timestamps observados dentro do intervalo e linhagem
conferida por campo. No replay, os 20 papéis de monitoramento são vinculados a
cada ciclo, e a contagem do evento SCENARIO inclui os eventos anteriores que
participaram de SQL10. Não se deriva esperado de consulta de produção.

`CollectionSweepArtifact` v2 informa um universo limitado de chaves e contagens.
O overload existente de sweep executa quatro travessias, valida o owner ROOT/filho
e chama o mesmo kernel e catálogo de 33 responsabilidades. A V102 confere cada
raiz/contagem contra staging, além das auditorias e páginas, e sela o JSON do
universo no recibo imutável. Para esse universo declarado, Java e SQL recusam
apply de ausência. O preview mantém a aplicabilidade e as pendências de cada
responsabilidade. O comportamento histórico V092 permanece separado por contrato.

## Regras locais e provas

| Regra | Origem e exemplo | Consumidor/prova |
| --- | --- | --- |
| INT-01 | Pedido B/C: faltar USER ou referência impede modo completo. | DeclaredIntegralInputs; IntegralArtifactAdmissionTest/BoundaryTest. |
| INT-02 | Pedido D/E: mesma origem/release FRE chega aos dois caminhos. | Capturas integrais, V100/V101; IntegralArtifactScenarioIT. |
| INT-03 | Pedido F/G: total declarado não equivale a freight_value. | SQL02 e fatos independentes; conjuntos A/B. |
| INT-04 | Pedido G: chave, valor, cardinalidade ou último decimal errado nunca passam. | IntegralOracleRejectionIT. |
| INT-05 | Pedido H: terminal vazio sozinho não prova universo completo. | V102; IntegralSweepEdgesIT, página perdida e contagens redistribuídas. |
| INT-06 | Pedido I: revisão da operação de replay não substitui a revisão da fonte. | AnalyticScenarioRuntime/Raster; IntegralArtifactReplayIT. |
| INT-07 | Pedido D/E e AGENTS: apoios usam lotes de até 16, lookup SQL do conjunto e gravações tipadas; chaves desconhecidas não chegam aos fatos. | DeclaredAnalyticSupport; IntegralArtifactScenarioIT/IntegralSupportFailureIT. |
| INT-08 | Pedido B/I: falhas do modo integral devem operar sobre entradas declaradas; flags que usavam fixtures internas são recusadas antes de SQL. | AnalyticScenarioRuntime; IntegralDependencyFailureIT. |

São regras de admissão/composição/prova local sustentadas pelo pedido e contratos
existentes. Responsável técnico nesta execução: agente implementador. Não há
responsável de negócio inventado ou nova regra nominal de transporte.

## Limites e recuperação

Tetos de páginas, bytes, linhas, statements, JVM e pacote permanecem explícitos.
As duas escalas físicas usam 2 e 24 raízes; não qualificam volume produtivo.
O pacote do runtime conserva seu limite agregado. Os exemplos completos são
um complemento pinado da distribuição, consumido por `local-scenario` do JAR
extraído, sem depender de JUnit ou workspace para executar.

Migrations instaladas não são reescritas. Correção posterior exige outra versão;
provas de domínio revertem a transação. Código conserva a fotografia anterior
para diff/overlay e aplicação inversa verificada. V099, 39/45, 67/115, V2-041 e
as parcelas externas G01–G08 permanecem preservados.
