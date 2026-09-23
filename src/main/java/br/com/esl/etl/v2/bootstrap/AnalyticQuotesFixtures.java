package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesSyntheticSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/** Packaged synthetic quotation inputs, lazily expanded across pages with exact repeats. */
public final class AnalyticQuotesFixtures {
    private AnalyticQuotesFixtures() {}

    public static ObjectNode data() {
        try (var input =
                AnalyticQuotesFixtures.class.getResourceAsStream(
                        "/analytic-laboratory/quote.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_QUOTE_FIXTURE_MISSING");
            }
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalStateException("ANA_QUOTE_FIXTURE_BOUND");
            }
            final var root = new ObjectMapper().readTree(bytes);
            if (!root.isObject() || !root.path("synthetic_fixture").asBoolean()) {
                throw new IllegalStateException("ANA_QUOTE_FIXTURE_MARKER");
            }
            return (ObjectNode) root;
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_QUOTE_FIXTURE_INVALID", failure);
        }
    }

    public static AnalyticQuotesSyntheticSource source(
            final int first, final int roots, final int pageSize) {
        if (first < 1
                || roots < 0
                || roots > 2048
                || first > 1000000 - roots
                || pageSize < 1
                || pageSize > 16) {
            throw new IllegalArgumentException("ANA_QUOTE_FIXTURE_SCOPE");
        }
        final var seed = data();
        return new AnalyticQuotesSyntheticSource(
                page -> {
                    final var rows = JsonNodeFactory.instance.arrayNode();
                    final int start = Math.multiplyExact(page - 1, pageSize);
                    for (int index = start;
                            index < Math.min(roots * 3, start + pageSize);
                            index++) {
                        rows.add(seed.deepCopy().put("sequence_code", first + index / 3));
                    }
                    return rows.toString();
                });
    }
}
