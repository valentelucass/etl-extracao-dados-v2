# 0389 — P08: diagnóstico 55804 no master, três predicados separados

- Data local: 30/09/2026. Anterior: [0388](0388-p08-os-elevado-pass-guard-master-55804.md), SHA `B82B5D0708C4856DD3728B2B74EC88F669B89B25942F16CB365DA7087E2DBA55`.
- Autoridade: nova unidade diagnóstica read-only do Supervisor. Banco é único executor SQL/ledger/editor. Um `sqlcmd -E -C` no `lpc:localhost/master` para separar os três predicados do 55804, sem reexecutar o guard composto, conectar ao shadow, consultar outro banco ou relaxar recusa de consumidor/transação. `../CONTEXTO_GLOBAL.md` continua ausente no caminho esperado; AGENTS, STATES, RETOMADA, 0388, runbook de continuidade e `maestri list` foram lidos.
- Estado: **PASS_DIAGNOSTIC_ONLY_PREDICATE_OTHER_DB_FALSE**. Diagnóstico concluído; P08/IT seguem abertos.

## Preparo e reserva

1. Script estático privado `target/p08-diag-55804-20260930-01/diagnose-55804-master.sql` consulta somente metadados/DMVs do `master`; nenhuma tabela de domínio, outro banco, nome de banco adicional, caminho, SPID, login ou texto SQL é projetado. O único `THROW` protege contexto exato local/Windows/master; não há `THROW` composto 55804. Um marcador numérico discrimina alvo ONLINE, quantidade de outros bancos por estado e sessões/requests/transações agregadas.
2. Prova offline SHA `F2C437F97D28A86164690EDCE2139FC1FF382E441AD488E8F089A8A8686AF546`: parser `ScriptDom160`, fontes estáticas permitidas, PowerShell parser, argv `sqlcmd` master-only, um launcher OS elevado, quatro marcadores sintéticos positivos e cinco negativos; SQL inválido e escopo `USE` recusados. Primeiro FAIL offline por caminho antigo da DLL do parser ausente foi preservado em `offline-attempt1.json`; a cópia local existente foi usada antes de qualquer reserva/efeito.
3. Reserva física **nova** de 600 s registrou alvo, impacto de leitura OS e agregados de catálogo/DMV e recuperação fail-closed. Não reutilizou 0388.

## Leitura e classificação

1. Uma sonda OS elevada read-only, pelo helper de 0388, passou. Recibo OS SHA `9DEA736849CE8FD94B1116D405577FBB9E6340085B426316F5DC2F1673BB004F`: `LUCAS/MSSQLSERVER` `Running/Manual`, PID único vinculado à imagem Microsoft assinada/SHA pinado, dois listeners apenas loopback, zero cliente. Nenhum caminho de imagem ou PID é emitido neste checkpoint.
2. **Uma** chamada `sqlcmd -E -C` não elevada em `lpc:localhost/master` retornou marcador de formato estrito, recibo diagnóstico SHA `DD94AD74310CF5A39283D51E59F46EBA412AABC51D6D102DF6902A914A4458AD`:

   | Predicado ou observação | Resultado no snapshot |
   | --- | ---: |
   | Alvo exato ONLINE | 1, predicado **verdadeiro** |
   | Outros bancos de usuário ONLINE | 5 |
   | Outros bancos OFFLINE / demais estados | 0 / 0 |
   | Nenhum outro banco de usuário | **falso** |
   | Outras sessões no shadow / sessões de usuário | 0 / 0, predicado de zero sessão de usuário **verdadeiro** |
   | Sessões com request / sessão com open transaction / request com open transaction / associação de transação | 0 / 0 / 0 / 0 |

3. Assim, **somente a proibição de qualquer outro banco de usuário falha no instante do diagnóstico**. A consulta não demonstra que algum deles conceda user ou grant ao reader. O 55804 de 0388 foi observado em outro instante; este snapshot não identifica retroativamente o ramo daquela recusa nem garante ausência contínua de consumidores/transações. O guard de consumidor/transação não foi executado ou modificado; nenhuma IT foi admitida.
4. Readback OS posterior SHA `FA0BCE2004773341AD4AFEFC97B3F56D2C8CC81B9B68F6471E523FA4DA976CCB` confirmou mesmo PID, serviço `Running`, listeners somente loopback e zero cliente. Recibo final SHA `E86495290C789255CEDD73B54433E87DD602DA87B66F673F4AA6951F578281CE`; ledger fechado SHA `4B3932A1C1195D7EFAA7C9C2E781E34C97D2ED6FBC52540296FEC0DAFA846E9B` em **90,398 s** de 600. Uma chamada OS elevada, uma chamada SQL master; **zero conexão ao shadow** e sem retry.

## Limites e próximo responsável

- Não houve readback novo do modo misto, `sa`, reader/user/grants, Flyway 106/105/0, agregados de tabelas, 064 ou stats. Stats 2536 permanecem históricos/observacionais; 0388 e FAILs 0354/0378–0380/0385–0387, 8 erros/74 classes faltantes, JaCoCo/Gate 1/P08 continuam abertos.
- Supervisor ETL decide eventual gate **novo** que examine privileges do reader no escopo autorizado e preserve integralmente o guard de consumidor/transação, com reserva própria. A contagem de bancos do host não é prova de grant nem motivo para admitir IT. Sem Start-Service/restart/KILL, JDBC/IT/Maven/DLL, DDL/DML/Flyway/login/grant, outros bancos consultados, fonte real, remoto ou produção.
