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
$fleetDecisionPath = Join-Path $repositoryRoot 'docs\catalogos\frota-manifestos-v2-035c\decisao-v02.json'
$terraFleetRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\frota-manifestos-v2-035b-execucao-terra.md'
$rotationRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\conter-e-rotacionar-segredos.md'
$rotationAttestationValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-V2041RotationAttestation.ps1'
$freightDecisionPath = Join-Path $repositoryRoot 'docs\catalogos\fretes-v2-011\decisao-v01.json'
$freightDecisionRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-011a-decisao-fretes-sol.md'
$freightDecisionValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-FretesV2011DecisionCatalog.ps1'
$freightShadowManifestPath = Join-Path $repositoryRoot 'database\manifest\fretes-shadow-vertical.json'
$freightShadowRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-011-fretes-shadow-base.md'
$freightShadowValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-FretesV2011ShadowVertical.ps1'
$locationDecisionPath = Join-Path $repositoryRoot 'docs\catalogos\localizacao-cargas-v2-028\decisao-v01.json'
$locationDecisionFixturePath = Join-Path $repositoryRoot 'docs\catalogos\localizacao-cargas-v2-028\fixtures\casos-v01.synthetic.json'
$locationDecisionRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-028a-decisao-localizacao-cargas-sol.md'
$locationDecisionValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-LocalizacaoCargasV2028DecisionCatalog.ps1'
$locationShadowManifestPath = Join-Path $repositoryRoot 'database\manifest\localizacao-cargas-shadow-vertical.json'
$locationShadowRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-028-localizacao-cargas-shadow.md'
$locationShadowValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-LocalizacaoCargasV2028ShadowVertical.ps1'
$qfnd02ManifestPath = Join-Path $repositoryRoot 'docs\catalogos\caracterizacao-v2-012\q-fnd-02\manifesto.json'
$qfnd02ManifestHashPath = Join-Path $repositoryRoot 'docs\catalogos\caracterizacao-v2-012\q-fnd-02\manifesto.sha256'
$qfnd02AdrPath = Join-Path $repositoryRoot 'docs\adr\0029-extensao-aditiva-perfis-fretes-localizacao.md'
$qfnd02RunbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-012-q-fnd-02-perfis-fretes-localizacao-sol.md'
$qfnd02ValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-V2012FretesLocalizacaoProfileExtension.ps1'
$sweepFoundationManifestPath = Join-Path $repositoryRoot 'docs\catalogos\sweep-v2-013\manifesto.json'
$sweepFoundationManifestHashPath = Join-Path $repositoryRoot 'docs\catalogos\sweep-v2-013\manifesto.sha256'
$sweepFoundationRunbookPath = Join-Path $repositoryRoot 'docs\runbooks\v2-013-fundacao-kernel-sweep-preview-sol.md'
$sweepFoundationValidatorPath = Join-Path $repositoryRoot 'scripts\validation\Test-V2013SweepPreviewFoundation.ps1'

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
$b55Phase = Test-Path (Join-Path $repositoryRoot 'database/manifest/runtime-bloco55-acceptances.json')
if ($b55Phase) {
    & (Join-Path $PSScriptRoot 'Test-Bloco55Acceptances.ps1') | Out-Null
    if ([regex]::Matches($stateText, '(?m)^  - \[x\] \*\*V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS —').Count -ne 1 -or
        [regex]::Matches($trailText, '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=P02V \| BLOCO=55 \| TAREFA=V2-022/INTEGRACAO_LOCAL_CINCO_VERTICAIS \|').Count -ne 1 -or
        [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=55 \|').Count -ne 1) {
        throw 'B55_SINGLE_COMPLETE_LOCAL_SLICE_REQUIRED'
    }
}
$fleetDecisionText = Read-StrictUtf8 -LiteralPath $fleetDecisionPath -MaximumBytes 512KB
$rotationRunbookText = Read-StrictUtf8 -LiteralPath $rotationRunbookPath -MaximumBytes 256KB
$freightDecisionText = Read-StrictUtf8 -LiteralPath $freightDecisionPath -MaximumBytes 128KB
$freightShadowManifestText = Read-StrictUtf8 -LiteralPath $freightShadowManifestPath -MaximumBytes 128KB
$locationDecisionText = Read-StrictUtf8 -LiteralPath $locationDecisionPath -MaximumBytes 160KB
$locationDecisionFixtureText = Read-StrictUtf8 -LiteralPath $locationDecisionFixturePath -MaximumBytes 160KB
$locationShadowManifestText = Read-StrictUtf8 -LiteralPath $locationShadowManifestPath -MaximumBytes 160KB
$qfnd02ManifestText = Read-StrictUtf8 -LiteralPath $qfnd02ManifestPath -MaximumBytes 512KB
$sweepFoundationManifestText = Read-StrictUtf8 -LiteralPath $sweepFoundationManifestPath -MaximumBytes 512KB
try {
    $fleetDecision = $fleetDecisionText | ConvertFrom-Json -Depth 100
    $freightDecision = $freightDecisionText | ConvertFrom-Json -Depth 100 -DateKind String
    $freightShadowManifest = $freightShadowManifestText | ConvertFrom-Json -Depth 100 -DateKind String
    $locationDecision = $locationDecisionText | ConvertFrom-Json -Depth 100 -DateKind String
    $locationDecisionFixture = $locationDecisionFixtureText | ConvertFrom-Json -Depth 100 -DateKind String
    $locationShadowManifest = $locationShadowManifestText | ConvertFrom-Json -Depth 100 -DateKind String
    $qfnd02Manifest = $qfnd02ManifestText | ConvertFrom-Json -Depth 100 -DateKind String
    $sweepFoundationManifest = $sweepFoundationManifestText | ConvertFrom-Json -Depth 100 -DateKind String
} catch {
    throw 'Uma decisão canônica obrigatória não é JSON válido.'
}

$replacementCharacter = [string][char]0xFFFD
if ($stateText.Contains($replacementCharacter, [System.StringComparison]::Ordinal) -or
    $trailText.Contains($replacementCharacter, [System.StringComparison]::Ordinal)) {
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
$localRecoveryClosed = $stateText -cmatch '(?m)^  - \[x\] \*\*V2-022/RECUPERACAO_DURAVEL_LOCAL —'
$noEligibleSelection = $stateText -cmatch '(?m)^5\. \*\*Próximo bloco atual:\*\* \*\*Nenhum próximo bloco oficial está elegível\.'

if ($nowTrail.Count -gt 1 -or ($nowTrail.Count -eq 1 -and $nowTrail[0].Groups['mark'].Value -ne ' ')) {
    throw 'A trilha deve ter no máximo uma linha aberta STATUS=AGORA.'
}
if ($nowTrail.Count -eq 0 -and -not $noEligibleSelection) {
    throw 'A ausência de STATUS=AGORA exige bloqueio explícito do próximo bloco no STATES.md.'
}
if ($nowTrail.Count -eq 1 -and $noEligibleSelection) {
    throw 'Há STATUS=AGORA apesar do bloqueio explícito do seletor no STATES.md.'
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
        '(?m)^\s*- \[x\] \*\*(?<task>V2-[0-9]{3}[a-z]?(?:/(?:[0-9]+|[A-Z][A-Z0-9_-]*))?)\b'
    ) |
        ForEach-Object { $_.Groups['task'].Value }
)
$checkedTrailTasks = @(
    $checkedTrail | ForEach-Object {
        $taskMatch = [regex]::Match(
            $_.Groups['body'].Value,
            '(?:^| \| )TAREFA=(?<task>V2-[0-9]{3}[a-z]?(?:/(?:[0-9]+|[A-Z][A-Z0-9_-]*))?)(?:$| \| )'
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

$r01Routes = @($trailCheckboxes | Where-Object {
    $_.Groups['body'].Value -cmatch '(?:^| \| )ROTA=R01(?:$| \| )'
})
$r01StateDependency = [regex]::IsMatch(
    $stateText,
    '(?m)^\s{2}- \[[ xX]\] \*\*V2-046a\b[^\r\n]*\r?\n\s{4}- \*\*Depende de:\*\* [^\r\n]*(?<![A-Za-z0-9-])V2-022(?![A-Za-z0-9-])[^\r\n]*$'
)
if ($r01Routes.Count -ne 1 -or -not $r01StateDependency) {
    throw 'R01 deve existir uma vez e preservar a dependência agregada V2-022 definida para V2-046a no STATES.md.'
}
$r01DependencyMatch = [regex]::Match(
    $r01Routes[0].Groups['body'].Value,
    '(?:^| \| )DEPENDE=(?<dependencies>[^ |]+)(?:$| \| )'
)
$r01Dependencies = if ($r01DependencyMatch.Success) {
    @($r01DependencyMatch.Groups['dependencies'].Value -csplit '\+')
} else {
    @()
}
if ($r01Dependencies -cnotcontains 'V2-022b') {
    throw 'R01 deve declarar V2-022b como predecessor fail-closed do dispatcher operacional.'
}

$v2041Section = [regex]::Match(
    $stateText,
    '(?ms)^- \[ \] \*\*V2-041\b(?<body>.*?)(?=^- \[[ xX]\] \*\*V2-016\b)'
)
$g01Routes = @($openTrail | Where-Object {
    $_.Groups['body'].Value -cmatch '(?:^| \| )ROTA=G01(?:$| \| )'
})
$rotationIntakeSections = @([regex]::Matches(
    $rotationRunbookText,
    '(?ms)^## Intake local fail-closed da evidência\r?\n(?<body>.*?)(?=^## |\z)'
))
$intakeStatePattern = 'CONTRACT_READY_EVIDENCE_NOT_RECEIVED'
if (-not $v2041Section.Success -or $g01Routes.Count -ne 1 -or $rotationIntakeSections.Count -ne 1) {
    throw 'V2-041, G01 e a seção de intake do runbook devem existir uma vez para preservar o gate fail-closed.'
}
$stateIntakes = @([regex]::Matches($v2041Section.Groups['body'].Value, "INTAKE_STATE=(?<state>$intakeStatePattern)(?= \|)"))
$runbookIntakes = @([regex]::Matches($rotationIntakeSections[0].Groups['body'].Value, "(?m)^INTAKE_STATE=(?<state>$intakeStatePattern)$"))
$trailIntakes = @([regex]::Matches($g01Routes[0].Groups['body'].Value, "(?:^| \| )INTAKE_STATE=(?<state>$intakeStatePattern)(?:$| \| )"))
if ($stateIntakes.Count -ne 1 -or $runbookIntakes.Count -ne 1 -or $trailIntakes.Count -ne 1 -or
    $stateIntakes[0].Groups['state'].Value -cne $runbookIntakes[0].Groups['state'].Value -or
    $stateIntakes[0].Groups['state'].Value -cne $trailIntakes[0].Groups['state'].Value) {
    throw 'O estado do intake V2-041 deve coincidir uma única vez entre STATES.md, runbook e G01.'
}
$intakeInvariantPattern = 'MAX_LOCAL_OUTCOME=STRUCTURALLY_VALID_UNVERIFIED \| INTAKE_UNLOCKS=NONE(?=$|[\r\n <])'
foreach ($intakeSource in @(
    $v2041Section.Groups['body'].Value,
    $rotationIntakeSections[0].Groups['body'].Value,
    $g01Routes[0].Groups['body'].Value
)) {
    if ([regex]::Matches($intakeSource, 'INTAKE_STATE=').Count -ne 1 -or
        [regex]::Matches($intakeSource, 'MAX_LOCAL_OUTCOME=').Count -ne 1 -or
        [regex]::Matches($intakeSource, 'INTAKE_UNLOCKS=').Count -ne 1 -or
        [regex]::Matches($intakeSource, $intakeInvariantPattern).Count -ne 1) {
        throw 'Cada fonte deve declarar uma única vez o estado, o teto local e INTAKE_UNLOCKS=NONE de V2-041.'
    }
}
$g01Body = $g01Routes[0].Groups['body'].Value
$expectedG01Keys = @(
    'STATUS',
    'ROTA',
    'TAREFA',
    'ESCOPO',
    'MODELO',
    'HOLD_ATUAL',
    'FECHAMENTO_REMOVE_SOMENTE_PRECONDICAO',
    'AUTORIZACAO_POSTERIOR',
    'INTAKE_STATE',
    'MAX_LOCAL_OUTCOME',
    'INTAKE_UNLOCKS',
    'GATE'
)
$actualG01Keys = @(
    foreach ($g01Segment in @($g01Body -csplit ' \| ')) {
        $g01KeyMatch = [regex]::Match($g01Segment, '^(?<key>[A-Z][A-Z0-9_]*)=')
        if (-not $g01KeyMatch.Success) {
            throw 'G01 contém segmento sem chave canônica uppercase.'
        }
        $g01KeyMatch.Groups['key'].Value
    }
)
if ($actualG01Keys.Count -ne $expectedG01Keys.Count) {
    throw 'G01 deve conter somente o conjunto fechado de chaves canônicas.'
}
foreach ($g01Key in $expectedG01Keys) {
    if (@($actualG01Keys | Where-Object { $_ -ceq $g01Key }).Count -ne 1) {
        throw "G01 deve declarar a chave $g01Key exatamente uma vez."
    }
}
if ($g01Routes[0].Groups['status'].Value -cne 'EXTERNAL_HOLD' -or
    $g01Body -cnotmatch '(?:^| \| )TAREFA=V2-041(?:$| \| )' -or
    $g01Body -match '(?:^| \| )BLOCO=' -or
    $g01Body -match '(?:^| \| )DESBLOQUEIA=' -or
    $g01Body -cnotmatch '(?:^| \| )HOLD_ATUAL=rede\+V2-025d\+release\+deploy\+cutover(?:$| \| )' -or
    $g01Body -cnotmatch '(?:^| \| )FECHAMENTO_REMOVE_SOMENTE_PRECONDICAO=V2-041(?:$| \| )' -or
    $g01Body -cnotmatch '(?:^| \| )AUTORIZACAO_POSTERIOR=SEPARADA(?:$| \| )' -or
    $g01Body -cnotmatch '(?:^| \| )GATE=Test-V2041RotationAttestation\.ps1(?:$| \| )' -or
    -not (Test-Path -LiteralPath $rotationAttestationValidatorPath -PathType Leaf)) {
    throw 'G01 deve permanecer EXTERNAL_HOLD sem bloco e referenciar um gate local existente.'
}

$entityQ01RoutePattern = 'Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-01'
$expectedRouteFamilies = [ordered]@{
    '^P\d{2}$' = 4
    '^P\d{2}R$' = 0
    '^G\d{2}$' = $(if ($b55Phase) { 6 } else { 9 })
    '^P\d{2}V$' = 0
    '^V\d{2}$' = 8
    '^V\d{2}A$' = 0
    '^R\d{2}$' = 2
    '^D\d{2}$' = 5
    '^Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-\d{2}$' = 60
    '^RAS-' = 6
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
$expectedCounts = if ($b55Phase) { @(115,65,50,193) } else { @(114,60,54,196) }
if ($stateCheckboxes.Count -ne $expectedCounts[0] -or $stateDone -ne $expectedCounts[1] -or $stateOpen -ne $expectedCounts[2] -or
    $checkedTrail.Count -ne $expectedCounts[1] -or $openTrail.Count -ne $expectedCounts[3]) {
    throw 'A auditoria exige a fotografia exata B54 ou a fase B55 comprovada separadamente.'
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

if ($nowTrail.Count -ne 0 -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=G05A | BLOCO=42 | TAREFA=V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED | FATIA=DECISAO_SEGURANCA_VERSIONADA |') -or
    -not $trailText.Contains('| RESULTADO=POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=G05T | BLOCO=43 | TAREFA=V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED | FATIA=APLICACAO_MECANICA_E_HARNESS |') -or
    -not $trailText.Contains('MODELO_RECOMENDADO=TERRA_XHIGH | MODELO_EXECUTADO=SOL_ULTRA | MOTIVO=LOTE_UNICO_AUTORIZADO_PELO_OWNER') -or
    -not $trailText.Contains('| RESULTADO=LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [ ] STATUS=EXTERNAL_HOLD | ROTA=G05 | TAREFA=V2-015d | ESCOPO=executar feed/NVD autorizado, aceitar política, classificar achados/exceções e registrar primeira baseline real | MODELO=SOL_ULTRA+ACAO_HUMANA | DEPENDE=G05T+feed+política+owner') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=Q-BST-01 | BLOCO=41 | TAREFA=V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE | FATIA=FUNDACAO_PLANEJAMENTO_OFFLINE |') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=G12 | BLOCO=40 | TAREFA=V2-015e | FATIA=CORRECAO_INTERCAMADAS_V010_V011 |') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=V08 | BLOCO=44 | TAREFA=V2-011a | FATIA=DECISAO_LOCAL |') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=V09 | BLOCO=45 | TAREFA=V2-011 | FATIA=EXECUCAO_BASE_SHADOW |') -or
    -not $trailText.Contains('| RESULTADO=IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=V10A | BLOCO=46 | TAREFA=V2-028a | FATIA=DECISAO_LOCAL |') -or
    -not $trailText.Contains('| RESULTADO=COMPLETE_LOCAL_DECISION_ONLY | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=V10 | BLOCO=47 | TAREFA=V2-028 | FATIA=EXECUCAO_BASE_SHADOW |') -or
    -not $trailText.Contains('| RESULTADO=IMPLEMENTADA_EM_SHADOW | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=Q-FND-02 | BLOCO=48 | TAREFA=V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE | FATIA=FUNDACAO_OFFLINE_TEST_ONLY |') -or
    -not $trailText.Contains('| RESULTADO=FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=Q-SWP-FND-01 | BLOCO=49 | TAREFA=V2-013/FUNDACAO_KERNEL_LOCAL | FATIA=FUNDACAO_KERNEL_LOCAL_FAIL_CLOSED |') -or
    -not $trailText.Contains('| RESULTADO=FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED | EVIDENCIA=STATES.md') -or
    -not $trailText.Contains('- [x] STATUS=CONCLUIDO | ROTA=Q-MED-FND-01 | BLOCO=50 | TAREFA=V2-050/FUNDACAO_MEDICAO_LOCAL | FATIA=FUNDACAO_MEDICAO_LOCAL_MULTIESCALA_TEST_ONLY |') -or
    -not $trailText.Contains('| RESULTADO=FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED | EVIDENCIA=STATES.md') -or
    (-not $b55Phase -and $trailText -cnotmatch '(?m)^\| Próximo bloco oficial \| \*\*Bloco 54 concluído localmente; sem sucessor elegível\*\* \|') -or
    ($b55Phase -and $trailText -cnotmatch '(?m)^\| Próximo bloco oficial \| \*\*Bloco 55 concluído localmente; sem sucessor elegível\*\* \|') -or
    $trailText -cnotmatch '(?m)^\| Modelo do próximo chat \| \*\*Astra — preferência do owner para implementação complexa\*\* \|') {
    throw 'A trilha deve concluir V08/44, V09/45, V10A/46, V10/47, Q-FND-02/48, Q-SWP-FND-01/49 e Q-MED-FND-01/50, preservando B51/P02M e B52/P02R concluído somente localmente.'
}

$foundationState = @([regex]::Matches(
    $stateText,
    '(?m)^\s*- \[x\] \*\*V2-012/FUNDACAO_LOCAL\b'
))
if ($foundationState.Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-015e\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-015\b').Count -ne 1 -or
    -not $stateText.Contains('Bloco 39, <code>Q-FND-01</code>, <code>FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES</code>')) {
    throw 'O STATES.md não preserva Q-FND-01, V2-015 agregada aberta e o fechamento isolado de G12/V2-015e.'
}
foreach ($openStateMarker in @('- [ ] **V2-012 —', '  - [ ] **V2-012a —', '  - [ ] **V2-012b —', '  - [ ] **V2-012c —')) {
    if (-not $stateText.Contains($openStateMarker)) {
        throw "O estado agregado foi indevidamente concluído: $openStateMarker."
    }
}

$foundationTrail = @([regex]::Matches(
    $trailText,
    '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-FND-01 \| BLOCO=39 \| TAREFA=V2-012/FUNDACAO_LOCAL \| FATIA=FUNDACAO_OFFLINE_PROVIDER_NEUTRAL \| .* \| RESULTADO=FOUNDATION_OFFLINE_COMPLETE_Q01_PENDING_ORACLES \| EVIDENCIA=STATES\.md$'
))
if ($foundationTrail.Count -ne 1) {
    throw 'A trilha não registra exatamente uma conclusão Q-FND-01/V2-012/FUNDACAO_LOCAL no Bloco 39.'
}
$qfnd02Trail = @([regex]::Matches(
    $trailText,
    '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-FND-02 \| BLOCO=48 \| TAREFA=V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE \| FATIA=FUNDACAO_OFFLINE_TEST_ONLY \| .* \| RESULTADO=FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES \| EVIDENCIA=STATES\.md$'
))
if ($qfnd02Trail.Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-013/FUNDACAO_KERNEL_LOCAL\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL\b').Count -ne 1) {
    throw 'Os fechamentos Q-FND-02/B48, Q-SWP-FND-01/B49 e Q-MED-FND-01/B50 não estão íntegros.'
}
foreach ($finalEvidenceMarker in @(
    '<code>clean verify</code> offline final passou com 609 testes',
    '14 validadores, o self-test de nove casos',
    'scanner offline completo sobre 860 candidatos/859 textos/um binário'
)) {
    if (-not $trailText.Contains($finalEvidenceMarker)) {
        throw "A trilha não registra a evidência final de Q-FND-01: $finalEvidenceMarker."
    }
}
if (-not $stateText.Contains('totalizando 14 validadores finais') -or
    -not $stateText.Contains('passou com 860 candidatos, 859 textos, um binário verificado e zero finding')) {
    throw 'O STATES.md não registra integralmente os gates finais executados para Q-FND-01.'
}
if ([regex]::IsMatch($trailText, "(?m)^- \[x\].*\| ROTA=$entityQ01RoutePattern \|")) {
    throw 'Uma caracterização Q-*-01 de entidade foi indevidamente concluída.'
}

$bootstrapFoundationState = @([regex]::Matches(
    $stateText,
    '(?m)^\s*- \[x\] \*\*V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE\b'
))
$bootstrapFoundationTrail = @([regex]::Matches(
    $trailText,
    '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-BST-01 \| BLOCO=41 \| TAREFA=V2-047/FUNDACAO_PLANEJAMENTO_OFFLINE \| FATIA=FUNDACAO_PLANEJAMENTO_OFFLINE \| .* \| RESULTADO=FOUNDATION_OFFLINE_COMPLETE_BOOTSTRAP_EXECUTION_BLOCKED \| EVIDENCIA=STATES\.md$'
))
if ($bootstrapFoundationState.Count -ne 1 -or
    $bootstrapFoundationTrail.Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-047\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-015d/DECISAO_POLITICA_LOCAL_FAIL_CLOSED\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-015d/IMPLEMENTACAO_LOCAL_FAIL_CLOSED\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-015d —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-015 —').Count -ne 1 -or
    -not $localRecoveryClosed) {
    throw 'O estado deve fechar G05A/G05T, manter V2-015d/V2-015 abertas e registrar P02R concluído somente localmente.'
}
foreach ($block41EvidenceMarker in @(
    'red inicial do novo validator pela ausência de <code>manifesto.json</code>',
    '17 linhas, seis entidades planejáveis, 16 fontes canônicas e 34 cenários sintéticos',
    'drift preexistente de <code>PUB-07</code>/ADR 0025',
    'varredura offline passou com 870 candidatos/869 textos/um binário e zero finding',
    'Maven, SQLCMD e gate progressivo não eram aplicáveis'
)) {
    if (-not $trailText.Contains($block41EvidenceMarker)) {
        throw "A trilha não registra a evidência final do Bloco 41: $block41EvidenceMarker."
    }
}
if (-not $stateText.Contains('27 negativos com reason codes únicos') -or
    -not $stateText.Contains('12 arquivos alterados/relevantes passaram UTF-8 estrito sem BOM') -or
    -not $stateText.Contains('**Aceite Sol e evidência em 06/09/2026:** <code>POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING</code>.') -or
    -not $stateText.Contains('<code>POM_FAIL_BUILD_ON_CVSS_NOT_ZERO current=11</code>') -or
    -not $stateText.Contains('<code>WORKFLOW_ARTIFACT_MISSING_BEHAVIOR_NOT_ERROR current=warn</code>') -or
    -not $stateText.Contains('33 casos sintéticos, três PASS, 30 BLOCK e zero exceção efetiva') -or
    [regex]::Matches($trailText, '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=G05A \| BLOCO=42 \| .* \| RESULTADO=POLICY_DECISION_LOCAL_COMPLETE_TERRA_IMPLEMENTATION_PENDING \| EVIDENCIA=STATES\.md$').Count -ne 1) {
    throw 'O estado e a trilha não registram integralmente o fechamento auditável do Bloco 42/G05A.'
}
if (-not $stateText.Contains('**Execução e retomada G05T em 06/09/2026 — concluída localmente:**') -or
    -not $stateText.Contains('terminou <code>BUILD SUCCESS</code> às 14:30:18 -03:00 com 656 testes, zero falhas, zero erros e um skip esperado') -or
    -not $stateText.Contains('não existe rota <code>AGORA</code> elegível e o Bloco 44 não foi atribuído') -or
    -not $stateText.Contains('a regressão dirigida passou com <code>javac -encoding UTF-8</code>') -or
    -not $trailText.Contains('G05T foi concluída no Bloco 43 como <code>LOCAL_FAIL_CLOSED_IMPLEMENTATION_COMPLETE_AUTHORIZED_FEED_BASELINE_PENDING</code>') -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=44 \|').Count -ne 1) {
    throw 'O estado e a trilha devem preservar a prova de G05T/Bloco 43 e atribuir o Bloco 44 somente a V08.'
}

if ([regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-011a\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-011 —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-028a\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-028 —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-012/FUNDACAO_PERFIS_FRETES_LOCALIZACAO_OFFLINE\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-013 —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-013/FUNDACAO_KERNEL_LOCAL\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-050 —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-046a\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-046b\b').Count -ne 1 -or
    [string]$freightDecision.task -cne 'V2-011a' -or
    [string]$freightDecision.decisionStatus -cne 'COMPLETE_LOCAL_DECISION_ONLY' -or
    [string]$freightDecision.execution.baseShadowGate -cne 'V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW' -or
    [string]$freightDecision.execution.relationalParityGates -cne 'V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES' -or
    [string]$freightDecision.execution.crosswalkOwner -cne 'V2-046b' -or
    -not $stateText.Contains('<code>V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW</code>') -or
    -not $stateText.Contains('<code>V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES</code>') -or
    -not $trailText.Contains('GATE_BASE=V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW') -or
    -not $trailText.Contains('GATES_RELACIONAIS=V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES') -or
    [string]$freightShadowManifest.roadmapTask -cne 'V2-011' -or
    [string]$freightShadowManifest.route -cne 'V09' -or
    [int]$freightShadowManifest.block -ne 45 -or
    [string]$freightShadowManifest.localState -cne 'IMPLEMENTADA_EM_SHADOW_RELATIONS_PENDING' -or
    [string]$freightShadowManifest.dependencyPolicy.baseShadow -cne 'V2_046A_NOT_REQUIRED_FOR_BASE_SHADOW' -or
    [string]$freightShadowManifest.dependencyPolicy.relationalParity -cne 'V2_046A/V2_046B_REQUIRED_ONLY_FOR_RELATIONAL/PARITY_GATES' -or
    [string]$freightShadowManifest.dependencyPolicy.crosswalkOwner -cne 'V2-046b' -or
    [string]$freightShadowManifest.dependencyPolicy.v2_046a -cne 'OPEN' -or
    [string]$freightShadowManifest.dependencyPolicy.v2_046b -cne 'OPEN' -or
    [int]$freightShadowManifest.contract.maximumPageSize -ne 100 -or
    [string]$freightShadowManifest.portabilityEnforcement.scope -cne 'V2-017a_APPLICABLE_6389_SLICE_ONLY_AGGREGATE_REMAINS_OPEN' -or
    -not (Test-Path -LiteralPath $freightDecisionRunbookPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $freightDecisionValidatorPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $freightShadowRunbookPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $freightShadowValidatorPath -PathType Leaf)) {
    throw 'Os fechamentos V08/V09/V10/Q-FND-02/Q-SWP-FND-01/Q-MED-FND-01 não preservam as decisões, verticais base ou gates relacionais.'
}
if ([string]$locationDecision.task -cne 'V2-028a' -or
    [string]$locationDecision.route -cne 'V10A' -or
    [int]$locationDecision.block -ne 46 -or
    [string]$locationDecision.decisionStatus -cne 'COMPLETE_LOCAL_DECISION_ONLY' -or
    [string]$locationDecision.sourceContract.id -cne 'dataexport-8656' -or
    [string]$locationDecision.sourceContract.releaseFingerprint -cne 'da95fc17fc3db2fae7635c5456166d72af844b1d826e84ab6c2ba8b6f1b65c24' -or
    [string]$locationDecision.identityDecision.identityFingerprint -cne '14af11dce5fd7696238907b72f8f6c8c77f86a4cff3045b489da5eb132c0d6f0' -or
    [string]$locationDecision.identity.sourceKey.path -cne '/corporation_sequence_number' -or
    [string]$locationDecision.identity.forbiddenAlias.path -cne '/sequence_number' -or
    [string]$locationDecision.volume.downstreamProjection -cne 'COALESCE(volumes_localizacao, volumes_fretes, 0)' -or
    [string]$locationDecision.volume.freightCandidate.storage -cne 'PRESENCE_AND_PROVENANCE_ONLY_NO_RELATION' -or
    [string]$locationDecision.freshness.path -cne '/service_at' -or
    [string]$locationDecision.freshness.bothNullDivergent -cne 'QUARANTINE_STABLE_REASON_NEVER_KEEP_LAST' -or
    [string]$locationDecision.status.nullOrBlank -cne 'sem_status' -or
    [string]$locationDecision.status.unknown -cne 'PRESERVE_RAW_AS_NON_TERMINAL_NO_INFERENCE' -or
    [string]$locationDecision.unsourcedLegacy.state -cne 'ABSENT' -or
    [string]$locationDecision.unsourcedLegacy.provenance -cne 'UNSOURCED_LEGACY' -or
    [string]$locationDecision.presence.sweep -cne 'DISABLED_NO_COMPLETENESS_PROOF' -or
    [bool]$locationDecision.historicalLimits.appliesAsV2Defaults -or
    @($locationDecision.rules).Count -ne 7 -or
    @($locationDecisionFixture.cases).Count -ne 24 -or
    -not (Test-Path -LiteralPath $locationDecisionRunbookPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $locationDecisionValidatorPath -PathType Leaf)) {
    throw 'O fechamento V10A/V2-028a não preserva o catálogo local, seus bindings ou limites fail-closed.'
}
if ([string]$locationShadowManifest.roadmapTask -cne 'V2-028' -or
    [string]$locationShadowManifest.route -cne 'V10' -or
    [int]$locationShadowManifest.block -ne 47 -or
    [string]$locationShadowManifest.localState -cne 'IMPLEMENTADA_EM_SHADOW' -or
    [int]$locationShadowManifest.templateId -ne 8656 -or
    [string]$locationShadowManifest.identity.sourceKey -cne '/corporation_sequence_number:INTEGER_TYPE_TAGGED_NO_UNPROVEN_NUMERIC_RANGE' -or
    [string]$locationShadowManifest.identity.forbiddenAlias -cne '/sequence_number:NEVER_SOURCE_KEY_ALIAS_OR_FALLBACK' -or
    [int]$locationShadowManifest.syntheticLimits.maximumRecordsPerLocalMicrobatch -ne 100 -or
    [string]$locationShadowManifest.freshness.path -cne '/service_at' -or
    [string]$locationShadowManifest.unsourcedLegacy.presence -cne 'ABSENT' -or
    [string]$locationShadowManifest.unsourcedLegacy.provenance -cne 'UNSOURCED_LEGACY' -or
    [string]$locationShadowManifest.deferred.freightVolumeFallback -cne 'FREIGHT_VOLUME_FALLBACK_DEFERRED_RELATION_AND_PUBLICATION' -or
    [string]$locationShadowManifest.persistence.current -cne 'core.localizacao_cargas' -or
    [string]$locationShadowManifest.validation.authorizedTarget -cne 'localhost/ETL_SISTEMA_V2_SHADOW' -or
    @($locationShadowManifest.bindings.portability.matrixIds).Count -ne 17 -or
    @($locationShadowManifest.implementedRules).Count -ne 7 -or
    -not (Test-Path -LiteralPath $locationShadowRunbookPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $locationShadowValidatorPath -PathType Leaf) -or
    -not $stateText.Contains('<code>BUILD SUCCESS</code> em 01:34 com 714 testes') -or
    -not $stateText.Contains('estado agregado antes/depois permaneceu <code>0|0|0</code>') -or
    -not $trailText.Contains('PRESERVA=Q-FND-01_BYTE_A_BYTE')) {
    throw 'O fechamento V10/B47 ou a promoção fail-closed de Q-FND-02 não preserva manifesto, rollback, testes ou fronteiras.'
}
$measurementFoundationTrail = @($checkedTrail | Where-Object {
    $_.Groups['body'].Value -cmatch '(?:^| \| )ROTA=Q-MED-FND-01(?:$| \| )'
})
if ($measurementFoundationTrail.Count -ne 1 -or
    $measurementFoundationTrail[0].Groups['body'].Value -cnotmatch '(?:^| \| )BLOCO=50(?:$| \| )' -or
    $measurementFoundationTrail[0].Groups['body'].Value -cnotmatch '(?:^| \| )TAREFA=V2-050/FUNDACAO_MEDICAO_LOCAL(?:$| \| )' -or
    $measurementFoundationTrail[0].Groups['body'].Value -cnotmatch '(?:^| \| )FATIA=FUNDACAO_MEDICAO_LOCAL_MULTIESCALA_TEST_ONLY(?:$| \| )' -or
    $measurementFoundationTrail[0].Groups['body'].Value -cnotmatch '(?:^| \| )DEPENDE=V2-021\+V2-023\+Q-FND-01\+Q-FND-02\+Q-SWP-FND-01(?:$| \| )' -or
    $measurementFoundationTrail[0].Groups['body'].Value -cnotmatch '(?:^| \| )PROIBE=REDE\+FONTE\+ENV_SECRET\+BANCO\+SQL_PLAN_REAL\+DDL\+DML\+MIGRATION\+ORACULO_EXTERNO\+HEAP_OU_SLO_PRODUTIVO\+PUBLICACAO\+SWEEP\+PRUNE\+CUTOVER(?:$| \| )' -or
    $measurementFoundationTrail[0].Groups['body'].Value -cnotmatch '(?:^| \| )RESULTADO=FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SCALE_GATE_PASSED(?:$| \| )' -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-050 —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-038 —').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[ \] STATUS=CANDIDATO \| ROTA=Q-(?:USR|COL|MAN|COT|CAP|FRE|LOC|FAT|INV|SIN)-05 \| TAREFA=V2-050 \|').Count -ne 10 -or
    [regex]::Matches($trailText, '(?m)^- \[ \] STATUS=(?:CANDIDATO|CONDICIONAL) \| ROTA=O-(?:MAT0[1-5]|VW(?:0[1-9]|1[0-9]))-02 \| TAREFA=V2-050 \|').Count -ne 24 -or
    -not $localRecoveryClosed) {
    throw 'Q-MED-FND-01 deve estar concluída uma única vez no Bloco 50 e limitada à fundação local multiescala test-only.'
}

if ([string]$qfnd02Manifest.task.route -cne 'Q-FND-02' -or
    [int]$qfnd02Manifest.task.block -ne 48 -or
    [string]$qfnd02Manifest.outcome -cne 'FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES' -or
    [int]$qfnd02Manifest.q01Lock.fileCount -ne 43 -or
    [string]$qfnd02Manifest.q01Lock.aggregateSha256 -cne 'ab99feb5e413deb44bf0dfc26f737453fa802cd6b293805ba82aaa1c56b9029a' -or
    [int]$qfnd02Manifest.foundation.entityProfileCount -ne 2 -or
    [int]$qfnd02Manifest.foundation.sourceChannelCount -ne 3 -or
    [string]$qfnd02Manifest.foundation.profileStatus -cne 'PREPARED_NOT_EXECUTED' -or
    [string]$qfnd02Manifest.foundation.gateStatus -cne 'ORACLE_REQUIRED' -or
    [string]$qfnd02Manifest.foundation.providerEvidence -cne 'NOT_EXECUTED' -or
    [int]$qfnd02Manifest.mutations.cataloged -ne 48 -or
    @($qfnd02Manifest.profiles).Count -ne 2 -or
    @($qfnd02Manifest.governanceArtifacts).Count -ne 3 -or
    -not (Test-Path -LiteralPath $qfnd02ManifestHashPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $qfnd02AdrPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $qfnd02RunbookPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $qfnd02ValidatorPath -PathType Leaf) -or
    -not $stateText.Contains('<code>clean verify</code> offline registrou 780 testes, zero falhas/erros e dois skips esperados') -or
    -not $stateText.Contains('<code>327ca45986ab6ebe434f56b641053faa8a68f88d946636cdea22400321fd2dd4</code>')) {
    throw 'O fechamento Q-FND-02/B48 não preserva manifesto, lock, perfis/canais, provas ou fronteiras.'
}

$sweepFoundationTrail = @([regex]::Matches(
    $trailText,
    '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=Q-SWP-FND-01 \| BLOCO=49 \| TAREFA=V2-013/FUNDACAO_KERNEL_LOCAL \| FATIA=FUNDACAO_KERNEL_LOCAL_FAIL_CLOSED \| .* \| RESULTADO=FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED \| EVIDENCIA=STATES\.md$'
))
$sweepManifestHash = (Get-FileHash -LiteralPath $sweepFoundationManifestPath -Algorithm SHA256).Hash.ToLowerInvariant()
$sweepManifestSidecar = Read-StrictUtf8 -LiteralPath $sweepFoundationManifestHashPath -MaximumBytes 1KB
if ($sweepFoundationTrail.Count -ne 1 -or
    [string]$sweepFoundationManifest.task.route -cne 'Q-SWP-FND-01' -or
    [int]$sweepFoundationManifest.task.block -ne 49 -or
    [string]$sweepFoundationManifest.task.taskId -cne 'V2-013/FUNDACAO_KERNEL_LOCAL' -or
    [string]$sweepFoundationManifest.outcome -cne 'FOUNDATION_LOCAL_COMPLETE_NO_ENTITY_SWEEP_ENABLED' -or
    [int]$sweepFoundationManifest.foundation.entitySweepEnabledCount -ne 0 -or
    [bool]$sweepFoundationManifest.foundation.applyCapability -or
    [int]$sweepFoundationManifest.matrix.rowCount -ne 33 -or
    [int]$sweepFoundationManifest.matrix.familyCount -ne 11 -or
    [int]$sweepFoundationManifest.matrix.enabledCount -ne 0 -or
    [int]$sweepFoundationManifest.matrix.provenCompleteCount -ne 0 -or
    [int]$sweepFoundationManifest.fixture.caseCount -ne 53 -or
    [int]$sweepFoundationManifest.fixture.mutationCount -ne 52 -or
    $sweepManifestSidecar -cne "$sweepManifestHash  manifesto.json`n" -or
    -not (Test-Path -LiteralPath $sweepFoundationRunbookPath -PathType Leaf) -or
    -not (Test-Path -LiteralPath $sweepFoundationValidatorPath -PathType Leaf) -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-013/FUNDACAO_KERNEL_LOCAL\b').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-013 —').Count -ne 1 -or
    [regex]::Matches($stateText, '(?m)^\s*- \[x\] \*\*V2-050/FUNDACAO_MEDICAO_LOCAL\b').Count -ne 1 -or
    -not $stateText.Contains('<code>clean verify</code> terminou <code>BUILD SUCCESS</code> em 01:43 com 893 testes') -or
    -not $trailText.Contains('zero <code>ENABLED</code>/zero <code>PROVEN_COMPLETE</code> e habilita zero entidades')) {
    throw 'O fechamento Q-SWP-FND-01/B49 não preserva manifesto, matriz, mutantes, limites ou o fechamento isolado posterior de Q-MED-FND-01/B50.'
}

foreach ($block40EvidenceMarker in @(
    'O Bloco 40 confirmou pelo <code>master</code> o alvo exato',
    'V039 passou com captura das contagens tipadas',
    'V041 passou limites BIGINT, oito negativos físicos',
    '<code>clean verify</code> offline final em Java 17.0.20.1 registrou 656 testes',
    'varredura de 863 candidatos/862 textos/um binário',
    'pós-gate confirmou novamente zero histórico/objetos'
)) {
    if (-not $trailText.Contains($block40EvidenceMarker)) {
        throw "A trilha não registra a evidência final do Bloco 40: $block40EvidenceMarker."
    }
}
if (-not $stateText.Contains('<code>CONCLUIDO_LOCAL_SHADOW_ROLLBACK_ONLY</code>') -or
    -not $stateText.Contains('passou com 656 testes, zero falhas, zero erros, um skip esperado') -or
    -not $stateText.Contains('scanner offline examinou 863 candidatos, 862 textos e um binário verificado')) {
    throw 'O STATES.md não registra integralmente os gates finais executados para G12/V2-015e.'
}
$openEntityQ01 = @([regex]::Matches(
    $trailText,
    "(?m)^- \[ \] STATUS=(?:CANDIDATO|EXTERNAL_HOLD) \| ROTA=$entityQ01RoutePattern \|"
))
if ($openEntityQ01.Count -ne 10) {
    throw "As dez caracterizações Q-*-01 de entidade devem permanecer abertas; encontrado $($openEntityQ01.Count)."
}
$requiredOpenEntityQ01 = [ordered]@{
    'Q-USR-01' = 'CANDIDATO'
    'Q-COL-01' = 'CANDIDATO'
    'Q-MAN-01' = 'EXTERNAL_HOLD'
    'Q-COT-01' = 'CANDIDATO'
    'Q-CAP-01' = 'CANDIDATO'
    'Q-FRE-01' = 'CANDIDATO'
    'Q-LOC-01' = 'CANDIDATO'
    'Q-FAT-01' = 'CANDIDATO'
    'Q-INV-01' = 'CANDIDATO'
    'Q-SIN-01' = 'CANDIDATO'
}
foreach ($entityRoute in $requiredOpenEntityQ01.GetEnumerator()) {
    if (-not $trailText.Contains("- [ ] STATUS=$($entityRoute.Value) | ROTA=$($entityRoute.Key) |")) {
        throw "A rota $($entityRoute.Key) não preserva o estado aberto esperado."
    }
}

$completedBlocks = @(
    $checkedTrail | ForEach-Object {
        $blockMatch = [regex]::Match($_.Groups['body'].Value, '(?:^| \| )BLOCO=(?<block>[0-9]+)(?:$| \| )')
        if ($blockMatch.Success) { [int]$blockMatch.Groups['block'].Value }
    }
)
if ($completedBlocks.Count -eq 0) {
    throw 'A trilha não contém blocos funcionais concluídos deriváveis.'
}
$lastFunctionalBlock = ($completedBlocks | Measure-Object -Maximum).Maximum
$nextFunctionalBlock = $lastFunctionalBlock + 1
if ($lastFunctionalBlock -ne $(if ($b55Phase) {55} else {53}) -or $nextFunctionalBlock -ne $(if ($b55Phase) {56} else {54}) -or
    (-not $b55Phase -and $trailText -cnotmatch '(?m)^\| Último bloco com aceite canônico novo \| Bloco 53 — P02Q — V2-022/QUALIFICACAO_FISICA_LOCAL') -or
    ($b55Phase -and $trailText -cnotmatch '(?m)^\| Último bloco com aceite canônico novo \| Bloco 55 — P02V, V2-042 e G06/G07/G08 Windows/SQL local') -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=G12 \| BLOCO=40 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=Q-BST-01 \| BLOCO=41 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=G05A \| BLOCO=42 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=G05T \| BLOCO=43 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=V08 \| BLOCO=44 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=V09 \| BLOCO=45 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=V10A \| BLOCO=46 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=V10 \| BLOCO=47 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=Q-FND-02 \| BLOCO=48 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=Q-SWP-FND-01 \| BLOCO=49 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[xX]\] STATUS=CONCLUIDO \| ROTA=Q-MED-FND-01 \| BLOCO=50 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=42 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=43 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=44 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=45 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=46 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=47 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=48 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=49 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=50 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=51 \|').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=52 \|').Count -ne 1) {
    throw 'A numeração deve fechar V08/44, V09/45, V10A/46, V10/47, Q-FND-02/48, Q-SWP-FND-01/49 e Q-MED-FND-01/50, concluir B51/P02M e B52/P02R somente localmente, com Bloco 53/P02Q concluído localmente e Bloco 54 concluído localmente com gates nominais pendentes.'
}

$vehicleDecisions = @($fleetDecision.dimensions | Where-Object { [string]$_.name -ceq 'VEICULOS' })
$driverDecisions = @($fleetDecision.dimensions | Where-Object { [string]$_.name -ceq 'MOTORISTAS' })
if ([string]$fleetDecision.decisionStatus -cne 'COMPLETE_LOCAL_REASSESSMENT_BLOCKED' -or
    $vehicleDecisions.Count -ne 1 -or [string]$vehicleDecisions[0].status -cne 'BLOCKED' -or
    $driverDecisions.Count -ne 1 -or [string]$driverDecisions[0].status -cne 'BLOCKED') {
    throw 'A fotografia histórica V02 de V2-035c não preserva Veículos e Motoristas como BLOCKED.'
}
if ($fleetDecision.routeDecision.newDecisionSliceCreated -isnot [bool] -or
    [bool]$fleetDecision.routeDecision.newDecisionSliceCreated -or
    $fleetDecision.routeDecision.newFunctionalBlockAssigned -isnot [bool] -or
    [bool]$fleetDecision.routeDecision.newFunctionalBlockAssigned -or
    [int]$fleetDecision.routeDecision.nowRouteCount -ne 0 -or
    [string]$fleetDecision.routeDecision.nextFunctionalBlock -cne 'UNASSIGNED_39') {
    throw 'A fotografia histórica da reavaliação V02 foi reescrita para criar fatia, rota ou bloco funcional.'
}
if ($trailText -cnotmatch '(?m)^- \[x\] STATUS=CONCLUIDO \| ROTA=D00 \| BLOCO=38 \| TAREFA=V2-035c .*\| RESULTADO_VEICULOS=BLOCKED \| RESULTADO_MOTORISTAS=BLOCKED \| REAVALIACAO_V02=COMPLETE_LOCAL_REASSESSMENT_BLOCKED \| HANDOFF_TERRA=NAO_CRIADO \| EVIDENCIA=STATES\.md$') {
    throw 'A linha histórica D00 não registra integralmente a reavaliação fail-closed V02.'
}
if ($trailText -cnotmatch '(?m)^- \[ \] STATUS=CANDIDATO \| ROTA=D03 .*\| HOLD=V2-035c/REASSESSMENT_V02/VEICULOS=BLOCKED \|' -or
    $trailText -cnotmatch '(?m)^- \[ \] STATUS=CANDIDATO \| ROTA=D04 .*\| HOLD=V2-035c/REASSESSMENT_V02/MOTORISTAS=BLOCKED \|') {
    throw 'D03/D04 não preservam os holds exatos da reavaliação V02.'
}
if (Test-Path -LiteralPath $terraFleetRunbookPath) {
    throw 'O runbook Terra D03 existe apesar de Veículos estar BLOCKED.'
}
if ($trailText -cnotmatch 'A reavaliação V02 de Frota deixou o Bloco 39 não atribuído naquele momento') {
    throw 'A trilha não preserva o Bloco 39 não atribuído como fotografia histórica da V02.'
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

$motorClosed = @($checkedTrail | Where-Object { $_.Groups['body'].Value -cmatch '(?:^| \| )ROTA=P02M(?:$| \| )' })
$recoveryClosed = @($checkedTrail | Where-Object { $_.Groups['body'].Value -cmatch '(?:^| \| )ROTA=P02R(?:$| \| )' })
if (-not $localRecoveryClosed -or (-not $b55Phase -and -not $noEligibleSelection) -or $motorClosed.Count -ne 1 -or $recoveryClosed.Count -ne 1 -or
    $motorClosed[0].Groups['body'].Value -cnotmatch '\| BLOCO=51 \| TAREFA=V2-022/INTEGRACAO_LOCAL_COLETAS_FRETES \|' -or
    $motorClosed[0].Groups['body'].Value -cnotmatch '\| RESULTADO=LOCAL_INTEGRATION_COMPLETE_OPERATIONAL_GATES_PENDING \|' -or
    $recoveryClosed[0].Groups['body'].Value -cnotmatch '\| BLOCO=52 \| TAREFA=V2-022/RECUPERACAO_DURAVEL_LOCAL \|' -or
    $recoveryClosed[0].Groups['body'].Value -cnotmatch '\| PROIBE=REDE\+FONTE\+SQL_FISICO\+CLI_POSITIVA\+DEPLOY \|' -or
    $recoveryClosed[0].Groups['body'].Value -cnotmatch '\| RESULTADO=LOCAL_DURABLE_RECOVERY_COMPLETE_PHYSICAL_GATES_PENDING \| EVIDENCIA=STATES\.md$') {
    throw 'B51/B52 devem fechar somente motor/recuperação locais, preservando a ausência de sucessor autorizado.'
}
foreach ($motorTask in @('TRAVESSIA_STAGING_LOCAL', 'INTEGRACAO_LOCAL_COLETAS_FRETES', 'RECUPERACAO_DURAVEL_LOCAL')) {
    if ([regex]::Matches($stateText, "(?m)^  - \[x\] \*\*V2-022/$motorTask —").Count -ne 1) {
        throw "Falta conclusão única da subentrega local V2-022/$motorTask."
    }
}
$heldTasks = if ($b55Phase) { @('V2-022','V2-041') } else { @('V2-022', 'V2-022b', 'V2-041', 'V2-042b', 'V2-042c') }
foreach ($heldTask in $heldTasks) {
    if ([regex]::Matches($stateText, "(?m)^\s*- \[ \] \*\*$heldTask —").Count -ne 1) {
        throw "O pacote local não pode fechar o gate agregado/externo $heldTask."
    }
}
foreach ($motorArtifact in @(
    'docs/runbooks/v2-022-recuperacao-duravel-local.md',
    'docs/adr/0034-recuperacao-duravel-local-sem-reemissao-de-permits.md',
    'database/migrations/V015__create_runtime_durable_recovery.sql',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/RuntimeRecoveryProcessProbe.java',
    'docs/runbooks/v2-022-motor-local-coletas-fretes.md',
    'docs/adr/0033-motor-local-coletas-fretes-e-recuperacao.md',
    'src/main/java/br/com/esl/etl/v2/bootstrap/LocalColetasFretesRuntime.java',
    'src/main/java/br/com/esl/etl/v2/plataforma/orquestracao/RuntimeDispatcher.java',
    'src/test/java/br/com/esl/etl/v2/plataforma/fonte/dataexport/LocalRuntimeIntegrationTest.java'
)) {
    if (-not (Test-Path -LiteralPath (Join-Path $repositoryRoot $motorArtifact) -PathType Leaf)) {
        throw "Artefato obrigatório do motor local ausente: $motorArtifact."
    }
}
foreach ($historicalSlice in @(
    'V2-035a/FUNDACAO_REFERENCIAS_LOCAL',
    'V2-035b/DIMENSAO_USUARIOS_LOCAL',
    'V2-009d/ENFORCEMENT_USUARIOS_LOCAL'
)) {
    if ([regex]::Matches($stateText, ('(?m)^    - \[x\] \*\*' + [regex]::Escape($historicalSlice) + ' —')).Count -ne 1) {
        throw "Falta subcheckbox histórico local único: $historicalSlice."
    }
    $historicalRoute = @($checkedTrail | Where-Object {
        $_.Groups['body'].Value.Contains(" | TAREFA=$historicalSlice |")
    })
    if ($historicalRoute.Count -ne 1 -or
        $historicalRoute[0].Groups['status'].Value -cne 'MARCO_CONSOLIDADO' -or
        $historicalRoute[0].Groups['body'].Value -cmatch '(?:^| \| )BLOCO=') {
        throw 'Reconhecimento histórico não pode consumir bloco funcional ou duplicar conclusão.'
    }
}
foreach ($historicalParent in @('V2-035a', 'V2-035b', 'V2-009d')) {
    if ([regex]::Matches($stateText, ('(?m)^  - \[ \] \*\*' + $historicalParent + ' —')).Count -ne 1) {
        throw "O reconhecimento local não conclui o pai $historicalParent."
    }
}
$physicalClosed = @($checkedTrail | Where-Object { $_.Groups['body'].Value -cmatch '(?:^| \| )ROTA=P02Q(?:$| \| )' })
if ((-not $b55Phase -and ($nowTrail.Count -ne 0 -or -not $noEligibleSelection)) -or $physicalClosed.Count -ne 1 -or
    $physicalClosed[0].Groups['body'].Value -cnotmatch '(?:^| \| )ROTA=P02Q \| BLOCO=53 \| TAREFA=V2-022/QUALIFICACAO_FISICA_LOCAL \|' -or
    $physicalClosed[0].Groups['body'].Value -cnotmatch '\| PROIBE=FONTE_EXTERNA\+GRANT_OPERACIONAL\+DEPLOY \| RESULTADO=LOCAL_PHYSICAL_QUALIFICATION_COMPLETE_OPERATIONAL_GATES_PENDING \| EVIDENCIA=STATES\.md$' -or
    [regex]::Matches($stateText, '(?m)^  - \[x\] \*\*V2-022/QUALIFICACAO_FISICA_LOCAL').Count -ne 1 -or
    [regex]::Matches($trailText, '(?m)^- \[[ xX]\].*\| BLOCO=53 \|').Count -ne 1 -or
    (-not $b55Phase -and [regex]::Matches($stateText, '(?m)^\s*- \[ \] \*\*V2-042 —').Count -ne 1)) {
    throw 'Bloco 53 fecha somente P02Q, uma vez, sem rota AGORA nem aceite operacional presumido.'
}
if (-not $stateText.Contains('LOCAL_A_G_COMPLETE_NOMINAL_GATES_PENDING') -or -not $trailText.Contains($(if ($b55Phase) {'| Último bloco funcional concluído | Bloco 55 — laboratório A–J; fonte real e produção com gates próprios |'} else {'| Último bloco funcional concluído | Bloco 54 — laboratório A–G; gates nominais preservados |'}))) { throw 'B54_LOCAL_COMPLETION_PANEL_DRIFT' }
$selectionStatus = 'zero rota AGORA; Bloco 53 integrado local concluído; Bloco 54 concluído localmente com gates nominais pendentes'
if ($b55Phase) { $selectionStatus = 'zero rota AGORA; Bloco 55/P02V A–J e V2-042 comprovados para Windows/SQL local' }
Write-Output "PASS: trilha GPT-5.6 validada com $($checkedTrail.Count) marcos históricos, $($openTrail.Count) fatias abertas, $($stateDone)/$($stateCheckboxes.Count) checkboxes concluídos no STATES.md e $selectionStatus."
