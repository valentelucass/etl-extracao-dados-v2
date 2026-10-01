# Reconstrução da sombra local — execução autorizada e controlada

## Resultado atual — 28/09/2026, após prontidão UAC

A única tentativa autorizada concluiu a extração da mídia e o setup SQL Server
2025 Express 17.0.1000.7 com exit 0; `Summary.txt` registra `Passed`.
`MSSQLSERVER` é instância padrão local, serviço Manual agora Running, Windows
auth, TCP/Named Pipes off e nenhum listener TCP do processo. O preflight
read-only em `lpc:localhost/master` confirmou banco e arquivos alvo ausentes.
Um único `CREATE DATABASE [ETL_SISTEMA_V2_SHADOW]` saiu 0; o readback em
`master`/banco confirmou `ONLINE`, compatibilidade 170, collation
`Latin1_General_100_CI_AS_SC`, quotas de dados/log previstas e zero objetos
de usuário/histórico Flyway. Recibos privados e recuperação estão no
[checkpoint 0311](../continuidade/checkpoints/0311-instancia-e-banco-shadow-local-criados.md)
e em `target/shadow-local-rebuild-20260928-01/ledger.jsonl`.

As seções abaixo preservam a preparação e fotografia históricas de antes do
sinal UAC; não descrevem o estado atual do serviço. A variante JDBC física
12.8.1 segue suspensa por Segurança. Sem decisão explícita sobre 12.8.2,
não executar migrations, IT/JDBC ou P07/P08 físicos dependentes do pin.

## Parada segura e prontidão da instalação — 28/09/2026

O operador ainda **não sinalizou prontidão para novo UAC**. A autorização do
usuário para instalar SQL Server **nesta máquina** e criar exclusivamente
`localhost/ETL_SISTEMA_V2_SHADOW` continua vigente; ela não libera repetir
o prompt cancelado antes do sinal. A decisão posterior de Segurança suspende
**JDBC/DDL via perfil físico 12.8.1**, mesmo se o servidor for instalado.
Alterar o pin `AGENTS.md` para JAR/DLL 12.8.2 exige decisão explícita do
usuário. Instalação do serviço é separável desse pin; migrations, JDBC e P08
físico aguardam a decisão e seus gates.

Preflight read-only atual: máquina `LUCAS`, serviço `MSSQLSERVER` ausente,
nenhum processo `sqlservr`/`setup`/mídia ativo e diretório de extração
`%LOCALAPPDATA%\ETL-V2-SQL2025-Media` ausente. C: tem 672,4 GiB livres.
Mídia Microsoft `target/ci-p10-20260927-01/tooling-microsoft/SQLEXPR_x64_ENU.exe`:
748.772.024 bytes, SHA-256
`74AA90C11202A5524E769B9BC22531BAEF22D91E9B2D2E8C3CB99E89A65C5297`,
assinatura Authenticode `Valid`/Microsoft Corporation, igual ao recibo
`media-receipt.json`; `sqlcmd.exe` local é v1.10.0. Nenhum SQL foi conectado.
Repetir esse preflight imediatamente antes do efeito; esta fotografia não
autoriza concluir que o banco não existe em um serviço futuro.

