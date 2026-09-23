:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 THROW 52854,N'EXACT_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'bloco55-manifestos-backfill-v2' AND policy_fingerprint='f7a935a8e8017a71e49c8d5347a359e37c4c99708cd4030d0961bbea02411150' AND policy_state=N'RATIFIED';
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'bloco55-cotacoes-backfill-v2' AND policy_fingerprint='e7d7ca60394ba3137d308716f8e6c952df287d8180f9d61a59c0046ff2327342' AND policy_state=N'RATIFIED';
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'bloco55-localizacao_cargas-backfill-v2' AND policy_fingerprint='b124a3d48c76bb3d1a6a6bdc64d1cfd3647f9f7cba963f9fedb1a39a29c475af' AND policy_state=N'RATIFIED';
COMMIT TRANSACTION;
PRINT N'CORRECTED_POLICIES_REVOKED_OLD_INVALID_POLICIES_REMAIN_REVOKED_DATA_PRESERVED';
