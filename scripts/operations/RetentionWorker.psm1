$ErrorActionPreference = 'Stop'

function Initialize-RetentionArchive {
    if (-not ('Jujuba.Retention.Archive' -as [type])) {
        Add-Type -Path (Join-Path $PSScriptRoot 'RetentionArchive.cs') -ReferencedAssemblies System.Data,System.IO.Compression,System
    }
}

function Get-RetentionArchiveDirectory {
    $base = Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos/etl-extracao-dados-v2'
    $parent = Get-Item -LiteralPath $base -ErrorAction Stop
    if ($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Diretorio de arquivo redirecionado.' }
    $path = Join-Path $parent.FullName 'retencao-ocorrencias'
    $sid = [Security.Principal.WindowsIdentity]::GetCurrent().User
    $allowed = @($sid.Value,'S-1-5-18','S-1-5-32-544')
    if (-not [IO.Directory]::Exists($path)) {
        $acl = New-Object Security.AccessControl.DirectorySecurity
        $acl.SetAccessRuleProtection($true,$false)
        foreach ($id in $allowed) {
            $principal = [Security.Principal.SecurityIdentifier]::new($id)
            $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new($principal,'FullControl','ContainerInherit,ObjectInherit','None','Allow'))
        }
        [void][IO.Directory]::CreateDirectory($path,$acl)
    }
    $dir = Get-Item -LiteralPath $path
    if ($dir.Attributes -band [IO.FileAttributes]::ReparsePoint) { throw 'Arquivo redirecionado.' }
    $security = $dir.GetAccessControl()
    foreach ($rule in $security.GetAccessRules($true,$true,[Security.Principal.SecurityIdentifier])) {
        if ($rule.AccessControlType -eq 'Allow' -and $allowed -notcontains $rule.IdentityReference.Value) { throw 'Diretorio de recuperacao sem privacidade.' }
    }
    return $dir.FullName
}

function New-RetentionCommand($Connection,$Procedure,$Transaction) {
    $cmd = $Connection.CreateCommand()
    $cmd.CommandType = [Data.CommandType]::StoredProcedure
    $cmd.CommandText = 'ctl.usp_jujuba_retencao_' + $Procedure
    $cmd.CommandTimeout = 170
    if ($Transaction) { $cmd.Transaction = $Transaction }
    return $cmd
}

