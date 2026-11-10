# Pure validation and time calculation. Dot-sourcing has no external effects.
function Test-AllReceivingComplete0762($Report) {
 if($Report.schema -cne 'ALL_RECEIVING0762_V1' -or -not $Report.passed -or -not $Report.completedAt -or @($Report.entities).Count -ne 11){return $false}
 $names0762=@('CONSOLIDACAO_DETALHADA','OCORRENCIAS','COLETAS','FRETES','MANIFESTOS','COTACOES','LOCALIZACAO_CARGAS','CONTAS_A_PAGAR','FATURAS_POR_CLIENTE','INVENTARIO','SINISTROS')
 if(@(Compare-Object ($names0762|Sort-Object) (@($Report.entities|ForEach-Object entity)|Sort-Object)).Count){return $false}
 $start0762=[datetime]::ParseExact($Report.periodStart,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 $end0762=[datetime]::ParseExact($Report.periodEnd,'yyyy-MM-dd',[Globalization.CultureInfo]::InvariantCulture)
 if(($end0762-$start0762).Days -ne 7){return $false}
 foreach($entity0762 in $Report.entities){
  if($entity0762.status -cne 'STORED'){return $false}
  foreach($day0762 in $entity0762.days){if(-not $day0762.stored -or -not $day0762.sqlEveryOccurrenceExact -or -not $day0762.terminal -or $day0762.rejectedRows -ne 0 -or $day0762.rows -ne $day0762.typedRows){return $false}}
  for($date0762=$start0762;$date0762 -le $end0762;$date0762=$date0762.AddDays(1)){
   $matches0762=@($entity0762.days|Where-Object {$_.day -ceq $date0762.ToString('yyyy-MM-dd')})
   if($matches0762.Count -ne 1 -or -not $matches0762[0].stored){return $false}
  }
 }
 return $true
}
function Get-AllReceivingNext0762([DateTimeOffset]$CompletedAt){return $CompletedAt.AddMinutes(60).AddSeconds(2)}
function Write-AllReceivingState0762([string]$Path,$State){
 $pending0762=$Path+'.partial'
 [IO.File]::WriteAllText($pending0762,($State|ConvertTo-Json -Depth 12),(New-Object Text.UTF8Encoding($false)))
 Move-Item -LiteralPath $pending0762 -Destination $Path -Force
}
