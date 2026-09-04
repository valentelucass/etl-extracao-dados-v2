# Runbook — Usuários current/history em sombra

Este runbook operacionaliza somente a fatia local de V2-033 definida no
[ADR 0019](../adr/0019-usuarios-current-history-em-sombra.md), na migration
[V007](../../database/migrations/V007__create_usuarios_current_history.sql), no validator
[026](../../database/validation/026_validate_usuarios_current_history.sql), no exercício
[027](../../database/validation/027_exercise_usuarios_current_history_rollback.sql) e no SHOWPLAN
[028](../../database/validation/028_validate_usuarios_current_history_showplan.sql), além do gate
negativo de sidecar tardio
[029](../../database/validation/029_exercise_usuarios_late_sidecar_rollback.sql). Ele não
autoriza credencial, chamada GraphQL/Data Export, banco remoto, produção, publicação de view,
deploy, job, sweep, DML manual ou cutover.

## Guardas obrigatórias

- O único banco permitido para o exercício SQL é
  `localhost/ETL_SISTEMA_V2_SHADOW`, com autenticação integrada e transação rollback-only. Nunca use
  `ETL_SISTEMA`, `esl_cloud`, banco legado ou banco de dashboards.
- Use apenas fixtures sintéticas. Não copie nome, ID, tenant, cursor, URL, payload, token, documento
  ou fingerprint real para comando, log, evidência ou issue.
- A operação GraphQL de Usuários é fixa: `individual(enabled=true)`, somente `id/name` e `pageInfo`,
  com página máxima 20. Mutation, query arbitrária, introspection e template `9901` são proibidos.
- `updatedAt` não faz parte do contrato. `started_at_utc`, `observed_at_utc`, last-seen e ordem de
  observação são tempos técnicos e nunca devem ser apresentados como frescor da origem.
- `hasNextPage=false` é terminalidade local não verificada. Não o use para provar snapshot,
  cobertura, consistência ou ausência.
- Cursor vale somente dentro da travessia em andamento. Não registre, persista, copie ou reutilize
  cursor entre execução, processo, retry ou replay.
- Current/history é somente shadow. O estado interno `PUBLISHED` não significa publicação no schema
  `pub` nem autorização produtiva.
- Nunca atualize `active`, current, history, quarantine ou evidência por DML ad hoc. Não contorne os
  procedures, locks, gates de contrato/DQ ou lifecycle.

## Papéis

| Responsabilidade | Owner-papel necessário |
|---|---|
| Contrato e eventual rodada GraphQL autorizada | owner ESL e Segurança/Operações |
| Configuração, execução e resposta a falhas | Plataforma/Operações |
| Migration, índices, grants e investigação SQL | Plataforma de Dados/DBA |
| Quarantine e regra de nome/identidade | owner do domínio Usuários e Plataforma de Dados |
| Retenção, mascaramento e legal hold | Segurança/Compliance, data owner e DBA |
| Publicação dimensional e cutover | owner consumidor, Plataforma de Dados e Operações |

Use somente o papel quando o repositório não identificar um responsável nominal. Não invente nome
de pessoa ou equipe.

## Validação local segura

Antes de abrir o exercício, confirme de modo read-only que o alvo local existe. O comando abaixo
não imprime connection string, principal ou dado de domínio:

```powershell
sqlcmd.exe -S localhost -C -E -d master -b -Q "SET NOCOUNT ON; IF DB_ID(N'ETL_SISTEMA_V2_SHADOW') IS NULL THROW 50000, N'Alvo local de sombra ausente.', 1; SELECT N'LOCAL_SHADOW_TARGET_OK' AS result;"
```

Execute o cenário sintético a partir do diretório que mantém válidos os includes `:r`. O próprio
arquivo recusa outro nome de banco e encerra com rollback:

```powershell
Push-Location .\database\validation
try {
    sqlcmd.exe -S localhost -C -E -f 65001 -d ETL_SISTEMA_V2_SHADOW -i .\027_exercise_usuarios_current_history_rollback.sql -b
    sqlcmd.exe -S localhost -C -E -f 65001 -d ETL_SISTEMA_V2_SHADOW -i .\029_exercise_usuarios_late_sidecar_rollback.sql -b
} finally {
    Pop-Location
}
```

Feche também manifesto, namespace concorrente e planos com os gates dedicados:

```powershell
.\scripts\validation\Test-UsuariosCurrentHistoryManifest.ps1
.\scripts\validation\Test-UsuariosCurrentHistoryConcurrency.ps1
.\scripts\validation\Test-UsuariosCurrentHistoryShowplan.ps1
```

