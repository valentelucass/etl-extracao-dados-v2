[CmdletBinding()]
param([ValidateRange(1,1000)][int]$MaximoDias=97)

& (Join-Path $PSScriptRoot 'Reprocess-DashboardReportDays.ps1') -MaximoDias $MaximoDias
