# Contrato offline — Data Export 8636 (Contas a Pagar)

Este diretório é a fatia `V2-025b` do template Data Export `8636`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Contas a Pagar.

## Limite deliberado

Este contrato não decide regra financeira, competência, status de pagamento, fornecedor, filial,
centro de custo, plano de contas, valores, catálogo derivado, frescor, dedupe, persistência,
identidade, unicidade, estabilidade, escopo de tenant, grão, relacionamento raiz–parcela,
cardinalidade, reducer, schema, fato ou publicação. `ant_ils_sequence_code` é registrado somente
como candidato de parcela; a identidade da raiz `accounting_debit` não foi exposta. As decisões de
identidade/grão pertencem a `V2-009b` e qualquer modelagem, relação financeira ou reducer pertence
à vertical `V2-029`.

As fixtures não são respostas da ESL. Seus valores e datas são sintéticos e existem apenas para
exercitar a forma local de cinco linhas físicas com cinco candidatos de parcela. Elas não provam
tipos, nulidade, paginação remota, semântica de `per`, ordenação total, snapshot, completude,
identidade ou política do fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. O legado usava `GET` com corpo,
  comportamento que a V2 não porta.
- A caracterização da V1 combina, na mesma requisição, `search[accounting_debits][issue_date]` e
  `search[accounting_debits][created_at]`. Um futuro contador ou oráculo deve reproduzir ambos os
  filtros; omitir um deles compara outro conjunto.
- Ordenação histórica de paridade: `issue_date desc`. Ela não substitui chave, cursor,
  repetibilidade ou prova de completude.
- A observação sanitizada de 15/07/2026 com `per=3` tinha cinco linhas físicas e cinco valores de
  `ant_ils_sequence_code`, sem identificador da raiz contábil. Portanto, `physical_rows` é a única
  métrica observada; `source_entities` e `distinct_root_keys` permanecem `UNVERIFIED`.
- O `/info` observado informou 28 campos e nove filtros, sem tipos versionados. O catálogo lista
  somente um subconjunto de nomes sustentado por evidência estática e não afirma que seja completo.
- A baseline executável da V1 usa `per=100`, timeout de 60 s e caps de 500 páginas/10.000 linhas;
  seus retries, tentativas com `per=50/25` e timeouts maiores não são defaults da V2.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto,
sweep, desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é
autorizada por este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical
`V2-029` e dos gates comuns aplicáveis.

`V2-025d` é uma atividade futura separada, bloqueada por `V2-041` e por reconfirmação humana de
janela/teto. Este catálogo não é autorização de rede nem substituto para essa rodada.

## Integridade local

O [manifesto](manifesto.json) usa vocabulários fechados, declara a proveniência de cada aspecto e
registra SHA-256 de todas as fixtures. Os fingerprints semântico, de metadata e de resposta são o
SHA-256 UTF-8 (sem BOM) dos respectivos materiais em `fingerprintInputs`; o de release é o
SHA-256 UTF-8 de `release|semantics|metadata|response`, nessa ordem. A validação desta fatia
confere JSON, hashes, vocabulários, contagens sintéticas e bloqueios documentados, sem executar
código de produção ou acessar a rede.
