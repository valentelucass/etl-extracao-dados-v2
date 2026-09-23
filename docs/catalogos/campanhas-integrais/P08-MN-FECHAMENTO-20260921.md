# P08 M/N — fechamento local

## Resultado

**M e N: ACEITO_NO_ESCOPO_LOCAL.** O escopo aceito é exclusivamente a
qualificação sintética rollback-only em `localhost/ETL_SISTEMA_V2_SHADOW`.
Não constitui aceite dos pais V2, paridade real, revisão humana, produção ou
cutover.

O pacote primário e a reprodução têm 724 membros e o mesmo ZIP
`05700f87b3f376d7f48e4f7b334a876fd7505c4e1d00399b684e9ab68c179b4b`; o
manifesto é `45a5c532c8997e88f6691c59ecd3d6e6b2879c7db376b4a658c7732eeba16a73`.
O readback do selo confirmou novamente esses mesmos bytes.

## M — pacote extraído

A/B passaram pelo JAR extraído: sete etapas, 133 comparações, 231 previews,
exit 0 e rollback confirmado por sequência. VALUE, PRECISION, KEY e
MULTIPLICITY recusaram com exit 40; OLD_REFERENCE recusou após seis etapas;
MISSING_USER, PIN_DRIFT e COMMAND recusaram na admissão com exit 20. Cada
recibo possui ledger/reserva próprio, sem DDL, commit, fonte ou processo
remanescente.

## N — entrega local

O validador de regressão confirmou 2.162 testes unitários, 492 de integração,
105 classes de integração, cobertura e rollback. O scanner offline passou com
3.675 candidatos, 3.666 textos, um binário verificado e zero achados; o
autoteste do scanner passou 17 casos. A trilha de preparação passou um caso
positivo e 24 negativos.

O selo e os recibos estão em
`target/macrobloco-qualificacao-pacote-20260913-01/p08-mn-n-seal-23/` e
`p08-mn-n-scanner-23/`. Ledgers, hashes, selos e falhas históricas não foram
reescritos. A sucessão P06 histórica permanece uma fotografia; este relatório
é seu sucessor documental de P08, sem alterar o manifesto ou selo P06.

Os contadores canônicos permanecem 39/45 e 67/115.
