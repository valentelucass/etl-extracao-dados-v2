package br.com.esl.etl.v2.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/** Pure, bounded synthetic freight fixture transformation for revision and correction cycles. */
final class AnalyticFreightScenarioData {
    private AnalyticFreightScenarioData() {}

    static ObjectNode load(final int revision, final boolean correction) {
        try (var input =
                AnalyticFreightScenarioData.class.getResourceAsStream(
                        "/analytic-laboratory/freight-attributes.synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("ANA_SCENARIO_FREIGHT_RESOURCE");
            }
            final byte[] bytes = input.readNBytes(32769);
            if (bytes.length > 32768) {
                throw new IllegalStateException("ANA_SCENARIO_FREIGHT_RESOURCE_BOUND");
            }
            final var data = (ObjectNode) new ObjectMapper().readTree(bytes);
            if (revision > 1) {
                ((ObjectNode) data.path("attributes")).put("km", "18.12500000");
            }
            if (correction) {
                final var attributes = (ObjectNode) data.path("attributes");
                attributes.put("service_date", "2036-04-02");
                attributes.put("data_previsao_entrega", "2036-04-03");
                attributes.put("km", "25.25000000");
            }
            return data;
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_SCENARIO_FREIGHT_RESOURCE", failure);
        }
    }
}
