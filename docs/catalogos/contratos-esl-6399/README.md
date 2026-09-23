# Contrato offline — Data Export 6399 (Manifestos)

Este diretório é a fatia `V2-025b` do template Data Export `6399`. Ele transforma somente a
evidência local, estática e histórica sanitizada já registrada no `STATES.md` em um contrato
versionado e fixtures artificiais. Não chama a ESL, não lê credencial, não cria configuração de
runtime e não implementa a vertical de Manifestos.

## Limite deliberado

Este contrato não decide identidade, grão físico, cardinalidade de filhos, reducer, persistência,
relação Manifesto→Coleta, fatos ou publicação. A observação de `sequence_code` repetido em linhas
físicas é registrada apenas como candidato de agregado; as decisões locais posteriores pertencem
exclusivamente a `V2-009b/6399` e `V2-026a` e não transformam este catálogo em garantia atual do
fornecedor.

As fixtures não são respostas da ESL. Seus valores e datas são sintéticos e existem somente para
exercitar, de forma legível, uma expansão física por referências de pick/MDF-e. A revisão
`2026-09-04.v2-025b.2` corrige o nome artificial `pick_sequence_code`: a evidência estática
versionada usa `mft_pfs_pck_sequence_code`, e não existe `@JsonAlias` para o nome anterior. As
fixtures também carregam o par físico MDF-e `mft_mfs_number` + `mft_mfs_key` sem afirmar
identidade ou relação. `mdfe_status` é escalar da raiz replicado pela expansão: sua presença
isolada não cria filho MDF-e nem dispara quarentena de chave. As fixtures não provam tipos atuais
do fornecedor, nulidade, paginação remota, ordenação total, snapshot, completude ou política do
fornecedor.

## O que a evidência sustenta

- Transporte-alvo: `GET_WITH_QUERY`, sem fallback de método. A observação histórica registrou
  `GET` diário aceito, `POST /data` rejeitado e um `422` para janela mensal genérica; somente uma
  classificação sanitizada de “janela grande” poderá no futuro solicitar reparticionamento. Outro
  `422` é terminal.
- Filtro candidato de negócio: `search[manifests][service_date]` no formato civil
  `AAAA-MM-DD - AAAA-MM-DD`; a tradução de `[início, fimExclusivo)` para as bordas da fonte continua
  pendente.
- Ordenação histórica de paridade: `sequence_code asc`. Ela não substitui chave, cursor,
  repetibilidade ou prova de completude.
- A observação sanitizada de 15/07/2026 com `per=3` tinha quatro linhas físicas e três valores de
  `sequence_code`, sem `id` publicado. Referências de pick e MDF-e expandiram o agregado. O source
  path local caracterizado de pick é somente `mft_pfs_pck_sequence_code`; `pick_sequence_code` é
  nome interno legado e não path aceito.
- O `/info` observado informou 91 campos e 18 filtros, sem tipos versionados. O catálogo lista
  apenas o subconjunto de nomes sustentado por evidência estática e não afirma que seja completo.

## Bloqueios e próximos gates

O contrato fica `TRANSITIONAL` e permanece sem prova independente de completude. Portanto, sweep,
desativação por ausência e cutover são proibidos. Nenhuma implementação em sombra é autorizada por
este documento isoladamente: ela ainda depende da decisão `V2-009b`, da vertical `V2-026` e dos
gates comuns aplicáveis.

`V2-025d` é uma atividade futura separada, bloqueada por `V2-041` e por reconfirmação humana de
janela/teto. Este catálogo não é autorização de rede nem substituto para essa rodada.

## Integridade local

O [manifesto](manifesto.json) usa vocabulários fechados, declara a proveniência de cada aspecto e
registra SHA-256 de todas as fixtures. O validator dedicado é
`scripts/validation/Test-DataExport6399ContractCatalog.ps1`. Os fingerprints semântico, de metadata e de resposta são o
SHA-256 UTF-8 (sem BOM) dos respectivos materiais em `fingerprintInputs`; o de release é o SHA-256
UTF-8 de `release|semantics|metadata|response`, nessa ordem. A validação desta fatia confere JSON,
hashes, vocabulários e bloqueios documentados, sem executar código de produção ou acessar a rede.
