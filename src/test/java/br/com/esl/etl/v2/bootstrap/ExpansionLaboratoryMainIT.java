package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ExpansionLaboratoryMainIT {
    @ParameterizedTest
    @ValueSource(strings = {"scenario", "hydrate", "replay", "status"})
    void commandsConsumeOneComposedScenarioAndDistinguishPending(final String command)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var options =
                    ExpansionLaboratoryMain.Options.parse(
                            new String[] {command, "--synthetic-expansion-lab"});
            final var status =
                    ExpansionLaboratoryMain.execute(options, session, CancellationToken.none())
                            .status();
            assertEquals(8, status.roots());
            assertEquals(16, status.components());
            assertEquals(2, status.invoices());
            if (command.equals("status")) {
                assertFalse(status.complete());
                assertEquals(1, status.pending());
                assertTrue(status.blocked() > 0);
            } else {
                assertTrue(status.complete());
                assertEquals(14, status.links());
                assertEquals(2, status.revenue());
                assertEquals(0, status.pending());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CAP", "FAT", "INV", "SIN", "INVOICE", "REVENUE"})
    void everyProjectionExecutesItsBoundedTypedJdbcQuery(final String projection)
            throws SQLException {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var options =
                    ExpansionLaboratoryMain.Options.parse(
                            new String[] {
                                "query",
                                "--synthetic-expansion-lab",
                                "--projection=" + projection,
                                "--limit=1"
                            });
            final var result =
                    ExpansionLaboratoryMain.execute(options, session, CancellationToken.none());
            assertTrue(result.status().complete());
            assertEquals(1, result.detailRows());
        }
    }
}
