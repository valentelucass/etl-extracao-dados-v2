# P07/P08 — sombra local 12.8.2: gates físicos

## Atualização 0318 — JDBC e Flyway info locais comprovados

Após o gate de loopback 0317, o Builder qualificou JDK17/IT no perfil
`shadow-local-integration` e executou um JDBC read-only bem-sucedido no alvo
exato: Windows auth, transporte TCP `::1`/`127.0.0.1`, rollback, zero objetos
e histórico ausente. Uma primeira tentativa falhou por assert de caixa
`LUCAS`/`Lucas`, foi preservada e teve readback sem delta; a guarda ASCII
corrigida passou offline antes do único reattempt autorizado. Gate separado
`flyway:info` retornou 104 migrations V001–V104 pendentes; readback confirmou
`ctl`/histórico ausentes e banco ainda vazio. Ver [checkpoint 0318](../continuidade/checkpoints/0318-p08-jdbc-flyway-info-handoff.md)
e `STATES.md` para hashes/ledger. **Este chat termina sem `migrate`, `validate`
físico ou validação sintética pós-schema.** A seção “Resultado da rodada
atual” abaixo é fotografia histórica dos attempts anteriores, preservada
para rastreabilidade, não a condição corrente de parada JDBC.

Decisão explícita do usuário em 28/09/2026: trocar somente o par shadow para
`mssql-jdbc:12.8.2.jre11` e `mssql-jdbc_auth:12.8.2.x64` e prosseguir com
migrations V2 e provas sintéticas exclusivamente em
`localhost/ETL_SISTEMA_V2_SHADOW`. O roteiro 12.8.1 e seus recibos permanecem
históricos. O serviço e o banco foram criados uma única vez no checkpoint 0311;
não repetir setup nem `CREATE DATABASE`.

## Resultado da rodada atual

Atualização após novo sinal do operador: ele declarou que não cancelou o UAC
anterior. A mensagem 4100 é retorno do Windows e não identifica seu gesto.
Sessão console 3, token médio/Administradores em negação, desktop seguro ativo
e AppInfo Running são compatíveis com necessidade de prompt, mas os canais
locais não comprovam que o prompt anterior foi apresentado. Em attempt novo,
preflight de serviço/WMI/socket e SQL `master`/alvo vazio passou. A primeira
invocação do launcher foi recusada por Execution Policy antes do UAC; após
ajuste somente no processo invocador, uma elevação iniciou o helper. Ele parou
na primeira chamada `SetNumericalValue` para `ListenOnAllIPs=0`: retorno
`0x80041024` (`WBEM_E_PROVIDER_NOT_CAPABLE`). Não houve restart. Reconciliação
read-only confirmou mesmo PID/configuração, zero listeners e schema vazio.
**Parada da rodada:** uma elevação consumida; não repetir UAC, nem iniciar
JDBC/Flyway/migrations/IT. Investigar método de configuração suportado somente
em leitura antes de propor outro attempt. Ledger privado
`target/shadow-local-rebuild-20260928-01/pin-1282-uac-v2-ledger.jsonl`.

O delta de pin, lock/README e pacote foi aplicado e passou POM efetivo,
hashes/POMs, A/B, oito comandos puros, 21 recusas, `clean verify` em espelho
v2, PMD local, SAST, secret scan e validadores estáticos. O attempt inicial de
`clean verify` falhou por `JAVA_TOOL_OPTIONS` herdado e permanece preservado.
O preflight v2 em `master` e no alvo exato passou; o banco segue vazio. A
primeira consulta de `master` foi recusada porque `AUTO_CLOSE=ON` torna
`sys.databases.collation_name` temporariamente `NULL`; a collation foi
confirmada no banco por `DATABASEPROPERTYEX`.

A única tentativa elevada para configurar TCP só em loopback terminou com
`InvalidOperationException` antes de emitir recibo do helper. Readback de
serviço, WMI, sockets, `master` e alvo mostrou configuração e banco sem efeito:
TCP/Named Pipes ainda desabilitados, zero listeners SQL e zero objetos de
usuário. **Parada física vigente:** não repetir UAC, não iniciar Flyway/JDBC,
migrations ou IT. A retomada exige novo sinal de prontidão do operador, novo
preflight e reserva. Ledger privado em
`target/shadow-local-rebuild-20260928-01/pin-1282-ledger.jsonl`.

