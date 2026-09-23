# ADR 0022 — Runtime e orquestrador offline deny-all

- Status: Aceito para V2-022a; dispatcher operacional permanece em V2-022b
- Data: 2026-09-04
- Escopo: planejamento local, control plane e superfície CLI sem autorização positiva

## Contexto

O ADR 0007 fixou o JAR como CLI one-shot e reservou a concorrência, leases e recovery para o
control plane durável. O ADR 0010 fixou a fronteira de identidade do composition root como
deny-all até que provider, principals, mapeamentos e auditoria durável sejam aprovados. Faltava
materializar o contrato entre uma definição de workload, o plano determinístico e as ocorrências
`PLANNED` sem abrir uma rota que pudesse iniciar uma fonte ou uma carga de domínio.

## Decisão

O pacote `plataforma.orquestracao` recebe somente valores tipados e limitados. Uma definição exige
workload, família/instância da fonte, tenant, entidade, fingerprints de contrato e configuração,
lease e dependências; não há defaults de fonte, tenant, ambiente, janela, modo ou chave de
idempotência. O registry recusa chave duplicada, dependência ausente, auto-dependência e ciclo. O
plano ordena o DAG de modo estável e exige que toda dependência obrigatória esteja na mesma
solicitação. Assim, uma definição de Fretes pode depender de Coletas sem que o orquestrador
conheça ou infira nomes de verticais.

A partição usa explicitamente `[início, fim exclusivo)`, ambiente, escopo, entidade e modo. A
persistência recupera leases vencidas, abre o ciclo, registra cada fonte lógica uma vez e cria
somente ocorrências `PLANNED`. Heartbeat renova a lease; cancelamento antes do despacho aceita
somente `PLANNED -> CANCELLED` com reason code fechado. Esses serviços não possuem adaptador de
fonte, mapper, staging, promoção, migration, DDL ou executor de domínio.

`plan --config` limita-se ao preflight seguro. Os handlers `run`, `replay`, `sweep-preview`,
`sweep-apply`, `force-run` e `status` validam a configuração e atravessam a fronteira única de
autorização antes de qualquer despacho. No artefato desta decisão ela sempre nega; o retorno é a
categoria estável `CONFIG_AUTH` (código 20), sem propagar detalhes de configuração, identidade ou
auditoria. `--help`, `--version`, `config validate`, `dry-run` e `plan` permanecem locais e sem
efeito externo.

Não há loop, daemon, PID file, cron interno, credencial, rede, schedule, deploy, principal
positivo ou caminho para V2-022b. Cadência, blackout, fechamento mensal, catch-up e SLA são
obrigações explícitas do scheduler/supervisor externo e de uma futura configuração versionada;
este subgate não inventa seus valores, nem cria partições implícitas a partir do relógio local.

## Consequências

- Testes sintéticos cobrem ordenação, dependência faltante, ciclo, plano limitado, recovery,
  registro de fonte, ocorrência `PLANNED`, heartbeat, cancelamento e código de saída deny-all.
- V2-010 e as demais verticais podem compor seus DAGs e exercitar staging em harness de sombra,
  mas não podem executar operação real, rede, DDL, promoção, sweep ou cutover.
- O adaptador positivo, o consumo único da capability, a identidade externa e a auditoria durável
  continuam exclusivamente em V2-022b, V2-042b e V2-042c.
- O control plane SQL existente continua sendo a autoridade de concorrência quando um adaptador
  explicitamente injetado for permitido; a CLI deste subgate não o compõe nem abre conexão.

## Alternativas rejeitadas

- **Despachar após o preflight:** um arquivo válido não é autorização de runtime.
- **Aceitar papel, principal ou token por CLI/configuração/ambiente:** permitiria autoatribuição
  e violaria o ADR 0010.
- **Derivar janelas, catch-up ou fechamento mensal do relógio da máquina:** criaria gaps ou
  duplicações sem política versionada, zona, blackout e autoridade operacional aprovadas.
- **Embeddar scheduler ou daemon no JAR:** contradiz o modelo one-shot e multiplica estados de
  recovery fora do control plane.
