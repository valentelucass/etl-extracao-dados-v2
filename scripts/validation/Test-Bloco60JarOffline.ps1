$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$evidence=Join-Path $root ('target/bloco60-local/jar-offline-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($evidence);$utf8=[Text.UTF8Encoding]::new($false,$true)
Add-Type -Path "$PSScriptRoot/Bloco60Process.cs"
$bundle=Join-Path $root 'target/bloco60-local/bundle-v2/administered';$results=@()
foreach($variant in @('standard','administered')){
 foreach($command in @('dry-run','run')){
  $jar=Join-Path $bundle $(if($variant -ceq 'standard'){'standard/etl-dataexport-v2.jar'}else{'etl-dataexport-v2.jar'})
  $start=[Diagnostics.ProcessStartInfo]::new();$start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
  $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden';$start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$root
  foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH','B60_PHYSICAL_PROBE')){[void]$start.Environment.Remove($key)}}
  $synthetic=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
  $start.Environment['V2_DATAEXPORT_TOKEN']=$synthetic;$start.Environment['V2_GRAPHQL_TOKEN']=$synthetic
  foreach($arg in @('-Xmx512m','-jar',$jar,$command,'--config',(Join-Path $bundle 'runtime.properties'))){$start.ArgumentList.Add($arg)}
  if($command -ceq 'run'){foreach($arg in @('--request',(Join-Path $root 'database/proposals/bloco60-local/requests/USUARIOS_RUN.json'))){$start.ArgumentList.Add($arg)}}
  $child=[Bloco60Child]::Run($start).GetAwaiter().GetResult()
  [IO.File]::WriteAllText((Join-Path $evidence ($variant+'-'+$command+'.log')),$child.Output,$utf8)
  $expected=if($command -ceq 'dry-run'){0}else{20}
  if($child.Code -ne $expected -or $child.Limited -or $child.Output -match 'RUNTIME_HTTP_ATTEMPTS|B60_SQL_ATTEMPTS'){throw 'B60_OFFLINE_JAR_DID_NOT_REFUSE_BEFORE_SOURCE_SQL'}
  $results+=@{variant=$variant;command=$command;exit=$child.Code;expected=$expected;passed=$true;sourceCalls=0;sqlOperations=0;layer='OFFLINE_JAR_DIAGNOSTIC_OR_AUTHORITY_REFUSAL'}
 }
}
[IO.File]::WriteAllText((Join-Path $evidence 'result.json'),($results|ConvertTo-Json),$utf8)
@{passed=$true;cases=$results.Count;evidence=[IO.Path]::GetRelativePath($root,$evidence);sqlExecuted=$false}|ConvertTo-Json
