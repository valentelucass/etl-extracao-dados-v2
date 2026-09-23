package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Presence;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuoteMapper;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesSyntheticSource;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class AnalyticLaboratoryQuoteMapperTest {
    @Test
    void packagedValuesPreserveNanoDecimalAndOriginalWire() {
        final var value = new AnalyticQuoteMapper().map(AnalyticQuotesFixtures.data());
        assertEquals(36, value.fields().size());
        assertTrue(value.valid());
        assertEquals("1", value.sequenceCode().value());
        assertEquals(new BigDecimal("120.00000000"), value.totalValue().value());
        assertEquals(new BigDecimal("7.12500000"), value.deliverySubtotal().value());
        assertEquals(Instant.parse("2036-04-01T15:00:00.123456789Z"), value.nfseIssuedAt().value());
        assertTrue(value.nfseIssuedAt().raw().contains("-03:00"));
        assertEquals("SP", value.originState().value());
        assertEquals("RJ", value.destinationState().value());
        assertEquals(37, AnalyticQuotesSyntheticSource.release().response().fields().size());
    }

    @Test
    void absenceNullAndZeroRemainDifferentFromInvalidDecimalAndOversizedText() {
        final var row = AnalyticQuotesFixtures.data();
        row.remove("qoe_qes_delivery_subtotal");
        row.putNull("qoe_qes_collect_subtotal");
        row.put("qoe_qes_itr_subtotal", "0");
        var value = new AnalyticQuoteMapper().map(row);
        assertTrue(value.valid());
        assertEquals(Presence.ABSENT, value.deliverySubtotal().presence());
        assertEquals(Presence.NULL, value.collectSubtotal().presence());
        assertEquals(new BigDecimal("0.00000000"), value.itrSubtotal().value());
        row.put("qoe_qes_itr_subtotal", "0.000000001");
        row.put("qoe_qes_freight_comments", "á".repeat(1025));
        value = new AnalyticQuoteMapper().map(row);
        assertFalse(value.valid());
        assertNull(value.itrSubtotal().value());
        assertFalse(value.itrSubtotal().valid());
        assertFalse(value.freightComments().valid());
        assertEquals("0.000000001", value.itrSubtotal().raw());
        assertEquals(
                br.com.esl.etl.v2.plataforma.expansao.ExpansionValue.Wire.STRING,
                value.itrSubtotal().wire());
    }
}
