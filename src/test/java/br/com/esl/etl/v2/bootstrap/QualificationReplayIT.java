package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationLineageEvidence;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationOracles;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationReplayIT {
    @Test
    @Timeout(240)
    void everyModeComparesNineteenOutputsAndReplayKeepsTheFactLineageOfItsEffectiveObservation()
            throws Exception {
        final var file =
                Path.of("src/main/resources/qualification-laboratory/outputs.synthetic.json");
        final var binding =
                new QualificationComparator.Binding(
                        QualificationJson.sha256(Path.of("pom.xml")),
                        QualificationJson.sha256(
                                Path.of(
                                        "src/main/resources/analytic-laboratory/fat.synthetic.json")),
                        QualificationJson.sha256(file),
                        "INDEPENDENT_SYNTHETIC_RULES_V1");
        final var verifier =
                new QualificationScenarioVerifier(
                        QualificationOracles.read(file), binding, binding);
        final var started = Instant.now();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var cycles = new ArrayList<AnalyticScenarioRuntime.Cycle>();
            AnalyticScenarioRuntime.Cycle previous = null;
            int revision = 0;
            for (final var mode :
                    List.of(
                            ExecutionMode.BOOTSTRAP,
                            ExecutionMode.INCREMENTAL,
                            ExecutionMode.BACKFILL,
                            ExecutionMode.REPLAY)) {
                final var cycle =
                        runtime.capture(
                                run,
                                mode,
                                ++revision,
                                mode == ExecutionMode.BACKFILL || mode == ExecutionMode.REPLAY,
                                previous,
                                CancellationToken.none());
                cycles.add(cycle);
                assertTrue(cycle.status().complete());
                final var result =
                        verifier.verify(
                                session,
                                run,
                                cycles,
                                started,
                                List.of(AnalyticSqlContract.values()),
                                CancellationToken.none());
                final var coordinates = new ArrayList<String>();
                if (result.selected().state() != QualificationGate.State.PASS_LOCAL) {
                    final var lineage =
                            new QualificationLineageEvidence(
                                    session,
                                    run.id(),
                                    run.expansion(),
                                    2,
                                    cycle.sourceRevision(),
                                    cycle.correction(),
                                    Math.toIntExact(
                                            cycles.stream()
                                                    .filter(
                                                            value ->
                                                                    value.sourceRevision()
                                                                                    == cycle
                                                                                            .sourceRevision()
                                                                            && value.correction()
                                                                                    == cycle
                                                                                            .correction())
                                                    .count()));
                    final int[] root = {0};
                    new JdbcAnalyticQueries(session)
                            .read(
                                    run.id(),
                                    AnalyticSqlContract.values()[7],
                                    2,
                                    2,
                                    CancellationToken.none(),
                                    row -> {
                                        try {
                                            final var value =
                                                    (AnalyticSqlValue.Text) row.values().get(103);
                                            final var json =
                                                    QualificationJson.parse(
                                                            value.value()
                                                                    .getBytes(
                                                                            java.nio.charset
                                                                                    .StandardCharsets
                                                                                    .UTF_8),
                                                            131072);
                                            coordinates.add(
                                                    lineage.manifestDifference(++root[0], json));
                                        } catch (final java.io.IOException invalid) {
                                            throw new IllegalArgumentException(
                                                    "QUAL_REPLAY_DIAGNOSTIC_PARSE", invalid);
                                        }
                                    });
                }
                assertEquals(
                        QualificationGate.State.PASS_LOCAL,
                        result.selected().state(),
                        () ->
                                mode
                                        + ":"
                                        + coordinates
                                        + result.outputs().stream()
                                                .filter(value -> value.differences() > 0)
                                                .toList());
                assertEquals(
                        mode == ExecutionMode.BOOTSTRAP
                                ? AnalyticScenarioRuntime.START
                                : AnalyticScenarioRuntime.START.plusDays(1),
                        cycle.status().nextDate());
                previous = cycle;
            }
            try (var connection = session.getConnection();
                    var sql =
                            connection.prepareStatement(
                                    "SELECT COUNT_BIG(*) FROM mart.analytic_manifest_current f"
                                            + " JOIN mart.analytic_manifest_observation o ON o.observation_id=f.observation_id"
                                            + " JOIN core.analytic_manifest_current c ON c.run_id=f.run_id AND c.source_key=f.source_key"
                                            + " WHERE f.run_id=? AND c.snapshot_id=o.snapshot_id AND o.action='NOOP'")) {
                sql.setQueryTimeout(15);
                sql.setString(1, run.id().toString());
                try (var rows = sql.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getLong(1));
                }
            }
        }
    }
}
