-- ADR0048: typed projections over the existing staging. Never a base-ingestion lookup.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE PROCEDURE stg.usp_capture_relational_laboratory
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
        SELECT o.*,DENSE_RANK() OVER(PARTITION BY source_key ORDER BY freshness_at_utc DESC) cohort_rank
        INTO #manifestos FROM stg.manifesto_observation o WHERE execution_id=@execution_id;
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
            DATEDIFF_BIG(SECOND,CONVERT(DATETIME2,'19700101',112),d.freshness_at_utc),DATEPART(NANOSECOND,d.freshness_at_utc),
            s.status_code,CONVERT(BIT,CASE WHEN s.status_code=N'closed' THEN 1 ELSE 0 END),N'ABSENT',NULL,
            N'mft_pfs_pck_sequence_code',d.mdfe_key
        FROM #manifestos d LEFT JOIN #status s ON s.source_key=d.source_key WHERE d.cohort_rank=1;
        -- Old child observations stay in base staging; current root selection never erases them.
    END;
    IF (@entity<>N'manifestos' AND @rows<>(SELECT COUNT_BIG(*) FROM #raw))
        OR EXISTS(SELECT 1 FROM #raw WHERE epoch_second IS NULL OR nano IS NULL
            OR ISNULL(JSON_VALUE(payload,'$.synthetic_fixture'),N'false')<>N'true'
            OR LEN(source_key)>128 OR LEN(alias_key)>128)
        THROW 53218,N'REL_LAB_TYPED_SYNTHETIC_CAPTURE_REQUIRED',1;
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
        CASE WHEN j.type=1 AND LEN(j.value)>0 AND LEN(j.value)<=120 THEN N'VALID'
            WHEN j.type=2 AND j.value NOT LIKE N'%[^0-9]%' AND LEN(j.value)<=120 THEN N'VALID'
            ELSE N'INVALID' END validity
    INTO #components FROM #raw r
    OUTER APPLY(SELECT * FROM OPENJSON(r.payload) WHERE [key]=r.component_field) j;
    IF EXISTS(SELECT 1 FROM #components WHERE LEN(component_key)>128) THROW 53219,N'REL_LAB_COMPONENT_BOUND',1;
    INSERT stg.relational_lab_component(execution_id,source_key,component_kind,presence,component_key,validity)
        SELECT @execution_id,source_key,kind,presence,component_key,validity FROM #components;
    INSERT stg.relational_lab_component(execution_id,source_key,component_kind,presence,component_key,validity)
        SELECT DISTINCT @execution_id,source_key,N'MDFE',N'VALUE',CONCAT(N'STRING:',mdfe_key),N'VALID'
        FROM #raw WHERE mdfe_key IS NOT NULL;
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
