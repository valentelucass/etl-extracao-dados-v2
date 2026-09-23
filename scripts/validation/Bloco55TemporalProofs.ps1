# Dot-sourced only by the bounded official-JAR controller after protected setup.
function Temporal-Process([string]$Name,$Policy,$Blueprint,[switch]$Persist){
 $policyFile=Join-Path $work ($Name+'.policy.json')
 [IO.File]::WriteAllText($policyFile,($Policy|ConvertTo-Json -Depth 5),$encoding)
 [IO.File]::Copy($policyFile,(Join-Path $evidence ($Name+'.policy.json')),$false)
 $blueprintFile=Join-Path $work ($Name+'.blueprint.json')
 [IO.File]::WriteAllText($blueprintFile,($Blueprint|ConvertTo-Json -Depth 5),$encoding)
 $start=[Diagnostics.ProcessStartInfo]::new()
 $start.FileName='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
 $start.UseShellExecute=$false;$start.CreateNoWindow=$true;$start.WindowStyle='Hidden'
 $start.RedirectStandardOutput=$true;$start.RedirectStandardError=$true;$start.WorkingDirectory=$protectedRevision
 foreach($key in @($start.Environment.Keys)){if($key.StartsWith('V2_') -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')){[void]$start.Environment.Remove($key)}}
 if($Persist){
  for($unit=1;$unit -le [int]$Policy.maximumBacklog;$unit++){Add-Bloco55Reservation ($Name+'_'+$unit) 'OFFICIAL_JAR_TEMPORAL_PLAN_PERSIST'|Out-Null}
  $start.UserName='etl_v2_exec';$start.Domain=$env:COMPUTERNAME;$start.Password=$credentials.etl_v2_exec;$start.LoadUserProfile=$true
 }
 foreach($arg in @('-Xmx512m',('-Djava.library.path='+(Join-Path $protectedRevision 'native')),'-jar',(Join-Path $protectedRevision 'etl-dataexport-v2.jar'),$(if($Persist){'run'}else{'plan'}),'--config',$config,'--temporal',$policyFile)){$start.ArgumentList.Add($arg)}
 if(-not $Persist){$start.ArgumentList.Add('--request');$start.ArgumentList.Add($blueprintFile)}
 $before=$server.Requests
 $child=[Bloco55Child]::Run($start).GetAwaiter().GetResult()
 [IO.File]::WriteAllText((Join-Path $evidence ($Name+'.log')),$child.Output,$encoding)
 if($child.Limited -or $child.Code -ne 0 -or $server.Requests -ne $before){throw 'TEMPORAL_PROCESS_NOT_CONFIRMED'}
 if(-not $Persist){return @($child.Output|ConvertFrom-Json -AsHashtable -DateKind String)}
 Record ([ordered]@{id=$Name;layer='OFFICIAL_JAR_SERVICE_SQL';expectedExit=0;observedExit=$child.Code;http=0;passed=$child.Output.Contains('TEMPORAL_PERSISTED windows=2 extracted=0 scheduler=0')})
}
foreach($template in @('MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
 $name=$ProofId+'_'+$template
 $policy=Get-Content (Join-Path $root ('config/laboratory/bloco55-temporal-'+$template.ToLowerInvariant()+'.json')) -Raw|ConvertFrom-Json -AsHashtable -DateKind String
 $policy.invocation=[guid]::NewGuid().ToString()
 $blueprint=New-Request $template '2032-02-28'
 $windows=@(Temporal-Process ($name+'_EXPORT') $policy $blueprint)
 if($windows.Count -ne 2 -or $windows[0].businessStart -cne '2032-02-28' -or $windows[1].businessStart -cne '2032-02-29'){throw 'EXACT_LEAP_WINDOWS_REQUIRED'}
 Temporal-Process ($name+'_PERSIST') $policy $blueprint -Persist
 $ids=@($windows|ForEach-Object{([guid]$_.executionId).ToString()})
 $sql="SET NOCOUNT ON; SELECT COUNT_BIG(*) planned FROM ctl.runtime_temporal_window WHERE execution_id IN('$($ids[0])','$($ids[1])') FOR JSON PATH,WITHOUT_ARRAY_WRAPPER;"
 $out=Sql ($name+'_PLANNED') $sql
 $planned=($out -split "`n"|Where-Object {$_.Trim().StartsWith('{')}) -join ''|ConvertFrom-Json
 Record ([ordered]@{id=$name+'_TWO_WINDOWS_DURABLE';layer='SQL_READ_OWN_OCCURRENCES';planned=$planned.planned;passed=$planned.planned -eq 2})
 foreach($step in @('SECOND','GAP','REPEAT')){
  $w=$windows[$(if($step -ceq 'SECOND'){1}else{0})];$w.invocationId=[guid]::NewGuid().ToString()
  $http=if($step -ceq 'REPEAT'){0}else{4}
  Jar ($name+'_'+$step) 'etl_v2_exec' 'run' $w 0 $http|Out-Null
  $expected=if($step -ceq 'SECOND'){'2032-02-28T03:00:00Z'}else{'2032-03-01T03:00:00Z'}
  Record ([ordered]@{id=$name+'_'+$step+'_CONTIGUOUS';layer='OFFICIAL_JAR_SQL_RECONCILIATION';expected=$expected;passed=([IO.File]::ReadAllText((Join-Path $evidence ($name+'_'+$step+'.log')))).Contains('TEMPORAL_EXECUTION_RECONCILIATION contiguous='+$expected)})
 }
 $requests.Add($windows[0]);$requests.Add($windows[1])
 $bad=$windows[0]|ConvertTo-Json -Depth 8|ConvertFrom-Json -AsHashtable -DateKind String
 $bad.invocationId=[guid]::NewGuid().ToString();$bad.businessEnd='2032-02-29'
 Jar ($name+'_REQUEST_CHANGED') 'etl_v2_exec' 'run' $bad 2 0|Out-Null
 $bad=$windows[0]|ConvertTo-Json -Depth 8|ConvertFrom-Json -AsHashtable -DateKind String
 $bad.invocationId=[guid]::NewGuid().ToString();$bad.temporalPolicy=$bad.temporalPolicy.Replace('"lookbackSeconds":"0"','"lookbackSeconds":"1"')
 Jar ($name+'_POLICY_CHANGED') 'etl_v2_exec' 'run' $bad 2 0|Out-Null
}
