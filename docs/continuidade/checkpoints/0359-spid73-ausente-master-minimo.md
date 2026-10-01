# 0359 — SPID 73 ausente em consultas mínimas no master; SQL auth pendente

- Data: 2026-09-29 UTC. Anterior: [0358](0358-spid73-dmv-timeout-sem-auth.md), SHA-256 `71756DF958C4AB8523E8FEA80E3529E48A1A92245D5D18E190A0ADA4BAC26167`.
- Autoridade: Supervisor autorizou apenas gates read-only curtos em `lpc:localhost/master` para sessão 73 e, se respondesse, transação 73 em reserva distinta. SQL auth local somente com sessão interna e zero sessões/transações de usuário comprovadas, mais novo preflight no shadow. Sem consulta ampla no shadow, KILL ou restart.
- Preflight OS: serviço `MSSQLSERVER` Running PID 20404, apenas listeners `::1:1433` e `127.0.0.1:1433`, zero sockets cliente TCP. Recibo privado `p08-v105-0359-os-preflight.json` no ledger.
- Gate sessão reservado: script privado SHA `B5F543728FC41A37C79014657DD4323357D1608B63CB833603EC175B04479455`; `sqlcmd -E -S lpc:localhost -d master -l 3 -t 5`, exit 0. Saída SHA `583D5A0674F7FF433204E6885C77F94BCB20F83AD9D03CC360381272EBCA693D`: `CONNECTED_MASTER|master|64|Shared memory|NTLM`, `BEFORE_SESSION73`, **zero linhas da seleção `sys.dm_exec_sessions WHERE session_id=73`**, `SESSION_QUERY_DONE`. Os marcadores mostram conexão e statement concluídos neste gate. `master` confirmou Windows-only e shadow exato online. Não houve timeout.
- Gate transação separado e reservado: script SHA `18BC75C62DFF58310C23FD37311A64366C5044E08BB731314416ACD3214B0821`; Windows auth/Shared memory em `master`, exit 0. Saída SHA `DD6DC9CA1D45835AF21A1544C0C9D788C4687000A3A4B0CE9111A0F52CF99AC6`: `CONNECTED_MASTER_TRAN`, `BEFORE_TRAN73`, **zero linhas `sys.dm_tran_session_transactions WHERE session_id=73`**, `TRAN_QUERY_DONE`. Sem `requests`/`connections` porque SPID 73 já não existia.
- Classificação: SPID 73 e sua associação transacional estavam ausentes no momento dessas duas leituras. Natureza/owner anterior, outras sessões e zero transações **não provados**. A melhora das seleções mínimas não identifica a instrução ou causa do timeout 0358. A condição expressa para configurar SQL auth não se cumpriu; não conectou ao shadow.
- Readback OS SHA `1F14A5AA910987C368C758E5ECA5A86758122B3D1BB941059FEB2BAD41AA8E21`: mesmo serviço PID 20404, dois listeners loopback, zero cliente TCP e nenhum `sqlcmd` remanescente. Isso não prova inexistência de clientes Shared memory. Ledger físico privado SHA `9257F50BE06694FE598035B7C74DA31C9C1DBFD7E94E7FF2EAC241315E5F2AE7`.
- Sem SQL auth/login/user, senha/DPAPI, alteração LoginMode, restart, KILL, retry ou efeito P08. FAIL P08 0354, stats 2536 não aceitas, FAILs anteriores e limites do backup 0325 preservados.

## Próximas ações

1. Supervisor decidir gate pontual em `master` para demonstrar zero outras sessões/transações de usuário, seguido de preflight completo no shadow se responder; não inferir zero da ausência da SPID 73.
2. Apenas após esses critérios, reservar SQL auth e readback em unidade própria. P08 permanece independente.
