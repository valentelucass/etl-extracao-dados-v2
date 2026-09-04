# Contratos históricos Data Export — Coletas e Fretes

> Revisão de governança (2026-09-01): este documento preserva a caracterização das primeiras sondas. A baseline contratual offline vigente de 6908/6389/Usuários está em [`contratos-primeira-onda`](contratos-primeira-onda/README.md); a matriz por campo permanece em `portabilidade/matriz-campos.csv`. Transporte operacional é `GET_WITH_QUERY` fixo e qualquer nova sonda continua bloqueada por V2-041 até autorização externa nova.

Status: contrato de leitura formalizado e revalidado por sondas em 2026-08-25. Os payloads técnicos de Coletas e Fretes foram perfilados. Em janelas fechadas específicas, duas travessias Data Export com `per` distintos, ambas até página vazia, coincidiram integralmente com GraphQL até seu terminal: 15 entidades para 6908 e 34 para 6389. Isso prova cobertura **empírica das janelas testadas** contra o baseline GraphQL, mas não garante ordem, snapshot, unicidade ou cobertura globais do fornecedor. Receita, CT-e, vínculo Frete–Coleta e contas não são promovidos por este contrato de origem; pertencem à paridade de domínio posterior.

## Governança da nova evidência

- O Bloco 1 (infraestrutura local de contrato) está implementado localmente em 2026-08-25: perfil Failsafe opt-in, sonda `/info`, parsers, fixtures estritamente sintéticas, perfil sanitizado e harness GraphQL somente leitura verificam o processo local; nada disso é evidência de contrato remoto.
- Uma nova sonda sanitizada autorizada passa a ser a evidência vigente para `id` e `updated_at`; a contradição histórica fica registrada, sem apagar observações anteriores. Campos, tipos, nulos, formatos, limites e resposta vazia precisam constar do resumo sanitizado da execução.
- O fuso de compatibilidade do cliente é `America/Sao_Paulo`: ele já está declarado como `api.dataexport.timezone` no legado para janelas Data Export e o V2 exige o mesmo identificador IANA em `API_DATAEXPORT_TIMEZONE`. A decisão vem da configuração operacional versionada, não do fuso da máquina ou da inferência de um offset no payload; ela **não** prova como o fornecedor interpreta os limites temporais do filtro. Qualquer alteração exige nova validação de migração.
- A execução Failsafe exige `-Pcontract-tests -Dcontract.tests.enabled=true`. Sem qualquer variável `CONTRACT_*`, a IT remota é ignorada sem abrir rede; uma configuração parcial é recusada antes da conexão e nunca há fallback implícito para `API_*`.
- Quando o harness Java estiver autorizado, seu único artefato remoto é `target/contract-evidence/<run-id>/summary.json`, transitório e fora do Git, sem token, URL de tenant, payload, ID ou hash de ID. As sondas diretas por `curl.exe` não gravam arquivo: emitem somente o resumo sanitizado no stdout. Totais e classificação revisados podem ser incorporados ao catálogo e ao runbook após aprovação.
- A paridade transitória usa `ContractRemoteExecution` e o harness do V2 definido no [ADR 0005](../adr/0005-harness-graphql-read-only-paridade.md), nunca comandos do V1. Para cada entidade, `CONTRACT_6908_MAX_CALLS` ou `CONTRACT_6389_MAX_CALLS` limita conjuntamente as chamadas Data Export e GraphQL; não há banco, CLI, persistência, expurgo ou escrita nessa comparação.
- Os dois `per` configurados são positivos, distintos e previamente aprovados. O harness usa ambos nas amostras e travessias repetidas; a comparação transitória com GraphQL usa o primeiro valor configurado, sem tamanho imposto localmente. As flags opcionais `CONTRACT_SOURCE_TIMEZONE_FORMALLY_CONFIRMED`, `CONTRACT_<template>_ORDER_TOTAL_OR_CURSOR_CONFIRMED` e `CONTRACT_<template>_COVERAGE_CONFIRMED` começam em `false`; registram declarações formais, não substituem a prova observada.
- Não existe autorização remota vigente enquanto V2-041 permanecer em `EXTERNAL_HOLD`. Em uma rodada futura nominalmente autorizada, use somente `GET_WITH_QUERY` e os probes allowlisted pelo `AGENTS.md`, mantendo payloads/IDs em memória e emitindo apenas contagens e flags sanitizadas. Nos templates 6908 e 6389, `per` limita entidades distintas pelo `id`, não linhas físicas do relatório detalhado. Uma página com mais linhas físicas que `per` só é válida quando todos os registros tiverem `id` escalar não nulo e o conjunto de `id` distintos tiver tamanho menor ou igual ao `per`; caso contrário, a rodada falha e é encerrada sem adaptação do resultado.
- A sonda de perfil emite `technical_field_names` e `field_profiles` por metadata/página — apenas presença, nulos, tipos JSON e formatos temporais; nenhum valor é emitido. A rodada atual teve 31/31 nomes técnicos seguros e 124 perfis de página em 6908, e 109/109 nomes técnicos seguros e 436 perfis em 6389. Em ambos os templates, as quatro páginas de amostra (`per` 2 e 5) foram válidas, tiveram limite de entidades verificável e incluíram o perfil de `updated_at`.
- A revalidação de 2026-08-25 confirmou páginas 1/2 de 6908 e 6389 para `per` 2, 5 e 10, com JSON válido e quantidade física exata. Em travessia limitada com `per=100`, o 6908 teve três páginas não vazias e uma terminal vazia (246 entidades em 258 linhas físicas); o 6389 teve oito páginas não vazias e uma terminal vazia (769 entidades em 830 linhas físicas). Em ambos, não houve sobreposição de `id` nem de chave natural entre páginas. Isso confirma a semântica de limite por entidade, expansão física e término observado; isoladamente, não é garantia global de ordenação, cursor, snapshot ou contagem oficial.
- A sonda cURL de identidade percorreu Data Export e GraphQL até os respectivos terminais em janelas fechadas, mantendo IDs e chaves naturais apenas em memória. Para 6908, as travessias Data Export com `per` 5 e 10 e o GraphQL terminal tiveram 15 entidades cada; para 6389, `per` 10 e 25 e o GraphQL terminal tiveram 34 entidades cada. Em cada comparação, os mapas de `id`/chave natural foram iguais, sem nulo, duplicação ou limite de entidades inválido. Isso encerra a cobertura empírica das respectivas janelas contra GraphQL. Não é uma garantia global de snapshot, ordem ou unicidade do fornecedor. Vínculo, receita, CT-e e contas permanecem deliberadamente fora do contrato de origem e exigem regra de domínio em V2-012.
- A sonda 4924 é opcional e exclusivamente de teste. Ela só é montada com o conjunto completo `CONTRACT_4924_*`, tem teto próprio, uma amostra fechada `per=1`, transporte aprovado sem fallback/retry e compartilha o stop-token da execução. Não entra no enum de templates de produção, no runtime, no banco ou em uma terceira vertical.

