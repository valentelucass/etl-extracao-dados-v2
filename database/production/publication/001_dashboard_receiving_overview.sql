-- Read-only publication of completed receiving coverage, not a business fact.
-- The deployment wrapper runs this single batch in ETL_SISTEMA_V2.
CREATE OR ALTER VIEW pub.v2_dashboard_receiving_overview
AS
WITH source_count AS (
    SELECT COUNT_BIG(DISTINCT source_scope) AS total
    FROM ctl.production_ingestion_coverage
), coverage AS (
    SELECT
        c.entity_name,
        COUNT_BIG(*) AS covered_days,
        SUM(CAST(CASE WHEN r.run_id IS NULL THEN 1 ELSE 0 END AS bigint)) AS invalid_days,
        SUM(CAST(r.row_count AS bigint)) AS latest_rows,
        MIN(c.business_date) AS first_business_date,
        MAX(c.business_date) AS last_business_date,
        MAX(c.stored_utc) AS latest_capture_utc
    FROM ctl.production_ingestion_coverage c
    LEFT JOIN ctl.production_ingestion_run r
        ON r.run_id = c.latest_run_id
        AND r.source_scope = c.source_scope
        AND r.entity_name = c.entity_name
        AND r.period_start = c.business_date
        AND r.period_end = c.business_date
        AND r.run_state = 'STORED'
        AND r.row_count IS NOT NULL
    GROUP BY c.entity_name
)
SELECT
    t.entity_name,
    t.report_id,
    t.expected_field_count AS field_count,
    CASE WHEN status.code IN ('AVAILABLE', 'NOT_LOADED') THEN COALESCE(c.covered_days, 0) END AS covered_days,
    CASE WHEN status.code IN ('AVAILABLE', 'NOT_LOADED') THEN COALESCE(c.latest_rows, 0) END AS latest_rows,
    CASE WHEN status.code = 'AVAILABLE' THEN c.first_business_date END AS first_business_date,
    CASE WHEN status.code = 'AVAILABLE' THEN c.last_business_date END AS last_business_date,
    CASE WHEN status.code = 'AVAILABLE' THEN TODATETIMEOFFSET(c.latest_capture_utc, '+00:00') END AS latest_capture_utc,
    status.code AS coverage_status
FROM ctl.production_export_template t
CROSS JOIN source_count s
LEFT JOIN coverage c ON c.entity_name = t.entity_name
CROSS APPLY (VALUES (
    CASE WHEN s.total > 1 THEN 'MULTIPLE_SOURCES'
         WHEN c.invalid_days > 0 THEN 'INVALID_COVERAGE'
         WHEN c.covered_days IS NULL THEN 'NOT_LOADED'
         ELSE 'AVAILABLE' END
)) status(code);
