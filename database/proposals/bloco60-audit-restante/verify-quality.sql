IF EXISTS(SELECT 1 FROM ctl.data_quality_policy policy
 JOIN ctl.data_quality_check_policy check_1 ON check_1.policy_version=policy.policy_version AND check_1.policy_fingerprint=policy.policy_fingerprint AND check_1.check_ordinal=1
 JOIN ctl.data_quality_check_policy check_2 ON check_2.policy_version=policy.policy_version AND check_2.policy_fingerprint=policy.policy_fingerprint AND check_2.check_ordinal=2
 JOIN ctl.data_quality_check_policy check_3 ON check_3.policy_version=policy.policy_version AND check_3.policy_fingerprint=policy.policy_fingerprint AND check_3.check_ordinal=3
 JOIN ctl.data_quality_check_policy check_4 ON check_4.policy_version=policy.policy_version AND check_4.policy_fingerprint=policy.policy_fingerprint AND check_4.check_ordinal=4
 WHERE policy.policy_version IN(N'bloco60-usuarios-backfill-v5',N'bloco60-usuarios-replay-v5') AND policy.policy_fingerprint<>LOWER(CONVERT(CHAR(64),HASHBYTES('SHA2_256',CONCAT(
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
