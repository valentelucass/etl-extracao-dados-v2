# Checkpoint 0317 — SQL TCP somente loopback, readback independente PASS

## Autoridade e alvo

- 28/09/2026, 23:49 UTC. Anterior [0316](0316-p08-wmi-setflag-alternativa-offline.md), SHA-256 `2DC225D44542D502547281FD71118F9FAB6DBA974EEAB1ABAEB1BFF3C52C321C`.
- Usuário autorizou uma nova tentativa do [roteiro revisável](../../runbooks/p08-loopback-wmi-flag-proposta-20260928.md), preflight/reserva novos, UAC legítimo e parada sem retry ante falha/deriva. Alvo único `LUCAS/MSSQLSERVER` e `localhost/ETL_SISTEMA_V2_SHADOW`; nenhum remoto/produção/fonte/cutover.
- Unidade: gate de transporte SQL TCP exclusivamente `::1` e `127.0.0.1`. Este PASS não é aceite P07/P08 nem JDBC/schema.

## Execução e evidência

| Passo | Observado | Recibo privado |
| --- | --- | --- |
| Preflight WMI/serviço/socket | `MSSQLSERVER` Running/Manual PID 2116, TCP/NP false, ListenAll=1, IP19=`::1`/IP20=`127.0.0.1` ativos/desabilitados/porta1433/dinâmica vazia, outras 22 entradas desabilitadas, zero listeners. | `pin-1282-uac-v3-ledger.jsonl` |
| Preflight SQL read-only | `lpc:localhost/master` e alvo exato saíram 0, 17.0.1000.7/dois arquivos, alvo zero tabelas/views/procedures de usuário. | `pin-1282-uac-v3-{master,target}-preflight.out` |
| Reserva e efeito | Helper v3 SHA-256 `E153ED7BC58014C5D003E48E32ADFF987662AEAA3FEA8977330BBE08FE2B2AA0`, launcher hash `833421D676D90866C126CBC000A394D6BBAE3619FCBC998D22E88FF9545DDD69`; uma elevação, helper exit 0; `SetFlag(false)`, `SetEnable()` apenas IP19/IP20, TCP por último, um restart. | `pin-1282-uac-v3-launch-result.json`, `pin-1282-loopback-v3-{start,result}.json` |
| Readback independente WMI/registro/socket | PID novo 20404 Running/Manual, TCP=true/NP=false/ListenAll=0, registro IP19/IP20 enabled=1, outros IPs off, dois listeners do SQL exatamente `::1:1433` e `127.0.0.1:1433`; nenhum wildcard/remoto/extra. | `pin-1282-uac-v3-ledger.jsonl` |
| SQL read-only após restart | `master` e alvo saíram 0; alvo permaneceu vazio 0/0/0. | `pin-1282-uac-v3-post-{master,target}.out` |

Ledger v3 SHA-256 `70C5FB2E32CDE84457EBE930324DF1FC3605F3816AA2AB3B3DA999A09EF0CA74`.
Attempts v1/v2 e ledgers anteriores preservados. Nenhum firewall, remoto,
DDL, JDBC, migration ou dado de domínio nesta unidade. Não houve retry UAC.

## Retomada imediata

1. Gate separado JDBC/Flyway Windows auth **read-only** com JAR/DLL 12.8.2 e URL literal localhost/banco shadow, sem credenciais; conferir transporte loopback real.
2. Se esse gate passar e o alvo continuar vazio, reservar `flyway:info`, V001–V104, `validate` e inventário em unidades separadas, parando em qualquer erro/deriva.
3. Só após schema confirmado, validações sintéticas rollback-only/IT com contagens antes/depois; P07/P08 e P01–P33 integrais não foram promovidos por este gate.
