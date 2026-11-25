[CmdletBinding()]
param([string]$OperationsDirectory)
$ErrorActionPreference='Stop'
if(!$OperationsDirectory){$OperationsDirectory=Join-Path $PSScriptRoot '../operations'}
. (Join-Path $OperationsDirectory 'RefreshSupervisor0766.ps1')
$directory=Join-Path ([Environment]::GetFolderPath('MyDocuments')) 'Armazenamento-Projetos/etl-extracao-dados-v2/atualizacao0766'
$old=Join-Path $directory 'runtime-manifest-dashboard-fields-0820.json'
$actual=Get-RefreshScheduledManifest0766 (Join-Path $directory 'operational-settings.json') $old
if([IO.Path]::GetFullPath($actual) -ine [IO.Path]::GetFullPath((Join-Path $directory 'runtime-manifest-dashboard-fields-0821.json'))){throw 'Agendamento nao selecionou o runtime com a correcao do rateio.'}
foreach($name in @('historico-90-dias-settings.json','qualification-settings.json')){
    if((Get-RefreshScheduledManifest0766 (Join-Path $directory $name) $old) -cne $old){throw 'A execucao isolada perdeu a versao solicitada.'}
}
$other=Join-Path ([IO.Path]::GetTempPath()) 'operational-settings.json'
if((Get-RefreshScheduledManifest0766 $other $old) -cne $old){throw 'O seletor atingiu outro ambiente.'}
Write-Output 'Agendamento de producao e execucoes isoladas conferidos.'
