#Requires -Version 7.5
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$utf8=[Text.UTF8Encoding]::new($false,$true)
$successor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/raster-contrato-local/manifesto.json')){
    $successor=& (Join-Path $PSScriptRoot 'Test-RasterLocalContinuation.ps1') -AsMap
}
$manifest='docs/catalogos/bloco56-continuacao/manifesto.json'
$m=Get-Content -LiteralPath (Join-Path $root $manifest) -Raw|ConvertFrom-Json
$prior=Get-Content -LiteralPath (Join-Path $root $m.predecessor.path) -Raw|ConvertFrom-Json
$fixture=Join-Path $root ('target/bloco56-continuacao/guards/'+[guid]::NewGuid().ToString('N'))
$paths=@($prior.changedExistingFiles.path)+@($prior.preservedFiles.path)+@($prior.newFiles.path)+@($m.changedExistingFiles.path)+@($m.newFiles.path)+@($manifest,$m.predecessor.path)
$paths=@($paths|Sort-Object -Unique)
foreach($path in $paths){
    if($path -cnotmatch '^[A-Za-z0-9_./-]+$' -or $path.Contains('..')){throw 'B56C_GUARD_PATH'}
    $dest=Join-Path $fixture $path
    [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest))
    $revision=if($null -ne $successor -and $successor.ContainsKey($path)){$successor[$path].snapshot}else{$path}
    [IO.File]::Copy((Join-Path $root $revision),$dest,$false)
}
$validator=Join-Path $fixture 'scripts/validation/Test-Bloco56Continuacao.ps1'
& $validator|Out-Null
$results=[Collections.Generic.List[object]]::new()
function Reject([string]$Id,[string]$Path,[scriptblock]$Mutate,[string]$Expected,[switch]$RefreshHash){
    $file=Join-Path $fixture $Path;$mf=Join-Path $fixture $manifest
    $bytes=[IO.File]::ReadAllBytes($file);$manifestBytes=[IO.File]::ReadAllBytes($mf)
    try{
        $before=$utf8.GetString($bytes);$after=& $Mutate $before
        if($before -ceq $after){throw 'B56C_GUARD_NO_CHANGE'}
        [IO.File]::WriteAllText($file,$after,$utf8)
        if($RefreshHash){
            $mm=$utf8.GetString($manifestBytes)|ConvertFrom-Json
            $entry=@($mm.newFiles|Where-Object path -CEQ $Path)
            if($entry.Count -ne 1){throw 'B56C_GUARD_ENTRY'}
            $entry[0].sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()
            [IO.File]::WriteAllText($mf,($mm|ConvertTo-Json -Depth 20),$utf8)
        }
        $reason='NOT_REJECTED';try{& $validator|Out-Null}catch{$reason=$_.Exception.Message}
        if($reason -notmatch $Expected){throw ('B56C_GUARD_FAILED_'+$Id+'_'+$reason)}
        $results.Add([ordered]@{id=$Id;reason=$reason;layer='OFFLINE_ISOLATED_FIXTURE';passed=$true})
    }finally{[IO.File]::WriteAllBytes($file,$bytes);[IO.File]::WriteAllBytes($mf,$manifestBytes)}
}
Reject 'FALSE_PROGRESS' $manifest {param($s)$s.Replace('"done": 65','"done": 66')} 'B56C_PROGRESS'
Reject 'INVENTED_CALL' $manifest {param($s)$s.Replace('"sourceReadCalls": 11','"sourceReadCalls": 12')} 'B56C_EVIDENCE_SCOPE'
Reject 'SQL_FROM_READ_ADOPTION' $manifest {param($s)$s.Replace('"sqlOperations": 0','"sqlOperations": 1')} 'B56C_NO_NEW_ACCEPTANCE_OR_RUNTIME'
Reject 'ROTATION_FROM_ENV_ACCESS' $manifest {param($s)$s.Replace('"rotationProven": false','"rotationProven": true')} 'B56C_UNPROVEN_ACCEPTANCE_OR_EFFECT'
Reject 'RUNTIME_HASH_EXCEPTION' $manifest {param($s)$s.Replace('"path": "STATES.md"','"path": "src/main/java/br/com/esl/etl/v2/bootstrap/RuntimeOperationalRequest.java"')} 'B56C_EXACT_SEVEN_DELTAS'
Reject 'SNAPSHOT_REWRITTEN' 'docs/continuidade/historico/bloco56-preparacao/STATES.md' {param($s)$s+"`nFALSE"} 'B56C_HASH_'
Reject 'MIGRATION_REWRITTEN' 'database/migrations/V023__correct_scoped_runtime_output_projection.sql' {param($s)$s+"`n-- altered"} 'B56C_HASH_'
Reject 'SAMPLE_AS_IDENTITY' 'docs/catalogos/bloco56-continuacao/evidencias/data-10633.json' {param($s)$s.Replace('"identityProven": false','"identityProven": true')} 'B56C_UNPROVEN_ACCEPTANCE_OR_EFFECT' -RefreshHash
Reject 'HTTP_ERROR_AS_SUCCESS' 'docs/catalogos/bloco56-continuacao/evidencias/info-8636.json' {param($s)$s.Replace('"httpStatus": 200','"httpStatus": 401')} 'B56C_HTTP_RESULT' -RefreshHash
& $validator|Out-Null
[IO.File]::WriteAllText((Join-Path $fixture 'results.json'),($results|ConvertTo-Json -Depth 6),$utf8)
if($results.Count -ne 9){throw 'B56C_NINE_GUARDS_REQUIRED'}
'B56_CONTINUATION_NINE_CONTRAPROOFS_PASS'
