# Contrato offline — Data Export 6906 (Cotações)

Este diretório é a fatia `V2-025b` do template Data Export `6906`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Cotações.

## Limite deliberado

Este contrato não decide identidade, unicidade, estabilidade, escopo de tenant, grão físico,
frescor, reducer, persistência, tarifa mínima, fatos ou publicação. A observação de
`sequence_code` em três linhas físicas é registrada somente como chave candidata; sua decisão
pertence exclusivamente a `V2-009b`.

As fixtures não são respostas da ESL. Seus valores, sequências, datas e textos são sintéticos e
existem apenas para exercitar a forma local de uma resposta sem expansão observada. Elas não
provam tipos, nulidade, paginação remota, ordenação total, snapshot, completude ou política do
fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. O legado usava `GET` com corpo,
  comportamento que a V2 não porta.
- Filtro candidato de negócio: `search[quotes][requested_at]` no formato civil
  `AAAA-MM-DD - AAAA-MM-DD`; a tradução de `[início, fimExclusivo)` para as bordas da fonte
  continua pendente.
- Ordenação histórica de paridade: `sequence_code asc`. Ela não substitui chave, cursor,
  repetibilidade ou prova de completude.
- A observação sanitizada de 15/07/2026 com `per=3` tinha três linhas físicas e três valores de
  `sequence_code`, sem `id` publicado.
- O `/info` observado informou 37 campos e seis filtros, sem tipos versionados. O catálogo lista
  apenas um subconjunto de nomes sustentado por evidência estática e não afirma que seja completo.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto,
sweep, desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é
autorizada por este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical
`V2-027` e dos gates comuns aplicáveis.

`V2-025d` é uma atividade futura separada, bloqueada por `V2-041` e por reconfirmação humana de
janela/teto. Este catálogo não é autorização de rede nem substituto para essa rodada.

## Integridade local

O [manifesto](manifesto.json) usa vocabulários fechados, declara a proveniência de cada aspecto e
registra SHA-256 de todas as fixtures. Os fingerprints semântico, de metadata e de resposta são o
SHA-256 UTF-8 (sem BOM) dos respectivos materiais em `fingerprintInputs`; o de release é o
SHA-256 UTF-8 de `release|semantics|metadata|response`, nessa ordem. A validação desta fatia
confere JSON, hashes, vocabulários e bloqueios documentados, sem executar código de produção ou
acessar a rede.
