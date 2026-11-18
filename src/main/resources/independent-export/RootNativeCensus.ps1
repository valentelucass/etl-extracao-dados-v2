function WNeed([bool]$Value,[string]$Code){if(-not$Value){throw ('FC_'+$Code)}}
function WNoLinks([string]$Path){for($at=[IO.Path]::GetFullPath($Path);$at;$at=[IO.Path]::GetDirectoryName($at)){if(Test-Path -LiteralPath $at){WNeed (([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-eq0) 'NO_REPARSE'}}}
function WHold($Pin){
 WNeed ($Pin-is[Collections.IDictionary]-and$Pin.Count-eq3-and$Pin.path-is[string]-and[IO.Path]::IsPathFullyQualified($Pin.path)-and[IO.Path]::GetFullPath($Pin.path)-ceq$Pin.path-and[IO.Path]::GetExtension($Pin.path)-ine'.env'-and$Pin.sha256-cmatch'^[A-Fa-f0-9]{64}$'-and$Pin.bytes-gt0-and$Pin.bytes-le67108864) 'INPUT_PIN'
 WNoLinks $Pin.path;$stream=[IO.File]::Open($Pin.path,'Open','Read','Read');$ok=$false
 try{WNeed ($stream.Length-eq$Pin.bytes-and(Get-FileHash -InputStream $stream -Algorithm SHA256).Hash-ieq$Pin.sha256) 'INPUT_PIN_DRIFT';$stream.Position=0;$leases.Add(@{pin=$Pin;stream=$stream});$ok=$true}finally{if(-not$ok){$stream.Dispose()}}
}
function WPostcheck {foreach($lease in $leases){WNoLinks $lease.pin.path;$lease.stream.Position=0;WNeed ($lease.stream.Length-eq$lease.pin.bytes-and(Get-FileHash -InputStream $lease.stream -Algorithm SHA256).Hash-ieq$lease.pin.sha256-and(Get-FileHash -LiteralPath $lease.pin.path -Algorithm SHA256).Hash-ieq$lease.pin.sha256) 'INPUT_POSTCHECK';$lease.stream.Position=0}}
function WReadBytes($Pin,[int]$Maximum){WNeed ($Pin.bytes-le$Maximum) 'READ_BOUND';$bytes=[IO.File]::ReadAllBytes($Pin.path);WNeed ($bytes.Length-eq$Pin.bytes-and$bytes.Length-le$Maximum) 'READ_SIZE';return ,$bytes}
function WReadJson($Pin){$bytes=WReadBytes $Pin 1048576;return ([Text.UTF8Encoding]::new($false,$true).GetString($bytes)|ConvertFrom-Json -AsHashtable -Depth 80 -DateKind String)}
function WTuple($Row){
 WNeed ($Row.pid-gt0-and$Row.pid-le2147483647-and$Row.parentPid-gt0-and$Row.parentPid-le2147483647-and$Row.name-cin@('java.exe','javaw.exe')-and$Row.creationUtc-is[string]) 'JVM_TUPLE'
 return ([string]$Row.pid+'|'+[string]$Row.parentPid+'|'+$Row.name+'|'+([DateTimeOffset]::Parse($Row.creationUtc)).ToUniversalTime().ToString('o'))
}
function WValidateSelection($Selection){
 WNeed ($Selection.schema-ceq'ROOT_APPROVED_FRETES_EXTERNAL_JVM_SELECTION0674_V1'-and$Selection.selectedBy-ceq'Root'-and$Selection.selectedIdentityCount-ge1-and$Selection.selectedIdentityCount-le128-and$Selection.selectedIdentities-is[array]-and$Selection.selectedIdentities.Count-eq$Selection.selectedIdentityCount) 'EXACT_SELECTION'
 foreach($flag in @('completeAllJvmEnumerationRequired','currentNativeObservationRequiredBeforeEverySend','unexpectedOrMissingJvmStops','noImageOrCommandProofClaimed')){WNeed ($Selection[$flag]-is[bool]-and$Selection[$flag]) 'SELECTION_FLAGS'}
 $expected=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);foreach($row in $Selection.selectedIdentities){WNeed ($expected.Add((WTuple $row))) 'DUPLICATE_SELECTED_TUPLE'}
}
function WNativeGate($Selection){
 WValidateSelection $Selection
 $expected=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);foreach($row in $Selection.selectedIdentities){[void]$expected.Add((WTuple $row))}
 $memory=Get-CimInstance Win32_PerfFormattedData_PerfOS_Memory -OperationTimeoutSec 2
 WNeed ($memory.AvailableMBytes-ge4096-and$memory.PercentCommittedBytesInUse-ge0-and$memory.PercentCommittedBytesInUse-le85) 'MEMORY_4G_85'
 $at=[DateTimeOffset]::UtcNow;$watch=[Diagnostics.Stopwatch]::StartNew()
 $snapshot=@(Get-CimInstance Win32_Process -Property ProcessId,ParentProcessId,Name,CreationDate -OperationTimeoutSec 2);$watch.Stop()
 WNeed ($watch.ElapsedMilliseconds-le2500-and$snapshot.Count-gt0-and$snapshot.Count-le4096) 'CIM_TIME_OR_ROW_BOUND'
 WNeed ($Selection.Contains('ownedMainProof')) 'OWNED_MAIN_PROOF_REQUIRED'
 $owned=$Selection.ownedMainProof
 WNeed ($owned.workerPid-eq$PID-and[EslIndependentExport.OwnedCurlJob]::CurrentOriginalContainsProcess([int]$owned.pid)) 'OWNED_MAIN_SAME_ORIGINAL'
 $mainRows=@($snapshot|Where-Object{$_.ProcessId-eq$owned.pid});$workerRows=@($snapshot|Where-Object{$_.ProcessId-eq$PID})
 WNeed ($mainRows.Count-eq1-and$workerRows.Count-eq1-and$workerRows[0].ParentProcessId-eq$owned.pid) 'OWNED_MAIN_ACTUAL_CHILD'
 $actualMain=@{pid=[long]$mainRows[0].ProcessId;parentPid=[long]$mainRows[0].ParentProcessId;name=([string]$mainRows[0].Name).ToLowerInvariant();creationUtc=([DateTimeOffset]$mainRows[0].CreationDate).ToUniversalTime().ToString('o')}
 WNeed ((WTuple $actualMain)-ceq(WTuple $owned)) 'OWNED_MAIN_KERNEL_TUPLE_DRIFT'
 $actualTuples=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$pids=[Collections.Generic.HashSet[long]]::new();$jvms=0
 foreach($row in $snapshot){
  WNeed ($pids.Add([long]$row.ProcessId)) 'DUPLICATE_PROCESS_PID'
  if($row.Name-ieq'java.exe'-or$row.Name-ieq'javaw.exe'){
   $jvms++;WNeed ($jvms-le128-and$null-ne$row.CreationDate) 'JVM_BOUND_OR_BIRTH'
   $tuple=@{pid=[long]$row.ProcessId;parentPid=[long]$row.ParentProcessId;name=([string]$row.Name).ToLowerInvariant();creationUtc=([DateTimeOffset]$row.CreationDate).ToUniversalTime().ToString('o')}
   WNeed ($actualTuples.Add((WTuple $tuple))) 'DUPLICATE_LIVE_TUPLE'
  }
 }
 $countMatches=($actualTuples.Count-eq$Selection.selectedIdentityCount);$setMatches=$actualTuples.SetEquals($expected)
 $onlySelected=@($expected|Where-Object{-not$actualTuples.Contains($_)});$onlyActual=@($actualTuples|Where-Object{-not$expected.Contains($_)})
 $decisionAt=[DateTimeOffset]::UtcNow;$ageMilliseconds=($decisionAt-$at).TotalMilliseconds;$ageValid=($ageMilliseconds-lt3000)
 try{WNeed ($countMatches-and$setMatches-and$ageValid) 'CURRENT_TUPLE_DRIFT_OR_AGE'}finally{
  $cause=[Collections.Generic.List[string]]::new();if(-not$countMatches){$cause.Add('COUNT')};if(-not$setMatches){$cause.Add('SET')};if(-not$ageValid){$cause.Add('AGE')}
  $observation=@{schema='ROOT_FRETES_NATIVE_GATE_OBSERVATION0715_V1';OriginalPid=$PID;observedUtc=$at.ToString('o');decisionUtc=$decisionAt.ToString('o');queryElapsedMs=$watch.ElapsedMilliseconds;enumeratedProcesses=$snapshot.Count;selectedCount=$Selection.selectedIdentityCount;actualCount=$actualTuples.Count;countMatches=$countMatches;exactSet=$setMatches;ageMs=$ageMilliseconds;ageLessThan3000=$ageValid;queryWithin2500=($watch.ElapsedMilliseconds-le2500);selectedOnly=$onlySelected;actualOnly=$onlyActual;causes=@($cause);tuplesFormat='pid|parentPid|name|creationUtc';commandLineRead=$false;imagePathRead=$false;Source=0;HTTP=0}
  $observationRun=if($c.Contains('run')){$c.run}else{Split-Path -Parent $c.outputPath}
  [void](S2Fresh (Join-Path $observationRun 'native-gate-observation.json') $observation 131072)
 }
 return @{observedUtc=$at.ToString('o');queryElapsedMs=$watch.ElapsedMilliseconds;enumeratedProcesses=$snapshot.Count;jvmCount=$jvms;selectedIdentityCount=$Selection.selectedIdentityCount;exactTupleMatch=$true;availableMiB=[long]$memory.AvailableMBytes;commitPercent=[int]$memory.PercentCommittedBytesInUse;imageOrCommandIdentityClaimed=$false}
}
function WQuote([string]$Value){WNeed ($Value-cnotmatch'[\x00-\x1F\x7F]') 'STDIN_CONFIG_CONTROL';return '"'+$Value.Replace('\','\\').Replace('"','\"')+'"'}
