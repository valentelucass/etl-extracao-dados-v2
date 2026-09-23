-- ANA17: use exact UTC second+nano in capture/reduction/application; DATETIME2(3) is legacy display only.
SET ANSI_NULLS ON; SET QUOTED_IDENTIFIER ON;
GO
CREATE FUNCTION core.ufn_analytic_manifest_clock(@payload NVARCHAR(MAX)) RETURNS TABLE AS RETURN(
 SELECT CONVERT(VARCHAR(24),CASE WHEN t.finished IS NOT NULL THEN 'FINISHED_AT' WHEN t.closed IS NOT NULL THEN 'CLOSED_AT'
 WHEN t.departured IS NOT NULL THEN 'DEPARTURED_AT' ELSE 'CREATED_AT' END) freshness_origin,
 DATEDIFF_BIG(SECOND,CONVERT(DATETIMEOFFSET(7),'1970-01-01T00:00:00Z',127),core.ufn_analytic_iso_time(raw.value)) epoch_second,
 CASE WHEN core.ufn_analytic_iso_time(raw.value) IS NULL THEN NULL WHEN mark.dot=0 THEN 0
 WHEN mark.suffix-mark.dot-1 BETWEEN 1 AND 9 THEN TRY_CONVERT(INT,LEFT(SUBSTRING(raw.value,mark.dot+1,mark.suffix-mark.dot-1)+N'000000000',9)) END nano
 FROM(SELECT JSON_VALUE(@payload,'$.finished_at') finished,JSON_VALUE(@payload,'$.closed_at') closed,
 JSON_VALUE(@payload,'$.departured_at') departured,JSON_VALUE(@payload,'$.created_at') created) t
 CROSS APPLY(SELECT COALESCE(t.finished,t.closed,t.departured,t.created) value) raw
 CROSS APPLY(SELECT CHARINDEX(N'.',raw.value) dot,CASE WHEN RIGHT(raw.value,1)=N'Z' THEN LEN(raw.value)
 WHEN SUBSTRING(raw.value,LEN(raw.value)-5,1) IN(N'+',N'-') THEN LEN(raw.value)-5 ELSE 0 END suffix) mark
);
GO
-- No domain DML in migrations; all preparations to date were rollback-only.
IF EXISTS(SELECT 1 FROM stg.analytic_manifest_attributes) OR EXISTS(SELECT 1 FROM core.analytic_manifest_snapshot)
 THROW 53631,N'ANA_EXACT_CLOCK_REQUIRES_EMPTY_NEW_LAB_TABLES',1;
ALTER TABLE stg.analytic_manifest_attributes ADD fresh_second BIGINT NOT NULL,fresh_nano INT NOT NULL,fresh_origin VARCHAR(24) NOT NULL;
GO
ALTER TABLE stg.analytic_manifest_attributes ADD CONSTRAINT CK_analytic_manifest_exact_clock CHECK(fresh_nano BETWEEN 0 AND 999999999
 AND fresh_origin IN('FINISHED_AT','CLOSED_AT','DEPARTURED_AT','CREATED_AT'));