function Invoke-RetentionDay {
    param($Connection,[DateTime]$Day,[string]$Directory,$Transaction)
    $prefix = $Day.ToString('yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
    $candidates = @(Get-ChildItem -LiteralPath $Directory -Filter ($prefix+'_*.jsonl.gz') -File | Where-Object {
        $_.Name -match '^\d{4}-\d{2}-\d{2}_[a-f0-9]{32}\.jsonl\.gz$' -and -not ($_.Attributes -band [IO.FileAttributes]::ReparsePoint)
    })
    # Reutiliza o arquivo se a execução caiu entre a gravação e a confirmação do banco.
    $path = $null
    if ($candidates.Count -eq 1) {
        $path = $candidates[0].FullName
        $archiveId = [guid]::ParseExact($candidates[0].Name.Substring(11,32),'N')
        $digest = [Jujuba.Retention.Archive]::Verify($path)
    } elseif ($candidates.Count -gt 1) { throw 'Ha mais de um arquivo para o dia pendente.' }
    if (-not $path) {
        $volume = [IO.DriveInfo]::new([IO.Path]::GetPathRoot($Directory))
        if ($volume.AvailableFreeSpace -lt 512MB) { throw 'Espaco insuficiente para a copia de recuperacao.' }
        $archiveId = [guid]::NewGuid()
        $path = Join-Path $Directory ($prefix+'_'+$archiveId.ToString('N')+'.jsonl.gz')
        $part = $path+'.part'
        $cmd = New-RetentionCommand $Connection 'exportar' $Transaction
        try {
            [void]$cmd.Parameters.Add('@entidade',[Data.SqlDbType]::VarChar,40)
            $cmd.Parameters['@entidade'].Value = 'OCORRENCIAS'
            [void]$cmd.Parameters.Add('@dia',[Data.SqlDbType]::Date)
            $cmd.Parameters['@dia'].Value = $Day.Date
            $reader = $cmd.ExecuteReader([Data.CommandBehavior]::SequentialAccess)
            try { $expected = [Jujuba.Retention.Archive]::Write($reader,$part) } finally { $reader.Dispose() }
            $digest = [Jujuba.Retention.Archive]::Verify($part)
            if ($digest -cne $expected) { throw 'Copia de recuperacao incompleta.' }
            [IO.File]::Move($part,$path)
        } finally {
            $cmd.Dispose()
            if ([IO.File]::Exists($part)) { [IO.File]::Delete($part) }
        }
    }
    $cmd = New-RetentionCommand $Connection 'retirar' $Transaction
    try {
        [void]$cmd.Parameters.AddWithValue('@entidade','OCORRENCIAS')
        [void]$cmd.Parameters.Add('@dia',[Data.SqlDbType]::Date)
        $cmd.Parameters['@dia'].Value = $Day.Date
        [void]$cmd.Parameters.AddWithValue('@arquivo',$archiveId)
        [void]$cmd.Parameters.AddWithValue('@assinatura',$digest)
        [void]$cmd.ExecuteNonQuery()
    } catch {
        # Apenas o erro explícito de conteúdo divergente garante que a transação foi desfeita.
        $reason = $_.Exception.GetBaseException()
        if ($reason.Number -eq 52307) { [IO.File]::Delete($path) }
        throw
    } finally { $cmd.Dispose() }
    return [pscustomobject]@{dia=$prefix;arquivo=$archiveId;assinatura=$digest;bytes=([IO.FileInfo]$path).Length}
}

function Invoke-RetentionQueue {
    param($Connection,[string]$Directory,[int]$MaxDays)
    $lock = $Connection.CreateCommand()
    try {
        $lock.CommandText = "DECLARE @r int; EXEC @r=sys.sp_getapplock @Resource=N'jujuba:retencao:executor',@LockMode='Exclusive',@LockOwner='Session',@LockTimeout=0; SELECT @r;"
        if ([int]$lock.ExecuteScalar() -lt 0) { return }
        # A trava da sessão garante que nenhum outro executor esteja escrevendo estes temporários.
        $root = [IO.Path]::GetFullPath($Directory)
        foreach ($temporary in (Get-ChildItem -LiteralPath $root -Filter '*.jsonl.gz.part' -File)) {
            if ($temporary.DirectoryName -eq $root -and $temporary.Name -match '^\d{4}-\d{2}-\d{2}_[a-f0-9]{32}\.jsonl\.gz\.part$' -and
                -not ($temporary.Attributes -band [IO.FileAttributes]::ReparsePoint)) { [IO.File]::Delete($temporary.FullName) }
        }
        for ($i=0; $i -lt $MaxDays; $i++) {
            $next = New-RetentionCommand $Connection 'proxima'
            try {
                $r = $next.ExecuteReader()
                try {
                    if (-not $r.Read()) { break }
                    if ($r.GetString(0) -cne 'OCORRENCIAS') { throw 'Entidade inesperada.' }
                    $dia = $r.GetDateTime(1)
                } finally { $r.Dispose() }
            } finally { $next.Dispose() }
            $result = Invoke-RetentionDay $Connection $dia $Directory
            Write-Output ('{0} confirmado, {1} bytes compactados.' -f $result.dia,$result.bytes)
        }
        $expired = New-RetentionCommand $Connection 'arquivos_vencidos'
        try {
            $r = $expired.ExecuteReader()
            try {
                while ($r.Read()) {
                    $name = $r.GetDateTime(1).ToString('yyyy-MM-dd')+'_'+$r.GetGuid(0).ToString('N')+'.jsonl.gz'
                    $target = Join-Path $Directory $name
                    if ([IO.File]::Exists($target)) { [IO.File]::Delete($target) }
                }
            } finally { $r.Dispose() }
        } finally { $expired.Dispose() }
    } finally {
        $lock.CommandText = "IF APPLOCK_MODE('public','jujuba:retencao:executor','Session')='Exclusive' EXEC sys.sp_releaseapplock @Resource=N'jujuba:retencao:executor',@LockOwner='Session';"
        try { [void]$lock.ExecuteNonQuery() } finally { $lock.Dispose() }
    }
}
Export-ModuleMember -Function Initialize-RetentionArchive,Get-RetentionArchiveDirectory,Invoke-RetentionDay,Invoke-RetentionQueue
