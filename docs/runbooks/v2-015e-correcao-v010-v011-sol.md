# Bloco 40 — G12 — V2-015e — correção intercamadas V010/V011

## Estado e objetivo

- **Modelo:** GPT-5.6 Sol com reasoning Ultra.
- **Estado inicial:** `PLANEJADO_NAO_INICIADO`.
- **Rota:** `G12`, única `STATUS=AGORA`.
- **Objetivo:** alinhar os contratos Java e SQL já aceitos de Coletas e Cotações antes de qualquer caracterização externa, bootstrap ou paridade.
- **Resultado esperado:** `CONTRATOS_JAVA_SQL_ALINHADOS_E_GATES_LOCAIS_VERDES`.

Linha operacional canônica:

```text
STATUS=AGORA | ROTA=G12 | BLOCO=40 | TAREFA=V2-015e | FATIA=CORRECAO_INTERCAMADAS_V010_V011 | ESCOPO=terminalidade retroativa de Coletas e identidade BIGINT, tri-state, tarifa efetiva e moeda referenciada de Cotações | MODELO=SOL_ULTRA | DEPENDE=V2-010+V2-027 | RUNBOOK=[v2-015e-correcao-v010-v011-sol.md](v2-015e-correcao-v010-v011-sol.md) | REDE=PROIBIDA | BANCO=LOCAL_SHADOW_ROLLBACK_ONLY | PRODUCAO=PROIBIDA | SAIDA=CONTRATOS_JAVA_SQL_ALINHADOS_E_GATES_LOCAIS_VERDES
```

## Fontes de verdade

Antes de editar, ler uma única vez e usar diretamente durante a execução:

1. `AGENTS.md`, `../CONTEXTO_GLOBAL.md`, `STATES.md` e `docs/runbooks/trilha-de-chats-gpt-5-6.md`;
2. este runbook;
3. ADR 0023, regras COL-03 e o catálogo/manifesto de Coletas V2-010;
4. identidade 6906, decisão/manifesto de Cotações V2-027 e regras COT-01/COT-02;
5. V004, V010 e V011, somente nos trechos diretamente consumidos pelas duas promoções;
6. validators 038–041, wrappers PowerShell e testes Java diretamente relacionados.

Não reabrir identidade, grão, relação, status, frescor, completude, tarifa, moeda, unidade, arredondamento ou publicação já congelados. Divergência sem regra aceita permanece fail-closed e é registrada como limite específico.

## Preflight e preservação

1. Confirmar diretório, branch, HEAD e `git status`; a referência observada na preparação é `main` em `0b910432f12d81a306072e24aa44885da94c62a1`, com árvore amplamente suja.
2. Preservar todas as alterações preexistentes. Não usar `reset`, `checkout`, `clean`, commit, push ou operação destrutiva.
3. Antes de alterar V010/V011 em place, comprovar no histórico local e nas evidências canônicas que continuam drafts não publicados e executados apenas em rollback.
4. Se o SQL Server local for usado, consultar primeiro `master` e confirmar literalmente `localhost/ETL_SISTEMA_V2_SHADOW`. Verificar de forma sanitizada se V010/V011 não constam como migrations aplicadas com sucesso em `ctl.flyway_schema_history`.
5. Se qualquer alvo autorizado tiver V010 ou V011 aplicada persistentemente, não reescrever o arquivo e nunca usar `flyway repair`. Implementar uma única correção forward-only na próxima versão livre, hoje V013, reconciliando baseline, manifests, inventários e validators.
6. Nunca aplicar simultaneamente uma correção in-place e uma migration forward para o mesmo defeito.

## Limites de autorização

Permitido:

- código e testes locais diretamente necessários ao Bloco 40;
- migrations V010/V011, ou uma migration forward única se o preflight exigir imutabilidade;
- manifests/fingerprints, baseline SQLCMD, validators 038–041 e wrappers derivados;
- SQLCMD somente contra o alvo local autorizado, com autenticação Windows já existente, dados sintéticos e rollback integral;
- Maven e ferramentas locais offline.

Proibido:

- rede, internet, API, `curl`, credencial, token ou leitura de `.env`;
- payload, cursor, identificador ou dado de negócio real;
- banco remoto/produtivo, `ETL_SISTEMA`, `esl_cloud`, `DASHBOARDS` ou `DASHBOARDS_DEV`;
- Flyway `repair` ou `clean`, dado persistente, usuário/login/job/agendamento;
- Q-*-01, bootstrap, relação, paridade real, sweep, objeto `pub`, fato, release, deploy ou cutover;
- alteração de V004 ou de componente compartilhado sem teste que demonstre ser indispensável ao contrato aceito deste bloco.

## Método obrigatório

