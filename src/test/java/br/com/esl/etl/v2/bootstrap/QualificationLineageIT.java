package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationLineageIT {
    @Test
    @Timeout(240)
    void metadataPreservesSourceWireAndExactCaptureLineageWithoutOutputGoldens() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var cycle =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            assertTrue(cycle.status().complete());
            final var evidence =
                    new QualificationLineageEvidence(
                            session, run.id(), run.expansion(), 2, 1, false);
            final var differences = new ArrayList<String>();
            final var scopes =
                    Map.ofEntries(
                            Map.entry(AnalyticSqlContract.SQL_01, "FAT"),
                            Map.entry(AnalyticSqlContract.SQL_02, "FRE"),
                            Map.entry(AnalyticSqlContract.SQL_03, "COL"),
                            Map.entry(AnalyticSqlContract.SQL_05, "COT"),
                            Map.entry(AnalyticSqlContract.SQL_06, "CAP"),
                            Map.entry(AnalyticSqlContract.SQL_07, "LOC"),
                            Map.entry(AnalyticSqlContract.SQL_08, "MAN"),
                            Map.entry(AnalyticSqlContract.SQL_09, "MAN"),
                            Map.entry(AnalyticSqlContract.SQL_11, "INV"),
                            Map.entry(AnalyticSqlContract.SQL_12, "SIN"));
            for (final var entry : scopes.entrySet()) {
                final var ordinal = new AtomicInteger();
                final var columns =
                        br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog.columns(
                                entry.getKey());
                final int column =
                        columns.stream()
                                        .filter(value -> value.name().equals("Metadata"))
                                        .findFirst()
                                        .orElseThrow()
                                        .ordinal()
                                - 1;
                new JdbcAnalyticQueries(session)
                        .read(
                                run.id(),
                                entry.getKey(),
                                2,
                                4096,
                                CancellationToken.none(),
                                row -> {
                                    final int index = ordinal.getAndIncrement();
                                    final boolean components =
                                            java.util.Set.of("CAP", "FAT", "INV", "SIN")
                                                    .contains(entry.getValue());
                                    final int root = (components ? index / 2 : index) + 1;
                                    final int component = components ? index % 2 + 1 : 1;
                                    try {
                                        final var json =
                                                QualificationJson.parse(
                                                        ((AnalyticSqlValue.Text)
                                                                        row.values().get(column))
                                                                .value()
                                                                .getBytes(StandardCharsets.UTF_8),
                                                        131072);
                                        if (!evidence.compare(
                                                        entry.getValue(), root, component, json)
                                                && differences.size() < 24) {
                                            differences.add(
                                                    entry.getKey().id()
                                                            + ":"
                                                            + root
                                                            + ":"
                                                            + component
                                                            + ":members="
                                                            + json.size());
                                        }
                                    } catch (final java.io.IOException invalid) {
                                        throw new IllegalArgumentException(
                                                "INVALID_METADATA", invalid);
                                    }
                                });
            }
            assertTrue(evidence.retainedTechnicalRecords() <= 22);
            assertEquals(java.util.List.of(), differences);
        }
    }
}
