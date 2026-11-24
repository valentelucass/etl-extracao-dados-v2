[CmdletBinding()]
param([switch]$Aplicar)
$ErrorActionPreference='Stop'
$repo=(Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1') -DisableNameChecking
$fonte='database/production/publication/071_dashboard_vedacit_filial_faturamento.sql'
$passo='071-dashboard-vedacit-filial-faturamento'
$sql=[IO.File]::ReadAllText((Join-Path $repo $fonte)).Replace([string][char]13,'')
$sha=[Security.Cryptography.SHA256]::Create()
try {$hash=[BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($sql))).Replace('-','')}
finally {$sha.Dispose()}
$conn=New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential (Get-ProjetosSqlAdminCredential) -ApplicationName 'ETL-Faturamento-Vedacit'
try {
    $conn.Open()
    $tx=$conn.BeginTransaction()
    $cmd=$conn.CreateCommand()
    $cmd.Transaction=$tx
    $cmd.CommandTimeout=180
    $null=$cmd.Parameters.AddWithValue('@fonte',$fonte)
    $null=$cmd.Parameters.AddWithValue('@passo',$passo)
    $null=$cmd.Parameters.AddWithValue('@hash',$hash)
    $cmd.CommandText=@'
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET LOCK_TIMEOUT 5000;
SET DEADLOCK_PRIORITY LOW;
SET ANSI_NULLS, QUOTED_IDENTIFIER, ANSI_PADDING, ANSI_WARNINGS, CONCAT_NULL_YIELDS_NULL, ARITHABORT ON;
SET NUMERIC_ROUNDABORT OFF;
DECLARE @lock int;
EXEC @lock=sys.sp_getapplock @Resource=N'dashboard:fatos-fretes',@LockMode='Exclusive',@LockOwner='Transaction',@LockTimeout=5000;
IF @lock<0 THROW 52275,'Publicacao regular de fretes em andamento.',1;
SELECT CASE WHEN source_name=@fonte THEN source_sha256 ELSE 'DIVERGENTE' END FROM ctl.production_installation_step WHERE step_id=@passo;
'@
    $registro=$cmd.ExecuteScalar()
    if($registro -and $registro -ine $hash){throw 'Assinatura da regra de faturamento divergente.'}
    $conciliacao=@'
SELECT pagador_documento_key,COUNT_BIG(*) fretes,SUM(receita_bruta) receita
FROM core.dashboard_faturamento_leitura WHERE pagador_documento_key LIKE N'60642774%'
GROUP BY pagador_documento_key ORDER BY pagador_documento_key FOR JSON PATH;
'@
    $cmd.CommandText=$conciliacao
    $antes=$cmd.ExecuteScalar()
    if(!$registro){
        $cmd.CommandText=$sql
        $null=$cmd.ExecuteNonQuery()
        $cmd.CommandText='INSERT ctl.production_installation_step(step_id,source_name,source_sha256) VALUES(@passo,@fonte,@hash)'
        $null=$cmd.ExecuteNonQuery()
    }
    $cmd.CommandText=@'
IF EXISTS (SELECT 1 FROM pub.fato_fretes_faturamento_preparada
    WHERE pagador_documento_key=N'60642774001209' AND
    (COALESCE(filial_nome,'')<>N'SPO - RODOGARCIA TRANSPORTES RODOVIARIOS LTDA' OR COALESCE(filial_key,'')<>LOWER(filial_nome)
    OR COALESCE(filial_apelido,'')<>filial_nome OR COALESCE(filial_cnpj,'')<>N'60960473000243' OR filial_id IS NULL))
 THROW 52276,'Divergencia na conciliacao do faturamento.',1;
SELECT COUNT_BIG(*) FROM pub.fato_fretes_faturamento_preparada WHERE pagador_documento_key=N'60642774001209';
'@
    $quantidade=$cmd.ExecuteScalar()
    $cmd.CommandText=$conciliacao
    if($cmd.ExecuteScalar() -cne $antes){throw 'Totais de fretes ou receita por CNPJ divergiram.'}
    if($Aplicar){$tx.Commit()}else{$tx.Rollback()}
    [pscustomobject]@{Aplicado=[bool]$Aplicar;Fretes=$quantidade;Passo=$passo}
} finally {$conn.Dispose()}
