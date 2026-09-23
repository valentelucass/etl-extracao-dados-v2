# Checkpoint 0267 — Cotações: expansão física limitada e envelope inválido

Data: 22/09/2026. Sucede o checkpoint 0266.

## Ordem e resultado

Após o limite físico observado no checkpoint anterior, foi implementado e
autotestado um classificador estrito: expansão acima de `per=100` só é aceita
quando cada linha tem `sequence_code` inteiro, escalar e não nulo e há no máximo
100 valores distintos. A ordem read-only consultou o metadado e as páginas 2–7
de 6906, com teto de sete chamadas, intervalo de três segundos, timeout de 30
segundos e resposta máxima de 10 MiB; sem redirect, retry ou fallback.

O metadado e as páginas 2–4 responderam HTTP 200. Página 2: 112 linhas físicas
e 100 valores distintos; página 3: 49 e 48. A página 4 possuía envelope de
dados inválido; a sonda parou com `DATA_ENVELOPE_INVALID` após quatro chamadas.
Páginas 5–7 não foram chamadas. O recibo sanitizado é
`docs/continuidade/probes/2026-09-22-6906-paginacao-expansao-envelope.md`.

## Conclusão

Foi concluída somente a correção verificável do controlador de sonda: ele
aceita expansão física sob limite de entidades e recusa envelope inválido sem
prosseguir. Nenhuma caixa adicional foi marcada. A observação não estabelece
identidade canônica, grão/contrato oficial, terminal, completude, tarifa,
oráculo, paridade, aprovação ou produção.

Não houve GraphQL, 4924, banco, escrita, DDL/DML, agenda, deploy ou corte.
O working tree preexistente foi preservado. Próxima ação externa só cabe sob
ordem própria que trate o envelope inválido sem repetir esta rodada; o input
externo que fecha B16 continua sendo contrato/release oficial, referência
tarifária e janela/oráculo aprovados.

O autocontrole da sonda e `Test-OfflineSecretScan.ps1` passaram (18 casos).
`Test-Gpt56ChatTrail.ps1` parou no marcador histórico `HANDOFF_PIN`, antes de
suas asserções; nenhum manifesto/ledger histórico foi alterado. `git diff
--check` passou, com avisos CRLF/LF preexistentes.
