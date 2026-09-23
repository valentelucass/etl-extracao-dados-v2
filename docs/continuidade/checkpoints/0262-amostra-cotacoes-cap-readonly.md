# Checkpoint 0262 — amostra read-only de Cotações e Contas a Pagar

Data: 22/09/2026. Sucede o checkpoint 0261 e registra a ordem externa
explicitamente autorizada pelo usuário para investigar 6906 e 8636.

## Pré-condição, alcance e efeito

A ordem foi registrada previamente em `STATES.md`. Alvo: Data Export dos
templates 6906 e 8636, somente `GET /info` e uma página `GET_WITH_QUERY` por
template, por `curl.exe`. Limites consumidos: quatro chamadas seriais,
`per=100`, intervalo de três segundos, timeout de 30 segundos e resposta de
no máximo 10 MiB. Trava interprocessos V2 adquirida; não houve redirect, retry,
fallback, GraphQL, 4924, banco, escrita, DDL/DML, agenda, deploy ou corte.
Respostas ficaram em memória; não se gravou payload, URL, token, header, ID ou
dado de negócio.

## Resultado observado

Todos os quatro pedidos responderam HTTP 200. Para 6906, o metadado resumiu 37
campos e seis filtros; a página inicial teve 100 linhas físicas e o candidato
`sequence_code` foi numérico/escalar, presente e distinto nas 100 linhas. A
amostra não é terminal e não prova estabilidade, tenant, release oficial,
referência tarifária, oráculo ou paridade.

Para 8636, o metadado resumiu 28 campos e nove filtros; a página teve 86 linhas
físicas. Não havia `accounting_debit_id`; `ant_ils_sequence_code` ocorreu 86
vezes como número escalar não nulo, com 85 valores distintos. A contraevidência
preserva a conclusão fail-closed: a candidata não é chave de raiz/linha e não
prova raiz→parcela→rateio. Nenhum checkbox P16–P29 foi fechado.

Recibo sanitizado:
`docs/continuidade/probes/2026-09-22-6906-8636-amostra-readonly.md`
(SHA-256 `83af1af8ccf70ac02c289fe1f404a2d98c052b7d76655f9701f13e303adf05d0`).

## Alterações e verificação

| Artefato | Resultado |
| --- | --- |
| `STATES.md` | ordem e resultado sanitizado registrados antes/depois do efeito |
| `TRILHA_CONCLUSAO_POR_MODELO.md` | rota e limite da amostra registrados sem promover aceite |
| `scripts/probes/Invoke-DataExport6906And8636ContractProbe.ps1` | sonda dedicada, com travas de escopo, transporte, volume, timeout e sanitização; SHA-256 `688a45c288613b3e2f0dd08d26a32b7b0f1e9b2271718b007598834ed7507185` |
| `BLOCOS_ETAPA_2.md` | resultado referenciado, sem caixa marcada sem o aceite correspondente |

O autoteste da sonda passou com `network_calls=0`. Após a rodada, o mesmo
autoteste passou novamente e `git diff --check` não encontrou erro de
whitespace; avisos CRLF/LF são preexistentes. Esses testes não comprovam
contrato oficial, referência, oráculo, paridade ou produção.

## Próximas ações

1. Não repetir esta ordem nem ampliar páginas/janelas automaticamente; uma nova
   consulta exige ordem própria.
2. Se chegar release tarifário e oráculo oficial de 6906, confrontar a amostra
   com esse material e executar P16/P17 de Cotações.
3. Se a ESL ou Financeiro fornecer o ID da raiz e a prova de grão 8636,
   confrontar a nova evidência e executar P16/P17 de Contas a Pagar.
