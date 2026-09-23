package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QualificationOracleTest {
    private static final Path FILE =
            Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");

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
