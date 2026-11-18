param([Parameter(Mandatory)][string]$GuardPath,[Parameter(Mandatory)][string]$GuardSha256)
$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
if((Get-FileHash -LiteralPath $GuardPath -Algorithm SHA256).Hash-ine$GuardSha256){throw 'GUARD_PIN'}
. $GuardPath
function Selection($rows,[int]$count){
 return @{schema='ROOT_APPROVED_FRETES_EXTERNAL_JVM_SELECTION0674_V1';selectedBy='Root';
 selectedIdentities=@($rows);selectedIdentityCount=$count;completeAllJvmEnumerationRequired=$true;
 currentNativeObservationRequiredBeforeEverySend=$true;unexpectedOrMissingJvmStops=$true;noImageOrCommandProofClaimed=$true}
}
$tuple=@{pid=101;parentPid=100;name='java.exe';creationUtc='2036-03-20T00:00:00.0000000Z'}
WValidateSelection (Selection @($tuple) 1)
$cases=0
foreach($bad in @((Selection @($tuple) 2),(Selection @() 0),(Selection @() 1),(Selection @($tuple,$tuple) 2))){
 $refused=$false
 try{WValidateSelection $bad}catch{$refused=$true}
 if(-not$refused){throw 'INVALID_ORIGINAL_SELECTION_ACCEPTED'}
 $cases++
}
if($cases-ne4){throw 'SELECTION_CASE_COUNT'}
[Console]::Out.WriteLine('ROOT_SELECTION_CONTRACT_PASS cases=5 Source=0 HTTP=0 CIM=0')

