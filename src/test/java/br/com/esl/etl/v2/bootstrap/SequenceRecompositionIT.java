package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SequenceRecompositionIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(1100)
    void supplementsRecomposeAllFactsWithoutAnySourceCapture(final boolean alternate)
            throws Exception {
        final var sequence =
                new LocalArtifactSequence(
                        SequenceRecompositionFixtures.write(folder, alternate, 2),
                        CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 100000);
            final var counts = new java.util.ArrayList<Long>();
            final var run = UUID.randomUUID();
            final var results =
                    sequence.execute(
                            session,
                            run,
                            AnalyticScenarioObserver.NONE,
                            CancellationToken.none(),
                            result -> {
                                try (var connection = session.getConnection();
                                        var sql =
                                                connection.prepareStatement(
                                                        "SELECT COUNT_BIG(*) FROM ctl.analytic_scenario_step WHERE cycle_id IN "
                                                                + "(SELECT cycle_id FROM ctl.analytic_scenario_cycle WHERE run_id=?)")) {
                                    sql.setQueryTimeout(10);
                                    sql.setString(1, run.toString());
                                    try (var row = sql.executeQuery()) {
                                        row.next();
                                        counts.add(row.getLong(1));
                                    }
                                } catch (final java.sql.SQLException failure) {
                                    throw new IllegalStateException(failure);
                                }
                            });
            assertEquals(4, results.size());
            for (final var result : results) {
                IntegralArtifactReplayIT.exact(result.comparison());
            }
            assertEquals(counts.get(2), counts.get(3));
            assertEquals(33L, counts.get(3));
        }
    }
}
