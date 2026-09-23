#Requires -Version 7.5
param([switch]$SelfTest)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$localSuccessor=$null
if(Test-Path -LiteralPath (Join-Path $root 'docs/catalogos/bloco61-local/manifesto.json')){
 $localSuccessor=& (Join-Path $PSScriptRoot 'Test-Bloco61Local.ps1') -AsMap
}
function Historical([string]$Path){
 if($null -ne $localSuccessor -and $localSuccessor.ContainsKey($Path)){return $localSuccessor[$Path].snapshot}
 return $Path
}
function Read([string]$Path){[IO.File]::ReadAllText((Join-Path $root (Historical $Path)),[Text.UTF8Encoding]::new($false,$true)).Replace("`r`n","`n")}
$old=Read 'database/proposals/bloco60-local/package.json'|ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String
function Validate($p){
 if($p.block -ne 60 -or $p.status -cne 'PREPARED_CORRECTIVE_APPROVAL_REQUIRED' -or $p.target -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or $p.validFrom -cne '2026-09-10T00:00:00Z' -or $p.validUntil -cne $old.validUntil -or $p.approvalRecorded -ne $false -or $p.sqlExecuted -ne $false -or $p.sourceRealExecuted -ne $false){throw 'B60R_SCOPE'}
 foreach($key in @('serverName','administrator','executor','observer','authorityId','policy','sourceInstance','tenantScope','loopback','cases')){if($p[$key] -cne $old[$key]){throw 'B60R_IDENTITIES_AND_CASES'}}
 if(($p.limits.Keys|Sort-Object)-join '|' -cne (($old.limits.Keys|Sort-Object)-join '|')){throw 'B60R_LIMIT_KEYS'}
 foreach($key in $old.limits.Keys){if($p.limits[$key] -ne $old.limits[$key]){throw 'B60R_LIMIT'}}
 if($p.predecessorPackage -cne 'a3d28adeb17775bcb965756bece5e43ac18c8eea9f9d00349f66c976716db57e' -or $p.predecessorReceipt -cne 'f2b35d102e8517b4fda6be67d2d84b8b1945077670470f77ef1d8f31307b605b'){throw 'B60R_PREDECESSOR'}
 if($p.expectedCatalog -cne 'cfd68974c4fc7667cde1bf9376250d41d58fccf026f9aae5a03268062ecd8e34' -or $null -ne $p.expectedRows -or $p.baselineMethod -cne 'EXISTING_V024_COMPENSATED_SERVICE_19_NO_DDL'){throw 'B60R_BASELINE'}
 foreach($key in @('serviceBefore','serviceActive','serviceAfter','usersScopeBefore','usersScopeActive','usersScopeAfter')){if($p.versions[$key] -ne @{serviceBefore=19;serviceActive=20;serviceAfter=21;usersScopeBefore=2;usersScopeActive=3;usersScopeAfter=4}[$key]){throw 'B60R_VERSIONS'}}
 $seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
 foreach($e in $p.files){
  if($e.path -cnotmatch '^[A-Za-z0-9_.$/-]+$' -or $e.path.Contains('..') -or $e.path.StartsWith('/') -or -not $seen.Add($e.path)){throw 'B60R_PATH'}
  if((Get-FileHash (Join-Path $root (Historical $e.path))).Hash.ToLowerInvariant() -cne $e.sha256){throw 'B60R_FILE_HASH'}
 }
 foreach($e in $old.files){if((Get-FileHash (Join-Path $root (Historical $e.path))).Hash.ToLowerInvariant() -cne $e.sha256){throw 'B60R_OLD_PACKAGE_DRIFT'}}
 $matrix=Read 'database/proposals/bloco60-correcao/matrix.json'|ConvertFrom-Json -AsHashtable
 $original=Read 'database/proposals/bloco60-local/matrix.json'|ConvertFrom-Json -AsHashtable
 if($matrix.Count -ne 74){throw 'B60R_MATRIX_COUNT'}
 $oldIds=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 $newInvocations=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
 foreach($c in $original){$r=Read ('database/proposals/bloco60-local/'+$c.request)|ConvertFrom-Json -AsHashtable;foreach($key in @('executionId','invocationId','cycleId','idempotencyKey')){[void]$oldIds.Add($r[$key])}}
 for($i=0;$i -lt 74;$i++){
  $c=$matrix[$i];$o=$original[$i]
  foreach($key in $o.Keys|Where-Object {$_ -cnotin @('group','expected')}){if(($c[$key]|ConvertTo-Json -Compress -Depth 10) -cne ($o[$key]|ConvertTo-Json -Compress -Depth 10)){throw 'B60R_CASE_ORACLE'}}
  foreach($key in $o.expected.Keys|Where-Object {$_ -cne 'replayOf'}){if($c.expected[$key] -cne $o.expected[$key]){throw 'B60R_CASE_EXPECTED'}}
  $expectedGroup='B60_C_'+[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::UTF8.GetBytes($o.group))).Substring(0,16)
  if($c.group -cne $expectedGroup -or $c.group -cnotmatch '^B60_[A-Z0-9_]{1,40}$'){throw 'B60R_KEY_GROUP'}
  $r=Read ('database/proposals/bloco60-correcao/'+$c.request)|ConvertFrom-Json -AsHashtable
  if(-not $newInvocations.Add($r.invocationId)){throw 'B60R_INVOCATION_DUPLICATE'}
  foreach($key in @('executionId','invocationId','cycleId','idempotencyKey')){if($oldIds.Contains($r[$key])){throw 'B60R_OLD_ID_REUSED'}}
  if($r.replayOf -and $r.replayOf -cne $c.expected.replayOf){throw 'B60R_REPLAY_ORACLE'}
 }
 $quality=Read 'database/proposals/bloco60-correcao/quality-references.json'|ConvertFrom-Json
 foreach($q in $quality){if($q.version -cnotmatch '^bloco60-usuarios-(backfill|replay)-v2$' -or [Convert]::ToHexString([Security.Cryptography.SHA256]::HashData([Text.Encoding]::Unicode.GetBytes($q.material))).ToLowerInvariant() -cne $q.fingerprint){throw 'B60R_DQ_FINGERPRINT'}}
 $prefix='database/proposals/bloco60-correcao/'
 $activate=Read ($prefix+'activate.sql');$compensate=Read ($prefix+'compensate.sql');$rows=Read ($prefix+'preserved-row-hashes.sql')
 if($activate.Contains('INSERT ctl.runtime_identity_scope') -or $activate.Contains('INSERT ctl.source_protocol_binding') -or -not $activate.Contains('scope_version=3,revoked=0') -or -not $activate.Contains('mapping_version=20') -or -not $activate.Contains('mapping_version=19') -or [regex]::Matches($activate,'(?m)^GRANT EXECUTE').Count -ne 2){throw 'B60R_ACTIVATION'}
 if(-not $compensate.Contains('scope_version=4 WHERE') -or -not $compensate.Contains('mapping_version=21') -or $compensate.Contains('bloco60-usuarios-backfill-v1') -or [regex]::Matches($compensate,'(?m)^REVOKE EXECUTE').Count -ne 2){throw 'B60R_COMPENSATION'}
 if($rows.Contains("name NOT IN") -or -not $rows.Contains("@table=N'runtime_identity_scope'") -or -not $rows.Contains("mode IN(N''BACKFILL'',N''REPLAY'')")){throw 'B60R_PRESERVATION'}
 foreach($file in Get-ChildItem (Join-Path $root $prefix) -Filter '*.sql'){
  $sql=[IO.File]::ReadAllText($file.FullName)
  if($sql -match '(?im)^\s*(CREATE|ALTER|DROP|TRUNCATE|DELETE)\s'){throw 'B60R_NO_DDL_OR_DELETE'}
  foreach($include in [regex]::Matches($sql,'(?m)^:r "([^"]+)"')){if(-not (Test-Path -LiteralPath (Join-Path $file.DirectoryName $include.Groups[1].Value))){throw 'B60R_SQL_INCLUDE'}}
 }
 $controller=Read 'scripts/validation/Invoke-Bloco60CorrectivePhysical.ps1'
 if($controller -match "'INSTALL_V024'|'QUALIFY_UPGRADE'|'QUALIFY_BASELINE_SUFFIX'" -or -not $controller.Contains('physical-corrective/') -or -not $controller.Contains("'RECOVERED_HISTORICAL_ROWS'") -or -not $controller.Contains('B60_PRIOR_RESULT_RECONCILE_BEFORE_REPEAT')){throw 'B60R_CONTROLLER'}
 foreach($file in Get-ChildItem $PSScriptRoot -Filter '*Bloco60Corrective*.ps1'){$errors=$null;$tokens=$null;$null=[Management.Automation.Language.Parser]::ParseFile($file.FullName,[ref]$tokens,[ref]$errors);if($errors.Count){throw 'B60R_POWERSHELL_SYNTAX'}}
 $semantic=Read 'target/b60-correcao-20260910/semantic-green-02/result.json'|ConvertFrom-Json
 if(-not $semantic.passed -or $semantic.results.Count -ne 3 -or @($semantic.results|Where-Object {$_.sqlOpened -or $_.aclTested -or -not $_.passed}).Count){throw 'B60R_PACKAGED_VERIFIER'}
}
$package=Read 'database/proposals/bloco60-correcao/package.json'|ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String
Validate $package;$guards=0
if($SelfTest){foreach($tuple in @(
 @('B60R_SCOPE',{param($p)$p.target='localhost/ETL_SISTEMA'}),
 @('B60R_SCOPE',{param($p)$p.approvalRecorded=$true}),
 @('B60R_SCOPE',{param($p)$p.validUntil='2026-09-17T00:00:00Z'}),
 @('B60R_LIMIT',{param($p)$p.limits.jvms=81}),
 @('B60R_LIMIT',{param($p)$p.limits.renewals=1}),
 @('B60R_PREDECESSOR',{param($p)$p.predecessorPackage='0'*64}),
 @('B60R_BASELINE',{param($p)$p.expectedRows=9137}),
 @('B60R_VERSIONS',{param($p)$p.versions.serviceBefore=17}),
 @('B60R_PATH',{param($p)$p.files[0].path='../outside'}),
 @('B60R_FILE_HASH',{param($p)$p.files[0].sha256='0'*64})
 )){$copy=$package|ConvertTo-Json -Depth 30|ConvertFrom-Json -AsHashtable -Depth 30 -DateKind String;& $tuple[1] $copy;$reason='ACCEPTED';try{Validate $copy}catch{$reason=$_.Exception.Message};if($reason -cne $tuple[0]){throw ('B60R_MUTANT_'+$tuple[0]+'_GOT_'+$reason)};$guards++}}
@{passed=$true;guards=$guards;cases=74;sqlExecuted=$false;sqlCompiled=$false;physicalQualified=$false;newAcceptances=0;package=(Get-FileHash (Join-Path $root 'database/proposals/bloco60-correcao/package.json')).Hash.ToLowerInvariant()}|ConvertTo-Json
