package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.UUID;

/** Closed failure inputs exercise real guards; unexpected errors continue to the caller. */
public final class AnalyticScenarioFaults {
    private AnalyticScenarioFaults() {}

    public static RasterGateway incompleteRaster(final RasterGateway source) {
        return new RasterGateway() {
            @Override
            public Response fetch(final RasterWindow window)
                    throws IOException, InterruptedException {
                return new Response(source.fetch(window).body(), null);
            }

            @Override
            public RasterBinding binding(
                    final RasterWindow window, final int trip, final int stop) {
                return source.binding(window, trip, stop);
            }
        };
    }

    public static void rejectPartialCollectionSnapshot(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final Clock technicalClock,
            final CancellationToken token)
            throws SQLException {
        rejectPartialCollectionSnapshot(
                session, run, technicalClock, token, AnalyticScenarioObserver.NONE);
    }

    public static void rejectPartialCollectionSnapshot(
            final ColetaTemporalLaboratorySession session,
            final AnalyticScenarioRuntime.Run run,
            final Clock technicalClock,
            final CancellationToken token,
            final AnalyticScenarioObserver observer)
            throws SQLException {
        final var runtime =
                new LocalAnalyticCollectionRuntime(
                        session,
                        run.id(),
                        run.relational(),
                        AnalyticScenarioRuntime.policy(run.pageSize()),
                        AnalyticScenarioRuntime.LOGICAL_CLOCK,
                        technicalClock);
        final var captures = new ArrayList<UUID>(4);
        for (int ordinal = 0; ordinal < 4; ordinal++) {
            captures.add(
                    runtime.capture(
                                    AnalyticScenarioRuntime.START,
                                    ExecutionMode.BACKFILL,
                                    null,
                                    AnalyticCollectionsFixtures.source(
                                                    2, run.roots() - 1, run.pageSize())
                                            .observed(observer),
                                    token)
                            .source()
                            .executionId());
        }
        try {
            new JdbcAnalyticCollectionSweep(session)
                    .prepare(
                            new SyntheticCollectionSnapshot(
                                    run.id(), AnalyticScenarioRuntime.START, run.roots(), false),
                            UUID.randomUUID(),
                            captures,
                            token);
        } catch (final SQLException failure) {
            if (failure.getErrorCode() == 53775) {
                return;
            }
            throw failure;
        }
        throw new IllegalStateException("ANA_SCENARIO_INVALID_SNAPSHOT_ACCEPTED");
    }
}
