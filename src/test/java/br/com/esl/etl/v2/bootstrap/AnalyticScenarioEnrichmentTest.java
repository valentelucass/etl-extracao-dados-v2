package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreightAnalyticAttributesMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AnalyticScenarioEnrichmentTest {
    @Test
    void correctionOverridesRevisionAndDoesNotChangeSubsequentBaselineCapture() {
        final var mapper = new FreightAnalyticAttributesMapper();
        final var initial = mapper.map(AnalyticFreightScenarioData.load(1, false));
        final var revision = mapper.map(AnalyticFreightScenarioData.load(2, false));
        final var corrected = mapper.map(AnalyticFreightScenarioData.load(2, true));
        final var recaptured = mapper.map(AnalyticFreightScenarioData.load(1, false));

        assertEquals(new BigDecimal("12.12500000"), initial.km().value());
        assertEquals(new BigDecimal("18.12500000"), revision.km().value());
        assertEquals(new BigDecimal("25.25000000"), corrected.km().value());
        assertEquals(LocalDate.parse("2036-04-01"), revision.serviceDate().value());
        assertEquals(LocalDate.parse("2036-04-02"), corrected.serviceDate().value());
        assertEquals(LocalDate.parse("2036-04-03"), corrected.dataPrevisaoEntrega().value());
        assertEquals(initial, recaptured);
        assertTrue(corrected.km().valid());
    }
}
