$ErrorActionPreference='Stop';Set-StrictMode -Version Latest
Import-Module "$PSScriptRoot/Bloco60Assertions.psm1" -Force
$case=@{id='SYNTHETIC';expectedExit=@(0);expectedHttp=@(2);expected=@{publications=1;seals=1;consumptions=1};fault='NONE'}
$observed=@{publications=1;seals=1;consumptions=1;quality='PASSED';completedChecks=4;passedChecks=4;failedChecks=0;applicationRows=2;candidateRows=2;typedHistory=2}
$log='RUNTIME_HTTP_ATTEMPTS protocol=GRAPHQL total=2'
Assert-Bloco60Case $case $observed 0 2 $log $false
$guards=0
foreach($key in @('seals','consumptions','completedChecks','passedChecks','applicationRows')){
 $copy=$observed.Clone();$copy[$key]=0;$caught=$false
 try {Assert-Bloco60Case $case $copy 0 2 $log $false}catch{$caught=$true}
 if(-not $caught){throw ('B60_ASSERTION_GUARD_'+$key)};$guards++
}
foreach($tuple in @(@(40,2,$log,$false),@(0,1,$log,$false),@(0,2,'RUNTIME_HTTP_ATTEMPTS protocol=GRAPHQL total=UNOBSERVED',$false),@(0,2,$log,$true))){
 $caught=$false;try{Assert-Bloco60Case $case $observed $tuple[0] $tuple[1] $tuple[2] $tuple[3]}catch{$caught=$true}
 if(-not $caught){throw 'B60_ASSERTION_TELEMETRY_GUARD'};$guards++
}
$case.expected=@{publications=0;seals=0;consumptions=1};$case.expectedExit=@(40)
$negative=$observed.Clone();$negative.publications=0;$negative.seals=0;$negative.applicationRows=0;$negative.typedHistory=0
Assert-Bloco60Case $case $negative 40 2 $log $false
foreach($key in @('applicationRows','typedHistory')){
 $copy=$negative.Clone();$copy[$key]=1;$caught=$false;try{Assert-Bloco60Case $case $copy 40 2 $log $false}catch{$caught=$true}
 if(-not $caught){throw 'B60_NEGATIVE_FALSE_ABSENCE'};$guards++
}
$hash='a'*64;$extra='b'*64
$before=@{table_name='synthetic';hashes=($hash+','+$hash)}|ConvertTo-Json -Compress
$after=@{table_name='synthetic';hashes=($hash+','+$hash+','+$extra)}|ConvertTo-Json -Compress
Assert-Bloco60PreservedRows $before $after
foreach($candidate in @((@{table_name='synthetic';hashes=$hash}|ConvertTo-Json -Compress),(@{table_name='synthetic';hashes=($hash+','+$extra)}|ConvertTo-Json -Compress),'')){
 $caught=$false;try{Assert-Bloco60PreservedRows $before $candidate}catch{$caught=$true};if(-not $caught){throw 'B60_PRESERVATION_GUARD'};$guards++
}
@{passed=$true;guards=$guards;layer='OFFLINE_ASSERTION_CONTROLLER';sqlExecuted=$false}|ConvertTo-Json
