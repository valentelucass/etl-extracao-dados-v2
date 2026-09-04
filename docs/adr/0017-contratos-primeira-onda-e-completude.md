# ADR 0017 — Contratos versionados da primeira onda e gate de completude

- Status: Aceito e implementado offline por V2-025a
- Data: 2026-08-31

## Contexto

Os clientes Data Export e GraphQL já possuíam transporte, parsing, limites e drift gate, mas seus
releases eram montados ad hoc em testes. As propriedades que mudam o conjunto lido — filtro fixo,
ordem, `per`, raiz e semântica de paginação — não estavam todas ligadas ao fingerprint runtime.
Além disso, evidência sintética, classificação contratual e prova de completude eram descritas com
vocabulários sobrepostos.

A evidência permitida neste bloco é exclusivamente local, sintética e histórica sanitizada.
V2-041 impede qualquer nova chamada ou uso de credencial. Nenhuma das três fontes possui garantia
versionada do fornecedor ou oráculo independente aprovado para snapshot completo.

## Decisão

O manifesto V2-025a contém três contratos independentes, com versões, fixtures limitadas, hashes e
fingerprints de semântica, metadata, resposta e release. Cada dimensão usa somente
`PROVEN`, `TRANSITIONAL`, `ABSENT` ou `BUSINESS_DECISION_PENDING`; a proveniência permanece em outro
campo. A configuração runtime incorpora o fingerprint semântico dos templates/documentos, de modo
que alteração em `enabled=true`, máximo 20, filtros, raiz, ordem, chave, transporte ou terminalidade
exija novo binding V2-044.

Os releases consumíveis não dependem de recursos de teste: catálogos produtivos constroem os shapes
estruturais fixos dos templates `6908`/`6389` e do documento GraphQL `individual`. No Data Export, o
gate verifica antes de I/O tanto `per` no intervalo `1..100` quanto a ordem exata versionada, e a
configuração aceita somente o fuso de origem `America/Sao_Paulo`. Os limites de páginas e registros
por ocorrência continuam obrigatórios no streamer; os valores produtivos e sua governança pertencem
a V2-022 e não são apresentados por este ADR como evidência contratual do fornecedor.

No `6389`, a ordem de paridade é `corporation_sequence_number asc`, como no comportamento útil do
sidecar legado e na matriz canônica. `id` permanece a chave auditável; ordenação nunca substitui
identidade. `finished_at` e `fit_dpn_performance_finished_at` ficam cobertos por fixture, mas sua
normalização de negócio não é antecipada. A ambiguidade de datas permanece decisão pendente e deve
falhar/quarentenar, sem heurística US/BR.

O estado comum é `BLOCKED_NO_COMPLETENESS_PROOF`. Ele permite somente `SHADOW_UPSERT` de registros
observados, sujeito aos gates da vertical, e nega sweep/desativação/cutover. Página vazia e
`hasNextPage=false` continuam terminais locais não verificados. Cursor de Usuários não é checkpoint
durável; sem prova de TTL/retomada, replay começa na página 1 com staging novo.

A autorização emitida pelo guard carrega o efeito solicitado. Após V2-033, o kernel SQL de Usuários
aceita somente `SHADOW_UPSERT` e rejeita os demais efeitos antes de abrir conexão. Isso não contorna
outros gates: publicação, sweep/desativação e cutover de Usuários permanecem bloqueados, assim como
qualquer promoção dos sidecars de Coletas/Fretes; a evidência sintética não prova completude remota.

A tradução do intervalo interno `[start,endExclusive)` para a fonte inclusiva não é inventada.
Formato, inclusão de bordas, precisão e DST permanecem `BUSINESS_DECISION_PENDING` até fixture ou
garantia externa apropriada. `scopes.by_updated_at` continua apenas overlap complementar.

## Consequências

- Mudanças de semântica fora do payload passam a invalidar o fingerprint runtime e o binding.
- Fixtures com dois valores de `per` validam limite por IDs distintos, expansão física e terminal
  local sem transformar o teste em prova global.
- `individual` formaliza `enabled=true`, máximo 20 e `id` JSON integral ou textual não vazio no wire
baseline transitório. O `Long` legado prova o destino interno, mas Jackson pode coercer texto
numérico; portanto o tipo do token remoto e sua canonicalização ficam explicitamente para V2-009a,
em vez de serem inventados. A classificação externa dos campos e a nullability de `name` continuam
transitórias. `updatedAt`, incremental temporal, ordenação e template `9901` permanecem
explicitamente ausentes.

O release V2-044 aceita uma união fechada de tipos escalares para a chave quando o contrato a declara
explicitamente; presença obrigatória, não nulidade e cardinalidade escalar continuam invariantes.
Essa união entra no fingerprint e não equivale a canonicalização, unicidade ou permissão de promoção.
- V2-009a pode tratar identidade e tenant scope como próximo gate, sem receber uma falsa prova de
  unicidade global deste bloco.
- V2-025d e qualquer evidência externa continuam condicionados a V2-041 e autorização própria.

## Alternativas rejeitadas

- **Usar `service_at asc` no 6389:** diverge do comportamento funcional e da matriz vigente.
- **Declarar completude pela página terminal:** paginação não é oráculo de cobertura.
- **Congelar `id/name` como tipos remotos pela fixture:** fixture sintética prova o parser, não o
  schema do fornecedor.
- **Converter end-exclusive subtraindo um segundo ou dia:** cria lacuna/duplicação sem prova de
  borda e precisão.
- **Copiar fallback, acúmulo integral ou heurística temporal do legado:** viola transporte fixo,
  memória limitada e tratamento explícito de dados ambíguos.
