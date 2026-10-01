# P08 — candidato físico 12.8.1 e preflight pendente

**SUSPENSO em 28/09/2026:** Segurança/Supervisor proibiu executar JDBC ou
a variante física 12.8.1 até decisão explícita do usuário sobre o pin
`AGENTS.md`, por CVE-2025-59250. Este roteiro e os recibos 0304/0305 são
históricos; sua lista de comandos físicos não está liberada mesmo se o
operador liberar UAC. O [delta 12.8.2 proposto](../catalogos/p29-sast-preparacao-20260928/PROPOSTA-SHADOW-12.8.2.md)
não foi aplicado.

Este roteiro registra o preparo offline de 28/09/2026. A pausa do UAC permanece:
não executar setup, SQL, DLL nativa ou comandos `run`/`status`/`resume`/`compare`
até o operador indicar prontidão, a instância local existir e o alvo passar nos
preflights. A sombra anterior está em outra máquina e fica fora do escopo.

## Pins e caminho de carga

O lock normal `dependency-lock.json` fixa **JAR 12.8.2.jre11 e DLL
12.8.2.x64**. Ele continua próprio do pacote offline normal. O lock separado
`dependency-lock.shadow-local-12.8.1.json` fixa **JAR 12.8.1.jre11 e DLL
12.8.1.x64**; seu SHA-256 é
`a62b4009dbd44769502a886d3bf7e31c0354ee037c569c4a3996ffb388c6f2b2`.
A DLL Microsoft no cache/ZIP é somente um artefato passivo até execução física.

`Invoke-Qualification.ps1` verifica manifesto e par físico antes de Java. O
classpath do supervisor usa `etl-dataexport-v2.jar` + `lib/*`; o supervisor
abre `QualificationSqlEvidence.master` por `DriverManager` antes de gerar o
worker. O worker recebe o mesmo classpath e `java.library.path=native`, e abre
`ColetaTemporalLaboratorySession` no banco exato. `inspect` e `plan` rodam
sem JDBC; `config-validate` e `dry-run` usam `Main` com deny-all. O launcher
recusa o pacote normal 12.8.2 no caminho físico antes de iniciar Java.

Após a decisão de URL de processo, a correção PMD e a verificação v79 da
fixture, dois builds `PackageShadow` A/B offline produziram ZIPs candidatos idênticos em
`target/macrobloco-qualificacao-pacote-20260928-01/`. Os candidatos anteriores
do checkpoint 0304 permanecem históricos, sem aceite físico:

| Recibo | Valor A = B |
| --- | --- |
| Build | `p08-shadow-url-v79-build-a` / `p08-shadow-url-v79-build-b` |
| Pacote | `p08-shadow-url-v79-candidate-a` / `p08-shadow-url-v79-candidate-b` |
| Revisão | `be9e54929e4d510d4803271c64f9a3850e9c894ca1bb24725d05c96ea2a0ec2d` |
| Manifesto | `007a779729ec36f3bfed3c85e41f7159d704982d9ffb4cd885cabdcbf5048d8f` |
| ZIP | `4e707a9c146a4deca7e5bb75ca3e34b03e1633d2f2321e7b717225530addbc8e` |
| Conteúdo | 186 membros, nove dependências, 2.158 inputs |

Os comandos puros `config-validate`, `dry-run`, `inspect`, `plan` passaram em
ambas as extrações, JDK 17, heap 512 MiB, limite de 30 s por comando. Nenhuma
conexão ou DLL foi executada. Resultado do empacotador:
`PACKAGED_NOT_SMOKE_QUALIFIED`.
O pacote normal 12.8.2 reconstruído na mesma fonte tem 188 membros e lock
global intacto; seus dois comandos `Main` puros passaram. No candidato físico,
quatro guardas de pacote recusaram antes de Java e dois `run` com URL ausente ou
remota saíram 2 antes de controle/SQL. O envelope passou 25/25 guardas.

## Preflight preparado, sem execução

1. Após o operador liberar UAC, reconciliar processo/serviço/log da tentativa
   anterior e seguir o
   [runbook da instância](reconstrucao-shadow-local-20260928.md). Parar se
   instância, serviço ou banco existir em estado inesperado; não repetir
   instalação/criação às cegas.
2. Com autenticação Windows, consultar **somente `localhost`, `master`** via
   `sqlcmd -S localhost -E -C -d master -b -l 5 -t 10`. Confirmar
   `DB_NAME()=master`, `SERVERPROPERTY('MachineName')=LUCAS`, instância
   `MSSQLSERVER`, sem cluster/HA e nome/estado exatos de
   `ETL_SISTEMA_V2_SHADOW`. Conferir que não há listener remoto. Se o banco
   já existir ou o resultado for incerto, parar para inventário; não fazer
   `CREATE`, `DROP`, `clean`, `restore` ou conexão a outra máquina.
3. A criação exclusiva do alvo, V001–V104 versionadas, `flyway:validate`,
   inventário de schema e validadores sintéticos com rollback seguem a ordem
   e recuperação do runbook da instância. Antes e depois da IT Maven/P08,
   registrar apenas contagens agregadas das tabelas de auditoria do banco
   exato; nenhuma linha de domínio/segredo. Drift ou delta encerra a rodada.
4. Revalidar os dois ZIPs pelo hash externo, extrair em diretórios separados e
   curtos, validar envelope/manifesto, lock, JAR/DLL 12.8.1 e classpath.
   Reservar dois controles novos fora dos payloads; preservar qualquer controle
   parcial. Usar JDK 17, PowerShell 7.5+, heap 512 MiB e teto/timeout do
   contrato da campanha. Comparar A/B apenas depois de readback e rollback.

Comandos **preparados, não executados** para cada extração, após as condições
acima e com `V2_SHADOW_JDBC_URL` definida e validada no processo:

```powershell
$pin = (Get-Content .\package.sha256 -Raw).Trim()
$campaign = Join-Path $PWD.Path 'config/campaign.synthetic.json'
$control = Join-Path (Split-Path $PWD.Path -Parent) 'controle-novo-a-ou-b'
if ([string]::IsNullOrWhiteSpace($env:V2_SHADOW_JDBC_URL)) { throw 'QUAL_SHADOW_URL_REQUIRED' }
& .\Invoke-Qualification.ps1 -Command inspect -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command plan -ManifestSha256 $pin -Campaign $campaign
& .\Invoke-Qualification.ps1 -Command run -ManifestSha256 $pin -Campaign $campaign -Control $control
& .\Invoke-Qualification.ps1 -Command status -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command resume -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command compare -ManifestSha256 $pin -Control $control
```

## Contrato de processo adotado

O Supervisor escolheu o caminho de adaptação: o laboratório P08 consome
exclusivamente `V2_SHADOW_JDBC_URL` do processo. A validação exige host literal
`localhost`, banco exato, `integratedSecurity=true`, `encrypt=true`, escolha
explícita `trustServerCertificate=true|false`, `loginTimeout` entre 1 e 5 s e
`socketTimeout` entre 2.000 ms e o teto da configuração sintética; recusa
porta/instância, usuário/senha/domínio, opções extras/duplicadas e ausência da
variável. O valor não entra no documento de configuração, manifesto, log ou
linha de comando. O supervisor passa ao filho apenas a URL validada entre as
variáveis `V2_*`. Não há fallback para a URL estática anterior. Definir a
escolha de certificado somente após preflight real da instância; uma falha de
TLS interrompe a rodada. Os comandos físicos permanecem suspensos enquanto
UAC, banco local e qualificação material não existirem.
