param([ValidateSet('positive','dependency-negative')][string]$Example='positive')
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'));$utf8=[Text.UTF8Encoding]::new($false,$true)
$directory=Join-Path $root ('target/bloco55/manual/'+$Example)
if(Test-Path $directory){throw 'FROZEN_MANUAL_EXAMPLE_EXISTS'}
[void][IO.Directory]::CreateDirectory((Join-Path $directory 'requests'))
Import-Module (Join-Path $PSScriptRoot 'Bloco55Artifact.psm1') -Force
$review=Read-Bloco55Bundle (Join-Path $root 'target/bloco55/reviewed-bundle-v4') 'a586b69ca27ade4082c363186067e3213db455398090f594f229960df5032c13'
$files=[Collections.Generic.List[object]]::new();$days=if($Example -ceq 'positive'){4}else{1}
for($day=1;$day -le $days;$day++){
 $date=([DateTime]::ParseExact($(if($Example -ceq 'positive'){'2032-07-01'}else{'2032-08-01'}),'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)).AddDays($day-1)
 $coleta=$null
 foreach($template in @('COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS')){
  $q=@($review.Manifest.quality|Where-Object {$_.template -ceq $template -and $_.mode -ceq 'BACKFILL'})
  if($q.Count -ne 1){throw 'MANUAL_EXACT_DQ'}
  $civil=$date.ToString('yyyy-MM-dd');$end=$date.AddDays(1).ToString('yyyy-MM-dd')
  $r=[ordered]@{invocationId=[guid]::NewGuid().ToString();executionId=[guid]::NewGuid().ToString();cycleId=[guid]::NewGuid().ToString();template=$template;mode='BACKFILL';start=$civil+'T03:00:00Z';endExclusive=$end+'T03:00:00Z';replayOf='';idempotencyKey=[guid]::NewGuid().ToString();businessStart=$civil;businessEnd=$civil;leaseSeconds='30';pageSize='2';maximumPages='4';maximumRows='16';maximumDistinctRoots='16';qualityVersion=$q[0].version;qualityFingerprint=$q[0].fingerprint;compatibilityVersion='bloco55-compatible-v1'}
  if($template -ceq 'COLETAS'){$coleta=$r|ConvertTo-Json -Depth 5 -Compress}
  if($template -ceq 'FRETES'){$r.dependencyRequest=$coleta}
  if($template -ceq 'COTACOES'){$r.referenceReleaseId='3'}
  $relative='requests/W'+$day+'_'+$template+'.json';$file=Join-Path $directory $relative
  [IO.File]::WriteAllText($file,($r|ConvertTo-Json -Depth 5),$utf8)
  $files.Add([ordered]@{path=$relative;sha256=(Get-FileHash $file).Hash.ToLowerInvariant()})
 }
}
$config=[IO.File]::ReadAllText((Join-Path $review.Path 'runtime.properties')).Replace('http://127.0.0.1:1','http://127.0.0.1:62129')
$hash=[Convert]::ToHexString([Security.Cryptography.SHA256]::HashData($utf8.GetBytes($config))).ToLowerInvariant()
$manifest=[ordered]@{version='bloco55-manual-v1';database='localhost/ETL_SISTEMA_V2_SHADOW';artifactManifestSha256=$review.Hash;configurationSha256=$hash;referenceReleaseId='3';failurePolicy='CONTINUE_INDEPENDENT_STOP_ON_PREFLIGHT_INTEGRITY_LIMIT';requests=$files}
$file=Join-Path $directory 'manifest.json';[IO.File]::WriteAllText($file,($manifest|ConvertTo-Json -Depth 6),$utf8)
'MANUAL_MANIFEST_SHA256='+(Get-FileHash $file).Hash.ToLowerInvariant()