O probe de concorrência extrai a fórmula do namespace diretamente do entrypoint comum V004 e exige
timeout `-1` para a mesma tuple, independência de outro `environment` e liberação por rollback. Ele
é uma prova composicional junto ao exercício 027 dos entrypoints reais; não registre como duas
execuções completas simultâneas. O SHOWPLAN atribui cada índice ao statement correspondente e exige
zero conversão ou warning operacional. Um item de lifecycle classificado como typed-oversized exige
revisão do cap ou particionamento conforme o runbook comum; ele é excluído antes do `TOP`, portanto
não impede um item posterior que caiba. O exercício 029 demonstra que uma execução generic-only já
terminal não pode receber o sidecar tipado depois do fence. Preenchimento máximo do orçamento não é
alegado.

Para validar Java, arquitetura, formatação, análise estática e testes locais sem iniciar o runtime:

```powershell
.\mvnw.cmd verify
```

Esses comandos não constituem evidência de execução até serem realmente executados. Registre apenas
o comando, `PASS`/`FAIL`/`BLOCKED`, código de saída e categorias sanitizadas; não cole linhas de
current/history, source keys, nomes, cursores ou fingerprints. Não habilite perfis remotos nem use
variáveis `CONTRACT_*`/credenciais nesta validação.

## Fluxo de uma execução de sombra

1. Crie uma execução nova, ligada ao contrato/configuração vigentes e à entidade `usuarios`.
2. Inicie obrigatoriamente na página 1, sem cursor. Fixe antes do I/O os caps de páginas, nodes,
   bytes, duração, requests e itens em voo.
3. Leia serialmente. Cada página válida contém de 1 a 20 nodes e é convertida em um único
   microbatch; o batch não atravessa o limite de uma página.
4. Para cada node, preserve a source key type-tagged e a presença de `name` como `ABSENT`, `NULL` ou
   `VALUE`. Valores inválidos vão para quarantine com reason code sanitizado.
5. Avance com `endCursor` somente enquanto a mesma travessia estiver viva. Cursor ausente ou
   repetido com `hasNextPage=true` falha fechado.
6. Aceite `hasNextPage=false` apenas como fim local daquela tentativa. Conclua auditoria e gates
   comuns antes de solicitar `SHADOW_UPSERT`.
7. Aplique somente pelo wrapper atômico. O SQL deduplica, detecta conflitos, resolve a ordem total e
   grava current/history/reconciliação set-based.
8. Interprete a disposição agregada sem consultar conteúdo: `INSERTED`, `UPDATED` e `REACTIVATED`
   criam histórico; `NO_OP` e `STALE_NO_OP` não criam.

Não existe comando operacional de produção neste runbook. A composição futura deve chamar portas
tipadas; operador não executa os procedures manualmente para antecipar ou reparar uma execução.

## Tratamento de presença e mudança

- `ABSENT` em usuário já existente conserva o valor e o hash atuais. Não o converta para nulo.
- `NULL` explícito pode mudar o atributo para nulo e, se a ordem for mais nova, gerar `UPDATED`.
- `VALUE` mantém o texto validado; não faça trim, case folding, pseudônimo ou fallback na aplicação.
- Estado idêntico mais novo é `NO_OP`: atualiza last-seen/ordem, sem histórico.
- Estado mais antigo é `STALE_NO_OP`: não regride current e não cria histórico.
- Empate exato da ordem com estado divergente é incidente de integridade e deve parar a execução.
- Reaparecimento de current legitimamente inativo é `REACTIVATED` e conserva o mesmo `usuario_id`.
  V007 não produz `active=0`; não simule reativação desativando linha por DML fora do exercício
  rollback-only.

## Falhas, caps e cancelamento

| Sinal | Efeito obrigatório | Continuação segura |
|---|---|---|
| página vazia | falhar como anomalia de paginação | nova execução desde a página 1 após diagnóstico |
| `hasNextPage=true` sem cursor ou com cursor repetido | falhar; não completar auditoria | descartar retomada e reiniciar sem cursor persistido |
| página maior que 20 ou cap de páginas/nodes/bytes/requests excedido | falhar por limite | rever somente o cap autorizado; nunca promover a tentativa parcial |
| timeout, fonte indisponível, circuito aberto ou retry budget esgotado | falhar com categoria sanitizada | respeitar backoff/circuito; não fazer fallback manual |
| cancelamento | interromper I/O e não fazer retry implícito | nova execução explícita se o motivo estiver resolvido |
| erro de parser, MIME/status ou drift de contrato | falhar antes de apply | corrigir/ratificar contrato; não tolerar campo inesperado |
| erro ao stagear uma página | falhar toda a travessia | nova execução e staging novo; não continuar do cursor |
| quarantine ou atributos conflitantes | bloquear candidate set | owner do domínio/Plataforma de Dados avalia o reason code, sem expor registro |
| falha de gate de contrato, DQ ou reconciliação | não aplicar current/history | preservar evidência e corrigir o gate, sem DML manual |
| falha após commit cuja resposta ficou incerta | retry da mesma execução pelo wrapper | aceitar somente a evidência idempotente; divergência é incidente |

