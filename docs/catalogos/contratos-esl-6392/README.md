# Contrato offline — Data Export 6392 (Sinistros)

Este diretório é a fatia `V2-025b` do template Data Export `6392`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Sinistros.

## Limite deliberado

Este contrato não decide identidade, unicidade, estabilidade, escopo de tenant, grão físico,
frescor, horas, timezone de campos, dedupe, valores financeiros, reducer, persistência, relações,
filhos, schema, fato ou publicação. `sequence_code` é registrado somente como candidato de raiz.
As relações de minuta e invoice observadas não compõem identificador, não criam filhos e não
alteram o grão sem prova de colisão, cardinalidade e estabilidade em `V2-009b` e `V2-032`.

O hash composto do legado não é identidade V2 e não é portado por este contrato. A escolha de
`treatment_at` ou `opening_at_date` para frescor e quaisquer conversões de data/hora permanecem
fora desta fatia. Valores, solução e tipo de tratativa também permanecem para a vertical própria.

As fixtures não são respostas da ESL. Seus identificadores e relações são sintéticos e existem
somente para exercitar a forma local de duas linhas físicas com dois candidatos `sequence_code`.
Elas não provam tipos, nulidade, paginação remota, semântica de `per`, ordenação total, snapshot,
completude, identidade ou política do fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. O legado usava `GET` com corpo,
  comportamento que a V2 não porta.
- Filtro candidato de negócio: `search[insurance_claims][opening_at_date]` no formato civil
  `AAAA-MM-DD - AAAA-MM-DD`; a tradução de `[início, fimExclusivo)` para as bordas da fonte
  continua pendente.
- Ordenação histórica de paridade: `sequence_code asc`. Ela não substitui chave, cursor,
  repetibilidade ou prova de completude.
- A observação sanitizada de 15/07/2026 com `per=3` tinha duas linhas físicas e dois valores de
  `sequence_code`, sem `id` publicado; relações de minuta e invoice estavam presentes, mas sem
  cardinalidade ou identidade comprovadas.
- O `/info` observado informou 44 campos e seis filtros, sem tipos versionados. O catálogo lista
  somente o subconjunto mínimo sustentado por evidência estática e não afirma que ele seja completo.
- A baseline executável da V1 usa `per=100`, timeout de 60 s e caps de 500 páginas/10.000 linhas;
  esses valores são evidência histórica, não defaults da V2.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto, sweep,
desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é autorizada por
este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical `V2-032` e dos
gates comuns aplicáveis.

`V2-025d` é uma atividade futura separada, bloqueada por `V2-041` e por reconfirmação humana de
janela/teto. Este catálogo não é autorização de rede nem substituto para essa rodada.

## Integridade local

O [manifesto](manifesto.json) usa vocabulários fechados, declara a proveniência de cada aspecto e
registra SHA-256 de todas as fixtures. Os fingerprints semântico, de metadata e de resposta são o
SHA-256 UTF-8 (sem BOM) dos respectivos materiais em `fingerprintInputs`; o de release é o
SHA-256 UTF-8 de `release|semantics|metadata|response`, nessa ordem. A validação desta fatia
confere JSON, hashes, vocabulários, contagens sintéticas e bloqueios documentados, sem executar
código de produção ou acessar a rede.
