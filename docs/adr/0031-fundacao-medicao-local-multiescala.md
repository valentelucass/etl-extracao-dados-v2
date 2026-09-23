# ADR 0031 — Fundação local de medição multiescala

- Status: aceito somente para a fundação test-only do Bloco 50
- Decisão: `FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED`
- Escopo: `Q-MED-FND-01` / `V2-050/FUNDACAO_MEDICAO_LOCAL`

## Contexto

Uma paginação terminar corretamente não prova que a execução mantém somente uma página viva,
nem que memória, plano SQL, push-down ou SLO de uma entidade são adequados em produção. A
fundação precisa medir os dois streamers existentes sem rede, fonte, banco ou runtime e sem usar
oscilações da JVM como gate.

## Decisão

Os tipos de medição existem exclusivamente em `src/test`. Um plano fechado admite apenas
`DataExportPageStreamer` ou `GraphQlPageStreamer`, as escalas 16, 256 e 4096 e oito registros por
página. A execução coleta páginas buscadas/consumidas, registros, bytes, acquisitions, releases,
máximo/final em voo e máximo/final retido.

O gate estrutural exige pico em voo exatamente um. Um limite apenas menor ou igual a um
aceitaria um gauge morto e foi rejeitado. O gateway também recusa o próximo fetch enquanto a
página anterior não tiver sido liberada. Contagem em voo e retenção de execução são independentes.

O evaluator recebe somente `MeasurementEvidence`. Duração e amostras de heap vivem em
`MeasurementDiagnostics`, nunca determinam aprovação ou rejeição e são rotuladas
`JVM_HEAP_DIAGNOSTIC_ONLY`. Um mutante conserva os objetos reais em `ArrayList<Object>` e é
rejeitado genericamente por retained maior que zero com
`EXECUTION_WIDE_PAGE_RETENTION_DETECTED`; seu nome de classe não participa da decisão.

As integrações exercitam os loops produtivos reais. Data Export inclui a página terminal vazia;
GraphQL termina na última página não vazia e mantém o Bloom detector de ciclos em memória fixa de
1 MiB. A fixture e o receipt permanecem estritamente sintéticos.

## Consequências

- `MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY` vale somente para os seis cenários locais.
- `LEAK_MUTANT_REJECTED` prova que a métrica de retenção não é um alias do in-flight.
- `ENTITY_HEAP_PLATEAU_NOT_PROVEN` permanece verdadeiro.
- `ENTITY_SQL_PLAN_NOT_EXECUTED` permanece verdadeiro.
- `ENTITY_PUSH_DOWN_PLAN_NOT_PROVEN` permanece verdadeiro.
- `ENTITY_V2_050_GATE=OPEN` permanece aberto, assim como V2-050 pai, V2-038 e todas as rotas de
  entidade e saída.
- Não há alteração em `src/main`, runtime, `Main`, composition root, POM, workflow, banco ou SQL.
