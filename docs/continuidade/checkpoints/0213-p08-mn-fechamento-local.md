# Checkpoint 0213 — P08 M/N fechado no escopo local

Em 2026-09-21, M e N passaram no escopo sintético rollback-only de
`localhost/ETL_SISTEMA_V2_SHADOW`. O pacote primário e a reprodução têm 724
membros, manifesto `45a5c532c8997e88f6691c59ecd3d6e6b2879c7db376b4a658c7732eeba16a73`
e ZIP `05700f87b3f376d7f48e4f7b334a876fd7505c4e1d00399b684e9ab68c179b4b`.

M: A/B e VALUE, PRECISION, KEY, MULTIPLICITY, OLD_REFERENCE, MISSING_USER,
PIN_DRIFT e COMMAND passaram pelo JAR extraído, cada qual com rollback
confirmado. N: regressão (2.162 unitários/492 integrações/105 classes), scanner
(3.675 candidatos/zero achados), 17 autotestes, preparação (1 positivo/24
negativos), selo/readback e `Test-P08MnDelivery` passaram.

Evidência: `target/macrobloco-qualificacao-pacote-20260913-01/p08-mn-*-23/`;
selo `f4bad0388c65eb16c84e933e8fbd384ac63e7516350d9d28a25ca8b3e7461027`;
sucessor documental `docs/catalogos/p08-mn/manifesto.json` e seu selo
`manifesto.sha256`.

Não houve DDL, commit, fonte, produção, paridade real, cutover ou revisão
humana. P06 e os selos/ledgers históricos continuam imutáveis; 39/45 e 67/115
permanecem canônicos.

Próximas ações:

1. Avançar apenas para macrobloco explicitamente elegível fora de P08.
2. Tratar gates externos somente com a autorização e o owner correspondentes.
3. Não repetir recibos P08 aprovados sem causa nova comprovada.
