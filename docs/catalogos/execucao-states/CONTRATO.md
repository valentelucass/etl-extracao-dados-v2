# Critérios aplicados nesta execução

A ordem adotada é `target/preparacao-execucao-states-20260915-01/PROMPT-EXECUCAO-STATES.md`.
STATES, ADR0049/0050 e o [contrato integral anterior](../cadeia-integral-por-contratos/CONTRATO.md)
definem os comportamentos locais. Não foi recebida nova ratificação externa.
A condição anterior de terminar na admissão nominal foi revogada nesta ordem.

## Correções contra critérios existentes

- EXP-04 e V2-021: decimais locais `DECIMAL(28,8)`, sem arredondamento.
  Número JSON é convertido com BigDecimal exato; string conserva a gramática
  decimal estrita. Escala maior que8 e mais de20dígitos integrais são recusados
  antes de normalizar a escala, inclusive expoentes extremos.
- Preflight e consumo usam os mesmos bytes e preservam a escala decimal.
  A representação interna exponencial de `0.00000001` não torna inválido o número.
- V2-009/V2-030: chaves STRING admitem separadores. Raiz, parcela e componente
  fiscal permanecem três componentes distintos, inclusive quando sua concatenação
  textual seria igual. O preflight usa um array JSON para preservar os limites
  da tupla; duplicata exata continua recusada. Não muda identidade de negócio,
  binding, schema, série nominal ou contrato de fornecedor.
- V2-050: a validação dos suplementos conserva até64chaves de um lote e um
  filtro fixo de128KiB por grupo. Relê lotes anteriores quando necessário,
  com conferência de hashes e comparação exata das chaves para detectar duplicatas,
  sem escrever arquivos ou usar SQL no preflight. Limite existente:128lotes
  por grupo e131072bytes por lote. O filtro não é identidade nem decide recusa.
  Colisões apenas exigem comparação exata. O pior caso adversarial ainda pode
  exigir releitura quadrática; cancelamento permanece cooperativo. Os recibos
  medem o teto local, sem alegar escala produtiva.
- V2-043/V2-022: o cancelamento é conferido também dentro do lote e durante
  a releitura. Falha não recebe resultado de preflight concluído.

Os nove grupos de suplementos continuam distintos; escopo, cardinalidade,
revisão, identidade, moeda/unidade, papéis, vigência e política fiscal permanecem
explícitos. Não houve mudança de schema ou regra de negócio. Raster permanece
MANTER no escopo local e desabilitado por padrão, com identidade real pendente.

Modo integral exige todas as entradas, sem substituição por fixtures implícitas.
Sweep integral permanece preview-only:33responsabilidades e apply recusado.
As contagens39/45 e67/115 são históricas e permanecem idênticas.
