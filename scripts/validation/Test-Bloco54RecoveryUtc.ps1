#Requires -Version 7.0
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$file=Join-Path $PSScriptRoot 'Restore-Bloco54HarnessMapping.ps1'
$tokens=$null;$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile($file,[ref]$tokens,[ref]$errors)
if($errors.Count){throw 'RECOVERY_SCRIPT_PARSE_FAILURE'}
$assignments=@($ast.FindAll({param($node) $node -is [Management.Automation.Language.AssignmentStatementAst] -and $node.Left.Extent.Text -in @('$originalUntilUtc','$until')},$true))
if($assignments.Count -ne 2){throw 'EXACT_RECOVERY_UTC_ASSIGNMENTS_REQUIRED'}
$parse=[scriptblock]::Create(($assignments.Extent.Text -join "`n"))
$priorCulture=[Globalization.CultureInfo]::CurrentCulture
try {
    foreach($culture in @('pt-BR','en-US')){
        [Globalization.CultureInfo]::CurrentCulture=[Globalization.CultureInfo]::GetCultureInfo($culture)
        $contract=[pscustomobject]@{originalUntil='2026-10-07T22:34:30.615'}
        . $parse
        if($originalUntilUtc.Kind -ne [DateTimeKind]::Utc -or $until -cne $contract.originalUntil -or $originalUntilUtc.ToString('o') -cne '2026-10-07T22:34:30.6150000Z'){throw 'UTC_ORIGINAL_EXPIRY_CHANGED'}
    }
    foreach($invalid in @('07/10/2026 22:34:30.615','2026-10-07T22:34:30.615-03:00','')){
        $contract=[pscustomobject]@{originalUntil=$invalid}
        $rejected=$false
        try {. $parse}catch{$rejected=$true}
        if(-not $rejected){throw 'AMBIGUOUS_RECOVERY_DATE_ACCEPTED'}
    }
}finally{[Globalization.CultureInfo]::CurrentCulture=$priorCulture}
'B54_RECOVERY_UTC_5_CASES_PASS_NO_DATABASE_NO_RESERVATION'
