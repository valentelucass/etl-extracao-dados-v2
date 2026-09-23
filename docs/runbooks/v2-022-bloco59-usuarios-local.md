# B59 — integração local de Usuários/GraphQL

Contrato: [ADR 0040](../adr/0040-usuarios-graphql-no-runtime-operacional-local.md).
Regras current/history: [runbook de Usuários](usuarios-current-history-em-sombra.md).
Evidência e matriz A–F: [catálogo](../catalogos/bloco59-local/README.md).

## Entrada e composição

O request fechado contém exclusivamente strings:

| Campo | Semântica |
| --- | --- |
| protocol / operation | GRAPHQL / USERS_SNAPSHOT, literais |
| invocationId | Nova ocorrência de autorização; não muda o fingerprint do plano |
| executionId / cycleId / idempotencyKey | Identidade da execução/plano e idempotência |
| mode / replayOf | BACKFILL com replayOf vazio; REPLAY com origem distinta |
| start / endExclusive | Intervalo técnico não vazio, precisão de milissegundos; não filtra a fonte |
| leaseSeconds | Lease explícita, limitada pelo contrato comum |
| pageSize | Literal 20 |
| maximumPages / maximumNodes | Caps positivos do streamer; páginas não excedem o budget de workload |
| qualityVersion / qualityFingerprint | Política DQ vinculada ao scope e ao selo |
| compatibilityVersion | Versão da política contratual estrita, sem allowances |

Configuração GraphQL existente, namespace explícito, timezone IANA e destino
shadow local passam pelo preflight. Configuração Data Export não é necessária.
O arquivo é limitado a 16 KiB; duplicatas, tokens extras, campos inesperados,
valores numéricos JSON e protocolo/operação inválidos falham antes da autoridade.
Não editar nem consultar `.env` neste bloco. Não materializar segredo nos testes.

A CLI operacional existente chega a RuntimeCompositionRoot, autoridade Windows/SQL,
consumo único, RuntimeOperationalExecution, dispatcher e LocalUsuariosRuntime.
O handler usa os adapters reais sobre interfaces injetáveis. O harness positivo
substitui transporte e persistência por simuladores; conserva parser/gate,
mapper, auditoria, sessão, DQ e adapters JDBC reais. STATUS consulta apenas recovery;
diagnóstico e recusa do JAR não são um ensaio positivo com Windows/SQL reais.

O diagnóstico `RUNTIME_HTTP_ATTEMPTS protocol=GRAPHQL total=UNOBSERVED` declara
que o contador legado de Data Export não mede tentativas GraphQL. O governor
GraphQL mantém os tetos; páginas e nós continuam auditados. Não interpretar essa
mensagem como zero chamadas nem como telemetria física qualificada.

## Interrupção e recuperação

| Situação observada | Resposta local |
| --- | --- |
| Fonte/gate/parser/cursor/cap falha | Interromper; sem selo, prepare ou apply |
| Cancelamento ao mapear/datar lote, entre páginas ou antes de promover | Parar cooperativamente; sem selo/apply posterior |
| Auditoria/lease indisponível | Não concluir a travessia; preservar causa/resultado incerto quando pertinente |
| DQ reprovada, parcial ou de outro binding | Não emitir selo durável de Usuários nem apply |
| Confirmação perdida antes do selo | Leitura durável; sem repetição automática nem retomada de cursor |
| Confirmação do selo perdida | Leitura durável verifica selo/candidate/DQ antes de continuar |
| Confirmação de apply perdida | Recuperar somente recibo íntegro da mesma ocorrência; sem nova fonte/apply confirmado |
| Recibo ausente, adulterado, NULL ou estrangeiro | Não inferir ausência de commit ou sucesso; manter recusa/recuperação exigida |
| Replay explícito | Nova execution ligada à origem, novo staging, primeira página sem cursor |

Current/history, conflito, dedupe, hash/no-op e ordem total continuam nos entrypoints
SQL de V007. Não copiar seu algoritmo para JVM nem fazer DML manual.
hasNextPage=false é terminalidade local não verificada; nunca permite desativação.

## Validação local e próxima dependência física

O runner privado `target/bloco59-local/Invoke-Java.ps1` executa Maven offline,
Java 17 e heap 512 MiB, com POM temporário que altera somente o diretório de build.
Logs RED, comandos, exits, relatórios e inventário anterior permanecem preservados.
Nenhum clean sobre target compartilhado, dependência instalada ou orçamento renovado.

O arquivo `database/preparation/bloco59/usuarios-runtime.sql` é preparação não
executada. A cadeia V001–V023 existente não implementa recovery operacional de
Usuários: V022 restringe entidade, terminalidade e estado de selo. A preparação
explicita o delta necessário sem fabricar uma migration aplicada.

Para um futuro ensaio físico, Plataforma de Dados/DBA e Segurança/Operações devem
fornecer alvo/estado autorizado, adoção versionada da extensão e rollback,
identidade/permissões mínimas, policy/scope vigentes e comprovação de source_catalog
compatível com a instância GraphQL. Os grants de domínio V007 não substituem a
qualificação da identidade operacional. Nenhum desses efeitos é autorizado aqui.
As campanhas/budgets B53–B57 não são herdados.

A integração local não qualifica snapshot, completude, fonte real, transação,
concorrência física, publicação para consumidores, bootstrap, sweep ou cutover.
