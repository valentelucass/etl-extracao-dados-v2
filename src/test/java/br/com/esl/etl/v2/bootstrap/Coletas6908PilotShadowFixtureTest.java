package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportColetasContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class Coletas6908PilotShadowFixtureTest {
    private static final LocalDate DAY = LocalDate.parse("2036-03-19");

    @Test
    void deliversTheNormalizedPageAndLeavesTheTerminalPageEmpty() {
        final var release = DataExportColetasContractCatalog.release();
        final var compatibility =
                ContractCompatibilityPolicy.create(
                        "strict-1", release.contractFingerprint(), List.of());
        final var observation =
                DataExportContractObservationConfiguration.forRelease(
                        DataExportTemplate.COLETAS,
                        release,
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, compatibility),
                        new ImmutableFingerprint("fixture-config-v1", "a".repeat(64)));
        final var fixture = new RuntimeBootstrapTestFixture(Instant.parse("2036-03-21T12:00:00Z"));
        final var gateway = Coletas6908PilotShadowIT.fixtureForDay(fixture, observation, DAY);
        final var first =
                new DataExportPageRequest(
                        DataExportTemplate.COLETAS,
                        new BusinessDateRange(DAY, DAY),
                        Optional.empty(),
                        1,
                        3,
                        DataExportTemplate.COLETAS.defaultOrderBy());

        final var page = gateway.dataGateway().fetch(first);
        assertEquals(5, page.recordCount());
        page.forEachRecord(Coletas6908PilotShadowFixtureTest::assertNormalizedRow);
        assertEquals(0, gateway.dataGateway().fetch(first.withPage(2)).recordCount());
        assertEquals(2, fixture.fetches);
    }

    @Test
    void replacesAllFiveFixtureDatesWithoutMutatingTheSharedResource() throws Exception {
        final List<JsonNode> source = fixtureRows();
        assertEquals(5, source.size());
        source.forEach(row -> assertNotEquals(DAY.toString(), row.path("request_date").asText()));

        final List<JsonNode> delivered =
                Coletas6908PilotShadowIT.normalizeFixtureDates(source, DAY);

        assertEquals(5, delivered.size());
        delivered.forEach(Coletas6908PilotShadowFixtureTest::assertNormalizedRow);
        assertEquals(fixtureRows(), source);
    }

    @Test
    void rejectsFixtureDriftBeforeDelivery() throws Exception {
        final List<JsonNode> source = fixtureRows();
        assertThrows(
                IllegalStateException.class,
                () -> Coletas6908PilotShadowIT.normalizeFixtureDates(source.subList(0, 4), DAY));
        for (final String name :
                List.of(
                        "request_date",
                        "finish_date",
                        "service_date",
                        "created_at",
                        "updated_at")) {
            final List<JsonNode> invalid = new ArrayList<>(source);
            final ObjectNode missingDate = ((ObjectNode) invalid.get(0)).deepCopy();
            missingDate.remove(name);
            invalid.set(0, missingDate);
            assertThrows(
                    IllegalStateException.class,
                    () -> Coletas6908PilotShadowIT.normalizeFixtureDates(invalid, DAY));
        }
        final List<JsonNode> invalid = new ArrayList<>(source);
        final ObjectNode inventedStatus = ((ObjectNode) invalid.get(0)).deepCopy();
        inventedStatus.put("status_updated_at", "2036-03-19T12:00:00Z");
        invalid.set(0, inventedStatus);
        assertThrows(
                IllegalStateException.class,
                () -> Coletas6908PilotShadowIT.normalizeFixtureDates(invalid, DAY));
    }

    private static void assertNormalizedRow(final JsonNode row) {
        final ZoneId zone = ZoneId.of("America/Sao_Paulo");
        final Instant dayStart = DAY.atStartOfDay(zone).toInstant();
        final Instant dayEnd = DAY.plusDays(1).atStartOfDay(zone).toInstant();
        final Instant createdAt = Instant.parse(row.path("created_at").asText());
        final Instant updatedAt = Instant.parse(row.path("updated_at").asText());
        for (final String name : List.of("request_date", "finish_date", "service_date")) {
            assertEquals(DAY.toString(), row.path(name).asText());
        }
        assertEquals(dayStart, createdAt);
        assertEquals(DAY.atTime(12, 0).atZone(zone).toInstant(), updatedAt);
        assertTrue(updatedAt.isAfter(createdAt));
        assertTrue(updatedAt.isBefore(dayEnd));
        assertFalse(row.has("status_updated_at"));
        final var staged = new ColetaDataExportRecordMapper().map(1, row);
        assertEquals(ColetaFreshnessOrigin.FINISH_DATE, staged.freshnessOrigin());
        assertEquals(dayStart, staged.freshnessAtUtc());
    }

    private static List<JsonNode> fixtureRows() throws Exception {
        try (var input =
                Coletas6908PilotShadowFixtureTest.class.getResourceAsStream(
                        "/contracts/bloco62/6908-page.synthetic.json")) {
            final JsonNode root =
                    new ObjectMapper().readTree(java.util.Objects.requireNonNull(input));
            final List<JsonNode> rows = new ArrayList<>();
            root.forEach(rows::add);
            return rows;
        }
    }
}