## Coletas — 6908

| Item | Contrato atual |
|---|---|
| Raiz | `picks` |
| Filtro obrigatório | Consulta: `picks.request_date`; metadata `/info`: `request_date` (`yyyy-MM-dd - yyyy-MM-dd`) |
| Escopo de alteração | Consulta: `scopes.by_updated_at`; metadata `/info`: `by_updated_at` (`yyyy-MM-dd HH:mm:ss - yyyy-MM-dd HH:mm:ss`) |
| Ordem conhecida | `sequence_code asc` produziu três páginas não vazias e uma terminal vazia com `per=100`, sem sobreposição de `id`/chave natural. Em outra janela fechada, `per` 5 e 10 chegaram ao terminal e coincidiram com GraphQL em 15 entidades; isto é cobertura empírica dessa janela, não prova de ordenação total, cursor ou snapshot global |
| Identidade observada | `id` presente e não nulo como número JSON e ratificado como source key escopada; duas travessias Data Export (`per` 5 e 10) e GraphQL terminal tiveram os mesmos mapas de source IDs correlacionados por business key em uma janela fechada de 15 entidades, sem convertê-los em `canonical_id` |
| Chave de negócio observada | `sequence_code`, presente como número JSON e 1:1 com o ID na janela comparada; não é ainda uma garantia global de estabilidade para `MERGE` |
| Atualização observada | `updated_at` presente e não nulo, com formato textual `offset_date_time`; o cliente forma filtros com `America/Sao_Paulo` por compatibilidade explícita V1/V2. Isso não certifica a semântica temporal adotada pelo fornecedor; a monotonicidade continua pendente. |
| Manifesto observado | `pck_mik_mft_crn_psn_nickname`, `pck_mik_mft_sequence_code`, `pck_mik_mft_vie_license_plate` e `pck_mik_mft_vie_vee_name`; todos vieram nulos na única linha, portanto não há evidência de cobertura |
| Paridade exigida | A paridade histórica `6908.sequence_code → GraphQL source_id` foi concluída em uma janela fechada: duas travessias Data Export com `per` distintos, GraphQL terminal, relação 1:1, volume igual e ausência de nulos/duplicatas. Isso é evidência de crosswalk da janela, não identidade técnica global. A matriz de campos equivalentes pertence ao mapper de V2-010. |

