[CmdletBinding()]
param([ValidatePattern('^[a-z0-9-]{1,64}$')][string]$EvidenceAttempt)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$scannerPath = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot 'Invoke-OfflineSecretScan.ps1')).Path
$temporaryRoot = [System.IO.Path]::GetFullPath([System.IO.Path]::GetTempPath()).TrimEnd('\', '/')
$testRoot = Join-Path $temporaryRoot ('etl-v2-offline-scan-selftest-' + [guid]::NewGuid().ToString('N'))
if ($EvidenceAttempt) {
    $evidenceRoot = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../../target/macrobloco-qualificacao-pacote-20260913-01'))
    $testRoot = Join-Path $evidenceRoot $EvidenceAttempt
    if (Test-Path -LiteralPath $testRoot) { throw 'SCANNER_EVIDENCE_ATTEMPT_EXISTS' }
    $parent = Get-Item -LiteralPath $evidenceRoot
    while ($null -ne $parent) {
        if (($parent.Attributes -band [IO.FileAttributes]::ReparsePoint) -ne 0) { throw 'SCANNER_EVIDENCE_REPARSE' }
        $parent = $parent.Parent
    }
}
$quote = [char] 34
$caseCount = 0

function New-TestRepository {
    param([Parameter(Mandatory)][string] $Name)

    $caseRoot = Join-Path $testRoot $Name
    [void] (New-Item -ItemType Directory -Path $caseRoot -Force)
    & git init --quiet $caseRoot 2>&1 | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw 'Unable to initialize an isolated scanner fixture.'
    }
    return $caseRoot
}

function Write-TestFile {
    param(
        [Parameter(Mandatory)][string] $Root,
        [Parameter(Mandatory)][string] $RelativePath,
        [Parameter(Mandatory)][string] $Content
    )

    $path = Join-Path $Root $RelativePath
    $parent = Split-Path -Parent $path
    [void] (New-Item -ItemType Directory -Path $parent -Force)
    [System.IO.File]::WriteAllText($path, $Content, [System.Text.UTF8Encoding]::new($false))
}

