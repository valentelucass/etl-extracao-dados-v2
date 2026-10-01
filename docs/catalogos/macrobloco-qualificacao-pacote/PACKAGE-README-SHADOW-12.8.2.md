# ETL V2 — candidato físico da sombra local 12.8.2

Este pacote é uma **variante candidata**, destinada exclusivamente à futura
qualificação sintética em `localhost/ETL_SISTEMA_V2_SHADOW`, Windows/x64 e
Java 17. `dependencies.json`, SBOM, proveniência e manifesto pinam
`mssql-jdbc:12.8.2.jre11` e `mssql-jdbc_auth:12.8.2.x64` do Maven Central.
O lock global 12.8.2 permanece separado por variante. O lock e o README shadow 12.8.1 ficam históricos. A DLL só pode ser carregada durante o
teste local autorizado; não a copie para PATH global nem a versione. O ZIP
não instala SQL Server, executa migrations, chama fonte real ou concede
aceite P07/P08. `PACKAGED_NOT_SMOKE_QUALIFIED` permanece até a prova física.

O manifesto contém SHA-256/tamanho/papel de cada membro. Obtenha o hash do
manifesto por canal confiável e extraia com o validador de envelope; hash não
é assinatura. A verificação recusa extras, ausência, alteração, POM/SBOM
incompatível e caminho nativo acima de 240 caracteres. Mantenha payload
imutável e controle fora dele.

## Comandos offline permitidos no pacote extraído

O launcher verifica o manifesto. `config-validate` e `dry-run` executam
`Main` com configuração `LOCAL_SHADOW`, fontes/auditoria desligadas e
deny-all; limpam `V2_*` herdadas e não recebem flags shadow ou caminho da
DLL. `inspect` e `plan` verificam o pacote e planejam a campanha; não
abrem JDBC nem iniciam worker. Execute em PowerShell 7.5+ e JDK 17:

```powershell
$pin = (Get-Content .\package.sha256 -Raw).Trim()
$campaign = Join-Path $PWD.Path 'config/campaign.synthetic.json'
& .\Invoke-Qualification.ps1 -Command config-validate -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command dry-run -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command inspect -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command plan -ManifestSha256 $pin -Campaign $campaign
```

## Preflight físico obrigatório antes de run/resume/compare

**Os comandos físicos exigem gates de pacote, SCA, schema e transporte loopback antes da execução.**
Primeiro reconciliar a instalação local, verificar `master` read-only com
autenticação Windows e confirmar `SERVERPROPERTY('MachineName')=LUCAS`,
instância local, `DB_NAME()=master`, banco exato ONLINE, V001–V104 e
contagens agregadas de auditoria antes. Confirmar JDBC exclusivamente em
loopback, sem listener em endereço externo, e DLL/JAR 12.8.2 no manifesto.
Parar ante banco/objeto preexistente inesperado, drift ou resultado incerto;
não fazer retry/limpeza. O procedimento de instalação/criação e recuperação
fica em `docs/runbooks/reconstrucao-shadow-local-20260928.md` do repositório.

O laboratório exige `V2_SHADOW_JDBC_URL` no **ambiente do processo**, sem
persisti-la no documento de configuração, no pacote ou no log. Antes de
`master`, controle ou worker, ele valida host literal `localhost`, banco exato,
autenticação integrada, `encrypt=true`, escolha explícita de certificado na
URL, timeouts limitados e ausência de usuário/senha/domínio e propriedades
extras. O supervisor projeta ao worker somente essa URL validada entre as
variáveis `V2_*`. A configuração de certificado deve ser definida pelo
preflight real do alvo local; falha TLS não autoriza fallback silencioso.

Para A/B, usar **dois ZIPs idênticos por SHA**, extrações separadas em caminhos
curtos e controles novos fora dos payloads. Após preflight e autoridade de
execução confirmados, os comandos seriam, em cada diretório extraído:

```powershell
$pin = (Get-Content .\package.sha256 -Raw).Trim()
$campaign = Join-Path $PWD.Path 'config/campaign.synthetic.json'
$control = Join-Path (Split-Path $PWD.Path -Parent) 'controle-proprio-a-ou-b'
if ([string]::IsNullOrWhiteSpace($env:V2_SHADOW_JDBC_URL)) { throw 'QUAL_SHADOW_URL_REQUIRED' }
& .\Invoke-Qualification.ps1 -Command run -ManifestSha256 $pin -Campaign $campaign -Control $control
& .\Invoke-Qualification.ps1 -Command status -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command resume -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command compare -ManifestSha256 $pin -Control $control
```

`run` reserva controle novo e faz preflight `master` antes do worker. O
supervisor recebe JAR da aplicação + `lib/*`; o worker usa os mesmos JARs e
`native/`. A configuração sintética impõe alvo localhost, rollback e limites
de tempo/memória/volume. Comparar contagens de auditoria antes/depois,
journal, saída e rollback; sem recibo terminal, resultado é desconhecido.
Nenhum desses comandos qualifica fonte real, release, deploy ou cutover.
