#Requires -Version 7.5
param([Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$PackageAttempt,
    [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
    [ValidatePattern('^macrobloco-[a-z0-9-]{1,90}$')][string]$RoundName='macrobloco-qualificacao-pacote-20260913-01')
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$utf8=[Text.UTF8Encoding]::new($false,$true)
[Console]::OutputEncoding=$utf8
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$round=Join-Path $root ('target/'+$RoundName)
$evidence=Join-Path $round $Attempt
if(Test-Path -LiteralPath $evidence){throw 'QUAL_GUARDS_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($evidence)
function Json([string]$path,$value){[IO.File]::WriteAllText($path,($value|ConvertTo-Json -Depth 30),$utf8)}
function Read([string]$path){return Get-Content -Raw -LiteralPath $path|ConvertFrom-Json -AsHashtable}
Json (Join-Path $evidence 'reservation.json') ([ordered]@{state='RESERVED';package=$PackageAttempt;
    target=$evidence;maximumCases=24;caseSeconds=120;maximumLogBytes=1048576;domainCommit=$false;ddl=$false;
    scope='actual extracted entrypoint rejects typed inputs/content before child creation and JDBC';
    recovery='retain originals, mutants, raw logs and owned process receipt; never retry same attempt'})
Import-Module (Join-Path $PSScriptRoot 'QualificationPackage.psm1') -Force
$package=Join-Path $round $PackageAttempt
$receipt=Read (Join-Path $package 'result.json')
$archive=Join-Path $package 'qualification.zip'
$cases=@(
    @{name='missing-member';kind='config';key='host';value=$null;code='QUAL_JSON_MEMBERS'},
    @{name='extra-member';kind='config';key='password';value='synthetic-not-a-secret';code='QUAL_JSON_MEMBERS'},
    @{name='forbidden-host';kind='config';key='host';value='forbidden.invalid';code='QUAL_CONFIG_SCOPE'},
    @{name='forbidden-database';kind='config';key='database';value='FORBIDDEN_SYNTHETIC';code='QUAL_CONFIG_SCOPE'},
    @{name='schema-version';kind='config';key='schemaVersion';value=99;code='QUAL_JSON_INTEGER'},
    @{name='profile-opt-in';kind='config';key='profileActive';value=$false;code='QUAL_CONFIG_SCOPE'},
    @{name='enabled-opt-in';kind='config';key='integrationEnabled';value=$false;code='QUAL_CONFIG_SCOPE'},
    @{name='rows-limit';kind='config';key='maximumRows';value=4097;code='QUAL_JSON_INTEGER'},
    @{name='timeout-order';kind='config';key='socketMillis';value=2000;code='QUAL_CONFIG_TIMEOUT_ORDER'},
    @{name='duplicate-member';kind='duplicate';code='QUAL_COMMAND_REFUSED'},
    @{name='truncated-json';kind='truncated';code='QUAL_COMMAND_REFUSED'},
    @{name='invalid-utf8';kind='utf8';code='QUAL_JSON_UTF8'},
    @{name='missing-case';kind='campaign';key='cases';value=@();code='QUAL_JSON_ARRAY'},
    @{name='roots-limit';kind='campaign';key='roots';value=33;code='QUAL_JSON_INTEGER'},
    @{name='revision-pin';kind='pin';code='QUAL_PACKAGE_CAMPAIGN_REVISION'},
    @{name='replay-mode';kind='action';code='QUAL_CAMPAIGN_ACTION_MODE'},
    @{name='oracle-versus-jar';kind='member';member='oracles/outputs.synthetic.json';code='QUAL_PACKAGE_JAR_RESOURCE_HASH'},
    @{name='physical-schema-versus-jar';kind='member';member='contracts/physical-columns.v098.json';code='QUAL_PACKAGE_JAR_RESOURCE_HASH'},
    @{name='physical-schema-v105-versus-jar';kind='member';member='contracts/physical-columns.v105.json';code='QUAL_PACKAGE_JAR_RESOURCE_HASH'},
    @{name='sbom-duplicate';kind='sbom';member='sbom.cdx.json';code='QUAL_SBOM_CONTENT'},
    @{name='dependency-provenance';kind='dependency';member='dependencies.json';code='QUAL_SBOM_LOCK_CORRESPONDENCE'},
    @{name='native-path-limit';kind='native-path';code='QUAL_PACKAGE_NATIVE_PATH_LIMIT'}
)
$results=[Collections.Generic.List[object]]::new()
$pwsh=(Get-Command pwsh.exe -CommandType Application|Select-Object -First 1).Source
foreach($case in $cases){
    $caseRoot=Join-Path $evidence $case.name
    $null=[IO.Directory]::CreateDirectory($caseRoot)
    $payload=Join-Path $caseRoot 'pacote verificado'
    if($case.kind -ceq 'native-path'){
        # Keep the JAR loadable while exceeding the explicit native-member bound of240.
        $padding=250-$caseRoot.Length-2-'native/mssql-jdbc_auth-12.8.2.x64.dll'.Length
        if($padding -lt 1 -or $padding -gt 200){throw 'QUAL_NATIVE_PATH_MUTANT_ROOT'}
        $payload=Join-Path $caseRoot ('n'*$padding)
    }
    $null=Expand-QualificationPackage -Archive $archive -ArchiveSha256 $receipt.archiveSha256 -Destination $payload -AllowedRoot $round
    $manifest=Read (Join-Path $payload 'package.json')
    function Hash([string]$name){return @($manifest.files|Where-Object path -CEQ $name)[0].sha256}
    $campaign=[ordered]@{version='qualification-campaign-v1';id='guard';pins=[ordered]@{
        revision=$manifest.revision;jar=(Hash 'etl-dataexport-v2.jar');schema=(Hash 'schema/schema-index.json');
        contracts=(Hash 'contracts/query-contracts.synthetic.json');fixture=(Hash 'fixtures/fixture-index.json');oracle=(Hash 'oracles/outputs.synthetic.json')};
        roots=2;pageSize=2;maximumSeconds=420;cases=@([ordered]@{id='guard';wave='all';dependsOn=@();
        action='SCENARIO';mode='BOOTSTRAP';fault='NONE';barrier='NONE';outputs=@(1..19|ForEach-Object {'SQL-{0:00}' -f $_});
        tick='2036-04-02T12:00:00Z';start='2036-04-01';endExclusive='2036-04-02';zone='America/Sao_Paulo';
        lookbackSeconds=0;deadlineSeconds=86400;maximumCatchUp=1;blackouts=@();expected='PASS_LOCAL'})}
    $configuration=Read (Join-Path $payload 'config/config.synthetic.json')
    switch($case.kind){
        'config' {if($null -eq $case.value){$null=$configuration.Remove($case.key)}else{$configuration[$case.key]=$case.value}}
        'campaign' {$campaign[$case.key]=$case.value}
        'pin' {$campaign.pins.fixture='0'*64}
        'action' {$campaign.cases[0].action='REPLAY'}
        'member' {[IO.File]::AppendAllText((Join-Path $payload $case.member),"`n ",$utf8)}
        'sbom' {$bom=Read (Join-Path $payload $case.member);$bom.components[8]=$bom.components[0];Json (Join-Path $payload $case.member) $bom}
        'dependency' {$lock=Read (Join-Path $payload $case.member);$lock.dependencies[0].origin='https://invalid.example/synthetic';Json (Join-Path $payload $case.member) $lock}
    }
    $manifestHash=$receipt.manifestSha256
    if($case.ContainsKey('member')){
        $member=@($manifest.files|Where-Object path -CEQ $case.member)[0]
        $member.size=(Get-Item -LiteralPath (Join-Path $payload $case.member)).Length
        $member.sha256=(Get-FileHash -LiteralPath (Join-Path $payload $case.member)).Hash.ToLowerInvariant()
        Json (Join-Path $payload 'package.json') $manifest
        $manifestHash=(Get-FileHash -LiteralPath (Join-Path $payload 'package.json')).Hash.ToLowerInvariant()
        [IO.File]::WriteAllText((Join-Path $payload 'package.sha256'),$manifestHash+"`n",$utf8)
    }
    $configPath=Join-Path $caseRoot 'configuration.json';$campaignPath=Join-Path $caseRoot 'campaign.json'
    Json $configPath $configuration;Json $campaignPath $campaign
    switch($case.kind){
        'duplicate' {$original=[IO.File]::ReadAllText($configPath,$utf8);[IO.File]::WriteAllText($configPath,('{"host":"localhost",'+$original.TrimStart().Substring(1)),$utf8)}
        'truncated' {[IO.File]::WriteAllText($configPath,'{',$utf8)}
        'utf8' {[IO.File]::WriteAllBytes($configPath,[byte[]]@(0xc0,0xaf))}
    }
    $control=Join-Path $caseRoot 'control'
    $arguments=@('-NoProfile','-File',(Join-Path $payload 'Invoke-Qualification.ps1'),'-Command','run',
        '-ManifestSha256',$manifestHash,'-Campaign',$campaignPath,'-Configuration',$configPath,'-Control',$control)
    Json (Join-Path $caseRoot 'reservation.json') ([ordered]@{state='RESERVED';expectedExit=2;expectedCode=$case.code;cwd=$payload;caseSeconds=120})
    $info=[Diagnostics.ProcessStartInfo]::new($pwsh);$info.WorkingDirectory=$payload
    $info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
    foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
    $process=[Diagnostics.Process]::Start($info)
    try{
        Json (Join-Path $caseRoot 'process.json') ([ordered]@{pid=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('O');cwd=$payload})
        $outPath=Join-Path $caseRoot 'stdout.log';$errPath=Join-Path $caseRoot 'stderr.log'
        $outFile=[IO.File]::Open($outPath,[IO.FileMode]::CreateNew);$errFile=[IO.File]::Open($errPath,[IO.FileMode]::CreateNew)
        $outTask=$process.StandardOutput.BaseStream.CopyToAsync($outFile);$errTask=$process.StandardError.BaseStream.CopyToAsync($errFile)
        $timer=[Diagnostics.Stopwatch]::StartNew();$limit=$false
        while(-not $process.WaitForExit(100)){
            if($timer.Elapsed.TotalSeconds -gt 120 -or $outFile.Length -gt 1048576 -or $errFile.Length -gt 1048576){$limit=$true;$process.Kill($true);$process.WaitForExit();break}
        }
        $code=if($limit){124}else{$process.ExitCode}
        try{$null=$outTask.GetAwaiter().GetResult();$null=$errTask.GetAwaiter().GetResult()}finally{$outFile.Dispose();$errFile.Dispose()}
        Json (Join-Path $caseRoot 'exit.json') ([ordered]@{exit=$code;limit=$limit;controlCreated=(Test-Path -LiteralPath $control)})
        $out=[IO.File]::ReadAllText($outPath,$utf8);$err=[IO.File]::ReadAllText($errPath,$utf8)
        $refusal=Read-QualificationJsonBytes $utf8.GetBytes($err.Trim())
        $created=Test-Path -LiteralPath $control
        $passed=$code -eq 2 -and $refusal.state -ceq 'REFUSED' -and $refusal.code -ceq $case.code -and -not $created -and $out.Trim().Length -eq 0
        $results.Add([ordered]@{case=$case.name;exit=$code;expectedCode=$case.code;observedCode=$refusal.code;passed=$passed;controlCreated=$created})
        Json (Join-Path $evidence 'result.json') ([ordered]@{state='IN_PROGRESS';cases=$results.ToArray()})
        if(-not $passed){throw 'QUAL_EXTRACTED_GUARD_FAILED'}
    }finally{if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
}
Json (Join-Path $evidence 'result.json') ([ordered]@{state='PASS_LOCAL';package=$PackageAttempt;cases=$results.ToArray();count=$results.Count;jdbc='NOT_STARTED';childrenCreated=0})
Get-Content -Raw -LiteralPath (Join-Path $evidence 'result.json')
