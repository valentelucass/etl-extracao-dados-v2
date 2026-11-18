param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$metadata=(& (Join-Path $PSScriptRoot 'Prepare-ManifestosOfflineUnit.ps1') -OutputDirectory $OutputDirectory)|ConvertFrom-Json -AsHashtable
$unit=Get-Content -Raw -LiteralPath $metadata.unit.path|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -Raw -LiteralPath $metadata.manifestCandidate.path|ConvertFrom-Json -AsHashtable
foreach($relative in @('src/main/resources/contracts/dataexport/cotacoes-6906-observed-p2-info-qualified-20261025.json','src/main/resources/contracts/dataexport/cotacoes-native37-v1.json','src/main/resources/contracts/dataexport/cotacoes-6906-observed-p3-offline-supplement-20261107.json','scripts/validation/Test-CotacoesWireScope.ps1','scripts/validation/Prepare-CotacoesOfflineUnit.ps1')){
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'COTACOES_INPUT_SCOPE'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'COTACOES_INPUT_LINK'}}
 $item=Get-Item -LiteralPath $path
 if($item.Length-le0-or$item.Length-gt262144-or$unit.controlFiles-ccontains$relative){throw 'COTACOES_EXACT_CONTROL'}
 $unit.controlFiles+=@($relative);$unit.inputPins+=@(@{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()})
}
$unit.counts.controls=$unit.controlFiles.Count;$unit.counts.inputs=$unit.inputPins.Count
$unit.revision='COTACOES_6906_AND_SHARED_RECONCILIATION_V1';$unit.scopeNote='Exact Cotações code/tests plus all current shared reconciliation regression tests. No Source or production authorization.'
function Publish([string]$name,$value){
 $path=Join-Path $OutputDirectory $name;$bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine)
 $stream=[IO.File]::Open($path,'CreateNew','Write','Read');try{$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
 return @{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
}
$unitPin=Publish 'cotacoes-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin;$manifest.controlFiles=$unit.controlFiles;$manifest.counts=$unit.counts
$manifest.note='Candidate metadata only. Current Root must admit exact build; Source, HTTP and SQL remain zero.'
$manifestPin=Publish 'cotacoes-root-manifest.CANDIDATE.json' $manifest
@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;OriginalInvocations=0}|ConvertTo-Json -Depth 8
