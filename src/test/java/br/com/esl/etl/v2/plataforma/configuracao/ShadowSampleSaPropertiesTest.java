package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ShadowSampleSaPropertiesTest {
    private static final String SUFFIX =
            ";databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=false"
                    + ";encrypt=true;trustServerCertificate=false";

    @Test
    void acceptsOnlyTheSeparateSaSampleTargetAndLeavesWindowsGuardClosed() {
        for (final String authority : new String[] {"localhost", "localhost:1433"}) {
            final var target =
                    ShadowStorageProperties.enabledSampleSa(
                            "jdbc:sqlserver://" + authority + SUFFIX);
            assertTrue(target.usesSampleSaAuthentication());
            assertFalse(target.usesIntegratedSecurity());
            assertEquals(ShadowStorageTargetKind.LOCAL_EPHEMERAL, target.targetKind());
            ColetaTemporalLaboratorySession.validateSampleSaTarget(target);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> BoundedRuntimeDataSource.validateTarget(target));
            assertFalse(target.toString().contains(authority));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "example.test",
                "127.0.0.1",
                "[::1]",
                "localhost:1444",
                "localhost\\MSSQLSERVER",
                "localhost@remote",
                "localhost.evil"
            })
    void refusesAnyOtherAuthority(final String authority) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabledSampleSa(
                                "jdbc:sqlserver://" + authority + SUFFIX));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "database",
                "integrated",
                "tls",
                "trust",
                "missingTls",
                "credential",
                "user",
                "duplicate",
                "unicode",
                "override",
                "braces"
            })
    void refusesAmbiguityCredentialsAndUnverifiedTls(final String mutation) {
        final String original = "jdbc:sqlserver://localhost" + SUFFIX;
        final String url =
                switch (mutation) {
                    case "database" -> original.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA");
                    case "integrated" ->
                            original.replace("integratedSecurity=false", "integratedSecurity=true");
                    case "tls" -> original.replace("encrypt=true", "encrypt=false");
                    case "trust" ->
                            original.replace(
                                    "trustServerCertificate=false", "trustServerCertificate=true");
                    case "missingTls" -> original.replace(";encrypt=true", "");
                    case "credential" -> original + ";password=synthetic";
                    case "user" -> original + ";user=sa";
                    case "duplicate" -> original + ";Encrypt=true";
                    case "unicode" ->
                            original.replace("Certificate=false", "Certificate=fal\u017Fe");
                    case "override" -> original + ";hostNameInCertificate=remote";
                    case "braces" -> original + ";applicationName={synthetic}";
                    default -> throw new IllegalArgumentException("SYNTHETIC_MUTATION");
                };
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ShadowStorageProperties.enabledSampleSa(url));
        assertFalse(failure.toString().contains(url));
    }

    @Test
    void originalFactoryStillRequiresWindowsAndSaValidatorDoesNotAcceptIt() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ShadowStorageProperties.enabled(
                                ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                                "jdbc:sqlserver://localhost" + SUFFIX,
                                null));
        final var windows =
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost"
                                + SUFFIX.replace(
                                        "integratedSecurity=false", "integratedSecurity=true"),
                        null);
        assertTrue(windows.usesIntegratedSecurity());
        assertFalse(windows.usesSampleSaAuthentication());
        assertThrows(
                IllegalArgumentException.class,
                () -> ColetaTemporalLaboratorySession.validateSampleSaTarget(windows));
    }
}