function Invoke-ScannerCase {
    param(
        [Parameter(Mandatory)][string] $Name,
        [Parameter(Mandatory)][scriptblock] $Arrange,
        [Parameter(Mandatory)][int] $ExpectedExitCode,
        [Parameter()][string] $ExpectedRule,
        [Parameter()][string[]] $ForbiddenOutputLiteral = @()
    )

    $caseRoot = New-TestRepository -Name $Name
    & $Arrange $caseRoot
    $output = @(& $scannerPath -Source $caseRoot 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    if ($EvidenceAttempt) {
        [IO.File]::WriteAllText((Join-Path $testRoot ($Name + '-stdout.log')), ($output -join "`n"), [Text.UTF8Encoding]::new($false))
        [IO.File]::WriteAllText((Join-Path $testRoot ($Name + '-exit.json')),
            (@{exit=$exitCode;expected=$ExpectedExitCode;rule=$ExpectedRule}|ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    }
    if ($exitCode -ne $ExpectedExitCode) {
        throw ('Scanner self-test case {0} returned an unexpected exit code.' -f $Name)
    }
    if (
        -not [string]::IsNullOrEmpty($ExpectedRule) -and
        -not ($output -match ('rule={0}(?:\s|$)' -f [regex]::Escape($ExpectedRule)))
    ) {
        throw ('Scanner self-test case {0} did not emit the expected sanitized rule.' -f $Name)
    }
    $joinedOutput = $output -join "`n"
    foreach ($literal in $ForbiddenOutputLiteral) {
        if (-not [string]::IsNullOrEmpty($literal) -and $joinedOutput.Contains($literal)) {
            throw ('Scanner self-test case {0} exposed fixture content.' -f $Name)
        }
    }
    $script:caseCount++
}

try {
    [void] (New-Item -ItemType Directory -Path $testRoot)

    Invoke-ScannerCase -Name 'unexplained-tracked-removal' -ExpectedExitCode 1 -ExpectedRule 'MISSING_CANDIDATE' -Arrange {
        param($caseRoot)
        Write-TestFile -Root $caseRoot -RelativePath 'unexplained.txt' -Content 'safe fixture'
        & git -C $caseRoot add -- unexplained.txt
        if ($LASTEXITCODE -ne 0) { throw 'SCANNER_FIXTURE_INDEX' }
        # Exact file created by this test; never remove a repository input.
        Remove-Item -LiteralPath (Join-Path $caseRoot 'unexplained.txt')
    }

    Invoke-ScannerCase -Name 'unicode-candidate-path-is-read-as-utf8' -ExpectedExitCode 0 -Arrange {
        param($caseRoot)
        Write-TestFile -Root $caseRoot -RelativePath 'docs/catalogos/rotação/manifesto.json' -Content '{"safe":true}'
    }

    $approvedValue = 'must-not-be-used-' + 'token'
    Invoke-ScannerCase `
        -Name 'exact-approved-fixture' `
        -ExpectedExitCode 0 `
        -ForbiddenOutputLiteral $approvedValue `
        -Arrange {
        param($root)
        $line =
            'environment.put(' + $quote + 'API_DATAEXPORT_TOKEN' + $quote + ', ' +
            $quote + $approvedValue + $quote + ');'
        Write-TestFile `
            -Root $root `
            -RelativePath 'src/test/java/br/com/esl/etl/v2/contratos/ContractTestConfigurationTest.java' `
            -Content $line
    }

    foreach ($marker in @('test', 'example', 'redacted')) {
        $unapprovedValue = 'looks-like-' + $marker + '-but-is-not-approved-123456'
        Invoke-ScannerCase `
            -Name ('broad-marker-' + $marker) `
            -ExpectedExitCode 1 `
            -ExpectedRule 'SECRET_QUOTED_ASSIGNMENT' `
            -ForbiddenOutputLiteral $unapprovedValue `
            -Arrange {
                param($root)
                $key = 'SERVICE_' + 'TOKEN'
                $content = $key + '=' + $quote + $unapprovedValue + $quote
                Write-TestFile -Root $root -RelativePath 'fixture.properties' -Content $content
            }
    }

    foreach ($newTextExtension in @('cs', 'template', 'pom', 'txt')) {
        $unapprovedValue = 'unapproved-' + 'new-text-format-123456'
        Invoke-ScannerCase -Name ('scan-new-text-' + $newTextExtension) -ExpectedExitCode 1 -ExpectedRule 'SECRET_QUOTED_ASSIGNMENT' -ForbiddenOutputLiteral $unapprovedValue -Arrange {
            param($root)
            $key = 'SERVICE_' + 'TOKEN'
            $content = $key + '=' + $quote + $unapprovedValue + $quote
            Write-TestFile -Root $root -RelativePath ('fixture.' + $newTextExtension) -Content $content
        }
    }

    $schemaDescription = 'An object encapsulating a security identity.'
    foreach ($variant in @('approved', 'changed', 'other-path')) {
        $expectedExit = if ($variant -ceq 'approved') { 0 } else { 1 }
        $expectedRule = if ($expectedExit -eq 0) { '' } else { 'SECRET_QUOTED_ASSIGNMENT' }
        Invoke-ScannerCase -Name ('schema-description-' + $variant) -ExpectedExitCode $expectedExit -ExpectedRule $expectedRule -Arrange {
            param($root)
            $relative = if ($variant -ceq 'other-path') { 'schema.json' } else {
                'docs/catalogos/macrobloco-qualificacao-pacote/third-party/bom-1.6.schema.json'
            }
            $value = if ($variant -ceq 'changed') { 'unapproved-' + 'schema-value-123456' } else { $schemaDescription }
            Write-TestFile -Root $root -RelativePath $relative -Content ('"token": "' + $value + '"')
        }
    }

    $environmentExampleValue = 'unapproved-' + 'example-value-123456'
    Invoke-ScannerCase `
        -Name 'environment-example-is-scanned' `
        -ExpectedExitCode 1 `
        -ExpectedRule 'SECRET_QUOTED_ASSIGNMENT' `
        -ForbiddenOutputLiteral $environmentExampleValue `
        -Arrange {
            param($root)
            $key = 'SERVICE_' + 'TOKEN'
            $content = $key + '=' + $quote + $environmentExampleValue + $quote
            Write-TestFile -Root $root -RelativePath '.env.example' -Content $content
        }

    $csvValue = 'catalog-' + 'credential-value-123456'
    Invoke-ScannerCase `
        -Name 'csv-catalog-is-scanned' `
        -ExpectedExitCode 1 `
        -ExpectedRule 'SECRET_QUOTED_ASSIGNMENT' `
        -ForbiddenOutputLiteral $csvValue `
        -Arrange {
            param($root)
            $key = 'SERVICE_' + 'TOKEN'
            $content = 'name,value' + "`n" + $key + '=' + $quote + $csvValue + $quote
            Write-TestFile -Root $root -RelativePath 'catalog.csv' -Content $content
        }

    Invoke-ScannerCase `
        -Name 'unknown-binary-fails-closed' `
        -ExpectedExitCode 1 `
        -ExpectedRule 'UNSCANNED_NON_TEXT' `
        -Arrange {
            param($root)
            $path = Join-Path $root 'fixture.bin'
            [System.IO.File]::WriteAllBytes($path, [byte[]] (0, 1, 2, 3))
        }

    Invoke-ScannerCase `
        -Name 'nul-in-text-fails-closed' `
        -ExpectedExitCode 1 `
        -ExpectedRule 'EMBEDDED_NUL' `
        -Arrange {
            param($root)
            $path = Join-Path $root 'fixture.md'
            [System.IO.File]::WriteAllBytes($path, [byte[]] (65, 0, 66))
        }

    $ignoredEnvironmentValue = 'local-only-' + 'value-123456'
    Invoke-ScannerCase `
        -Name 'ignored-environment-file-is-inventoried' `
        -ExpectedExitCode 1 `
        -ExpectedRule 'SENSITIVE_FILE' `
        -ForbiddenOutputLiteral $ignoredEnvironmentValue `
        -Arrange {
            param($root)
            Write-TestFile -Root $root -RelativePath '.gitignore' -Content ".env*`n"
            $key = 'SERVICE_' + 'TOKEN'
            Write-TestFile `
                -Root $root `
                -RelativePath '.envrc' `
                -Content ($key + '=' + $ignoredEnvironmentValue)
        }

    Invoke-ScannerCase `
        -Name 'ignored-untracked-root-env-is-allowed' `
        -ExpectedExitCode 0 `
        -ForbiddenOutputLiteral $ignoredEnvironmentValue `
        -Arrange {
            param($root)
            Write-TestFile -Root $root -RelativePath '.gitignore' -Content ".env*`n"
            Write-TestFile -Root $root -RelativePath '.env' -Content ('LOCAL_VALUE=' + $ignoredEnvironmentValue)
        }

    Invoke-ScannerCase `
        -Name 'tracked-root-env-is-inventoried' `
        -ExpectedExitCode 1 `
        -ExpectedRule 'SENSITIVE_FILE' `
        -ForbiddenOutputLiteral $ignoredEnvironmentValue `
        -Arrange {
            param($root)
            Write-TestFile -Root $root -RelativePath '.gitignore' -Content ".env*`n"
            Write-TestFile -Root $root -RelativePath '.env' -Content ('LOCAL_VALUE=' + $ignoredEnvironmentValue)
            & git -C $root add -f -- .env
            if ($LASTEXITCODE -ne 0) { throw 'SCANNER_FIXTURE_INDEX' }
        }

    Write-Output ('OFFLINE_SECRET_SCAN_SELF_TEST status=PASS cases={0}' -f $caseCount)
}
finally {
    if ($EvidenceAttempt) {
        [IO.File]::WriteAllText((Join-Path $testRoot 'result.json'),
            (@{passed=($caseCount -eq 20);cases=$caseCount;evidenceRetained=$true;scannerSha256=(Get-FileHash -LiteralPath $scannerPath).Hash.ToLowerInvariant()}|ConvertTo-Json),
            [Text.UTF8Encoding]::new($false))
    } else {
    $resolvedTarget = [System.IO.Path]::GetFullPath($testRoot)
    $expectedPrefix = $temporaryRoot + [System.IO.Path]::DirectorySeparatorChar
    $expectedLeafPrefix = 'etl-v2-offline-scan-selftest-'
    $isSafeTarget =
        $resolvedTarget.StartsWith($expectedPrefix, [System.StringComparison]::OrdinalIgnoreCase) -and
        [System.IO.Path]::GetFileName($resolvedTarget).StartsWith(
            $expectedLeafPrefix,
            [System.StringComparison]::Ordinal
        )
    if (-not $isSafeTarget) {
        throw 'Refusing to remove an unexpected scanner fixture path.'
    }
    if (Test-Path -LiteralPath $resolvedTarget) {
        Remove-Item -LiteralPath $resolvedTarget -Recurse -Force
    }
    }
}

exit 0
