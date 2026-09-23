#Requires -Version 7.5
param([string]$InputPath,[switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$required=@('OPEN','TO_DONE','TO_FINISHED','CANCELLATION','NATIVE_ABSENT','NATIVE_NULL','NATIVE_INVALID',
    'OFFSET_EQUIVALENT','SUBMILLISECOND','TEMPORAL_TIE','REVERSE_ORDER','ROOT_EXPANSION','DUPLICATE',
    'REFERENCE_ABSENT','REFERENCE_NULL','REFERENCE_INVALID')
function ExactInstant($value){
    if($null -eq $value){return $null}
    $match=[regex]::Match([string]$value,'^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d{1,9}))?(Z|[+-]\d{2}:\d{2})$')
    if(-not $match.Success){return $null}
    try{
        $offset=if($match.Groups[3].Value -ceq 'Z'){'+00:00'}else{$match.Groups[3].Value}
        $second=[DateTimeOffset]::ParseExact($match.Groups[1].Value+$offset,"yyyy-MM-dd'T'HH:mm:sszzz",[Globalization.CultureInfo]::InvariantCulture)
        return "$($second.ToUnixTimeSeconds()):$($match.Groups[2].Value.PadRight(9,'0'))"
    }catch{return $null}
}
function LoadArtifact($artifact,[string]$base){
    if($artifact.path -notmatch '^[A-Za-z0-9_./-]+$' -or $artifact.path.Contains('..') -or [IO.Path]::IsPathRooted($artifact.path)){throw 'INPUT_ARTIFACT_PATH'}
    $path=[IO.Path]::GetFullPath((Join-Path $base $artifact.path))
    if(-not (Test-Path -LiteralPath $path -PathType Leaf)){throw 'INPUT_ARTIFACT_MISSING'}
    $node=Get-Item -LiteralPath $path
    while($node.FullName -cne $base){
        if(($node.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0){throw 'INPUT_ARTIFACT_REPARSE'}
        $node=if($node -is [IO.DirectoryInfo]){$node.Parent}else{$node.Directory}
        if($null -eq $node){throw 'INPUT_ARTIFACT_PATH'}
    }
    if((Get-Item -LiteralPath $path).Length -gt 1048576){throw 'INPUT_ARTIFACT_TOO_LARGE'}
    if($artifact.sha256 -cnotmatch '^[a-f0-9]{64}$' -or (Get-FileHash -LiteralPath $path).Hash.ToLowerInvariant() -cne $artifact.sha256){throw 'INPUT_ARTIFACT_HASH'}
    return Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -Depth 30 -DateKind String
}
function Validate($package,[string]$base){
    if($package.version -ne 1 -or $package.entity -cne 'coletas' -or $package.mode -cnotin @('SYNTHETIC','REPRESENTATIVE')){throw 'INPUT_CONTRACT'}
    if($package.source -cnotmatch '^[A-Za-z0-9][-A-Za-z0-9._]{0,127}$' -or $package.tenant -cnotmatch '^[A-Za-z0-9][-A-Za-z0-9._]{0,127}$' -or
        $package.tenant.ToUpperInvariant() -cin @('GLOBAL','DEFAULT','SINGLETON')){throw 'INPUT_SCOPED_IDENTITY'}
    if($package.oracle.originKind -cnotin @('SYNTHETIC_INDEPENDENT_EVENTS','INDEPENDENT_EVENT_LOG') -or
        [string]::IsNullOrWhiteSpace($package.oracle.owner) -or
        [string]::IsNullOrWhiteSpace($package.oracle.originReference)){throw 'INPUT_INDEPENDENT_ORACLE_REQUIRED'}
    if($package.mode -ceq 'REPRESENTATIVE' -and ($package.oracle.originKind -ceq 'SYNTHETIC_INDEPENDENT_EVENTS' -or
        -not $package.securityAttestationValidated)){throw 'INPUT_REAL_SECURITY_OR_ORACLE_MISSING'}
    $oracle=LoadArtifact $package.oracle.artifact $base
    if($oracle.originReference -cne $package.oracle.originReference){throw 'INPUT_ORACLE_ORIGIN_BINDING'}
    $captures=@{}
    foreach($capture in $package.captures){
        if($captures.ContainsKey($capture.captureId)){throw 'INPUT_DUPLICATE_CAPTURE'}
        if($capture.source -cne $package.source -or $capture.tenant -cne $package.tenant -or $capture.queryDate -cne $package.queryDate){throw 'INPUT_CAPTURE_SCOPE_OR_WINDOW'}
        if($capture.kind -cnotin @('DATA_EXPORT_6908','GRAPHQL_TEMPORAL') -or -not $capture.terminal -or
            [string]::IsNullOrWhiteSpace($capture.executionId) -or [string]::IsNullOrWhiteSpace($capture.contractVersion) -or
            $capture.contractFingerprint -cnotmatch '^[a-f0-9]{64}$'){throw 'INPUT_CAPTURE_CONTRACT_OR_COMPLETENESS'}
        if($capture.artifact.sha256 -ceq $package.oracle.artifact.sha256 -or $capture.artifact.path -ceq $package.oracle.artifact.path){throw 'INPUT_ORACLE_IS_CAPTURE'}
        $document=LoadArtifact $capture.artifact $base
        if($document.captureId -cne $capture.captureId -or $document.executionId -cne $capture.executionId -or
            $document.contractVersion -cne $capture.contractVersion -or $document.contractFingerprint -cne $capture.contractFingerprint){throw 'INPUT_CAPTURE_ARTIFACT_BINDING'}
        $captures.Add($capture.captureId,@{metadata=$capture;document=$document})
    }
    $coverage=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($binding in $package.bindings){
        if(-not $seen.Add($binding.caseId)){throw 'INPUT_DUPLICATE_CASE'}
        if(-not $captures.ContainsKey($binding.dataCapture) -or -not $captures.ContainsKey($binding.referenceCapture)){throw 'INPUT_CAPTURE_LINK_MISSING'}
        $data=$captures[$binding.dataCapture];$reference=$captures[$binding.referenceCapture]
        if($data.metadata.kind -cne 'DATA_EXPORT_6908' -or $reference.metadata.kind -cne 'GRAPHQL_TEMPORAL' -or
            $binding.dataKey -cnotmatch '^INTEGER:[0-9]+$' -or $binding.referenceKey -cnotmatch '^(INTEGER|STRING):.+$'){throw 'INPUT_TYPED_IDENTITY'}
        $dataRows=@($data.document.rows|Where-Object {$_.key -ceq $binding.dataKey})
        $referenceRows=@($reference.document.rows|Where-Object {$_.key -ceq $binding.referenceKey})
        $expected=@($oracle.expectations|Where-Object {$_.caseId -ceq $binding.caseId})
        if($dataRows.Count -eq 0 -or $referenceRows.Count -eq 0 -or $expected.Count -ne 1){throw 'INPUT_SCOPED_MATCH_OR_ORACLE_MISSING'}
        $event=$expected[0]
        if($event.source -cne $package.source -or $event.tenant -cne $package.tenant -or $event.queryDate -cne $package.queryDate -or
            $event.dataCapture -cne $binding.dataCapture -or $event.referenceCapture -cne $binding.referenceCapture -or
            $event.dataKey -cne $binding.dataKey -or $event.referenceKey -cne $binding.referenceKey){throw 'INPUT_ORACLE_SCOPED_LINK'}
        $instant=ExactInstant $event.expectedInstant
        if($null -eq $instant -or $event.expectedStatus -cnotin @('pending','treatment','manifested','in_transit','draft','done','finished','canceled','cancelled')){throw 'INPUT_ORACLE_EXPECTATION_INVALID'}
        foreach($row in @($dataRows)+@($referenceRows)){
            if($row.status -cne $event.expectedStatus){throw 'INPUT_EXPECTED_STATUS_DIVERGENCE'}
        }
        foreach($row in $referenceRows){
            $referenceInstant=ExactInstant $row.instant
            $referenceState=switch($row.timePresence){
                'ABSENT' {if($null -ne $row.instant){throw 'INPUT_REFERENCE_PRESENCE'};'ABSENT'}
                'NULL' {if($null -ne $row.instant){throw 'INPUT_REFERENCE_PRESENCE'};'NULL'}
                'VALUE' {if($null -eq $row.instant){throw 'INPUT_REFERENCE_PRESENCE'};if($null -eq $referenceInstant){'INVALID'}else{'VALID'}}
                default {throw 'INPUT_REFERENCE_PRESENCE'}
            }
            if($referenceState -cne $event.expectedReferenceState){throw 'INPUT_EXPECTED_REFERENCE_STATE_DIVERGENCE'}
            if($referenceState -ceq 'VALID' -and $referenceInstant -cne $instant){throw 'INPUT_EXPECTED_INSTANT_DIVERGENCE'}
        }
        foreach($row in $dataRows){if($row.nativePresence -cnotin @('ABSENT','NULL','VALUE') -or ($row.nativePresence -cne 'VALUE' -and $null -ne $row.instant)){throw 'INPUT_NATIVE_PRESENCE'}
            $native=ExactInstant $row.instant
            if($null -ne $native -and $native -cne $instant){throw 'INPUT_NATIVE_TIME_DIVERGENCE'}
        }
        $valid=switch($event.caseKind){
            'OPEN' {$event.expectedStatus -cin @('pending','treatment','manifested','in_transit','draft')}
            'TO_DONE' {$event.fromStatus -ceq 'pending' -and $event.expectedStatus -ceq 'done'}
            'TO_FINISHED' {$event.fromStatus -ceq 'pending' -and $event.expectedStatus -ceq 'finished'}
            'CANCELLATION' {$event.fromStatus -ceq 'pending' -and $event.expectedStatus -cin @('canceled','cancelled')}
            'NATIVE_ABSENT' {$dataRows[0].nativePresence -ceq 'ABSENT'}
            'NATIVE_NULL' {$dataRows[0].nativePresence -ceq 'NULL'}
            'NATIVE_INVALID' {$dataRows[0].nativePresence -ceq 'VALUE' -and $null -eq (ExactInstant $dataRows[0].instant)}
            'REFERENCE_ABSENT' {$event.expectedReferenceState -ceq 'ABSENT'}
            'REFERENCE_NULL' {$event.expectedReferenceState -ceq 'NULL'}
            'REFERENCE_INVALID' {$event.expectedReferenceState -ceq 'INVALID'}
            'OFFSET_EQUIVALENT' {$referenceRows[0].instant -cne $event.expectedInstant -and (ExactInstant $referenceRows[0].instant) -ceq $instant}
            'SUBMILLISECOND' {$instant.Split(':')[-1].Substring(3) -cne '000000'}
            'TEMPORAL_TIE' {(ExactInstant $event.beforeInstant) -ceq $instant -and $event.fromStatus -cne $event.expectedStatus}
            'REVERSE_ORDER' {
                $previous=ExactInstant $event.beforeInstant
                if($null -eq $previous){$false}else{
                    $beforeParts=$previous.Split(':');$afterParts=$instant.Split(':')
                    [long]$beforeParts[0] -gt [long]$afterParts[0] -or ([long]$beforeParts[0] -eq [long]$afterParts[0] -and [int]$beforeParts[1] -gt [int]$afterParts[1])
                }
            }
            'ROOT_EXPANSION' {$dataRows.Count -gt 1}
            'DUPLICATE' {$referenceRows.Count -gt 1}
            default {$false}
        }
        if(-not $valid){throw 'INPUT_CASE_COVERAGE_NOT_DEMONSTRATED'}
        $null=$coverage.Add($event.caseKind)
    }
    $missing=@($required|Where-Object {-not $coverage.Contains($_)})
    if($missing.Count -gt 0){throw ('INPUT_COVERAGE_MISSING:'+($missing -join ','))}
    return @{passed=$true;mode=$package.mode;validatedCases=$seen.Count;coverage=@($coverage|Sort-Object);
        status=$(if($package.mode -ceq 'SYNTHETIC'){'SYNTHETIC_INPUT_MECHANISM_VERIFIED'}else{'INPUTS_VALID_REQUIRE_NOMINAL_REVIEW'});
        realQualificationAccepted=$false;operationalPromotionAuthorized=$false;remoteCalls=0}
}
if([string]::IsNullOrWhiteSpace($InputPath)){
    if(-not $SelfTest){@{passed=$false;status='EXTERNAL_INPUT_MISSING';missing=@('independent event oracle linked to captures','qualified scoped correspondence','representative window and transitions','nominal acceptance and V2-041');remoteCalls=0}|ConvertTo-Json;exit 2}
    $InputPath=Join-Path $root 'src/test/resources/contracts/coletas-temporal-integration/representative/package.json'
}
$path=(Resolve-Path -LiteralPath $InputPath).Path
$base=Split-Path -Parent $path
$package=Get-Content -LiteralPath $path -Raw|ConvertFrom-Json -Depth 40 -DateKind String
$result=Validate $package $base
$guards=0
if($SelfTest){
    foreach($mutation in @(
        {param($p)$p.tenant='GLOBAL'},
        {param($p)$p.oracle.owner=''},
        {param($p)$p.oracle.originKind='GRAPHQL'},
        {param($p)$p.mode='REPRESENTATIVE'},
        {param($p)$p.oracle.artifact.sha256='0'*64},
        {param($p)$p.captures[0].tenant='ANOTHER_SYNTHETIC'},
        {param($p)$p.captures[0].queryDate='2036-01-19'},
        {param($p)$p.captures[0].contractVersion='wrong'},
        {param($p)$p.captures[0].terminal=$false},
        {param($p)$p.bindings[0].referenceKey='STRING:unmatched'},
        {param($p)$p.bindings[0].dataCapture='unbound'},
        {param($p)$p.bindings[0].caseId='unbound'},
        {param($p)$p.bindings=@($p.bindings|Select-Object -Skip 1)}
    )){
        $copy=$package|ConvertTo-Json -Depth 40|ConvertFrom-Json -Depth 40 -DateKind String
        &$mutation $copy
        $rejected=$false
        try{$null=Validate $copy $base}catch{$rejected=$_.Exception.Message.StartsWith('INPUT_')}
        if(-not $rejected){throw 'INPUT_GUARD_ACCEPTED_MUTATION'}
        $guards++
    }
    if((ExactInstant '2036-01-20T10:00:00.123456789Z') -ceq (ExactInstant '2036-01-20T10:00:00.123456790Z')){throw 'INPUT_NANO_PRECISION_LOST'}
    if((ExactInstant '2036-01-20T10:00:00.123456789Z') -cne (ExactInstant '2036-01-20T07:00:00.123456789-03:00')){throw 'INPUT_OFFSET_NORMALIZATION'}
    $guards+=2
}
$result.guards=$guards
$result|ConvertTo-Json -Depth 8
