package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QualificationJdbcTargetTest {
    private static final String PREFIX =
            "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                    + "integratedSecurity=true;encrypt=true;";

    private static QualificationConfiguration configuration() throws Exception {
        return QualificationConfiguration.read(
                Path.of("src/main/resources/qualification-laboratory/config.synthetic.json"));
    }

    @Test
    void usesOnlyTheValidatedProcessTargetAndKeepsTheCertificateChoice() throws Exception {
        final var config = configuration();
        for (final String certificate : List.of("true", "false")) {
            final String url =
                    PREFIX
                            + "trustServerCertificate="
                            + certificate
                            + ";loginTimeout=5;socketTimeout=45000";
            assertEquals(url, config.validatedJdbcUrl(url));
        }
        assertEquals(
                PREFIX + "trustServerCertificate=false;loginTimeout=1;socketTimeout=2000",
                config.validatedJdbcUrl(
                        "jdbc:sqlserver://localhost;SOCKETTIMEOUT=2000;TRUSTSERVERCERTIFICATE=false;"
                                + "encrypt=true;integratedSecurity=true;databaseName=ETL_SISTEMA_V2_SHADOW;"
                                + "loginTimeout=1"));
    }

    @Test
    void refusesMissingRemoteCredentialAndUnboundedTargetsWithoutEchoingThem() throws Exception {
        final var config = configuration();
        final String valid =
                PREFIX + "trustServerCertificate=false;loginTimeout=5;socketTimeout=45000";
        final List<String> invalid =
                List.of(
                        "",
                        valid.replace("localhost", "127.0.0.1"),
                        valid.replace("localhost", "not-local.test"),
                        valid.replace("localhost;", "localhost:1433;"),
                        valid.replace("localhost;", "localhost\\SQLEXPRESS;"),
                        valid.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA"),
                        valid.replace("integratedSecurity=true", "integratedSecurity=false"),
                        valid.replace("encrypt=true", "encrypt=false"),
                        valid.replace("trustServerCertificate=false;", ""),
                        valid.replace("socketTimeout=45000", "socketTimeout=0"),
                        valid.replace("socketTimeout=45000", "socketTimeout=90001"),
                        valid.replace("loginTimeout=5", "loginTimeout=6"),
                        valid + ";user=sample",
                        valid + ";password=sample",
                        valid + ";domain=sample",
                        valid + ";applicationName=sample",
                        valid + ";databaseName=ETL_SISTEMA_V2_SHADOW",
                        valid + ";",
                        valid + "\npassword=sample",
                        valid.replace("localhost;", "localhost ;"),
                        valid.replace(
                                "trustServerCertificate=false", "trustServerCertificate={false}"));
        assertEquals(
                "QUAL_SHADOW_URL_REQUIRED",
                assertThrows(IllegalArgumentException.class, () -> config.validatedJdbcUrl(null))
                        .getMessage());
        for (final String url : invalid) {
            final var error =
                    assertThrows(
                            IllegalArgumentException.class, () -> config.validatedJdbcUrl(url));
            assertTrue(error.getMessage().startsWith("QUAL_SHADOW_URL_"));
            assertFalse(error.toString().contains("sample"));
            assertFalse(error.toString().contains("not-local.test"));
        }
    }

    @Test
    void projectsOnlyTheValidatedTargetToTheWorkerAndLeavesInvalidInputUntouched()
            throws Exception {
        final var config = configuration();
        final String valid =
                PREFIX + "trustServerCertificate=false;loginTimeout=5;socketTimeout=45000";
        final Map<String, String> environment = new HashMap<>();
        environment.put("V2_SHADOW_JDBC_URL", "untrusted");
        environment.put("V2_SOURCE_TOKEN", "marker");
        environment.put("v2_other", "synthetic-marker");
        environment.put("PATH", "synthetic-path");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        QualificationSqlOptIn.projectWorkerEnvironment(
                                environment, config, valid + ";domain=sample"));
        assertEquals("untrusted", environment.get("V2_SHADOW_JDBC_URL"));
        QualificationSqlOptIn.projectWorkerEnvironment(environment, config, valid);
        assertEquals(valid, environment.get("V2_SHADOW_JDBC_URL"));
        assertEquals("synthetic-path", environment.get("PATH"));
        assertFalse(environment.containsKey("V2_SOURCE_TOKEN"));
        assertFalse(environment.containsKey("v2_other"));
        assertEquals(2, environment.size());
    }
}
