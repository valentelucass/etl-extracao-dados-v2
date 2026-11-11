-- Keep the payable identifier and freshness ordering used by the V1 upsert.
CREATE OR ALTER VIEW pub.vw_contas_a_pagar_powerbi
AS
WITH source_rows AS (
    SELECT j.*, JSON_QUERY(l.source_envelope,'$.record') AS raw_record,
           l.stored_utc, l.business_date, l.occurrence_ordinal
    FROM stg.production_export_latest l
    CROSS APPLY OPENJSON(l.source_envelope,'$.record') WITH (
        sequence_code bigint '$.ant_ils_sequence_code',
        document_number nvarchar(max) '$.document',
        issue_date date '$.issue_date',
        entry_type nvarchar(max) '$.type',
        original_value nvarchar(max) '$.value',
        interest_value nvarchar(max) '$.interest_value',
        discount_value nvarchar(max) '$.discount_value',
        value_to_pay nvarchar(max) '$.value_to_pay',
        paid bit '$.paid',
        paid_value nvarchar(max) '$.paid_value',
        provider_name nvarchar(max) '$.ant_rir_name',
        branch_name nvarchar(max) '$.ant_crn_psn_nickname',
        accounting_classification nvarchar(max) '$.ant_ils_pas_ant_classification',
        accounting_description nvarchar(max) '$.ant_ils_pas_ant_name',
        accounting_value nvarchar(max) '$.ant_ils_pas_value',
        cost_center_name nvarchar(max) '$.ant_ces_acr_name',
        cost_center_value nvarchar(max) '$.ant_ces_value',
        launch_area nvarchar(max) '$.ant_aln_name',
        competence_month int '$.competence_month',
        competence_year int '$.competence_year',
        created_at datetimeoffset(7) '$.created_at',
        comments nvarchar(max) '$.ant_ils_comments',
        expense_description nvarchar(max) '$.ant_ils_expense_description',
        liquidation_date date '$.ant_ils_atn_liquidation_date',
        transaction_date date '$.ant_ils_atn_transaction_date',
        user_name nvarchar(max) '$.ant_uer_name',
        reconciled bit '$.ant_ils_atn_reconciled'
    ) j
    WHERE l.entity_name='CONTAS_A_PAGAR'
      AND (SELECT COUNT_BIG(DISTINCT source_scope) FROM ctl.production_ingestion_coverage
           WHERE entity_name='CONTAS_A_PAGAR')=1
), ranked AS (
    SELECT s.*, ROW_NUMBER() OVER(PARTITION BY sequence_code ORDER BY
        GREATEST(CONVERT(datetime2(3),created_at),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),liquidation_date))),
            DATEADD(millisecond,-1,DATEADD(day,1,CONVERT(datetime2(3),transaction_date)))) DESC,
        stored_utc DESC,business_date DESC,occurrence_ordinal DESC) AS row_rank
    FROM source_rows s
    WHERE sequence_code IS NOT NULL
)
SELECT CONVERT(time(0),ISNULL(s.created_at,'1900-01-01T00:00:00+00:00')) AS [Hora (Solicitacao)],
       s.sequence_code AS [Lançamento a Pagar/N°],
       CONVERT(varchar(100),s.document_number) AS [N° Documento],
       s.issue_date AS [Emissão],
       CONVERT(nvarchar(100),CASE entry.kind COLLATE Latin1_General_100_BIN2
           WHEN N'Advance' THEN N'Adiantamento' WHEN N'CiotBilling' THEN N'Pagamento CIOT'
           WHEN N'DriverBilling' THEN N'Pagamento Motorista' WHEN N'StorageInvoice' THEN N'Nota Fiscal Armazenagem'
           ELSE entry.kind END) AS [Tipo],
       amounts.original_value AS [Valor],
       amounts.interest_value AS [Juros],
       amounts.discount_value AS [Desconto],
       amounts.value_to_pay AS [Valor a pagar],
       CASE WHEN s.paid=1 THEN 'Sim' ELSE 'Não' END AS [Pago],
       amounts.paid_value AS [Valor pago],
       CONVERT(nvarchar(255),s.provider_name) AS [Fornecedor/Nome],
       CONVERT(nvarchar(255),s.branch_name) AS [Filial],
       CONVERT(nvarchar(100),CASE NULLIF(TRIM(s.accounting_classification),N'') COLLATE Latin1_General_100_BIN2
           WHEN N'variable_costs' THEN N'4. Custos Variáveis' WHEN N'static_expenses' THEN N'3. Custos Fixos'
           WHEN N'revenue' THEN N'1. Receitas' WHEN N'deductions' THEN N'2. Deduções'
           WHEN N'financial_result' THEN N'5. Resultado Financeiro' WHEN N'taxes' THEN N'6. Impostos'
           ELSE CASE WHEN NULLIF(TRIM(s.accounting_classification),N'') IS NOT NULL
                     THEN s.accounting_classification END END) AS [Conta Contábil/Classificação],
       CONVERT(nvarchar(255),s.accounting_description) AS [Conta Contábil/Descrição],
       amounts.accounting_value AS [Conta Contábil/Valor],
       CONVERT(nvarchar(255),s.cost_center_name) AS [Centro de custo/Nome],
       amounts.cost_center_value AS [Centro de custo/Valor],
       CONVERT(nvarchar(255),s.launch_area) AS [Área de Lançamento],
       s.competence_month AS [Mês de Competência],
       s.competence_year AS [Ano de Competência],
       s.created_at AS [Data criação],
       s.comments AS [Observações],
       s.expense_description AS [Descrição da despesa],
       s.liquidation_date AS [Baixa/Data liquidação],
       s.transaction_date AS [Data transação],
       CONVERT(nvarchar(255),s.user_name) AS [Usuário/Nome],
       CONVERT(nvarchar(50),CASE WHEN s.paid=1 THEN N'PAGO' ELSE N'ABERTO' END) AS [Status Pagamento],
       CASE s.reconciled WHEN 1 THEN 'Conciliado' WHEN 0 THEN 'Não conciliado' END AS [Conciliado],
       s.raw_record AS [Metadata],
       CONVERT(datetime2(7),TODATETIMEOFFSET(s.stored_utc,'+00:00') AT TIME ZONE 'E. South America Standard Time') AS [Data de extracao]
FROM ranked s
CROSS APPLY (VALUES(NULLIF(TRIM(s.entry_type),N''),CHARINDEX(N'::',REVERSE(TRIM(s.entry_type))))) reverse_type(text_value,delimiter)
CROSS APPLY (VALUES(CASE WHEN reverse_type.delimiter>0 THEN TRIM(RIGHT(reverse_type.text_value,reverse_type.delimiter-1))
                         ELSE reverse_type.text_value END)) entry(kind)
CROSS APPLY (VALUES(
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.original_value,N'R$',N''),N',',N'')),N'')),
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.interest_value,N'R$',N''),N',',N'')),N'')),
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.discount_value,N'R$',N''),N',',N'')),N'')),
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.value_to_pay,N'R$',N''),N',',N'')),N'')),
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.paid_value,N'R$',N''),N',',N'')),N'')),
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.accounting_value,N'R$',N''),N',',N'')),N'')),
    TRY_CONVERT(decimal(18,2),NULLIF(TRIM(REPLACE(REPLACE(s.cost_center_value,N'R$',N''),N',',N'')),N''))
)) amounts(original_value,interest_value,discount_value,value_to_pay,paid_value,accounting_value,cost_center_value)
WHERE s.row_rank=1;
