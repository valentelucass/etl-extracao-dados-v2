package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class Contract4924ConfigurationTest {

    @Test
    void remainsAbsentWhenNoAuxiliaryConfigurationExists() {
        assertTrue(Contract4924Configuration.optionalFrom(Map.of()).isEmpty());
    }

    @Test
    void acceptsAnExplicitFalseValueAsAnInactiveAuxiliaryProbe() {
        assertTrue(
                Contract4924Configuration.optionalFrom(Map.of("CONTRACT_4924_ENABLED", "false"))
                        .isEmpty());
    }

    @Test
    void rejectsAuxiliaryValuesThatWereAccidentallyLeftAlongsideAnInactiveProbe() {
        final Map<String, String> environment = new HashMap<>();
        environment.put("CONTRACT_4924_ENABLED", "false");
        environment.put("CONTRACT_4924_ROOT", "invoices");

        assertThrows(
                IllegalStateException.class,
                () -> Contract4924Configuration.optionalFrom(environment));
    }

    @Test
    void requiresEveryAuxiliaryKeyWithoutEchoingConfiguredValues() {
        final Map<String, String> environment = new HashMap<>();
        environment.put("CONTRACT_4924_ENABLED", "true");
        environment.put("CONTRACT_4924_ROOT", "invoices");
        environment.put("CONTRACT_4924_BUSINESS_FILTER", "closed_at");
        environment.put("CONTRACT_4924_CLOSED_WINDOW", "2026-08-13..2026-08-13");
        environment.put("CONTRACT_4924_ORDER_BY", "closed_at");
        environment.put("CONTRACT_4924_TRANSPORT", "GET_WITH_BODY");
        environment.put("CONTRACT_4924_MAX_CALLS", "2");

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> Contract4924Configuration.optionalFrom(environment));

        assertEquals(
                "Configuração obrigatória ausente: CONTRACT_4924_TEMPLATE_ID.",
                exception.getMessage());
        assertFalse(exception.getMessage().contains("invoices"));
    }

    @Test
    void acceptsOnlyTheDedicatedTemplateAndRedactsItsConfiguration() {
        final Contract4924Configuration configuration =
                Contract4924Configuration.optionalFrom(validEnvironment()).orElseThrow();

        assertEquals(Contract4924Configuration.TEMPLATE_ID, 4924);
        assertEquals("invoices", configuration.root());
        assertEquals("closed_at", configuration.businessFilter());
        assertEquals("invoice_number", configuration.orderBy());
        assertEquals(DataExportTransport.POST_JSON, configuration.approvedTransport());
        assertEquals(2, configuration.maximumCalls());
        assertFalse(configuration.toString().contains("invoices"));
    }

    @Test
    void rejectsAnAuxiliaryTemplateDifferentFrom4924() {
        final Map<String, String> environment = new HashMap<>(validEnvironment());
        environment.put("CONTRACT_4924_TEMPLATE_ID", "6389");

        assertThrows(
                IllegalArgumentException.class,
                () -> Contract4924Configuration.optionalFrom(environment));
    }

    static Map<String, String> validEnvironment() {
        return Map.ofEntries(
                Map.entry("CONTRACT_4924_ENABLED", "true"),
                Map.entry("CONTRACT_4924_TEMPLATE_ID", "4924"),
                Map.entry("CONTRACT_4924_ROOT", "invoices"),
                Map.entry("CONTRACT_4924_BUSINESS_FILTER", "closed_at"),
                Map.entry("CONTRACT_4924_CLOSED_WINDOW", "2026-08-13..2026-08-13"),
                Map.entry("CONTRACT_4924_ORDER_BY", "invoice_number"),
                Map.entry("CONTRACT_4924_TRANSPORT", "POST_JSON"),
                Map.entry("CONTRACT_4924_MAX_CALLS", "2"));
    }
}
