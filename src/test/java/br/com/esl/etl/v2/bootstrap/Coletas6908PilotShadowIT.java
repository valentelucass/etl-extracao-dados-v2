package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opt-in fisica: fonte estritamente sintetica, SQL apenas no shadow local com rollback. */
class Coletas6908PilotShadowIT {
    @TempDir Path directory;

    @Test
    void syntheticTraversalStagesPromotesComparesAndRollsBack() throws Exception {
        Assumptions.assumeTrue(
                Boolean.getBoolean("shadow.local.integration.enabled")
                        && Boolean.getBoolean("shadow.local.integration.profile.active"));
        final String target = System.getenv("V2_SHADOW_JDBC_URL");
        final Clock clock = Clock.systemUTC();
        final LocalDate day =
                LocalDate.now(clock.withZone(ZoneId.of("America/Sao_Paulo"))).minusDays(1);
        final RuntimeConfiguration configuration = configuration(target, clock);
        final RuntimeOperationalRequest request = request(configuration, day);
        final Coletas6908PilotPlan plan =
                new Coletas6908PilotPlan(
                        "synthetic-source",
                        "synthetic-tenant",
                        day,
                        3,
                        2,
                        100,
                        10 * 1024 * 1024,
                        Duration.ofSeconds(60));
        final var fixture = new RuntimeBootstrapTestFixture(clock.instant());
        final long[] before = counts();
        final var receipt =
                Coletas6908PilotRunner.runInLocalShadow(
                        plan,
                        configuration,
                        request,
                        (config, frozen, cancellation, observation) ->
                                fixtureForDay(fixture, observation, day));
        final long[] after = counts();
        assertEquals(2, fixture.fetches);
        assertEquals("SIMULATED_PROMOTION_ROLLED_BACK", receipt.status());
        assertEquals("PARIDADE_DA_TRAVESSIA_LIMITADA_OBSERVADA", receipt.parity());
        assertEquals(2, receipt.pagesFetched());
        assertEquals(5, receipt.physicalRows());
        assertEquals(2, receipt.expectedRoots());
        assertEquals(5, receipt.observedPhysicalRows());
        assertEquals(0, receipt.presenceComparedCells());
        assertFalse(receipt.windowCompleteness());
        assertFalse(receipt.childCompleteness());
        assertArrayEquals(before, after);
    }

    static DataExportHttpGatewayBundle fixtureForDay(
            final RuntimeBootstrapTestFixture fixture,
            final DataExportContractObservationConfiguration observation,
            final LocalDate day) {
        final var original = fixture.gateways(observation);
        return RuntimeBootstrapTestFixture.boundGateways(
                request -> {
                    if (!request.businessDateWindow().startInclusive().equals(day)
                            || !request.businessDateWindow().endInclusive().equals(day)) {
                        throw new IllegalStateException("COL_PILOT_FIXTURE_DATE_SCOPE");
                    }
                    final var response = original.dataGateway().fetch(request);
                    if (request.page() != 1) {
                        return response;
                    }
                    return RuntimeBootstrapTestFixture.observedPage(
                            normalizeFixtureDates(response.records(), day),
                            response.contractObservation().orElseThrow(),
                            response.observationLimits().orElseThrow(),
                            observation.responsePathBoundary());
                },
                original.templateInfoGateway(),
                observation);
    }

