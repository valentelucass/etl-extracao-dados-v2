package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQuoteTariffs;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationTemporalPlansIT {
    @Test
    @Timeout(120)
    void actualPlansCoverTheNewBoundedVerificationQueriesOnCapturedData() throws Exception {
        final var date = AnalyticLaboratoryFreightOperationalIT.DATE;
        final var zone = ZoneId.of("America/Sao_Paulo");
        final var start = date.atStartOfDay(zone).toInstant();
        final var end = date.plusDays(1).atStartOfDay(zone).toInstant();
        final var window =
                LaboratoryCaptureWindow.planned(
                        new RuntimeTemporalPlanner.Window(
                                start, end, start, end, end.plusSeconds(1800)),
                        zone);
        final var output = Path.of("target", "qualification-temporal-plans");
        Files.createDirectory(output);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 5000);
            final var f = AnalyticLaboratoryCollectionSupplementIT.start(session);
            final var tariff =
                    new JdbcAnalyticQuoteTariffs(session)
                            .importPackaged(f.run(), 1, date, date.plusDays(3));
            final var execution = UUID.randomUUID();
            final var result =
                    new LocalAnalyticQuotesRuntime(session, Clock.systemUTC())
                            .capture(
                                    f.run(),
                                    execution,
                                    date,
                                    ExecutionMode.BOOTSTRAP,
                                    null,
                                    1,
                                    tariff.release(),
                                    2,
                                    QualificationTemporalInputs.quotes(
                                            date,
                                            window.dates(),
                                            false,
                                            false,
                                            new AtomicInteger()),
                                    CancellationToken.none(),
                                    window);
            assertEquals(1, result.roots());
            AnalyticLaboratoryScaleIT.actualPlans(
                    session,
                    execution,
                    output,
                    "temporal",
                    List.of(
                            "SELECT e.current_state,p.partition_start_utc,p.partition_end_exclusive_utc "
                                    + "FROM ctl.execution_attempt e JOIN ctl.execution_partition p "
                                    + "ON p.partition_id=e.partition_id "
                                    + "WHERE e.execution_id=?",
                            "SELECT MIN(freshness_business_date),MAX(freshness_business_date),COUNT_BIG(*) "
                                    + "FROM stg.cotacao_record WHERE execution_id=?",
                            "SELECT COUNT_BIG(*),SUM(physical_rows),SUM(CONVERT(int,terminal_empty_page)),"
                                    + "MIN(requested_page_size),MAX(requested_page_size) "
                                    + "FROM ctl.execution_page_audit WHERE execution_id=?"));
            try (var plans = Files.list(output)) {
                final var files =
                        plans.filter(path -> path.toString().endsWith(".sqlplan")).toList();
                assertEquals(
                        3, files.size(), "Three actual plans required; no grants are requested");
                for (final var file : files) {
                    final var xml = Files.readString(file, StandardCharsets.UTF_8);
                    assertFalse(
                            xml.contains("<SpillToTempDb")
                                    || xml.contains("<HashSpillDetails")
                                    || xml.contains("<SortSpillDetails"));
                }
            }
        }
    }
}
