package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Exact business values first; control IDs/monitoring/absence have separate integration proofs. */
class QualificationValueOracleIT {
    @Test
    @Timeout(240)
    void independentBusinessValuesAndPhysicalColumnMetadataAcrossSeventeenPositiveOutputs()
            throws Exception {
        final var file =
                Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");
        final var oracle = QualificationOracles.read(file);
        final var specification = QualificationJson.read(file, 524288);
        final var differences = new ArrayList<String>();
        final var reported = new java.util.HashSet<String>();
        final var checked = new AtomicInteger();
        final Instant before = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var cycle =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            assertTrue(cycle.status().complete());
            final var context =
                    new QualificationOracles.Context(
                            run.id(),
                            2,
                            1,
                            false,
                            0,
                            null,
                            before,
                            Instant.now(),
                            new NonBusinessEvidence());
            final var reader = new JdbcAnalyticQueries(session);
            for (final var contract : AnalyticSqlContract.values()) {
                if (contract == AnalyticSqlContract.SQL_04
                        || contract == AnalyticSqlContract.SQL_10) {
                    continue;
                }
                final JsonNode rules = find(specification, contract);
                final var expected = oracle.expected(contract, context);
                final var ordinal = new AtomicLong();
                final var receipt =
                        reader.read(
                                run.id(),
                                contract,
                                2,
                                4096,
                                CancellationToken.none(),
                                metadata ->
                                        assertEquals(
                                                AnalyticSqlCatalog.columns(contract), metadata),
                                row -> {
                                    final long index = ordinal.getAndIncrement();
                                    if (index >= expected.count()) {
                                        return;
                                    }
                                    final var values = expected.at(index);
                                    for (int column = 0; column < contract.columns(); column++) {
                                        final var rule = rules.get(column);
                                        if (List.of("OBSERVED_TIME", "STRUCTURED_LINEAGE")
                                                        .contains(rule.path("rule").asText())
                                                || rule.path("value")
                                                        .asText()
                                                        .equals("LOCATION_HASH")) {
                                            continue;
                                        }
                                        checked.incrementAndGet();
                                        final var wanted = values.get(column);
                                        final var actual = row.values().get(column);
                                        if (!wanted.equals(actual)
                                                && differences.size() < 32
                                                && reported.add(contract.id() + ":" + column)) {
                                            differences.add(
                                                    contract.id()
                                                            + " row="
                                                            + index
                                                            + " column="
                                                            + (column + 1)
                                                            + " expected="
                                                            + bounded(wanted)
                                                            + " actual="
                                                            + bounded(actual));
                                        }
                                    }
                                });
                if (receipt.rows() != expected.count() && differences.size() < 32) {
                    differences.add(
                            contract.id()
                                    + " rows expected="
                                    + expected.count()
                                    + " actual="
                                    + receipt.rows());
                }
            }
            assertTrue(checked.get() > 1000);
            assertEquals(List.of(), differences);
        }
    }

    private static JsonNode find(final JsonNode specification, final AnalyticSqlContract contract) {
        for (final var output : specification.path("contracts")) {
            if (output.path("id").asText().equals(contract.id())) {
                return output.path("columns");
            }
        }
        throw new IllegalArgumentException("MISSING_ORACLE");
    }

    private static String bounded(final AnalyticSqlValue value) {
        final var text = value.toString();
        return text.substring(0, Math.min(128, text.length()));
    }

    private static final class NonBusinessEvidence implements QualificationOracles.Evidence {
        @Override
        public List<AnalyticSqlValue> monitor(final long row) {
            throw new IllegalArgumentException("MONITOR_NOT_THIS_SCOPE");
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
