#Requires -Version 7.0
# Isolated evidence writer regression. No SQL connection, process, mapping or ledger mutation.
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$encoding=[Text.UTF8Encoding]::new($false,$true)
$evidence=Join-Path $root ('target/bloco54/sql-evidence-selftest-'+[guid]::NewGuid().ToString('N'))
[void][IO.Directory]::CreateDirectory($evidence)
$errors=$null
$ast=[Management.Automation.Language.Parser]::ParseFile((Join-Path $PSScriptRoot 'Invoke-Bloco54AuthorityMatrix.ps1'),[ref]$null,[ref]$errors)
if($errors.Count){throw 'MATRIX_PARSER_FAILED'}
$writer=@($ast.FindAll({param($node) $node -is [Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -ceq 'Invoke-LocalSql'},$true))
if($writer.Count -ne 1){throw 'EXACT_EVIDENCE_WRITER_REQUIRED'}
. ([scriptblock]::Create($writer[0].Extent.Text))
$script:probeExit=0
$script:probeLines=[string[]]@()
function sqlcmd { $script:LASTEXITCODE=$script:probeExit; $script:probeLines }
try {
    $returned=Invoke-LocalSql 'empty-success' 'PRINT N'''';'
    if($returned.Count -ne 0 -or (Get-Item -LiteralPath (Join-Path $evidence 'empty-success.sql.log')).Length -ne 0){throw 'EMPTY_SQL_SUCCESS_NOT_RETAINED'}
    $script:probeLines=[string[]]@('ação confirmada','SECOND_RECEIPT')
    $returned=Invoke-LocalSql 'unicode-success' 'SELECT 1;'
    if(($returned -join '|') -cne 'ação confirmada|SECOND_RECEIPT'){throw 'SQL_OUTPUT_CHANGED'}
    $written=[IO.File]::ReadAllLines((Join-Path $evidence 'unicode-success.sql.log'),$encoding)
    if(($written -join '|') -cne ($returned -join '|')){throw 'SQL_LOG_ENCODING_CHANGED'}
    $script:probeExit=17
    $script:probeLines=[string[]]@()
    $denied=$false
    try {Invoke-LocalSql 'empty-failure' 'SELECT 1;'|Out-Null}
    catch {if($_.Exception.Message -cne 'SQL_FAILED_empty-failure'){throw};$denied=$true}
    if(-not $denied -or -not (Test-Path -LiteralPath (Join-Path $evidence 'empty-failure.sql.log'))){throw 'SQL_FAILURE_NOT_PRESERVED'}
    'B54_SQL_EVIDENCE_SELFTEST_3_PASS_NO_DATABASE'
} finally {Remove-Item -LiteralPath Function:\sqlcmd}
