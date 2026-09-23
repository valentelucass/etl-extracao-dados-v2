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

class IntegralCampaignIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(1800)
    void sevenStagesKeepTheirStateAndCompareIndependentExpectedTuples(final boolean alternate)
            throws Exception {
        final var sequence =
                new LocalArtifactSequence(
                        IntegralCampaignFixtures.write(folder, alternate, 2),
                        CancellationToken.none());
        final var frontiers = new java.util.ArrayList<java.time.LocalDate>();
        final var run = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 100000);
            try {
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
                                                            "SELECT next_date FROM ctl.analytic_scenario_frontier WHERE run_id=?")) {
                                        sql.setQueryTimeout(10);
                                        sql.setString(1, run.toString());
                                        try (var row = sql.executeQuery()) {
                                            if (!row.next()) {
                                                throw new IllegalStateException(
                                                        "CAMPAIGN_FRONTIER_MISSING");
                                            }
                                            frontiers.add(row.getDate(1).toLocalDate());
                                        }
                                    } catch (final java.sql.SQLException failure) {
                                        throw new IllegalStateException(failure);
                                    }
                                });
                assertEquals(7, results.size());
                for (final var result : results) {
                    IntegralArtifactReplayIT.exact(result.comparison());
                    assertEquals(33, result.comparison().sweepPreview().size());
                }
                assertEquals(sequence.start(), frontiers.get(2));
                assertEquals(sequence.start().plusDays(1), frontiers.get(3));
                assertEquals(frontiers.get(3), frontiers.get(4));
                assertEquals(frontiers.get(4), frontiers.get(5));
                assertEquals(frontiers.get(5), frontiers.get(6));
                assertEquals(3, results.get(5).referenceRevision());
                assertEquals(results.get(4).sourceRevision(), results.get(5).sourceRevision());
            } catch (final java.sql.SQLException failure) {
                if ("QUAL_LINEAGE_COLLECTION_BINDING_COUNT".equals(failure.getMessage())) {
                    failure.addSuppressed(
                            SequenceBindingDiagnostics.observe(session, run, frontiers.size()));
                }
                throw failure;
            }
        }
    }
}
