# Runbook — Revalidar contratos 6908 e 6389

Este procedimento só pode ser usado em uma futura rodada externa autorizada de V2-025d/paridade. Enquanto V2-041 permanecer em `EXTERNAL_HOLD`, não execute probe, harness, `curl`, health check nem use qualquer credencial. A baseline offline V2-025a está no [catálogo da primeira onda](../catalogos/contratos-primeira-onda/README.md) e não substitui revalidação remota nem a autoriza.

## Pré-autorização obrigatória

O responsável pelo ambiente precisa fornecer, por template, antes de qualquer chamada:

1. Alvo read-only identificado, URL Data Export e URL GraphQL com credenciais estritamente somente leitura. Pode ser uma API já autorizada para leitura; não pressupõe sandbox.
2. Janela populada com no mínimo três páginas, janela declarada vazia e janela com alteração tardia. O harness exige `YYYY-MM-DD..YYYY-MM-DD` para cada janela de negócio, além de uma janela `updated_at` tardia em Instants ISO-8601 explícitos. Esses valores não podem conter URL, token, payload de negócio, ID ou hash de ID; não deduzir a janela vazia a partir do resultado.
3. Teto total de chamadas por entidade/template, compartilhado entre Data Export e GraphQL, e dois valores aceitos de `per` que a fonte respeite como teto de entidades distintas por `id`. Linhas físicas podem expandir uma mesma entidade no relatório detalhado; os limites de volume físico continuam independentes. Os valores são declarados separadamente em `CONTRACT_6908_MAX_CALLS` e `CONTRACT_6389_MAX_CALLS`; cada tentativa remota da respectiva entidade consome o mesmo teto.
4. Para esta migração, usar `America/Sao_Paulo`, já declarado como `api.dataexport.timezone` no legado e exigido no V2 como `API_DATAEXPORT_TIMEZONE`. Essa é uma compatibilidade formal V1/V2 do cliente: não a substitua pelo fuso do host, token ou de uma amostra textual, e não a interprete como prova de que o fornecedor aplica os limites de filtro nesse fuso ou de que `updated_at` é monotônico. Qualquer divergência exige nova aprovação de migração.
5. Confirmação de um transporte aceito e, quando disponível, cursor/ordem total e cobertura ou contagem oficial suficiente para avaliá-la. Para uma janela fechada específica, também é aceitável comparar o conjunto terminal completo do GraphQL V1 com duas travessias terminais do Data Export em valores distintos de `per`, desde que os mapas completos por ID e chave natural sejam iguais. Isso demonstra cobertura empírica somente daquela janela; não declara ordem, cursor ou snapshot global do fornecedor.
6. Se a comparação financeira exigir 4924, raiz e filtro de negócio aprovados, janela fechada, ordenação, transporte e teto próprio para a sonda auxiliar. Ela continua opcional e não cria uma terceira vertical.

Sem os demais dados, uma sonda read-only limitada ainda pode coletar evidência parcial, mas não pode provar vínculo, receita, CT-e ou contas. O fuso de compatibilidade já está definido, mas não substitui as provas temporais da fonte. No contrato atual, `updated_at` permanece `UNVERIFIED` e contas permanecem `ABSENT`; qualquer promoção posterior pertence a V2-011/V2-012.

## Trava técnica e configuração

A suíte externa é opt-in e exige **as duas** travas Failsafe:

```text
mvn --batch-mode --no-transfer-progress -Pcontract-tests -Dcontract.tests.enabled=true verify
```

O perfil ativa o Failsafe e a propriedade autoriza o gate interno; uma só delas não é suficiente. A IT de evidência só abre rede quando existe ao menos uma variável `CONTRACT_*`. Sem nenhuma, ela é marcada como ignorada e não abre conexão; com configuração parcial, a validação falha antes da primeira conexão. Ela lê somente variáveis `CONTRACT_*`, descritas em [`config/contract-test.example.properties`](../../config/contract-test.example.properties). Não configure `API_*` esperando que o harness as reutilize.

