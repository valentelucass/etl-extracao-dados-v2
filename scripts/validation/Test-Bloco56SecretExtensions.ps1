#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$fixture=Join-Path $root ('target/bloco56-continuacao/secret-guards/'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($fixture)
& git -C $fixture init --quiet
if($LASTEXITCODE -ne 0){throw 'SECRET_GUARD_LOCAL_FIXTURE_INIT'}
$utf8=[Text.UTF8Encoding]::new($false,$true)
$scanner=Join-Path $root 'scripts/security/Invoke-OfflineSecretScan.ps1'
$results=[Collections.Generic.List[object]]::new()
foreach($extension in @('graphql','jsonl')){
    $file=Join-Path $fixture ('sample.'+$extension)
    $clean=if($extension -eq 'graphql'){'query Example { __schema { queryType { name } } }'}else{'{"state":"OBSERVED","httpStatus":200}'}
    [IO.File]::WriteAllText($file,$clean,$utf8)
    $output=@(& pwsh -NoProfile -File $scanner -Source $fixture)
    if($LASTEXITCODE -ne 0 -or -not ($output -match 'findings=0')){throw 'SECRET_GUARD_TEXT_NOT_SCANNED'}
    # Deliberately synthetic and assembled here; never a provisioned secret.
    $sentinel='Bearer '+('SYNTHETIC'+'-ONLY-123456789')
    [IO.File]::WriteAllText($file,($clean+"`n"+$sentinel),$utf8)
    $output=@(& pwsh -NoProfile -File $scanner -Source $fixture)
    if($LASTEXITCODE -ne 1 -or -not ($output -match 'rule=BEARER_LITERAL') -or ($output -match 'UNSCANNED_NON_TEXT') -or (($output -join "`n").Contains($sentinel))){throw 'SECRET_GUARD_DETECTION_OR_REDACTION'}
    $results.Add([ordered]@{extension=$extension;cleanTextPassed=$true;syntheticSecretRejected=$true;valueNeverPrinted=$true})
    [IO.File]::WriteAllText($file,$clean,$utf8)
}
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json),$utf8)
'B56_SECRET_EXTENSIONS_PASS_TWO_CLEAN_TWO_SECRET_REJECTIONS'
