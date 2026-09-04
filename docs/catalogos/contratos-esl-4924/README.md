# Contrato offline — Data Export 4924 (Faturas por Cliente)

Este diretório é a fatia `V2-025b` do template Data Export `4924`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Faturas por Cliente.

## Limite deliberado

Este contrato não decide precedência fiscal, presença ou seleção entre CT-e e NFS-e, CNPJ, status,
valores, títulos, frescor, dedupe, persistência, identidade, unicidade, estabilidade, escopo de
tenant, grão, aliases, rekey, crosswalk título–frete, cardinalidade, relações, filhos, reducer,
schema, fato ou publicação. O `id` observado é registrado somente como candidato de source key da
linha. `unique_id` não veio no payload observado; a ordenação histórica que o menciona não prova
sua presença, identidade ou contrato. Qualquer título lógico e sua relação com documentos e fretes
permanecem para `V2-009b` e `V2-030`.

As fixtures não são respostas da ESL. Seus identificadores e datas são sintéticos e existem somente
para exercitar a forma local de três linhas físicas com três candidatos `id`. Elas não provam tipos,
nulidade, paginação remota, semântica de `per`, ordenação total, snapshot, completude, identidade ou
política do fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. O legado usava `GET` com corpo,
  comportamento que a V2 não porta.
- Filtro candidato de negócio: `search[freights][service_at]` no formato civil
  `AAAA-MM-DD - AAAA-MM-DD`; a tradução de `[início, fimExclusivo)` para as bordas da fonte
  continua pendente.
- Ordenação histórica de paridade: `unique_id asc`. Ela não substitui chave, cursor,
  repetibilidade, presença de `unique_id` ou prova de completude.
- A observação sanitizada de 15/07/2026 com `per=3` tinha três linhas físicas e três valores
  inteiros de `id`; `unique_id` não foi publicado e o documento não é universal.
- O `/info` observado informou 52 campos e 17 filtros, sem tipos versionados. O catálogo lista
  somente o subconjunto mínimo sustentado pela evidência e não afirma que ele seja completo.
- A baseline executável da V1 usa `per=100`, timeout de 60 s e caps de 1.200 páginas/150.000
  linhas; esses valores são evidência histórica, não defaults da V2.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto, sweep,
desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é autorizada por
este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical `V2-030` e dos
gates comuns aplicáveis.

O caso em que CT-e e NFS-e coexistem permanece `UNRESOLVED`: esta fatia não escolhe precedência,
não mapeia documento fiscal e não cria crosswalk. Isso exige caracterização e decisão explícita no
gate próprio, antes de qualquer core, fato ou publicação.

`V2-025d` é uma atividade futura separada, bloqueada por `V2-041` e por reconfirmação humana de
janela/teto. Este catálogo não é autorização de rede nem substituto para essa rodada.

## Integridade local

O [manifesto](manifesto.json) usa vocabulários fechados, declara a proveniência de cada aspecto e
registra SHA-256 de todas as fixtures. Os fingerprints semântico, de metadata e de resposta são o
SHA-256 UTF-8 (sem BOM) dos respectivos materiais em `fingerprintInputs`; o de release é o
SHA-256 UTF-8 de `release|semantics|metadata|response`, nessa ordem. A validação desta fatia
confere JSON, hashes, vocabulários, contagens sintéticas e bloqueios documentados, sem executar
código de produção ou acessar a rede.

