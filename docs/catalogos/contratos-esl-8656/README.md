# Contrato offline — Data Export 8656 (Localização de Cargas)

Este diretório é a fatia `V2-025b` do template Data Export `8656`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Localização de Cargas.

## Limite deliberado

Este contrato não decide identidade, unicidade, estabilidade, escopo de tenant, grão físico,
frescor, dedupe, persistência, relacionamento com Fretes, status, conversão numérica, região,
fatos ou publicação. A observação de `corporation_sequence_number` em três linhas físicas é
registrada somente como chave candidata; sua decisão pertence exclusivamente a `V2-009b`.
O nome `sequence_number` é registrado apenas como ordenação histórica aceita pela V1: ele não
foi publicado na resposta observada e não é promovido a campo ou chave da fonte.

As fixtures não são respostas da ESL. Seus valores, sequências, datas e textos são sintéticos e
existem apenas para exercitar a forma local de uma resposta sem expansão observada. Elas não
provam tipos, nulidade, paginação remota, ordenação total, snapshot, completude ou política do
fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. O legado usava `GET` com corpo,
  comportamento que a V2 não porta.
- Filtro candidato de negócio: `search[freights][service_at]` no formato civil
  `AAAA-MM-DD - AAAA-MM-DD`; a tradução de `[início, fimExclusivo)` para as bordas da fonte
  continua pendente.
- Ordenação histórica de paridade: `sequence_number asc`. Ela não substitui chave, cursor,
  repetibilidade ou prova de completude e o respectivo campo não veio no payload observado.
- A observação sanitizada de 15/07/2026 com `per=3` tinha três linhas físicas e três valores de
  `corporation_sequence_number`, sem `id` ou `sequence_number` publicado.
- O `/info` observado informou 24 campos e oito filtros, sem tipos versionados. O catálogo lista
  somente um subconjunto de nomes sustentado por evidência estática e não afirma que seja
  completo.
- Uma tentativa histórica de avançar para uma segunda página recebeu `429` e parou. Isso é
  evidência de throttling naquele cenário, não de quota, repetibilidade ou política global do
  fornecedor.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto,
sweep, desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é
autorizada por este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical
`V2-028` e dos gates comuns aplicáveis.

`V2-025d` é uma atividade futura separada, bloqueada por `V2-041` e por reconfirmação humana de
janela/teto. Este catálogo não é autorização de rede nem substituto para essa rodada.

## Integridade local

O [manifesto](manifesto.json) usa vocabulários fechados, declara a proveniência de cada aspecto e
registra SHA-256 de todas as fixtures. Os fingerprints semântico, de metadata e de resposta são o
SHA-256 UTF-8 (sem BOM) dos respectivos materiais em `fingerprintInputs`; o de release é o
SHA-256 UTF-8 de `release|semantics|metadata|response`, nessa ordem. A validação desta fatia
confere JSON, hashes, vocabulários e bloqueios documentados, sem executar código de produção ou
acessar a rede.
