# 0368 — LOCAL_V2: rodada catalog-only parou no invocador antes de SQL

- Data: 2026-09-29. Anterior: [0367](0367-p08-runtime-seletor-candidatos-offline.md), SHA-256 `B4BB528BD94AA87C818B60C3C9517724A9B5E2DF01DEC9134E33FC4132B0DD57`.
- Autoridade: pedido explícito do Supervisor pós-0367 para **uma** rodada finita catalog-only em `localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth `sqlcmd -E -C`, preflight estrito, consulta única e readback; sem IT Maven, smoke A/B, DDL, Flyway, restart, login ou fonte real. O objetivo material era classificar o ramo de catálogo V024 para `LOCAL_V2`/`DATA_EXPORT`. **Não foi alcançado.**
- Reserva antes do efeito: ledger privado `target/p08-local-v2-catalog-20260929-01/physical-ledger.jsonl`, teto 300 s, timeout por `sqlcmd` 15 s, consulta planejada 10 s e `LOCK_TIMEOUT` planejado 1800 ms. Impacto previsto: leitura de metadados locais com possibilidade de estatística automática; recuperação: parar sem retry, classificar delta por readback, sem mutação/restauração. Pins dos helpers e referências 0354/0367 estão no ledger; nenhuma reserva anterior foi reutilizada.

## Resultado observado e camada

1. Preparação offline: helpers privados de master/alvo/contagens/064/stats/consumidores e uma consulta catalog-only com saída somente de presença, active, compatibilidade de kind/binding e ramo V024. O parser PowerShell reportou zero erros; o help local do `sqlcmd` indicou a grafia `-r1`, corrigida e repinada no ledger **antes** da primeira tentativa. Nenhum arquivo versionado de SQL, Java, contrato, migration, baseline ou Runtime foi alterado.
2. Uma execução do preflight começou pela camada OS: `MSSQLSERVER` em execução, listeners somente loopback. O **primeiro** `sqlcmd`, destinado ao `master`, recusou os argumentos com `'-i': Missing argument` e exit 1. Não há arquivo de saída `master`, marcador de conexão ou qualquer `.out`; a consulta de catálogo não foi chamada. O erro ocorreu na montagem dos argumentos, antes de conexão SQL.
3. Causa do helper reproduzida offline: o parâmetro PowerShell foi nomeado `$input`, variável automática da linguagem. Uma função pura chamada com argumento demonstrou que `$input` estava vazio no corpo; o caminho do arquivo SQL chegou vazio ao `-i`. A correção necessária é renomear o parâmetro e validá-lo offline; **não foi aplicada nem usada nesta reserva**.
4. Readback independente da camada OS encontrou serviço/PID e listeners iguais à fotografia inicial; nenhum output SQL foi criado. Recibo privado `target/p08-local-v2-catalog-20260929-01/frozen-result.json` e ledger fechado com estado `CLOSED_PRE_SQL_INVOCATION_FAIL`, SHA-256 final do ledger `3F03BD294DED0F19E2EA0F8FA3FE3F93495A4D29CBEF94504A753A74E7D835C5`. Sem execução SQL, não há delta SQL a atribuir a esta unidade. Master/alvo, zero consumidores, Flyway, contagens/objetos, 064 e stats **não foram medidos agora**; readback SQL planejado não ocorreu. Nenhum resultado de `LOCAL_V2` foi observado.

Esta falha de invocação congelou a rodada antes das pré-condições SQL e da consulta única. Não houve retry, correção do helper em uso, Maven/IT, smoke, Flyway, DDL, restart, login, alteração de credencial ou acesso a fonte real. O pós-0354 de 2536 grupos permanece apenas fotografia histórica, **não baseline aceita**; esta rodada não o comparou. Os candidatos A/B 0367 seguem apenas offline.

## Estado e próximo responsável

- **LOCAL_V2 físico: DESCONHECIDO.** Não é possível decidir se V024 admitiria ou recusaria o fixture no estado atual; a análise de ramos de 0367 continua condicional.
- **P08: FAIL/aberto.** Preservados os 8 erros 0354, 74 classes não executadas, JaCoCo não alcançado, A/B físico não executado e nenhum aceite novo.
- Supervisor decide se emite **nova** unidade/reserva para o diagnóstico catalog-only após correção e teste offline do invocador. Banco permanece único executor eventual de SQL e dono do ledger. A rodada 0368 não pode ser continuada por retry.
