-- Read-only, preparado; não executado no Bloco 52.
SET NOCOUNT ON;
IF OBJECT_ID(N'ctl.runtime_contract_evidence',N'U') IS NULL
    OR OBJECT_ID(N'ctl.usp_runtime_recovery',N'P') IS NULL
    OR OBJECT_ID(N'ctl.fn_runtime_recovery_identity',N'FN') IS NULL
    OR OBJECT_ID(N'ctl.fn_runtime_recovery_field',N'FN') IS NULL
    THROW 52340,N'RUNTIME_RECOVERY_STRUCTURE_MISSING',1;
IF NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'ctl.trg_runtime_contract_evidence_immutable')
    AND is_disabled=0 AND parent_id=OBJECT_ID(N'ctl.runtime_contract_evidence'))
    THROW 52341,N'RUNTIME_RECOVERY_IMMUTABILITY_MISSING',1;
IF (SELECT COUNT(*) FROM sys.columns WHERE object_id=OBJECT_ID(N'ctl.runtime_contract_evidence'))<>10
    OR NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'ctl.runtime_contract_evidence')
        AND name=N'identity_material' AND max_length=8000 AND is_nullable=0)
    OR NOT EXISTS(SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID(N'ctl.runtime_contract_evidence')
        AND name=N'audit_hash' AND max_length=64 AND is_nullable=0)
    THROW 52342,N'RUNTIME_RECOVERY_SHAPE_MISMATCH',1;
IF EXISTS(SELECT 1 FROM sys.database_permissions WHERE major_id=OBJECT_ID(N'ctl.usp_runtime_recovery')
    AND state IN(N'G',N'W'))
    THROW 52343,N'RUNTIME_RECOVERY_UNAUTHORIZED_GRANT',1;
IF OBJECT_ID(N'ctl.runtime_coleta_publication_receipt',N'U') IS NULL
    OR OBJECT_ID(N'ctl.fn_runtime_coleta_publication_hash',N'FN') IS NULL
    OR NOT EXISTS(SELECT 1 FROM sys.triggers WHERE object_id=OBJECT_ID(N'ctl.trg_runtime_coleta_receipt_immutable') AND is_disabled=0)
    THROW 52348,N'RUNTIME_COLETA_RECEIPT_STRUCTURE_MISSING',1;
IF (SELECT COUNT(*) FROM dbo.v2_procedure_grant_allowlist)<>41
    THROW 52344,N'RUNTIME_RECOVERY_ALLOWLIST_CHANGED',1;
PRINT N'Recuperação durável: estrutura validada, sem autorização operacional.';
