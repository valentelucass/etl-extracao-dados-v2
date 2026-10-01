# 0356 — diagnóstico de consumidores parou no preflight master

- Data: 2026-09-29 UTC. Anterior: [0355](0355-sql-auth-preflight-consumers-stop.md), SHA-256 `248C3CEA5B985BBFBDF81FFC8D2CFF2971FE8F365631ACCF517EDA927C49E36E`.
- Autoridade: nova unidade do Supervisor para identificar os consumidores de 54913 em leitura, encerrar somente cliente próprio ocioso de forma graciosa e, apenas com zero consumidores comprovados, retomar a configuração SQL auth local. Sem `KILL`, sessão alheia, produção, remoto ou fonte real.
- Reserva: `target/shadow-local-rebuild-20260928-01/p08-v105-0356-consumer-physical-ledger.jsonl`, SHA `D3C3C96196B02D81527003EF61601AE0D63D12C92F1FDCC1E4F42966AAE43FAF`. SQL estático de DMV e snapshot catalog-only preparados em `target` para a unidade, **não executados** após a falha do preflight.
- Preflight observado: uma chamada `sqlcmd -E` a `lpc:localhost/master`, login timeout 5 s/query 45 s, retornou `Timeout expired` sem `AUTH_MASTER_PREFLIGHT_OK` (saída SHA `59B1B8296AD710F16CE3B5BBE94918D4314D7BC8A5865CB6FD082FE224663C78`). O processo `sqlcmd` retornou 0 mesmo assim; a ausência do marcador determina **FAIL**. Nenhuma segunda chamada SQL foi feita.
- Readback OS sem SQL: `MSSQLSERVER` Running, PID 20404; dois listeners, ambos `::1`/`127.0.0.1`, zero não loopback; zero sockets de cliente TCP locais para porta 1433. Isto não exclui sessões por Shared memory nem identifica SPID, login, host, programa, estado ou transação.
- Resultado: **zero consumidores não provado**; diagnóstico SQL e configuração SQL auth inelegíveis nesta unidade. Nenhum cliente encerrado, `KILL`, senha/DPAPI, principal, mudança de `LoginMode` ou reinício. Preservar 0355/54913, P08 0354 FAIL, 2536 stats não aceitas e limites backup 0325.

## Próximas ações

1. Supervisor decidir nova sonda/preflight após apurar a expiração de `master`; exigir marcador de SQL e readback antes de agir em cliente.
2. Se uma futura sonda classificar consumidor próprio ocioso, fechar apenas o cliente causador de modo gracioso; consumidor alheio ou transação ativa requer ação do responsável, sem `KILL`.
3. Configurar login somente após zero consumidores e todos os guardas locais comprovados em reserva distinta.
