package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.sombra.ShadowAuditFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShadowStoragePropertiesTest {

    @Test
    void remainsDisabledWithoutAnyStorageConfiguration() {
        final ShadowStorageProperties properties = ShadowStorageProperties.disabled();

        assertFalse(properties.auditEnabled());
        assertThrows(IllegalStateException.class, properties::jdbcUrl);
        assertThrows(IllegalStateException.class, properties::targetKind);
    }

    @Test
    void keepsTheAuditNoopAndDoesNotRequireADataSourceWhenDisabled() {
        final DataExportExtractionAudit audit =
                ShadowAuditFactory.create(ShadowStorageProperties.disabled(), null);

        audit.executionStarted(
                new DataExportExtractionAudit.ExecutionStarted(
                        UUID.fromString("00000000-0000-0000-0000-000000000001"),
                        DataExportTemplate.COLETAS,
                        new BusinessDateRange(LocalDate.of(2026, 8, 25), LocalDate.of(2026, 8, 25)),
                        Optional.empty(),
                        Instant.parse("2026-08-25T12:00:00Z")));
    }

    @Test
    void acceptsAnExplicitLoopbackEphemeralTarget() {
        final ShadowStorageProperties properties =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://127.0.0.1:14333;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true",
                        null);

        assertTrue(properties.auditEnabled());
        assertEquals(ShadowStorageTargetKind.LOCAL_EPHEMERAL, properties.targetKind());
        assertTrue(properties.jdbcUrl().contains("ETL_SISTEMA_V2_SHADOW"));
    }

    @Test
    void acceptsIntegratedSecurityForALocalTargetWithoutRuntimeCredentials() {
        final ShadowStorageProperties configured =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true",
                        null);

        assertTrue(configured.usesIntegratedSecurity());
    }

    @Test
    void trimsJdbcPropertyWhitespaceWithoutWeakeningIntegratedSecurity() {
        final ShadowStorageProperties configured =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost ; databaseName = ETL_SISTEMA_V2_SHADOW ; integratedSecurity"
                                + " = true ;",
                        null);

        assertTrue(configured.usesIntegratedSecurity());

        final String[] missingOrDisabledIntegratedSecurity = {
            "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW",
            "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=false",
            "jdbc:sqlserver://localhost; databaseName = ETL_SISTEMA_V2_SHADOW ; integratedSecurity ="
                    + " false"
        };
        for (final String jdbcUrl : missingOrDisabledIntegratedSecurity) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ShadowStorageProperties.enabled(
                                    ShadowStorageTargetKind.LOCAL_EPHEMERAL, jdbcUrl, null));
        }
    }

    @Test
    void requiresExactShadowDatabaseSpellingBeforeAnyJdbcComposition() {
        for (final String database :
                new String[] {"ETL_\u017FISTEMA_V2_SHADOW", "etl_sistema_v2_shadow"}) {
            final String jdbcUrl =
                    "jdbc:sqlserver://localhost;databaseName="
                            + database
                            + ";integratedSecurity=true";
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ShadowStorageProperties.enabled(
                                    ShadowStorageTargetKind.LOCAL_EPHEMERAL, jdbcUrl, null));
        }
    }

    @Test
    void rejectsUnicodeCaseFoldedJdbcPropertyName() {
        final String jdbcUrl =
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                        + "integratedSecurity=true;soc\u212AetTimeout=2000";

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.LOCAL_EPHEMERAL, jdbcUrl, null));
    }

    @Test
    void rejectsUnicodeCaseFoldedCertificateFlag() {
        final String jdbcUrl =
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                        + "integratedSecurity=true;encrypt=true;trustServerCertificate=fal\u017Fe";

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.APPROVED_NON_PRODUCTION,
                                jdbcUrl,
                                "CHG-TEST-001"));
    }

    @Test
    void requiresAnApprovalReferenceForANonProductionApprovedTarget() {
        assertThrows(
                IllegalStateException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.APPROVED_NON_PRODUCTION,
                                approvedNonProductionJdbcUrl(),
                                null));

        final ShadowStorageProperties configured =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.APPROVED_NON_PRODUCTION,
                        approvedNonProductionJdbcUrl(),
                        "CHG-TEST-001");

        assertEquals(ShadowStorageTargetKind.APPROVED_NON_PRODUCTION, configured.targetKind());
    }

    @Test
    void requiresValidatedTlsForEveryApprovedNonProductionTarget() {
        final String prefix =
                "jdbc:sqlserver://not-production.test;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true";
        final String[] invalidTlsConfigurations = {
            prefix,
            prefix + ";encrypt=true",
            prefix + ";encrypt=false;trustServerCertificate=false",
            prefix + ";encrypt=true;trustServerCertificate=true"
        };

        for (final String jdbcUrl : invalidTlsConfigurations) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            ShadowStorageProperties.enabled(
                                    ShadowStorageTargetKind.APPROVED_NON_PRODUCTION,
                                    jdbcUrl,
                                    "CHG-TEST-001"));
        }

        assertTrue(
                ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.APPROVED_NON_PRODUCTION,
                                approvedNonProductionJdbcUrl(),
                                "CHG-TEST-001")
                        .auditEnabled());
    }

    @Test
    void rejectsUnsafeTargetsWithoutLeakingTheConnectionValue() {
        final String remoteEndpoint = "jdbc:sqlserver://not-loopback.test:1433";
        final IllegalArgumentException remote =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ShadowStorageProperties.enabled(
                                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                        remoteEndpoint + ";databaseName=ETL_SISTEMA_V2_SHADOW",
                                        null));
        assertFalse(remote.toString().contains(remoteEndpoint));

        final String legacyDatabase = "jdbc:sqlserver://127.0.0.1:1433;databaseName=ETL_SISTEMA";
        final IllegalArgumentException legacy =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ShadowStorageProperties.enabled(
                                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                        legacyDatabase,
                                        null));
        assertFalse(legacy.toString().contains(legacyDatabase));

        final String duplicateDatabase =
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;databaseName=ETL_SISTEMA";
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.LOCAL_EPHEMERAL, duplicateDatabase, null));

        final String credentialInJdbc =
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;password=synthetic";
        final IllegalArgumentException credentials =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                ShadowStorageProperties.enabled(
                                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                        credentialInJdbc,
                                        null));
        assertFalse(credentials.toString().contains("synthetic"));

        final String duplicateAuthentication =
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;integratedSecurity=false";
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                duplicateAuthentication,
                                null));

        final String alternateCredential =
                "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=true;clientKeyPassword=synthetic";
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                alternateCredential,
                                null));
    }

    @Test
    void redactsConnectionAndApprovalReferenceInToString() {
        final String text =
                ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.APPROVED_NON_PRODUCTION,
                                approvedNonProductionJdbcUrl(),
                                "CHG-TEST-001")
                        .toString();

        assertFalse(text.contains("not-production.test"));
        assertFalse(text.contains("CHG-TEST-001"));
    }

    private static String approvedNonProductionJdbcUrl() {
        return "jdbc:sqlserver://not-production.test"
                + ";databaseName=ETL_SISTEMA_V2_SHADOW"
                + ";integratedSecurity=true"
                + ";encrypt=true"
                + ";trustServerCertificate=false";
    }
}
