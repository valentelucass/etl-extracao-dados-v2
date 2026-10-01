# 0352 — P08: classificação offline das estatísticas pós-0351

- Data: 2026-09-29 UTC. Anterior: [0351](0351-p08-fullclass-failsafe-pass-jacoco-stats-fail.md), SHA-256 `EF8E2C94DB4D2D3949F1A043F90254E4968D0F51AFB0C91861201FB9E9040D27`.
- Autoridade: Supervisor solicitou comparação **somente offline** dos dumps 0351, análise do guard V105/064 e proposta de critério observacional. Nenhum SQL/JDBC/IT/Flyway/smoke, consulta de metadata viva ou alteração de V105/064/estatísticas/Runtime nesta unidade.
- Resultado: delta classificado como **grupos agregados de estatísticas automáticas**, inclusive um na coluna V105 `ctl.execution_audit.failure_category`. Proposta técnica: permitir `2448` e 064 pós **somente como fotografia observacional para futuro preflight**, sujeita à decisão do Supervisor; não aceitar o Gate 1 ou P08, nem tratar o estado como pronto para outro ALTER.

## Prova independente dos recibos preservados

Fonte: `target/shadow-local-rebuild-20260928-01/p08-v105-0351-fullclass-{pre,post}-stats.out`, `post-stats-after064.out`, `pre/post-v064.out`, saídas master/alvo/contagens e recibo sanitizado 0351 SHA `400DEADA68B1245A6F8F3A9DFB1CD025852601FE80D52895B0A7ECE1F79E2275`. Comparação read-only das linhas agregadas `P08_STATS_INVENTORY`, por schema/tabela/coluna/tipo/flags, sem nome gerado, ID ou payload. O SQL preservado agrupa `sys.stats` × `sys.stats_columns` nos sete schemas V2; cada grupo tem `COUNT_BIG`.

| Critério | Observado |
| --- | --- |
| Grupos globais | **2235→2448; 213 novos em 38 tabelas; zero removidos; zero contagens de grupos existentes modificadas** |
| Flags dos 213 novos | Todos `auto_created=1`, `user_created=0`, `has_filter=0`, `index_backed=0`, `COUNT_BIG=1`; zero grupos novos manuais, filtrados ou ligados a índice |
| Quatro colunas V105 | `ctl.execution_audit.status`: 0 novos; `traversal_verification`: 0; **`failure_category`: 1 novo grupo `nvarchar`, flags 1/0/0/0**; `ref.expansion_lab_label.label`: 0 |
| 064 | Apenas linha 15, `V105_STATISTICS_BLOCKERS`, mudou **1/1/0/0→2/2/0/0**. SHA pré `A0B50FFD1CB4FBDB3F8E51D5875FC3B7094B905F71741C7271CC3E3A90F8B865`; pós `78F6F2664AD673D815D01C7B281DF2D606AF8DE419FAE1A9BC48816582E6D2E3` |
| Estabilidade após 064 | Inventário pós e pós-064 iguais, SHA `E92AA86CDDAB2B7ADB918F6F1310AA2D86D7702962999E12FA134B15E81DB2E0` |
| Readback não estatístico | Arquivos master, alvo e contagens byte-idênticos antes/depois; alvo conserva 106=SCHEMA+105 SQL/0 falhas, objetos/tabelas/linhas/principals e zero consumidores. Recibo 0351 confirma mesmo PID, endereços apenas loopback e mesmos listeners |

O inventário de grupos não mostra nome/ID de estatística, histogramas, timestamps nem mudança interna de uma estatística já existente. Assim, **213 grupos não são prova de 213 objetos `sys.stats` distintos** nem da consulta criadora. O 064 usa `DISTINCT (object_id,stats_id)` no recorte das quatro colunas e confirma uma entrada automática adicional nesse recorte, coerente com o único grupo novo em `failure_category`. Não atribuir instrução exata da IT; a classificação é pelas flags do catálogo e pelo tempo da comparação pré/pós, não por trace de otimização.

## Efeito sobre V105 e critério proposto

A migration aplicada V105 SHA `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17` usa `sys.stats`/`sys.stats_columns` **antes** de `DROP PROCEDURE`/ALTER e lança `54066/V105_UNREVIEWED_COLUMN_STATISTICS` se houver estatística não revisada nas quatro colunas ou estatística filtrada sem índice nas duas tabelas. O comentário da migration explica que ALTER de collation pode descartar estatística automática; a guarda evita perda silenciosa de metadata. Ela foi cumprida ao aplicar V105 e **não é uma invariável que proíba estatísticas criadas depois**. Se um futuro DDL alterar novamente `failure_category`, deverá inventariar essa dependência e preservá-la ou recusar em migration nova, com preflight/recuperação próprios; não repetir nem editar V105 aplicada.

O 064 SHA `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533` é **snapshot observacional**, não executa ALTER nem lança `54066`. Seu contador pós-migration detecta a nova estatística; o delta não demonstra regressão de dados/schema, mas descumpre o critério estrito de hash 064 idêntico do gate 0351. A fotografia anterior 2235/`A0B50...` permanece histórica e o **FAIL 0351 não é reclassificado**; Maven também falhou independentemente no JaCoCo shadow.

**Proposta para Supervisor:** registrar 2448 grupos/SHA `E92AA...` e 064 pós/SHA `78F6...` como **novo ponto observacional de preflight**, tal como o precedente 0335/0348, se aceitar explicitamente o escopo agregado e a ausência de mudanças fora de estatísticas automáticas. Para um efeito futuro: comparar master/alvo/dados/schema/histórico/socket e os dois hashes observacionais; inventariar globalmente estatísticas antes e depois do 064; aceitar sem revisão adicional apenas igualdade exata. Qualquer grupo novo/removido/modificado, grupo manual/filtrado/indexado ou novo delta 064 exige classificação e decisão **antes do próximo efeito**. Se houver DDL em qualquer das quatro colunas, não usar esse aceite observacional como dispensa do guard pré-DDL: exigir novo plano versionado de dependências/dados/backup. Nenhuma estatística foi removida ou atualizada nesta unidade; P08, Gate 1 e smoke A/B permanecem abertos, com FAILs históricos e limites backup 0325 preservados.
