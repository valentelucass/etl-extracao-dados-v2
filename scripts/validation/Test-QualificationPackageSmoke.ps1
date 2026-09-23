#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$PackageAttempt,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
    [ValidateRange(2,32)][int]$Roots=2,
    [switch]$UsePackagedCampaign,
    [ValidateSet('SCENARIO','REPLAY','RECOMPOSE','ABSENCE','VARIANTS','TEMPORAL','ADMISSION','WAVES','CONCURRENCY','RASTER_INCOMPLETE','MANIFEST_FLEET_MISSING','FINANCIAL_REFERENCE_MISSING','COLLECTION_SNAPSHOT_INVALID','BEFORE_SQL','DURING_CAPTURE','AFTER_PREPARATION','BEFORE_RECEIPT')][string]$Case='SCENARIO')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
[Console]::OutputEncoding=[Text.UTF8Encoding]::new($false,$true)
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root 'target/macrobloco-qualificacao-pacote-20260913-01'
$attemptRoot=Join-Path $round $Attempt
if(Test-Path -LiteralPath $attemptRoot){throw 'QUAL_SMOKE_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($attemptRoot)
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Save([string]$name,$value){[IO.File]::WriteAllText((Join-Path $attemptRoot $name),($value|ConvertTo-Json -Depth 24),$utf8)}
Save 'reservation.json' ([ordered]@{state='RESERVED_OUTCOME_UNKNOWN';package=$PackageAttempt;case=$Case;roots=$Roots;
    target='localhost/ETL_SISTEMA_V2_SHADOW';budgetSeconds=480;domainCommit=$false;ddl=$false;
    recovery='observe only owned launcher/controller/child, reconcile aggregate SQL and journal before any new attempt'})
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
$packageRoot=Join-Path $round $PackageAttempt
$packaged=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes((Join-Path $packageRoot 'result.json')))
$destination=Join-Path $attemptRoot 'pacote extraído'
$null=Expand-QualificationPackage -Archive (Join-Path $packageRoot 'qualification.zip') -ArchiveSha256 $packaged.archiveSha256 -Destination $destination -AllowedRoot $round
$manifest=Test-QualificationPackage -Directory $destination -ManifestSha256 $packaged.manifestSha256
function Hash([string]$name){return @($manifest.files|Where-Object path -CEQ $name)[0].sha256}
$mode=switch($Case){'REPLAY'{'REPLAY'};'RECOMPOSE'{'BACKFILL'};default{'BOOTSTRAP'}}
$barrier=if($Case -in @('BEFORE_SQL','DURING_CAPTURE','AFTER_PREPARATION','BEFORE_RECEIPT')){$Case}else{'NONE'}
$fault=if($Case -in @('RASTER_INCOMPLETE','MANIFEST_FLEET_MISSING','FINANCIAL_REFERENCE_MISSING','COLLECTION_SNAPSHOT_INVALID')){$Case}else{'NONE'}
$action=if($barrier -ne 'NONE'){'SCENARIO'}elseif($fault -ne 'NONE'){'DEGRADATION'}else{$Case}
$expected=if($fault -ne 'NONE'){'BLOCKED_DEPENDENCY'}elseif($barrier -eq 'NONE'){'PASS_LOCAL'}elseif($barrier -eq 'BEFORE_RECEIPT'){'OUTCOME_UNKNOWN'}else{'CANCELLED'}
$campaign=[ordered]@{version='qualification-campaign-v1';id='package-smoke';pins=[ordered]@{
    revision=$manifest.revision;jar=(Hash 'etl-dataexport-v2.jar');schema=(Hash 'schema/schema-index.json');
    contracts=(Hash 'contracts/query-contracts.synthetic.json');fixture=(Hash 'fixtures/fixture-index.json');oracle=(Hash 'oracles/outputs.synthetic.json')};
    roots=$Roots;pageSize=2;maximumSeconds=420;cases=@([ordered]@{id='smoke';wave='all';dependsOn=@();action=$action;
        mode=$mode;fault=$fault;barrier=$barrier;outputs=@(1..19|ForEach-Object {'SQL-{0:00}' -f $_});
        tick='2036-04-02T12:00:00Z';start='2036-04-01';endExclusive='2036-04-02';zone='America/Sao_Paulo';
        lookbackSeconds=0;deadlineSeconds=86400;maximumCatchUp=1;blackouts=@();expected=$expected})}
