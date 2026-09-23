param()
$ErrorActionPreference='Stop'
Set-StrictMode -Version Latest
$root=[IO.Path]::GetFullPath((Join-Path $PSScriptRoot '../..'))
$expected=Join-Path $root 'target/macrobloco-qualificacao-pacote-20260913-01'
$checked=0
foreach($name in @('New-SequenceExamples','Invoke-SequenceJarProof')){
    $script=Join-Path $PSScriptRoot ($name+'.ps1')
    $tokens=$null;$errors=$null
    $null=[Management.Automation.Language.Parser]::ParseFile($script,[ref]$tokens,[ref]$errors)
    if($errors.Count){throw 'P06_HELPER_PARSE'}
    $parameters=if($name -eq 'New-SequenceExamples'){@{BuildAttempt='p06-no-execution';OutputName='p06-no-execution'}}else{@{Attempt='p06-no-execution';Package='p06-no-execution';Case='sequence-a'}}
    $observed=& $script @parameters -ValidateOnly
    if($observed.round -cne $expected -or $observed.physical){throw 'P06_HELPER_DEFAULT_ROUND'}
    if($name -eq 'Invoke-SequenceJarProof' -and ($observed.nominalRows -ne 33 -or $observed.schemaVersion -ne 104)){throw 'P06_HELPER_ORACLE_SCHEMA'}
    $checked++
    $alternate=& $script @parameters -ValidateOnly -RoundName 'macrobloco-campanhas-integrais-20260915-01'
    if($alternate.round -cne (Join-Path $root 'target/macrobloco-campanhas-integrais-20260915-01')){throw 'P06_HELPER_ALTERNATE_ROUND'}
    $checked++
    foreach($bad in @('../escape','macrobloco-x/escape','macrobloco-x\escape')){
        $reason='ACCEPTED'
        try{$null=& $script @parameters -ValidateOnly -RoundName $bad}catch{$reason=$_.FullyQualifiedErrorId}
        if($reason -notlike 'ParameterArgumentValidationError*'){throw 'P06_HELPER_PARAMETER_REFUSAL'}
        $checked++
    }
}
if(Test-Path -LiteralPath (Join-Path $expected 'p06-no-execution')){throw 'P06_HELPER_UNEXPECTED_EFFECT'}
Write-Output ('P06_HELPERS PASS checks='+$checked+' SQL=0 process=0')