Diagnóstico posterior somente leitura (`P08-UAC-CAUSAL-0314`): o evento
PowerShell 4100/record 47081 registra que o Windows cancelou a operação de
`Start-Process` às 19:08:05.529. O launcher anterior imprimia apenas o tipo
da exceção. O registro não prova a intenção ou gesto do operador; tampouco
mostra erro causal de parâmetro ou do helper. Executável/script existiam,
parâmetros eram aceitos e o helper tem zero erros de parser, hash intacto e
nenhum recibo. O ledger e o helper originais permanecem preservados. Não há
correção causal comprovada nem novo helper para executar agora. Em eventual
attempt distinto, após sinal do operador, preflight e reserva novos, o
launcher deve registrar mensagem e `FullyQualifiedErrorId` sanitizados e
código nativo quando houver; essa melhoria é apenas diagnóstica.

## Ordem e parada

1. Congelar os hashes do código, migrations, baseline, validadores, lock e
   toolchain em attempt novo. Qualificar offline o par JAR/DLL, POMs, licença,
   proveniência, POM efetivo, testes, SAST, pacote A/B e guardas. A DLL no
   pacote é byte passivo. O feed/SCA e aceite nominal P11 continuam externos e
   abertos; nenhum resultado local os substitui. Uma falha preserva o attempt.
2. Reservar e executar somente leitura em `lpc:localhost/master` com Windows
   auth. Confirmar máquina `LUCAS`, instância padrão SQL Server Express local,
   `DB_NAME()=master`, alvo exato `ONLINE`, compatibilidade e arquivos locais, ausência
   de cluster/HA e de listener externo. Verificar collation no próprio alvo
   com `DATABASEPROPERTYEX` porque `AUTO_CLOSE=ON` pode retornar `NULL` em
   `sys.databases.collation_name`. Ler no alvo exato apenas inventário de
   objetos, histórico Flyway e contagens agregadas. Qualquer estado diferente
   do banco vazio do checkpoint 0311 exige reconciliação, sem `clean`/`drop`.
3. Demonstrar transporte JDBC exclusivamente local antes de `flyway:info`.
   Shared Memory em `sqlcmd` não prova o transporte JDBC. Se for necessário
   habilitar TCP, restringir o listener a `127.0.0.1`/`::1`, verificar socket
   real e firewall, sem bind externo; não aceitar conexão remota. Validar
   autenticação integrada da própria invocação Flyway usando JAR/DLL 12.8.2
   temporária, sem credenciais nem PATH global, com prova read-only primeiro.
4. Em banco vazio e após os validadores estáticos, reservar `flyway:info`,
   `flyway:migrate` V001–V104 e `flyway:validate` como gates separados. A URL
   fica apenas no ambiente do processo e deve apontar ao host/banco literais,
   com `integratedSecurity=true`; o plugin usa `cleanDisabled=true`,
   `baselineOnMigrate=false`, `outOfOrder=false`. Após resposta incerta ou erro,
   consultar `master`, `ctl.flyway_schema_history` e inventário; não repetir,
   reparar, limpar ou restaurar automaticamente.
5. Conferir 104 sucessos até V104 e inventário estrutural; classificar cada
   `database/validation/` em leitura ou escrita sintética transacional antes de
   executá-lo. Para validações que escrevem e para a IT opt-in, obter contagens
   agregadas antes/depois no banco exato e exigir ROLLBACK sem delta. A IT usa
   gateway sintético, conexão única compartilhada e bloqueio de `commit`.
6. Só com todos os gates anteriores verdes, qualificar P07/P08 físicos nos
   bytes finais, com controles novos, A/B, extrações separadas e readback.
   Nenhum resultado parcial promove P01–P33, P11, P29, release ou cutover.

Toda reserva registra pré-condição, alvo, teto e recuperação no ledger privado
`target/shadow-local-rebuild-20260928-01/`. A resposta terminal registra camada
e resultado sanitizado. Produção, remoto, fonte real, job e credenciais estão
fora do escopo.
