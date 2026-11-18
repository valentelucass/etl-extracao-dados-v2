param([Parameter(Mandatory)][string]$OutputDirectory)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
# Metadata preparation only. No Java/Maven/Python, source data, JAR/classes, network or SQL.
$repo=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$actor=$env:CODEX_THREAD_ID
$parsedActor=[guid]::Empty
if(-not[guid]::TryParse($actor,[ref]$parsedActor)){throw 'REAL_CURRENT_CHAT_ID_REQUIRED'}
$metadata=(& (Join-Path $PSScriptRoot 'Prepare-IndependentExportFinalUnit.ps1') -OutputDirectory $OutputDirectory)|ConvertFrom-Json -AsHashtable
$unit=Get-Content -Raw -LiteralPath $metadata.unit.path|ConvertFrom-Json -AsHashtable
$manifest=Get-Content -Raw -LiteralPath $metadata.manifestCandidate.path|ConvertFrom-Json -AsHashtable
$added=@(
 'src/main/resources/contracts/dataexport/manifestos-native91-v1.json',
 'src/main/resources/contracts/dataexport/manifestos-6399-observed-p2-info-qualified-20261025.json',
 'scripts/validation/Prepare-ManifestosOfflineUnit.ps1',
 'scripts/validation/Test-ManifestosWireScope.ps1'
)
foreach($relative in $added){
 $path=[IO.Path]::GetFullPath((Join-Path $repo $relative))
 if(-not$path.StartsWith($repo+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'MANIFESTOS_INPUT_SCOPE'}
 for($at=$path;$at;$at=[IO.Path]::GetDirectoryName($at)){if(([IO.File]::GetAttributes($at)-band[IO.FileAttributes]::ReparsePoint)-ne0){throw 'MANIFESTOS_INPUT_LINK_REFUSED'}}
 $item=Get-Item -LiteralPath $path
 if($item.Length-le0-or$item.Length-gt262144){throw 'MANIFESTOS_METADATA_INPUT_BOUND'}
 if($unit.controlFiles-ccontains$relative){throw 'MANIFESTOS_DUPLICATE_CONTROL'}
 $unit.controlFiles+=@($relative)
 $unit.inputPins+=@([ordered]@{file=$relative;path=$path;bytes=$item.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant()})
}
$unit.counts.controls=$unit.controlFiles.Count
$unit.counts.inputs=$unit.inputPins.Count
$unit.revision='MANIFESTOS_6399_CODE_AND_SHARED_READER_SOURCE0_V1'
$unit.scopeNote='Exact final shared reconciliation suite plus explicit Manifestos code/tests; no historical 131/replay/Source/SQL claim.'
$unit.executingRootUUID=$actor
$unit.authorityCreated=$false
$unitPin=$null
function Publish-Metadata([string]$name,$value){
 $path=Join-Path $OutputDirectory $name
 $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($value|ConvertTo-Json -Depth 100)+[Environment]::NewLine)
 $stream=[IO.File]::Open($path,'CreateNew','Write','Read')
 try{$stream.Write($bytes);$stream.Flush($true)}finally{$stream.Dispose()}
 return [ordered]@{path=$path;bytes=$bytes.Length;sha256=(Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash}
}
$unitPin=Publish-Metadata 'manifestos-unit.PREPARED.json' $unit
$manifest.sourceUnit=$unitPin
$manifest.ownerRootUUID=$actor
$manifest.predecessorResourceOwnerRootUUID='01a17ee4-5f83-7051-9cec-ffc8e16642fe'
$manifest.controlFiles=$unit.controlFiles
$manifest.counts=$unit.counts
$manifest.note='Candidate metadata only. Current executor is explicit; no approval, source authority or resource ownership is granted.'
$manifestPin=Publish-Metadata 'manifestos-root-manifest.CANDIDATE.json' $manifest
[ordered]@{unit=$unitPin;manifestCandidate=$manifestPin;counts=$unit.counts;Source=0;HTTP=0;SQL=0;testsExecuted=0;OriginalInvocations=0}|ConvertTo-Json -Depth 8
