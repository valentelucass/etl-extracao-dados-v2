-- ADR0048 / REL-LAB-06. Plan and reconciliation receipts reference the existing control plane.
SET ANSI_NULLS ON;
SET QUOTED_IDENTIFIER ON;
SET XACT_ABORT ON;
GO
CREATE TABLE recon.relational_lab_partition (
    run_id UNIQUEIDENTIFIER NOT NULL REFERENCES ctl.relational_lab_run(run_id),
    execution_mode NVARCHAR(16) COLLATE Latin1_General_100_BIN2 NOT NULL,
    ordinal INT NOT NULL, business_date DATE NOT NULL, first_root INT NOT NULL, root_count INT NOT NULL,
    manifesto_execution UNIQUEIDENTIFIER NULL REFERENCES ctl.relational_lab_capture(execution_id),
    coleta_execution UNIQUEIDENTIFIER NULL REFERENCES ctl.relational_lab_capture(execution_id),
    frete_execution UNIQUEIDENTIFIER NULL REFERENCES ctl.relational_lab_capture(execution_id),
    completion_receipt UNIQUEIDENTIFIER NULL REFERENCES recon.relational_lab_receipt(receipt_id),
    CONSTRAINT PK_relational_lab_partition PRIMARY KEY(run_id,execution_mode,ordinal),
    CONSTRAINT UQ_relational_lab_partition_date UNIQUE(run_id,execution_mode,business_date),
    CONSTRAINT CK_relational_lab_partition CHECK(execution_mode IN(N'BOOTSTRAP',N'BACKFILL',N'REPLAY',N'INCREMENTAL')
        AND ordinal BETWEEN 1 AND 366 AND first_root BETWEEN 1 AND 8192
        AND root_count BETWEEN 0 AND 4096 AND first_root+root_count<=8193
        AND (completion_receipt IS NULL OR
            (manifesto_execution IS NOT NULL AND coleta_execution IS NOT NULL AND frete_execution IS NOT NULL)))
);
GO
CREATE TYPE recon.relational_lab_partition_batch AS TABLE(
    ordinal INT NOT NULL PRIMARY KEY,business_date DATE NOT NULL,first_root INT NOT NULL,root_count INT NOT NULL
);
GO
CREATE PROCEDURE recon.usp_plan_relational_lab_partitions
    @run_id UNIQUEIDENTIFIER,@mode NVARCHAR(16),@partitions recon.relational_lab_partition_batch READONLY
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    IF @mode IS NULL OR @mode NOT IN(N'BOOTSTRAP',N'BACKFILL',N'REPLAY',N'INCREMENTAL')
        OR (SELECT COUNT_BIG(*) FROM @partitions) NOT BETWEEN 1 AND 64
        THROW 53240,N'REL_LAB_PLAN_BOUND',1;
    IF EXISTS(SELECT 1 FROM @partitions p JOIN ctl.relational_lab_run r ON r.run_id=@run_id
        WHERE p.business_date<r.window_start OR p.business_date>r.window_end)
        OR NOT EXISTS(SELECT 1 FROM ctl.relational_lab_run WHERE run_id=@run_id)
        THROW 53241,N'REL_LAB_PLAN_WINDOW',1;
    IF EXISTS(SELECT ordinal,business_date,first_root,root_count FROM @partitions p
        WHERE EXISTS(SELECT 1 FROM recon.relational_lab_partition e WHERE e.run_id=@run_id AND e.execution_mode=@mode AND e.ordinal=p.ordinal)
        EXCEPT SELECT ordinal,business_date,first_root,root_count FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode)
        THROW 53242,N'REL_LAB_PLAN_RETRY_DIVERGENT',1;
    INSERT recon.relational_lab_partition(run_id,execution_mode,ordinal,business_date,first_root,root_count)
    SELECT @run_id,@mode,p.ordinal,p.business_date,p.first_root,p.root_count FROM @partitions p
        WHERE NOT EXISTS(SELECT 1 FROM recon.relational_lab_partition e WHERE e.run_id=@run_id AND e.execution_mode=@mode AND e.ordinal=p.ordinal);
    IF (SELECT MIN(ordinal) FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode)<>1
        OR (SELECT COUNT_BIG(*) FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode)
            <>(SELECT MAX(ordinal) FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode)
        OR EXISTS(SELECT 1 FROM recon.relational_lab_partition p JOIN recon.relational_lab_partition previous
            ON previous.run_id=p.run_id AND previous.execution_mode=p.execution_mode AND previous.ordinal=p.ordinal-1
            WHERE p.run_id=@run_id AND p.execution_mode=@mode AND p.business_date<>DATEADD(DAY,1,previous.business_date))
        THROW 53243,N'REL_LAB_PLAN_GAP',1;
