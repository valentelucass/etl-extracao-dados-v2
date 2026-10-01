# 0369 — LOCAL_V2: preflight parou em consumidores presentes

- Data: 2026-09-29. Anterior: [0368](0368-p08-local-v2-preflight-invocador-fail.md), SHA-256 `FC85B6E538E5A7AB20D00CC4A4EB427C9771E252908EF99CD54A39927317345B`.
- Autoridade: ordem do Supervisor pós-0368 para corrigir o invocador **em cópia privada**, provar offline argv completo e, apenas se PASS, uma nova rodada catalog-only em `localhost/ETL_SISTEMA_V2_SHADOW` com Windows auth `sqlcmd -E -C`. Sem IT Maven, smoke A/B, DDL/Flyway, restart/login, fonte real ou produção.
- Estado: **CLOSED_PREFLIGHT_CONSUMERS_FAIL**. Catálogo `LOCAL_V2` e ramo V024 físicos continuam **DESCONHECIDOS**; P08/0354 seguem FAIL e abertos.

## Preparação, reserva e efeito observado

1. A cópia privada em `target/p08-local-v2-catalog-20260929-02` renomeou o parâmetro PowerShell `$input` para `$sqlFile`. `offline-argv-proof.ps1` passou parse e verificou quatro argv completos para master, alvo, 064 e consulta, incluindo fixture sintética, caminho `-i` absoluto não vazio, `lpc:localhost`, `-E -C`, `-l 5`, `-t 15/10`, `-b -r1` e flags de saída. **Nenhuma conexão** nessa prova. O script e ledger 0368 mantiveram SHA-256 `A400BF8AAADF3CFD9708D8C9F3B3FC3B1ED7A8C8BEE558DF250254828D2F8416` e `3F03BD294DED0F19E2EA0F8FA3FE3F93495A4D29CBEF94504A753A74E7D835C5`.
2. Reserva física **nova** antes de SQL: `target/p08-local-v2-catalog-20260929-02/physical-ledger.jsonl`, teto 300 s, login 5 s, snapshots 15 s, consulta planejada 10 s e `LOCK_TIMEOUT` 1800 ms. Impacto previsto: leituras locais de metadados podem alterar estatísticas automáticas. Recuperação: parar sem retry, readback autoritativo e classificação de delta, sem mutação ou aceite automático. A reserva 0368 não foi reutilizada.
3. Uma execução de preflight: OS confirmou serviço e listeners só loopback; marcadores SQL confirmaram `master` e shadow exatos por Shared memory/Windows auth, Flyway 106 linhas = 1 SCHEMA + 105 SQL, zero falhas, 1819 objetos, 247 tabelas, 147 linhas agregadas, sete schemas sob owner esperado e contagens selecionadas estáveis. Validador 064 SHA-256 `78F6F2664AD673D815D01C7B281DF2D606AF8DE419FAE1A9BC48816582E6D2E3`. Inventários antes/depois de 064 têm 2536 grupos e SHA-256 `9090E84C838A0C0E7ED77742736A03AF4281CB0008EDACA26E3E051F51B3D33F`, exatamente o pós-0354 **como observação de entrada**, não baseline aceita.
4. O guard de consumidores em `master` saiu 1 com **SQL 55003/SHADOW_CONSUMERS_PRESENT**. O marcador do alvo já indicava sessões adicionais; zero consumidores é falso. A consulta única `LOCAL_V2` **não foi chamada** e não há output dela. Sem retry ou correção. Readback OS independente confirmou mesmo serviço/PID/listeners loopback. Pós SQL não foi executado porque o preflight não passou; o inventário de stats medido antecede o guard de consumidores, logo não afirma estado pós SQL integral.
5. O primeiro `frozen-result.json` calculou falsos negativos para guards já observados porque o helper privado supunha collation `NULL` no `master`, zero sessões no marcador do alvo, um literal de contagens com aridade errada e hash 064 de 62 caracteres. Os outputs SQL/SQL guards eram válidos; `reconcile-offline.ps1` leu **somente arquivos já capturados**, produziu `final-result.json` sem expor linha, ID, payload ou segredo e acrescentou evento ao ledger, sem repetir SQL. Hash final do recibo `BBC7CED268E70AB937499986ADF9624BB0803C744E9086BBA2907015ABB5552B`; ledger final `FA018F0606E00DE699D66034EE8ACEF0C8207F5A5DA67E521F68632E9BD2EB2F`. O primeiro recibo foi preservado para auditar o erro de interpretação.

## Limites e próxima decisão

- **Não há resultado catalog-only:** presença, active, kind/binding `DATA_EXPORT` e admissão/recusa V024 continuam desconhecidos. Não imputar 51301 nem aceitar o fixture corrigido. O estado persistido precisaria ser observado em outra unidade, depois de demonstrar zero consumidores.
- O inventário 2536 coincidiu com pós-0354 no ponto medido; +88 automáticas de 0354 continuam sem query criadora e **2536 não é baseline aceita**. Nenhum delta foi promovido ou atribuído a esta rodada.
- Preserve 0354: **8 erros, 33/107 classes executadas, 74 faltantes, JaCoCo não alcançado, A/B físico e Gate 1/P08 abertos**. Candidatos 0367 continuam apenas offline; nenhum aceite novo. Nenhum arquivo Runtime, SQL versionado, contrato ou schema foi alterado.
- Próximo responsável: **Supervisor** decide como obter zero consumidores e se emite nova autoridade/reserva para catalog-only com literais do helper privado corrigidos e prova offline nova. **Banco** continua único executor SQL/ledger. Não repetir nesta reserva.

## Próximas ações

1. Supervisor classifica a condição de consumidores e decide eventual nova unidade; nenhum KILL, restart ou retry implícito.
2. Se houver nova ordem, Banco corrige e prova offline os guards privados antes de reservar qualquer efeito físico novo.
