# P08 — correção local do teto do runtime e requalificação dos guardas

## Veredito

**M e N permanecem abertos, sem aceite.** A rodada anterior preservou uma
divergência real: o montador de pacote aceitava724 membros, mas o verificador
Java ainda impunha512 no array do manifesto e514 no conjunto de arquivos.
Assim, `missing-member` parava em `QUAL_JSON_ARRAY` antes de alcançar o
contrato de mutação `QUAL_JSON_MEMBERS`.

A correção centralizou o limite em `QualifiedPackage.MAX_MEMBERS=1024` e o
limite total em `MAX_CONTENT_FILES=1026`. Os testes de contrato e arquitetura
passaram26 casos sem falhas/erros. Um build local offline gerou o candidato
`p08-runtime-member-limit-candidate-17`, com724 membros e manifesto
`86e26ccd96389e588d254b88306b8d6513fb8fd453c599b38fc7f842c9b6680a`.
Os21 guardas da entrada extraída passaram contra o JAR corrigido:
`childrenCreated=0` e `jdbc=NOT_STARTED`. Em particular,
`missing-member` voltou a observar `QUAL_JSON_MEMBERS`.

## Ações executadas

| Ação | Resultado | Recibo |
| --- | --- | --- |
| Pacote primário offline | PASS | `target/macrobloco-qualificacao-pacote-20260913-01/p08-final-primary-20260921-16/result.json` |
| Reprodução offline independente | PASS, byte idêntico | `target/macrobloco-qualificacao-pacote-20260913-01/p08-final-reproduction-20260921-16/result.json` |
| Extração e validação dos dois payloads | PASS | confirmação no ledger `p08-offline-reconciliacao-20260921-16` |
| Guard de envelope | PASS, 25 casos | `target/macrobloco-qualificacao-pacote-20260913-01/package-guards-0c103d9fbe8a4df09753d008ded28efa/result.json` |
| Guard da entrada extraída | BLOCKED, 1.º caso | `target/macrobloco-qualificacao-pacote-20260913-01/p08-extracted-guards-offline-20260921-16/result.json` |
| Correção de limite no runtime | PASS, 26 testes | `QualificationBoundedContractsTest`, `ArchitectureRulesTest`, `QualificationContractTest` |
| Build do candidato corrigido | PASS, exit 0 | `target/macrobloco-qualificacao-pacote-20260913-01/p08-runtime-member-limit-build-17/result.json` |
| Guard da entrada extraída requalificado | PASS, 21 casos; sem filho/JDBC | `target/macrobloco-qualificacao-pacote-20260913-01/p08-extracted-guards-runtime-limit-17/result.json` |

O ZIP final é
`ebd4d13d6e5acd0c3d92d259a8e797ffa1f9d36caf4e40b2aa2427892c3a19c9`; o
manifesto é
`c29621a64e96bfa7d448d87164a104b95d001efc82a5f067a0f1a2002c8ea5d4`.
São 724 membros, 9 dependências, 2.116 entradas no inventário de fontes e a
revisão é `a5bc79511296e119dc169833c362238492c12e3fbbca6cab61e3798c0f615bd9`.
Os hashes coincidem com `p08-primary-package-archivelimit-11`, logo as provas
A/B, VALUE e COMMAND já existentes pertencem aos mesmos bytes; elas continuam
insuficientes para M.

## Ações não executadas

Não houve SQL, JDBC, smoke, guard de controle, scanner, selagem,
cancelamento, retomada ou as contraprovas `PRECISION`, `KEY`, `MULTIPLICITY`,
`OLD_REFERENCE`, `MISSING_USER` e `PIN_DRIFT`. Houve somente execução JAR
dos guardas corrompidos, todos recusados antes de criar filho/JDBC. Não houve nova leitura agregada
em `ctl.execution_audit`, `ctl.page_audit` ou `ctl.execution_page_audit`; portanto
esta rodada não declara equivalência nova dessas auditorias. Não houve fonte,
DDL, commit, produção, deploy ou processo filho JAR.

## Orçamento e recuperação

O ledger físico fechado conserva 1.200 s consumidos de 51.600 s; seus 50.400 s
não são saldo reutilizável. O ledger offline da correção de limite está fechado
com PASS_LOCAL e preserva build, candidato e os21 guardas. O próximo macrobloco
precisa requalificar as evidências ainda pendentes sem inferir aceite deste
recorte local: contraprovas diretas, smoke, guard de controle, scanner,
sucessão, selo/readback e gates por parcela.

A e L permanecem abertas. Os contadores canônicos continuam 39/45 construção e
67/115 aceites; não houve revisão humana, paridade produtiva, cutover ou uso de
produção.