if($Case -ceq 'WAVES'){
    $seed=$campaign.cases[0]
    function Copy-SmokeCase { $copy=[ordered]@{};foreach($entry in $seed.GetEnumerator()){$copy[$entry.Key]=$entry.Value};return $copy }
    $failed=Copy-SmokeCase;$failed.id='raster';$failed.wave='raster';$failed.action='DEGRADATION'
    $failed.fault='RASTER_INCOMPLETE';$failed.expected='BLOCKED_DEPENDENCY'
    $independent=Copy-SmokeCase;$independent.id='captacao';$independent.wave='captacao'
    $independent.action='QUERY';$independent.outputs=@('SQL-06');$independent.expected='PASS_LOCAL'
    $dependent=Copy-SmokeCase;$dependent.id='after-raster';$dependent.wave='dependent'
    $dependent.action='QUERY';$dependent.outputs=@('SQL-13');$dependent.dependsOn=@('raster')
    $dependent.expected='BLOCKED_DEPENDENCY'
    $campaign.cases=@($failed,$independent,$dependent)
}
if($Case -ceq 'ADMISSION'){
    $seed=$campaign.cases[0];$cases=[Collections.Generic.List[object]]::new()
    foreach($id in @('blackout','expired','not-due')){
        $item=[ordered]@{};foreach($entry in $seed.GetEnumerator()){$item[$entry.Key]=$entry.Value}
        $item.id=$id;$item.action='SCENARIO';$item.expected='BLOCKED_DEPENDENCY'
        if($id -ceq 'blackout'){$item.blackouts=@('2036-04-01')}
        if($id -ceq 'expired'){$item.deadlineSeconds=60}
        if($id -ceq 'not-due'){$item.tick='2036-04-01T12:00:00Z'}
        $cases.Add($item)
    }
    $campaign.cases=$cases.ToArray()
}
$campaignPath=Join-Path $attemptRoot 'campaign.json'
if($UsePackagedCampaign){
    if($Case -cne 'SCENARIO' -or $Roots -ne 2){throw 'QUAL_PACKAGED_EXAMPLE_SCOPE'}
    $campaignPath=Join-Path $destination 'config/campaign.synthetic.json'
    $campaign=Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($campaignPath))
}
Save 'campaign.json' $campaign
$control=Join-Path $attemptRoot 'control'
$pwsh=(Get-Command pwsh.exe -CommandType Application).Source
$results=[Collections.Generic.List[object]]::new()
foreach($command in @('inspect','plan','run','status','resume','compare')){
    $arguments=@('-NoProfile','-File',(Join-Path $destination 'Invoke-Qualification.ps1'),'-Command',$command,'-ManifestSha256',$packaged.manifestSha256)
    if($command -in @('plan','run')){$arguments+=@('-Campaign',$campaignPath)}
    if($command -in @('run','status','resume','compare')){$arguments+=@('-Control',$control)}
    Save ($command+'-reservation.json') ([ordered]@{state='RESERVED';command=$command;cwd=$destination;maximumSeconds=480})
    $info=[Diagnostics.ProcessStartInfo]::new($pwsh);$info.WorkingDirectory=$destination
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    $info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
    foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::Start($info)
    try{
        Save ($command+'-process.json') ([ordered]@{pid=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('O');command=$command;cwd=$destination})
        $outPath=Join-Path $attemptRoot ($command+'-stdout.log');$errPath=Join-Path $attemptRoot ($command+'-stderr.log')
        $outFile=[IO.File]::Open($outPath,[IO.FileMode]::CreateNew);$errFile=[IO.File]::Open($errPath,[IO.FileMode]::CreateNew)
        $stdout=$process.StandardOutput.BaseStream.CopyToAsync($outFile);$stderr=$process.StandardError.BaseStream.CopyToAsync($errFile)
        if(-not $process.WaitForExit(480000)){$process.Kill($true);$process.WaitForExit();$code=124}else{$code=$process.ExitCode}
        try{$null=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()}finally{$outFile.Dispose();$errFile.Dispose()}
        Save ($command+'-exit.json') ([ordered]@{exit=$code;stdoutBytes=(Get-Item -LiteralPath $outPath).Length;stderrBytes=(Get-Item -LiteralPath $errPath).Length})
        $out=[IO.File]::ReadAllText($outPath,$utf8);$err=[IO.File]::ReadAllText($errPath,$utf8)
        if($out.Length -gt 1048576 -or $err.Length -gt 1048576 -or $out.Contains([char]0xfffd) -or $err.Contains([char]0xfffd)){throw 'QUAL_SMOKE_LOG_INTEGRITY'}
        $results.Add([ordered]@{command=$command;exit=$code;cwd=$destination;sourceWorkspace=$false})
        Save 'result.json' ([ordered]@{state=if($code -eq 0){'IN_PROGRESS'}else{'FAILED'};commands=$results.ToArray();case=$Case;roots=$Roots})
        if($code -ne 0){throw ('QUAL_SMOKE_COMMAND_FAILED_'+$command)}
    }finally{$process.Dispose()}
}
if($Case -ceq 'ADMISSION'){
    foreach($id in @('blackout','expired','not-due')){
        $caseRoot=Join-Path $control ('case-'+$id)
        if(-not (Test-Path -LiteralPath (Join-Path $caseRoot 'intent.json'))){throw 'QUAL_ADMISSION_INTENT_MISSING'}
        if(Test-Path -LiteralPath (Join-Path $caseRoot 'process.json')){throw 'QUAL_ADMISSION_CREATED_CHILD'}
        if(Test-Path -LiteralPath (Join-Path $caseRoot 'receipt.json')){throw 'QUAL_ADMISSION_CREATED_RECEIPT'}
    }
}
Save 'result.json' ([ordered]@{state='PASS_LOCAL';package=$PackageAttempt;commands=$results.ToArray();case=$Case;roots=$Roots;
    sourceWorkspace=$false;bundledCampaign=[bool]$UsePackagedCampaign;manifestSha256=$packaged.manifestSha256;archiveSha256=$packaged.archiveSha256})
Get-Content -LiteralPath (Join-Path $attemptRoot 'result.json') -Raw
