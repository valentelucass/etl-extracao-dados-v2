# Comandos da revisão entregue

PowerShell7.5, Windows/x64 e Java17. Os runners desta rodada derivam dos já existentes, com diretório exclusivo e mesmos limites/travas. As versões executadas ficam vinculadas no selo. Não repetir autores one-shot nem tentativas existentes; resultados desconhecidos exigem reconciliação antes de qualquer novo efeito.

## Verificação somente de arquivos

```powershell
& .\scripts\validation\Test-ConstructionClosure.ps1 -SelfTest -IncludePrivateEvidence
& .\scripts\validation\Test-QualificationLaboratory.ps1 -SelfTest -IncludePrivateEvidence
& .\scripts\validation\Test-AnalyticLaboratory.ps1 -SelfTest -IncludePrivateEvidence
& .\scripts\validation\Test-ContinuidadeAgentes.ps1 -IncludePrivateEvidence
```

## Extrair em diretório novo e conferir pins

```powershell
$round = Join-Path $PWD.Path 'target/macrobloco-fechamento-construcao-20260913-01'
$destination = Join-Path $round 'operador-exemplo-01/pacote extraído'
$zipPin = 'b1ae469e21a08937592496a72a252173af6a7d98e935b4c79c63135328364403'
$pin = '8aa50f6704007da5b3c1264d6a4269a89918a89567b7e71cec50990a25478c52'
Import-Module .\scripts\validation\QualificationPackage.psm1
Expand-QualificationPackage -Archive (Join-Path $round 'qualification-final-01/qualification.zip') -ArchiveSha256 $zipPin -Destination $destination -AllowedRoot $round
Test-QualificationPackage -Directory $destination -ManifestSha256 $pin
Set-Location -LiteralPath $destination
$campaign = Join-Path $PWD.Path 'config/campaign.synthetic.json'
$control = Join-Path (Split-Path $PWD.Path -Parent) 'controle-exemplo-01'
& .\Invoke-Qualification.ps1 -Command inspect -ManifestSha256 $pin
& .\Invoke-Qualification.ps1 -Command plan -ManifestSha256 $pin -Campaign $campaign
```

## Exemplo sintético com efeito SQL local limitado

O schema V001–V098 já deve existir e estar qualificado. Extração não instala migration. Somente localhost/ETL_SISTEMA_V2_SHADOW, Windows integrado, duas travas e rollback. As condições de uma nova execução precisam permanecer vigentes; o exemplo não autoriza fonte real ou produção.

```powershell
& .\Invoke-Qualification.ps1 -Command run -ManifestSha256 $pin -Campaign $campaign -Control $control
& .\Invoke-Qualification.ps1 -Command status -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command resume -ManifestSha256 $pin -Control $control
& .\Invoke-Qualification.ps1 -Command compare -ManifestSha256 $pin -Control $control
```

Run exige controle novo. Resume conserva o nonce e nunca repete automaticamente tentativa reservada; owner vivo/reserva insuficiente mantém admissão fechada, selo íntegro permite concluir eventos faltantes. Pins pertencem à própria revisão: não migrar journals antigos alterando hashes. Status/compare conferem recibos de dados revertidos; não prometem persistência de domínio.

Execução realizada nesta rodada: verify-final-01; regression-final-01; qualification-final-01/reproducible-build-01/qualification-repro-01/artifact-final-01; os5smokes do resumo; resume-package-02; extracted-guards-01; control-guards-01. Comandos exatos, reservas, limites, logs e exits estão nesses diretórios e em artifacts-01. Os gates externos estão no [plano](ENTRADAS-E-EFEITOS-EXTERNOS.md).