ALTER TABLE core.analytic_manifest_snapshot ADD fresh_second BIGINT NOT NULL,fresh_nano INT NOT NULL;
GO
ALTER TABLE core.analytic_manifest_snapshot ADD CONSTRAINT CK_analytic_manifest_snapshot_clock CHECK(fresh_nano BETWEEN 0 AND 999999999);
GO
CREATE OR ALTER PROCEDURE stg.usp_capture_relational_laboratory
    @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@receipt_id UNIQUEIDENTIFIER,@now DATETIME2(7)
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    DECLARE @entity NVARCHAR(16),@date DATE,@state NVARCHAR(32),@fingerprint CHAR(64),@rows BIGINT,
        @maximum INT,@technical_now DATETIME2(3)=SYSUTCDATETIME();
    SELECT @entity=p.entity_name,@date=audit.business_window_start,@state=a.current_state,
        @fingerprint=a.contract_fingerprint,@rows=audit.records_delivered,@maximum=r.maximum_rows
    FROM ctl.execution_attempt a WITH(UPDLOCK,HOLDLOCK)
    JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
    JOIN ctl.execution_audit audit ON audit.execution_id=a.execution_id
    JOIN ctl.relational_lab_run r ON r.run_id=@run_id
    JOIN ctl.relational_lab_contract expected ON expected.run_id=r.run_id AND expected.entity_name=p.entity_name
        AND expected.contract_fingerprint=a.contract_fingerprint COLLATE Latin1_General_100_BIN2
    JOIN ctl.execution_source_protocol protocol ON protocol.execution_id=a.execution_id
    WHERE a.execution_id=@execution_id AND p.environment_name=N'LOCAL_SHADOW'
        AND p.source_instance=r.source_instance AND p.tenant_scope=r.tenant_scope
        AND a.contract_version=N'synthetic-relational-v1' AND protocol.source_kind=N'DATA_EXPORT'
        AND p.execution_mode IN(N'BOOTSTRAP',N'BACKFILL',N'REPLAY',N'INCREMENTAL')
        AND audit.status=N'COMPLETED' AND audit.business_window_start=audit.business_window_end
        AND audit.business_window_start BETWEEN DATEADD(DAY,-r.expansion_days,r.window_start)
            AND DATEADD(DAY,r.expansion_days,r.window_end)
        AND audit.template_id=CASE p.entity_name WHEN N'manifestos' THEN 6399 WHEN N'coletas' THEN 6908 WHEN N'fretes' THEN 6389 END
        AND audit.pages_fetched=audit.terminal_page
        AND audit.pages_fetched=(SELECT COUNT_BIG(*) FROM ctl.page_audit WHERE execution_id=a.execution_id)
        AND audit.records_delivered=(SELECT COALESCE(SUM(CONVERT(BIGINT,record_count)),0) FROM ctl.page_audit WHERE execution_id=a.execution_id)
        AND EXISTS(SELECT 1 FROM ctl.page_audit WHERE execution_id=a.execution_id AND page_number=audit.terminal_page AND record_count=0 AND is_terminal=1);
    IF @entity IS NULL OR (@state NOT IN(N'EXTRACTING',N'STAGED')
        AND NOT(@state=N'DEGRADED' AND EXISTS(SELECT 1 FROM ctl.relational_lab_capture WHERE execution_id=@execution_id AND run_id=@run_id)))
        THROW 53210,N'REL_LAB_CAPTURE_NOT_COMPLETE',1;
    IF EXISTS(SELECT 1 FROM ctl.relational_lab_capture WHERE execution_id=@execution_id)
    BEGIN
        IF NOT EXISTS(SELECT 1 FROM ctl.relational_lab_capture WHERE execution_id=@execution_id AND run_id=@run_id)
            THROW 53211,N'REL_LAB_CAPTURE_RUN_MISMATCH',1;
        SELECT considered,inserted,updated,noop FROM recon.relational_lab_receipt
            WHERE run_id=@run_id AND execution_id=@execution_id AND operation=N'CAPTURE';
        RETURN;
    END;
    IF @rows>@maximum OR @rows+(SELECT COALESCE(SUM(physical_rows),0) FROM ctl.relational_lab_capture WHERE run_id=@run_id)>@maximum
        THROW 53212,N'REL_LAB_CAPTURE_BUDGET',1;

    CREATE TABLE #raw(source_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2,
        payload NVARCHAR(MAX),presence NVARCHAR(MAX),epoch_second BIGINT,nano INT,
        status_code NVARCHAR(50) COLLATE Latin1_General_100_BIN2,terminal BIT,
        alias_presence NVARCHAR(8) COLLATE Latin1_General_100_BIN2,alias_key NVARCHAR(256) COLLATE Latin1_General_100_BIN2,
        component_field NVARCHAR(64),mdfe_key CHAR(44) COLLATE Latin1_General_100_BIN2);
    IF @entity=N'coletas'
    BEGIN
        IF @rows<>(SELECT COUNT_BIG(*) FROM stg.coleta_record WHERE execution_id=@execution_id)
            OR EXISTS(SELECT 1 FROM stg.execution_record WHERE execution_id=@execution_id AND validation_disposition<>N'VALID')
            THROW 53213,N'REL_LAB_COLETA_QUARANTINE',1;
        INSERT #raw
        SELECT d.source_key,d.payload_json,d.field_presence_json,e.epoch_second,e.nano,d.status_code,d.terminal,
            d.sequence_code_presence,CASE WHEN d.sequence_code_presence=N'VALUE'
                THEN CONCAT(CASE WHEN LEFT(d.sequence_code_json,1)=N'"' THEN N'STRING:' ELSE N'INTEGER:' END,
                    JSON_VALUE(CONCAT(N'{"v":',d.sequence_code_json,N'}'),N'$.v')) END,
            N'synthetic_item_key',NULL
        FROM stg.coleta_record d JOIN stg.coleta_exact_time e ON e.stage_record_id=d.stage_record_id
        WHERE d.execution_id=@execution_id;
    END;
    IF @entity=N'fretes'
    BEGIN
        IF @rows<>(SELECT COUNT_BIG(*) FROM stg.frete_record WHERE execution_id=@execution_id)
            OR EXISTS(SELECT 1 FROM stg.execution_record WHERE execution_id=@execution_id AND validation_disposition<>N'VALID')
            THROW 53214,N'REL_LAB_FRETE_QUARANTINE',1;
        INSERT #raw
        SELECT d.source_key,d.payload_json,d.field_presence_json,
            DATEDIFF_BIG(SECOND,CONVERT(DATETIME2,'19700101',112),d.freshness_at_utc),DATEPART(NANOSECOND,d.freshness_at_utc),
            d.status_code,d.terminal,
            CASE WHEN a.[key] IS NULL THEN N'ABSENT' WHEN a.type=0 THEN N'NULL' ELSE N'VALUE' END,
            CASE WHEN a.type IN(1,2) THEN CONCAT(CASE a.type WHEN 1 THEN N'STRING:' ELSE N'INTEGER:' END,a.value) END,
            N'synthetic_pick_item',NULL
        FROM stg.frete_record d OUTER APPLY(SELECT * FROM OPENJSON(d.payload_json) WHERE [key]=N'corporation_sequence_number') a
        WHERE d.execution_id=@execution_id;
    END;
    IF @entity=N'manifestos'
    BEGIN
        IF @rows<>(SELECT COUNT_BIG(*) FROM stg.manifesto_observation WHERE execution_id=@execution_id)
            OR EXISTS(SELECT 1 FROM stg.manifesto_observation WHERE execution_id=@execution_id AND validation_disposition<>N'VALID')
            THROW 53215,N'REL_LAB_MANIFESTO_QUARANTINE',1;
        -- Same latest-freshness cohort and declared status/metric rules as V022; no metric SUM.
        SELECT o.*,clock.epoch_second exact_epoch_second,clock.nano exact_nano,
            DENSE_RANK() OVER(PARTITION BY source_key ORDER BY clock.epoch_second DESC,clock.nano DESC) cohort_rank
        INTO #manifestos FROM stg.manifesto_observation o CROSS APPLY core.ufn_analytic_manifest_clock(o.payload_json) clock WHERE execution_id=@execution_id;
        IF EXISTS(SELECT 1 FROM #manifestos WHERE exact_epoch_second IS NULL OR exact_nano IS NULL)
            THROW 53630,N'ANA_MANIFEST_EXACT_CLOCK_INVALID',1;
        SELECT c.source_key,j.[key] COLLATE Latin1_General_100_BIN2 field_name,j.type wire_type,
            j.value COLLATE Latin1_General_100_BIN2 value INTO #fields
        FROM #manifestos c CROSS APPLY OPENJSON(c.root_fields_json) j WHERE cohort_rank=1;
        IF EXISTS(SELECT 1 FROM #fields GROUP BY source_key,field_name
            HAVING (MIN(wire_type)=0 AND MAX(wire_type)<>0)
                OR (field_name<>N'status' AND COUNT(DISTINCT CONCAT(wire_type,N':',value))>1)
                OR (field_name=N'status' AND COUNT(DISTINCT value)>1
                    AND MIN(CASE WHEN value IN(N'closed',N'in_transit',N'pending') THEN 1 ELSE 0 END)=0))
            THROW 53216,N'REL_LAB_MANIFESTO_COHORT_CONFLICT',1;
        IF EXISTS(SELECT 1 FROM #manifestos c CROSS APPLY OPENJSON(c.metric_values_json) j
            WHERE cohort_rank=1 AND JSON_VALUE(j.value,'$.presence')=N'VALUE'
            GROUP BY source_key,j.[key] HAVING COUNT(DISTINCT JSON_VALUE(j.value,'$.value'))>1)
            OR EXISTS(SELECT 1 FROM #manifestos WHERE cohort_rank=1 GROUP BY source_key HAVING COUNT(DISTINCT competence_json)>1)
            THROW 53217,N'REL_LAB_MANIFESTO_METRIC_CONFLICT',1;
        SELECT source_key,CASE WHEN MIN(CASE WHEN value IN(N'closed',N'in_transit',N'pending') THEN 1 ELSE 0 END)=1
            THEN CASE MAX(CASE value WHEN N'closed' THEN 3 WHEN N'in_transit' THEN 2 ELSE 1 END)
                WHEN 3 THEN N'closed' WHEN 2 THEN N'in_transit' ELSE N'pending' END ELSE MIN(value) END status_code
        INTO #status FROM #fields WHERE field_name=N'status' GROUP BY source_key;
        INSERT #raw
        SELECT d.source_key,d.payload_json,d.field_presence_json,
            d.exact_epoch_second,d.exact_nano,
            s.status_code,CONVERT(BIT,CASE WHEN s.status_code=N'closed' THEN 1 ELSE 0 END),N'ABSENT',NULL,
            N'mft_pfs_pck_sequence_code',d.mdfe_key
        FROM #manifestos d LEFT JOIN #status s ON s.source_key=d.source_key;
        -- MAN-01 retains each pick across all cohorts; MAN-02 reduces each MDF-e independently.
        SELECT *,DENSE_RANK() OVER(PARTITION BY source_key,mdfe_key ORDER BY exact_epoch_second DESC,exact_nano DESC) child_rank
        INTO #mdfe FROM #manifestos WHERE mdfe_key IS NOT NULL;
        IF EXISTS(SELECT 1 FROM #mdfe WHERE child_rank=1 GROUP BY source_key,mdfe_key HAVING COUNT(DISTINCT mdfe_number)>1)
            THROW 53252,N'REL_LAB_MDFE_PAIR_CONFLICT',1;
    END;
    IF (@entity<>N'manifestos' AND @rows<>(SELECT COUNT_BIG(*) FROM #raw))
        OR EXISTS(SELECT 1 FROM #raw WHERE epoch_second IS NULL OR nano IS NULL
            OR ISNULL(JSON_VALUE(payload,'$.synthetic_fixture'),N'false')<>N'true'
            OR LEN(source_key)>128 OR LEN(alias_key)>128)
        THROW 53218,N'REL_LAB_TYPED_SYNTHETIC_CAPTURE_REQUIRED',1;
    -- REL-LAB-07: equal exact time conflicts must not disappear behind terminal precedence.
    IF @entity<>N'manifestos' AND EXISTS(
        SELECT source_key,epoch_second,nano FROM
            (SELECT DISTINCT source_key,epoch_second,nano,alias_presence,alias_key,status_code,terminal FROM #raw) variants
        GROUP BY source_key,epoch_second,nano HAVING COUNT_BIG(*)>1)
        THROW 51428,N'Frescor igual ou desconhecido possui conteúdo divergente.',1;
    -- Coletas terminal precedence is applied across observations before freshness, matching V010.
    SELECT *,DENSE_RANK() OVER(PARTITION BY source_key ORDER BY
        CASE WHEN @entity=N'coletas' THEN CONVERT(INT,terminal) ELSE 0 END DESC,epoch_second DESC,nano DESC) freshness_rank
    INTO #ranked FROM #raw;
    SELECT DISTINCT source_key,alias_presence,alias_key,epoch_second,nano,status_code,terminal
    INTO #roots FROM #ranked WHERE freshness_rank=1;
    IF EXISTS(SELECT source_key FROM #roots GROUP BY source_key HAVING COUNT_BIG(*)>1)
        THROW 51428,N'Frescor igual ou desconhecido possui conteúdo divergente.',1;
    IF EXISTS(SELECT 1 FROM #roots s JOIN core.relational_lab_root t ON t.run_id=@run_id AND t.entity_name=@entity AND t.source_key=s.source_key
        WHERE s.epoch_second=t.epoch_second AND s.nano=t.nano
            AND EXISTS(SELECT s.alias_presence,s.alias_key,s.status_code,s.terminal
                EXCEPT SELECT t.alias_presence,t.alias_key,t.status_code,t.terminal))
        THROW 51428,N'Frescor igual ou desconhecido possui conteúdo divergente.',1;
    SELECT s.*,CASE WHEN t.source_key IS NULL THEN N'INSERTED'
        WHEN @entity=N'coletas' AND t.terminal=0 AND s.terminal=1 THEN N'UPDATED'
        WHEN @entity=N'coletas' AND t.terminal=1 AND s.terminal=0 THEN N'NOOP'
        WHEN s.epoch_second>t.epoch_second OR (s.epoch_second=t.epoch_second AND s.nano>t.nano) THEN N'UPDATED'
        ELSE N'NOOP' END action
    INTO #apply FROM #roots s LEFT JOIN core.relational_lab_root t
        ON t.run_id=@run_id AND t.entity_name=@entity AND t.source_key=s.source_key;
    INSERT ctl.relational_lab_capture VALUES(@execution_id,@run_id,@entity,@date,@fingerprint,@rows,
        (SELECT COUNT_BIG(*) FROM #roots),0,@now);
    INSERT stg.relational_lab_root SELECT @execution_id,source_key,alias_presence,alias_key,epoch_second,nano,status_code,terminal FROM #roots;
    INSERT core.relational_lab_root SELECT @run_id,@entity,source_key,@execution_id,alias_presence,alias_key,epoch_second,nano,status_code,terminal
        FROM #apply WHERE action=N'INSERTED';
    UPDATE t SET execution_id=@execution_id,alias_presence=s.alias_presence,alias_key=s.alias_key,
        epoch_second=s.epoch_second,nano=s.nano,status_code=s.status_code,terminal=s.terminal
    FROM core.relational_lab_root t JOIN #apply s ON t.run_id=@run_id AND t.entity_name=@entity AND t.source_key=s.source_key
        WHERE s.action=N'UPDATED';
    -- Presence/type live separately; malformed values are retained as invalid candidates, never cast to a key.
    SELECT DISTINCT r.source_key,CASE WHEN @entity=N'manifestos' THEN N'PICK' ELSE N'ITEM' END kind,
        CASE WHEN j.[key] IS NULL THEN N'ABSENT' WHEN j.type=0 THEN N'NULL' ELSE N'VALUE' END presence,
        CASE WHEN j.type=1 THEN CONCAT(N'STRING:',j.value) WHEN j.type=2 THEN CONCAT(N'INTEGER:',j.value)
            WHEN j.type<>0 THEN CONCAT(N'INVALID:',j.value) END component_key,
        CASE WHEN j.type=1 AND stg.fn_relational_lab_key_valid(CONCAT(N'STRING:',j.value))=1 THEN N'VALID'
            WHEN j.type=2 AND stg.fn_relational_lab_key_valid(CONCAT(N'INTEGER:',j.value))=1 THEN N'VALID'
            ELSE N'INVALID' END validity
    INTO #components FROM #raw r
    OUTER APPLY(SELECT * FROM OPENJSON(r.payload) WHERE [key] COLLATE Latin1_General_100_BIN2=r.component_field COLLATE Latin1_General_100_BIN2) j;
    IF EXISTS(SELECT 1 FROM #components WHERE LEN(component_key)>128) THROW 53219,N'REL_LAB_COMPONENT_BOUND',1;
    INSERT stg.relational_lab_component(execution_id,source_key,component_kind,presence,component_key,validity)
        SELECT @execution_id,source_key,kind,presence,component_key,validity FROM #components;
    INSERT stg.relational_lab_component(execution_id,source_key,component_kind,presence,component_key,validity)
        SELECT DISTINCT @execution_id,source_key,N'MDFE',N'VALUE',CONCAT(N'STRING:',mdfe_key),N'VALID'
        FROM #raw WHERE mdfe_key IS NOT NULL;
    IF @entity=N'manifestos'
        INSERT stg.relational_lab_mdfe(execution_id,source_key,mdfe_key,mdfe_number,freshness_at_utc)
        SELECT @execution_id,source_key,mdfe_key,MIN(mdfe_number),MAX(freshness_at_utc)
        FROM #mdfe WHERE child_rank=1 GROUP BY source_key,mdfe_key;
    UPDATE ctl.relational_lab_capture SET component_rows=(SELECT COUNT_BIG(*) FROM stg.relational_lab_component WHERE execution_id=@execution_id)
        WHERE execution_id=@execution_id;
    INSERT recon.relational_lab_receipt
    SELECT @receipt_id,@run_id,@execution_id,N'CAPTURE',COUNT_BIG(*),
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action=N'INSERTED' THEN 1 ELSE 0 END)),0),
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action=N'UPDATED' THEN 1 ELSE 0 END)),0),
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN action=N'NOOP' THEN 1 ELSE 0 END)),0),0,0,0,@now FROM #apply;
    IF @state=N'EXTRACTING'
    BEGIN
        EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTING',N'EXTRACTED',N'EXTRACTION_OK',@technical_now;
        EXEC ctl.usp_control_plane_transition_execution @execution_id,N'EXTRACTED',N'STAGED',N'STAGING_OK',@technical_now;
    END;
    SELECT considered,inserted,updated,noop FROM recon.relational_lab_receipt WHERE receipt_id=@receipt_id;
END;
GO


ALTER PROCEDURE core.usp_prepare_analytic_manifest @run_id UNIQUEIDENTIFIER,@execution_id UNIQUEIDENTIFIER,@fingerprint CHAR(64),@now DATETIME2(7)
AS BEGIN
 SET NOCOUNT ON; SET XACT_ABORT ON;
 EXEC ctl.usp_analytic_lab_lock @run_id,'MAT05';
 DECLARE @rel UNIQUEIDENTIFIER,@rows BIGINT,@maximum INT;
 SELECT @rel=g.relational_run,@maximum=r.maximum_rows FROM ctl.analytic_lab_source_group g JOIN ctl.analytic_lab_run r ON r.run_id=g.run_id WHERE g.run_id=@run_id;
 IF @rel IS NULL OR @execution_id IS NULL OR @now IS NULL THROW 53601,N'ANA_MANIFEST_PREPARATION_SCOPE',1;
 EXEC ctl.usp_relational_lab_lock @rel;
 SELECT @rows=c.physical_rows FROM ctl.relational_lab_capture c JOIN ctl.execution_attempt e ON e.execution_id=c.execution_id
 JOIN ctl.relational_lab_contract k ON k.run_id=c.run_id AND k.entity_name=c.entity_name
 WHERE c.run_id=@rel AND c.execution_id=@execution_id AND c.entity_name=N'manifestos'
 AND c.contract_fingerprint=@fingerprint COLLATE Latin1_General_100_BIN2 AND e.contract_fingerprint COLLATE Latin1_General_100_BIN2=c.contract_fingerprint
 AND k.contract_fingerprint=c.contract_fingerprint AND e.contract_version=N'synthetic-relational-v1';
 IF @rows IS NULL OR @rows>@maximum THROW 53602,N'ANA_MANIFEST_CAPTURE_PROFILE',1;
 IF EXISTS(SELECT 1 FROM ctl.analytic_manifest_preparation WHERE run_id=@run_id AND execution_id=@execution_id)
 BEGIN SELECT physical_rows,root_rows,blocked_roots FROM ctl.analytic_manifest_preparation WHERE run_id=@run_id AND execution_id=@execution_id; RETURN; END;
 SELECT o.* INTO #observations FROM stg.manifesto_observation o WHERE o.execution_id=@execution_id;
 IF (SELECT COUNT_BIG(*) FROM #observations)<>@rows THROW 53603,N'ANA_MANIFEST_CAPTURE_COUNT',1;
 IF EXISTS(SELECT 1 FROM #observations o CROSS APPLY OPENJSON(o.payload_json) j GROUP BY o.manifesto_observation_id,j.[key] COLLATE Latin1_General_100_BIN2 HAVING COUNT_BIG(*)>1)
 THROW 53604,N'ANA_MANIFEST_DUPLICATE_PROPERTY',1;
 SELECT o.manifesto_observation_id source_observation_id,d.field_name COLLATE Latin1_General_100_BIN2 field_name,d.kind,d.maximum,d.reducer,
 CONVERT(VARCHAR(6),CASE WHEN j.[key] IS NULL THEN 'ABSENT' WHEN j.type=0 THEN 'NULL' ELSE 'VALUE' END) presence,j.type wire_type,j.value raw_value
 INTO #fields FROM #observations o CROSS JOIN(VALUES (N'sequence_code','BIGINT',255,'COHERENT'),
 (N'mft_crn_psn_nickname','TEXT',255,'COHERENT'),
 (N'created_at','TIME',64,'COHERENT'),
 (N'departured_at','TIME',64,'COHERENT'),
 (N'closed_at','TIME',64,'COHERENT'),
 (N'finished_at','TIME',64,'COHERENT'),
 (N'status','TEXT',50,'STATUS'),
 (N'mft_mfs_number','INT',255,'CHILD'),
 (N'mft_mfs_key','TEXT',100,'CHILD'),
 (N'mdfe_status','TEXT',50,'COHERENT'),
 (N'mft_ape_name','TEXT',255,'COHERENT'),
 (N'mft_man_name','TEXT',255,'COHERENT'),
 (N'mft_vie_license_plate','TEXT',10,'COHERENT'),
 (N'mft_vie_vee_name','TEXT',255,'COHERENT'),
 (N'mft_vie_onr_name','TEXT',255,'COHERENT'),
 (N'mft_mdr_iil_name','TEXT',255,'COHERENT'),
 (N'vehicle_departure_km','INT',255,'COHERENT'),
 (N'closing_km','INT',255,'COHERENT'),
 (N'traveled_km','INT',255,'COHERENT'),
 (N'invoices_count','INT',255,'COHERENT'),
 (N'invoices_volumes','INT',255,'COHERENT'),
 (N'invoices_weight','DECIMAL',255,'COHERENT'),
 (N'total_taxed_weight','DECIMAL',255,'METRIC'),
 (N'total_cubic_volume','DECIMAL',255,'COHERENT'),
 (N'invoices_value','DECIMAL',255,'COHERENT'),
 (N'manifest_freights_total','DECIMAL',255,'METRIC'),
 (N'mft_pfs_pck_sequence_code','BIGINT',255,'CHILD'),
 (N'mft_cat_cot_number','TEXT',50,'COHERENT'),
 (N'daily_subtotal','DECIMAL',255,'COHERENT'),
 (N'total_cost','DECIMAL',255,'METRIC'),
 (N'operational_expenses_total','DECIMAL',255,'COHERENT'),
 (N'mft_a_t_inss_value','DECIMAL',255,'COHERENT'),
 (N'mft_a_t_sest_senat_value','DECIMAL',255,'COHERENT'),
 (N'mft_a_t_ir_value','DECIMAL',255,'COHERENT'),
 (N'paying_total','DECIMAL',255,'COHERENT'),
 (N'mft_uer_name','TEXT',255,'COHERENT'),
 (N'mft_aoe_rer_name','TEXT',255,'COHERENT'),
 (N'advance_subtotal','DECIMAL',255,'COHERENT'),
 (N'toll_subtotal','DECIMAL',255,'COHERENT'),
 (N'fleet_costs_subtotal','DECIMAL',255,'COHERENT'),
 (N'mft_s_n_svs_sge_pyr_nickname','TEXT',255,'COHERENT'),
 (N'mft_s_n_svs_sge_sse_name','TEXT',255,'COHERENT'),
 (N'mobile_read_at','TIME',64,'COHERENT'),
 (N'km','DECIMAL',255,'METRIC'),
 (N'manual_km','BIT',255,'COHERENT'),
 (N'generate_mdfe','BIT',255,'COHERENT'),
 (N'monitoring_request','BIT',255,'COHERENT'),
 (N'delivery_manifest_items_count','INT',255,'COHERENT'),
 (N'transfer_manifest_items_count','INT',255,'COHERENT'),
 (N'pick_manifest_items_count','INT',255,'COHERENT'),
 (N'dispatch_draft_manifest_items_count','INT',255,'COHERENT'),
 (N'consolidation_manifest_items_count','INT',255,'COHERENT'),
 (N'reverse_pick_manifest_items_count','INT',255,'COHERENT'),
 (N'manifest_items_count','INT',255,'METRIC'),
 (N'finalized_manifest_items_count','INT',255,'METRIC'),
 (N'uniq_destinations_count','INT',255,'COHERENT'),
 (N'contract_type','TEXT',50,'COHERENT'),
 (N'mft_mdr_contract_type','TEXT',50,'COHERENT'),
 (N'calculation_type','TEXT',50,'COHERENT'),
 (N'cargo_type','TEXT',255,'COHERENT'),
 (N'calculated_pick_count','INT',255,'COHERENT'),
 (N'calculated_delivery_count','INT',255,'COHERENT'),
 (N'calculated_dispatch_count','INT',255,'COHERENT'),
 (N'calculated_consolidation_count','INT',255,'COHERENT'),
 (N'calculated_reverse_pick_count','INT',255,'COHERENT'),
 (N'freight_subtotal','DECIMAL',255,'COHERENT'),
 (N'fuel_subtotal','DECIMAL',255,'COHERENT'),
 (N'pick_subtotal','DECIMAL',255,'COHERENT'),
 (N'delivery_subtotal','DECIMAL',255,'COHERENT'),
 (N'dispatch_subtotal','DECIMAL',255,'COHERENT'),
 (N'consolidation_subtotal','DECIMAL',255,'COHERENT'),
 (N'reverse_pick_subtotal','DECIMAL',255,'COHERENT'),
 (N'additionals_subtotal','DECIMAL',255,'COHERENT'),
 (N'discounts_subtotal','DECIMAL',255,'COHERENT'),
 (N'discount_value','DECIMAL',255,'COHERENT'),
 (N'driver_services_total','DECIMAL',255,'COHERENT'),
 (N'mft_aoe_comments','TEXT',4000,'COHERENT'),
 (N'mft_cat_cot_status','TEXT',50,'COHERENT'),
 (N'mft_iks_id','TEXT',100,'COHERENT'),
 (N'mft_s_n_sequence_code','TEXT',50,'COHERENT'),
 (N'mft_s_n_starting_at','TIME',64,'COHERENT'),
 (N'mft_s_n_ending_at','TIME',64,'COHERENT'),
 (N'mft_tl1_license_plate','TEXT',10,'COHERENT'),
 (N'mft_tl1_weight_capacity','DECIMAL',255,'COHERENT'),
 (N'mft_tl2_license_plate','TEXT',10,'COHERENT'),
 (N'mft_tl2_weight_capacity','DECIMAL',255,'COHERENT'),
 (N'mft_vie_weight_capacity','DECIMAL',255,'METRIC'),
 (N'mft_vie_cubic_weight','DECIMAL',255,'COHERENT'),
 (N'operational_comments','TEXT',4000,'COHERENT'),
 (N'closing_comments','TEXT',4000,'COHERENT'),
 (N'mft_mte_unloading_recipient_names','ARRAY',4000,'COHERENT'),
 (N'mft_mte_delivery_region_names','ARRAY',4000,'COHERENT')) d(field_name,kind,maximum,reducer)
 OUTER APPLY(SELECT * FROM OPENJSON(o.payload_json) j WHERE j.[key] COLLATE Latin1_General_100_BIN2=d.field_name COLLATE Latin1_General_100_BIN2) j;
 -- Raw is preserved by the immutable source; an excessive field is rejected before bounded audit insertion.
 IF EXISTS(SELECT 1 FROM #fields WHERE DATALENGTH(raw_value)>8000) THROW 53605,N'ANA_MANIFEST_RAW_BOUND',1;
 SELECT f.*,CONVERT(VARCHAR(32),CASE WHEN presence<>'VALUE' THEN NULL
 WHEN (kind IN('TEXT','TIME','DECIMAL') AND wire_type<>1) OR(kind IN('BIGINT','INT') AND wire_type<>2)
 OR(kind='BIT' AND wire_type<>3) OR(kind='ARRAY' AND wire_type<>4) THEN 'WIRE_TYPE'
 WHEN kind IN('TEXT','TIME','ARRAY') AND DATALENGTH(raw_value)>maximum*2 THEN 'TEXT_BOUND'
 WHEN kind='DECIMAL' AND stg.ufn_analytic_decimal_exact(raw_value) IS NULL THEN 'DECIMAL_EXACT'
 WHEN kind IN('BIGINT','INT') AND (raw_value COLLATE Latin1_General_100_BIN2 LIKE N'%[^0-9+-]%' OR TRY_CONVERT(BIGINT,raw_value) IS NULL
 OR(kind='INT' AND TRY_CONVERT(INT,raw_value) IS NULL)) THEN 'INTEGER_EXACT'
 WHEN kind='TIME' AND core.ufn_analytic_iso_time(raw_value) IS NULL THEN 'ISO_OFFSET_TIME'
 WHEN kind='ARRAY' AND ((SELECT COUNT_BIG(*) FROM OPENJSON(CASE WHEN kind='ARRAY' AND wire_type=4 THEN raw_value ELSE N'[]' END))>32 OR EXISTS(SELECT 1 FROM OPENJSON(CASE WHEN kind='ARRAY' AND wire_type=4 THEN raw_value ELSE N'[]' END) WHERE type<>1 OR DATALENGTH(value)>512)) THEN 'ARRAY_BOUND'
 END) issue,
 stg.ufn_analytic_decimal_exact(raw_value) decimal_value,
 TRY_CONVERT(BIGINT,CASE WHEN kind IN('BIGINT','INT') THEN raw_value END) integer_value,
 CASE WHEN kind='BIT' THEN CASE raw_value WHEN N'true' THEN 1 WHEN N'false' THEN 0 END END boolean_value,
 core.ufn_analytic_iso_time(CASE WHEN kind='TIME' AND DATALENGTH(raw_value)<=128 THEN raw_value END) time_value,
 CONVERT(NVARCHAR(4000),CASE WHEN kind IN('TEXT','ARRAY') THEN raw_value END) COLLATE Latin1_General_100_BIN2 text_value
 INTO #parsed FROM #fields f;
 SELECT o.manifesto_observation_id source_observation_id,clock.* INTO #exact_clock FROM #observations o CROSS APPLY core.ufn_analytic_manifest_clock(o.payload_json) clock;
 IF EXISTS(SELECT 1 FROM #exact_clock WHERE epoch_second IS NULL OR nano IS NULL) THROW 53630,N'ANA_MANIFEST_EXACT_CLOCK_INVALID',1;
 INSERT stg.analytic_manifest_attributes(source_observation_id,valid,fresh_second,fresh_nano,fresh_origin,[sequence_code],[mft_crn_psn_nickname],[created_at],[departured_at],[closed_at],[finished_at],[status],[mft_mfs_number],[mft_mfs_key],[mdfe_status],[mft_ape_name],[mft_man_name],[mft_vie_license_plate],[mft_vie_vee_name],[mft_vie_onr_name],[mft_mdr_iil_name],[vehicle_departure_km],[closing_km],[traveled_km],[invoices_count],[invoices_volumes],[invoices_weight],[total_taxed_weight],[total_cubic_volume],[invoices_value],[manifest_freights_total],[mft_pfs_pck_sequence_code],[mft_cat_cot_number],[daily_subtotal],[total_cost],[operational_expenses_total],[mft_a_t_inss_value],[mft_a_t_sest_senat_value],[mft_a_t_ir_value],[paying_total],[mft_uer_name],[mft_aoe_rer_name],[advance_subtotal],[toll_subtotal],[fleet_costs_subtotal],[mft_s_n_svs_sge_pyr_nickname],[mft_s_n_svs_sge_sse_name],[mobile_read_at],[km],[manual_km],[generate_mdfe],[monitoring_request],[delivery_manifest_items_count],[transfer_manifest_items_count],[pick_manifest_items_count],[dispatch_draft_manifest_items_count],[consolidation_manifest_items_count],[reverse_pick_manifest_items_count],[manifest_items_count],[finalized_manifest_items_count],[uniq_destinations_count],[contract_type],[mft_mdr_contract_type],[calculation_type],[cargo_type],[calculated_pick_count],[calculated_delivery_count],[calculated_dispatch_count],[calculated_consolidation_count],[calculated_reverse_pick_count],[freight_subtotal],[fuel_subtotal],[pick_subtotal],[delivery_subtotal],[dispatch_subtotal],[consolidation_subtotal],[reverse_pick_subtotal],[additionals_subtotal],[discounts_subtotal],[discount_value],[driver_services_total],[mft_aoe_comments],[mft_cat_cot_status],[mft_iks_id],[mft_s_n_sequence_code],[mft_s_n_starting_at],[mft_s_n_ending_at],[mft_tl1_license_plate],[mft_tl1_weight_capacity],[mft_tl2_license_plate],[mft_tl2_weight_capacity],[mft_vie_weight_capacity],[mft_vie_cubic_weight],[operational_comments],[closing_comments],[mft_mte_unloading_recipient_names],[mft_mte_delivery_region_names])
 SELECT p.source_observation_id,CONVERT(BIT,CASE WHEN COUNT(issue)=0 THEN 1 ELSE 0 END),MAX(clock.epoch_second),MAX(clock.nano),MAX(clock.freshness_origin),CONVERT(BIGINT,MAX(CASE WHEN field_name=N'sequence_code' AND issue IS NULL THEN integer_value END)) [sequence_code],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_crn_psn_nickname' AND issue IS NULL THEN text_value END)) [mft_crn_psn_nickname],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'created_at' AND issue IS NULL THEN time_value END)) [created_at],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'departured_at' AND issue IS NULL THEN time_value END)) [departured_at],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'closed_at' AND issue IS NULL THEN time_value END)) [closed_at],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'finished_at' AND issue IS NULL THEN time_value END)) [finished_at],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'status' AND issue IS NULL THEN text_value END)) [status],
 CONVERT(INT,MAX(CASE WHEN field_name=N'mft_mfs_number' AND issue IS NULL THEN integer_value END)) [mft_mfs_number],
 CONVERT(NVARCHAR(100),MAX(CASE WHEN field_name=N'mft_mfs_key' AND issue IS NULL THEN text_value END)) [mft_mfs_key],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'mdfe_status' AND issue IS NULL THEN text_value END)) [mdfe_status],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_ape_name' AND issue IS NULL THEN text_value END)) [mft_ape_name],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_man_name' AND issue IS NULL THEN text_value END)) [mft_man_name],
 CONVERT(NVARCHAR(10),MAX(CASE WHEN field_name=N'mft_vie_license_plate' AND issue IS NULL THEN text_value END)) [mft_vie_license_plate],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_vie_vee_name' AND issue IS NULL THEN text_value END)) [mft_vie_vee_name],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_vie_onr_name' AND issue IS NULL THEN text_value END)) [mft_vie_onr_name],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_mdr_iil_name' AND issue IS NULL THEN text_value END)) [mft_mdr_iil_name],
 CONVERT(INT,MAX(CASE WHEN field_name=N'vehicle_departure_km' AND issue IS NULL THEN integer_value END)) [vehicle_departure_km],
 CONVERT(INT,MAX(CASE WHEN field_name=N'closing_km' AND issue IS NULL THEN integer_value END)) [closing_km],
 CONVERT(INT,MAX(CASE WHEN field_name=N'traveled_km' AND issue IS NULL THEN integer_value END)) [traveled_km],
 CONVERT(INT,MAX(CASE WHEN field_name=N'invoices_count' AND issue IS NULL THEN integer_value END)) [invoices_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'invoices_volumes' AND issue IS NULL THEN integer_value END)) [invoices_volumes],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'invoices_weight' AND issue IS NULL THEN decimal_value END)) [invoices_weight],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'total_taxed_weight' AND issue IS NULL THEN decimal_value END)) [total_taxed_weight],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'total_cubic_volume' AND issue IS NULL THEN decimal_value END)) [total_cubic_volume],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'invoices_value' AND issue IS NULL THEN decimal_value END)) [invoices_value],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'manifest_freights_total' AND issue IS NULL THEN decimal_value END)) [manifest_freights_total],
 CONVERT(BIGINT,MAX(CASE WHEN field_name=N'mft_pfs_pck_sequence_code' AND issue IS NULL THEN integer_value END)) [mft_pfs_pck_sequence_code],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'mft_cat_cot_number' AND issue IS NULL THEN text_value END)) [mft_cat_cot_number],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'daily_subtotal' AND issue IS NULL THEN decimal_value END)) [daily_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'total_cost' AND issue IS NULL THEN decimal_value END)) [total_cost],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'operational_expenses_total' AND issue IS NULL THEN decimal_value END)) [operational_expenses_total],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_a_t_inss_value' AND issue IS NULL THEN decimal_value END)) [mft_a_t_inss_value],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_a_t_sest_senat_value' AND issue IS NULL THEN decimal_value END)) [mft_a_t_sest_senat_value],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_a_t_ir_value' AND issue IS NULL THEN decimal_value END)) [mft_a_t_ir_value],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'paying_total' AND issue IS NULL THEN decimal_value END)) [paying_total],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_uer_name' AND issue IS NULL THEN text_value END)) [mft_uer_name],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_aoe_rer_name' AND issue IS NULL THEN text_value END)) [mft_aoe_rer_name],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'advance_subtotal' AND issue IS NULL THEN decimal_value END)) [advance_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'toll_subtotal' AND issue IS NULL THEN decimal_value END)) [toll_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'fleet_costs_subtotal' AND issue IS NULL THEN decimal_value END)) [fleet_costs_subtotal],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_s_n_svs_sge_pyr_nickname' AND issue IS NULL THEN text_value END)) [mft_s_n_svs_sge_pyr_nickname],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'mft_s_n_svs_sge_sse_name' AND issue IS NULL THEN text_value END)) [mft_s_n_svs_sge_sse_name],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'mobile_read_at' AND issue IS NULL THEN time_value END)) [mobile_read_at],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'km' AND issue IS NULL THEN decimal_value END)) [km],
 CONVERT(BIT,MAX(CASE WHEN field_name=N'manual_km' AND issue IS NULL THEN CONVERT(INT,boolean_value) END)) [manual_km],
 CONVERT(BIT,MAX(CASE WHEN field_name=N'generate_mdfe' AND issue IS NULL THEN CONVERT(INT,boolean_value) END)) [generate_mdfe],
 CONVERT(BIT,MAX(CASE WHEN field_name=N'monitoring_request' AND issue IS NULL THEN CONVERT(INT,boolean_value) END)) [monitoring_request],
 CONVERT(INT,MAX(CASE WHEN field_name=N'delivery_manifest_items_count' AND issue IS NULL THEN integer_value END)) [delivery_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'transfer_manifest_items_count' AND issue IS NULL THEN integer_value END)) [transfer_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'pick_manifest_items_count' AND issue IS NULL THEN integer_value END)) [pick_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'dispatch_draft_manifest_items_count' AND issue IS NULL THEN integer_value END)) [dispatch_draft_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'consolidation_manifest_items_count' AND issue IS NULL THEN integer_value END)) [consolidation_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'reverse_pick_manifest_items_count' AND issue IS NULL THEN integer_value END)) [reverse_pick_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'manifest_items_count' AND issue IS NULL THEN integer_value END)) [manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'finalized_manifest_items_count' AND issue IS NULL THEN integer_value END)) [finalized_manifest_items_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'uniq_destinations_count' AND issue IS NULL THEN integer_value END)) [uniq_destinations_count],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'contract_type' AND issue IS NULL THEN text_value END)) [contract_type],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'mft_mdr_contract_type' AND issue IS NULL THEN text_value END)) [mft_mdr_contract_type],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'calculation_type' AND issue IS NULL THEN text_value END)) [calculation_type],
 CONVERT(NVARCHAR(255),MAX(CASE WHEN field_name=N'cargo_type' AND issue IS NULL THEN text_value END)) [cargo_type],
 CONVERT(INT,MAX(CASE WHEN field_name=N'calculated_pick_count' AND issue IS NULL THEN integer_value END)) [calculated_pick_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'calculated_delivery_count' AND issue IS NULL THEN integer_value END)) [calculated_delivery_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'calculated_dispatch_count' AND issue IS NULL THEN integer_value END)) [calculated_dispatch_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'calculated_consolidation_count' AND issue IS NULL THEN integer_value END)) [calculated_consolidation_count],
 CONVERT(INT,MAX(CASE WHEN field_name=N'calculated_reverse_pick_count' AND issue IS NULL THEN integer_value END)) [calculated_reverse_pick_count],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'freight_subtotal' AND issue IS NULL THEN decimal_value END)) [freight_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'fuel_subtotal' AND issue IS NULL THEN decimal_value END)) [fuel_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'pick_subtotal' AND issue IS NULL THEN decimal_value END)) [pick_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'delivery_subtotal' AND issue IS NULL THEN decimal_value END)) [delivery_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'dispatch_subtotal' AND issue IS NULL THEN decimal_value END)) [dispatch_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'consolidation_subtotal' AND issue IS NULL THEN decimal_value END)) [consolidation_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'reverse_pick_subtotal' AND issue IS NULL THEN decimal_value END)) [reverse_pick_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'additionals_subtotal' AND issue IS NULL THEN decimal_value END)) [additionals_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'discounts_subtotal' AND issue IS NULL THEN decimal_value END)) [discounts_subtotal],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'discount_value' AND issue IS NULL THEN decimal_value END)) [discount_value],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'driver_services_total' AND issue IS NULL THEN decimal_value END)) [driver_services_total],
 CONVERT(NVARCHAR(4000),MAX(CASE WHEN field_name=N'mft_aoe_comments' AND issue IS NULL THEN text_value END)) [mft_aoe_comments],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'mft_cat_cot_status' AND issue IS NULL THEN text_value END)) [mft_cat_cot_status],
 CONVERT(NVARCHAR(100),MAX(CASE WHEN field_name=N'mft_iks_id' AND issue IS NULL THEN text_value END)) [mft_iks_id],
 CONVERT(NVARCHAR(50),MAX(CASE WHEN field_name=N'mft_s_n_sequence_code' AND issue IS NULL THEN text_value END)) [mft_s_n_sequence_code],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'mft_s_n_starting_at' AND issue IS NULL THEN time_value END)) [mft_s_n_starting_at],
 CONVERT(DATETIMEOFFSET(7),MAX(CASE WHEN field_name=N'mft_s_n_ending_at' AND issue IS NULL THEN time_value END)) [mft_s_n_ending_at],
 CONVERT(NVARCHAR(10),MAX(CASE WHEN field_name=N'mft_tl1_license_plate' AND issue IS NULL THEN text_value END)) [mft_tl1_license_plate],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_tl1_weight_capacity' AND issue IS NULL THEN decimal_value END)) [mft_tl1_weight_capacity],
 CONVERT(NVARCHAR(10),MAX(CASE WHEN field_name=N'mft_tl2_license_plate' AND issue IS NULL THEN text_value END)) [mft_tl2_license_plate],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_tl2_weight_capacity' AND issue IS NULL THEN decimal_value END)) [mft_tl2_weight_capacity],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_vie_weight_capacity' AND issue IS NULL THEN decimal_value END)) [mft_vie_weight_capacity],
 CONVERT(DECIMAL(28,8),MAX(CASE WHEN field_name=N'mft_vie_cubic_weight' AND issue IS NULL THEN decimal_value END)) [mft_vie_cubic_weight],
 CONVERT(NVARCHAR(4000),MAX(CASE WHEN field_name=N'operational_comments' AND issue IS NULL THEN text_value END)) [operational_comments],
 CONVERT(NVARCHAR(4000),MAX(CASE WHEN field_name=N'closing_comments' AND issue IS NULL THEN text_value END)) [closing_comments],
 CONVERT(NVARCHAR(4000),MAX(CASE WHEN field_name=N'mft_mte_unloading_recipient_names' AND issue IS NULL THEN text_value END)) [mft_mte_unloading_recipient_names],
 CONVERT(NVARCHAR(4000),MAX(CASE WHEN field_name=N'mft_mte_delivery_region_names' AND issue IS NULL THEN text_value END)) [mft_mte_delivery_region_names]
 FROM #parsed p JOIN #exact_clock clock ON clock.source_observation_id=p.source_observation_id WHERE NOT EXISTS(SELECT 1 FROM stg.analytic_manifest_attributes old WHERE old.source_observation_id=p.source_observation_id) GROUP BY p.source_observation_id;
 INSERT stg.analytic_manifest_field_audit SELECT source_observation_id,field_name,presence,wire_type,CONVERT(NVARCHAR(4000),raw_value),issue,reducer FROM #parsed p
 WHERE NOT EXISTS(SELECT 1 FROM stg.analytic_manifest_field_audit old WHERE old.source_observation_id=p.source_observation_id AND old.field_name=p.field_name);
 -- Reconstruct the latest captured cohort across preparations; equal-clock corrections cannot hide behind the old root pointer.
 SELECT a.*,o.source_key COLLATE Latin1_General_100_BIN2 source_key,o.freshness_at_utc,o.observed_at_utc,DENSE_RANK() OVER(PARTITION BY o.source_key ORDER BY a.fresh_second DESC,a.fresh_nano DESC) freshness_rank
 INTO #all FROM stg.analytic_manifest_attributes a JOIN stg.manifesto_observation o ON o.manifesto_observation_id=a.source_observation_id
 JOIN ctl.relational_lab_capture c ON c.execution_id=o.execution_id
 WHERE c.run_id=@rel AND EXISTS(SELECT 1 FROM #observations changed WHERE changed.source_key=o.source_key);
 SELECT * INTO #cohort FROM #all WHERE freshness_rank=1;
 SELECT c.source_key,a.field_name,MAX(CASE WHEN a.issue IS NOT NULL THEN 1 ELSE 0 END) invalid,
 CASE WHEN COUNT(DISTINCT CONVERT(VARBINARY(8000),CASE WHEN a.reducer='METRIC' THEN CONVERT(NVARCHAR(100),stg.ufn_analytic_decimal_exact(a.raw_value)) ELSE a.raw_value END))>1
 OR(MIN(CASE WHEN a.presence='NULL' THEN 0 WHEN a.presence='VALUE' THEN 1 END)=0 AND MAX(CASE WHEN a.presence='VALUE' THEN 1 ELSE 0 END)=1 AND a.reducer='COHERENT')
 THEN 1 ELSE 0 END conflict
 INTO #conflicts FROM #cohort c JOIN stg.analytic_manifest_field_audit a ON a.source_observation_id=c.source_observation_id
 WHERE a.reducer NOT IN('CHILD','STATUS') GROUP BY c.source_key,a.field_name,a.reducer;
 SELECT c.source_key,MAX(c.fresh_second) fresh_second,MAX(c.fresh_nano) fresh_nano,MAX(c.freshness_at_utc) cohort_at,COUNT_BIG(*) source_rows,MAX(c.observed_at_utc) extracted_at,
 CONVERT(VARCHAR(32),CASE WHEN MIN(CONVERT(INT,c.valid))=0 THEN 'INVALID_ATTRIBUTES'
 WHEN EXISTS(SELECT 1 FROM #conflicts x WHERE x.source_key=c.source_key AND x.conflict=1) THEN 'ATTRIBUTE_CONFLICT'
 WHEN COUNT(DISTINCT c.status)>1 AND MAX(CASE WHEN c.status NOT IN(N'closed',N'in_transit',N'pending') THEN 1 ELSE 0 END)=1 THEN 'STATUS_CONFLICT'
 ELSE 'READY' END) disposition,MAX(c.[sequence_code]) [sequence_code],
 MAX(c.[mft_crn_psn_nickname]) [mft_crn_psn_nickname],
 MAX(c.[created_at]) [created_at],
 MAX(c.[departured_at]) [departured_at],
 MAX(c.[closed_at]) [closed_at],
 MAX(c.[finished_at]) [finished_at],
 CASE WHEN MAX(CASE WHEN c.status NOT IN(N'closed',N'in_transit',N'pending') THEN 1 ELSE 0 END)=0 THEN CASE MAX(CASE c.status WHEN N'closed' THEN 3 WHEN N'in_transit' THEN 2 WHEN N'pending' THEN 1 END) WHEN 3 THEN N'closed' WHEN 2 THEN N'in_transit' WHEN 1 THEN N'pending' END ELSE MAX(c.status) END [status],
 CASE WHEN COUNT(DISTINCT c.[mft_mfs_number])=1 THEN MAX(c.[mft_mfs_number]) END [mft_mfs_number],
 CASE WHEN COUNT(DISTINCT CONVERT(VARBINARY(8000),c.[mft_mfs_key]))=1 THEN MAX(c.[mft_mfs_key]) END [mft_mfs_key],
 CASE WHEN COUNT(DISTINCT CONVERT(VARBINARY(8000),c.[mdfe_status]))=1 THEN MAX(c.[mdfe_status]) END [mdfe_status],
 MAX(c.[mft_ape_name]) [mft_ape_name],
 MAX(c.[mft_man_name]) [mft_man_name],
 MAX(c.[mft_vie_license_plate]) [mft_vie_license_plate],
 MAX(c.[mft_vie_vee_name]) [mft_vie_vee_name],
 MAX(c.[mft_vie_onr_name]) [mft_vie_onr_name],
 MAX(c.[mft_mdr_iil_name]) [mft_mdr_iil_name],
 MAX(c.[vehicle_departure_km]) [vehicle_departure_km],
 MAX(c.[closing_km]) [closing_km],
 MAX(c.[traveled_km]) [traveled_km],
 MAX(c.[invoices_count]) [invoices_count],
 MAX(c.[invoices_volumes]) [invoices_volumes],
 MAX(c.[invoices_weight]) [invoices_weight],
 MAX(c.[total_taxed_weight]) [total_taxed_weight],
 MAX(c.[total_cubic_volume]) [total_cubic_volume],
 MAX(c.[invoices_value]) [invoices_value],
 MAX(c.[manifest_freights_total]) [manifest_freights_total],
 CASE WHEN COUNT(DISTINCT c.[mft_pfs_pck_sequence_code])=1 THEN MAX(c.[mft_pfs_pck_sequence_code]) END [mft_pfs_pck_sequence_code],
 MAX(c.[mft_cat_cot_number]) [mft_cat_cot_number],
 MAX(c.[daily_subtotal]) [daily_subtotal],
 MAX(c.[total_cost]) [total_cost],
 MAX(c.[operational_expenses_total]) [operational_expenses_total],
 MAX(c.[mft_a_t_inss_value]) [mft_a_t_inss_value],
 MAX(c.[mft_a_t_sest_senat_value]) [mft_a_t_sest_senat_value],
 MAX(c.[mft_a_t_ir_value]) [mft_a_t_ir_value],
 MAX(c.[paying_total]) [paying_total],
 MAX(c.[mft_uer_name]) [mft_uer_name],
 MAX(c.[mft_aoe_rer_name]) [mft_aoe_rer_name],
 MAX(c.[advance_subtotal]) [advance_subtotal],
 MAX(c.[toll_subtotal]) [toll_subtotal],
 MAX(c.[fleet_costs_subtotal]) [fleet_costs_subtotal],
 MAX(c.[mft_s_n_svs_sge_pyr_nickname]) [mft_s_n_svs_sge_pyr_nickname],
 MAX(c.[mft_s_n_svs_sge_sse_name]) [mft_s_n_svs_sge_sse_name],
 MAX(c.[mobile_read_at]) [mobile_read_at],
 MAX(c.[km]) [km],
 MAX(CONVERT(INT,c.[manual_km])) [manual_km],
 MAX(CONVERT(INT,c.[generate_mdfe])) [generate_mdfe],
 MAX(CONVERT(INT,c.[monitoring_request])) [monitoring_request],
 MAX(c.[delivery_manifest_items_count]) [delivery_manifest_items_count],
 MAX(c.[transfer_manifest_items_count]) [transfer_manifest_items_count],
 MAX(c.[pick_manifest_items_count]) [pick_manifest_items_count],
 MAX(c.[dispatch_draft_manifest_items_count]) [dispatch_draft_manifest_items_count],
 MAX(c.[consolidation_manifest_items_count]) [consolidation_manifest_items_count],
 MAX(c.[reverse_pick_manifest_items_count]) [reverse_pick_manifest_items_count],
 MAX(c.[manifest_items_count]) [manifest_items_count],
 MAX(c.[finalized_manifest_items_count]) [finalized_manifest_items_count],
 MAX(c.[uniq_destinations_count]) [uniq_destinations_count],
 MAX(c.[contract_type]) [contract_type],
 MAX(c.[mft_mdr_contract_type]) [mft_mdr_contract_type],
 MAX(c.[calculation_type]) [calculation_type],
 MAX(c.[cargo_type]) [cargo_type],
 MAX(c.[calculated_pick_count]) [calculated_pick_count],
 MAX(c.[calculated_delivery_count]) [calculated_delivery_count],
 MAX(c.[calculated_dispatch_count]) [calculated_dispatch_count],
 MAX(c.[calculated_consolidation_count]) [calculated_consolidation_count],
 MAX(c.[calculated_reverse_pick_count]) [calculated_reverse_pick_count],
 MAX(c.[freight_subtotal]) [freight_subtotal],
 MAX(c.[fuel_subtotal]) [fuel_subtotal],
 MAX(c.[pick_subtotal]) [pick_subtotal],
 MAX(c.[delivery_subtotal]) [delivery_subtotal],
 MAX(c.[dispatch_subtotal]) [dispatch_subtotal],
 MAX(c.[consolidation_subtotal]) [consolidation_subtotal],
 MAX(c.[reverse_pick_subtotal]) [reverse_pick_subtotal],
 MAX(c.[additionals_subtotal]) [additionals_subtotal],
 MAX(c.[discounts_subtotal]) [discounts_subtotal],
 MAX(c.[discount_value]) [discount_value],
 MAX(c.[driver_services_total]) [driver_services_total],
 MAX(c.[mft_aoe_comments]) [mft_aoe_comments],
 MAX(c.[mft_cat_cot_status]) [mft_cat_cot_status],
 MAX(c.[mft_iks_id]) [mft_iks_id],
 MAX(c.[mft_s_n_sequence_code]) [mft_s_n_sequence_code],
 MAX(c.[mft_s_n_starting_at]) [mft_s_n_starting_at],
 MAX(c.[mft_s_n_ending_at]) [mft_s_n_ending_at],
 MAX(c.[mft_tl1_license_plate]) [mft_tl1_license_plate],
 MAX(c.[mft_tl1_weight_capacity]) [mft_tl1_weight_capacity],
 MAX(c.[mft_tl2_license_plate]) [mft_tl2_license_plate],
 MAX(c.[mft_tl2_weight_capacity]) [mft_tl2_weight_capacity],
 MAX(c.[mft_vie_weight_capacity]) [mft_vie_weight_capacity],
 MAX(c.[mft_vie_cubic_weight]) [mft_vie_cubic_weight],
 MAX(c.[operational_comments]) [operational_comments],
 MAX(c.[closing_comments]) [closing_comments],
 MAX(c.[mft_mte_unloading_recipient_names]) [mft_mte_unloading_recipient_names],
 MAX(c.[mft_mte_delivery_region_names]) [mft_mte_delivery_region_names]
 INTO #roots FROM #cohort c GROUP BY c.source_key;
 IF (SELECT COUNT_BIG(*) FROM #roots)>@maximum THROW 53606,N'ANA_MANIFEST_ROOT_BOUND',1;
 INSERT ctl.analytic_manifest_preparation SELECT @run_id,@execution_id,@fingerprint,'analytic-manifest-preparation-v1',@rows,COUNT_BIG(*),COALESCE(SUM(CONVERT(BIGINT,CASE WHEN disposition<>'READY' THEN 1 ELSE 0 END)),0),@now FROM #roots;
 INSERT core.analytic_manifest_snapshot(run_id,execution_id,source_key,cohort_at,source_rows,disposition,extracted_at,fresh_second,fresh_nano,[sequence_code],[mft_crn_psn_nickname],[created_at],[departured_at],[closed_at],[finished_at],[status],[mft_mfs_number],[mft_mfs_key],[mdfe_status],[mft_ape_name],[mft_man_name],[mft_vie_license_plate],[mft_vie_vee_name],[mft_vie_onr_name],[mft_mdr_iil_name],[vehicle_departure_km],[closing_km],[traveled_km],[invoices_count],[invoices_volumes],[invoices_weight],[total_taxed_weight],[total_cubic_volume],[invoices_value],[manifest_freights_total],[mft_pfs_pck_sequence_code],[mft_cat_cot_number],[daily_subtotal],[total_cost],[operational_expenses_total],[mft_a_t_inss_value],[mft_a_t_sest_senat_value],[mft_a_t_ir_value],[paying_total],[mft_uer_name],[mft_aoe_rer_name],[advance_subtotal],[toll_subtotal],[fleet_costs_subtotal],[mft_s_n_svs_sge_pyr_nickname],[mft_s_n_svs_sge_sse_name],[mobile_read_at],[km],[manual_km],[generate_mdfe],[monitoring_request],[delivery_manifest_items_count],[transfer_manifest_items_count],[pick_manifest_items_count],[dispatch_draft_manifest_items_count],[consolidation_manifest_items_count],[reverse_pick_manifest_items_count],[manifest_items_count],[finalized_manifest_items_count],[uniq_destinations_count],[contract_type],[mft_mdr_contract_type],[calculation_type],[cargo_type],[calculated_pick_count],[calculated_delivery_count],[calculated_dispatch_count],[calculated_consolidation_count],[calculated_reverse_pick_count],[freight_subtotal],[fuel_subtotal],[pick_subtotal],[delivery_subtotal],[dispatch_subtotal],[consolidation_subtotal],[reverse_pick_subtotal],[additionals_subtotal],[discounts_subtotal],[discount_value],[driver_services_total],[mft_aoe_comments],[mft_cat_cot_status],[mft_iks_id],[mft_s_n_sequence_code],[mft_s_n_starting_at],[mft_s_n_ending_at],[mft_tl1_license_plate],[mft_tl1_weight_capacity],[mft_tl2_license_plate],[mft_tl2_weight_capacity],[mft_vie_weight_capacity],[mft_vie_cubic_weight],[operational_comments],[closing_comments],[mft_mte_unloading_recipient_names],[mft_mte_delivery_region_names])
 SELECT @run_id,@execution_id,source_key,cohort_at,source_rows,disposition,extracted_at,fresh_second,fresh_nano,[sequence_code],[mft_crn_psn_nickname],[created_at],[departured_at],[closed_at],[finished_at],[status],[mft_mfs_number],[mft_mfs_key],[mdfe_status],[mft_ape_name],[mft_man_name],[mft_vie_license_plate],[mft_vie_vee_name],[mft_vie_onr_name],[mft_mdr_iil_name],[vehicle_departure_km],[closing_km],[traveled_km],[invoices_count],[invoices_volumes],[invoices_weight],[total_taxed_weight],[total_cubic_volume],[invoices_value],[manifest_freights_total],[mft_pfs_pck_sequence_code],[mft_cat_cot_number],[daily_subtotal],[total_cost],[operational_expenses_total],[mft_a_t_inss_value],[mft_a_t_sest_senat_value],[mft_a_t_ir_value],[paying_total],[mft_uer_name],[mft_aoe_rer_name],[advance_subtotal],[toll_subtotal],[fleet_costs_subtotal],[mft_s_n_svs_sge_pyr_nickname],[mft_s_n_svs_sge_sse_name],[mobile_read_at],[km],[manual_km],[generate_mdfe],[monitoring_request],[delivery_manifest_items_count],[transfer_manifest_items_count],[pick_manifest_items_count],[dispatch_draft_manifest_items_count],[consolidation_manifest_items_count],[reverse_pick_manifest_items_count],[manifest_items_count],[finalized_manifest_items_count],[uniq_destinations_count],[contract_type],[mft_mdr_contract_type],[calculation_type],[cargo_type],[calculated_pick_count],[calculated_delivery_count],[calculated_dispatch_count],[calculated_consolidation_count],[calculated_reverse_pick_count],[freight_subtotal],[fuel_subtotal],[pick_subtotal],[delivery_subtotal],[dispatch_subtotal],[consolidation_subtotal],[reverse_pick_subtotal],[additionals_subtotal],[discounts_subtotal],[discount_value],[driver_services_total],[mft_aoe_comments],[mft_cat_cot_status],[mft_iks_id],[mft_s_n_sequence_code],[mft_s_n_starting_at],[mft_s_n_ending_at],[mft_tl1_license_plate],[mft_tl1_weight_capacity],[mft_tl2_license_plate],[mft_tl2_weight_capacity],[mft_vie_weight_capacity],[mft_vie_cubic_weight],[operational_comments],[closing_comments],[mft_mte_unloading_recipient_names],[mft_mte_delivery_region_names] FROM #roots;
 UPDATE c SET snapshot_id=s.snapshot_id FROM core.analytic_manifest_current c JOIN core.analytic_manifest_snapshot s
 ON s.run_id=c.run_id AND s.source_key=c.source_key WHERE s.run_id=@run_id AND s.execution_id=@execution_id;
 INSERT core.analytic_manifest_current SELECT s.run_id,s.source_key,s.snapshot_id FROM core.analytic_manifest_snapshot s
 WHERE s.run_id=@run_id AND s.execution_id=@execution_id AND NOT EXISTS(SELECT 1 FROM core.analytic_manifest_current c WHERE c.run_id=s.run_id AND c.source_key=s.source_key);
 SELECT physical_rows,root_rows,blocked_roots FROM ctl.analytic_manifest_preparation WHERE run_id=@run_id AND execution_id=@execution_id;
END;
GO

ALTER VIEW core.analytic_lab_manifest_projection AS SELECT s.* FROM core.analytic_manifest_current c JOIN core.analytic_manifest_snapshot s ON s.snapshot_id=c.snapshot_id;
GO
