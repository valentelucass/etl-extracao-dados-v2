[CmdletBinding()]
param(
    [Parameter()]
    [ValidateNotNullOrEmpty()]
    [string] $Source = (Join-Path $PSScriptRoot '..\..')
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$sourceRoot = (Resolve-Path -LiteralPath $Source).Path.TrimEnd('\', '/')
$sourcePrefix = $sourceRoot + [System.IO.Path]::DirectorySeparatorChar
$pathComparison = if ($env:OS -eq 'Windows_NT') {
    [System.StringComparison]::OrdinalIgnoreCase
} else {
    [System.StringComparison]::Ordinal
}
$metadataExcludedDirectories = @(
    '.codex-local',
    '.git',
    '.idea',
    '.venv',
    '.vscode',
    'coverage',
    'logs',
    'node_modules',
    'target'
)
$textExtensions = @(
    '.bat',
    '.cmd',
    '.cjs',
    '.cs',
    '.csv',
    '.editorconfig',
    '.gitattributes',
    '.gitignore',
    '.graphql',
    '.java',
    '.json',
    '.jsonl',
    '.md',
    '.pom',
    '.properties',
    '.ps1',
    '.psd1',
    '.psm1',
    '.py',
    '.sha256',
    '.sh',
    '.sql',
    '.template',
    '.toml',
    '.txt',
    '.xml',
    '.yaml',
    '.yml'
)
$extensionlessTextFiles = @('.env.example', 'Dockerfile', 'LICENSE', 'NOTICE', 'mvnw')
$maximumTextFileBytes = 5MB
$strictUtf8 = [System.Text.UTF8Encoding]::new($false, $true)

$approvedBinaryFiles =
    [System.Collections.Generic.Dictionary[string, string]]::new(
        [System.StringComparer]::Ordinal
    )
$approvedBinaryFiles.Add(
    '.mvn/wrapper/maven-wrapper.jar',
    '4e2fbf6554bc8a4702cdfdd3bef464f423393d784ddbb037216320ce55d5e4e1'
)

$allowedSyntheticFindings =
    [System.Collections.Generic.HashSet[string]]::new([System.StringComparer]::Ordinal)
foreach ($entry in @(
    # QUAL-I: four public CycloneDX1.6 schema descriptions; exact path/value, never a directory exemption.
    'SECRET_QUOTED_ASSIGNMENT|docs/catalogos/macrobloco-qualificacao-pacote/third-party/bom-1.6.schema.json|A key used to encrypt and decrypt messages in symmetric cryptography.',
    'SECRET_QUOTED_ASSIGNMENT|docs/catalogos/macrobloco-qualificacao-pacote/third-party/bom-1.6.schema.json|A piece of data known only to the parties involved, in a secure communication.',
    'SECRET_QUOTED_ASSIGNMENT|docs/catalogos/macrobloco-qualificacao-pacote/third-party/bom-1.6.schema.json|A secret word, phrase, or sequence of characters used during authentication or authorization.',
    'SECRET_QUOTED_ASSIGNMENT|docs/catalogos/macrobloco-qualificacao-pacote/third-party/bom-1.6.schema.json|An object encapsulating a security identity.',
    # ANA-39: exact local fixture values; no generic exemption for token fields or synthetic paths.
    # SQL joins compare token columns with the derived-table alias normalized; this is no literal.
    'INLINE_SECRET_ASSIGNMENT|database/migrations/V071__consume_analytic_owned_fleet_references.sql|normalized',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/plataforma/fonte/raster/RasterLoopbackTransport.java|synthetic-raster-password',
    # Immutable predecessor copy, whose exact bytes are checked by IntegralChainSuccession.
    'HARDCODED_SECRET_ARGUMENT|docs/continuidade/historico/cadeia-integral/src/main/java/br/com/esl/etl/v2/plataforma/fonte/raster/RasterLoopbackTransport.java|synthetic-raster-password',
    'SECRET_QUOTED_ASSIGNMENT|src/main/resources/analytic-laboratory/fleet-references.synthetic.json|sha256-utf16le-v1',
    'SECRET_QUOTED_ASSIGNMENT|src/main/resources/analytic-laboratory/fleet-references.synthetic.json|0f2f56d9b0a0734d3f7d76776e5fa095201448d200fd0661335406a169966785',
    'SECRET_QUOTED_ASSIGNMENT|src/main/resources/analytic-laboratory/fleet-references.synthetic.json|d3c630b2817ba68a2ae0e37bc4bcfd4aa06b0f1ebee83b954cb60266bfa19d60',
    'SECRET_QUOTED_ASSIGNMENT|src/main/resources/analytic-laboratory/fleet-references.synthetic.json|440b374138a2135d40f9a94f5191f22e86a1e9c789826e41c20a2dae3df18fb7',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/contratos/ContractDataExportProbeTest.java|synthetic-dataexport-token',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/contratos/ContractDataExportProbeTest.java|synthetic-graphql-token',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/contratos/ContractRemoteExecutionTest.java|synthetic-dataexport-token',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/contratos/ContractRemoteExecutionTest.java|synthetic-graphql-token',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/contratos/ContractTestConfigurationTest.java|must-not-be-used-token',
    'BEARER_LITERAL|src/test/java/br/com/esl/etl/v2/contratos/ReadOnlyGraphQlParityHarnessTest.java|test-contract-token',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/plataforma/configuracao/DataExportPropertiesTest.java|system-token',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/plataforma/configuracao/DataExportPropertiesTest.java|test-token',
    'INLINE_SECRET_ASSIGNMENT|src/test/java/br/com/esl/etl/v2/plataforma/configuracao/DataExportPropertiesTest.java|payload-value',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/plataforma/configuracao/ShadowStoragePropertiesTest.java|runtime-password-secret',
    'HARDCODED_SECRET_ARGUMENT|src/test/java/br/com/esl/etl/v2/plataforma/configuracao/ShadowStoragePropertiesTest.java|runtime-password',
    'BEARER_LITERAL|src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/HttpDataExportTemplateInfoGatewayTest.java|test-token'
)) {
    [void] $allowedSyntheticFindings.Add($entry)
}

$rules = @(
    [pscustomobject]@{
        Id = 'PRIVATE_KEY'
        Pattern = [regex]::new('-----BEGIN (?:EC |OPENSSH |PGP |RSA )?PRIVATE KEY-----')
        ValueGroup = $null
    },
    [pscustomobject]@{
        Id = 'JWT'
        Pattern = [regex]::new('(?<![A-Za-z0-9_-])eyJ[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}\.[A-Za-z0-9_-]{8,}(?![A-Za-z0-9_-])')
        ValueGroup = $null
    },
    [pscustomobject]@{
        Id = 'KNOWN_TOKEN_PREFIX'
        Pattern = [regex]::new('(?i)(?<![A-Za-z0-9])(?:AKIA[0-9A-Z]{16}|AIza[0-9A-Za-z_-]{30,}|gh[pousr]_[0-9A-Za-z]{30,}|xox[baprs]-[0-9A-Za-z-]{20,}|sk_live_[0-9A-Za-z]{16,})(?![A-Za-z0-9])')
        ValueGroup = $null
    },
    [pscustomobject]@{
        Id = 'BEARER_LITERAL'
        Pattern = [regex]::new('(?i)\bBearer\s+(?<value>[A-Za-z0-9._~+/=-]{8,})')
        ValueGroup = 'value'
    },
    [pscustomobject]@{
        Id = 'SECRET_QUOTED_ASSIGNMENT'
        Pattern = [regex]::new('(?im)^[ \t]*(?:(?:set|export)[ \t]+)?[''\"]?(?:\$env:)?[A-Za-z0-9_.-]*(?:token|password|passwd|senha|secret|api[_-]?key|client[_-]?secret|access[_-]?key)[A-Za-z0-9_.-]*[''\"]?[ \t]*[:=][ \t]*[''\"](?<value>[^''\"\r\n]{8,})[''\"]')
        ValueGroup = 'value'
    },
    [pscustomobject]@{
        Id = 'SECRET_BARE_ASSIGNMENT'
        Pattern = [regex]::new('(?im)^[ \t]*(?:(?:set|export)[ \t]+)?[''\"]?(?:\$env:)?[A-Za-z0-9_.-]*(?:token|password|passwd|senha|secret|api[_-]?key|client[_-]?secret|access[_-]?key)[A-Za-z0-9_.-]*[''\"]?[ \t]*[:=][ \t]*(?<value>[^\s''\"<>$({\[;#]{8,})[''\"]?[ \t]*$')
        ValueGroup = 'value'
    },
    [pscustomobject]@{
        Id = 'INLINE_SECRET_ASSIGNMENT'
        Pattern = [regex]::new('(?i)(?:^|[^A-Za-z0-9_.-])[A-Za-z0-9_.-]*(?:token|password|passwd|senha|secret|api[_-]?key|client[_-]?secret|access[_-]?key)[A-Za-z0-9_.-]*[ \t]*=[ \t]*[`''\"]?(?<value>(?=[A-Za-z0-9!@%^&*._~+/=-]{8,})(?=[A-Za-z0-9!@%^&*._~+/=-]*[0-9!@%^&*._~+/=-])[A-Za-z0-9!@%^&*._~+/=-]{8,})(?=[ \t]*(?:$|[`''\".,;)]))')
        ValueGroup = 'value'
    },
    [pscustomobject]@{
        Id = 'HARDCODED_SECRET_ARGUMENT'
        Pattern = [regex]::new('(?i)\b(?:put|set|setProperty)\s*\(\s*[''\"][^''\"\r\n]*(?:token|password|passwd|senha|secret|api[_-]?key|client[_-]?secret)[^''\"\r\n]*[''\"]\s*,\s*[''\"](?<value>[^''\"\r\n]{8,})[''\"]')
        ValueGroup = 'value'
    }
)

$findings = [System.Collections.Generic.List[object]]::new()
$inventory = [System.Collections.Generic.List[object]]::new()
$seenFindings =
    [System.Collections.Generic.HashSet[string]]::new(
        [System.StringComparer]::OrdinalIgnoreCase
    )
$scannedFiles = 0
$verifiedBinaries = 0
$unexpectedNonTextFiles = 0
$oversizedTextFiles = 0

function Get-RelativePath {
    param([Parameter(Mandatory)][string] $FullName)

    return ($FullName.Substring($sourceRoot.Length).TrimStart('\', '/') -replace '\\', '/')
}

function Test-ExcludedMetadataPath {
    param([Parameter(Mandatory)][string] $RelativePath)

    $segments = $RelativePath -split '/'
    foreach ($segment in $segments) {
        if ($metadataExcludedDirectories -contains $segment) {
            return $true
        }
    }
    return $false
}

function Get-GitCandidatePaths {
    $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
    $startInfo.FileName = 'git'
    $startInfo.Arguments = '-c core.quotepath=false ls-files --cached --others --exclude-standard -z'
    $startInfo.WorkingDirectory = $sourceRoot
    $startInfo.UseShellExecute = $false
    $startInfo.RedirectStandardOutput = $true
    $startInfo.RedirectStandardError = $true
    $startInfo.CreateNoWindow = $true

    $process = [System.Diagnostics.Process]::new()
    $process.StartInfo = $startInfo
    [void] $process.Start()
    $standardOutput = $process.StandardOutput.ReadToEnd()
    [void] $process.StandardError.ReadToEnd()
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) {
        throw 'Git candidate enumeration failed.'
    }

    return @($standardOutput -split "`0" | Where-Object { $_.Length -gt 0 })
}

function Get-LineNumber {
    param(
        [Parameter(Mandatory)][string] $Text,
        [Parameter(Mandatory)][int] $Index
    )

    if ($Index -le 0) {
        return 1
    }
    return 1 + [regex]::Matches($Text.Substring(0, $Index), "`n").Count
}

function Add-Finding {
    param(
        [Parameter(Mandatory)][string] $RuleId,
        [Parameter(Mandatory)][string] $RelativePath,
        [Parameter(Mandatory)][int] $Line
    )

    $key = '{0}|{1}|{2}' -f $RuleId, $RelativePath, $Line
    if ($seenFindings.Add($key)) {
        $findings.Add([pscustomobject]@{
            Rule = $RuleId
            Path = $RelativePath
            Line = $Line
        })
    }
}

function Test-AllowedSyntheticFinding {
    param(
        [Parameter(Mandatory)][string] $RuleId,
        [Parameter(Mandatory)][string] $RelativePath,
        [Parameter(Mandatory)][string] $Candidate
    )

    return $allowedSyntheticFindings.Contains(
        ('{0}|{1}|{2}' -f $RuleId, $RelativePath, $Candidate.Trim())
    )
}

function Get-MetadataFiles {
    # Apply the same metadata exclusions before descending into accumulated build evidence.
    $directories = [System.Collections.Generic.Stack[string]]::new()
    $directories.Push($sourceRoot)
    while ($directories.Count -gt 0) {
        foreach ($item in (Get-ChildItem -LiteralPath $directories.Pop() -Force)) {
            if (Test-ExcludedMetadataPath -RelativePath (Get-RelativePath -FullName $item.FullName)) {
                continue
            }
            if ($item.PSIsContainer) {
                if (($item.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -eq 0) {
                    $directories.Push($item.FullName)
                }
            }
            else {
                Write-Output $item
            }
        }
    }
}

try {
    foreach ($file in (Get-MetadataFiles)) {
        $relativePath = Get-RelativePath -FullName $file.FullName
        if (Test-ExcludedMetadataPath -RelativePath $relativePath) {
            continue
        }

        $isApprovedEnvironmentExample = $file.Name -ceq '.env.example'
        $isEnvironmentFile = $file.Name -match '^\.env.*$'
        $isSensitiveLocalFile =
            $file.Name -match '(?i)^(?:application(?:\..*)?\.local\.properties|config\.bat)$'
        $isPrivateMaterial = $file.Extension -match '(?i)^\.(?:jks|key|p12|pem|pfx)$'
        if (
            ($isEnvironmentFile -and -not $isApprovedEnvironmentExample) -or
            $isSensitiveLocalFile -or
            $isPrivateMaterial
        ) {
            Add-Finding -RuleId 'SENSITIVE_FILE' -RelativePath $relativePath -Line 1
        }
    }

    $candidatePaths = @(Get-GitCandidatePaths)
    foreach ($gitPath in $candidatePaths) {
        $relativePath = $gitPath -replace '\\', '/'
        $fullPath = [System.IO.Path]::GetFullPath((Join-Path $sourceRoot $gitPath))
        if (-not $fullPath.StartsWith($sourcePrefix, $pathComparison)) {
            Add-Finding -RuleId 'CANDIDATE_OUTSIDE_ROOT' -RelativePath $relativePath -Line 1
            continue
        }
        if (-not (Test-Path -LiteralPath $fullPath -PathType Leaf)) {
            Add-Finding -RuleId 'MISSING_CANDIDATE' -RelativePath $relativePath -Line 1
            continue
        }

        $file = Get-Item -LiteralPath $fullPath -Force
        if (($file.Attributes -band [System.IO.FileAttributes]::ReparsePoint) -ne 0) {
            Add-Finding -RuleId 'UNSAFE_LINK' -RelativePath $relativePath -Line 1
            continue
        }

        $isTextFile =
            $textExtensions -contains $file.Extension.ToLowerInvariant() -or
            $extensionlessTextFiles -ccontains $file.Name
        if (-not $isTextFile) {
            if ($approvedBinaryFiles.ContainsKey($relativePath)) {
                $actualHash =
                    (Get-FileHash -LiteralPath $fullPath -Algorithm SHA256).Hash.ToLowerInvariant()
                if (
                    -not [string]::Equals(
                        $approvedBinaryFiles[$relativePath],
                        $actualHash,
                        [System.StringComparison]::Ordinal
                    )
                ) {
                    Add-Finding -RuleId 'BINARY_INTEGRITY_MISMATCH' -RelativePath $relativePath -Line 1
                    $inventory.Add([pscustomobject]@{
                        Kind = 'BINARY_INTEGRITY_MISMATCH'
                        Path = $relativePath
                        Bytes = $file.Length
                    })
                } else {
                    $verifiedBinaries++
                    $inventory.Add([pscustomobject]@{
                        Kind = 'VERIFIED_BINARY'
                        Path = $relativePath
                        Bytes = $file.Length
                    })
                }
            } else {
                $unexpectedNonTextFiles++
                Add-Finding -RuleId 'UNSCANNED_NON_TEXT' -RelativePath $relativePath -Line 1
                $inventory.Add([pscustomobject]@{
                    Kind = 'UNSCANNED_NON_TEXT'
                    Path = $relativePath
                    Bytes = $file.Length
                })
            }
            continue
        }
        if ($file.Length -gt $maximumTextFileBytes) {
            $oversizedTextFiles++
            Add-Finding -RuleId 'UNSCANNED_OVERSIZE_TEXT' -RelativePath $relativePath -Line 1
            $inventory.Add([pscustomobject]@{
                Kind = 'UNSCANNED_OVERSIZE_TEXT'
                Path = $relativePath
                Bytes = $file.Length
            })
            continue
        }

        try {
            $text = $strictUtf8.GetString([System.IO.File]::ReadAllBytes($fullPath))
        }
        catch [System.Text.DecoderFallbackException] {
            Add-Finding -RuleId 'INVALID_UTF8' -RelativePath $relativePath -Line 1
            continue
        }
        if ($text.Contains([char] 0)) {
            Add-Finding -RuleId 'EMBEDDED_NUL' -RelativePath $relativePath -Line 1
            continue
        }

        $scannedFiles++
        foreach ($rule in $rules) {
            foreach ($match in $rule.Pattern.Matches($text)) {
                if ($null -ne $rule.ValueGroup) {
                    $candidate = $match.Groups[$rule.ValueGroup].Value
                    if (
                        Test-AllowedSyntheticFinding `
                            -RuleId $rule.Id `
                            -RelativePath $relativePath `
                            -Candidate $candidate
                    ) {
                        continue
                    }
                }
                $line = Get-LineNumber -Text $text -Index $match.Index
                Add-Finding -RuleId $rule.Id -RelativePath $relativePath -Line $line
            }
        }
    }
}
catch {
    Write-Output 'OFFLINE_SECRET_SCAN status=ERROR details=REDACTED'
    exit 2
}

foreach ($item in ($inventory | Sort-Object Path, Kind)) {
    Write-Output ('OFFLINE_SECRET_INVENTORY kind={0} path={1} bytes={2}' -f
        $item.Kind,
        $item.Path,
        $item.Bytes)
}
foreach ($finding in ($findings | Sort-Object Path, Line, Rule)) {
    Write-Output ('OFFLINE_SECRET_FINDING rule={0} path={1} line={2}' -f
        $finding.Rule,
        $finding.Path,
        $finding.Line)
}

Write-Output ('OFFLINE_SECRET_SCAN status={0} candidate_files={1} scanned_text={2} verified_binaries={3} unexpected_non_text={4} oversized_text={5} findings={6}' -f
    $(if ($findings.Count -eq 0) { 'PASS' } else { 'FAIL' }),
    $candidatePaths.Count,
    $scannedFiles,
    $verifiedBinaries,
    $unexpectedNonTextFiles,
    $oversizedTextFiles,
    $findings.Count)

if ($findings.Count -gt 0) {
    exit 1
}
exit 0
