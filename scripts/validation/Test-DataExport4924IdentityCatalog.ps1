#Requires -Version 7.0

[CmdletBinding()]
param()

& (Join-Path $PSScriptRoot 'Test-DataExportV2009bIdentityCatalog.ps1') -TemplateId '4924'

