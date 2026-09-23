#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$private=Join-Path $root 'target/bloco56-continuacao'
$before=@{}
foreach($name in @('info','data','schema-root','schema-types','schema-relations')){
    $path=Join-Path $private ($name+'-ledger.jsonl')
    if(-not(Test-Path -LiteralPath $path)){throw 'B56_PROBE_GUARD_NEEDS_EXECUTED_LEDGER'}
    $before[$path]=(Get-FileHash -LiteralPath $path).Hash
}
$cases=@(
    @{script='Invoke-Bloco56IdentityMetadataProbe.ps1';args=@();reason='B56_SINGLE_USE_ALREADY_RESERVED_NO_REPEAT'},
    @{script='Invoke-Bloco56IdentityDataProbe.ps1';args=@();reason='B56_DATA_SINGLE_USE_NO_REPEAT'},
    @{script='Invoke-Bloco56SchemaProbe.ps1';args=@('-Stage','root');reason='B56_SCHEMA_SINGLE_USE_NO_REPEAT'},
    @{script='Invoke-Bloco56SchemaProbe.ps1';args=@('-Stage','types');reason='B56_SCHEMA_SINGLE_USE_NO_REPEAT'},
    @{script='Invoke-Bloco56SchemaProbe.ps1';args=@('-Stage','relations');reason='B56_SCHEMA_SINGLE_USE_NO_REPEAT'}
)
foreach($case in $cases){
    $output=@(& pwsh -NoProfile -File (Join-Path $root ('scripts/probes/'+$case.script)) @($case.args) 2>&1)
    if($LASTEXITCODE -eq 0 -or -not (($output -join "`n").Contains($case.reason))){throw 'B56_PROBE_REPLAY_NOT_REJECTED'}
}
foreach($name in @('Invoke-Bloco56IdentityMetadataProbe.ps1','Invoke-Bloco56IdentityDataProbe.ps1','Invoke-Bloco56SchemaProbe.ps1')){
    & pwsh -NoProfile -File (Join-Path $root ('scripts/probes/'+$name)) -SelfTest
    if($LASTEXITCODE -ne 0){throw 'B56_PROBE_OFFLINE_TEST_FAILED'}
}
foreach($path in $before.Keys){if((Get-FileHash -LiteralPath $path).Hash -cne $before[$path]){throw 'B56_PROBE_LEDGER_CHANGED_DURING_OFFLINE_TEST'}}
'B56_PROBE_GUARDS_PASS_FIVE_REPLAYS_REJECTED_THREE_OFFLINE_SUITES_ZERO_NEW_REQUESTS'
