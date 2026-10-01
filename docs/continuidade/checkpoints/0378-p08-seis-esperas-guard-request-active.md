# 0378 — P08: retomada de seis esperas parou no guard do método 1

- Data: 2026-09-29 UTC. Anterior: [0377](0377-p08-seis-esperas-pausa-sem-efeito.md), SHA-256 `9E0C93D78B2F5EF9D6BA84FC0A892B48E74D985D0D69894A0A7742A995F9D331`.
- Autoridade efetiva: novo pedido explícito do usuário para continuar as qualificações após a pausa 0377, encaminhado pelo Supervisor. Seis métodos de 0377 na ordem exata, serial, uma chamada Maven por método, 3600 s total e 600 s por método incluindo preparação/preflight/guard/IT/readback; parar na primeira recusa/FAIL/delta incerto. Alvo único `localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth, perfil/trava shadow, fixture sintético rollback-only. Proibidos 0376 repetido, 107 ITs/A-B, Flyway/DDL, restart/login, fonte real, outros bancos e produção.
- Estado: **STOP_GUARD_REQUEST_ACTIVE_NO_MAVEN**. A primeira recusa encerrou a campanha. Método 1 ficou sem IT executada; métodos 2–6 continuam não iniciados. Nenhum resultado físico incerto, nenhum retry.

## Preparação, reserva e preflight

1. `../CONTEXTO_GLOBAL.md` ausente no caminho esperado; AGENTS/STATES/RETOMADA/runbook e 0377/0376/0354/0366/0374 lidos. `maestri list` identificou Supervisor `Codex`. Lista ordenada `methods.json` preservou SHA `07E5234BFDE02ACB6DE83DAA6A2869E1C1EE154345D6BAAB9A76CB00211FEFA8`.
2. Espelho físico novo `C:\Users\lucas\p08m0378_01`, sem `target`/`.env` herdados, conferiu **2170/2170** hashes e pins 0374 de revision/manifest/ZIP/lock apenas como candidatos offline. Prova offline 0377 dos seis métodos no bytecode SHA `589C91A3EDE20C11D2A0DC8B148EA5D01884D469000286F0CF2F89D9A83DA5CD` reutilizada somente para seleção; não houve nova compilação nem execução de IT.
3. Reserva **nova** `target/p08-six-waits-20260929-02-m01/reservation.json` antes do primeiro SQL, teto 600 s desde a preparação, alvo/impacto/recuperação registrados. Impacto previsto: leituras locais e, somente se guard passasse, possível transação sintética rollback-only, locks e estatísticas automáticas. Recuperação: parar sem retry, readback independente e congelar resultado incerto.
4. Preflight `sqlcmd -E -C` com Shared memory em `master` e shadow exatos, serviço e listeners só loopback, Flyway **106=SCHEMA+105 SQL/0 fail**, 1819 objetos/247 tabelas/147 linhas agregadas, contagens, 064 e inventário de stats **2536** passaram. `2536` é observação, não baseline aceita. Recibo privado pré SHA `7BD918B6E0D54A5556C40E58FFFBDBDB75407A8DE004F4C22890F0651A206EFF`.

## Guard, parada e readback

1. O único guard categórico, imediatamente antes do Maven, retornou **SQL 55104 `REQUEST_ACTIVE`**. O predicado identifica request ativa associada ao alvo no instante da sonda; não captura owner, statement, blocker, plano ou identidade da request. O runner parou exit **126**. **Zero chamadas Maven, zero XML Failsafe, zero JDBC, zero DLL temporária e zero método executado**. Preflight e readback marcaram zero outras sessões em seus instantes, o que não invalida a recusa pontual do guard.
2. Readback independente por Windows auth em `master`/shadow e OS: alvo, histórico Flyway, contagens agregadas, 064, inventário de stats, PID e listeners iguais nos recortes medidos; **2536→2536**, ainda só observacional. Recibo pós SHA `BCEA3C4DB079E0F5A0CA16F773C5BEB6845140C221B13B0C72ADFC649883F1FC`. Zero processo Java/cmd próprio e `target` ausente no espelho.
3. Fechamento em **168,844 s** do teto do método: `target/p08-six-waits-20260929-02-m01/final-receipt.json` SHA `76ED118CFB77053B4425CB15DBDFCDA106060DBBAA44798A228D08C255F5507F`; ledger físico SHA `1D8AA0C407739FEE9BAAB8622E436B05B0FFEB909D81B2D35F60DE3AE6D1A10F`. `physical-result.json` SHA `4FBE71DC39ED193D98360F04A16D2BD9FA2C7DC7327EAD15D1BBCD276134C3AA`. Artefatos privados e FAILs históricos preservados.

## Limites e handoff

- Guard recusado é falha de pré-condição da campanha, não prova de defeito do método nem de causa das sete esperas 0354. O método 0376 permanece PASS apenas naquela rodada; 8 erros históricos, 74 classes faltantes, JaCoCo, A/B físico, Gate 1 e P08 abertos; pins 0374 não aceitos.
- Arquivos versionados alterados nesta unidade: somente `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md`, `RETOMADA.md` e este checkpoint. SQL/Java/Runtime, migrations/baseline e artefatos anteriores intactos. Nenhum DDL/Flyway, KILL/fechamento de cliente, restart/login, fonte real, outro banco ou produção.
- Próximo responsável: **Supervisor ETL** revisa a recusa SQL 55104 e decide eventual gate físico distinto, com autoridade/reserva próprias; **Banco** permanece único executor SQL/JDBC/ledger. Não há reserva ativa ou processo próprio esperando resposta.

## Próximas ações

1. Supervisor recebe o handoff e confirma a parada no primeiro guard recusado, sem promover P08.
2. Se houver nova autoridade explícita, definir unidade e teto novos após avaliar a pré-condição `REQUEST_ACTIVE`; não reutilizar a reserva 0378 nem repetir o efeito nesta campanha.
