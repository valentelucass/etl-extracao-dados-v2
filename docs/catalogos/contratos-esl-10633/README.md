# Contrato offline — Data Export 10633 (Inventário)

Este diretório é a fatia `V2-025b` do template Data Export `10633`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Inventário.

## Limite deliberado

Este contrato não decide identidade, unicidade, estabilidade, escopo de tenant, grão físico,
cardinalidade de filhos, frescor, dedupe, persistência, relacionamento por minuta, parsing,
quarentena, comprovante, reducers, fatos ou publicação. A observação de `sequence_code` repetido
em linhas físicas é registrada somente como candidato de agregado; a decisão sobre raiz, filhos e
seus componentes naturais pertence exclusivamente a `V2-009b`. Os reducers de valor, peso e
volume ficam exclusivamente na vertical `V2-031`.

As fixtures não são respostas da ESL. Seus valores, sequências, datas e referências de invoice são
sintéticos e existem apenas para exercitar a forma local de uma expansão física. Elas não provam
tipos, nulidade, paginação remota, ordenação total, snapshot, completude, identidade ou política do
fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. O legado usava `GET` com corpo,
  comportamento que a V2 não porta.
- Filtro candidato de negócio: `search[check_in_orders][started_at]` no formato civil
  `AAAA-MM-DD - AAAA-MM-DD`; a tradução de `[início, fimExclusivo)` para as bordas da fonte
  continua pendente.
- Ordenação histórica de paridade: `sequence_code asc`. Ela não substitui chave, cursor,
  repetibilidade ou prova de completude.
- A observação sanitizada de 15/07/2026 com `per=3` tinha 27 linhas físicas e três valores de
  `sequence_code`; mapeamentos de invoices expandiram a raiz. Nenhum `id` foi observado.
- O `/info` observado informou 26 campos e sete filtros, sem tipos versionados. O catálogo lista
  apenas o subconjunto de nomes sustentado por evidência estática e não afirma que seja completo.
- A baseline executável da V1 usa `per=100`, timeout de 90 s e caps de 500 páginas/10.000 linhas;
  esses valores são evidência histórica, não defaults da V2.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto, sweep,
desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é autorizada por
este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical `V2-031` e dos
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
