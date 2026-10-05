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

class ShadowSampleSqlPinPropertiesTest {
    static final String URL =
            "jdbc:sqlserver://localhost:1433;databaseName=ETL_SISTEMA_V2_SHADOW"
                    + ";integratedSecurity=false;encrypt=true;trustServerCertificate=false";

    @Test
    void onlyExplicitFixedManualApprovalCreatesTheSeparateMode() {
        final var target = ShadowStorageProperties.enabledSampleSqlPin(URL, "HERMES325fac56a833");
        assertTrue(target.usesSampleSqlPinAuthentication());
        assertTrue(target.usesSampleSaAuthentication());
        assertFalse(target.usesIntegratedSecurity());
        assertEquals("HERMES325fac56a833", target.approvalReference());
        assertThrows(
                IllegalArgumentException.class,
                () -> ColetaTemporalLaboratorySession.validateSampleSaTarget(target));
        assertThrows(
                IllegalArgumentException.class,
                () -> BoundedRuntimeDataSource.validateTarget(target));
        assertFalse(ShadowStorageProperties.enabledSampleSa(URL).usesSampleSqlPinAuthentication());
        assertThrows(
                IllegalArgumentException.class,
                () -> ShadowStorageProperties.enabledSampleSqlPin(URL, null));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShadowStorageProperties.enabledSampleSqlPin(URL, "arbitrary"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ShadowStorageProperties.enabledSampleSqlPin(URL, " HERMES325fac56a833"));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "host",
                "port",
                "database",
                "integrated",
                "encrypt",
                "trust",
                "pin",
                "trustManager",
                "socketFactory",
                "password",
                "missingEncrypt",
                "duplicate"
            })
    void targetAndTlsOverridesAreRefused(final String defect) {
        final String url =
                switch (defect) {
                    case "host" -> URL.replace("localhost", "remote.test");
                    case "port" -> URL.replace(":1433", ":1444");
                    case "database" -> URL.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA");
                    case "integrated" ->
                            URL.replace("integratedSecurity=false", "integratedSecurity=true");
                    case "encrypt" -> URL.replace("encrypt=true", "encrypt=false");
                    case "trust" ->
                            URL.replace(
                                    "trustServerCertificate=false", "trustServerCertificate=true");
                    case "pin" -> URL + ";serverCertificate=arbitrary.cer";
                    case "trustManager" -> URL + ";trustManagerClass=arbitrary";
                    case "socketFactory" -> URL + ";socketFactoryClass=arbitrary";
                    case "password" -> URL + ";password=synthetic";
                    case "missingEncrypt" -> URL.replace(";encrypt=true", "");
                    case "duplicate" -> URL + ";Encrypt=true";
                    default -> throw new IllegalArgumentException("SYNTHETIC_DEFECT");
                };
        assertThrows(
                IllegalArgumentException.class,
                () -> ShadowStorageProperties.enabledSampleSqlPin(url, "HERMES325fac56a833"));
    }
}
