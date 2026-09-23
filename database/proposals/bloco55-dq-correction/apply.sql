:On Error exit
SET NOCOUNT ON; SET XACT_ABORT ON;
IF DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW' OR @@TRANCOUNT<>0 THROW 52854,N'EXACT_TARGET_REQUIRED',1;
BEGIN TRANSACTION;
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'bloco55-manifestos-backfill-v1' AND policy_fingerprint='88127ab8dbc7e2c3c81b866362da1a13e7e8ac4af75b922628bd8abdea39c722' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_INVALID_PRIOR_POLICY_REQUIRED',1;
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'bloco55-manifestos-backfill-v1' AND policy_fingerprint='88127ab8dbc7e2c3c81b866362da1a13e7e8ac4af75b922628bd8abdea39c722';
INSERT ctl.data_quality_policy VALUES(N'bloco55-manifestos-backfill-v2','f7a935a8e8017a71e49c8d5347a359e37c4c99708cd4030d0961bbea02411150','7b8f6f9c3f5f851230de377156940ebf4c804c28449e3b83987795089571e241',4,3600,N'laboratory-owner',N'laboratory-owner',N'bloco55-preserve-v1',N'laboratory-owner',N'RATIFIED','2026-09-08T00:00:00.124',SYSUTCDATETIME());
INSERT ctl.data_quality_check_policy SELECT N'bloco55-manifestos-backfill-v2','f7a935a8e8017a71e49c8d5347a359e37c4c99708cd4030d0961bbea02411150',n,c,0,0,N'laboratory-owner' FROM (VALUES(1,N'COUNT_EQUATION'),(2,N'PAGE_TERMINALITY'),(3,N'PROMOTION_RECONCILIATION'),(4,N'QUARANTINE_SLA'))p(n,c);
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'bloco55-cotacoes-backfill-v1' AND policy_fingerprint='efbb2d1e09ab7f3d083072809f5bd97795f5c059ea3348309936b1909a75d0aa' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_INVALID_PRIOR_POLICY_REQUIRED',1;
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'bloco55-cotacoes-backfill-v1' AND policy_fingerprint='efbb2d1e09ab7f3d083072809f5bd97795f5c059ea3348309936b1909a75d0aa';
INSERT ctl.data_quality_policy VALUES(N'bloco55-cotacoes-backfill-v2','e7d7ca60394ba3137d308716f8e6c952df287d8180f9d61a59c0046ff2327342','5052e3801fe9bb121f3636a16894f82914a576cfd8c3982429956e728f6af887',4,3600,N'laboratory-owner',N'laboratory-owner',N'bloco55-preserve-v1',N'laboratory-owner',N'RATIFIED','2026-09-08T00:00:00.124',SYSUTCDATETIME());
INSERT ctl.data_quality_check_policy SELECT N'bloco55-cotacoes-backfill-v2','e7d7ca60394ba3137d308716f8e6c952df287d8180f9d61a59c0046ff2327342',n,c,0,0,N'laboratory-owner' FROM (VALUES(1,N'COUNT_EQUATION'),(2,N'PAGE_TERMINALITY'),(3,N'PROMOTION_RECONCILIATION'),(4,N'QUARANTINE_SLA'))p(n,c);
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'bloco55-localizacao_cargas-backfill-v1' AND policy_fingerprint='6cc4f9addad28a3c4526501bc0648ae8a2dc9a769590e990bb1d5af1ab5e00a4' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_INVALID_PRIOR_POLICY_REQUIRED',1;
UPDATE ctl.data_quality_policy SET policy_state=N'REVOKED' WHERE policy_version=N'bloco55-localizacao_cargas-backfill-v1' AND policy_fingerprint='6cc4f9addad28a3c4526501bc0648ae8a2dc9a769590e990bb1d5af1ab5e00a4';
INSERT ctl.data_quality_policy VALUES(N'bloco55-localizacao_cargas-backfill-v2','b124a3d48c76bb3d1a6a6bdc64d1cfd3647f9f7cba963f9fedb1a39a29c475af','67585b27c8adc885f6faa94411e80f1a59f8f9316efa842e33d3324d65903d8d',4,3600,N'laboratory-owner',N'laboratory-owner',N'bloco55-preserve-v1',N'laboratory-owner',N'RATIFIED','2026-09-08T00:00:00.124',SYSUTCDATETIME());
INSERT ctl.data_quality_check_policy SELECT N'bloco55-localizacao_cargas-backfill-v2','b124a3d48c76bb3d1a6a6bdc64d1cfd3647f9f7cba963f9fedb1a39a29c475af',n,c,0,0,N'laboratory-owner' FROM (VALUES(1,N'COUNT_EQUATION'),(2,N'PAGE_TERMINALITY'),(3,N'PROMOTION_RECONCILIATION'),(4,N'QUARANTINE_SLA'))p(n,c);
GO
:r "verify.sql"
GO
IF @@TRANCOUNT<>1 OR XACT_STATE()<>1 THROW 52854,N'EXACT_TRANSACTION_REQUIRED',1;
COMMIT TRANSACTION;
PRINT N'B55_THREE_DQ_REVISIONS_COMMITTED';
