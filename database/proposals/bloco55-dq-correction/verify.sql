SET NOCOUNT ON;
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'bloco55-manifestos-backfill-v2' AND policy_fingerprint='f7a935a8e8017a71e49c8d5347a359e37c4c99708cd4030d0961bbea02411150' AND scope_fingerprint='7b8f6f9c3f5f851230de377156940ebf4c804c28449e3b83987795089571e241' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_CORRECTED_POLICY_REQUIRED',1;
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_check_policy WHERE policy_version=N'bloco55-manifestos-backfill-v2' AND policy_fingerprint='f7a935a8e8017a71e49c8d5347a359e37c4c99708cd4030d0961bbea02411150' AND maximum_failed_rows=0 AND maximum_failure_basis_points=0 AND threshold_owner_role=N'laboratory-owner')<>4 THROW 52854,N'EXACT_FOUR_STRICT_CHECKS_REQUIRED',1;
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'bloco55-cotacoes-backfill-v2' AND policy_fingerprint='e7d7ca60394ba3137d308716f8e6c952df287d8180f9d61a59c0046ff2327342' AND scope_fingerprint='5052e3801fe9bb121f3636a16894f82914a576cfd8c3982429956e728f6af887' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_CORRECTED_POLICY_REQUIRED',1;
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_check_policy WHERE policy_version=N'bloco55-cotacoes-backfill-v2' AND policy_fingerprint='e7d7ca60394ba3137d308716f8e6c952df287d8180f9d61a59c0046ff2327342' AND maximum_failed_rows=0 AND maximum_failure_basis_points=0 AND threshold_owner_role=N'laboratory-owner')<>4 THROW 52854,N'EXACT_FOUR_STRICT_CHECKS_REQUIRED',1;
IF NOT EXISTS(SELECT 1 FROM ctl.data_quality_policy WHERE policy_version=N'bloco55-localizacao_cargas-backfill-v2' AND policy_fingerprint='b124a3d48c76bb3d1a6a6bdc64d1cfd3647f9f7cba963f9fedb1a39a29c475af' AND scope_fingerprint='67585b27c8adc885f6faa94411e80f1a59f8f9316efa842e33d3324d65903d8d' AND policy_state=N'RATIFIED') THROW 52854,N'EXACT_CORRECTED_POLICY_REQUIRED',1;
IF (SELECT COUNT_BIG(*) FROM ctl.data_quality_check_policy WHERE policy_version=N'bloco55-localizacao_cargas-backfill-v2' AND policy_fingerprint='b124a3d48c76bb3d1a6a6bdc64d1cfd3647f9f7cba963f9fedb1a39a29c475af' AND maximum_failed_rows=0 AND maximum_failure_basis_points=0 AND threshold_owner_role=N'laboratory-owner')<>4 THROW 52854,N'EXACT_FOUR_STRICT_CHECKS_REQUIRED',1;
PRINT N'B55_THREE_CORRECTED_DQ_REVISIONS_EXACT';
IF EXISTS(SELECT 1 FROM ctl.data_quality_policy policy
 JOIN ctl.data_quality_check_policy check_1 ON check_1.policy_version=policy.policy_version AND check_1.policy_fingerprint=policy.policy_fingerprint AND check_1.check_ordinal=1
 JOIN ctl.data_quality_check_policy check_2 ON check_2.policy_version=policy.policy_version AND check_2.policy_fingerprint=policy.policy_fingerprint AND check_2.check_ordinal=2
 JOIN ctl.data_quality_check_policy check_3 ON check_3.policy_version=policy.policy_version AND check_3.policy_fingerprint=policy.policy_fingerprint AND check_3.check_ordinal=3
 JOIN ctl.data_quality_check_policy check_4 ON check_4.policy_version=policy.policy_version AND check_4.policy_fingerprint=policy.policy_fingerprint AND check_4.check_ordinal=4
 WHERE policy.policy_version IN(N'bloco55-manifestos-backfill-v2',N'bloco55-cotacoes-backfill-v2',N'bloco55-localizacao_cargas-backfill-v2') AND policy.policy_fingerprint<>LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
            N'dq-policy-v1|',
            DATALENGTH(policy.policy_version), N':', policy.policy_version, N'|',
            policy.scope_fingerprint, N'|', policy.expected_checks, N'|',
            policy.quarantine_sla_seconds, N'|',
            DATALENGTH(policy.threshold_owner_role), N':', policy.threshold_owner_role, N'|',
            DATALENGTH(policy.quarantine_sla_owner_role), N':',
                policy.quarantine_sla_owner_role, N'|',
            DATALENGTH(policy.retention_policy_version), N':',
                policy.retention_policy_version, N'|',
            DATALENGTH(policy.retention_owner_role), N':', policy.retention_owner_role, N'|',
            CONVERT(NVARCHAR(33), policy.effective_from_utc, 126), N'|',
            check_1.check_ordinal, N':', check_1.check_code, N':',
                check_1.maximum_failed_rows, N':', check_1.maximum_failure_basis_points, N':',
                DATALENGTH(check_1.threshold_owner_role), N':', check_1.threshold_owner_role, N'|',
            check_2.check_ordinal, N':', check_2.check_code, N':',
                check_2.maximum_failed_rows, N':', check_2.maximum_failure_basis_points, N':',
                DATALENGTH(check_2.threshold_owner_role), N':', check_2.threshold_owner_role, N'|',
            check_3.check_ordinal, N':', check_3.check_code, N':',
                check_3.maximum_failed_rows, N':', check_3.maximum_failure_basis_points, N':',
                DATALENGTH(check_3.threshold_owner_role), N':', check_3.threshold_owner_role, N'|',
            check_4.check_ordinal, N':', check_4.check_code, N':',
                check_4.maximum_failed_rows, N':', check_4.maximum_failure_basis_points, N':',
                DATALENGTH(check_4.threshold_owner_role), N':', check_4.threshold_owner_role
        )),2))) THROW 52854,N'CANONICAL_DQ_FINGERPRINT_MISMATCH',1;
