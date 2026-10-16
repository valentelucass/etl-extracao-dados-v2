package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class C78ShadowStorageMetadataTest {
    @Test
    void explicitC78MetadataCarriesTheActualTargetWithoutInheritingSampleFlags() {
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var metadata = ShadowStorageProperties.enabledC78Local(target);
        assertTrue(metadata.auditEnabled());
        assertTrue(metadata.usesC78LocalAuthentication());
        assertFalse(metadata.usesIntegratedSecurity());
        assertFalse(metadata.usesSampleSaAuthentication());
        assertFalse(metadata.usesSampleSqlPinAuthentication());
        assertEquals(ShadowStorageTargetKind.LOCAL_EPHEMERAL, metadata.targetKind());
        assertEquals(target.jdbcUrl(), metadata.jdbcUrl());
        assertEquals(target.approvalReference(), metadata.approvalReference());
    }

    @Test
    void normalWindowsAndSampleMetadataKeepTheirOriginalPoliciesAndC78DefaultFalse() {
        assertFalse(ShadowStorageProperties.disabled().usesC78LocalAuthentication());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                C78LocalSqlTarget.URL,
                                null));
        final var windows =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true",
                        null);
        assertTrue(windows.usesIntegratedSecurity());
        assertFalse(windows.usesC78LocalAuthentication());
        final var sample = ShadowStorageProperties.enabledSampleSa(C78LocalSqlTarget.URL);
        assertTrue(sample.usesSampleSaAuthentication());
        assertFalse(sample.usesC78LocalAuthentication());
    }

    @Test
    void nullForeignTargetsAndPreviousTrustReferencesCannotBecomeC78Metadata() {
        assertThrows(
                NullPointerException.class, () -> ShadowStorageProperties.enabledC78Local(null));
        for (final String reference :
                new String[] {"HERMES325fac56a833", "USER_20261005_E3_TEST_RETAINED", ""}) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ShadowStorageProperties.enabledC78Local(
                                    new C78LocalSqlTarget(C78LocalSqlTarget.URL, reference)));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabledC78Local(
                                new C78LocalSqlTarget(
                                        C78LocalSqlTarget.URL.replace(
                                                "localhost:1433", "remote:1433"),
                                        C78LocalSqlTarget.APPROVAL_REFERENCE)));
    }
}
