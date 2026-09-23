package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class IntegralArtifactScenarioIT {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @Timeout(240)
    void explicitElevenInputsPropagateAcrossFiveFactsAndNineteenSqlConsumers(
            final boolean alternate) throws Exception {
        final var input = IntegralArtifactFixtures.write(folder, alternate, alternate ? 24 : 2);
        final var metrics =
                new QualificationMetrics(
                        new br.com.esl.etl.v2.plataforma.qualificacao.QualificationConfiguration(
                                30, 45000, 240, 240, 512, 4096, 100000, 67108864),
                        () -> {});
        final var scenario =
                new LocalArtifactScenario(
                        input, folder.resolve("oracle.json"), CancellationToken.none());
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            final var run = java.util.UUID.randomUUID();
            final QualificationScenarioVerifier.Result result;
            try {
                result =
                        scenario.execute(session, CancellationToken.none(), metrics, run, () -> {});
            } catch (final Exception failure) {
                diagnostics(session, run, alternate, failure.getClass().getSimpleName());
                throw failure;
            }
            if (result.outputs().stream().anyMatch(value -> value.differences() != 0)) {
                diagnostics(session, run, alternate, "ORACLE_DIVERGENCE");
            }
            final var comparisons =
                    IntegralArtifactFixtures.object().put("purpose", "COORDINATES_ONLY");
            final var outcomes = comparisons.putArray("outputs");
            for (final var comparison : result.outputs()) {
                outcomes.addObject().put("result", comparison.toString());
            }
            IntegralArtifactFixtures.save(
                    Path.of("target", "integral-comparisons-" + (alternate ? "b" : "a") + ".json"),
                    comparisons);
            assertEquals(19, result.outputs().size());
            assertEquals(35, result.scopes().size());
            for (final var comparison : result.outputs()) {
                assertTrue(comparison.differences() == 0, comparison::toString);
            }
            assertEquals(QualificationGate.State.PASS_LOCAL, result.selected().state());
            assertEquals(33, result.sweepPreview().size());
            final var measured = metrics.snapshot();
            assertEquals(11, measured.inputs().stream().filter(lane -> lane.pages() > 0).count());
            assertEquals(0, measured.inFlight());
            assertTrue(measured.largestBatch() <= 64);
            IntegralArtifactFixtures.save(
                    Path.of("target", "integral-metrics-" + (alternate ? "b" : "a") + ".json"),
                    ((com.fasterxml.jackson.databind.node.ObjectNode)
                                    new com.fasterxml.jackson.databind.ObjectMapper()
                                            .valueToTree(measured))
                            .put("preparedStatements", session.preparedStatements()));
        }
    }

    private static void diagnostics(
            final ColetaTemporalLaboratorySession session,
            final java.util.UUID run,
            final boolean alternate,
            final String reason)
            throws Exception {
        final var report =
                IntegralArtifactFixtures.object()
                        .put("purpose", "DIAGNOSTIC_ONLY_NEVER_AN_ORACLE")
                        .put("reason", reason);
        final var outputs = report.putArray("outputs");
        try (var connection = session.getConnection();
                var statement =
                        connection.prepareStatement(
                                "SELECT TOP (32) r.source_key,r.disposition,r.source_value,r.revenue_value,r.original_reference_date"
                                        + " FROM mart.expansion_lab_revenue r JOIN ctl.analytic_lab_source_group g"
                                        + " ON g.expansion_run=r.run_id WHERE g.run_id=?")) {
            statement.setQueryTimeout(30);
            statement.setString(1, run.toString());
            final var facts = report.putArray("revenueDiagnostics");
            try (var rows = statement.executeQuery()) {
                while (rows.next()) {
                    final var fact = facts.addObject();
                    for (int i = 1; i <= 5; i++) {
                        fact.put(rows.getMetaData().getColumnLabel(i), rows.getString(i));
                    }
                }
            }
        }
        for (final var contract :
                br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract.values()) {
            final var output = outputs.addObject().put("id", contract.id());
            final var rows = output.putArray("firstTwoRows");
            final int[] count = {0};
            try {
                new br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries(session)
                        .read(
                                run,
                                contract,
                                2,
                                4096,
                                CancellationToken.none(),
                                row -> {
                                    count[0]++;
                                    if (rows.size() < 2) {
                                        final var values = rows.addObject();
                                        final var columns =
                                                br.com.esl.etl.v2.plataforma.analitico
                                                        .AnalyticSqlCatalog.columns(contract);
                                        for (int i = 0; i < columns.size(); i++) {
                                            if (!columns.get(i).name().equals("Metadata")) {
                                                values.put(
                                                        columns.get(i).name(),
                                                        row.values().get(i).toString());
                                            }
                                        }
                                    }
                                });
                output.put("observedCount", count[0]);
            } catch (final java.sql.SQLException failure) {
                output.put("diagnosticSqlState", failure.getSQLState());
            }
        }
        IntegralArtifactFixtures.save(
                Path.of("target", "integral-diagnostic-" + (alternate ? "b" : "a") + ".json"),
                report);
    }
}