    static List<JsonNode> normalizeFixtureDates(final List<JsonNode> records, final LocalDate day) {
        if (records.size() != 5) {
            throw new IllegalStateException("COL_PILOT_FIXTURE_ROWS");
        }
        final ZoneId zone = ZoneId.of("America/Sao_Paulo");
        final String businessDate = day.toString();
        final Instant createdAt = day.atStartOfDay(zone).toInstant();
        final Instant updatedAt = day.atTime(12, 0).atZone(zone).toInstant();
        final List<JsonNode> normalized = new ArrayList<>(5);
        for (final JsonNode record : records) {
            if (!(record instanceof ObjectNode row) || row.has("status_updated_at")) {
                throw new IllegalStateException("COL_PILOT_FIXTURE_DATE_SHAPE");
            }
            for (final String name :
                    List.of(
                            "request_date",
                            "finish_date",
                            "service_date",
                            "created_at",
                            "updated_at")) {
                if (!row.path(name).isTextual()) {
                    throw new IllegalStateException("COL_PILOT_FIXTURE_DATE_SHAPE");
                }
            }
            final ObjectNode copy = row.deepCopy();
            copy.put("request_date", businessDate);
            copy.put("finish_date", businessDate);
            copy.put("service_date", businessDate);
            copy.put("created_at", createdAt.toString());
            copy.put("updated_at", updatedAt.toString());
            assertEquals(businessDate, copy.path("request_date").textValue());
            assertEquals(businessDate, copy.path("finish_date").textValue());
            assertEquals(businessDate, copy.path("service_date").textValue());
            assertEquals(createdAt.toString(), copy.path("created_at").textValue());
            assertEquals(updatedAt.toString(), copy.path("updated_at").textValue());
            assertFalse(copy.has("status_updated_at"));
            normalized.add(copy);
        }
        return List.copyOf(normalized);
    }

    private RuntimeConfiguration configuration(final String target, final Clock clock)
            throws Exception {
        String text = Files.readString(Path.of("config/application.example.properties"));
        text =
                text.replace("dataexport.enabled=false", "dataexport.enabled=true")
                        .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true")
                        .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                        .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1")
                        .replace("<host-autorizado>", "source.example.test")
                        .replace("<identificador-estavel-nao-secreto>", "synthetic-source")
                        .replace("<escopo-nao-secreto>", "synthetic-tenant")
                        .replace(
                                "dataexport.retry.max-attempts=3",
                                "dataexport.retry.max-attempts=1")
                        .replace(
                                "jdbc:sqlserver://127.0.0.1:<porta-local>;"
                                        + "databaseName=ETL_SISTEMA_V2_SHADOW;"
                                        + "integratedSecurity=true;encrypt=true;"
                                        + "trustServerCertificate=true",
                                target);
        final Path path = directory.resolve("pilot-runtime.properties");
        Files.writeString(path, text);
        return new RuntimeConfigurationFactory().load(path, new Properties(), Map.of(), clock);
    }

    private static RuntimeOperationalRequest request(
            final RuntimeConfiguration configuration, final LocalDate day) throws Exception {
        final ZoneId zone = ZoneId.of("America/Sao_Paulo");
        final var document =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("invocationId", UUID.randomUUID().toString())
                        .put("executionId", UUID.randomUUID().toString())
                        .put("cycleId", UUID.randomUUID().toString())
                        .put("template", "COLETAS")
                        .put("mode", "BACKFILL")
                        .put("start", day.atStartOfDay(zone).toInstant().toString())
                        .put(
                                "endExclusive",
                                day.plusDays(1).atStartOfDay(zone).toInstant().toString())
                        .put("replayOf", "")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("businessStart", day.toString())
                        .put("businessEnd", day.toString())
                        .put("leaseSeconds", "60")
                        .put("pageSize", "3")
                        .put("maximumPages", "2")
                        .put("maximumRows", "100")
                        .put("maximumDistinctRoots", "3")
                        .put("qualityVersion", "synthetic-quality-1")
                        .put("qualityFingerprint", "b".repeat(64))
                        .put("compatibilityVersion", "strict-1");
        return RuntimeOperationalRequest.fromDocument(configuration, document);
    }

    private static long[] counts() throws SQLException {
        final String[] tables = {
            "ctl.execution_audit",
            "ctl.execution_page_audit",
            "stg.execution_record",
            "stg.coleta_record",
            "core.coleta"
        };
        final long[] values = new long[tables.length];
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            for (int index = 0; index < tables.length; index++) {
                try (var connection = session.getConnection();
                        var statement = connection.createStatement()) {
                    statement.setQueryTimeout(10);
                    try (var rows =
                            statement.executeQuery("SELECT COUNT_BIG(*) FROM " + tables[index])) {
                        if (!rows.next()) {
                            throw new SQLException("COL_PILOT_COUNT_UNOBSERVABLE");
                        }
                        values[index] = rows.getLong(1);
                        if (rows.next()) {
                            throw new SQLException("COL_PILOT_COUNT_UNOBSERVABLE");
                        }
                    }
                }
            }
        }
        return values;
    }
}
