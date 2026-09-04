#Requires -Version 7.0

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$utf8 = [System.Text.UTF8Encoding]::new($false, $true)
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$repositoryPrefix = $repositoryRoot + [System.IO.Path]::DirectorySeparatorChar
$statePath = Join-Path $repositoryRoot 'STATES.md'
$trailPath = Join-Path $repositoryRoot 'docs\runbooks\trilha-de-chats-gpt-5-6.md'

function Read-StrictUtf8 {
    param(
        [Parameter(Mandatory)][string]$LiteralPath,
        [Parameter(Mandatory)][long]$MaximumBytes
    )

    $item = Get-Item -LiteralPath $LiteralPath -ErrorAction Stop
    if ($item.Length -gt $MaximumBytes) {
        throw "O arquivo '$($item.Name)' excede o limite de validação."
    }
    return $utf8.GetString([System.IO.File]::ReadAllBytes($item.FullName))
}

function Get-CapturedInteger {
    param(
        [Parameter(Mandatory)][System.Text.RegularExpressions.Match]$Match,
        [Parameter(Mandatory)][string]$Group
    )

    if (-not $Match.Success) {
        throw 'O painel rápido não contém as contagens canônicas esperadas.'
    }
    return [int]$Match.Groups[$Group].Value
}

$stateText = Read-StrictUtf8 -LiteralPath $statePath -MaximumBytes 2MB
$trailText = Read-StrictUtf8 -LiteralPath $trailPath -MaximumBytes 1MB

if ($stateText -match '[�]' -or $trailText -match '[�]') {
    throw 'Foi encontrado caractere de substituição Unicode nos documentos.'
}

$trailCheckboxes = @([regex]::Matches(
    $trailText,
    '(?m)^- \[(?<mark>[ xX])\] (?<body>STATUS=(?<status>[A-Z_]+)(?: \| .*)?)$'
))
if ($trailCheckboxes.Count -eq 0) {
    throw 'A trilha não contém linhas operacionais reconhecíveis.'
}

$validStatuses = @(
    'CONCLUIDO',
    'AGORA',
    'CANDIDATO',
    'EXTERNAL_HOLD',
    'CONDICIONAL',
    'MARCO_CONSOLIDADO'
)
$checkedTrail = @($trailCheckboxes | Where-Object { $_.Groups['mark'].Value -match '[xX]' })
$openTrail = @($trailCheckboxes | Where-Object { $_.Groups['mark'].Value -eq ' ' })
$nowTrail = @($trailCheckboxes | Where-Object { $_.Groups['status'].Value -ceq 'AGORA' })

if ($nowTrail.Count -ne 1 -or $nowTrail[0].Groups['mark'].Value -ne ' ') {
    throw 'A trilha deve ter exatamente uma linha aberta STATUS=AGORA.'
}

foreach ($item in $trailCheckboxes) {
    $status = $item.Groups['status'].Value
    $isChecked = $item.Groups['mark'].Value -match '[xX]'
    if ($status -cnotin $validStatuses) {
        throw "Status desconhecido na trilha: $status."
    }
    if ($isChecked -and $status -cnotin @('CONCLUIDO', 'MARCO_CONSOLIDADO')) {
        throw "Linha marcada usa status aberto: $status."
    }
    if (-not $isChecked -and $status -cin @('CONCLUIDO', 'MARCO_CONSOLIDADO')) {
        throw "Linha aberta usa status concluído: $status."
    }
}

foreach ($item in $checkedTrail) {
    if ($item.Groups['body'].Value -cnotmatch '(?:^| \| )EVIDENCIA=STATES\.md(?:$| \| )') {
        throw 'Toda linha histórica marcada deve apontar EVIDENCIA=STATES.md.'
    }
}

$checkedStateTasks = @(
    [regex]::Matches(
        $stateText,
        '(?m)^\s*- \[x\] \*\*(?<task>V2-[0-9]{3}[a-z]?(?:/[0-9]+)?)\b'
    ) |
        ForEach-Object { $_.Groups['task'].Value }
)
$checkedTrailTasks = @(
    $checkedTrail | ForEach-Object {
        $taskMatch = [regex]::Match(
            $_.Groups['body'].Value,
            '(?:^| \| )TAREFA=(?<task>V2-[0-9]{3}[a-z]?(?:/[0-9]+)?)(?:$| \| )'
        )
        if (-not $taskMatch.Success) {
            throw 'Toda linha histórica marcada deve identificar uma TAREFA exata.'
        }
        $taskMatch.Groups['task'].Value
    }
)
$duplicateCheckedTrailTasks = @($checkedTrailTasks | Group-Object | Where-Object Count -gt 1)
$historicalDifference = @(Compare-Object -ReferenceObject $checkedStateTasks -DifferenceObject $checkedTrailTasks)
if ($duplicateCheckedTrailTasks.Count -gt 0 -or
    $checkedStateTasks.Count -ne $checkedTrailTasks.Count -or
    $historicalDifference.Count -gt 0) {
    throw 'O histórico marcado da trilha não espelha exatamente os checkboxes concluídos do STATES.md.'
}

$routes = [System.Collections.Generic.List[string]]::new()
foreach ($item in $openTrail) {
    $routeMatch = [regex]::Match($item.Groups['body'].Value, '(?:^| \| )ROTA=(?<route>[^ |]+)')
    if (-not $routeMatch.Success) {
        throw 'Toda linha aberta deve possuir uma ROTA explícita.'
    }
    $routes.Add($routeMatch.Groups['route'].Value)
}
$duplicateRoutes = @($routes | Group-Object | Where-Object Count -gt 1)
if ($duplicateRoutes.Count -gt 0) {
    throw "Há ROTA duplicada na trilha: $($duplicateRoutes[0].Name)."
}

