package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationMonitoringIT {
    @Test
    @Timeout(240)
    void allMonitorFamiliesHaveIndependentlyExpectedRowsStatesAndReceiptBoundTimes()
            throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC());
            final var run = runtime.start(2, 2);
            final var first =
                    runtime.capture(
                            run, ExecutionMode.BOOTSTRAP, 1, false, null, CancellationToken.none());
            assertTrue(first.status().complete());
            final var expected = new QualificationMonitoring(session, run, List.of(first));
            assertEquals(21, expected.count());
            final var ordinal = new AtomicInteger();
            final var differences = new ArrayList<String>();
            new JdbcAnalyticQueries(session)
                    .read(
                            run.id(),
                            AnalyticSqlContract.SQL_10,
                            2,
                            4096,
                            CancellationToken.none(),
                            row -> {
                                final int index = ordinal.getAndIncrement();
                                if (index >= expected.count()) {
                                    differences.add("EXTRA_ROW");
                                    return;
                                }
                                final var wanted = expected.row(index);
                                for (int column = 0; column < 9; column++) {
                                    if (!wanted.get(column).equals(row.values().get(column))
                                            && differences.size() < 24) {
                                        // Technical monitor only; no business payload is printed.
                                        differences.add(
                                                index
                                                        + ":"
                                                        + column
                                                        + ":expected="
                                                        + wanted.get(column)
                                                        + ":actual="
                                                        + row.values().get(column));
                                    }
                                }
                            });
            assertEquals(expected.count(), ordinal.get());
            assertEquals(List.of(), differences);
        }
    }
}
