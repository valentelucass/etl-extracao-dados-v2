[CmdletBinding()]
param([Parameter(Mandatory)][string[]]$ProbeReceipt)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..\..'))
$catalogPath = Join-Path $root 'src/main/resources/contracts/dataexport/receiving-catalog-0766-v1.json'
$catalog = Get-Content -LiteralPath $catalogPath -Raw -Encoding utf8 | ConvertFrom-Json
$observations = [System.Collections.Generic.List[object]]::new()

foreach ($path in $ProbeReceipt) {
    $receipt = Get-Content -LiteralPath $path -Raw -Encoding utf8 | ConvertFrom-Json
    if ($receipt.transport -cne 'GET_QUERY' -or $receipt.stopped -or -not $receipt.templates) {
        throw 'DATA_EXPORT_PROBE_NOT_COMPLETED'
    }

    foreach ($template in $receipt.templates) {
        $profiles = @($catalog.entities | Where-Object reportId -EQ $template.template)
        if ($template.template -notin @(6908, 6389) -or $profiles.Count -ne 1) {
            throw 'UNSUPPORTED_DASHBOARD_REPORT'
        }

        $info = $template.info
        if ($info.http_status -ne 200 -or -not $info.metadata_shape_valid -or
            -not $info.required_business_filter_declared -or -not $info.updated_at_filter_declared) {
            throw 'DATA_EXPORT_METADATA_OR_DATE_FILTER_INVALID'
        }
        $declared = @($info.technical_field_names | Sort-Object -CaseSensitive -Unique)
        if ($declared.Count -ne $info.field_count) {
            throw 'DATA_EXPORT_METADATA_FIELD_COUNT_MISMATCH'
        }
        $required = @($profiles[0].fields)
        $missing = @($required | Where-Object { $_ -cnotin $declared })
        $added = @($declared | Where-Object { $_ -cnotin $required })
        $nonEmpty = @($template.pages | Where-Object { $_.record_count -gt 0 })
        $differences = @(foreach ($page in $nonEmpty) {
            $pageFields = @($page.technical_field_names | Sort-Object -CaseSensitive -Unique)
            $missingFromPage = @($declared | Where-Object { $_ -cnotin $pageFields })
            $extraInPage = @($pageFields | Where-Object { $_ -cnotin $declared })
            $notInEveryRow = @($page.field_profiles | Where-Object presence_count -NE $page.record_count)
            if ($page.http_status -ne 200 -or -not $page.record_shape_valid -or
                -not $page.entity_limit_verifiable -or $page.entity_count_exceeds_requested_size -or
                $page.field_count -ne $pageFields.Count -or
                $pageFields.Count -ne @($page.field_profiles).Count -or
                $missingFromPage.Count -or $extraInPage.Count -or $notInEveryRow.Count) {
                [ordered]@{
                    page = $page.page
                    per = $page.per
                    fieldCount = $page.field_count
                    missing = $missingFromPage
                    additional = $extraInPage
                    notInEveryRow = @($notInEveryRow | ForEach-Object technical_name)
                }
            }
        })
        $observations.Add([pscustomobject][ordered]@{
            entity = $profiles[0].entity
            reportId = $template.template
            baselineFieldCount = $required.Count
            declaredFieldCount = $declared.Count
            missingBaselineFields = $missing
            additionalFields = $added
            sampledPages = $nonEmpty.Count
            differences = $differences
            consistent = $nonEmpty.Count -gt 0 -and $missing.Count -eq 0 -and $differences.Count -eq 0
        })
    }
}

$reports = @(foreach ($group in $observations | Group-Object reportId) {
    $observed = @($group.Group)
    $first = $observed[0].additionalFields -join "`n"
    $differentRevisions = @($observed | Where-Object { ($_.additionalFields -join "`n") -cne $first })
    [ordered]@{
        entity = $observed[0].entity
        reportId = $observed[0].reportId
        consistent = $differentRevisions.Count -eq 0 -and @($observed | Where-Object { -not $_.consistent }).Count -eq 0
        observations = $observed
    }
})
$passed = $reports.Count -gt 0 -and @($reports | Where-Object { -not $_.consistent }).Count -eq 0
[ordered]@{ passed = $passed; reports = $reports } | ConvertTo-Json -Depth 9
exit $(if ($passed) { 0 } else { 1 })