## Fretes — 6389

| Item | Contrato atual |
|---|---|
| Raiz | `freights` |
| Filtro obrigatório | `freights.service_at` (`yyyy-MM-dd - yyyy-MM-dd`) |
| Escopo de alteração | `scopes.by_updated_at` (`yyyy-MM-dd HH:mm:ss - yyyy-MM-dd HH:mm:ss`) |
| Ordem de paridade | `corporation_sequence_number asc`, alinhada ao comportamento útil do sidecar legado e à matriz canônica. Em `per=100`, oito páginas não vazias terminaram em página vazia, sem sobreposição de `id`/chave natural. Em outra janela fechada, `per` 10 e 25 chegaram ao terminal e coincidiram com GraphQL em 34 entidades; isto é cobertura empírica dessa janela, não prova de ordenação total, cursor ou snapshot global. A ordem não substitui `id`. |
| Identidade observada | `id` presente e não nulo como número JSON e ratificado como source key escopada; duas travessias Data Export (`per` 10 e 25) e GraphQL terminal tiveram os mesmos mapas de source IDs correlacionados por business key em uma janela fechada de 34 entidades, sem concluir vínculo, canonical ID ou receita |
| Chave de negócio candidata | `corporation_sequence_number` foi 1:1 com o ID na janela comparada; a alegação de estabilidade e escopo global ainda precisa de preflight, portanto não é chave de `MERGE` |
| Atualização observada | `updated_at` exposto no `/data`, não nulo na amostra e com formato textual `offset_date_time`; `/info` o classifica como filtro `datetime`. O cliente usa `America/Sao_Paulo` por compatibilidade explícita V1/V2. A semântica e a monotonicidade por entidade são `UNVERIFIED`; o V2 não o usa como watermark nem tem procedure de avanço nesta fase. |
| Número de referência | `reference_number` exposto no `/info` e no `/data`, mas vazio em 10/10 linhas da amostra; não assumir cobertura |
| Vínculo candidato observado | `fit_p_m_pck_sequence_code` presente na amostra; não é FK e não substitui `pickItemId` por inferência. Cardinalidade e receita pertencem à paridade de domínio V2-012. |
| Performance preservada | `finished_at` e `fit_dpn_performance_finished_at` são os campos úteis comprovados do sidecar legado e estão nas fixtures V2-025a. Formato ambíguo permanece `BUSINESS_DECISION_PENDING`; a V2 não porta a heurística US/BR. |
| Campos de contas | `ABSENT` para equivalência técnica: nenhum campo Data Export foi designado como `accountingCreditId` ou `accountingCreditInstallmentId`; banco, agência, conta e `fit_ant_*` não são inferidos. |
| Limite de paridade do contrato | A paridade de source IDs contra GraphQL foi concluída somente para a janela de 34 entidades, com dois `per` Data Export distintos. `fretes.id` GraphQL e `6389.id` permanecem source IDs; o `canonical_id` é surrogate V2 e qualquer equivalência exige crosswalk versionado. `corporation_sequence_number` é alias 0..N no legado e requer preflight de nulo, formato, ambiguidade e estabilidade antes de resolver uma relação. A relação 6389–4924 não é requisito deste contrato de origem. |

