[CmdletBinding()]
param(
    [string]$RuntimeRoot,
    [string]$PrivateRoot
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$approvedPin = 'E3A5A7061FB56C225F93CC330CD733F5911F7D3F420D608EBE9AFA7371ACE276'
$approvedEngine = '73DCD4A89FF178CF6B2978EE29DCCD8898FE5C1977ABAC8208AA4ECB5EC43E32'
$utf8 = New-Object System.Text.UTF8Encoding($false, $true)

function Assert-ReferencePath([string]$Path) {
    $current = [IO.Path]::GetFullPath($Path)
    while ($current) {
        if ((Test-Path -LiteralPath $current) -and
            ((Get-Item -LiteralPath $current -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) {
            throw 'SHARED_REFERENCE_REPARSE_REFUSED'
        }
        $parent = [IO.Directory]::GetParent($current)
        if ($null -eq $parent) { break }
        $current = $parent.FullName
    }
}

function Read-ReferenceJson([string]$Path) {
    Assert-ReferencePath $Path
    if (!(Test-Path -LiteralPath $Path -PathType Leaf) -or
        (Get-Item -LiteralPath $Path).Length -gt 32768) {
        throw 'SHARED_REFERENCE_FILE_MISSING_OR_OVERSIZED'
    }
    [IO.File]::ReadAllText($Path, $utf8) | ConvertFrom-Json
}

$certificate = $null
try {
    if (!$RuntimeRoot) {
        $repository = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
        $RuntimeRoot = Join-Path (Split-Path $repository -Parent) '.runtime/sql-server'
    }
    if (!$PrivateRoot) {
        $PrivateRoot = Join-Path ([Environment]::GetFolderPath('LocalApplicationData')) 'Rodogarcia/SqlServer'
    }
    $RuntimeRoot = [IO.Path]::GetFullPath($RuntimeRoot)
    $PrivateRoot = [IO.Path]::GetFullPath($PrivateRoot)
    Assert-ReferencePath $RuntimeRoot
    Assert-ReferencePath $PrivateRoot
    $config = Read-ReferenceJson (Join-Path $RuntimeRoot 'config.json')
    if ($config.productVersion -cne '16.0.1000.6' -or
        $config.registryEdition -cne 'Standard Edition' -or
        $config.serverExecutableSHA256 -ine $approvedEngine) {
        throw 'SHARED_REFERENCE_ENGINE_CONFIGURATION_REFUSED'
    }
    if ($config.endpoint -cne '127.0.0.1:1433') {
        throw 'SHARED_REFERENCE_ENDPOINT_REFUSED'
    }
    $receipt = Read-ReferenceJson (Join-Path $PrivateRoot 'tls.json')
    if ($receipt.certificateSHA256 -ine $approvedPin) {
        throw 'SHARED_REFERENCE_IMMUTABLE_PIN_REFUSED'
    }
    if ($receipt.id -cnotmatch '^[a-f0-9]{32}$') {
        throw 'SHARED_REFERENCE_CERTIFICATE_PATH_REFUSED'
    }
    $certificatePath = Join-Path $PrivateRoot ('sql-public-' + $receipt.id + '.pem')
    $truststorePath = [IO.Path]::ChangeExtension($certificatePath, '.p12')
    if ([IO.Path]::GetFullPath($receipt.certificate) -ine $certificatePath -or
        [IO.Path]::GetFullPath($receipt.truststore) -ine $truststorePath) {
        throw 'SHARED_REFERENCE_CERTIFICATE_PATH_REFUSED'
    }
    foreach ($file in @($certificatePath, $truststorePath)) {
        Assert-ReferencePath $file
        if (!(Test-Path -LiteralPath $file -PathType Leaf) -or
            (Get-Item -LiteralPath $file).Length -gt 65536) {
            throw 'SHARED_REFERENCE_TLS_FILE_MISSING_OR_OVERSIZED'
        }
    }
    if ((Get-FileHash -LiteralPath $certificatePath -Algorithm SHA256).Hash -ine $receipt.fileSHA256 -or
        (Get-FileHash -LiteralPath $truststorePath -Algorithm SHA256).Hash -ine $receipt.truststoreSHA256) {
        throw 'SHARED_REFERENCE_TLS_FILE_HASH_REFUSED'
    }
    $pem = [IO.File]::ReadAllText($certificatePath, $utf8)
    if ($pem -cnotmatch '\A\s*-----BEGIN CERTIFICATE-----\s+([A-Za-z0-9+/=\s]+)-----END CERTIFICATE-----\s*\z') {
        throw 'SHARED_REFERENCE_PUBLIC_CERTIFICATE_FORMAT_REFUSED'
    }
    $der = [Convert]::FromBase64String(($Matches[1] -replace '\s', ''))
    $sha = [Security.Cryptography.SHA256]::Create()
    try { $actualPin = [BitConverter]::ToString($sha.ComputeHash($der)).Replace('-', '') }
    finally { $sha.Dispose() }
    if ($actualPin -cne $approvedPin) { throw 'SHARED_REFERENCE_IMMUTABLE_PIN_REFUSED' }
    $certificate = New-Object Security.Cryptography.X509Certificates.X509Certificate2 -ArgumentList (, $der)
    if ($certificate.Subject -cne 'CN=SSL_Self_Signed_Fallback' -or
        $certificate.Issuer -cne $certificate.Subject -or
        [DateTime]::UtcNow -lt $certificate.NotBefore.ToUniversalTime() -or
        [DateTime]::UtcNow -gt $certificate.NotAfter.ToUniversalTime()) {
        throw 'SHARED_REFERENCE_CERTIFICATE_IDENTITY_OR_VALIDITY_REFUSED'
    }
    $credentialPath = Join-Path $PrivateRoot 'admin.clixml'
    Assert-ReferencePath $credentialPath
    if (!(Test-Path -LiteralPath $credentialPath -PathType Leaf)) {
        throw 'SHARED_REFERENCE_CREDENTIAL_FILE_MISSING'
    }
    # Inspect only the existence of the credential; do not read or hash its bytes.
    [ordered]@{
        status = 'SHARED_RUNTIME_REFERENCE_COMPATIBLE_OFFLINE_ONLY'
        observedAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        approvedVersion = '16.0.1000.6'
        approvedLeafPin = $approvedPin
        historicalReceiptAtUtc = $receipt.updatedAt
        publicTlsFilesVerified = $true
        credentialFilePresent = $true
        credentialResolved = $false
        currentServerIdentityProved = $false
        physicalReady = $false
        sqlConnections = 0
        httpCalls = 0
        aclChanges = 0
        trustChanges = 0
    } | ConvertTo-Json -Compress
    exit 0
} catch {
    $reason = $_.Exception.Message
    if ($reason -cnotmatch '^SHARED_REFERENCE_[A-Z_]+$') { $reason = 'SHARED_REFERENCE_READ_FAILED' }
    [ordered]@{ status = 'REFUSED'; reason = $reason; physicalReady = $false; sqlConnections = 0; httpCalls = 0 } |
        ConvertTo-Json -Compress
    exit 1
} finally {
    if ($null -ne $certificate) { $certificate.Dispose() }
}
