package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Compatibility adapter for the original packaged synthetic scenario. */
final class AnalyticCollectionSweepFixtures {
    private AnalyticCollectionSweepFixtures() {}

    static List<CollectionSweepInput> inputs(
            final SyntheticCollectionSnapshot snapshot,
            final int pageSize,
            final AnalyticScenarioObserver observer) {
        if (!snapshot.date()
                .equals(
                        LocalDate.parse(
                                AnalyticCollectionsFixtures.data()
                                        .path("request_date")
                                        .asText()))) {
            throw new IllegalArgumentException("ANA_COLLECTION_SWEEP_FIXTURE_SCOPE");
        }
        final var inputs = new ArrayList<CollectionSweepInput>(4);
        for (int ordinal = 0; ordinal < 4; ordinal++) {
            inputs.add(
                    new CollectionSweepInput(
                            snapshot.run(),
                            snapshot.date(),
                            snapshot.fingerprint(),
                            AnalyticCollectionsFixtures.source(
                                            snapshot.omitFirst() ? 2 : 1,
                                            snapshot.expectedRoots(),
                                            pageSize)
                                    .observed(observer)));
        }
        return List.copyOf(inputs);
    }
}