`CONTRACT_PAGE_SIZE_A` e `CONTRACT_PAGE_SIZE_B` devem ser positivos, distintos e previamente aprovados para comparação. O harness usa ambos nas amostras e travessias repetidas; a comparação transitória com GraphQL usa o primeiro valor configurado, sem impor `per=1`. `CONTRACT_SOURCE_TIMEZONE_FORMALLY_CONFIRMED`, `CONTRACT_<template>_ORDER_TOTAL_OR_CURSOR_CONFIRMED` e `CONTRACT_<template>_COVERAGE_CONFIRMED` são flags opcionais, com padrão `false`. A primeira só registra confirmação explícita da semântica temporal pelo fornecedor; ela não pode ser preenchida por inferência a partir da compatibilidade V1/V2 `America/Sao_Paulo`. As demais registram confirmação formal já obtida, mas não substituem a evidência de payload, travessia e paridade.

Valores ausentes devem falhar antes da conexão; não inclua token, URL de tenant, IDs, hashes de IDs ou payload em comando, log, arquivo ou evidência. Na autonomia atual, `CONTRACT_DATAEXPORT_TRANSPORT` é `GET_WITH_QUERY`; incompatibilidade não tenta fallback remoto. Para consulta direta, use as sondas PowerShell que chamam `curl.exe`, não o harness Java, até haver autorização explícita para outra forma de tráfego.

`CONTRACT_4924_ENABLED` começa em `false`. Quando for `true`, todas as chaves `CONTRACT_4924_*` do arquivo de exemplo são obrigatórias, inclusive o ID fixo `4924`, raiz, filtro de negócio, janela fechada, ordenação, transporte e teto. A sonda auxiliar faz somente `/info` e uma amostra fechada com `per=1`; ela não pagina, não cria módulo/tabela/carga recorrente e não autoriza equivalência financeira por nome de campo.

## Sondas diretas autorizadas

Na autorização read-only atual, há três sondas que usam somente `curl.exe` e mantêm respostas, tokens, URLs, IDs e cursores exclusivamente em memória:

1. `scripts/probes/Invoke-DataExportContractProbe.ps1` coleta perfil sanitizado e, com `-BoundedTraversal`, percorre somente Data Export com `per=100` até a página vazia ou ao teto. Ela registra contagens de entidades e linhas físicas, sobreposição entre páginas e motivo de parada, sem declarar cobertura formal.
2. `scripts/probes/Invoke-DataExportGraphQlIdentityProbe.ps1` compara uma janela fechada dos templates 6908 ou 6389 com GraphQL por documento `query` estático mínimo. Informe `-PageSizeA` e `-PageSizeB` positivos e distintos: ela percorre cada tamanho até página vazia, valida em cada página que `id` é escalar/não nulo e que as entidades distintas não excedem o `per`, e compara ambos os mapas Data Export ao mesmo GraphQL terminal (`hasNextPage=false`). A saída contém apenas contagens e flags de igualdade.
3. `scripts/probes/Invoke-DataExportFinancialProbeMinimal.ps1` reúne apenas evidência auxiliar de vínculo, CT-e e receita em memória. Ela não promove relações ou contas; para no primeiro erro/`429` e sua saída nunca contém valores, IDs, URLs, payloads ou tokens.

Use uma única sonda por vez, teto global de no máximo dez chamadas, `GET_WITH_QUERY` para Data Export e sem tentativa manual de retry, fallback ou mudança de transporte. A segunda sonda não consulta 4924 nem valida relação Frete–Coleta, receita ou campos de contas.

## Segurança e limite de chamadas

1. Faça no máximo uma chamada `GET /api/analytics/reports/{template}/info` por template na execução, inclusive quando a sonda auxiliar 4924 estiver autorizada.
2. Não provoque `400`, `429`, fallback ou falha de transporte. A sonda usa somente o transporte aprovado; esses cenários são cobertos por testes locais com servidor controlado.
3. No harness Java autorizado, use exclusivamente `ContractRemoteExecution`: ele compartilha um stop-token e o orçamento de cada entidade entre Data Export e GraphQL. Para as sondas diretas atuais, cada execução mantém o próprio teto global e deve ser serial; não crie orçamentos paralelos para uma fonte. A sonda 4924 usa o próprio `CONTRACT_4924_MAX_CALLS`, mas o mesmo stop-token. Ao receber a primeira resposta `429`, ou ao atingir qualquer teto aprovado, interrompa a suíte sem novas tentativas manuais. A sonda direta não consome `Retry-After` nem faz retry; o cabeçalho é apenas uma evidência quando vier efetivamente na resposta ou estiver especificado pelo fornecedor.
4. O harness Java autorizado grava apenas o resumo sanitizado em `target/contract-evidence/<run-id>/summary.json`. As sondas diretas por `curl.exe` não gravam arquivo e emitem o mesmo tipo de resumo somente no stdout. O resumo contém HTTP, indicação booleana de `Content-Type` JSON e de presença de `Retry-After`, contagens, nomes técnicos de campos, tipos JSON, presença, nulos, formatos e flags de comparação. A ausência de evento `429` — e, portanto, de `Retry-After` — não invalida a validação remota; se o cabeçalho for observado, seu valor continua redigido. Nunca salve valor de cabeçalho, payload de negócio, segredo, URL de tenant, ID ou hash de ID.