END;
GO
CREATE PROCEDURE recon.usp_attach_relational_lab_capture
    @run_id UNIQUEIDENTIFIER,@mode NVARCHAR(16),@ordinal INT,@execution_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    DECLARE @entity NVARCHAR(16);
    SELECT @entity=c.entity_name FROM ctl.relational_lab_capture c
        JOIN ctl.execution_attempt a ON a.execution_id=c.execution_id
        JOIN ctl.execution_partition p ON p.partition_id=a.partition_id
        JOIN recon.relational_lab_partition slot ON slot.run_id=c.run_id AND slot.business_date=c.business_date
        WHERE c.run_id=@run_id AND c.execution_id=@execution_id AND slot.ordinal=@ordinal
            AND slot.execution_mode=@mode AND p.execution_mode=@mode;
    IF @entity IS NULL THROW 53244,N'REL_LAB_PARTITION_CAPTURE_MISMATCH',1;
    IF EXISTS(SELECT 1 FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode AND ordinal=@ordinal
        AND CASE @entity WHEN N'manifestos' THEN manifesto_execution WHEN N'coletas' THEN coleta_execution ELSE frete_execution END IS NOT NULL
        AND CASE @entity WHEN N'manifestos' THEN manifesto_execution WHEN N'coletas' THEN coleta_execution ELSE frete_execution END<>@execution_id)
        THROW 53245,N'REL_LAB_PARTITION_CAPTURE_IMMUTABLE',1;
    UPDATE recon.relational_lab_partition SET
        manifesto_execution=CASE WHEN @entity=N'manifestos' THEN @execution_id ELSE manifesto_execution END,
        coleta_execution=CASE WHEN @entity=N'coletas' THEN @execution_id ELSE coleta_execution END,
        frete_execution=CASE WHEN @entity=N'fretes' THEN @execution_id ELSE frete_execution END
    WHERE run_id=@run_id AND execution_mode=@mode AND ordinal=@ordinal;
END;
GO
CREATE PROCEDURE recon.usp_complete_relational_lab_partition
    @run_id UNIQUEIDENTIFIER,@mode NVARCHAR(16),@ordinal INT,@receipt_id UNIQUEIDENTIFIER
AS
BEGIN
    SET NOCOUNT ON;
    EXEC ctl.usp_relational_lab_lock @run_id;
    IF NOT EXISTS(SELECT 1 FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode AND ordinal=@ordinal
        AND manifesto_execution IS NOT NULL AND coleta_execution IS NOT NULL AND frete_execution IS NOT NULL)
        OR NOT EXISTS(SELECT 1 FROM recon.relational_lab_receipt WHERE run_id=@run_id AND receipt_id=@receipt_id
            AND operation=N'RESOLVE' AND orphaned=0 AND blocked=0)
        THROW 53246,N'REL_LAB_PARTITION_NOT_RECONCILED',1;
    UPDATE recon.relational_lab_partition SET completion_receipt=@receipt_id
        WHERE run_id=@run_id AND execution_mode=@mode AND ordinal=@ordinal AND completion_receipt IS NULL;
END;
GO
CREATE PROCEDURE recon.usp_relational_lab_partition_status @run_id UNIQUEIDENTIFIER,@mode NVARCHAR(16)
AS
BEGIN
    SET NOCOUNT ON;
    SELECT COUNT_BIG(*) planned,
        COALESCE(SUM(CONVERT(BIGINT,CASE WHEN completion_receipt IS NOT NULL THEN 1 ELSE 0 END)),0) completed,
        COALESCE(MIN(CASE WHEN completion_receipt IS NULL THEN ordinal END)-1,MAX(ordinal),0) contiguous_completed,
        MIN(CASE WHEN completion_receipt IS NULL THEN business_date END) first_incomplete
    FROM recon.relational_lab_partition WHERE run_id=@run_id AND execution_mode=@mode;
END;
GO