$expectedRouteFamilies = [ordered]@{
    '^P\d{2}$' = 7
    '^G\d{2}$' = 10
    '^V\d{2}$' = 16
    '^R\d{2}$' = 2
    '^D\d{2}$' = 5
    '^Q-' = 60
    '^RAS-' = 11
    '^F-' = 5
    '^C-' = 19
    '^O-' = 72
    '^Z\d{2}$' = 6
}
foreach ($family in $expectedRouteFamilies.GetEnumerator()) {
    $actual = @($routes | Where-Object { $_ -match $family.Key }).Count
    if ($actual -ne $family.Value) {
        throw "A família de rotas '$($family.Key)' deveria ter $($family.Value), mas tem $actual."
    }
}

$panelState = [regex]::Match(
    $trailText,
    '\| Checkboxes no STATES\.md \| \*\*(?<total>\d+): (?<done>\d+) concluídos e (?<open>\d+) pendentes\*\* \|'
)
$panelOpenRoutes = [regex]::Match(
    $trailText,
    '\| Fatias abertas materializadas nesta trilha \| \*\*(?<open>\d+)\*\* \|'
)
$stateCheckboxes = @([regex]::Matches($stateText, '(?m)^\s*- \[(?<mark>[ xX])\]'))
$stateDone = @($stateCheckboxes | Where-Object { $_.Groups['mark'].Value -match '[xX]' }).Count
$stateOpen = $stateCheckboxes.Count - $stateDone

if ((Get-CapturedInteger -Match $panelState -Group 'total') -ne $stateCheckboxes.Count -or
    (Get-CapturedInteger -Match $panelState -Group 'done') -ne $stateDone -or
    (Get-CapturedInteger -Match $panelState -Group 'open') -ne $stateOpen) {
    throw 'As contagens do painel não espelham os checkboxes do STATES.md.'
}
if ((Get-CapturedInteger -Match $panelOpenRoutes -Group 'open') -ne $openTrail.Count) {
    throw 'A quantidade de fatias abertas do painel diverge das linhas da trilha.'
}

$checkedHeadings = @([regex]::Matches(
    $stateText,
    '(?m)^\s*- \[x\] \*\*(?<heading>V2-[^*]+)\*\*'
))
$duplicateCheckedHeadings = @(
    $checkedHeadings |
        ForEach-Object { $_.Groups['heading'].Value } |
        Group-Object |
        Where-Object Count -gt 1
)
if ($duplicateCheckedHeadings.Count -gt 0) {
    throw "Há tarefa concluída duplicada no STATES.md: $($duplicateCheckedHeadings[0].Name)."
}

$trailTasks = @(
    [regex]::Matches($trailText, 'TAREFA=(?<task>V2-[0-9]{3}[a-z]?)') |
        ForEach-Object { $_.Groups['task'].Value } |
        Sort-Object -Unique
)
foreach ($task in $trailTasks) {
    $taskPattern = '(?<![A-Za-z0-9-])' + [regex]::Escape($task) + '(?![A-Za-z0-9])'
    if (-not [regex]::IsMatch($stateText, $taskPattern)) {
        throw "A tarefa $task existe na trilha, mas não no STATES.md."
    }
}

$aggregateCoverage = @(
    [regex]::Matches($trailText, '(?m)^\| (?<task>V2-[0-9]{3}[a-z]?) \|') |
        ForEach-Object { $_.Groups['task'].Value } |
        Sort-Object -Unique
)
$coveredTasks = @($trailTasks + $aggregateCoverage | Sort-Object -Unique)
$openStateTasks = @(
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*(?<task>V2-[0-9]{3}[a-z]?)\b') |
        ForEach-Object { $_.Groups['task'].Value } |
        Sort-Object -Unique
)
foreach ($task in $openStateTasks) {
    if ($task -cnotin $coveredTasks) {
        throw "A tarefa aberta $task do STATES.md não possui rota nem cobertura agregada na trilha."
    }
}

if ($stateText -cnotmatch 'Bloco 24 — V2-009b — somente identidade e grão 6399 \(Manifestos\), em Sol Ultra') {
    throw 'O próximo bloco canônico do STATES.md não é o Bloco 24 de identidade 6399.'
}
if ($nowTrail[0].Groups['body'].Value -cnotmatch 'BLOCO=24 \| TAREFA=V2-009b \| ESCOPO=identidade e grão 6399 - Manifestos \| MODELO=SOL_ULTRA') {
    throw 'STATUS=AGORA não espelha o próximo bloco canônico do STATES.md.'
}

$markdownLinks = [regex]::Matches($trailText, '\[[^\]]+\]\((?<target>[^)]+)\)')
foreach ($link in $markdownLinks) {
    $target = $link.Groups['target'].Value
    if ($target -match '^(?:https?://|#)') {
        continue
    }
    $relativeTarget = ($target -split '#', 2)[0].Replace('/', [System.IO.Path]::DirectorySeparatorChar)
    $resolvedTarget = [System.IO.Path]::GetFullPath((Join-Path (Split-Path $trailPath -Parent) $relativeTarget))
    if (-not $resolvedTarget.StartsWith($repositoryPrefix, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $resolvedTarget -PathType Leaf)) {
        throw "Link local inválido ou fora do repositório: $target."
    }
}

Write-Output "PASS: trilha GPT-5.6 validada com $($checkedTrail.Count) marcos históricos, $($openTrail.Count) fatias abertas, $($stateDone)/$($stateCheckboxes.Count) checkboxes concluídos no STATES.md e uma única rota AGORA."
