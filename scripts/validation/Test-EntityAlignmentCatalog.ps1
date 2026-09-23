#Requires -Version 7.5
param([switch]$SelfTest)
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$catalog=Join-Path $root 'docs/catalogos/alinhamento-entidades'
$utf8=[Text.UTF8Encoding]::new($false,$true)
$java=[IO.File]::ReadAllText((Join-Path $root 'src/main/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/DataExportTemplate.java'),$utf8)
function Require([bool]$condition,[string]$reason){if(-not $condition){throw $reason}}
function Check($m){
    Require ($m.version -eq 1 -and $m.sourceCalls -eq 0 -and $m.realAcceptance -ceq $false) 'ENTITY_SCOPE'
    Require (($m.entities.id|Sort-Object)-join ',' -ceq 'CAP,COL,COT,FAT,FRE,INV,LOC,MAN,RAS,SIN,USER') 'ENTITY_UNIVERSE'
    foreach($e in $m.entities){
        Require ($e.currentApiExecution -ceq 'BLOCKED_V2_041') 'ENTITY_API_HOLD'
        foreach($p in @($e.extractor,$e.consumer,$e.proof,$e.contract)){
            Require ($p -cmatch '^(src/(main|test)/java/|docs/catalogos/)[A-Za-z0-9_./-]+$' -and -not $p.Contains('..')) 'ENTITY_PATH'
            Require (Test-Path -LiteralPath (Join-Path $root $p) -PathType Leaf) 'ENTITY_LINK'
        }
        if($e.source -ceq 'DATA_EXPORT'){
            $pattern='(?s)\b'+[regex]::Escape($e.template)+'\(\s*"[^"]+",\s*(\d+),\s*new SearchPath\("([^"]+)", "([^"]+)"\),\s*"[^"]+",\s*(null|new SearchPath\("[^"]+", "[^"]+"\)),\s*"([^"]+)",\s*List.of\("([^"]+)"\),\s*(\d+)\)'
            $match=[regex]::Match($java,$pattern)
            Require $match.Success 'ENTITY_RUNTIME_TEMPLATE'
            Require ($e.templateId -eq [int]$match.Groups[1].Value -and $e.businessFilter -ceq ($match.Groups[2].Value+'.'+$match.Groups[3].Value) -and $e.paginationEntityField -ceq $match.Groups[5].Value -and $e.orderBy -ceq $match.Groups[6].Value -and $e.pageSize -eq [int]$match.Groups[7].Value) 'ENTITY_RUNTIME_DRIFT'
            $updated=if($match.Groups[4].Value -ceq 'null'){$null}else{'scopes.by_updated_at'}
            Require ($e.updatedAtFilter -ceq $updated) 'ENTITY_UPDATED_FILTER'
            $scope=if($e.templateId -in @(6908,6389,4924)){'EXISTING_PROBE_SCOPE_ONLY'}else{'SEPARATE_SCOPE_REQUIRED'}
            Require ($e.afterCredentialHold -ceq $scope) 'ENTITY_API_SCOPE'
        }elseif($e.source -ceq 'GRAPHQL'){
            Require ($e.operation -ceq 'USERS_SNAPSHOT' -and $e.root -ceq 'individual' -and $e.enabled -ceq $true -and $e.pageSize -eq 20 -and $null -eq $e.sourceTemporalFilter -and ($e.executionModes -join ',') -ceq 'BACKFILL,REPLAY') 'ENTITY_USERS_DECISION'
        }elseif($e.source -ceq 'RASTER'){
            Require ($e.rootCandidate -ceq 'CodSolicitacao' -and $e.childCandidate -ceq 'Ordem' -and $e.positionalIdentityAllowed -ceq $false -and $e.afterCredentialHold -ceq 'SEPARATE_SCOPE_REQUIRED') 'ENTITY_RASTER_IDENTITY'
        }else{throw 'ENTITY_SOURCE'}
    }
}
$manifest=Get-Content -Raw -LiteralPath (Join-Path $catalog 'entidades.json')|ConvertFrom-Json -AsHashtable
Check $manifest
$expected=@{}
foreach($part in 1..3){
    $name='docs/catalogos/macrobloco-integracao-funcional/rastreabilidade-2437-campos-{0:D3}.json' -f $part
    $data=Get-Content -Raw -LiteralPath (Join-Path $root $name)|ConvertFrom-Json -AsHashtable
    foreach($row in $data.rows){Require (-not $expected.ContainsKey($row.matrixId)) 'ENTITY_DUPLICATE_MATRIX';$expected.Add($row.matrixId,$row)}
}
$csv=@(Import-Csv -LiteralPath (Join-Path $catalog 'campos.csv'))
Require ($expected.Count -eq 2437 -and $csv.Count -eq 2437) 'ENTITY_FIELD_UNIVERSE'
$seen=[Collections.Generic.HashSet[string]]::new([StringComparer]::Ordinal)
foreach($field in $csv){
    Require ($seen.Add($field.matrixId) -and $expected.ContainsKey($field.matrixId)) 'ENTITY_FIELD_ID'
    $r=$expected[$field.matrixId];$o=$r.original
    $target=if($null -ne $r.audit.local -and $r.audit.local.ContainsKey('mapping') -and $r.audit.local.mapping.ContainsKey('destination')){[string]$r.audit.local.mapping.destination}else{''}
    $pairs=@(@($field.rowKind,$o.row_kind),@($field.entity,$o.entity),@($field.sourceDocument,$o.template_or_document),@($field.sourcePath,$o.source_path),@($field.sourceType,$o.source_type),@($field.legacyDto,$o.dto_field),@($field.declaredTarget,$o.v2_target),@($field.implementedTarget,$target),@($field.classification,$r.audit.state),@($field.gate,$r.audit.gate))
    foreach($pair in $pairs){Require ([string]$pair[0] -ceq [string]$pair[1]) ('ENTITY_FIELD_RECLASSIFIED_'+$field.matrixId)}
}
$guards=0
if($SelfTest){
    foreach($test in @(
        @('ENTITY_API_HOLD',{param($m)$m.entities[0].currentApiExecution='ALLOWED'}),
        @('ENTITY_RUNTIME_DRIFT',{param($m)$m.entities[1].paginationEntityField='corporation_sequence_number'}),
        @('ENTITY_UPDATED_FILTER',{param($m)$m.entities[2].updatedAtFilter='scopes.by_updated_at'}),
        @('ENTITY_API_SCOPE',{param($m)$m.entities[5].afterCredentialHold='EXISTING_PROBE_SCOPE_ONLY'}),
        @('ENTITY_USERS_DECISION',{param($m)$m.entities[9].executionModes=@('INCREMENTAL')}),
        @('ENTITY_RASTER_IDENTITY',{param($m)$m.entities[10].positionalIdentityAllowed=$true})
    )){
        $copy=$manifest|ConvertTo-Json -Depth 12|ConvertFrom-Json -AsHashtable
        & $test[1] $copy
        $reason='ACCEPTED';try{Check $copy}catch{$reason=$_.Exception.Message}
        Require ($reason -ceq $test[0]) ('ENTITY_GUARD_'+$reason);$guards++
    }
}
[ordered]@{status='PASS';entities=11;fields=$csv.Count;reclassifications=0;sourceCalls=0;guards=$guards}|ConvertTo-Json