## Matriz de confirmação

| Template | Confirmar no `/info` | Confirmar em uma amostra `/data` sanitizada |
|---:|---|---|
| 6908 | ID técnico, dados de manifesto de coleta, `updated_at`, `sequence_code`, filtros `picks.request_date` e `scopes.by_updated_at` | ID não nulo, tipo do ID, cobertura histórica, coerência entre número de coleta e manifesto |
| 6389 | ID técnico, data de atualização, número de referência, campos de contas, vínculo Coleta/N° Coleta, minuta e filtros | ID não nulo, watermark retornado, referência, cardinalidade do vínculo e semântica dos campos de contas |
| 4924 (auxiliar) | Raiz/filtro/ordenação explicitamente aprovados, CT-e e campos financeiros declarados | Perfil sanitizado de uma janela fechada, sem assumir que `id`, documento de fatura ou `fit_ant_*` é a chave/equivalência de Frete |

## Parâmetros mínimos de dados

Envie somente as janelas aprovadas. Preserve as duas raízes como irmãs; `scopes.by_updated_at` é complementar à mesma partição de negócio e nunca prova de presença/snapshot por si só. O objeto abaixo descreve os parâmetros lógicos; com `GET_WITH_QUERY`, eles são serializados na query string, nunca enviados no corpo:

```json
{
  "search": {
    "<raiz>": {
      "<data_de_negocio>": "YYYY-MM-DD - YYYY-MM-DD"
    },
    "scopes": {
      "by_updated_at": "YYYY-MM-DD HH:mm:ss - YYYY-MM-DD HH:mm:ss"
    }
  },
  "page": "1",
  "per": "<per-aprovado>"
}
```

Para Coletas, `<raiz>` é `picks` e a data é `request_date`. Para Fretes, são `freights` e `service_at`. Primeiro colete o primeiro `per` aprovado para perfil de payload e, na janela populada, execute travessias repetidas com os dois `per` aprovados.

## Roteiro de evidência

1. Na autonomia atual, execute primeiro `Invoke-DataExportContractProbe.ps1`: ele chama `/info` e registra somente metadados/perfil sanitizado. A fixture sintética local é prova do parser, não substitui esta evidência. A `ContractRemoteEvidenceIT` só deve ser usada quando houver autorização explícita para tráfego Java.
2. Nas três janelas fornecidas, crie o perfil sanitizado de `/data`: campos, tipos JSON, presença, nulos, formatos, resposta vazia, contagens, `Content-Type` JSON e a flag de filtros obrigatórios declarados no `/info`. A sonda sanitizada é a evidência vigente para `id` e `updated_at`; mantenha a divergência histórica registrada no catálogo.
3. Na janela populada com ao menos três páginas, execute `Invoke-DataExportContractProbe.ps1 -BoundedTraversal` e avalie em memória contagens, chaves, duplicatas intra/interpágina e página terminal vazia. Para cada página, valide que todos os registros têm `id` escalar não nulo e que a quantidade de `id` distintos não ultrapassa o `per` pedido; mais linhas físicas que `per` são expansão permitida, não falha automática. Se o limite de entidade não puder ser verificado ou for excedido, pare e registre falha de contrato, sem alterar o tamanho nem fazer fallback. A amostra com `scopes.by_updated_at` deve apenas registrar se observou alteração tardia; nunca substitui a partição de negócio nem prova presença. Para cobertura de uma janela fechada, aceite ordem total/cursor ou contagem oficial da fonte; alternativamente, exija GraphQL V1 terminal completo e duas travessias Data Export terminais com `per` distintos e mapas integrais de ID/chave natural iguais. A alternativa é evidência empírica limitada à janela, não garantia global de ordenação ou snapshot.
4. Para V2-012, construa a matriz de equivalência financeira com os estados fechados `equivalente`, `complementar` ou `ausente`. A sonda 4924 apenas fornece evidência complementar na mesma janela fechada. `fit_ant_*` não equivale a `accountingCredit*` sem prova de campo e regra de negócio.
5. Para a comparação direta autorizada, use `Invoke-DataExportGraphQlIdentityProbe.ps1` na mesma janela, sempre com dois `per` distintos. Ele é o equivalente cURL transitório do harness GraphQL descrito no [ADR 0005](../adr/0005-harness-graphql-read-only-paridade.md). Nunca execute comandos do V1.
6. Coletas: exija relação 1:1 entre `6908.sequence_code` e `GraphQL id`, sem nulo/duplicidade e com volume igual. Fretes: preserve `fretes.id` GraphQL como canônico; só avalie `corporation_sequence_number` após preflight de nulo, formato, unicidade e estabilidade.
7. Em V2-012, valide Frete–Coleta por `fit_p_m_pck_sequence_code → sequence_code`, cardinalidade e soma de receita por coleta. Mantenha `pick_items_ids → pick_item_id` como baseline GraphQL. Para fatura, normalize CT-e de 44 dígitos; não assuma `6389.id = 4924.id`, nem transforme ausência/má-formação em igualdade.

