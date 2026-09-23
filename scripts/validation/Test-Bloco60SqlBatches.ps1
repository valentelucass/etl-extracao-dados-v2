#Requires -Version 7.5
param([string]$SqlPath='database/proposals/bloco60-continuacao/activate.sql',[switch]$ExpectDuplicate)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$working=Join-Path $root 'database/proposals/bloco60-correcao'
$dom='C:\Program Files\Microsoft SQL Server Management Studio 22\Release\Common7\IDE\Extensions\Application\Microsoft.SqlServer.TransactSql.ScriptDom.dll'
[void][Reflection.Assembly]::LoadFrom($dom)
function Expand([string]$Path,[int]$Depth=0){
 if($Depth -gt 8){throw 'B60N_INCLUDE_DEPTH'}
 $text=[IO.File]::ReadAllText($Path)
 $text=[regex]::Replace($text,'(?m)^:r "([^"]+)"\s*$',{param($m) Expand (Join-Path $working $m.Groups[1].Value) ($Depth+1)})
 [regex]::Replace($text,'(?m)^:On Error exit\s*$','')
}
$expanded=Expand (Join-Path $root $SqlPath)
$parser=[Microsoft.SqlServer.TransactSql.ScriptDom.TSql160Parser]::new($true)
$errors=$null;$reader=[IO.StringReader]::new($expanded)
try{$fragment=$parser.Parse($reader,[ref]$errors)}finally{$reader.Dispose()}
if($errors.Count){throw ('B60N_SQL_PARSE_'+($errors|ForEach-Object Number)-join '_')}
$duplicates=@()
foreach($batch in $fragment.Batches){
 $names=[Collections.Generic.HashSet[string]]::new([StringComparer]::OrdinalIgnoreCase)
 foreach($statement in $batch.Statements){
  $variables=switch($statement.GetType().Name){
   'DeclareVariableStatement' {@($statement.Declarations|ForEach-Object {$_.VariableName.Value})}
   'DeclareTableVariableStatement' {@($statement.Body.VariableName.Value)}
   default {@()}
  }
  foreach($name in $variables){if(-not $names.Add($name)){$duplicates+= $name}}
 }
}
if($ExpectDuplicate -and $duplicates.Count -lt 2 -or -not $ExpectDuplicate -and $duplicates.Count){throw 'B60N_SQL_BATCH_DECLARATIONS'}
@{passed=$true;sqlExecuted=$false;parser='ScriptDom160';batches=$fragment.Batches.Count;duplicates=@($duplicates);expectedDuplicate=[bool]$ExpectDuplicate;path=$SqlPath;parserSha256=(Get-FileHash $dom).Hash.ToLowerInvariant()}|ConvertTo-Json
