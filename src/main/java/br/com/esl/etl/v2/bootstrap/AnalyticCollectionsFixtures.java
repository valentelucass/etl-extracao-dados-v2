package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticCollectionSupplement;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.graphql.AnalyticCollectionSupplementMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/** Closed packaged source inputs and separate lateral fixture; no precomputed consumer rows. */
public final class AnalyticCollectionsFixtures {
    private AnalyticCollectionsFixtures() {}

    public static ObjectNode data() {
        return read("/analytic-laboratory/collection.synthetic.json");
    }

    public static AnalyticCollectionSupplement supplement() {
        return new AnalyticCollectionSupplementMapper()
                .map(read("/analytic-laboratory/collection-supplement.synthetic.json"));
    }

    public static RelationalSyntheticSource source(
            final int first, final int roots, final int pageSize) {
        if (first < 1
                || roots < 0
                || roots > 4096
                || first > 8193 - roots
                || pageSize < 1
                || pageSize > 16) {
            throw new IllegalArgumentException("ANA_COLLECTION_FIXTURE_SCOPE");
        }
        final var seed = data();
        final int rootsPerPage = Math.max(1, pageSize / 3);
        return new RelationalSyntheticSource(
                        page -> {
                            final int begin = Math.multiplyExact(page - 1, rootsPerPage);
                            final var rows = JsonNodeFactory.instance.arrayNode();
                            for (int index = begin;
                                    index < Math.min(roots, begin + rootsPerPage);
                                    index++) {
                                final int root = first + index;
                                for (int copy = 0; copy < 3; copy++) {
                                    rows.add(
                                            seed.deepCopy()
                                                    .put("id", 200000 + root)
                                                    .put("sequence_code", 100000 + root)
                                                    .put("synthetic_item_key", root));
                                }
                            }
                            return rows.toString();
                        })
                .withAnalyticCollectionDetails();
    }

    private static ObjectNode read(final String resource) {
        try (var input = AnalyticCollectionsFixtures.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("ANA_COLLECTION_FIXTURE_MISSING");
            }
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalStateException("ANA_COLLECTION_FIXTURE_BOUND");
            }
            final var parsed = new ObjectMapper().readTree(bytes);
            if (!parsed.isObject()) {
                throw new IllegalStateException("ANA_COLLECTION_FIXTURE_OBJECT");
            }
            return (ObjectNode) parsed;
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_COLLECTION_FIXTURE_INVALID", failure);
        }
    }
}