O relatório versionável contém apenas totais, classificação e status. Qualquer investigação de chave divergente ocorre fora do Git, em canal autorizado.

Em 2026-08-25, uma rodada read-only seguiu esse critério em janelas fechadas: 6908 foi comparado com `per` 5 e 10, com 15 entidades em cada conjunto terminal; 6389 foi comparado com `per` 10 e 25, com 34 entidades em cada conjunto terminal. Nos dois casos, os mapas completos de ID e chave natural Data Export coincidiram com o GraphQL V1 terminal. A evidência encerra a cobertura empírica dessas janelas, sem promover uma garantia global de ordem, cursor ou snapshot do fornecedor.

## Critérios para liberar mapper

- Nomes técnicos, tipos e semântica dos campos anunciados documentados no catálogo de contrato.
- ID comparado contra uma amostra segura do identificador canônico legado.
- Campos de contas classificados como IDs técnicos equivalentes, enriquecimento transitório ou dados apenas financeiros.
- Vínculo Frete → Coleta comparado por cardinalidade e receita antes de alterar fatos.
- Resultado registrado em `STATES.md`; caso contrário, os módulos continuam apenas com leitura bruta em sombra.

## Regras de encerramento

- V2-004b/V2-005b exigem fixture sintética, catálogo completo, evidência de payload com os dois `per` aprovados e a delimitação temporal pertinente. `America/Sao_Paulo` é a compatibilidade formal V1/V2; não prova por si só a semântica temporal do fornecedor. No 6389, essa lacuna fica explicitamente `UNVERIFIED`, sem watermark; contas sem correspondência técnica ficam `ABSENT`, sem inferência.
- V2-004c/V2-005c exigem que a fonte respeite o `per` como teto de `id` distintos, com `id` escalar/não nulo, e uma das duas provas de cobertura: ordem total/cursor ou contagem oficial; ou, para uma janela fechada, GraphQL V1 terminal completo mais duas travessias Data Export terminais com `per` distintos e igualdade integral por ID/chave natural. A segunda prova não declara garantia global do fornecedor.
- A identidade de 6908/6389 exige paridade sem divergência crítica não classificada. Vínculo Frete–Coleta, cardinalidade, receita, contas e a comparação 4924 não são substituídos por identidade base e permanecem como gate de domínio em V2-012.
- V2-006b exige filtros, formato, envelope, transporte, paginação e cobertura comprovados no escopo da validação. A sonda direta para no primeiro `429`, sem retry; `Retry-After` só conta como evidência se observado ou especificado, e sua ausência quando não houve `429` não bloqueia o contrato remoto.
- V2-006c permanece aberta até haver `DataSource` aprovado no fluxo de execução e uma execução de sombra real; o DDL local isolado de V2-008 já foi validado.
