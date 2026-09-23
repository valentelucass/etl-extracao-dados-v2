package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationRasterArtifactIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(90)
    void alternativeFilesRetainExplicitTripAndStopIdentityAndReplayThroughSameJdbc(
            final boolean alternate) throws Exception {
        final var artifact =
                new RasterArtifact(QualificationRasterArtifactFixtures.write(folder, 2, alternate));
        assertTrue(artifact.characterize(CancellationToken.none()).executable());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            new JdbcRasterLaboratory(session, Clock.systemUTC())
                    .start(
                            run,
                            artifact.window().start(),
                            artifact.window().endExclusive(),
                            artifact.zone(),
                            100000,
                            1000);
            final var runtime = new LocalRasterRuntime(session, Clock.systemUTC(), artifact.zone());
            final var first =
                    runtime.capture(
                            run,
                            ExecutionMode.BOOTSTRAP,
                            artifact.window(),
                            artifact.gateway(),
                            1000,
                            100000,
                            CancellationToken.none());
            assertEquals(4, first.receipt().applied());
            assertEquals(8, first.receipt().duplicates());
            assertEquals(0, first.receipt().quarantine());
            assertEquals(0, first.receipt().unbound());
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT trip_key,placa_veiculo,tempo_total_viagem FROM core.analytic_raster_trip"
                                            + " WHERE run_id=? ORDER BY trip_key")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                try (var row = sql.executeQuery()) {
                    assertTrue(row.next());
                    assertEquals("synthetic-explicit-journey-1", row.getString(1));
                    assertEquals(alternate ? "SYN0009" : "SYN0001", row.getString(2));
                    assertEquals(alternate ? 125 : 90, row.getInt(3));
                    assertTrue(row.next());
                    assertFalse(row.next());
                }
            }
            final var replay =
                    runtime.capture(
                            run,
                            ExecutionMode.REPLAY,
                            artifact.window(),
                            artifact.gateway(),
                            1000,
                            100000,
                            CancellationToken.none());
            assertEquals(4, replay.receipt().noops());
            assertEquals(0, replay.receipt().applied());
        }
    }

    @Test
    @Timeout(90)
    void incompleteTerminalFromArtifactCannotPublishAnyTripOrStop() throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 2, false);
        QualificationRasterArtifactFixtures.mutate(
                file, "manifest", value -> ((ObjectNode) value).put("complete", false));
        final var artifact = new RasterArtifact(file);
        assertFalse(artifact.characterize(CancellationToken.none()).executable());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            new JdbcRasterLaboratory(session, Clock.systemUTC())
                    .start(
                            run,
                            artifact.window().start(),
                            artifact.window().endExclusive(),
                            artifact.zone(),
                            100000,
                            1000);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new LocalRasterRuntime(session, Clock.systemUTC(), artifact.zone())
                                    .capture(
                                            run,
                                            ExecutionMode.BOOTSTRAP,
                                            artifact.window(),
                                            artifact.gateway(),
                                            1000,
                                            100000,
                                            CancellationToken.none()));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip WHERE run_id=?",
                            run));
            assertEquals(
                    0,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=?",
                            run));
        }
    }
}
