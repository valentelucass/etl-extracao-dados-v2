package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QualificationOracleTest {
    private static final Path FILE =
            Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");

    @TempDir Path folder;

    @Test
    void declaredMonitorProjectionBindsSelectorsAndPriorSql10EventsWithoutJdbc() throws Exception {
        IntegralArtifactFixtures.write(folder, false, 2);
        final var declaration = QualificationJson.read(folder.resolve("oracle.json"), 16384);
        final var oracle =
                new DeclaredSqlOracles(
                        PinnedLocalJson.reference(folder, declaration.path("outputs"), 524288),
                        CancellationToken.none());
        final var manifest =
                QualificationJson.read(folder.resolve("outputs/manifest.json"), 524288);
        final var ids = new HashMap<String, UUID>();
        for (final var row : manifest.path("monitor")) {
            final String selector = row.path("selector").asText();
            ids.put(
                    selector,
                    UUID.nameUUIDFromBytes(
                            selector.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
        final var first = oracle.expectedMonitorRows(Map.copyOf(ids), 0);
        final var replay = oracle.expectedMonitorRows(Map.copyOf(ids), 1);
        assertEquals(20, first.size());
        assertEquals(first.size(), replay.size());
        for (int index = 0; index < first.size(); index++) {
            final var row = manifest.path("monitor").get(index);
            final var expected = first.get(index);
            assertEquals(ids.get(row.path("selector").asText()), expected.id());
            assertEquals(row.path("rows").asLong(), expected.rows());
            assertEquals(row.path("entity").asText(), expected.entity());
            assertEquals(row.path("family").asText(), expected.family());
            assertEquals(row.path("state").asText(), expected.state());
            assertEquals(
                    expected.rows() + (row.path("selector").asText().equals("SCENARIO") ? 20 : 0),
                    replay.get(index).rows());
        }
    }

    @Test
    void declaredSqlOracleScopesRunKeysAndBoundsObservedTimeWithoutJdbc() throws Exception {
        IntegralArtifactFixtures.write(folder, false, 2);
        final var declaration = QualificationJson.read(folder.resolve("oracle.json"), 16384);
        final var oracle =
                new DeclaredSqlOracles(
                        PinnedLocalJson.reference(folder, declaration.path("outputs"), 524288),
                        CancellationToken.none());
        final var run = UUID.fromString("00000000-0000-4000-8000-000000000001");
        final var otherRun = UUID.fromString("00000000-0000-4000-8000-000000000002");
        final var started = Instant.parse("2037-08-11T12:00:00Z");
        final var finished = started.plusSeconds(2);
        final var invoice =
                oracle.expected(AnalyticSqlContract.SQL_01, run, null, null, started, finished);
        assertEquals(4, invoice.count());
        assertEquals(AnalyticSqlContract.SQL_01.columns(), invoice.at(0).size());
        final var firstKey = (AnalyticSqlValue.Text) invoice.at(0).get(1);
        final var secondKey =
                (AnalyticSqlValue.Text)
                        oracle.expected(
                                        AnalyticSqlContract.SQL_01,
                                        otherRun,
                                        null,
                                        null,
                                        started,
                                        finished)
                                .at(0)
                                .get(1);
        assertTrue(firstKey.value().startsWith(run.toString().toUpperCase() + "/"));
        assertEquals(firstKey.value().substring(36), secondKey.value().substring(36));
        assertFalse(firstKey.equals(secondKey));
        for (final long ordinal : new long[] {-1, invoice.count()}) {
            assertEquals(
                    "INTEGRAL_SQL_ORACLE_ORDINAL",
                    assertThrows(IllegalArgumentException.class, () -> invoice.at(ordinal))
                            .getMessage());
        }

        final var collection =
                oracle.expected(AnalyticSqlContract.SQL_03, run, null, null, started, finished);
        final var observedColumn = AnalyticSqlCatalog.columns(AnalyticSqlContract.SQL_03).get(40);
        final var wanted = collection.at(0).get(40);
        assertTrue(
                collection.equivalent(
                        0,
                        observedColumn,
                        wanted,
                        new AnalyticSqlValue.CivilDateTime(
                                LocalDateTime.ofInstant(started, ZoneOffset.UTC))));
        assertFalse(
                collection.equivalent(
                        0,
                        observedColumn,
                        wanted,
                        new AnalyticSqlValue.CivilDateTime(
                                LocalDateTime.ofInstant(started.minusSeconds(1), ZoneOffset.UTC))));
        assertTrue(
                collection.equivalent(
                        0,
                        observedColumn,
                        wanted,
                        new AnalyticSqlValue.CivilDateTime(
                                LocalDateTime.ofInstant(finished.plusMillis(1), ZoneOffset.UTC))));
        assertFalse(
                collection.equivalent(
                        0,
                        observedColumn,
                        wanted,
                        new AnalyticSqlValue.CivilDateTime(
                                LocalDateTime.ofInstant(finished.plusMillis(2), ZoneOffset.UTC))));
        assertFalse(
                collection.equivalent(
                        0, observedColumn, wanted, new AnalyticSqlValue.Text("invalid")));
    }

    @Test
    void everyDeclaredTupleCanBeReadAtItsBoundedOrdinalWithoutJdbc() throws Exception {
        IntegralArtifactFixtures.write(folder, true, 3);
        final var declaration = QualificationJson.read(folder.resolve("oracle.json"), 16384);
        final var oracle =
                new DeclaredSqlOracles(
                        PinnedLocalJson.reference(folder, declaration.path("outputs"), 524288),
                        CancellationToken.none());
        final var run = UUID.fromString("00000000-0000-4000-8000-000000000003");
        final var started = Instant.parse("2037-08-11T12:00:00Z");
        for (final var contract : AnalyticSqlContract.values()) {
            if (contract == AnalyticSqlContract.SQL_10) {
                continue;
            }
            final var expected = oracle.expected(contract, run, null, null, started, started);
            for (long ordinal = 0; ordinal < expected.count(); ordinal++) {
                assertEquals(contract.columns(), expected.at(ordinal).size(), contract.name());
            }
        }
    }

    @Test
    void variantMustDeclareBothTypedAlternativesBeforeAnyComparison() throws Exception {
        final var missing = QualificationJson.read(FILE, 524288);
        ((ObjectNode) missing.path("contracts").get(1).path("columns").get(17).path("value"))
                .remove("VALUES_AND_NULLS");
        assertThrows(IllegalArgumentException.class, () -> QualificationOracles.parse(missing));
        final var object = QualificationJson.read(FILE, 524288);
        ((ObjectNode) object.path("contracts").get(1).path("columns").get(17).path("value"))
                .putObject("VALUES_AND_NULLS");
        assertThrows(IllegalArgumentException.class, () -> QualificationOracles.parse(object));
    }

    @Test
    void perCellOriginMustResolveADeclaredFixtureFieldOrAnIndependentRule() throws Exception {
        for (final var origin :
                List.of(
                        "UNKNOWN_ORIGIN",
                        "fixture:analytic-laboratory/fat#/missing_field",
                        "fixture:../../unlisted#/amount",
                        "fixture:analytic-laboratory/unknown#/amount")) {
            final var node = QualificationJson.read(FILE, 524288);
            ((ObjectNode) node.path("contracts").get(0).path("columns").get(0))
                    .put("origin", origin);
            assertThrows(IllegalArgumentException.class, () -> QualificationOracles.parse(node));
        }
    }

    @Test
    void allFrozenColumnsHaveConsumableTypedIndependentRules() throws Exception {
        final var oracle = QualificationOracles.read(FILE);
        final var context =
                new QualificationOracles.Context(
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        2,
                        1,
                        false,
                        0,
                        null,
                        Instant.parse("2026-09-13T14:00:00Z"),
                        Instant.parse("2026-09-13T14:00:01Z"),
                        new TechnicalEvidence());
        int columns = 0;
        for (final var contract : AnalyticSqlContract.values()) {
            final var rows = oracle.expected(contract, context);
            if (contract != AnalyticSqlContract.SQL_04 && contract != AnalyticSqlContract.SQL_10) {
                assertEquals(contract.columns(), rows.at(0).size());
            }
            columns += contract.columns();
        }
        assertEquals(673, columns);
        assertEquals(0, oracle.expected(AnalyticSqlContract.SQL_04, context).count());
        assertEquals(4, oracle.expected(AnalyticSqlContract.SQL_06, context).count());
        assertEquals(
                new AnalyticSqlValue.Decimal(new java.math.BigDecimal("40.00000000")),
                oracle.expected(AnalyticSqlContract.SQL_06, context).at(0).get(15));
        assertEquals(
                new AnalyticSqlValue.Decimal(new java.math.BigDecimal("60.00000000")),
                oracle.expected(AnalyticSqlContract.SQL_06, context).at(1).get(15));
    }

    @Test
    void verifierFaultsSuppressOnlyTheAffectedOutputsBeforeJdbc() throws Exception {
        final var oracle = QualificationOracles.read(FILE);
        final String pin = QualificationJson.sha256(FILE);
        final var binding =
                new QualificationComparator.Binding(
                        pin, pin, pin, "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var verifier = new QualificationScenarioVerifier(oracle, binding, binding);
        final var context =
                new QualificationOracles.Context(
                        UUID.fromString("00000000-0000-4000-8000-000000000001"),
                        2,
                        1,
                        false,
                        0,
                        null,
                        Instant.parse("2026-09-13T14:00:00Z"),
                        Instant.parse("2026-09-13T14:00:01Z"),
                        new TechnicalEvidence());
        final var normal = AnalyticScenarioRuntime.Fault.NONE;
        final var raster = AnalyticScenarioRuntime.Fault.RASTER_INCOMPLETE;
        final var fleet = AnalyticScenarioRuntime.Fault.MANIFEST_FLEET_MISSING;
        assertEquals(
                oracle.expected(AnalyticSqlContract.SQL_13, context).count(),
                verifier.expected(normal, AnalyticSqlContract.SQL_13, context).count());
        assertEquals(0, verifier.expected(raster, AnalyticSqlContract.SQL_13, context).count());
        assertEquals(
                "QUAL_BLOCKED_OUTPUT_HAS_NO_EXPECTED_ROW",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        verifier.expected(
                                                        raster, AnalyticSqlContract.SQL_13, context)
                                                .at(0))
                        .getMessage());
        assertEquals(0, verifier.expected(fleet, AnalyticSqlContract.SQL_08, context).count());
        assertEquals(0, verifier.expected(fleet, AnalyticSqlContract.SQL_09, context).count());
        assertEquals(3, verifier.expected(fleet, AnalyticSqlContract.SQL_16, context).count());
        assertEquals(
                "QUAL_BLOCKED_OUTPUT_HAS_NO_EXPECTED_ROW",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        verifier.expected(
                                                        fleet, AnalyticSqlContract.SQL_16, context)
                                                .at(3))
                        .getMessage());
        assertEquals(
                oracle.expected(AnalyticSqlContract.SQL_07, context).count(),
                verifier.expected(fleet, AnalyticSqlContract.SQL_07, context).count());
    }

    @Test
    void changedContractMissingOutputAndOriginAreRefused() throws Exception {
        final var original = QualificationJson.read(FILE, 524288);
        final var wrongOrigin = original.deepCopy();
        ((ObjectNode) wrongOrigin).put("origin", "QUERIED_OUTPUT_GOLDEN");
        assertThrows(IllegalArgumentException.class, () -> QualificationOracles.parse(wrongOrigin));
        final var wrongColumn = original.deepCopy();
        ((ObjectNode) wrongColumn.path("contracts").get(0).path("columns").get(0))
                .put("name", "wrong");
        assertThrows(IllegalArgumentException.class, () -> QualificationOracles.parse(wrongColumn));
        final var missing = original.deepCopy();
        ((com.fasterxml.jackson.databind.node.ArrayNode) missing.path("contracts")).remove(1);
        assertThrows(IllegalArgumentException.class, () -> QualificationOracles.parse(missing));
    }

    private static final class TechnicalEvidence implements QualificationOracles.Evidence {
        @Override
        public List<AnalyticSqlValue> monitor(final long row) {
            throw new IllegalArgumentException("NO_CAPTURE");
        }

        @Override
        public int monitorCount() {
            return 0;
        }

        @Override
        public boolean lineage(
                final String entity, final int root, final int component, final JsonNode actual) {
            return false;
        }

        @Override
        public String locationHash(final int root) {
            return "0".repeat(64);
        }
    }
}
