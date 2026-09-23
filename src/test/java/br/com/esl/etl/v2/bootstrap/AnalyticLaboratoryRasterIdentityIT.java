package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.WINDOW;
import static br.com.esl.etl.v2.bootstrap.AnalyticLaboratoryRasterIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryRasterIdentityIT {
    @Test
    void explicitChildIdentitySurvivesReorderingPartialCaptureAndRetirement() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final var first = AnalyticRasterFixtures.data();
            final var stopA = ((ObjectNode) first.path("ColetasEntregas").get(0)).deepCopy();
            stopA.put("CodigoCliente", "SYNTHETIC A").put("Ordem", "20");
            final var stopB = stopA.deepCopy().put("CodigoCliente", "SYNTHETIC B").putNull("Ordem");
            first.putArray("ColetasEntregas").add(stopA).add(stopB);
            capture(runtime, run, first, 1, List.of("a", "b"), true, false);
            final var reordered = first.deepCopy();
            reordered
                    .putArray("ColetasEntregas")
                    .add(stopB)
                    .add(stopA.deepCopy().put("Ordem", "30"));
            capture(runtime, run, reordered, 2, List.of("b", "a"), true, false);
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=? AND stop_key='synth"
                                    + "etic-stop-a'"
                                    + " AND codigo_cliente=N'SYNTHETIC A' AND ordem='30'",
                            run));
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=? AND stop_key='synthet"
                                    + "ic-stop-b'"
                                    + " AND ordem_parada IS NULL AND [ORDEM] IS NULL",
                            run));
            final var partial = first.deepCopy();
            partial.putArray("ColetasEntregas").add(stopB);
            capture(runtime, run, partial, 3, List.of("b"), true, false);
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
            final var retired = first.deepCopy();
            retired.putArray("ColetasEntregas").add(stopA);
            capture(runtime, run, retired, 4, List.of("a"), false, false);
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_stop WHERE run_id=?",
                            run));
            assertFalse(
                    capture(runtime, run, retired, 5, List.of("a"), true, false)
                            .receipt()
                            .complete());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
            assertTrue(
                    capture(runtime, run, retired, 6, List.of("a"), true, true)
                            .receipt()
                            .complete());
            assertEquals(
                    2,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
        }
    }

    @Test
    void malformedSentinelQuarantinesAndHigherValidRevisionRecovers() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            final var runtime = AnalyticLaboratoryRasterIT.start(session, run);
            final var first = AnalyticRasterFixtures.data();
            capture(runtime, run, first, 1, List.of("a"), true, false);
            final var bad = first.deepCopy().put("DataHoraRealFim", "1900-01-01T99:00:00");
            assertEquals(
                    1,
                    capture(runtime, run, bad, 2, List.of("a"), true, false)
                            .receipt()
                            .quarantine());
            assertEquals(
                    0,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
            assertTrue(
                    capture(runtime, run, first, 3, List.of("a"), true, false)
                            .receipt()
                            .complete());
            assertEquals(
                    1,
                    scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM pub.analytic_lab_sql_13 WHERE run_id=?",
                            run));
        }
    }

    private static LocalRasterRuntime.Capture capture(
            final LocalRasterRuntime runtime,
            final UUID run,
            final ObjectNode row,
            final int revision,
            final List<String> childBindings,
            final boolean active,
            final boolean reactivate)
            throws Exception {
        final RasterGateway gateway =
                new RasterGateway() {
                    @Override
                    public Response fetch(final RasterWindow window) {
                        return new Response(
                                ("[" + row + "]").getBytes(StandardCharsets.UTF_8),
                                new Terminal(
                                        window,
                                        1,
                                        childBindings.size(),
                                        "synthetic-identity-window",
                                        JdbcRasterLaboratory.SOURCE,
                                        JdbcRasterLaboratory.TENANT,
                                        JdbcRasterLaboratory.VERSION));
                    }

                    @Override
                    public RasterBinding binding(
                            final RasterWindow window, final int trip, final int stop) {
                        return new RasterBinding(
                                "synthetic-trip-1",
                                stop == 0 ? null : "synthetic-stop-" + childBindings.get(stop - 1),
                                revision,
                                stop == 0 || active,
                                stop > 0 && reactivate,
                                "synthetic-explicit-identity",
                                JdbcRasterLaboratory.SOURCE,
                                JdbcRasterLaboratory.TENANT,
                                JdbcRasterLaboratory.VERSION);
                    }
                };
        return runtime.capture(
                run, ExecutionMode.BACKFILL, WINDOW, gateway, 10, 100, CancellationToken.none());
    }
}