Para cada defeito:

1. localizar a regra canônica;
2. adicionar teste de regressão que falhe no estado atual;
3. executar e registrar o vermelho com comando e motivo esperado;
4. aplicar a menor correção coesa;
5. executar o verde focado;
6. revisar consumidores, baseline, manifests e validators;
7. não produzir alteração estética ou churn de fingerprint sem mudança material.

Com subagentes, usar ownership exclusivo:

- Agente A: V010, validators/exercício 038–039 e teste estático de Coletas;
- Agente B: Cotações Java, V011, validators/exercício 040–041 e testes focados;
- Agente C: revisão read-only de V004, imutabilidade de migration, baseline/manifests e matriz de regressão;
- agente principal: arquivos compartilhados, integração, documentação e validação final única.

## Workstream A — Coletas/V010

### Invariante

COL-03 exige que uma observação terminal de origem vença um estado aberto mesmo com timestamp retroativo, e que um terminal persistido nunca regrida para aberto.

### Correção esperada

- A decisão tipada de `core.usp_apply_reconcile_publish_coletas` deve tratar `current aberto + typed terminal` antes do `STALE_NO_OP` genérico, com aplicação efetiva e resultado tipado `UPDATED`.
- Preservar `terminal atual + typed aberto` como não regressivo.
- Preservar `aberto atual + typed aberto retroativo` como stale.
- Não reduzir a correção a uma busca textual: provar a ordem e o comportamento dos dois `CASE` do plano.
- Não mudar ausência, sweep, aliases, relações, publicação, catálogo de status ou precedência de frescor.
- V004 pode continuar registrando a disposição técnica genérica stale enquanto o wrapper tipado aplica COL-03. Se isso permanecer, registrar o limite explicitamente; não tornar o kernel comum terminal-aware silenciosamente.

### Matriz mínima

1. `pending` em T2 seguido de `done` em T1, com T1 menor que T2: resultado tipado com uma atualização; `core.coleta` termina em `done`, `Coletada`, `terminal=1`, `attempt_count=1` e ação `Coleta Realizada`.
2. Estado terminal seguido de `pending` em T3: não regride.
3. Estado aberto em T2 seguido de estado aberto em T1: continua `STALE_NO_OP`.
4. Replay do terminal aceito: idempotente.
5. Empate divergente e tentativa parcial: continuam fail-closed.

Fortalecer `Test-ColetasV2010ShadowVertical.ps1`, `038_validate_coletas_shadow_vertical.sql` e `039_exercise_coletas_shadow_vertical_rollback.sql`. O exercício precisa capturar o result set tipado e sempre terminar em rollback.

## Workstream B — Cotações/Java/V011

### Identidade positiva de 64 bits

- Aplicar o domínio `1..9223372036854775807` ponta a ponta.
- Trocar os três enforcements SQL estreitados por conversão `BIGINT`, positividade e representação decimal canônica type-tagged.
- Aceitar `INTEGER:2147483648` e `INTEGER:9223372036854775807`.
- Rejeitar zero, negativo, overflow, sinal `+`, zero à esquerda, whitespace, decimal e algarismo não ASCII, sem ecoar a chave no erro.
- Alinhar `CotacaoStageRecord`/mapper: valor acima de `Long.MAX_VALUE` deve ser quarentenado antes do JDBC.
- Preservar a tuple completa `(environment_name, source_instance, tenant_scope, entity_name, source_key)` e o surrogate canônico separado.

### Presença tri-state e tarifa efetiva

Validar de forma fail-closed todos os tokens esperados em `field_presence_json`:

- `sequence_code`;
- `requested_at`;
- `qoe_qes_fit_nse_issued_at`;
- `qoe_qes_fit_fhe_cte_issued_at`;
- `qoe_qes_total`;
- `qoe_crn_psn_nickname`;
- `qoe_uer_name`;
- `qoe_qes_ony_sae_code`;
- `qoe_qes_diy_sae_code`.

Para cada atributo nullable promovido:

- `ABSENT`: não afirma valor e preserva o tipado corrente;
- `NULL`: aplica nulo explícito quando o contrato permitir;
- `VALUE`: aplica o valor novo, inclusive total `0.0000`;
- não usar `COALESCE` para implementar presença.

A resolução COT-02 deve usar UFs efetivas da mesma raiz escopada: em update, `ABSENT` conserva o valor current; `NULL` ou `VALUE` usa a afirmação da observação. Insert sem duas UFs válidas continua fail-closed. Outra environment/source/tenant nunca fornece valor ou tarifa. `currency_code` de origem deve permanecer nulo; moeda, unidade, arredondamento e mínimo vêm somente da release `QUOTE_TARIFF` explícita, ratificada para `SHADOW` e não revogada.

