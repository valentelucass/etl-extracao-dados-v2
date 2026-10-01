#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$BuildAttempt,
    [ValidatePattern('^macrobloco-[a-z0-9-]{1,90}$')][string]$RoundName='macrobloco-qualificacao-pacote-20260913-01')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root ('target/'+$RoundName)
$source=Join-Path $round ($BuildAttempt+'/build')
$attempt=Join-Path $round ('schema-guards-'+[Guid]::NewGuid().ToString('N'))
$build=Join-Path $attempt 'build'
$null=[IO.Directory]::CreateDirectory($attempt)
Copy-Item -LiteralPath $source -Destination $build -Recurse
Copy-Item -LiteralPath (Join-Path $round ($BuildAttempt+'/result.json')) -Destination (Join-Path $attempt 'result.json')
$utf8=[Text.UTF8Encoding]::new($false)
$results=[Collections.Generic.List[object]]::new()
function Check([string]$name,[string]$expected){
    $observed='ACCEPTED'
    try{
        & (Join-Path $PSScriptRoot 'New-QualificationPackage.ps1') -BuildAttempt ([IO.Path]::GetFileName($attempt)) `
            -OutputName ('reject-'+$name) -Candidate -ShadowLocalCandidate -RoundName $RoundName | Out-Null
    }catch{$observed=$_.Exception.Message}
    $results.Add([ordered]@{case=$name;expected=$expected;observed=$observed;passed=$observed -ceq $expected})
    if($observed -cne $expected){throw ('QUAL_SCHEMA_GUARD_FAILED_'+$name+'_GOT_'+$observed)}
}
$migration=Join-Path $build 'database/migrations/V105__align_audit_and_expansion_label_bin2.sql'
$renamed=Join-Path $build 'database/migrations/V106__align_audit_and_expansion_label_bin2.sql'
$baseline=Join-Path $build 'database/baseline/001_schema_foundation_baseline.sql'
$migrationBytes=[IO.File]::ReadAllBytes($migration)
$baselineBytes=[IO.File]::ReadAllBytes($baseline)
$state='FAILED'
try{
    Remove-Item -LiteralPath $migration
    try{Check 'missing-v105' 'QUAL_SCHEMA_COUNT'}finally{[IO.File]::WriteAllBytes($migration,$migrationBytes)}
    Move-Item -LiteralPath $migration -Destination $renamed
    try{Check 'v106-instead-of-v105' 'QUAL_SCHEMA_SEQUENCE'}finally{Move-Item -LiteralPath $renamed -Destination $migration}
    [IO.File]::AppendAllText($migration,"`n",$utf8)
    try{Check 'v105-byte-drift' 'QUAL_SCHEMA_INVENTORY_DRIFT'}finally{[IO.File]::WriteAllBytes($migration,$migrationBytes)}
    [IO.File]::AppendAllText($baseline,"`n",$utf8)
    try{Check 'baseline-byte-drift' 'QUAL_SCHEMA_BASELINE_DRIFT'}finally{[IO.File]::WriteAllBytes($baseline,$baselineBytes)}
    $state='PASS'
}finally{
    [IO.File]::WriteAllText((Join-Path $attempt 'schema-guards-result.json'),
        (@{state=$state;tests=$results;scope='offline mirrored package inputs; no JDBC, SQL, Flyway or network'}|ConvertTo-Json -Depth 8),$utf8)
}
@{state=$state;cases=$results.Count;attempt=$attempt}|ConvertTo-Json
