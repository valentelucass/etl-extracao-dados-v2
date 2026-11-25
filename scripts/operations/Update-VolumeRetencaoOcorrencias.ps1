[CmdletBinding()]
param([switch]$Aplicar)
$ErrorActionPreference = 'Stop'
$repo = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
Import-Module (Join-Path $repo '../.runtime/sql-server/SqlServerProjetos.psm1')
Import-Module (Join-Path $PSScriptRoot 'RetentionWorker.psm1')
Initialize-RetentionArchive
$dir = Get-RetentionArchiveDirectory
$cred = Get-ProjetosSqlAdminCredential
$con = New-ProjetosSqlConnection -Database ETL_SISTEMA_V2 -Credential $cred -ApplicationName 'ETL-Retencao-Volume-Historico'
try {
    $con.Open()
    $cmd = $con.CreateCommand()
    $cmd.CommandText = @'
SELECT dia,arquivo,assinatura,origem,publicadas,coberturas,marcadores FROM ctl.jujuba_retencao_dia
WHERE entidade='OCORRENCIAS' AND bytes_apagados IS NULL ORDER BY dia;
'@
    $r = $cmd.ExecuteReader()
    try {
        $dias = @(while ($r.Read()) {
            [pscustomobject]@{dia=$r.GetDateTime(0);arquivo=$r.GetGuid(1);assinatura=$r.GetString(2);
                origem=$r.GetInt64(3);publicadas=$r.GetInt64(4);coberturas=$r.GetInt32(5);marcadores=$r.GetInt32(6)}
        })
    } finally { $r.Dispose() }
    $cmd.CommandText = @'
SET XACT_ABORT ON; SET LOCK_TIMEOUT 3500;
UPDATE ctl.jujuba_retencao_dia SET bytes_apagados=@bytes
WHERE entidade='OCORRENCIAS' AND dia=@dia AND arquivo=@arquivo AND assinatura=@assinatura AND bytes_apagados IS NULL;
IF @@ROWCOUNT=0 AND NOT EXISTS(SELECT 1 FROM ctl.jujuba_retencao_dia WHERE entidade='OCORRENCIAS' AND dia=@dia
 AND arquivo=@arquivo AND assinatura=@assinatura AND bytes_apagados=@bytes) THROW 52308,'Auditoria alterada durante a medicao.',1;
'@
    [void]$cmd.Parameters.Add('@bytes',[Data.SqlDbType]::BigInt)
    [void]$cmd.Parameters.Add('@dia',[Data.SqlDbType]::Date)
    [void]$cmd.Parameters.Add('@arquivo',[Data.SqlDbType]::UniqueIdentifier)
    [void]$cmd.Parameters.Add('@assinatura',[Data.SqlDbType]::Char,64)
    $medidos=0; $ausentes=0; $bytes=0L
    foreach ($dia in $dias) {
        $nome = $dia.dia.ToString('yyyy-MM-dd')+'_'+$dia.arquivo.ToString('N')+'.jsonl.gz'
        $arquivo = Join-Path $dir $nome
        if (-not [IO.File]::Exists($arquivo)) { $ausentes++; continue }
        if ([IO.File]::GetAttributes($arquivo) -band [IO.FileAttributes]::ReparsePoint) {throw 'Arquivo redirecionado.'}
        $medida = Measure-RetentionArchive $con $arquivo
        if ($medida.assinatura -cne $dia.assinatura -or $medida.origem -ne $dia.origem -or
            $medida.publicacao -ne $dia.publicadas -or $medida.cobertura -ne $dia.coberturas -or $medida.marcador -ne $dia.marcadores) {
            throw 'O arquivo e a auditoria da retirada divergem.'
        }
        if ($Aplicar) {
            $cmd.Parameters['@bytes'].Value = $medida.bytes
            $cmd.Parameters['@dia'].Value = $dia.dia
            $cmd.Parameters['@arquivo'].Value = $dia.arquivo
            $cmd.Parameters['@assinatura'].Value = $dia.assinatura
            [void]$cmd.ExecuteNonQuery()
        }
        $bytes += $medida.bytes
        $medidos++
    }
    [pscustomobject]@{aplicado=[bool]$Aplicar;diasMedidos=$medidos;arquivosAusentes=$ausentes;bytes=$bytes}
} finally {
    if ($cmd) { $cmd.Dispose() }
    $con.Dispose(); $cred.Password.Dispose()
}
