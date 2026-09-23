package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

class FreightAnalyticAttributesMapperTest {
    private final FreightAnalyticAttributesMapper mapper = new FreightAnalyticAttributesMapper();

    @Test
    void absenceNullZeroNegativeAndExactNumericBoundsRemainDistinct() {
        final var data = envelope();
        final var row = (ObjectNode) data.path("attributes");
        row.putNull("modal")
                .put("total_cubic_volume", "0.00000000")
                .put("real_weight", "-0.10000000")
                .put("cte_id", "9223372036854775807")
                .put("service_type", 2147483647)
                .put("globalized", false);
        final var result = mapper.map(data);
        assertTrue(result.valid());
        assertEquals(Presence.NULL, result.modal().presence());
        assertEquals(Presence.ABSENT, result.paymentType().presence());
        assertEquals("0.00000000", result.totalCubicVolume().value().toPlainString());
        assertEquals("-0.10000000", result.realWeight().value().toPlainString());
        assertEquals(Long.MAX_VALUE, result.cteId().value());
        assertEquals(Integer.MAX_VALUE, result.serviceType().value());
        assertFalse(result.globalized().value());
    }

    @Test
    void invalidPrecisionRangesTypesAndDatesArePreservedWithIssues() {
        final var data = envelope();
        ((ObjectNode) data.path("attributes"))
                .put("total_cubic_volume", "0.000000001")
                .put("cte_id", "9223372036854775808")
                .put("service_type", "2147483648")
                .put("data_previsao_entrega", "2036-02-30")
                .put("globalized", "true")
                .put("origem_uf", "01234567890");
        final var result = mapper.map(data);
        assertFalse(result.valid());
        assertEquals("0.000000001", result.totalCubicVolume().raw());
        assertFalse(result.totalCubicVolume().valid());
        assertFalse(result.cteId().valid());
        assertFalse(result.serviceType().valid());
        assertFalse(result.dataPrevisaoEntrega().valid());
        assertFalse(result.globalized().valid());
        assertFalse(result.origemUf().valid());
    }

    @Test
    void unrecognizedEnvelopeAndComputedFactFieldsAreNotSourceAttributes() {
        final var source = envelope();
        ((ObjectNode) source.path("attributes")).put("performance_status", "NO PRAZO");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(source));
        final var wrong = envelope().put("version", "provider-approved");
        assertThrows(IllegalArgumentException.class, () -> mapper.map(wrong));
    }

    private static ObjectNode envelope() {
        final var result =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("provenance", "FIXTURE_SINTETICA_EXPLICITA")
                        .put("version", "synthetic-freight-attributes-v1");
        result.putObject("attributes");
        return result;
    }
}
