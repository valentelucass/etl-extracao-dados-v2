# ADR 0011 — Resiliência ESL e política de falhas compartilhada

- Status: Aceito e implementado offline por V2-043/V2-024; composição no runtime pertence a V2-022
- Data: 2026-08-30

## Contexto

As verticais ESL compartilham a mesma origem e podem receber throttling mesmo quando usam
templates diferentes. Limites, retries e circuit breakers criados por cliente isolado permitiriam
somar quotas, formar tempestade de retry e avançar estado depois de uma extração parcial. O legado
comprova que retry limitado, streaming de página e cancelamento cooperativo são úteis, mas seus
retries aninhados, breakers não coordenados e classificação de erro por texto não são contratos a
preservar.

V2-043 precisa fechar essa fundação sem credencial ou chamada externa. A composição do
orquestrador, os tradutores de janela por vertical e o uso operacional continuam em tarefas
posteriores.

## Decisão

Existe um único `EslRequestGovernor` por origem ESL. Cada ciclo cria escopos canônicos de workload,
mas todos consomem o mesmo semáforo justo, com exatamente uma requisição em voo, intervalo mínimo e
embargo global após rate limit. Budgets por origem e workload são atômicos, explícitos e não podem
ser reiniciados obtendo novamente o mesmo escopo. A configuração é tipada, obrigatória quando Data
Export ou GraphQL estiver habilitado e recusa limites inconsistentes. Com os dois transportes
ativos, instância, tenant e policy ESL precisam ser idênticos.

`DataExportHttpGatewayFactory` é o componente de transporte source-scoped: reutiliza `HttpClient`,
`ObjectMapper` e registry de circuitos. `forWorkload` recebe um ciclo do governor fornecido pela
composição, cria um único adapter do workload e o compartilha no bundle `/info` + `/data`. V2-022
deve criar o único governor da origem e fornecer seus ciclos a essa fábrica; a fábrica isoladamente
não impõe singleton. O circuit breaker é isolado por template, compartilhado entre `/info` e
`/data`, possui cooldown e permite uma única sonda half-open. Construtores diretos dos gateways
permanecem apenas para as sondas de contrato e compatibilidade local; não são a raiz produtiva.

`GraphQlHttpGatewayFactory` segue a mesma fronteira source-scoped para os três documentos estáticos
de V2-024. Ela recebe a instância exata do governor na construção e recusa cycle, policy ou
capability de cancelamento divergente antes de criar workload ou abrir I/O. O token é materializado
somente após validar a policy do governor. Retry, embargo e budgets GraphQL, portanto, não formam uma
quota paralela à origem ESL. Cancelamento, budget local, timeout de passo/ciclo, falha interna
sanitizada ou probe abandonada não contam como resposta alcançável nem fecham o circuito.

Deadlines usam relógio monotônico e a hierarquia ciclo → passo → request. Cada espera reamostra
cancelamento e tempo restante; request recebe o menor limite aplicável. O transporte usa
`sendAsync`, cancela o future em timeout/cancelamento e recebe o corpo com demanda limitada e teto
de bytes. O permit é liberado em todos os caminhos. Não é criado executor ou thread por request.

Retry ocorre somente em chamadas idempotentes previstas pelo gateway, com número de tentativas,
backoff exponencial limitado e jitter limitado. `429` e `5xx` retryable usam `Retry-After` delta ou
RFC 1123 quando válido; ausência ou formato inválido usam backoff. No caminho governado ou quando
há outra tentativa, valor válido acima do teto local falha de forma tipada e sanitizada. No governor
compartilhado, um `Retry-After` aceito também impõe embargo global, inclusive na resposta terminal,
impedindo que outra vertical contorne o rate limit. Probes de contrato continuam com uma tentativa,
apenas observam a resposta terminal e param no primeiro `429`, sem adotar retry/embargo do runtime.

A decisão operacional usa taxonomia fechada, nunca mensagem de exceção:

| Situação | Ação/estado | Categoria de saída |
|---|---|---|
| configuração, identidade ou autorização | `ABORT/FAILED` | `CONFIG_AUTH` (20) |
| lock indisponível | `ABORT/FAILED` | `LOCK` (30) |
| schema, drift, SQL, DQ crítico, budget/circuito esgotado ou falha permanente | `ABORT/FAILED` | `SOURCE_DQ` (40) |
| rate limit, timeout de request, `5xx` ou origem transitória | `RETRY` enquanto houver budget; depois `ABORT` | sucesso transitório; depois `SOURCE_DQ` |
| dependência obrigatória falhou | `BLOCK/BLOCKED` | `SOURCE_DQ` |
| dependência/fonte opcional falhou | `DEGRADE/DEGRADED` | `DEGRADED` (10) |
| fonte desabilitada | `SKIP/NOT_APPLICABLE` | `SUCCESS` (0) |
| observabilidade não crítica | `CONTINUE_WITH_ALERT` | `DEGRADED` |
| cancelamento | `ABORT/CANCELLED` | `CANCELLED` (50) |

O código de saída agregado conserva a causa de maior severidade causal; cancelamentos derivados não
mascaram a falha raiz. Publicação independente já confirmada permanece contabilizada. Checkpoint de
uma entidade só avança após `PUBLISHED`, extração completa, reconciliação positiva e ausência de
falha bloqueante não resolvida.

Falha de sink de log, métrica ou alerta não converte falha SQL/DQ em sucesso nem reduz sua severidade.
Quando a observabilidade for realmente não crítica, o evento tipado registra `DEGRADED`; quando ela
for parte da prova obrigatória, a indisponibilidade continua fail-closed.

HTTP 422 só produz `REPARTITION` quando uma categoria estruturada e sanitizada informa
`WINDOW_TOO_LARGE`. Texto livre, corpo ausente ou outra categoria são 422 não classificados e
terminais. O reparticionador aceita somente unidade explícita, produz duas janelas contíguas menores
e possui budget atômico; atingir a menor unidade ou o teto falha fechado. O tradutor de cada fonte
ainda deve provar timezone, precisão e bordas antes de usar essa divisão no runtime.

## Consequências

- Clientes e templates não criam quota paralela nem circuito global que derrube vertical saudável.
- A memória do transporte permanece limitada a uma resposta e a agregação de resultado é `O(1)`.
- Timeout e cancelamento interrompem corpo pendente, liberam conexão/permit e impedem retry tardio.
- Entidade parcial não publica checkpoint; falha independente não desfaz entidade já publicada.
- A fundação é comprovável por servidor loopback e relógios/fixtures sintéticos, sem alegar quota,
  terminalidade ou semântica de 422 do fornecedor.
- V2-041 continua bloqueando credenciais, sondas externas, release, deploy e cutover; este ADR não
  altera esse estado.

## Alternativas rejeitadas

- **Limiter/budget por cliente ou template:** soma concorrência e permite tempestade entre
  verticais.
- **Retry aninhado no orquestrador e no gateway:** multiplica tentativas e torna o budget opaco.
- **Classificar 422 por texto:** idioma e mensagem não constituem contrato confiável.
- **Acumular respostas ou execução inteira para cancelar depois:** viola memória limitada e mantém
  recursos abertos.
- **Avançar checkpoint após página parcial ou falha degradada:** transforma incompletude em estado
  publicado.
