package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.net.URI;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractTestConfigurationTest {

    @Test
    void readsOnlyContractVariablesAndRedactsSensitiveValues() {
        final Map<String, String> environment = new HashMap<>(validEnvironment());
        environment.put("API_BASE_URL", "https://must-not-be-used.example.test");
        environment.put("API_DATAEXPORT_TOKEN", "must-not-be-used-token");

        final ContractTestConfiguration configuration = ContractTestConfiguration.from(environment);

        assertEquals(
                URI.create("https://contract-dataexport.example.test"),
                configuration.dataExportProperties().baseUri());
        assertEquals(
                URI.create("https://contract-graphql.example.test/graphql"),
                configuration.graphQlEndpoint());
        assertEquals(
                ZoneId.of("America/Sao_Paulo"), configuration.dataExportProperties().sourceZone());
        assertEquals(3, configuration.maxCallsFor(DataExportTemplate.COLETAS));
        assertEquals(4, configuration.maxCallsFor(DataExportTemplate.FRETES));
        assertEquals(5, configuration.firstApprovedPageSize());
        assertEquals(25, configuration.secondApprovedPageSize());
        assertFalse(configuration.toString().contains("contract-dataexport-token"));
        assertFalse(configuration.toString().contains("contract-graphql-token"));
        assertFalse(configuration.toString().contains("contract-dataexport.example.test"));
        assertFalse(configuration.toString().contains("contract-graphql.example.test"));
    }

    @Test
    void doesNotFallBackToOperationalApiVariables() {
        final Map<String, String> environment =
                Map.of(
                        "API_BASE_URL", "https://must-not-be-used.example.test",
                        "API_DATAEXPORT_TOKEN", "must-not-be-used-token",
                        "API_DATAEXPORT_TIMEZONE", "America/Sao_Paulo");

        final IllegalStateException exception =
                assertThrows(
                        IllegalStateException.class,
                        () -> ContractTestConfiguration.from(environment));

        assertEquals(
                "Configuração obrigatória ausente: CONTRACT_DATAEXPORT_BASE_URL.",
                exception.getMessage());
    }

    @Test
    void requiresTwoDistinctApprovedPageSizes() {
        final Map<String, String> environment = new HashMap<>(validEnvironment());
        environment.put("CONTRACT_PAGE_SIZE_B", "5");

        assertThrows(
                IllegalArgumentException.class, () -> ContractTestConfiguration.from(environment));
    }

    @Test
    void acceptsTwoDistinctNonUnitPageSizesApprovedByTheOwner() {
        final Map<String, String> environment = new HashMap<>(validEnvironment());
        environment.put("CONTRACT_PAGE_SIZE_A", "10");
        environment.put("CONTRACT_PAGE_SIZE_B", "50");

        final ContractTestConfiguration configuration = ContractTestConfiguration.from(environment);

        assertEquals(10, configuration.firstApprovedPageSize());
        assertEquals(50, configuration.secondApprovedPageSize());
    }

    @Test
    void rejectsAContractEndpointWithCredentialsWithoutEchoingIt() {
        final Map<String, String> environment = new HashMap<>(validEnvironment());
        environment.put(
                "CONTRACT_GRAPHQL_URL",
                "https://user:secret@contract-graphql.example.test/graphql");

        final IllegalArgumentException exception =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> ContractTestConfiguration.from(environment));

        assertEquals(
                "Configuração de endpoint inválida: CONTRACT_GRAPHQL_URL.", exception.getMessage());
        assertFalse(exception.getMessage().contains("secret"));
    }

    @Test
    void acceptsTheOptional4924ConfigurationOnlyWhenItIsComplete() {
        final Map<String, String> environment = new HashMap<>(validEnvironment());
        environment.putAll(Contract4924ConfigurationTest.validEnvironment());

        final ContractTestConfiguration configuration = ContractTestConfiguration.from(environment);

        assertTrue(configuration.auxiliary4924().isPresent());
        assertEquals("invoices", configuration.auxiliary4924().orElseThrow().root());
    }

    @Test
    void ignoresAnExplicitlyDisabled4924FlagWhenDecidingWhetherRemoteTestsMayRun() {
        assertFalse(
                ContractTestConfiguration.hasAnyRemoteEnvironmentConfiguration(
                        Map.of("CONTRACT_4924_ENABLED", "false")));
        assertTrue(
                ContractTestConfiguration.hasAnyRemoteEnvironmentConfiguration(
                        Map.of("CONTRACT_4924_ENABLED", "true")));
    }

    private static Map<String, String> validEnvironment() {
        return Map.ofEntries(
                Map.entry(
                        "CONTRACT_DATAEXPORT_BASE_URL", "https://contract-dataexport.example.test"),
                Map.entry("CONTRACT_DATAEXPORT_TOKEN", "contract-dataexport-token"),
                Map.entry("CONTRACT_GRAPHQL_URL", "https://contract-graphql.example.test/graphql"),
                Map.entry("CONTRACT_GRAPHQL_TOKEN", "contract-graphql-token"),
                Map.entry("CONTRACT_SOURCE_TIMEZONE", "America/Sao_Paulo"),
                Map.entry("CONTRACT_6908_MAX_CALLS", "3"),
                Map.entry("CONTRACT_6389_MAX_CALLS", "4"),
                Map.entry("CONTRACT_DATAEXPORT_TRANSPORT", "GET_WITH_BODY"),
                Map.entry("CONTRACT_PAGE_SIZE_A", "5"),
                Map.entry("CONTRACT_PAGE_SIZE_B", "25"),
                Map.entry("CONTRACT_6908_POPULATED_WINDOW", "2026-08-13..2026-08-14"),
                Map.entry("CONTRACT_6908_EMPTY_WINDOW", "2026-08-15..2026-08-15"),
                Map.entry("CONTRACT_6908_LATE_CHANGE_BUSINESS_WINDOW", "2026-08-13..2026-08-13"),
                Map.entry(
                        "CONTRACT_6908_LATE_CHANGE_UPDATED_AT_WINDOW",
                        "2026-08-20T00:00:00Z..2026-08-20T23:59:59Z"),
                Map.entry("CONTRACT_6389_POPULATED_WINDOW", "2026-08-13..2026-08-14"),
                Map.entry("CONTRACT_6389_EMPTY_WINDOW", "2026-08-15..2026-08-15"),
                Map.entry("CONTRACT_6389_LATE_CHANGE_BUSINESS_WINDOW", "2026-08-13..2026-08-13"),
                Map.entry(
                        "CONTRACT_6389_LATE_CHANGE_UPDATED_AT_WINDOW",
                        "2026-08-20T00:00:00Z..2026-08-20T23:59:59Z"));
    }
}
