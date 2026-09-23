Set-StrictMode -Version Latest
$ErrorActionPreference='Stop'
function Read-ClosedJson([string]$Path,[int]$Maximum){
 $item=Get-Item -LiteralPath $Path
 if($item.Length -gt $Maximum -or ($item.Attributes -band [IO.FileAttributes]::ReparsePoint)){throw 'MANUAL_FILE_LIMIT_OR_REPARSE'}
 $text=[IO.File]::ReadAllText($item.FullName,[Text.UTF8Encoding]::new($false,$true))
 $json=[Text.Json.JsonDocument]::Parse($text)
 try{
  function Check-Object($Element){
   if($Element.ValueKind -eq [Text.Json.JsonValueKind]::Object){
    $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
    foreach($p in $Element.EnumerateObject()){if(-not $names.Add($p.Name)){throw 'MANUAL_DUPLICATE_JSON_KEY'};Check-Object $p.Value}
   }elseif($Element.ValueKind -eq [Text.Json.JsonValueKind]::Array){foreach($v in $Element.EnumerateArray()){Check-Object $v}}
  }
  Check-Object $json.RootElement
 }finally{$json.Dispose()}
 $text|ConvertFrom-Json -AsHashtable -DateKind String
}
function Assert-Keys($Document,[string[]]$Keys){
 if($Document -isnot [Collections.IDictionary] -or $Document.Count -ne $Keys.Count){throw 'MANUAL_CLOSED_SCHEMA'}
 foreach($key in $Document.Keys){if($key -cnotin $Keys){throw 'MANUAL_UNKNOWN_FIELD'}}
}
function Read-Bloco55ManualBatch([string]$Manifest,[string]$ExpectedSha256,$Review){
 $root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
 $allowed=(Join-Path $root 'target/bloco55/manual')+[IO.Path]::DirectorySeparatorChar
 $path=[IO.Path]::GetFullPath($Manifest)
 if(-not $path.StartsWith($allowed,[StringComparison]::OrdinalIgnoreCase) -or $ExpectedSha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'MANUAL_PATH_OR_HASH'}
 $parent=Get-Item ([IO.Path]::GetDirectoryName($path))
 while($parent.FullName.Length -ge $root.Length){if($parent.Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'MANUAL_REPARSE_PARENT'};$parent=$parent.Parent;if($null -eq $parent){break}}
 if((Get-FileHash $path).Hash.ToLowerInvariant() -cne $ExpectedSha256){throw 'MANUAL_MANIFEST_HASH'}
 $m=Read-ClosedJson $path 65536
 Assert-Keys $m @('version','database','artifactManifestSha256','configurationSha256','referenceReleaseId','failurePolicy','requests')
 if($m.version -cne 'bloco55-manual-v1' -or $m.database -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $m.artifactManifestSha256 -cne $Review.Hash -or $m.referenceReleaseId -cne '3' -or $m.failurePolicy -cne 'CONTINUE_INDEPENDENT_STOP_ON_PREFLIGHT_INTEGRITY_LIMIT'){throw 'MANUAL_EXACT_SCOPE'}
 $config=[IO.File]::ReadAllText((Join-Path $Review.Path 'runtime.properties')).Replace('http://127.0.0.1:1','http://127.0.0.1:62129')
 $configurationHash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($config))).ToLowerInvariant()
 if($m.configurationSha256 -cne $configurationHash){throw 'MANUAL_CONFIGURATION_HASH'}
 if($m.requests -isnot [Array] -or $m.requests.Count -lt 1 -or $m.requests.Count -gt 20){throw 'MANUAL_TWENTY_REQUESTS'}
 $required=@('invocationId','executionId','cycleId','template','mode','start','endExclusive','replayOf','idempotencyKey','businessStart','businessEnd','leaseSeconds','pageSize','maximumPages','maximumRows','maximumDistinctRoots','qualityVersion','qualityFingerprint','compatibilityVersion')
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal);$partitions=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 $counts=@{};$coletas=@{};$requests=[Collections.Generic.List[object]]::new();$ordinal=0
 foreach($entry in $m.requests){
  $ordinal++;Assert-Keys $entry @('path','sha256')
  if($entry.path -cnotmatch '^requests/[A-Z0-9_]{1,50}\.json$' -or $entry.sha256 -cnotmatch '^[a-f0-9]{64}$'){throw 'MANUAL_REQUEST_REFERENCE'}
  $file=Join-Path ([IO.Path]::GetDirectoryName($path)) $entry.path
  if((Get-Item ([IO.Path]::GetDirectoryName($file))).Attributes -band [IO.FileAttributes]::ReparsePoint){throw 'MANUAL_REQUEST_PARENT_REPARSE'}
  if((Get-FileHash $file).Hash.ToLowerInvariant() -cne $entry.sha256){throw 'MANUAL_REQUEST_HASH'}
  $r=Read-ClosedJson $file 16384
  $keys=$required+$(if($r.template -ceq 'COTACOES'){'referenceReleaseId'})+$(if($r.template -ceq 'FRETES'){'dependencyRequest'})
  Assert-Keys $r @($keys|Where-Object {$_})
  foreach($v in $r.Values){if($v -isnot [string] -or [Text.Encoding]::UTF8.GetByteCount($v) -gt 8192){throw 'MANUAL_REQUEST_STRING'}}
  if($r.template -cnotin @('COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS') -or $r.mode -cne 'BACKFILL' -or $r.replayOf -cne ''){throw 'MANUAL_WORKLOAD_MODE'}
  foreach($key in @('invocationId','executionId','cycleId','idempotencyKey')){if(([guid]$r[$key]).ToString() -cne $r[$key] -or -not $seen.Add($key+'|'+$r[$key])){throw 'MANUAL_UNIQUE_IDS'}}
  $date=[DateTime]::ParseExact($r.businessStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
  if($date.Year -lt 2018 -or $date.Year -gt 2039 -or $r.businessEnd -cne $r.businessStart -or $r.start -cne ($r.businessStart+'T03:00:00Z') -or $r.endExclusive -cne ($date.AddDays(1).ToString('yyyy-MM-dd')+'T03:00:00Z')){throw 'MANUAL_CIVIL_WINDOW'}
  if($r.leaseSeconds -cne '30' -or $r.pageSize -cne '2' -or $r.maximumPages -cne '4' -or $r.maximumRows -cne '16' -or $r.maximumDistinctRoots -cne '16' -or $r.compatibilityVersion -cne 'bloco55-compatible-v1'){throw 'MANUAL_FIXED_LAB_CAPS'}
  $q=@($Review.Manifest.quality|Where-Object {$_.template -ceq $r.template -and $_.mode -ceq 'BACKFILL'})
  if($q.Count -ne 1 -or $r.qualityVersion -cne $q[0].version -or $r.qualityFingerprint -cne $q[0].fingerprint){throw 'MANUAL_DQ_REFERENCE'}
  if($r.template -ceq 'COTACOES' -and $r.referenceReleaseId -cne $m.referenceReleaseId){throw 'MANUAL_TARIFF_REFERENCE'}
  if(-not $partitions.Add($r.template+'|'+$r.start)){throw 'MANUAL_DUPLICATE_PARTITION'}
  if(-not $counts.ContainsKey($r.template)){$counts[$r.template]=0};$counts[$r.template]++
  if($counts[$r.template] -gt 4){throw 'MANUAL_FOUR_WINDOWS'}
  if($r.template -ceq 'COLETAS'){$coletas[$r.start]=$r}
  if($r.template -ceq 'FRETES'){
   if(-not $coletas.ContainsKey($r.start)){throw 'MANUAL_DAG_PREDECESSOR_ORDER'}
   $dep=$r.dependencyRequest|ConvertFrom-Json -AsHashtable -DateKind String;Assert-Keys $dep $required
   foreach($key in $required){if($dep[$key] -cne $coletas[$r.start][$key]){throw 'MANUAL_FROZEN_PREDECESSOR_MISMATCH'}}
  }
  $requests.Add([pscustomobject]@{Ordinal=$ordinal;Path=$file;Sha256=$entry.sha256;Document=$r})
 }
 [pscustomobject]@{Manifest=$m;Path=$path;Sha256=$ExpectedSha256;Requests=$requests.ToArray();Configuration=$config}
}
Export-ModuleMember -Function Read-Bloco55ManualBatch