Preservar o payload e a presença da observação no staging. Não fabricar `VALUE`, rota, tarifa, release ou completude. Se a representação de presença no current ou o hash efetivo não estiver decidida pelas fontes canônicas, manter a evidência da observação, não inventar merge documental e registrar o limite.

### Matriz mínima

1. Limites positivos `2147483648` e `9223372036854775807` aceitos em Java/SQL.
2. `0`, `-1`, `9223372036854775808`, `+1`, `01`, whitespace e dígitos não ASCII rejeitados sem persistência.
3. Seed com usuário, total e SP→RJ; atualização mais fresca com usuário/total/UFs `ABSENT` preserva os tipados e resolve tarifa usando somente a rota da mesma raiz.
4. `NULL` limpa usuário/total quando permitido.
5. `VALUE 0.0000` substitui total anterior.
6. Insert com UFs `ABSENT` e update com UFs explicitamente nulas permanecem fail-closed.
7. Mesma source key em outro tenant não empresta UFs/tarifa.
8. Replay do caso `ABSENT` é idempotente; stale não regride.
9. Falha de presença ou tarifa não deixa reconciliação/publicação parcial.

Fortalecer `CotacaoDataExportRecordMapperTest`, testes do value object, `Test-CotacoesV2027ShadowVertical.ps1`, `040_validate_cotacoes_shadow_vertical.sql` e `041_exercise_cotacoes_shadow_vertical_rollback.sql`.

## Artefatos derivados

- Atualizar o manifesto de Coletas para explicitar as duas metades de COL-03 e recalcular seu fingerprint.
- Atualizar decisão/manifesto de Cotações somente para explicitar o domínio positivo signed 64-bit e a política tri-state já aceita; recalcular fingerprints realmente derivados.
- O baseline inclui migrations por `:r`; manter a lista coerente com a estratégia in-place ou forward.
- Validators devem verificar semântica e ordem, não consagrar `TRY_CONVERT(INT)` nem apenas contar tokens.
- Não alterar contratos de fornecedor, identidade global, fatos, views consumidoras ou indicadores de qualificação externa.

## Validação final

Executar focados antes da suíte completa:

```powershell
pwsh -NoProfile -File .\scripts\validation\Test-ColetasV2010DecisionCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ColetasV2010ShadowVertical.ps1
pwsh -NoProfile -File .\scripts\validation\Test-DataExport6906IdentityCatalog.ps1
pwsh -NoProfile -File .\scripts\validation\Test-CotacoesV2027ShadowVertical.ps1
pwsh -NoProfile -File .\scripts\validation\Test-SchemaFoundationManifest.ps1
pwsh -NoProfile -File .\scripts\validation\Test-ProgressiveDataGate.ps1
```

Com o alvo exato disponível e autorizado, executar 039 e 041 em rollback e, uma única vez no estado integrado, o gate progressivo e a prova concorrente aplicável. Confirmar depois somente contagens/ausência agregadas de objetos, dados e histórico temporários.

No final integrado:

```powershell
.\mvnw.cmd --offline --batch-mode --no-transfer-progress clean verify
pwsh -NoProfile -File .\scripts\validation\Test-V2012CharacterizationFoundation.ps1
pwsh -NoProfile -File .\scripts\validation\Test-Gpt56ChatTrail.ps1
pwsh -NoProfile -File .\scripts\security\Test-OfflineSecretScan.ps1
pwsh -NoProfile -File .\scripts\security\Invoke-OfflineSecretScan.ps1 -Source .
git diff --check
```

Auditar UTF-8 estrito nos arquivos alterados. Não repetir a suíte completa se ela já passou no mesmo estado final.

## Encerramento

Atualizar primeiro `STATES.md` e depois a trilha. Somente se todos os aceites aplicáveis tiverem evidência:

1. marcar V2-015e e G12 como concluídas, sem marcar V2-015 agregada;
2. registrar reds, greens, comandos/resultados, arquivos, estratégia de migration, rollback e limites;
3. manter Q-*-01 e todos os holds externos intactos;
4. atualizar o painel para 42/99 checkboxes e 204 fatias abertas;
5. substituir nos validators os invariantes transitórios do Bloco 40 por assertivas exatas do fechamento, sem enfraquecer o histórico;
6. promover uma nova rota `AGORA` somente se suas dependências estiverem comprovadamente satisfeitas; caso contrário, registrar zero `AGORA` e Bloco 41 não atribuído.

Se o banco local não estiver disponível, uma migration aplicada impedir a estratégia segura, ou um aceite físico não puder ser executado, manter V2-015e/G12 abertas e registrar precisamente o bloqueio. Não declarar o Bloco 40 concluído por validação apenas estática.
