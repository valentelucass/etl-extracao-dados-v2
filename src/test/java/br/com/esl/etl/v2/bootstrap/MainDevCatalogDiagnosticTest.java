package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogAdmission;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogInspector;
import br.com.esl.etl.v2.plataforma.persistencia.controle.C78DevCatalogPlan;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.UUID;

/** Diagnostic controls only; actual inspector refuses before provider/connector/socket access. */
class MainDevCatalogDiagnosticTest {
    @Test
    void publishesTheClosedCodeFromAnActualBankCatalogRefusal() throws Exception {
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var plan = C78DevCatalogPlan.prepare(UUID.randomUUID(), target);
        final String privateContent = UUID.randomUUID().toString();
        final var admission =
                new C78DevCatalogAdmission() {
                    @Override
                    public void beforeOpen(final C78DevCatalogPlan ignored) throws SQLException {
                        throw new SQLException(privateContent);
                    }

                    @Override
                    public void afterClose(
                            final C78DevCatalogPlan ignored,
                            final boolean closed,
                            final int attempted) {
                        throw new AssertionError("NO_CONNECTOR_OR_CLOSE_EXPECTED");
                    }
                };
        final var failure =
                assertThrows(
                        C78DevCatalogInspector.CatalogFailure.class,
                        () ->
                                new C78DevCatalogInspector(
                                                tuple -> {
                                                    throw new AssertionError("NO_SOCKET_EXPECTED");
                                                })
                                        .inspect(
                                                plan,
                                                key -> {
                                                    throw new AssertionError(
                                                            "NO_PROVIDER_EXPECTED");
                                                },
                                                admission));
        final String actual = diagnostic(failure);
        assertTrue(actual.contains("DEV_CATALOG_REFUSED reason=ADMISSION_REFUSED"), actual);
        assertFalse(actual.contains(privateContent));
    }

    @Test
    void arbitrarySqlAndNestedExceptionContentNeverBecomesAReasonOrMessage() throws Exception {
        final String privateContent = UUID.randomUUID().toString();
        final String actual =
                diagnostic(
                        new SQLException(
                                privateContent, new IllegalArgumentException(privateContent)));
        assertTrue(actual.contains("DEV_WORKSPACE_PREPARATION_REFUSED type=java.sql.SQLException"));
        assertFalse(actual.contains(privateContent));
        assertFalse(actual.contains("DEV_CATALOG_REFUSED reason="));
    }

    private static String diagnostic(final Exception failure) throws Exception {
        try (var bytes = new ByteArrayOutputStream();
                var out = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
            Main.publishDevFailure(failure, out);
            return bytes.toString(StandardCharsets.UTF_8);
        }
    }
}
