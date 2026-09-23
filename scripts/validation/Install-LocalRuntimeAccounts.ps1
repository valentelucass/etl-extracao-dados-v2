#Requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][string]$PackageDirectory,
    [Parameter(Mandatory)][ValidatePattern('^[a-f0-9]{64}$')][string]$ManifestSha256,
    [switch]$Apply,
    [switch]$ResumeBeforeAccountCreation,
    [switch]$ResumeInstalledAccounts
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$encoding = [Text.UTF8Encoding]::new($false, $true)
$repository = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$package = [IO.Path]::GetFullPath($PackageDirectory)
$packagePrefix = [IO.Path]::GetFullPath((Join-Path $repository 'target/bloco53')).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
if (-not $package.StartsWith($packagePrefix, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'PACKAGE_MUST_STAY_IN_BLOCK53_TARGET'
}
$manifestPath = Join-Path $package 'installation-manifest.json'
if ((Get-FileHash -LiteralPath $manifestPath -Algorithm SHA256).Hash.ToLowerInvariant() -cne $ManifestSha256) {
    throw 'REVIEWED_MANIFEST_CHANGED'
}
$manifest = [IO.File]::ReadAllText($manifestPath, $encoding) | ConvertFrom-Json -DateKind String
if ($manifest.version -ne 1 -or $manifest.database -cne 'ETL_SISTEMA_V2_SHADOW' -or
    $manifest.host -cne 'localhost' -or $manifest.accounts.Count -ne 2 -or
    $manifest.accounts[0] -cne 'etl_v2_exec' -or $manifest.accounts[1] -cne 'etl_v2_view' -or
    $manifest.procedureGrants -ne 22 -or $manifest.accountValidityDays -ne 30 -or
    $manifest.files.Count -gt 32 -or $manifest.files.Count -lt 15) {
    throw 'LOCAL_ACCOUNT_PACKAGE_CONTRACT'
}
foreach ($file in $manifest.files) {
    if ($file.path -cnotmatch '^[A-Za-z0-9][A-Za-z0-9._/-]{0,180}$' -or $file.path.Contains('..') -or
        $file.sha256 -cnotmatch '^[a-f0-9]{64}$') { throw 'PACKAGE_FILE_CONTRACT' }
    $path = [IO.Path]::GetFullPath((Join-Path $package $file.path))
    if (-not $path.StartsWith($package + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        (Get-Item -LiteralPath $path).Attributes.HasFlag([IO.FileAttributes]::ReparsePoint) -or
        (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash.ToLowerInvariant() -cne $file.sha256) {
        throw 'REVIEWED_PACKAGE_FILE_CHANGED'
    }
}
$root = [IO.Path]::GetFullPath((Join-Path ([Environment]::GetFolderPath('CommonApplicationData')) 'EslEtlV2'))
if ($root -cne 'C:\ProgramData\EslEtlV2') { throw 'LOCAL_INSTALLATION_PATH_REQUIRED' }
$administrator = [Security.Principal.WindowsPrincipal]::new([Security.Principal.WindowsIdentity]::GetCurrent())
$elevated = $administrator.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $Apply) {
    Write-Output "LOCAL_ACCOUNT_PACKAGE_VERIFIED accounts=2 grants=22 database=ETL_SISTEMA_V2_SHADOW elevated=$elevated mutation=0"
    exit 0
}
if (-not $elevated) { throw 'WINDOWS_ADMINISTRATOR_ELEVATION_REQUIRED_BEFORE_ANY_CHANGE' }
if ($manifest.executionIds.Count -ne 2) { throw 'TWO_RESERVED_STATUS_OCCURRENCES_REQUIRED' }
$ledgerPath = Join-Path $repository 'target/bloco53/cumulative-reservations.txt'
$ledger = @([IO.File]::ReadAllLines($ledgerPath, $encoding) | Where-Object { $_.Length -gt 0 })
if ($ledger.Count -gt 128 -or @($ledger | Select-Object -Unique).Count -ne $ledger.Count) { throw 'CUMULATIVE_RESERVATION_LIMIT' }
foreach ($execution in $manifest.executionIds) {
    if ($execution -cnotmatch '^[a-f0-9-]{36}$' -or $execution -cnotin $ledger) { throw 'STATUS_OCCURRENCE_NOT_RESERVED' }
}
if (Test-Path -LiteralPath $root) {
    if ($ResumeInstalledAccounts) {
        if ($ResumeBeforeAccountCreation -or
            (Get-FileHash -LiteralPath (Join-Path $root 'installation-manifest.json')).Hash.ToLowerInvariant() -cne $ManifestSha256) {
            throw 'ONLY_EXACT_INSTALLED_PACKAGE_MAY_RESUME'
        }
        $previous = [IO.File]::ReadAllText((Join-Path $root 'installation-result.txt'), $encoding)
        if ($previous -notmatch 'STATUS=PARTIAL_PRESERVED_NEW_ACCOUNTS_DISABLED' -or
            $previous -match 'STATUS=LOCAL_ACCOUNTS_PROVISIONED_AND_VERIFIED') {
            throw 'COMPLETED_INSTALLATION_MUST_NOT_BE_REPLAYED'
        }
        foreach ($file in $manifest.files) {
            if ((Get-FileHash -LiteralPath (Join-Path $root ('app/' + $file.path))).Hash.ToLowerInvariant() -cne $file.sha256) {
                throw 'INSTALLED_PACKAGE_DRIFT'
            }
        }
    } else {
    if (-not $ResumeBeforeAccountCreation) { throw 'INSTALLATION_ALREADY_EXISTS_REQUIRES_EXPLICIT_RECONCILIATION' }
    $previous = [IO.File]::ReadAllText((Join-Path $root 'installation-result.txt'), $encoding)
    $files = @(Get-ChildItem -LiteralPath $root -File -Recurse -Force)
    $relativeFiles = @($files | ForEach-Object { [IO.Path]::GetRelativePath($root, $_.FullName) })
    if ($previous -notmatch 'SQL_COMMITTED=False' -or
        $previous -notmatch 'STATUS=PARTIAL_PRESERVED_NEW_ACCOUNTS_DISABLED' -or
        $files.Count -ne 2 -or 'installation-result.txt' -cnotin $relativeFiles -or
        'secrets\etl_v2_exec.dpapi' -cnotin $relativeFiles -or
        @($files | Where-Object { $_.Attributes.HasFlag([IO.FileAttributes]::ReparsePoint) }).Count -gt 0) {
        throw 'ONLY_KNOWN_BEFORE_ACCOUNT_FAILURE_MAY_RESUME'
    }
    }
} elseif ($ResumeBeforeAccountCreation -or $ResumeInstalledAccounts) { throw 'NO_PARTIAL_INSTALLATION_TO_RESUME' }
foreach ($name in $manifest.accounts) {
    $existing = Get-LocalUser -Name $name -ErrorAction SilentlyContinue
    if ($ResumeInstalledAccounts) {
        if (-not $existing -or $existing.Description -cne 'ETL V2 local - Block 53') { throw 'OWN_INSTALLED_ACCOUNT_REQUIRED' }
    } elseif ($existing) { throw 'LOCAL_ACCOUNT_ALREADY_EXISTS_PRESERVE_IT' }
}
$java = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot\bin\java.exe'
$sqlcmd = (Get-Command sqlcmd.exe -CommandType Application -ErrorAction Stop).Source
if (-not (Test-Path -LiteralPath $java -PathType Leaf)) { throw 'PINNED_JAVA17_REQUIRED' }
if (-not $ResumeInstalledAccounts) {
    $preflight = & $sqlcmd -S localhost -d master -E -C -l 10 -t 20 -b -h -1 -W -i (Join-Path $package 'preflight.sql') 2>&1
    if ($LASTEXITCODE -ne 0 -or ($preflight -join ' ') -notmatch 'LOCAL_ACCOUNT_SQL_PREFLIGHT_PASS') {
        throw 'LOCAL_ACCOUNT_SQL_PREFLIGHT_FAILED'
    }
}

function Protect-Directory([string]$Path, [array]$Readers = @()) {
    [void][IO.Directory]::CreateDirectory($Path)
    $acl = [Security.AccessControl.DirectorySecurity]::new()
    $acl.SetAccessRuleProtection($true, $false)
    $adminSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')
    $acl.SetOwner($adminSid)
    foreach ($sid in @($adminSid, [Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $sid, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    }
    foreach ($sid in $Readers) {
        $acl.AddAccessRule([Security.AccessControl.FileSystemAccessRule]::new(
            $sid, 'ReadAndExecute', 'ContainerInherit,ObjectInherit', 'None', 'Allow'))
    }
    Set-Acl -LiteralPath $Path -AclObject $acl
}

function Invoke-AccountProcess([string]$Name, [string]$Executable, [string[]]$Arguments) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = $Executable
    $info.WorkingDirectory = Join-Path $root 'app'
    $info.UseShellExecute = $false
    $info.CreateNoWindow = $true
    $info.WindowStyle = [Diagnostics.ProcessWindowStyle]::Hidden
    $info.RedirectStandardOutput = $true
    $info.RedirectStandardError = $true
    $info.UserName = $Name
    $info.Domain = [Environment]::MachineName
    $info.Password = $credentials[$Name]
    $info.LoadUserProfile = $true
    foreach ($key in @($info.Environment.Keys)) {
        if ($key -match '^V2_' -or $key -in @('JAVA_TOOL_OPTIONS','_JAVA_OPTIONS','JDK_JAVA_OPTIONS','CLASSPATH')) {
            [void]$info.Environment.Remove($key)
        }
    }
    foreach ($argument in $Arguments) { [void]$info.ArgumentList.Add($argument) }
    $process = [Diagnostics.Process]::new()
    $process.StartInfo = $info
    try {
        if (-not $process.Start()) { throw 'OWN_ACCOUNT_PROCESS_NOT_STARTED' }
        $outTask = $process.StandardOutput.ReadToEndAsync()
        $errTask = $process.StandardError.ReadToEndAsync()
        if (-not $process.WaitForExit(60000)) {
            $process.Kill($true)
            [void]$process.WaitForExit(5000)
            throw 'OWN_ACCOUNT_PROCESS_DEADLINE'
        }
        $stdout = $outTask.GetAwaiter().GetResult()
        $stderr = $errTask.GetAwaiter().GetResult()
        if (($stdout.Length + $stderr.Length) -gt 16384) { throw 'OWN_ACCOUNT_PROCESS_OUTPUT_LIMIT' }
        return [pscustomobject]@{ Code=$process.ExitCode; Out=$stdout; Error=$stderr }
    } finally { $process.Dispose() }
}

$created = [Collections.Generic.List[string]]::new()
$credentials = @{}
$sqlCommitted = $false
$summary = [Collections.Generic.List[string]]::new()
$summary.Add('TARGET=localhost/ETL_SISTEMA_V2_SHADOW')
$summary.Add('PRODUCTION_DATABASE_ACCESSED=0')
$summary.Add('SQL_SERVICE_RESTARTED=0')
try {
    if ($ResumeInstalledAccounts) {
        foreach ($name in $manifest.accounts) {
            $sealed = [IO.File]::ReadAllText((Join-Path $root ('secrets/' + $name + '.dpapi')), $encoding)
            $credentials[$name] = ConvertTo-SecureString -String $sealed
            $sealed = $null
            $created.Add($name)
        }
        # Accounts remain disabled until exact database grants and mappings are verified below.
        $verification = & $sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -C -l 10 -t 20 -b -i (Join-Path $repository 'database/validation/053_validate_local_runtime_provisioning.sql') 2>&1
        if ($LASTEXITCODE -ne 0 -or ($verification -join ' ') -notmatch 'LOCAL_RUNTIME_PROVISIONING_EXACT_PASS') {
            throw 'INSTALLED_DATABASE_PROVISIONING_MUST_BE_VERIFIED'
        }
        foreach ($name in $manifest.accounts) { Enable-LocalUser -Name $name }
    } else {
    if ($ResumeBeforeAccountCreation) {
        # Both exact paths belong to this installer; retain even the unused encrypted material.
        foreach ($pair in @(
            @('installation-result.txt', 'attempt-before-account-creation.txt'),
            @('secrets/etl_v2_exec.dpapi', 'secrets/unused-before-account-creation.dpapi')
        )) {
            $source = [IO.Path]::GetFullPath((Join-Path $root $pair[0]))
            $destination = [IO.Path]::GetFullPath((Join-Path $root $pair[1]))
            if (-not $source.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase) -or
                -not $destination.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase) -or
                (Test-Path -LiteralPath $destination)) { throw 'OWN_PARTIAL_ARCHIVE_PATH_GUARD' }
            Move-Item -LiteralPath $source -Destination $destination
        }
    }
    Protect-Directory $root
    Protect-Directory (Join-Path $root 'secrets')
    foreach ($name in $manifest.accounts) {
        $random = [Security.Cryptography.RandomNumberGenerator]::GetBytes(32)
        $plain = 'aA1!' + [Convert]::ToBase64String($random)
        $secure = ConvertTo-SecureString -String $plain -AsPlainText -Force
        $plain = $null
        [Array]::Clear($random, 0, $random.Length)
        $credentials[$name] = $secure
        $sealed = ConvertFrom-SecureString -SecureString $secure
        [IO.File]::WriteAllText((Join-Path $root ('secrets/' + $name + '.dpapi')), $sealed, $encoding)
        $sealed = $null
        New-LocalUser -Name $name -Password $secure -Description 'ETL V2 local - Block 53' `
            -AccountExpires (Get-Date).AddDays(30) -UserMayNotChangePassword | Out-Null
        $created.Add($name)
        $localAccount = Get-LocalUser -Name $name
        $usersSid = [Security.Principal.SecurityIdentifier]::new('S-1-5-32-545')
        if (@(Get-LocalGroupMember -SID $usersSid | Where-Object { $_.SID -eq $localAccount.SID }).Count -eq 0) {
            Add-LocalGroupMember -SID $usersSid -Member $localAccount
        }
    }
    $readers = @($manifest.accounts | ForEach-Object { (Get-LocalUser -Name $_).SID })
    $adminMembers = @(Get-LocalGroupMember -SID ([Security.Principal.SecurityIdentifier]::new('S-1-5-32-544')))
    if (@($adminMembers | Where-Object { $_.SID -in $readers }).Count -ne 0) { throw 'NEW_ACCOUNT_HAS_ADMINISTRATOR_MEMBERSHIP' }
    Protect-Directory $root $readers
    Protect-Directory (Join-Path $root 'secrets')
    Protect-Directory (Join-Path $root 'app') $readers
    foreach ($file in $manifest.files) {
        $destination = Join-Path $root ('app/' + $file.path)
        [void][IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($destination))
        [IO.File]::Copy((Join-Path $package $file.path), $destination, $false)
    }
    [IO.File]::Copy($manifestPath, (Join-Path $root 'installation-manifest.json'), $false)
    $applyOutput = & $sqlcmd -S localhost -d ETL_SISTEMA_V2_SHADOW -E -C -l 10 -t 30 -b -i (Join-Path $root 'app/apply.sql') 2>&1
    if ($LASTEXITCODE -ne 0 -or ($applyOutput -join ' ') -notmatch 'LOCAL_ACCOUNT_SQL_COMMIT_PASS') {
        throw 'LOCAL_ACCOUNT_SQL_APPLICATION_FAILED'
    }
    }
    $sqlCommitted = $true
    $summary.Add('WINDOWS_ACCOUNTS_CREATED=2')
    $summary.Add('SQL_WINDOWS_LOGINS_CREATED=2')
    $summary.Add('DATABASE_USERS_CREATED=2')
    $summary.Add('EXACT_PROCEDURE_GRANTS=22')
    $summary.Add('CREDENTIAL_STORAGE=DPAPI_CURRENT_USER_ADMINISTRATORS_SYSTEM_ACL')
    foreach ($name in $manifest.accounts) {
        $kind = if ($name -eq 'etl_v2_exec') { 'SERVICE' } else { 'OPERATOR' }
        $result = Invoke-AccountProcess $name $sqlcmd @('-S','localhost','-d','ETL_SISTEMA_V2_SHADOW','-E','-C','-l','10','-t','20','-b','-h','-1','-W','-i',(Join-Path $root ('app/verify-' + $kind + '.sql')))
        if ($result.Code -ne 0 -or $result.Out -notmatch ('RESTRICTED_' + $kind + '_PASS')) {
            throw ('NEW_WINDOWS_SESSION_' + $kind + '_FAILED')
        }
        $summary.Add('NEW_WINDOWS_SESSION_' + $kind + '=PASS')
        $common = @(('-Djava.library.path=' + (Join-Path $root 'app/native')),
            ('-Djavax.net.ssl.trustStore=' + (Join-Path $root 'app/sql-local-trust.jks')),
            '-Djavax.net.ssl.trustStoreType=JKS','-jar',(Join-Path $root 'app/etl-dataexport-v2.jar'))
        $status = Invoke-AccountProcess $name $java ($common + @('status','--config',(Join-Path $root 'app/runtime.properties'),'--request',(Join-Path $root ('app/status-' + $kind + '.json'))))
        if ($status.Code -ne 10 -or $status.Out -notmatch 'Runtime: DEGRADED') { throw ('OFFICIAL_JAR_STATUS_' + $kind + '_FAILED_CODE_' + $status.Code) }
        $summary.Add('OFFICIAL_JAR_STATUS_' + $kind + '=AUTHORIZED_NOT_FOUND_EXIT_10')
    }
    $negative = Invoke-AccountProcess 'etl_v2_view' $java ($common + @('run','--config',(Join-Path $root 'app/runtime.properties'),'--request',(Join-Path $root 'app/run-denied-OPERATOR.json')))
    if ($negative.Code -ne 20) { throw 'OPERATOR_EXECUTION_NOT_DENIED' }
    $summary.Add('OFFICIAL_JAR_OPERATOR_RUN_DENIED=PASS')
    $summary.Add('SOURCE_FETCHES=0')
    $summary.Add('STATUS=LOCAL_ACCOUNTS_PROVISIONED_AND_VERIFIED')
} catch {
    # Keep created identities and committed SQL for audit. Disable only identities created here.
    foreach ($name in $created) { Disable-LocalUser -Name $name -ErrorAction Continue }
    $reason = [string]$_.Exception.Message
    if ($reason -cnotmatch '^[A-Z0-9_]{1,140}$') { $reason='INSTALLATION_FAILED_DETAILS_REDACTED' }
    $summary.Add('STATUS=PARTIAL_PRESERVED_NEW_ACCOUNTS_DISABLED')
    $summary.Add('REASON=' + $reason)
    $summary.Add('ERROR_TYPE=' + $_.Exception.GetType().Name)
    $summary.Add('SCRIPT_LINE=' + $_.InvocationInfo.ScriptLineNumber)
    $summary.Add('SQL_COMMITTED=' + $sqlCommitted)
    throw $reason
} finally {
    foreach ($credential in $credentials.Values) { $credential.Dispose() }
    if (Test-Path -LiteralPath $root -PathType Container) {
        [IO.File]::WriteAllLines((Join-Path $root 'installation-result.txt'), $summary, $encoding)
    }
    [IO.File]::WriteAllLines((Join-Path $package 'installation-result.txt'), $summary, $encoding)
}
$summary