## Evidência complementar — Fatura por Cliente 4924

O template 4924 não é um terceiro módulo do V2. Ele é apenas uma fonte complementar a validar para o vínculo financeiro de Fretes.

| Item | Evidência atual |
|---|---|
| ID de frete | `id` presente e preenchido em 10/10 linhas, como número JSON; tem o mesmo nome e tipo observado no 6389, mas a igualdade entre conjuntos ainda não foi testada |
| Dados financeiros | Campos `fit_ant_*`, incluindo banco, agência e conta, presentes na amostra |
| Granularidade | Em uma amostra de 10 linhas, os 10 IDs de frete eram distintos enquanto `fit_ant_document` tinha um único valor. Não usar documento de fatura como chave de frete; confirmar cardinalidade em janela fechada |
| Preparação local | A sonda auxiliar sanitizada está pronta e validada sem rede. Na execução controlada de 2026-08-25, 6908 terminou sua janela e a chamada seguinte a 6389 recebeu `HTTP_429`; a sonda parou sem retry, fallback, GraphQL ou persistência. Portanto não há comparação remota de CT-e, vínculo ou matriz financeira a alegar. |

## Regras comuns

1. A âncora de data de negócio é obrigatória; `scopes.by_updated_at` não funciona isoladamente.
2. O formato de atualização não aceita offset. O cliente converte usando o identificador IANA `America/Sao_Paulo`, declarado pela configuração operacional do legado e obrigatório no V2; não pode cair no timezone local da máquina. Essa compatibilidade não atesta a semântica com que a fonte aplica o filtro.
3. A extração deve ser particionada pela data de negócio e deduplicada de forma idempotente. `scopes.by_updated_at` não pode virar checkpoint enquanto `updated_at` estiver `UNVERIFIED`.
4. Não avançar watermark após página ou partição incompleta. Não inferir exclusão a partir de uma janela incremental.
5. Antes de qualquer mapper definitivo, validar o campo `id`, a cobertura histórica, tipos, nulos e paginação. Campos de contas permanecem `ABSENT` até designação técnica explícita.
6. Antes de usar 4924 como enriquecimento financeiro, comparar conjuntos de `6389.id` e `4924.id` em janela fechada e confrontar ambos com GraphQL, como trabalho de paridade de domínio.
7. A comparação de CT-e normaliza somente uma chave válida de 44 dígitos; valor malformado, nulo ou campo sem equivalência mantém a relação aberta. `fit_ant_*` não é promovido a `accountingCredit*` por semelhança de nome.

## Plataforma de paginação em sombra — V2-006a

