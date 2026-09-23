# Checkpoint 0268 — B16 Cotações concluída por aceite interno de sombra

Data: 22/09/2026. Sucede o checkpoint 0267.

## Decisão e resultado

O usuário decidiu que, diante da indisponibilidade permanente de contrato/release
do fornecedor, B16 de Cotações pode encerrar por aceite interno de sombra. A
decisão consolida as evidências existentes: mapper alinhado, vertical V2-027
validada localmente, observação read-only dos campos e controlador de expansão
física fail-closed. A primeira linha específica de Cotações foi marcada em
`BLOCOS_ETAPA_2.md` como aceite interno, e B16 de Cotações está concluída.

## Limite

O aceite não é contrato do fornecedor e não prova tenant, estabilidade,
unicidade global, grão oficial, terminal, snapshot/completude, tarifa, regra de
negócio, oráculo ou paridade. P17, publicação, cutover e produção permanecem
bloqueados. Não houve fonte externa, banco, escrita, DDL/DML, agenda, deploy ou
corte nesta unidade.

A decisão detalhada é
`docs/continuidade/decisoes/2026-09-22-b16-cotacoes-aceite-interno-sombra.md`.
`Test-DataExport6906IdentityCatalog.ps1` passou, confirmando a source key
inteira escopada e os limites de tenant, estabilidade, expansão, sweep e
cutover. `git diff --check` passou, com avisos CRLF/LF preexistentes.
O próximo passo elegível para Cotações é P17, somente se houver janela fechada e
oráculo independente; sem ambos, avançar outra rota independente.
