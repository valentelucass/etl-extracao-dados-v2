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

class SequenceReferenceIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(1000)
    void distinctReferenceReleaseChangesConsumersWhileSourcePagesRemainIdentical(
            final boolean alternate) throws Exception {
        final var sequence =
                new LocalArtifactSequence(
                        SequenceReferenceFixtures.write(folder, alternate),
                        CancellationToken.none());
        final var first = sequence.steps().get(0).input().read();
        final var second = sequence.steps().get(1).input().read();
        for (final String key :
                java.util.List.of("sources", "expansions", "raster", "relations", "revision")) {
            assertEquals(first.path(key), second.path(key));
        }
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(60, 100000);
            final UUID run = UUID.randomUUID();
            final var results =
                    sequence.execute(
                            session,
                            run,
                            AnalyticScenarioObserver.NONE,
                            CancellationToken.none(),
                            stage -> {});
            for (final var result : results) {
                IntegralArtifactReplayIT.exact(result.comparison());
                assertEquals(33, result.comparison().sweepPreview().size());
            }
            assertEquals(3, results.size());
            assertEquals(
                    java.util.List.of("bootstrap", "reference", "recompose"),
                    results.stream().map(LocalArtifactSequence.StageResult::id).toList());
            assertEquals(2, results.get(0).referenceRevision());
            assertEquals(3, results.get(1).referenceRevision());
            assertEquals(results.get(0).sourceRevision(), results.get(1).sourceRevision());
            assertEquals(
                    2,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_quote_snapshot s "
                                    + "JOIN core.analytic_quote_snapshot p ON p.snapshot_id=s.previous_snapshot "
                                    + "WHERE s.run_id=? AND s.tariff_release<>p.tariff_release "
                                    + "AND s.freshness_at_utc=p.freshness_at_utc AND s.attribute_hash=p.attribute_hash",
                            run));
            assertEquals(
                    4,
                    AnalyticLaboratoryRasterIT.scalar(
                            session,
                            "SELECT COUNT_BIG(*) FROM core.analytic_quote_snapshot WHERE run_id=?",
                            run));
        }
    }
}
