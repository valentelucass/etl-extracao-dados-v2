# ADR 0040 — Usuários/GraphQL no runtime operacional local

Data: 2026-09-09. Decisão adotada para a fatia B59 de V2-022.
Qualificação física não executada nem autorizada neste bloco.

## Contexto e fontes

Os ADRs [0016](0016-adaptador-graphql-transitorio.md) e
[0019](0019-usuarios-current-history-em-sombra.md) preservam `individual(enabled=true)`,
id/name, página máxima 20, identidade INTEGER/STRING e nome ABSENT/NULL/VALUE.
V007 já implementa current/history set-based; B58 caracteriza parser e staging.
Faltava compor esses componentes no runtime dos ADRs 0033–0035 e 0038.
Os cinco anexos ESL registrados em STATES não estabelecem template oficial de
Usuários. Data Export continua sendo a origem-alvo na API REST.

## Decisão

`RuntimeUsersRequest` é a extensão tipada mínima do envelope operacional.
`protocol=GRAPHQL` e `operation=USERS_SNAPSHOT` selecionam um schema fechado de
17 strings, sem query, cursor, template ou filtros arbitrários. Só BACKFILL e
REPLAY, com intervalo técnico de observação, estratégia FULL e milissegundos.
FULL não afirma snapshot da fonte. O fluxo Data Export e `runtime-request-v1`
preservam parsing e material de fingerprint anteriores. Não há template 9901.

Usuários reutiliza `GraphQlFirstWaveContractCatalog` e
`GraphQlRuntimeConfigurationFingerprint`. O fingerprint de request próprio é
`runtime-users-request-v1`; inclui destino, limites, política e ocorrência.
Uma nova invocation de autorização não muda a ocorrência. Configurações GraphQL
e Data Export simultâneas precisam compartilhar namespace e política ESL;
isso não autoriza trocar o tipo de uma instância já registrada no catálogo SQL.
A qualificação física deve conferir esse estado, sem remapear fonte silenciosamente.

`RuntimeCompositionRoot` congela o scope antes da autoridade. A injeção local é
restrita ao pacote bootstrap e exige o adaptador concreto
`WindowsSqlRuntimeAuthorization`: AUTHORIZE e CONSUME continuam obrigatórios.
O caminho padrão continua verificando o artefato administrado, a identidade e
o recibo durável. Nenhum callback de negócio roda antes do consumo confirmado.

`LocalUsuariosRuntime` compara a partição esperada e os bindings do guard antes
da factory. Compõe factory/gate, parser, streamer, extrator e mapper existentes,
staging JDBC de uma página e promoção JDBC tipada. O mesmo token com heartbeat
atravessa factory/gate/streamer/extrator. Contadores locais O(1) só acompanham
páginas e linhas confirmadas; não calculam dedupe, conflito ou current/history.
Não existe callback que possa substituir a extração por um resultado fabricado.

`ControlPlaneGraphQlExtractionAudit` exige início único, execução/operação
corretas, sequência, limites, tempos não regressivos, terminalidade e contagens
compatíveis. Falha de persistência ou de validação invalida a conclusão. A sessão
confere essa evidência junto ao permit e à política. Parser sem gate, página de
outra ocorrência ou conclusão sem páginas não chega à promoção.

## Ordem do selo e resultado incerto

O critério E do prompt exige zero selo durável quando a DQ de Usuários falha.
O teste `rejectedQualityCannotSealOrApplyUsers` reproduziu a incompatibilidade
da sequência anterior. Para Usuários a ordem é:

1. Travessia, staging e auditoria; guard completo e evidência retida em memória.
2. EXTRACTED/STAGED, candidate set tipado, avaliação DQ exata da ocorrência.
3. Selo durável em PROMOTED, após DQ aprovada, seguido de apply tipado.

Data Export conserva seu selo antes das transições. Para Usuários, uma confirmação
perdida antes do selo não permite retomar efeitos usando apenas permits vivos.
A sessão exige leitura durável; a raiz consulta recovery sem recompor a fonte.
Um recibo válido permite continuar/confirmar sem reextração. Sem evidência válida,
o resultado permanece RECOVERY_REQUIRED ou a recusa durável correspondente;
uma nova tentativa/replay explícita começa na primeira página, sem cursor anterior.
Um apply confirmado não é repetido. Campos agregados NULL não equivalem a zero.

## SQL preparado e limite da prova

V022 só aceita as cinco entidades Data Export e exige página vazia terminal.
O arquivo [usuarios-runtime.sql](../../database/preparation/bloco59/usuarios-runtime.sql)
prepara a extensão do recovery: terminal GraphQL preenchido, selo após DQ,
binding de operação/modo, candidate tipado e recibo de
`recon.usuario_reconciliation_result`. A leitura confere autorização APPLIED,
aplicações, history e o recibo genérico comum, retornando as contagens tipadas.
O algoritmo current/history de V007 e as migrations aplicadas não são alterados.
O arquivo não integra a sequência Flyway/baseline e não foi aplicado/compilado
em SQL Server. Sua futura adoção deve versionar migration/baseline e qualificar
alvo, estado, permissões, rollback, transações e concorrência sob autorização própria.

Os testes de protocolo JDBC verificam chamadas e bindings dos adaptadores reais,
com recibos agregados sintéticos. Não demonstram execução T-SQL, durabilidade,
concorrência física ou paridade ESL. O simulador não implementa o algoritmo de domínio.
As provas físicas anteriores permanecem históricas e não são reemitidas pelo B59.

## Regras e aceite

| Regra | Aplicação B59 | Exemplo/contraprova local |
| --- | --- | --- |
| USR-01 | Staging/promoção tipados de V007; current/history continua no SQL | INTEGER:1 e STRING:1 seguem distintos; recibo no-op tipado não vira insert genérico |
| USR-02 | SHADOW_UPSERT_ONLY; nenhuma desativação, sweep ou relação nova | Falha parcial/terminalidade não produz apply nem prova de ausência |
| USR-03 | Operação GraphQL transitória fechada | Request Data Export ou operação sidecar de Fretes é recusado |
| USR-04 | Sem template inferido, updatedAt ou watermark de fonte | Campos template/query/cursor/updatedAt e modo incremental são recusados |

Owner-papel: Plataforma de Dados/Operações para integração e qualificação SQL;
Segurança/Operações e owner ESL para autoridade/fonte; owner de Usuários para
regras e oráculos. Não há nome nominal novo inferido.

O máximo de aceite é INTEGRACAO_LOCAL_USUARIOS_TESTADA, por A–F do B59.
Não reabre V2-033, não fecha V2-022 pai, Q-USR-01, V2-012, sweep, release ou cutover.
Matriz, comandos e resultados: [catálogo B59](../catalogos/bloco59-local/README.md).
