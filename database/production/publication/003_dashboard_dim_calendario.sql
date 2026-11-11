-- The existing calendar function limits each active window to ten years.
CREATE OR ALTER VIEW pub.dim_calendario
AS
WITH calendar_rows AS (
    SELECT calendar_date, date_key, iso_weekday, is_weekend, holiday_kind, holiday_code,
           holiday_label, is_business_day, billing_reference_date
    FROM ref.ufn_calendar_seed_candidate_v1('20191201', '20291201')
    WHERE calendar_date >= '20191201'
    UNION ALL
    SELECT calendar_date, date_key, iso_weekday, is_weekend, holiday_kind, holiday_code,
           holiday_label, is_business_day, billing_reference_date
    FROM ref.ufn_calendar_seed_candidate_v1('20291201', '20330101')
    WHERE calendar_date >= '20291201'
), compatible AS (
    SELECT *,
           CASE WHEN billing_reference_date < '20191201' THEN calendar_date
                ELSE billing_reference_date END AS billing_day,
           CASE holiday_code
               WHEN 'NEW_YEAR' THEN N'Confraternizacao Universal'
               WHEN 'INDEPENDENCE_DAY' THEN N'Independencia do Brasil'
               WHEN 'REPUBLIC_DAY' THEN N'Proclamacao da Republica'
               WHEN 'BLACK_CONSCIOUSNESS_DAY' THEN N'Consciencia Negra'
               WHEN 'CARNIVAL_TUESDAY' THEN N'Carnaval - terca-feira'
               ELSE holiday_label
           END AS legacy_holiday_label
    FROM calendar_rows
)
SELECT c.calendar_date AS [data],
       c.date_key AS data_key,
       CONVERT(smallint, YEAR(c.calendar_date)) AS ano,
       CONVERT(tinyint, MONTH(c.calendar_date)) AS mes,
       CONVERT(tinyint, DAY(c.calendar_date)) AS dia,
       CONVERT(tinyint, DATEPART(QUARTER, c.calendar_date)) AS trimestre,
       CONVERT(tinyint, DATEPART(ISO_WEEK, c.calendar_date)) AS semana_iso,
       c.iso_weekday AS dia_semana_iso,
       CONVERT(nvarchar(20), CASE c.iso_weekday
           WHEN 1 THEN N'Segunda-feira' WHEN 2 THEN N'Terca-feira' WHEN 3 THEN N'Quarta-feira'
           WHEN 4 THEN N'Quinta-feira' WHEN 5 THEN N'Sexta-feira' WHEN 6 THEN N'Sabado'
           ELSE N'Domingo' END) COLLATE Latin1_General_CI_AS AS nome_dia_semana,
       c.is_weekend AS is_fim_semana,
       CONVERT(bit, CASE WHEN c.holiday_kind = N'NATIONAL' THEN 1 ELSE 0 END) AS is_feriado_nacional,
       CONVERT(nvarchar(100), CASE WHEN c.holiday_kind = N'NATIONAL' THEN c.legacy_holiday_label END)
           COLLATE Latin1_General_CI_AS AS nome_feriado_nacional,
       CONVERT(bit, CASE WHEN c.holiday_kind = N'OPTIONAL' THEN 1 ELSE 0 END) AS is_ponto_facultativo,
       CONVERT(nvarchar(100), CASE WHEN c.holiday_kind = N'OPTIONAL' THEN c.legacy_holiday_label END)
           COLLATE Latin1_General_CI_AS AS nome_ponto_facultativo,
       c.is_business_day AS is_dia_util,
       c.billing_day AS data_referencia_faturamento,
       YEAR(c.billing_day) * 10000 + MONTH(c.billing_day) * 100 + DAY(c.billing_day) AS data_referencia_faturamento_key,
       YEAR(c.calendar_date) * 100 + MONTH(c.calendar_date) AS ano_mes,
       CONVERT(datetime2(0), SWITCHOFFSET(i.installed_at, '+00:00')) AS atualizado_em
FROM compatible AS c
CROSS JOIN ctl.production_installation_step AS i
WHERE i.step_id = '0774-dashboard-dim-calendario';
