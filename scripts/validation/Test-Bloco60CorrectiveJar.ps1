#Requires -Version 7.5
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$out=Join-Path $root 'target/b60-correcao-20260910/semantic-green-02'
if(Test-Path $out){throw 'B60R_SEMANTIC_EVIDENCE_EXISTS'}
$java='C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin'
$classes=Join-Path $out 'probe-classes';[void][IO.Directory]::CreateDirectory($classes)
$variants=Get-Content (Join-Path $root 'target/b60-correcao-20260910/bundle-v2/variants.json') -Raw|ConvertFrom-Json
$admin=Join-Path $root $variants[0].path
& (Join-Path $java 'javac.exe') -cp ((Join-Path $admin 'etl-dataexport-v2.jar')+';'+(Join-Path $admin 'lib/*')) -d $classes (Join-Path $root 'src/test/java/br/com/esl/etl/v2/plataforma/autorizacao/AdministeredBundleOfflineProbe.java') *> (Join-Path $out 'javac.log')
if($LASTEXITCODE -ne 0){throw 'B60R_SEMANTIC_COMPILE'}
$results=@()
foreach($variant in $variants|Where-Object variant -CNE 'swapped'){
 $bundle=Join-Path $out ('app-bloco60/'+$variant.manifest.Substring(0,16));[void][IO.Directory]::CreateDirectory($bundle)
 foreach($file in Get-ChildItem (Join-Path $root $variant.path) -Recurse -File){$relative=[IO.Path]::GetRelativePath((Join-Path $root $variant.path),$file.FullName);$dest=Join-Path $bundle $relative;[void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($dest));[IO.File]::Copy($file.FullName,$dest,$false)}
 $jar=Join-Path $bundle 'etl-dataexport-v2.jar';$log=Join-Path $out ($variant.variant+'.log')
 & (Join-Path $java 'java.exe') -Xmx512m -cp ($classes+';'+$jar+';'+(Join-Path $bundle 'lib/*')) br.com.esl.etl.v2.plataforma.autorizacao.AdministeredBundleOfflineProbe $jar '2026-09-10T00:00:00Z' *> $log
 $exit=$LASTEXITCODE;$text=(Get-Content $log -Raw).Trim()
 $expected=switch($variant.variant){'administered'{'OFFLINE_BUNDLE_MANIFEST_AND_AUTHORITY_ACCEPTED_SQL_NOT_OPENED'};'expired'{'ADMINISTERED_MANIFEST_SCOPE_REJECTED'};'inconsistent'{'ADMINISTERED_ARTIFACT_HASH_REJECTED'}}
 $ok=$exit -eq $(if($variant.variant -ceq 'administered'){0}else{12}) -and $text -ceq $expected
 $results+=@{variant=$variant.variant;passed=$ok;exit=$exit;output=$text;expected=$expected;manifest=$variant.manifest;jarSha256=(Get-FileHash $jar).Hash.ToLowerInvariant();sqlOpened=$false;aclTested=$false}
}
$report=@{passed=@($results|Where-Object passed -NE $true).Count -eq 0;results=$results;swappedAuthorityRequiresPhysicalSql=$true;newAcceptances=0}
$report|ConvertTo-Json -Depth 8|Set-Content (Join-Path $out 'result.json') -Encoding utf8NoBOM
$report|ConvertTo-Json -Depth 8
if(-not $report.passed){exit 1}
