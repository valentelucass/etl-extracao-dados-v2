#Requires -Version 7.5
param(
 [switch]$ValidateOnly,
 [ValidatePattern('^macrobloco-[a-z0-9-]{1,90}$')][string]$RoundName='macrobloco-qualificacao-pacote-20260913-01',
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Attempt,
 [Parameter(Mandatory)][ValidatePattern('^[a-z0-9-]{1,64}$')][string]$Package,
 [Parameter(Mandatory)][ValidateSet('sequence-a','sequence-b')][string]$Case,
 [ValidateSet('COMPLETE','VALUE','PRECISION','KEY','MULTIPLICITY','OLD_REFERENCE','MISSING_USER','PIN_DRIFT','COMMAND')][string]$Variant='COMPLETE'
)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$round=Join-Path $root ('target/'+$RoundName)
Import-Module (Join-Path $root 'scripts/validation/QualificationPackage.psm1') -Force
Assert-QualificationPath $round
$nominalPath=Join-Path $root 'docs/catalogos/p06-revisao/sweep-responsibilities.v1.csv'
Assert-QualificationPath $nominalPath
if((Get-FileHash -LiteralPath $nominalPath).Hash.ToLowerInvariant() -cne '69ef33c8c095c200f4025264e38c0dd248fa4f12bde8722aaa00101c570d3bd9'){throw 'SEQUENCE_NOMINAL_PIN'}
$nominal=@(Import-Csv -LiteralPath $nominalPath)
if($ValidateOnly){return [pscustomobject]@{round=$round;nominalRows=$nominal.Count;schemaVersion=104;physical=$false}}
$attemptRoot=Join-Path $round $Attempt
if(Test-Path -LiteralPath $attemptRoot){throw 'SEQUENCE_ATTEMPT_EXISTS'}
$null=[IO.Directory]::CreateDirectory($attemptRoot)
$utf8=[Text.UTF8Encoding]::new($false,$true)
function Save([string]$name,$value){[IO.File]::WriteAllText((Join-Path $attemptRoot $name),($value|ConvertTo-Json -Depth 48),$utf8)}
function WriteJson([string]$file,$value){[IO.File]::WriteAllText($file,($value|ConvertTo-Json -Depth 48 -Compress),$utf8)}
function ReadJson([string]$file){return Read-QualificationJsonBytes ([IO.File]::ReadAllBytes($file)) 524288}
function Pin([string]$file){return @{file=[IO.Path]::GetFileName($file);sha256=(Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant()}}
Save 'reservation.json' @{state='RESERVED_OUTCOME_UNKNOWN';action='extracted-jar-sequence';package=$Package;case=$Case;variant=$Variant;
 target='localhost/ETL_SISTEMA_V2_SHADOW';heapMiB=512;sequenceSeconds=1800;stageSeconds=240;querySeconds=60;maximumLogBytes=16777216;
 ddl=$false;domainCommit=$false;recovery='reconcile owned process, receipt and aggregate SQL counts before retry'}
Import-Module (Join-Path $root 'scripts/validation/QualificationPackage.psm1') -Force
$packageRoot=Join-Path $round $Package
$packaged=ReadJson (Join-Path $packageRoot 'result.json')
$payload=Join-Path $attemptRoot 'extracted'
$null=Expand-QualificationPackage -Archive (Join-Path $packageRoot 'qualification.zip') -ArchiveSha256 $packaged.archiveSha256 -Destination $payload -AllowedRoot $round
$null=Test-QualificationPackage -Directory $payload -ManifestSha256 $packaged.manifestSha256
$schema=ReadJson (Join-Path $payload 'schema/schema-index.json')
if($schema.lastVersion -ne 104){throw 'SEQUENCE_PROOF_SCHEMA_REVISION'}
$jar=Join-Path $payload 'etl-dataexport-v2.jar';$jarHash=(Get-FileHash -LiteralPath $jar).Hash.ToLowerInvariant()
$caseRoot=Join-Path $payload ('artifact-cases/'+$Case)
$sequenceFile=Join-Path $caseRoot 'sequence.json'
$sequence=ReadJson $sequenceFile
if($sequence.steps.Count -ne 7){throw 'SEQUENCE_PROOF_STAGE_COUNT'}
$mutatedContract='';$mutatedStage=''
if($Variant -cne 'COMPLETE'){
 $bad=Join-Path $attemptRoot 'mutated-inputs';Copy-Item -LiteralPath $caseRoot -Destination $bad -Recurse
 $sequenceFile=Join-Path $bad 'sequence.json';$sequence=ReadJson $sequenceFile
 $stage=if($Variant -ceq 'OLD_REFERENCE'){$sequence.steps[5]}else{$sequence.steps[0]}
 $mutatedStage=$stage.id
 if($Variant -ceq 'COMMAND'){$sequence['sql']='arbitrary'}
 elseif($Variant -ceq 'PIN_DRIFT'){
  $input=ReadJson (Join-Path $bad $stage.input.file)
  $source=ReadJson (Join-Path $bad $input.sources.FRE.file)
  [IO.File]::AppendAllText((Join-Path $bad $source.pages[0].file),"`n",$utf8)
 }elseif($Variant -ceq 'MISSING_USER'){
  $input=ReadJson (Join-Path $bad $stage.input.file);$null=$input.sources.Remove('USER')
  $inputFile=Join-Path $bad 'bad-input.json';WriteJson $inputFile $input;$stage.input=Pin $inputFile
  $oracle=ReadJson (Join-Path $bad $stage.oracle.file);$oracle.inputSha256=$stage.input.sha256
  $oracleFile=Join-Path $bad 'bad-oracle.json';WriteJson $oracleFile $oracle;$stage.oracle=Pin $oracleFile
 }else{
  $oracle=ReadJson (Join-Path $bad $stage.oracle.file)
  $outputs=ReadJson (Join-Path $bad $oracle.outputs.file)
  $mutatedContract=if($Variant -ceq 'OLD_REFERENCE'){'SQL-05'}else{'SQL-02'}
  $selected=@($outputs.outputs|Where-Object id -CEQ $mutatedContract)
  if($selected.Count -ne 1){throw 'SEQUENCE_MUTATION_OUTPUT'}
  $entry=$selected[0];$rows=ReadJson (Join-Path $bad $entry.batches[0].file)
  if($Variant -ceq 'MULTIPLICITY'){
   if($rows.Count -lt 2){throw 'SEQUENCE_MULTIPLICITY_INPUT'}
   $rows[1]=$rows[0].Clone()
  }else{
   $catalog=ReadJson (Join-Path $payload 'contracts/query-contracts.synthetic.json')
   $metadata=@($catalog.contracts|Where-Object id -CEQ $mutatedContract)[0]
   $ordinal=if($Variant -ceq 'KEY'){1}elseif($Variant -ceq 'OLD_REFERENCE'){
    @($metadata.columns|Where-Object name -CEQ 'Min. Frete/KG')[0].ordinal-1
   }else{18}
   if($Variant -ceq 'KEY'){$rows[0][$ordinal]=9999991L}
   elseif($Variant -ceq 'VALUE'){$rows[0][$ordinal]='9999.00000000'}
   elseif($Variant -ceq 'PRECISION'){$rows[0][$ordinal]=([decimal]::Parse($rows[0][$ordinal],[Globalization.CultureInfo]::InvariantCulture)+[decimal]0.00000001).ToString('0.00000000',[Globalization.CultureInfo]::InvariantCulture)}
   else{
    $oldOracle=ReadJson (Join-Path $bad $sequence.steps[0].oracle.file)
    $oldOutputs=ReadJson (Join-Path $bad $oldOracle.outputs.file)
    $old=@($oldOutputs.outputs|Where-Object id -CEQ 'SQL-05')[0]
    $oldRows=ReadJson (Join-Path $bad $old.batches[0].file)
    if($rows[0][$ordinal] -ceq $oldRows[0][$ordinal]){throw 'SEQUENCE_REFERENCE_NOT_CHANGED'}
    $rows[0][$ordinal]=$oldRows[0][$ordinal]
   }
  }
  $rowsFile=Join-Path $bad 'bad-rows.json';WriteJson $rowsFile $rows;$entry.batches[0]=Pin $rowsFile
  $outputsFile=Join-Path $bad 'bad-outputs.json';WriteJson $outputsFile $outputs;$oracle.outputs=Pin $outputsFile
  $oracleFile=Join-Path $bad 'bad-oracle.json';WriteJson $oracleFile $oracle;$stage.oracle=Pin $oracleFile
 }
 WriteJson $sequenceFile $sequence
 Save 'mutation.json' @{variant=$Variant;stage=$mutatedStage;contract=$mutatedContract;expectedAuthoredBeforeSql=$true;fromObservedSql=$false}
}
Save 'binding.json' @{archiveSha256=$packaged.archiveSha256;manifestSha256=$packaged.manifestSha256;jarSha256=$jarHash;
 sequenceSha256=(Get-FileHash -LiteralPath $sequenceFile).Hash.ToLowerInvariant();schemaVersion=$schema.lastVersion}
function Sql([string]$database,[string]$statement,[string]$name){
 $info=[Diagnostics.ProcessStartInfo]::new('sqlcmd.exe');$info.UseShellExecute=$false;$info.CreateNoWindow=$true
 $info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true;$info.StandardOutputEncoding=$utf8;$info.StandardErrorEncoding=$utf8
 foreach($a in @('-S','localhost','-C','-E','-d',$database,'-l','5','-t','10','-b','-f','65001','-h','-1','-W','-Q',$statement)){$info.ArgumentList.Add($a)}
 $p=[Diagnostics.Process]::Start($info)
 try{$o=$p.StandardOutput.ReadToEndAsync();$e=$p.StandardError.ReadToEndAsync()
  if(-not $p.WaitForExit(15000)){$p.Kill($true);$p.WaitForExit();throw 'INTEGRAL_SQL_TIMEOUT'}
  [IO.File]::WriteAllText((Join-Path $attemptRoot $name),$o.GetAwaiter().GetResult()+$e.GetAwaiter().GetResult(),$utf8)
  if($p.ExitCode -ne 0){throw 'INTEGRAL_SQL_PREFLIGHT'}
 }finally{$p.Dispose()}
}
$query="SET NOCOUNT ON; IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' THROW 53900,N'QUAL_WRONG_TARGET',1; SELECT SCHEMA_NAME(t.schema_id)+N'.'+t.name,SUM(p.rows) FROM sys.tables t JOIN sys.partitions p ON p.object_id=t.object_id AND p.index_id IN(0,1) GROUP BY t.schema_id,t.name ORDER BY 1;"
Sql 'master' "IF (SELECT COUNT(*) FROM sys.databases WHERE name=N'ETL_SISTEMA_V2_SHADOW' AND state_desc=N'ONLINE')<>1 THROW 53900,N'QUAL_SHADOW_MISSING',1;" 'master.log'
Sql 'ETL_SISTEMA_V2_SHADOW' $query 'before.log'
$temporary=Join-Path $attemptRoot 'temporary';$null=[IO.Directory]::CreateDirectory($temporary)
$info=[Diagnostics.ProcessStartInfo]::new('C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe')
$info.WorkingDirectory=$payload;$info.UseShellExecute=$false;$info.CreateNoWindow=$true;$info.RedirectStandardOutput=$true;$info.RedirectStandardError=$true
$info.Environment['TEMP']=$temporary;$info.Environment['TMP']=$temporary
foreach($name in @('JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')){$null=$info.Environment.Remove($name)}
$info.Environment['V2_SHADOW_JDBC_URL']='jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;encrypt=true;trustServerCertificate=true;loginTimeout=5;socketTimeout=65000'
$arguments=@('-Xms64m','-Xmx512m','-Dfile.encoding=UTF-8',('-Djava.io.tmpdir='+$temporary),('-Djava.library.path='+(Join-Path $payload 'native')),
 '-Dshadow.local.integration.enabled=true','-Dshadow.local.integration.profile.active=true','-jar',$jar,'local-sequence','run','--sequence',$sequenceFile)
foreach($argument in $arguments){$info.ArgumentList.Add($argument)}
$watch=[Diagnostics.Stopwatch]::StartNew();$process=[Diagnostics.Process]::Start($info)
$timedOut=$false;$oversized=$false
try{
 Save 'process.json' @{pid=$process.Id;startedUtc=$process.StartTime.ToUniversalTime().ToString('O');arguments=$arguments;workingDirectory=$payload}
 $outPath=Join-Path $attemptRoot 'stdout.log';$errPath=Join-Path $attemptRoot 'stderr.log'
 $outFile=[IO.FileStream]::new($outPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 $errFile=[IO.FileStream]::new($errPath,[IO.FileMode]::CreateNew,[IO.FileAccess]::Write,[IO.FileShare]::Read)
 try{
  $stdout=$process.StandardOutput.BaseStream.CopyToAsync($outFile);$stderr=$process.StandardError.BaseStream.CopyToAsync($errFile)
  while(-not $process.WaitForExit(500)){
   $oversized=(Get-Item -LiteralPath $outPath).Length -gt 16777216 -or (Get-Item -LiteralPath $errPath).Length -gt 16777216
   $timedOut=$watch.Elapsed.TotalSeconds -ge 1800
   if($timedOut -or $oversized){$process.Kill($true);$process.WaitForExit();break}
  }
  $null=$stdout.GetAwaiter().GetResult();$null=$stderr.GetAwaiter().GetResult()
 }finally{$outFile.Dispose();$errFile.Dispose()}
 $exitCode=$process.ExitCode
}finally{if(-not $process.HasExited){$process.Kill($true);$process.WaitForExit()};$process.Dispose()}
Sql 'ETL_SISTEMA_V2_SHADOW' $query 'after.log'
$rollback=[IO.File]::ReadAllText((Join-Path $attemptRoot 'before.log'),$utf8) -ceq [IO.File]::ReadAllText((Join-Path $attemptRoot 'after.log'),$utf8)

$stdoutText=[IO.File]::ReadAllText($outPath,$utf8);$null=[IO.File]::ReadAllText($errPath,$utf8)
$stages=[regex]::Matches($stdoutText,'SEQUENCE_STAGE id=([a-z]+) .*? state=([A-Z_]+) millis=(\d+) jdbc=(\d+)')
$comparisons=[regex]::Matches($stdoutText,'SEQUENCE_COMPARE stage=([a-z]+) contract=(SQL-\d{2}) expected=(\d+) observed=(\d+) differences=(\d+)')
$previews=[regex]::Matches($stdoutText,'SEQUENCE_SWEEP stage=([a-z]+) responsibility=(SWP-[A-Z0-9-]+) disposition=([A-Z_]+) reason=([A-Z_]+)')
$admission=$Variant -cin @('COMMAND','PIN_DRIFT','MISSING_USER')
$expectedExit=if($Variant -ceq 'COMPLETE'){0}elseif($admission){20}else{40}
$passed=$rollback -and -not $timedOut -and -not $oversized -and $exitCode -eq $expectedExit
if($Variant -ceq 'COMPLETE'){
 $passed=$passed -and $stages.Count -eq 7 -and $comparisons.Count -eq 133 -and $previews.Count -eq 231
 $passed=$passed -and @($stages|Where-Object {$_.Groups[2].Value -cne 'PASS_LOCAL' -or [long]$_.Groups[3].Value -gt 240000}).Count -eq 0
 $passed=$passed -and @($comparisons|Where-Object {$_.Groups[5].Value -cne '0'}).Count -eq 0

 foreach($stage in $sequence.steps){
  $observed=@($previews|Where-Object {$_.Groups[1].Value -ceq $stage.id})
  $passed=$passed -and $observed.Count -eq 33 -and (($observed|ForEach-Object {$_.Groups[2].Value}|Sort-Object)-join '|') -ceq (($nominal.row_id|Sort-Object)-join '|')
  $passed=$passed -and @($observed|Where-Object {$_.Groups[3].Value -cne 'BLOCKED' -or $_.Groups[4].Value -cne 'APPLICABILITY_NOT_ENABLED'}).Count -eq 0
 }
}elseif($admission){$passed=$passed -and $stages.Count -eq 0 -and $stdoutText.Contains('SEQUENCE_REJECTED')}
else{
 $expectedStages=if($Variant -ceq 'OLD_REFERENCE'){6}else{1}
 $failed=@($comparisons|Where-Object {[long]$_.Groups[5].Value -gt 0})
 $passed=$passed -and $stages.Count -eq $expectedStages -and $failed.Count -eq 1 -and $failed[0].Groups[2].Value -ceq $mutatedContract
}
if(-not $admission){$passed=$passed -and $stdoutText.Contains('SEQUENCE_ROLLBACK_CONFIRMED')}
Save 'result.json' @{state='OBSERVED';passed=$passed;variant=$Variant;case=$Case;exit=$exitCode;expectedExit=$expectedExit;
 rollbackConfirmed=$rollback;timedOut=$timedOut;logLimitExceeded=$oversized;elapsedMilliseconds=$watch.ElapsedMilliseconds;
 stages=$stages.Count;comparisons=$comparisons.Count;previews=$previews.Count;layer='DISTRIBUTED_JAR_EXTRACTED_SEQUENCE';sourceCalls=0;ddl=$false;domainCommit=$false}
Get-Content -LiteralPath (Join-Path $attemptRoot 'result.json')
if(-not $passed){exit 1}
