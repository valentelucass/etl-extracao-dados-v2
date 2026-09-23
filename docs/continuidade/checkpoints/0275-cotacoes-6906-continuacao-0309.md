# Checkpoint 0275 — Cotações 6906, continuação limitada sem terminal

- Data: 2026-09-22T20:22:37Z.
- Anterior: `0274-env-raiz-api-raster.md`, SHA-256 `6c55c96441771f7bdc6bb32d91404b1f8e3c02236e2620237f10fcf2179bdb9e`.
- Objetivo do usuário: rastrear com cURL o que permitir avanço verificável na etapa 2.
- Estado: forma de duas páginas adicionais observada; P17–P20 sem aceite.

## Autorização e limites

O pedido atual autorizou investigação read-only por cURL. A menor rota
independente foi Cotações 6906, priorizada por `BLOCOS_ETAPA_2.md`. A ordem
foi congelada em `STATES.md` antes da chamada: `/info` e páginas 2–6 da
janela fechada 2026-09-03, `per=100`, máximo seis chamadas seriais, três
segundos entre elas, timeout de 30 segundos, resposta até 10 MiB, sem
redirect/retry/fallback. Respostas permaneceram em memória e só contagens,
tipos e status sanitizados foram registrados. Não houve GraphQL, 4924,
Raster, banco, escrita, deploy, agenda ou cutover. O working tree existente
foi preservado.

## Evidência

| Passo | Camada | Observado |
| --- | --- | --- |
| Autoteste da sonda | offline | PASS, zero chamadas de rede |
| `/info` 6906 | fonte real/read-only | HTTP 200, 37 campos, seis filtros |
| Página 2 | fonte real/read-only | HTTP 200, 100 linhas físicas e 100 candidatos distintos válidos |
| Página 3 | fonte real/read-only | HTTP 200, 62 linhas físicas e 60 candidatos distintos válidos; nove campos presentes, alguns nulos |
| Página 4 | fonte real/read-only | HTTP 200, envelope não reconhecido como lista; `DATA_ENVELOPE_INVALID`, exit 1, parada sem retry |
| Integridade documental | local | `git diff --check` passou; aviso CRLF/LF de `STATES.md` |

Foram quatro das seis chamadas possíveis. As páginas 5–6 não foram chamadas.
O recibo completo e sanitizado está em
`docs/continuidade/probes/2026-09-22-6906-continuacao-0309.md`. A página
1 desta data vem de rodada anterior, sem garantia de snapshot estável entre
rodadas. Outra janela teve página curta seguida de envelope inválido na
página 4. Isso não prova terminal ou completude. O catálogo local aceita
somente `data` array como envelope promovível. Nenhuma caixa B17–B20 foi
marcada, e nenhuma regra de negócio, identidade ou oráculo foi inferida.

## Próximas ações — até três

1. Fornecedor ESL e owner de Cotações: fornecer release/contrato 6906 com
   forma de página terminal, identidade, filtros e ordenação versionados.
2. Owner de tarifas: fornecer referência aprovada por rota/vigência/versão;
   Negócio e responsável de dados: janela fechada e oráculo independente com
   escopo, schema, grão e tolerâncias, além de aceite nominal.
3. Owner do canal/alvo: confirmar autorização específica para a
   caracterização real; só então comparar e tratar divergências de P17.

Financeiro/4924 permanece em `HTTP_NON_2XX` anterior e não foi repetido.
Sem novo contrato ou oráculo, não repetir esta sonda para interpretar a
página 4 por tentativa. Nenhum efeito desconhecido.