- `DataExportPageStreamer` percorre páginas serialmente e entrega cada resposta ao consumidor de imediato; não acumula o payload da execução. O consumidor recebe `DataExportReadPage`, com `execution_id`, requisição, página e horário de leitura para staging futuro.
- Cada uso declara `maxPages`, `maxRecords` e `maxPageSize`; o HTTP também limita o corpo a `API_DATAEXPORT_MAX_RESPONSE_BYTES` (10 MiB por padrão) antes de desserializar JSON, inclusive em resposta chunked. `maxRecords` continua protegendo a quantidade de linhas físicas, mas o limite de `per` é validado por entidades distintas de `id`: linhas detalhadas repetidas são aceitas somente quando cada `id` é escalar/não nulo e os `id` distintos não ultrapassam o pedido. Limite de entidade não verificável, excesso de qualquer limite ou falha do gateway, consumidor ou auditoria encerram a execução sem evento de sucesso.
- A requisição paginada exige `order_by` não vazio. Enquanto o contrato remoto não tiver ordem total, cursor ou semântica de página curta confirmados, somente uma **página vazia** encerra a travessia. Página curta não comprova fim de snapshot; a próxima página continua obrigatória ou a execução falha no limite.
- Mesmo após uma página vazia, o resultado do runtime é `LOCAL_TERMINAL_UNVERIFIED`: por si só ele não prova cobertura, snapshot, watermark, reconciliação ou expurgo. Uma comparação cURL independente pode provar cobertura empírica de uma **janela fechada específica** quando duas travessias Data Export com `per` distintos coincidem integralmente com o GraphQL terminal; ela não transforma o runtime em prova de snapshot ou cobertura global.
- A auditoria expõe somente `execution_id`, template, janelas, página, volume, horários e categoria da falha. Ela não recebe payload de negócio, segredo ou a mensagem original da exceção. O `execution_id` é associado ao MDC durante a travessia e o valor anterior é restaurado no fim. A implementação durável em `ctl` foi aplicada e exercitada no banco local isolado; a composição de runtime continua opt-in.
- O diagnóstico de HTTP retém para contrato apenas o booleano de `Content-Type` JSON e a presença de `Retry-After`; `toString()`, erro de JSON e configuração redigem corpo, valor de cabeçalho, URI e causa potencialmente remota. `Retry-After` não é aplicado automaticamente sem política remota confirmada.
- O `CircuitBreakingDataExportGateway` é um decorador por template. Apenas indisponibilidade classificada (`429`, `5xx` exceto `501`, ou I/O após retry) abre o circuito; erro de contrato ou JSON encerra e reinicia a sequência de indisponibilidades. Após o cooldown há uma única sonda half-open, protegida contra conclusões antigas em voo.
- O caminho operacional usa somente `GET_WITH_QUERY` e `PREFERRED_TRANSPORT_ONLY`; não troca método/corpo durante a execução. Resposta `204`, objeto JSON vazio ou envelope raiz com `error`/`errors` — inclusive se vier acompanhado de `data` — não são aceitos como registro.
- Como o gateway envia Bearer token, a URL base exige HTTPS fora de loopback, host explícito e não aceita `userinfo`, query ou fragmento. HTTP é aceito apenas para testes locais em `localhost`, `127.0.0.1` ou `::1`. O fuso da fonte precisa ser um identificador IANA disponível no JDK, não offset fixo.
- Esta camada é infraestrutura de leitura em memória. Não representa cobertura completa, watermark avançado, deduplicação, persistência, paridade ou autorização de cutover.

## Evidência ainda requerida para regras de domínio

| Folha | Evidência remota obrigatória |
|---|---|
| V2-011 | Antes de extração incremental: estratégia por data de negócio ou prova formal de semântica/monotonicidade de `updated_at`; antes de qualquer promoção, preflight de chaves e mapeamento aprovado. |
| V2-012 | Cardinalidade Frete–Coleta, receita, CT-e, 4924 e contas, sempre sem supor igualdade de ID, documento de fatura ou equivalência por nome. |

V2-004c e V2-005c estão fecháveis pela cobertura empírica acima: para cada janela testada, os dois tamanhos de página Data Export chegaram ao terminal e produziram exatamente o mesmo mapa de identidade do GraphQL terminal. Ordem total, cursor, contagem oficial, snapshot e unicidade **globais** do fornecedor continuam sendo endurecimentos futuros; não são inferidos dessa comparação nem são pré-requisito adicional para esse fechamento por janela.
