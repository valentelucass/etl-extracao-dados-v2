# Catálogo da fundação de medição V2-050

Este catálogo governa somente `Q-MED-FND-01`, Bloco 50 e
`V2-050/FUNDACAO_MEDICAO_LOCAL`. O resultado máximo desta fatia é
`FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED`.

## Fronteira da evidência

A fixture `SYNTHETIC_FIXTURE` descreve exatamente seis cenários locais: `DataExportPageStreamer`
e `GraphQlPageStreamer`, cada um com 16, 256 e 4096 páginas de dados e oito registros por
página. Cada cenário usa contexto, gateway, gauge, consumer e fixture novos. Os gateways criam
uma página por fetch; nenhum universo de páginas ou registros é pré-construído.

O Data Export mede também a página terminal vazia. Portanto, para `N` páginas de dados,
`fetchedPages`, `acquisitions` e `releases` valem `N + 1`, enquanto `consumedPages` vale `N`.
No GraphQL a última página não vazia declara `hasNextPage=false`, de modo que essas quatro
contagens valem `N`. Ambos contam `N * 8` registros.

## Gate estrutural

O `ManagedPageGauge` separa páginas em voo de páginas retidas durante a execução. Um cenário
saudável exige pico em voo exatamente igual a um, estado final zero e reconciliação exata entre
fetches, acquisitions e releases. O resultado positivo é
`MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY`.

`ExecutionWidePageRetentionMutant` mantém os objetos reais das páginas em um
`ArrayList<Object>` mesmo depois da liberação do in-flight. O evaluator genérico não conhece o
nome do mutante: rejeita qualquer evidência com retenção positiva usando
`EXECUTION_WIDE_PAGE_RETENTION_DETECTED`. O catálogo registra esse controle como
`LEAK_MUTANT_REJECTED`.

Heap e duração ficam separados em `MeasurementDiagnostics`. São evidência
`JVM_HEAP_DIAGNOSTIC_ONLY`: podem variar entre execuções e nunca participam da decisão
estrutural. Consequentemente, esta fundação declara explicitamente:

- `ENTITY_HEAP_PLATEAU_NOT_PROVEN`
- `ENTITY_SQL_PLAN_NOT_EXECUTED`
- `ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN`
- `ENTITY_V2_050_GATE=OPEN`

O detector Bloom de ciclo usado pelo `GraphQlPageStreamer` tem armazenamento fixo de 1 MiB e
não cresce com a quantidade de páginas. Isso é uma propriedade de limite local, não uma prova
de escala, heap ou completude produtiva.

## Receipt

O runner grava somente `target/v2-050-measurement/measurement-foundation-receipt.json`. A
escrita usa UTF-8 sem BOM, LF explícito, arquivo temporário privado e publicação atômica. Paths
fora de `target`, traversal, reparse points, symlinks, nomes não permitidos e resíduos são
recusados. A ordem é determinística para o mesmo objeto; heap e duração reais são deliberadamente
variáveis e, por isso, não há promessa de hash idêntico entre execuções diferentes.

O receipt contém somente metadados sintéticos e métricas agregadas. IDs de negócio, cursores,
URLs, payloads, segredos e dados de fonte são proibidos.
