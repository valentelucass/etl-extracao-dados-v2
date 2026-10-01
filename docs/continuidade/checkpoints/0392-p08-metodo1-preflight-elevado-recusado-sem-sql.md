# 0392 — P08: método 1 recusado no contrato do helper OS, antes de SQL

- Data local: 30/09/2026. Anterior: [0391](0391-p08-stats-delta-classificado-offline.md), SHA `0BEDCE80137DAC4BBF905724DD983236E9711FD892FF36174A8C2D107738460A`.
- Autoridade: nova unidade física do Supervisor para **somente o método 1** pendente de 0377, com teto 900 s, pin 0383 candidato e condição de stats PRE exatamente igual ao output 0390/POST igual ao PRE. Banco único executor SQL/JDBC/ledger/editor. Resultado: **STOP_OFFLINE_PROOF_SCHEMA_MISMATCH_PRE_OS**, zero SQL/IT, P08 aberto.

## Preparação offline e reserva

1. Espelho novo isolado sem `target`/`.env` herdados: **2170/2170 hashes** de fonte e cópia iguais, sem drift. Candidatos 0383 revision `16B013BF258A76E54E451CDF0039F598EECF3D23D1D8C8246AE86F93F119E1EB`, manifest `576C60B115DEA15D3B4C4D1271780500DD54A8271C873A648B37222A5EEAC171`, ZIP `64A01528AF95F209D662598E7C02D135FB7B6C8E44A240779DD2CD99FC912CC6` conferidos apenas offline. O seletor exato do método 1 tem um método em bytecode no recibo 0377, fixture e POM nos mesmos bytes. ScriptDom160 e parser PowerShell dos guards/helpers, SQL argv master/shadow, Maven offline JDK17 com duas travas, URL privada local integrada, DLL 12.8.2 do cache para `target/native`, dois mutantes do runner e guard 55104 de 0380 pinado passaram offline. Primeiro FAIL de sintaxe no próprio teste offline foi preservado e corrigido antes da reserva.
2. A prova offline SHA `67B4E1880A82FF15D4A704C17B8801E84DB7D6DB66B27A329A929E49E0FC7E6C` **não validou a compatibilidade de nomes do campo SHA da sonda entre seu recibo e o helper elevado**. Ela escreveu `osProbeSha256`; o helper de 0388 copiado exigia `probeSha256` sob `Set-StrictMode Latest`. Esta lacuna da prova foi identificada somente após a primeira chamada elevada. O guard P08-MASTER-TARGET-01, shadow 55811–55814 e 55104 não foram alterados, mas tampouco executados nesta unidade.
3. Reserva física **nova** de até 900 s registrou alvo, impacto de eventual leitura SQL/IT sintética rollback-only e recuperação por parada/readback, antes da primeira chamada elevada. Não houve aproveitamento de saldo anterior.

## Recusa e readback

1. O launcher elevado foi chamado **uma vez**. O filho retornou exit não zero com recibo `FAIL_ELEVATED_OS_FREEZE`, estágio `PRECONDITION`; nenhuma evidência de consulta OS interna foi emitida. Inspeção offline confirmou ausência de `probeSha256` no recibo e presença de `osProbeSha256`, sem recibo interno de PID e sem outputs SQL. A causa da recusa neste estágio é **incompatibilidade do contrato do recibo privado**, não deriva comprovada do serviço.
2. Pela parada obrigatória na primeira falha, **zero retry**, **zero `sqlcmd`**, zero consulta a `master`/shadow, zero guard 55104, zero Maven/JDBC/DLL/IT, zero XML Failsafe e zero stats PRE/POST. Os 2537 grupos de 0390 continuam observação histórica do snapshot 0390, não referência PRE desta rodada e não baseline aceita.
3. Um readback OS independente após a recusa confirmou `MSSQLSERVER` `Running/Manual`, processo único vinculado ao serviço, listeners apenas `::1`/`127.0.0.1` e zero cliente. A imagem do processo não foi reconsultada no readback; o preflight OS elevado **não passou** nesta unidade. Nenhuma mudança no serviço foi feita.
4. Recibo sanitizado SHA `C16A4DD4B945DAC4339F31DBF90B4B83228460D755D9B70C37633688F8233344`; ledger fechado SHA `34031614449463A24F1BD4AE25FF5086E4DEAEDC380B64725217995B1A487C5E` em **122,176 s** de 900. Uma chamada elevada, zero SQL/Maven/JDBC. Outputs e FAIL desta unidade preservados.

## Retomada

- O método 1 e os outros cinco permanecem não executados nesta campanha; 107 ITs/A-B não iniciados. FAILs 0354/0378–0391, oito erros/74 classes faltantes, JaCoCo/Gate 1/P08 e restrições de grants em outros bancos preservados. Sem DDL/Flyway/KILL, start/restart/login/grant, fonte real/remoto/produção.
- Próximo responsável: Supervisor ETL decide unidade **nova**. Antes de qualquer nova reserva/efeito, corrigir em cópia privada a chave para `probeSha256` ou o contrato equivalente, exigir teste offline negativo de campo ausente/renomeado e preservar este FAIL. Somente depois considerar espelho/preflight/reserva novos; não repetir a chamada elevada desta unidade nem inferir resultado do método 1.