Cap, erro, cancelamento e tentativa parcial nunca contam como prova de ausência e nunca alteram
`active` para zero.

## Replay e ordem total

- Replay sempre recebe uma execução nova e referencia a execução de origem; nunca copia seu cursor.
- Comece novamente na página 1 e construa staging independente. Não misture páginas de tentativas.
- A ordem efetiva é `(observation_order_at_utc, observation_order_execution_id)` da origem do replay.
  Não a substitua pelo relógio da repetição nem por `observed_at_utc` de uma página.
- O mesmo estado sob ordem equivalente permanece no-op. Uma observação anterior vira
  `STALE_NO_OP`; não force atualização para fazer o replay “ganhar”.
- Retry do mesmo registro no mesmo ordinal só é idempotente com conteúdo idêntico. Conteúdo
  divergente exige nova execução e investigação.
- Retry da execução já publicada deve conferir autorização, contagem de aplicações e histórico. Se
  a evidência divergir, pare; não reconstrua manualmente tabelas imutáveis.

## Ausência e desativação

Não compare current com o conjunto observado na JVM e não execute anti-join de ausência neste
bloco. A fonte não fornece prova independente de snapshot completo, e `pageInfo` não substitui essa
prova. Portanto:

- source key não observada permanece como estava;
- nenhuma falha ou página terminal produz tombstone;
- V2-012b/V2-013 precisam liberar explicitamente completude e sweep antes de qualquer desenho de
  desativação;
- o sweep futuro será execução separada, set-based e com guardrails próprios.

## Lifecycle e retenção minimizada

O lifecycle comum descrito em
[Retenção, arquivamento, purge e restore](retencao-arquivamento-e-restore.md) governa staging. Para
Usuários, o archive tipado preserva somente wire type, presença, hashes versionados, byte count
descartado e atestado; não duplica o nome. Restore é read-only em `recon` e nunca repovoa staging ou
current. A iTVF `recon.ufn_staging_lifecycle_extension_archive_budget` agrega o custo tipado por
execução com seek correlacionado e `TOP(max+1)`; V005 o soma ao custo genérico antes de oversized,
`TOP` e cumulativos. O trigger tipado é somente uma guarda de lower-bound/caps e não altera o plano
depois da admissão. Um registro genérico preexistente sem sidecar nunca pode ser completado tarde
pelo entrypoint tipado: o wrapper compartilha o row fence da execução com plan/archive e recusa o
bypass, inclusive em estado terminal.

Não use o lifecycle de staging para excluir `core.usuario`, `core.usuario_history`, quarantine ou
reconciliação. Não ative TTL, purge produtivo, cold storage, masking ou legal hold sem as políticas e
aceites externos próprios. Se archive/atestado/byte budget divergir, pare antes do purge e escale
para Plataforma de Dados/DBA e Segurança/Compliance.

## Evidência sanitizada mínima

Registre somente:

- data/hora, ambiente categórico `LOCAL_SHADOW` e owner-papel executor;
- versões de contrato/configuração/policy, sem fingerprints completos;
- limites configurados e contagens agregadas de páginas, candidatos, disposições, quarantine e
  histórico;
- terminalidade categórica `LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED`;
- resultado `PASS`, `FAIL` ou `BLOCKED`, código categórico e condição objetiva para continuar;
- confirmação categórica de rollback e de ausência de chamada externa.

Nunca registre source key, `usuario_id`, nome, payload, cursor, URL, token, connection string,
tenant, documento, linha de history/quarantine ou stack trace com conteúdo externo.

## Condições de parada e gates externos

Pare e preserve a evidência quando houver alvo diferente do shadow local, pedido de credencial ou
rede sem autorização, cap atingido, terminalidade anômala, cancelamento, drift, quarantine,
conflito, ordem empatada divergente, gate comum bloqueado, archive inconsistente ou saída não
sanitizável.

V2-041 continua bloqueando segredo, rede, sonda, health check, release, deploy, produção e cutover.
V2-012b/V2-013 bloqueiam desativação. A fatia Usuários de V2-035b fornece somente a projeção interna
`core.v_usuario_dimension_current_v1`, descrita no
[ADR 0021](../adr/0021-dimensao-current-de-usuarios-em-sombra.md), sem grant. V2-037 continua
bloqueando `pub.vw_dim_usuarios`, aliases, manifesto consumidor e paridade externa. Os gates de
retenção governam TTL e controles físicos. Nenhum desbloqueio isolado autoriza todos os demais
efeitos.
