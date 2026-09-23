# Comandos do pacote de qualificação local

Execute em PowerShell7.5, Windows/x64, Java17. O pacote usa somente SQL Server
localhost/ETL_SISTEMA_V2_SHADOW, autenticação Windows existente e dados sintéticos
com rollback. Cada execução recebe controle novo e limitado. Extração não aplica
migrations; o laboratório exige a fundação V001–V098 já qualificada.

## Extrair e verificar

Partindo da raiz do V2, os comandos abaixo usam o extrator local validado e um
diretório novo dentro da rodada. Os dois hashes são os efetivamente entregues.
O helper de extração pertence à ferramenta de montagem; depois de entrar no
payload, JAR, bibliotecas, configuração, contratos e oráculos vêm do pacote.

```powershell
$round = Join-Path $PWD.Path 'target/macrobloco-qualificacao-pacote-20260913-01'
$zip = Join-Path $round 'qualification-final-01/qualification.zip'
$destination = Join-Path $round 'operador-exemplo-01/pacote extraído'
$zipPin = '54889e1d2153881856a0e68be17b8e770904351d77be10e7b960c56b98aa92d9'
$pin = 'b008ff7bcc5b4e4468db6de01a8bb2e7e9a0395363a4c6c8803e2e9d560e3b2f'
Import-Module .\scripts\validation\QualificationPackage.psm1
Expand-QualificationPackage -Archive $zip -ArchiveSha256 $zipPin -Destination $destination -AllowedRoot $round
Test-QualificationPackage -Directory $destination -ManifestSha256 $pin
Set-Location -LiteralPath $destination
```

Não reutilize destino existente. O caminho absoluto da DLL nativa deve ter até
240caracteres; caminhos maiores são recusados antes de JDBC. Hash atesta
integridade, sem assinatura ou aprovação de release nominal.

## Inspecionar, planejar e executar o exemplo empacotado

```powershell
$campaign = Join-Path $PWD.Path 'config/campaign.synthetic.json'
$control = Join-Path (Split-Path $PWD.Path -Parent) 'controle-exemplo-01'
& .\Invoke-Qualification.ps1 -Command inspect -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command plan -ManifestSha256 $pin -Campaign $campaign
& .\Invoke-Qualification.ps1 -Command run -ManifestSha256 $pin -Campaign $campaign -Control $control
& .\Invoke-Qualification.ps1 -Command status -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command resume -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command compare -ManifestSha256 $pin -Control $control
```

Esse exemplo contém duas raízes sintéticas e as19saídas. `run` exige controle
novo; `status`, `resume` e `compare` conferem o journal existente. `resume` não
repete automaticamente caso sem recibo terminal. A prova final executa exatamente
esses seis comandos, inclusive o JSON empacotado, em `smokes-final-01-01`.

## Campanhas e configuração externa

Copie o JSON de exemplo para um caminho explícito fora do payload e preserve
seus pins. As ações fechadas são SCENARIO, QUERY, REPLAY, RECOMPOSE, ABSENCE,
VARIANTS, TEMPORAL, DEGRADATION e CONCURRENCY. `dependsOn`, `wave`, tick, janela,
blackout, deadline e catch-up são dados tipados; não aceitam texto executável.
REPLAY exige modo REPLAY; RECOMPOSE usa BACKFILL. Uma nova campanha usa outro
controle. Nunca acrescente arquivos ao payload imutável.

`-Configuration` pode indicar uma cópia externa da configuração sintética
empacotada. Host/banco fixos, schema98, duas travas, syntheticOnly e rollbackOnly
devem permanecer válidos. Campos desconhecidos, ausentes, duplicados e limites
incoerentes são recusados antes de JDBC. Não há campo de credencial.

Os estados PASS_LOCAL, FAILED, BLOCKED_DEPENDENCY, CANCELLED e OUTCOME_UNKNOWN
descrevem o dado/caso. Uma recusa esperada pode passar como contraprova e manter
o dado bloqueado. O relatório por escopo identifica o dependente recusado e o
ramo independente aprovado.

## Conferir a entrega sem executar SQL

Na raiz do V2:

```powershell
& .\scripts\validation\Test-QualificationLaboratory.ps1 -SelfTest -IncludePrivateEvidence
& .\scripts\validation\Test-AnalyticLaboratory.ps1 -SelfTest -IncludePrivateEvidence
& .\scripts\validation\Test-ContinuidadeAgentes.ps1 -IncludePrivateEvidence
```

Resultados, escalas, limites e gates externos estão no [relatório](RELATORIO.md).
O pacote não cria serviço, scheduler, schema, release publicado ou aceite real.
