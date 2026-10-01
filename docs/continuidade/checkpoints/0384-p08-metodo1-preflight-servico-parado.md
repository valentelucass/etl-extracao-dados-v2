# 0384 — P08: método 1 parou no preflight com serviço local parado

- Data: 29/09/2026, horário local. Anterior: [0383](0383-p08-package-shadow-ab-pwsh-portatil-offline.md).
- Autoridade: nova unidade do Supervisor sob pedido vigente para qualificar uma única vez `AnalyticExpansionCaptureIT#packagedInputsHydrateMissingFreightAndReuseBothFactsAcrossFourModes`, método 1 ainda pendente de 0377. Alvo exclusivo `localhost/ETL_SISTEMA_V2_SHADOW`, Windows auth, teto físico 900 s; preflight com zero consumidores e guard 55104 intacto imediatamente anterior ao Maven. Excluídos KILL, start/restart/login, Flyway/DDL, demais métodos, 107 ITs, A/B físico, fonte real, remoto e produção.
- Estado: **STOP_PREFLIGHT_SERVICE_NOT_RUNNING_NO_SQL**. A IT não começou. Reserva fechada, sem resultado de SQL incerto e sem retry.

## Preparação e reserva

1. `AGENTS.md`, `STATES.md`, `RETOMADA.md`, runbook de continuidade e checkpoint 0383 consultados; `../CONTEXTO_GLOBAL.md` continua ausente no caminho esperado. `maestri list` identificou `Codex` como Supervisor. Nenhum arquivo Java, SQL versionado, migration ou baseline foi alterado.
2. Candidatos A/B offline 0383 conferidos contra recibo SHA `B9A2DF7699FDB4F2D11D3A05F2DFEB2A43BC263806A50F3ED8EF410D83338EF7`: revision `16B013BF258A76E54E451CDF0039F598EECF3D23D1D8C8246AE86F93F119E1EB`, manifest `576C60B115DEA15D3B4C4D1271780500DD54A8271C873A648B37222A5EEAC171`, ZIP `64A01528AF95F209D662598E7C02D135FB7B6C8E44A240779DD2CD99FC912CC6` e lock `7CA07D0509DFE2300BBBE0077EC2B83E5F177A57E857E29D64DAEEE660763465`. São somente candidatos offline, sem smoke ou aceite físico.
3. Espelho novo `C:\Users\lucas\p08m0384_01` passou **2170/2170** hashes e ficou sem `target`/`.env` herdados. O invocador direto do preparo foi recusado pela Execution Policy do PowerShell antes da cópia; a invocação offline com `-ExecutionPolicy Bypass` concluiu o mesmo preparo, sem SQL. O byte da fixture do método permaneceu igual à prova de compilação offline 0377. Helper privado de guard copiado de 0380 sem alteração, SHA `62E4B9640429F653D183B60E50896AC45ACD336626F2E2750DC51740A170FBF5`; o runner exigiria esse hash e sucesso com zero outras sessões.
4. Reserva **nova** `target/p08-directed-wait-20260929-05/reservation.json` foi registrada antes de SQL com 900 s desde 02:10:33 UTC de 30/09/2026, alvo, impacto e recuperação. Impacto previsto: leituras locais de metadata/locks e, somente após guard PASS, IT sintética rollback-only com possível lock/estatística automática. Recuperação: parada sem retry, readback independente e congelamento de resultado incerto.

## Preflight, readback e limite

1. A única chamada de preflight retornou **`SERVICE_NOT_RUNNING`** no controle OS, antes da primeira invocação `sqlcmd`. Não houve consulta `master`/shadow, guard, Maven, JDBC, DLL ou XML Failsafe nesta rodada.
2. Readback OS independente, sem start/restart: `MSSQLSERVER` **Stopped**, sem PID, zero listeners do serviço, zero processos Java/cmd próprios, zero arquivos de saída SQL e `target` ausente no espelho. Como o serviço não estava disponível, histórico Flyway, objetos, contagens, 064 e stats **não foram medidos nesta unidade**. O valor 2536 segue apenas observação histórica sem baseline.
3. Ledger físico `target/p08-directed-wait-20260929-05/physical-ledger.jsonl` fechado em `CLOSED_PREFLIGHT_SERVICE_NOT_RUNNING`, SHA `297474EC35492C0ADF45A73E4F70FE0B2CC0EE7B5E98967D4F2FCBA230A0FF0E`. Recibo sanitizado `final-receipt.json` SHA `C26CD9A408A128DD342848110404C0C7163ED62A1F0934E309397CA2422F40A8`. Nenhum segundo preflight, retry, polling, KILL ou intervenção no serviço.
4. Validação documental: os quatro arquivos compartilhados desta unidade passaram decodificação UTF-8 estrita sem BOM; `git diff --check` saiu 0; `Test-TrilhaPreparation.ps1 -SelfTest` sob PowerShell 7.5.11 portátil passou 1 positivo/24 negativos, 33 etapas/48 IDs abertos/nove pacotes, `executionAuthorized=false`, sem SQL/rede/Maven. Build Java e validação de schema não se aplicam porque nenhum código/schema foi alterado e a pré-condição física parou antes de SQL.

## Handoff

- Método 1 segue não executado; métodos 2–6 também. Preservados PASS isolado 0376, FAILs 0378–0380 e 0354, oito erros, 74 classes faltantes, sete esperas sem causa fechada, JaCoCo/A-B físico/Gate 1/P08 abertos. A recusa atual só prova indisponibilidade do serviço no preflight desta unidade, sem atribuir causa às requests ou timeouts históricos.
- Arquivos compartilhados desta unidade: `STATES.md`, `TRILHA_CONCLUSAO_POR_MODELO.md`, `RETOMADA.md` e este checkpoint. Artefatos de execução estão somente em `target` privado e no espelho novo; sem DDL/DML/Flyway, rede de fonte, login, deploy ou produção.
- Próximo responsável: **Supervisor ETL** decide quando houver evidência nova de serviço `Running` ou autoridade específica para intervenção. Qualquer tentativa física futura exige unidade, reserva, preflight e guard novos; a reserva 0384 não se reutiliza. Banco encerra o turno após handoff.
