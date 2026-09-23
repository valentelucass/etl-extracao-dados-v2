package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.mapping.MapperCharacterization;
import br.com.esl.etl.v2.contratos.mapping.MapperProjection;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationWireOracle;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Existing V2-012 parser/mapper harness now consumes actual metadata returned by the SQL scenario.
 */
class QualificationCharacterizationIT {
    @Test
    @Timeout(240)
    void existingBoundedHarnessConsumesJdbcEvidenceAndKeepsExternalParityUnaccepted()
            throws Exception {
        final var independent = new QualificationWireOracle();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            assertTrue(
                    runtime.capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    1,
                                    false,
                                    null,
                                    CancellationToken.none())
                            .status()
                            .complete());
            int scopes = 0;
            for (final var contract :
                    List.of(
                            AnalyticSqlContract.SQL_02,
                            AnalyticSqlContract.SQL_05,
                            AnalyticSqlContract.SQL_07)) {
                final String entity =
                        contract == AnalyticSqlContract.SQL_02
                                ? "FRE"
                                : contract == AnalyticSqlContract.SQL_05 ? "COT" : "LOC";
                final var mapper =
                        entity.equals("FRE")
                                ? MapperProjection.Entity.FRETES
                                : entity.equals("COT")
                                        ? MapperProjection.Entity.COTACOES
                                        : MapperProjection.Entity.LOCALIZACAO_CARGAS_PIPELINE;
                final int metadata =
                        AnalyticSqlCatalog.columns(contract).stream()
                                        .filter(c -> c.name().equals("Metadata"))
                                        .findFirst()
                                        .orElseThrow()
                                        .ordinal()
                                - 1;
                final var ordinal = new AtomicInteger();
                new JdbcAnalyticQueries(session)
                        .read(
                                run.id(),
                                contract,
                                2,
                                4096,
                                CancellationToken.none(),
                                row -> {
                                    final int root = ordinal.incrementAndGet();
                                    try {
                                        final var actual =
                                                QualificationJson.parse(
                                                        ((AnalyticSqlValue.Text)
                                                                        row.values().get(metadata))
                                                                .value()
                                                                .getBytes(StandardCharsets.UTF_8),
                                                        131072);
                                        final var record =
                                                entity.equals("COT") ? quoteSource(actual) : actual;
                                        final var envelope = JsonNodeFactory.instance.objectNode();
                                        envelope.putArray("data").add(record);
                                        final var wanted =
                                                expectations(
                                                        entity,
                                                        independent.source(
                                                                entity, root, 1, 1, false));
                                        final byte[] bytes =
                                                envelope.toString()
                                                        .getBytes(StandardCharsets.UTF_8);
                                        final var report =
                                                MapperCharacterization.compare(
                                                        mapper,
                                                        1,
                                                        page -> new ByteArrayInputStream(bytes),
                                                        page -> wanted);
                                        assertTrue(report.matches(), report::toString);
                                        assertEquals(
                                                "NOT_EXECUTED",
                                                report.sanitized()
                                                        .path("providerEvidence")
                                                        .asText());
                                        final var mutant = wanted.deepCopy();
                                        ((ObjectNode) mutant.get(0))
                                                .put(
                                                        "/quarantine",
                                                        "INTENTIONAL_WRONG_EXPECTATION");
                                        assertFalse(
                                                MapperCharacterization.compare(
                                                                mapper,
                                                                1,
                                                                page ->
                                                                        new ByteArrayInputStream(
                                                                                bytes),
                                                                page -> mutant)
                                                        .matches());
                                    } catch (final java.io.IOException invalid) {
                                        throw new IllegalArgumentException(
                                                "QUAL_HARNESS_METADATA_INVALID", invalid);
                                    }
                                });
                assertEquals(2, ordinal.get());
                scopes++;
            }
            assertEquals(3, scopes);
        }
    }

    private static JsonNode expectations(final String entity, final ObjectNode input) {
        final var array = JsonNodeFactory.instance.arrayNode();
        final var row = array.addObject().put("/quarantine", "NONE");
        final String key =
                entity.equals("FRE")
                        ? "id"
                        : entity.equals("COT") ? "sequence_code" : "corporation_sequence_number";
        row.put("/" + key + "/typed", "INTEGER:" + input.path(key).asText());
        row.put("/" + key + "/presence", "VALUE");
        row.put("/" + key + "/wireType", "INTEGER");
        if (entity.equals("LOC")) {
            row.put("/taxed_weight/presence", "VALUE")
                    .put("/taxed_weight/wireType", "STRING")
                    .put("/service_type/presence", "ABSENT")
                    .put("/service_type/mappedPresence", "ABSENT")
                    .put("/fit_fln_status/terminal", "false")
                    .put("/invoices_volumes/typed", "3")
                    .put("/service_at/typed", "2036-04-01T03:00:00Z");
        } else if (entity.equals("COT")) {
            row.put("/qoe_uer_name/typed", "SYNTHETIC userName");
        }
        return array;
    }

    private static ObjectNode quoteSource(final JsonNode metadata) throws java.io.IOException {
        final var result = JsonNodeFactory.instance.objectNode();
        final var fields = metadata.fields();
        while (fields.hasNext()) {
            final var field = fields.next();
            final var detail = field.getValue();
            if (!detail.isObject() || !detail.has("presence")) {
                continue;
            }
            if (detail.path("presence").asText().equals("NULL")) {
                result.putNull(field.getKey());
            } else if (detail.path("presence").asText().equals("VALUE")) {
                if (detail.path("wire").asText().equals("STRING")) {
                    result.put(field.getKey(), detail.path("raw").asText());
                } else {
                    result.set(
                            field.getKey(),
                            QualificationJson.parse(
                                    detail.path("raw").asText().getBytes(StandardCharsets.UTF_8),
                                    32768));
                }
            }
        }
        return result;
    }
}