A [documentação atual da Microsoft](https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-command-prompt?view=sql-server-ver17)
exige `/SUPPRESSPRIVACYSTATEMENTNOTICE` junto de `/Q`; o argumento foi
incluído no comando preparado abaixo. A [matriz de requisitos](https://learn.microsoft.com/en-us/sql/sql-server/install/hardware-and-software-requirements-for-installing-sql-server-2025?view=sql-server-ver17)
lista Express em Windows 11 Pro x64 e .NET 4.7.2. A configuração mantém
`/FEATURES=SQLEngine`, sem replicação/Full Text/extension Azure, Windows auth,
serviço manual, TCP/Named Pipes desabilitados, 2 GiB de memória e sem update
automático. Licença e aviso de privacidade são aceitos somente no setup
autorizado; a extração não instala serviço.

Após sinal do operador, usar a sequência serial abaixo **uma vez**. O Windows
pode pedir UAC na extração e novamente no setup; o operador precisa clicar
**Sim** em cada prompt legítimo. Cancelamento, exit incerto, árvore parcial,
assinatura inválida ou serviço inesperado encerra a tentativa, sem retry,
reparo ou limpeza. O alvo do futuro DDL continua somente
`localhost/ETL_SISTEMA_V2_SHADOW`, após preflight read-only em `master`.

```powershell
$repo = 'C:\Users\lucas\OneDrive\Documentos\projetos\etl-extracao-dados-v2'
$media = Join-Path $repo 'target\ci-p10-20260927-01\tooling-microsoft\SQLEXPR_x64_ENU.exe'
$extract = Join-Path $env:LOCALAPPDATA 'ETL-V2-SQL2025-Media'
# Conferir novamente SHA/Authenticode, máquina, serviço/processos, destino e quota.
$unpack = Start-Process -FilePath $media -ArgumentList @('/q', "/x:$extract") -Verb RunAs -Wait -PassThru
# Só após exit 0 e inspeção da árvore completa: localizar setup.exe, validar
# Authenticode/versão, confirmar que nenhum serviço foi criado pela extração.
$setupArgs = @(
    '/Q', '/SUPPRESSPRIVACYSTATEMENTNOTICE', '/IACCEPTSQLSERVERLICENSETERMS',
    '/ACTION=Install', '/FEATURES=SQLEngine', '/INSTANCENAME=MSSQLSERVER',
    '/INSTANCEID=MSSQLSERVER', '/SQLSVCACCOUNT="NT Service\MSSQLSERVER"',
    '/SQLSVCSTARTUPTYPE=Manual', '/SQLSYSADMINACCOUNTS="LUCAS\lucas"',
    '/SQLCOLLATION=Latin1_General_100_CI_AS_SC', '/SQLMAXMEMORY=2048',
    '/TCPENABLED=0', '/NPENABLED=0', '/UPDATEENABLED=0'
)
# Start-Process setup.exe -ArgumentList $setupArgs -Verb RunAs -Wait -PassThru
# A linha comentada só é liberada após o readback da extração e nova reserva.
```

O diretório da mídia extraída pode conter `setup.exe` em subpasta; não
inferir caminho nem executar o exemplar isolado da amostragem 7-Zip. A
execução final precisa registrar caminho exato, hash, assinatura, PID/exit,
`Summary.txt`/`Detail.txt` e serviço observado. Após instalação, o próximo
passo é somente o preflight read-only em `lpc:localhost/master` já definido
abaixo. Não criar banco se houver estado preexistente ou incerto.

## Pausa operacional vigente após cancelamento do UAC

O usuário manteve a autorização para instalar a instância **nesta máquina** e
criar somente `localhost/ETL_SISTEMA_V2_SHADOW`, mas instruiu não repetir o
prompt UAC até o operador indicar que está pronto. Não executar os comandos
preparados nesta seção antes dessa indicação. A tentativa anterior foi
**apenas de extração** da mídia, cancelada pelo Windows; nenhum setup, serviço,
conexão SQL ou DDL ocorreu. Uma recusa UAC não prova bloqueio técnico
definitivo.

O executável de mídia Microsoft assinado contém 201 CABs contíguos, 201
arquivos e 826.661.623 bytes descompactados. O 7-Zip 26.03 standalone da
[página oficial](https://www.7-zip.org/download.html) extraiu amostras em
user-scope, inclusive `SETUP.EXE` assinado validamente pela Microsoft. Os
nomes internos são planos e há 15 duplicações; a posição de cada arquivo na
árvore de instalação não foi demonstrada por essa extração parcial. Não
executar o `SETUP.EXE` amostrado isoladamente. O arquivo completo assinado é
a fonte para extração normal quando o operador liberar o UAC.

### Sequência preparada, ainda não executada

1. Revalidar `Get-AuthenticodeSignature` e SHA-256 da mídia completa contra
   `media-receipt.json`, confirmar `LUCAS`, token e espaço livre; conferir
   `Get-Service MSSQLSERVER -ErrorAction SilentlyContinue`, processos
   `setup`/`sqlservr`, diretório de destino e logs. Se houver instância,
   extração ou instalação parcial, parar e reconciliar o estado primeiro.
2. Com operador pronto para UAC, extrair **uma vez** a mídia Microsoft assinada
   com `Start-Process -FilePath <caminho absoluto de SQLEXPR_x64_ENU.exe>
   -ArgumentList '/q', '/x:<LOCALAPPDATA>\ETL-V2-SQL2025-Media'
   -Verb RunAs -Wait -PassThru`. A chamada só extrai. Conferir exit code,
   árvore extraída, hash/Authenticode de `setup.exe` e ausência de instalação
   antes de prosseguir. Se houver cancelamento ou resultado incerto, parar.
3. Com `setup.exe` da **árvore completa** autenticado, executar uma vez, em
   console elevado e durante janela de 30 minutos, os argumentos de
   instalação listados abaixo. Registrar PID, exit code e `Summary.txt`/
   `Detail.txt`. Verificar serviço, versão/edição, protocolos e ausência de
   listener remoto. Não executar reparo, remoção ou nova tentativa automática.
4. Se o serviço `MSSQLSERVER` estiver instalado e parado, iniciar localmente
   apenas após essa reconciliação (`Start-Service -Name MSSQLSERVER`). Fazer
   preflight **read-only** com Windows auth no `master`, sem acessar qualquer
   outro host/banco:

   ```powershell
   $sqlcmd = '<caminho absoluto validado de sqlcmd.exe>'
   & $sqlcmd -S 'lpc:localhost' -E -C -d master -b -l 5 -t 10 -Q "SET NOCOUNT ON; SELECT @@SERVERNAME AS server_name, SERVERPROPERTY('MachineName') AS machine_name, SERVERPROPERTY('InstanceName') AS instance_name, SERVERPROPERTY('Edition') AS edition, SERVERPROPERTY('ProductVersion') AS product_version, SERVERPROPERTY('Collation') AS collation, SERVERPROPERTY('IsIntegratedSecurityOnly') AS windows_only, DB_NAME() AS database_name, ORIGINAL_LOGIN() AS login_name, CONNECTIONPROPERTY('net_transport') AS transport, SERVERPROPERTY('InstanceDefaultDataPath') AS data_path, SERVERPROPERTY('InstanceDefaultLogPath') AS log_path; SELECT name, state_desc FROM sys.databases WHERE name = N'ETL_SISTEMA_V2_SHADOW'; SELECT database_id, physical_name FROM sys.master_files WHERE DB_NAME(database_id) = N'ETL_SISTEMA_V2_SHADOW';"
   ```

   Exigir `machine_name=LUCAS`, instância padrão, `database_name=master`,
   `windows_only=1`, transporte shared memory, edição/versão/collation
   esperadas, caminhos locais dentro da instância, **zero linhas** nas duas
   consultas do banco alvo, e nenhum listener em endereço externo. A saída
   privada deve omitir qualquer dado de domínio; não capturar credenciais.
   Se o banco ou arquivo alvo existir, parar sem overwrite.
5. Só então materializar o `CREATE DATABASE` estático com `FILENAME` absolutos
   sob os diretórios retornados pela instância, quotas e collation abaixo,
   em chamada única contra `master`. Não interpolar caminho de fonte externa.
   Conferir `sys.databases`/`sys.master_files` após a chamada antes de seguir
   para V001–V104. `CREATE DATABASE` não participa de transação de rollback;
   por isso uma falha ou perda de resposta exige reconciliação, nunca retry,
   `drop`, limpeza ou restauração automática.

**Recuperação em estado incerto:** somente ler serviço/processos e logs de
`%ProgramFiles%\Microsoft SQL Server\170\Setup Bootstrap\Log\` (última
pasta, `Summary.txt` e `Detail.txt`), sem nova execução; se houver serviço
alcançável, repetir apenas a consulta read-only no `master` para identificar
nome/estado/arquivos do banco. Preservar instalação parcial e recibos para
decisão humana. `CREATE DATABASE` não será sequer preparado como comando
executável com `FILENAME` até que os caminhos reais do preflight sejam
conferidos. As validações sintéticas com rollback e contagens agregadas
antes/depois seguem a seção de schema abaixo; JDBC precisa de bind loopback
comprovado e DLL 12.8.1 no perfil opt-in.

## Decisão vigente de 28/09/2026, antes de qualquer efeito

O usuário autorizou expressamente o único Builder a **instalar SQL Server
local nesta máquina e criar exclusivamente o banco local
`ETL_SISTEMA_V2_SHADOW` para testes**; confirmou novamente “está autorizado,
já que é teste”. Essa decisão supera apenas a espera por autorização de
instalação/criação descrita na fotografia histórica abaixo. Não autoriza a
máquina da sombra antiga, host remoto, outro banco, login/senha, job, conexão
remota, `drop`, `restore`, `clean`, deploy ou cutover. Até este registro não
houve conexão SQL, DDL nem instalação de serviço. `STATES.md` conserva os
aceites.

**Alvo escolhido pelo Builder:** Microsoft SQL Server **2025 Express x64**,
versão de mídia `17.0.1000.7`, instância padrão **`MSSQLSERVER`** na máquina
Windows `LUCAS`, acessada localmente por `lpc:localhost` no `sqlcmd` e pelo
host `localhost` no JDBC após configuração restrita. Express é edição gratuita
conforme [Microsoft Learn](https://learn.microsoft.com/en-us/sql/sql-server/editions-and-components-of-sql-server-2025?view=sql-server-ver17);
[Windows 11 Pro x64 e .NET 4.7.2 são suportados](https://learn.microsoft.com/en-us/sql/sql-server/install/hardware-and-software-requirements-for-installing-sql-server-2025?view=sql-server-ver17).
Havia 682,5 GiB livres em C:, 15,9 GiB RAM, .NET release 533509, nenhum
serviço/processo SQL Server. O processo atual é `LUCAS\lucas`, **não
elevado**; setup requer administrador conforme
[Microsoft Learn](https://learn.microsoft.com/en-us/sql/database-engine/install-windows/install-sql-server-from-the-command-prompt?view=sql-server-ver17).
O download da mídia completa veio de `download.microsoft.com`, 748.772.024
bytes, SHA-256 `74AA90C11202A5524E769B9BC22531BAEF22D91E9B2D2E8C3CB99E89A65C5297`,
assinatura Authenticode `Valid`, `Microsoft Corporation`. O bootstrapper
`SQL2025-SSEI-Expr.exe` não será usado para configurar a instância, pois não
propaga todos os argumentos de `setup.exe`. Os termos Microsoft referidos
pelo instalador ficam em [aka.ms/useterms](https://aka.ms/useterms); aceitar
os termos só no comando de instalação autorizado. A DLL/JAR JDBC local
permanece no par 12.8.1 do perfil shadow; nenhum 12.8.2 será carregado na IT.

**Configuração de instalação reservada:** extrair somente a mídia para
`%LOCALAPPDATA%\ETL-V2-SQL2025-Media`; verificar assinatura do `setup.exe`.
Executar uma vez, sob elevação legítima do Windows, `setup.exe /Q
/SUPPRESSPRIVACYSTATEMENTNOTICE /IACCEPTSQLSERVERLICENSETERMS /ACTION=Install /FEATURES=SQLEngine
/INSTANCENAME=MSSQLSERVER /INSTANCEID=MSSQLSERVER
/SQLSVCACCOUNT="NT Service\MSSQLSERVER" /SQLSVCSTARTUPTYPE=Manual
/SQLSYSADMINACCOUNTS="LUCAS\lucas" /SQLCOLLATION=Latin1_General_100_CI_AS_SC
/SQLMAXMEMORY=2048 /TCPENABLED=0 /NPENABLED=0 /UPDATEENABLED=0`.
Ausência de `/SECURITYMODE=SQL` mantém somente autenticação Windows;
conta virtual não requer senha. `SQLEngine` instala só o mecanismo, sem
extensão Azure, Full Text ou SSIS. TCP e Named Pipes desabilitados evitam
listener remoto; `sqlcmd` Go suporta `lpc` local conforme
[Microsoft Learn](https://learn.microsoft.com/en-us/sql/tools/sqlcmd/sqlcmd-utility?view=sql-server-ver17).
Não abrir regra de firewall. A instalação tem teto de 30 minutos e uma
tentativa. O serviço Manual é iniciado apenas para os testes autorizados.
Se setup falhar ou retornar estado incerto, preservar `Summary.txt`,
`Detail.txt`, serviço, arquivos e registro; verificar estado antes de qualquer
nova ação. Não executar reparo, desinstalação ou limpeza automática.

**Criação reservada após instalação:** primeira conexão em `master` é
read-only com `sqlcmd -S lpc:localhost -E -C -d master -b -l 5 -t 10`.
Conferir `SERVERPROPERTY('MachineName')=LUCAS`, instância padrão local,
`DB_NAME()=master`, autenticação Windows, edição/versão, collation, ausência
exata de `ETL_SISTEMA_V2_SHADOW` em `sys.databases` e ausência de listener
remoto. Se o banco já existir, **parar**. O único DDL de banco permitido é
`CREATE DATABASE [ETL_SISTEMA_V2_SHADOW]` com diretório padrão da instância,
data file inicial 64 MiB, teto 2 GiB/crescimento 16 MiB, log inicial 32 MiB,
teto 512 MiB/crescimento 16 MiB; `COLLATE Latin1_General_100_CI_AS_SC` e
`COMPATIBILITY_LEVEL=170` conferido depois (se o default divergir, parar e
avaliar antes de `ALTER`). O DDL será estático, usando caminho local retornado
por `SERVERPROPERTY('InstanceDefaultDataPath')` e `'InstanceDefaultLogPath'`
após validação; no máximo uma execução e cinco minutos. Preflight e DDL
devem ficar em recibos separados. Estado parcial ou resposta perdida é
`UNKNOWN`: ler `sys.databases`/arquivos/objetos e parar; não repetir nem
apagar o banco. Sem backup/retirada automática: a sombra sintética fica
preservada até decisão posterior. Seguir migrations V001–V104 e rollback dos
validadores apenas se o banco novo estiver vazio e os hashes/versões
conferidos.

**JDBC:** o servidor inicialmente não aceitará TCP; não ativar protocolo
genérico para viabilizar a IT. Investigar e registrar configuração que vincule
somente loopback antes de qualquer alteração de rede, e conferir listener
`127.0.0.1`/`::1` sem endereço externo. Se isso não for garantido, manter a
IT pendente e executar migrations/validadores via protocolo local possível;
não alegar P07/P08 físicos completos.

## Fotografia histórica anterior à autorização

Estado: **PREPARADO, SEM AUTORIZAÇÃO DE INSTALAÇÃO OU CREATE DATABASE**. A sombra
anterior está em outra máquina. Este procedimento não a acessa nem transfere
dados, recibos ou autoridade dela. O único alvo proposto é o host `localhost`,
banco `ETL_SISTEMA_V2_SHADOW`, autenticação Windows existente. `STATES.md` é a
fonte de aceites e limites; este runbook não conclui P07/P08/P10/P29.

## Fotografia read-only de 28/09/2026

| Item | Estado conferido |
| --- | --- |
| Máquina local | Nenhum serviço SQL Server local, nenhum listener TCP local na porta 1433, `sqlcmd.exe` ausente; `V2_SHADOW_JDBC_URL` ausente no processo. Portanto não há preflight de `master` nem prova de existência/ausência do banco em uma instância. Nome de instância, versão, edição, porta e caminho de storage ainda não são conhecidos. |
| Fonte estrutural atual | `database/migrations/V001__...` até `V104__...`: 104 migrations; `database/baseline/001_schema_foundation_baseline.sql` referencia as 104 em ordem. `database/manifest/` contém 36 arquivos de contratos/fingerprints; `database/validation/` contém 67 arquivos entre validators de leitura e exercícios transacionais. |
| Conferência offline | `Test-SchemaFoundationManifest.ps1` e `Test-ProgressiveDataGate.ps1` saíram 0 na revisão v60; logs em `target/ci-p10-20260927-01/`. Isso verifica arquivos/contratos, não um SQL Server. |
| Inventários e provas anteriores | `docs/continuidade/checkpoints/0241-p07-integral-conferido.md`, `0242-p08-pacote-conferido.md`, `0243-qualificacao-local-e-sucessao-conferidas.md`, `docs/continuidade/qualificacao-p07-p33/{matriz,validation}.json` e `database/manifest/` preservam a fotografia histórica. Os diretórios privados de recibos `target/qualificacao-p07-p33-20260922-01/`, `target/macrobloco-qualificacao-pacote-20260913-01/` e `target/bloco55/schema/...` não existem nesta máquina. Nenhum desses registros comprova o novo banco. |
| Toolchain offline | Java 17.0.20.1+1 verificado e Maven wrapper já executaram `clean verify` v60; PMD dez regras/656 fontes e disposição 36/36 passaram na camada local. `sqlcmd` é tarefa de preparação de ferramenta, sem autorização implícita para instalar servidor ou criar banco. |

## Decisão material exigida antes de qualquer efeito SQL

O usuário, por intermédio do Supervisor, deve decidir nominalmente se autoriza **(a)** instalar ou prover
um serviço SQL Server **nesta** máquina, acessível pelo nome `localhost` usado
pelos scripts, e **(b)** criar exclusivamente o banco novo
`ETL_SISTEMA_V2_SHADOW`. A decisão deve indicar responsável de instalação/DBA,
instância/edição/versão, autenticação Windows, collation e compatibility level
aprovados, storage e quota, política de backup/retirada da sombra, janela,
limite de tentativas e recuperação de criação parcial. Não se infere default
instance, porta, diretório, login ou permissão administrativa. Se já existir
serviço/banco quando o preflight ocorrer, parar e qualificar o estado real;
não fazer drop, restore, overwrite, `flyway:clean` ou reset histórico.

A autorização de `AGENTS.md` de 25/08 cobre migrations versionadas e
validações sintéticas no banco local exato **depois de existente e conferido**;
não cobre instalar serviço ou `CREATE DATABASE`. A instrução atual do usuário
exige decisão específica antes desses dois efeitos; o Supervisor informou que
não tem autoridade para concedê-la. A pergunta objetiva ao usuário está
pendente. O Supervisor também fixou a decisão para JDBC: o perfil
`shadow-local-integration` agora seleciona `mssql-jdbc:12.8.1.jre11` e
`mssql-jdbc_auth:12.8.1.x64`, sem alterar os valores globais 12.8.2. A
documentação oficial Microsoft [descreve o carregamento da DLL com a versão
do driver no nome](https://learn.microsoft.com/en-us/sql/connect/jdbc/setting-the-connection-properties?view=sql-server-ver16),
e as [release notes 12.8.1](https://learn.microsoft.com/en-us/sql/connect/jdbc/release-notes-for-the-jdbc-driver?view=sql-server-ver17)
incluem suporte ao JDK 17. O perfil `shadow-migrations-windows-auth` também
fixa ambas as propriedades em 12.8.1; a avaliação Maven efetiva confirmou
o isolamento em ambos os perfis shadow e conservou 12.8.2 no build normal.
`process-test-classes` com as duas travas `shadow-local-integration` passou
sem URL/SQL e copiou só a DLL 12.8.1 a `target/native`; `dependency:tree`
confirmou o driver 12.8.1. A IT física ainda depende do banco autorizado,
do preflight, de contagens e rollback. A combinação 12.8.2
do lock histórico P08 permanece uma divergência do fluxo de pacote: não
executar DLL 12.8.2 pelo perfil shadow nem tratar o lock como revisado.

## Sequência pronta após a decisão, sempre serial

1. **Congelar revisão e ledger novo.** Registrar SHA-256 do `pom.xml`, das
   migrations V001–V104, baseline, validators, fonte/testes e toolchain;
   conferir `git status` e resultados pendentes. Criar reserva com alvo,
   responsável, teto, janela e recuperação para cada efeito. Não reabrir os
   ledgers de 22/09 nem reutilizar a aprovação temporal deles.
2. **Prover ferramenta e instância somente no escopo aprovado.** Resolver
   `sqlcmd` local; ferramenta ausente não é bloqueio de código. A instalação do
   serviço e parâmetros de instância/storage dependem da decisão acima.
   Nenhuma conexão à máquina antiga. Nenhuma mudança de `PATH` global para a
   prova Java; Java e DLL ficam no processo/`target`.
3. **Preflight `master` somente leitura antes de DDL.** Conectar com
   `sqlcmd -S localhost -E -C -d master -b -l 5 -t 10`, conferir
   `DB_NAME()='master'`, `SERVERPROPERTY('MachineName')` igual ao nome da
   máquina local, `IsClustered=0`, `IsHadrEnabled=0`, autenticação Windows e
   existência/estado exatos de `ETL_SISTEMA_V2_SHADOW` em `sys.databases`.
   Registrar apenas nome/estado/contagens sanitizados. Qualquer alias remoto,
   instância inesperada, DB já existente com objetos/linhas ou ambiguidade
   interrompe a rodada; não ajustar o alvo por tentativa e erro.
4. **Criar somente após aprovação explícita.** O DBA/owner aplica o DDL
   revisado para `CREATE DATABASE [ETL_SISTEMA_V2_SHADOW]` em `master` na
   instância local aprovada, com parâmetros acordados. Não criar login,
   usuário, senha, job, outro DB ou objeto de produção. Ler novamente
   `sys.databases` e conectar explicitamente ao banco exato. A contagem
   inicial de objetos de usuário e linhas deve ser zero; divergência pára.
   A recuperação de falha de criação é preservar o estado e obter decisão
   do DBA sobre o banco exato, nunca repetir ou fazer drop automaticamente.
5. **Instalar schema versionado.** Antes da aplicação, executar os dois
   validadores estáticos acima; conferir 104 nomes sequenciais, hashes e
   baseline. Em banco novo vazio, usar somente o perfil
   `shadow-migrations-windows-auth` de `pom.xml`, URL de processo validada
   para `localhost`/`ETL_SISTEMA_V2_SHADOW` com `integratedSecurity=true`,
   `flyway:info`, `flyway:migrate` e `flyway:validate` em ordem, com
   `cleanDisabled=true`, `baselineOnMigrate=false`, `outOfOrder=false`.
   O plugin e a extensão SQL Server 9.22.3 estão no cache Maven local; não
   executar um goal SQL até confirmar a resolução de autenticação Windows
   para o próprio Flyway dentro do limite de uso da DLL 12.8.1. O perfil de
   migration fixa o driver 12.8.1, mas não configura por si só
   `java.library.path` nem concede uso da DLL fora da IT opt-in. Preparar e
   revisar esse passo de autenticação com o owner antes da migration; não
   substituir silenciosamente por driver/DLL 12.8.2 ou por credencial.
   `database/transition/001_reset_historical_shadow_for_schema_foundation.sql`
   e `database/validation/002_exercise_schema_foundation_baseline_rollback.sql`
   pertencem à transição histórica e **não** devem rodar sobre banco novo.
   Se Flyway falhar, preservar `ctl.flyway_schema_history` e inventário,
   interromper; correção por migration aditiva revisada ou reconstrução exata
   separadamente autorizada, nunca reparo/limpeza automática.
6. **Conferir schema e baseline.** No alvo exato, verificar 104 versões
   Flyway com `success=1`, versão final 104 e ausência de versões extras.
   Registrar inventário sanitizado de schemas, objetos, colunas, índices,
   módulos e permissões por tipo, sem ler linhas de domínio. Executar os
   validators estruturais aplicáveis de `database/validation/`, começando
   por 001/003/005 e pelos da revisão final 056/057/061/062; classificar
   cada arquivo antes da execução em leitura ou rollback sintético. A
   equivalência física baseline × migrations requer um ensaio de baseline
   sobre banco vazio com rollback externo conferido e comparação do mesmo
   inventário após Flyway; preparar e revisar o wrapper transacional antes
   desse ensaio, pois o script 002 histórico inclui um reset inadequado.
   Não chamar essa equivalência de provada apenas pelos checks estáticos.
7. **Executar validações sintéticas.** Reservar cada script de
   `database/validation/` elegível, sempre `-S localhost -E -d
   ETL_SISTEMA_V2_SHADOW -b`, com timeout e rollback próprio. Capturar
   contagens agregadas por tabela antes/depois via `sys.partitions` e
   comparar. Qualquer delta, SQL não-2xx, timeout, sessão incerta ou objeto
   inesperado interrompe; consultar estado autoritativo antes de repetir.
8. **Provar JDBC e P07/P08 nos bytes finais.** Conferir primeiro que o
   perfil ainda resolve o par 12.8.1 e que o pacote P08 corresponde ao lock
   nominal da revisão, sem mesclar versões. Depois, usar as duas travas `-Pshadow-local-integration` e
   `-Dshadow.local.integration.enabled=true`, gateway sintético, conexão
   compartilhada que bloqueia `commit` e faz `ROLLBACK`, sem Flyway/fonte.
   Conferir as contagens de auditoria antes/depois. Executar P07 físico
   serial com JDK17/heap512MiB, suite/formatter/lint/análise e schema; em
   seguida P08 no mesmo revisionamento: dois pacotes reproduzíveis,
   checksum/SBOM/proveniência, JAR extraído, guardas, A/B, variantes,
   scanner, selo/readback. Os scripts P07/P08 ainda têm caminhos absolutos
   antigos de Maven/JDK; parametrizar o toolchain por processo e validar em
   teste offline antes do efeito físico. Cada etapa precisa de reserva e
   recibo novos; a prova de 22/09 não migra para este banco.

## Parada, recuperação e estado de aceite

Não há autorização para DDL enquanto serviço/instância e banco exatos não
estiverem decididos pelo Supervisor. Mesmo após a decisão, qualquer host
diferente de `localhost`, banco diferente de `ETL_SISTEMA_V2_SHADOW`,
resultado SQL desconhecido, contagem divergente ou ausência de rollback pára
as ações dependentes. Preservar log/recibo e consultar `master`, histórico
Flyway e inventário antes de qualquer nova tentativa. P07/P08 e P10/G02 não
recebem checkbox por este preparo; G02 remoto tem decisão própria do owner.
