# 0391 — P08: delta de stats 2536→2537 classificado offline

- Data local: 30/09/2026. Anterior: [0390](0390-p08-master-target-guard-revisado-stats-delta.md), SHA `9944284612404B2A0B0DD056DB61114A0FFB5766679F1ECAC4B063F69506EECB`.
- Autoridade: nova unidade do Supervisor para classificar o delta observacional de 0390, começando pelos outputs privados 0376/0380. Banco é o responsável exclusivo por eventual SQL/ledger e pela sincronização de estado. Resultado: **PASS_OFFLINE_DELTA_CLASSIFIED**, sem baseline aceita e sem IT.

## Comparação e prova

1. Os quatro inventários pré/pós, inclusive após 064, de **0376** e **0380**, além dos quatro de 0378, existem e são byte a byte idênticos: **12 outputs**, **2536 grupos**, dez campos por linha, hash de arquivo `9090E84C838A0C0E7ED77742736A03AF4281CB0008EDACA26E3E051F51B3D33F`. A consulta estática fonte exige `DB_NAME()` do shadow exato, Shared Memory e Windows auth. O output 0390 usa a mesma consulta, formato de dez campos, **2537 grupos**, hash de arquivo `2782DFE69FA94677F16D27F3531C58FD3A7A7D938EB1FD400F7730212A7BC57A`.
2. A comparação privada de conjuntos completos encontrou **um grupo adicionado, zero removidos e zero modificados**. O grupo adicional é `auto_created=1`, `user_created=0`, sem filtro, não associado a índice e com uma entrada de coluna. Agregados por grupo mudaram: automático **1714→1715**; criado pelo usuário **0→0**; filtrado **34→34**; associado a índice **822→822**. Estes são grupos do inventário, não uma contagem provada de objetos `sys.stats` distintos.
3. O grupo adicionado **não coincide** com as quatro colunas bloqueadoras V105 declaradas em `064_snapshot_epoch_v105.sql` e não está em tabela cujo nome corresponde à convenção de auditoria `_audit`. Nenhum nome de schema/tabela/coluna, ID ou hash de ID foi registrado aqui, em STATES ou no handoff.
4. Script privado `target/p08-stats-delta-offline-20260930-01/classify-offline.ps1` validou hash, formato, unicidade, escopo da consulta, igualdade dos 12 históricos, categorias e seis negativos de formato/duplicata/hash. Recibo sanitizado SHA `484BE59C86E6EC27541BD3835658BA24625DCF129A8B6617E0AFC4BDB35AEB9C`. A prova distingue o tipo do delta entre **os snapshots capturados**; não identifica quando, por qual request ou por qual tarefa surgiu o grupo adicional.

## Decisão de escopo e retomada

- O inventário individual comparável **existe**; a condição expressa pelo Supervisor para preparar uma nova sonda SQL por falta desse inventário não ocorreu. Nenhum script SQL novo, reserva física, chamada elevada, preflight OS, `sqlcmd` ou ledger novo foi aberto. O output 0390 é a última observação SQL; esta unidade não afirma o estado atual do servidor.
- **2537 não é baseline aceita**, 2536 continua referência histórica, `STATS_OBSERVATION_DELTA` de 0390 e P08/IT permanecem abertos. FAILs históricos, 8 erros/74 classes faltantes e limites de consumidor/transação e grants em outros bancos permanecem. Nenhum JDBC/IT/Maven/DLL, DDL/DML/Flyway/login/grant, start/restart/KILL, fonte real/remoto/produção.
- Próximo responsável: Supervisor ETL decide se a classificação offline encerra apenas esta investigação de tipo ou se uma unidade distinta com autoridade, critério e reserva próprios deve examinar causalidade ou novo snapshot. Não atribuir causa, aceitar baseline nem admitir IT a partir desta diferença.
