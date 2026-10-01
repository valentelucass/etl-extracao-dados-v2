# 0335 — P08/V105: validadores 063/064 e baseline observacional pós-IT

- Data: 2026-09-29 UTC; anterior: [0334](0334-p08-v105-auto-stat-catalogo-gates-063-064-adiados.md), SHA-256 `7301903D2C79BDF719CEAD242C8467131609A9F106392338D247E0F2BB5620CD`.
- Estado: P08 aberto. A classe `ExpansionLaboratoryReferencesIT` teve 5/5 PASS em 0333, mas o critério antigo de 064 idêntico ao pré-IT falhou pelo surgimento de uma estatística automática. Esse FAIL, os anteriores e os limites do backup 0325 permanecem registrados.
- Autoridade: decisão do Supervisor após 0334 autorizou exatamente um gate read-only 063 e um 064, com reservas distintas, inventário de estatísticas após cada execução e critério observacional pós-IT. Sem IT, JDBC, Flyway, smoke, restore, DDL explícito ou fonte real.

## Execução e evidência

Preflight `lpc:localhost` por autenticação Windows: `master` e `ETL_SISTEMA_V2_SHADOW` exatos; serviço `MSSQLSERVER` Running PID 20404; listeners apenas `::1` e `127.0.0.1`; zero consumidores. Histórico 106 linhas = SCHEMA + 105 SQL, zero falhas; 1819 objetos, 247 tabelas, 147 linhas, sete schemas/principals V2; contagens globais monitoradas de domínio/auditoria zero. Pins V105/063/064 permaneceram respectivamente `29D6612E42DAE014388EB3384F46F4A30D0402FAC66E8548806121E1D4E3DB17`, `381C177B2E42B33E6BB5A72F7730B8033C05DA50000085808C8207E18D11E7A4`, `D990AD9C862F6F4F51D08650D0C4B68755F0419877641938C3EC46269B5D4533`.

| Gate | Observado | Recibo privado |
| --- | --- | --- |
| Reserva/preflight | PASS; catálogo inicial de estatísticas com 873 grupos | `target/shadow-local-rebuild-20260928-01/p08-v105-0335-validators-ledger.jsonl`; inventário SHA-256 `BAEB5ED431D478B5537B4939F51AB203C223902070C6AC011188EE925BFD8717` |
| 063, uma chamada | Exit 0, `EPOCH_V105_STRUCTURAL_PASS`; saída SHA-256 `6CE5EF46A0AA267F9AC7085E2FC3BF73D943E619643F97626D2DCB359AED967E` | `p08-v105-0335-post-063.out` |
| Catálogo imediatamente após 063 | 873 grupos; hash idêntico ao inicial; nenhuma estatística nova | `p08-v105-0335-after-063-stats.out` |
| 064, uma chamada distinta | Exit 0; saída SHA-256 `A0B50FFD1CB4FBDB3F8E51D5875FC3B7094B905F71741C7271CC3E3A90F8B865`, idêntica à fotografia pós-IT de 0333 | `p08-v105-0335-post-064.out` |
| Catálogo imediatamente após 064 | 873 grupos; hash idêntico ao inicial; nenhuma estatística nova | `p08-v105-0335-after-064-stats.out` |
| Readback independente após cada gate | `master`, alvo e agregados iguais ao preflight; sockets loopback; nenhuma mudança de dado, schema, principal ou histórico | SHA-256 de saída normalizada: `FC9044AA34788EB0581CC52D7BBEE77032075FBA7E3C6A038C37A52DE3902762`, `CA025A54ECFDABC8853C8892E02BCE86A2DA047DD15680A8E389BFC300BC58D8`, `3C1015B95C285C7926C5A10F6F7C22666DB6FECC4D28E4338B544C57066B1BC6` |

A estatística de `ref.expansion_lab_label.label` continua `auto_created=1`, `user_created=0`, sem filtro e sem índice associado. O inventário não imprimiu nome gerado, IDs ou payload. A comparação é por tabela, coluna, tipo, flags e contagem; o hash de inventário permaneceu igual após ambos os validadores. Os 51 grupos de estatísticas automáticas sem índice no banco incluem estado anterior e não implicam 51 novos efeitos. 064 reportou um blocker automático, zero manual/filtrado, quatro colunas persistidas BIN2 (`persisted_non_bin2=0`) e TVP label BIN2. A ausência de delta nas duas execuções permite registrar `A0B50...` como **baseline observacional pós-IT**, sem substituir a fotografia pré-IT e sem alterar migration/validadores/manifests.

Uma primeira leitura independente após 063 usou formatação padrão do `sqlcmd`, gerando hashes de apresentação diferentes; a leitura normalizada `-W -s '|'` igualou byte a byte os recibos de preflight. Nenhum validador foi repetido por isso. Ledger físico final SHA-256 `7F027E1B3F8C8C29730C4FF308217E5D160FCE4983029424298102253AE9B022`. Não houve estatística adicional observada, correção automática ou restore.

## Retomada

1. Supervisor revisa o baseline observacional e define próximo gate P08; 5/5 IT PASS não constitui aceite integral.
2. Runtime pode apresentar plano smoke offline; execução física depende de autorização própria e preflight/reserva novos.
3. Preservar o backup 0325 com sua limitação: VERIFYONLY/HEADERONLY/FILELISTONLY/msdb concordam, mas ausência prévia do arquivo, tamanho físico e SHA não foram comprovados; restore não foi testado.
