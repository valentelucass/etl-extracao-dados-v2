# 0371 — LOCAL_V2: guard reader recusado na compilação SQL 130

- Data: 2026-09-29. Anterior: [0370](0370-p08-consumidores-reader-dmv-local.md), SHA-256 `0E107655038AA139EA77A8F5F0E659080F20EAE55217E76DD0F4F25059DEF2B7`.
- Autoridade: Supervisor pós-0370 autorizou **uma unidade catalog-only** com exceção estrita ao guard de zero consumidores: até duas sessões de usuário da categoria reader, ambas `SLEEPING`, banco corrente shadow, sem request nem transação; qualquer desvio impõe parada. Sem IT Maven, smoke A/B, efeito de schema, KILL/fechamento, restart/login, cinco bancos ou produção.
- Estado: **CLOSED_SQL130_GUARD_COMPILE_FAIL_NO_DELTA**. `LOCAL_V2` físico e ramo V024 continuam **DESCONHECIDOS**. Gate 1/P08 permanece FAIL/aberto.

## Preparação e reserva

1. Helpers privados novos em `target/p08-local-v2-catalog-20260929-04`; nenhum script/recibo/ledger 0368–0370 foi alterado. Prova **offline** `offline-proof.json` SHA-256 `A8C3354D197B8A45B155F82226D14AD1F897680736C38F9E2CB3E4C33C382099`: parse PowerShell, três argv completos com fixture sintética e caminho `-i` absoluto não vazio, literais de master/alvo/contagens/064/stats confrontados aos outputs 0369, fechamento UTC por `DateTimeOffset` testado nos tempos preservados 0370, e guarda estática do SQL. A guarda estática **não provava compilação T-SQL**; sua limitação foi demonstrada abaixo.
2. Reserva física **nova** antes do primeiro SQL: `physical-ledger.jsonl`, teto 300 s, login 5 s, snapshots 15 s, consulta 10 s, `LOCK_TIMEOUT` 1800 ms, Windows auth `sqlcmd -E -C` em `lpc:localhost`. Impacto previsto: leituras de metadados locais poderiam criar/alterar stats automáticas. Recuperação: parar sem retry, readback independente SQL/OS/stats, classificar delta sem mutação nem baseline automática. O SQL privado de catálogo SHA-256 `E447483A0D6C781456269CDAF8D66E781D404ECDF0678A5C6C2561E97EB7F017` embutia a regra reader imediatamente antes do único SELECT catalog-only, para detectar mudança naquele lote.

## Efeito observado e reconciliação

1. Preflight OS confirmou serviço em execução/listeners somente loopback. `master` e shadow exatos responderam por Windows auth/Shared memory. Flyway 106 = 1 SCHEMA + 105 SQL, zero falhas; 1819 objetos, 247 tabelas, 147 linhas agregadas e contagens esperadas. `064` SHA-256 `78F6F2664AD673D815D01C7B281DF2D606AF8DE419FAE1A9BC48816582E6D2E3`. Stats antes/depois de 064: 2536 grupos e SHA-256 `9090E84C838A0C0E7ED77742736A03AF4281CB0008EDACA26E3E051F51B3D33F`, exatamente pós-0354 **como observação de entrada, sem baseline aceita**. O marcador do alvo contou duas outras sessões; não traz categoria, atividade ou identidade.
2. **Uma invocação** do script catalog-only, exit 1: SQL Server reportou **Msg 130, nível 15, linhas 24 e 25**, no `SUM(CASE...)` do guard que continha dois `EXISTS` correlacionados. A agregação de expressão com subconsulta não compilou. `catalog.out` SHA-256 `D35B206184866B28F80662B34874D183E1AFA3F05AA190712CFF8DE36401F0E9`; zero `CATALOG_FLAGS`, zero flags observadas. O guard reader **não foi aprovado** nesta unidade e o SELECT de `ctl.source_catalog`/`ctl.source_protocol_binding` **não foi alcançado**. Nenhum retry, correção em uso ou segunda consulta de catálogo.
3. Readback independente posterior completou master/alvo/contagens/064/stats/OS. Hashes estruturais e de stats pré/pós iguais; serviço/PID/listeners loopback estáveis; marcador de outras sessões permaneceu em dois, sem provar que eram as mesmas sessões ou que satisfaziam o guard. Não houve delta nesses recortes. `final-result.json` SHA-256 `2A751A41CE02C04007DD87AB2AD336595AD3C505A923FD39680D3772E8A2E452`: último readback físico em 23,5 s e fechamento em 96,1 s, ambos sob teto 300 s. Ledger final SHA-256 `DBCFDD80023AE13EC3B07F4F9177B350AE6D206F1F241ED6AD7F1CE353D75F6C`.

## Limites e próxima decisão

- O erro é do helper privado desta unidade, **não evidência de ramo V024 ou de estado `LOCAL_V2`**. A observação 0370 de duas sessões reader não vale como prova temporal do guard 0371. O teste offline foi insuficiente para captar a regra de compilação SQL 130; uma revisão futura deve pré-computar flags de request/transação antes da agregação e provar os novos bytes em unidade separada. Não repetir a consulta nesta reserva.
- `2536` continua apenas observação de 0354, +88 automáticas sem query criadora nem aceite. Preservados **8 FAILs**, 33/107 classes executadas, **74 faltantes**, JaCoCo não alcançado, A/B físico e Gate 1/P08 abertos; candidatos 0367 só offline. Nenhum arquivo Runtime, SQL versionado, contrato ou schema foi editado.
- Próximo responsável: **Supervisor** decide se autoriza uma nova unidade/reserva após revisão do guard. **Banco** permanece único executor eventual SQL/ledger. Nenhum KILL, fechamento de cliente, DDL/Flyway, restart/login, IT/smoke, cinco bancos ou produção nesta unidade.

## Próximas ações

1. Supervisor revisa o FAIL SQL 130 e decide eventual nova autoridade; nenhum retry implícito.
2. Se autorizado em outra unidade, Banco cria nova cópia privada com predicados pré-computados, prova offline os bytes e refaz reserva/preflight/readback próprios.
