package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.medicao.ManagedPageGauge;
import br.com.esl.etl.v2.contratos.medicao.MeasurementDiagnostics;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalLaboratoryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Three physical scales and one repeated middle scale; heap is diagnostic, not a plateau claim. */
class RelationalLaboratoryScaleIT {
    @ParameterizedTest
    @ValueSource(ints = {16, 256, 1024, 256})
    @Timeout(240)
    void streamsExpandedRowsWithBoundedPagesAndSqlSetResolution(final int roots) throws Exception {
        final Path output =
                Path.of(
                        "target",
                        "relational-measurements",
                        "scale-" + roots + "-" + UUID.randomUUID());
        Files.createDirectories(output);
        final var mapper = new ObjectMapper();
        mapper.writeValue(
                output.resolve("reservation.json").toFile(),
                Map.of(
                        "rootsPerEntity",
                        roots,
                        "physicalRowsPerEntity",
                        roots * 2,
                        "pageSize",
                        17,
                        "batchCeiling",
                        100,
                        "seconds",
                        240,
                        "heapMiB",
                        512,
                        "rollbackOnly",
                        true,
                        "target",
                        "localhost/ETL_SISTEMA_V2_SHADOW"));
        final long started = System.nanoTime();
        final CancellationToken deadline =
                () ->
                        Thread.currentThread().isInterrupted()
                                || System.nanoTime() - started
                                        >= java.time.Duration.ofSeconds(240).toNanos();
        final long beforeHeap = heap();
        long peak = beforeHeap;
        final var probes = new ArrayList<Map<String, Object>>(3);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = RelationalLaboratoryLocalIntegrationIT.counts(session);
            final UUID run = UUID.randomUUID();
            final LocalDate date = LocalDate.of(2036, 4, 1);
            final var policy =
                    new RelationalLaboratoryPolicy(date, date, 100_000, 4, 3, 60, 2, 0, 17, 1024);
            final var lab = new JdbcRelationalLaboratory(session, Clock.systemUTC());
            lab.start(run, policy, RelationalSyntheticSource.contracts());
            final var runtime =
                    new LocalRelationalRuntime(
                            session, run, policy, Clock.systemUTC(), Clock.systemUTC());
            for (final var template :
                    List.of(
                            DataExportTemplate.MANIFESTOS,
                            DataExportTemplate.COLETAS,
                            DataExportTemplate.FRETES)) {
                final var probe = new Probe();
                final var fixture =
                        RelationalLaboratoryFixtures.source(template, date, 1, roots, 17, true)
                                .observed(probe);
                final var capture =
                        runtime.capture(
                                template, date, ExecutionMode.BOOTSTRAP, null, fixture, deadline);
                final var measured = probe.gauge.counts();
                assertEquals(roots, capture.receipt().considered());
                assertEquals(roots * 2L, measured.records());
                assertEquals((roots * 2 + 16) / 17 + 1, measured.fetchedPages());
                assertEquals(1, measured.maximumInFlightPages());
                assertEquals(0, measured.finalInFlightPages());
                assertEquals(measured.acquisitions(), measured.releases());
                assertEquals(0, measured.finalRetainedPages());
                assertTrue(probe.maximumBatch <= 17);
                assertEquals(1, probe.maximumBatchesInFlight);
                assertEquals(0, probe.batchesInFlight);
                probes.add(
                        Map.of(
                                "entity",
                                template.name(),
                                "managedPages",
                                measured,
                                "maximumBatchRows",
                                probe.maximumBatch,
                                "maximumBatchesInFlight",
                                probe.maximumBatchesInFlight,
                                "maximumPageBytes",
                                fixture.metrics().maximumPageBytes(),
                                "heapSamplePeakBytes",
                                probe.peak));
                peak = Math.max(peak, probe.peak);
            }
            for (int first = 1; first <= roots; first += 50) {
                lab.bindBatch(
                        run,
                        RelationalLaboratoryFixtures.bindingBatch(
                                date, first, Math.min(50, roots - first + 1)),
                        deadline);
            }
            final var first = lab.resolve(run, UUID.randomUUID(), deadline);
            assertEquals(roots * 2L, first.resolved());
            assertEquals(roots * 2L, first.inserted());
            assertEquals(roots * 2L, lab.resolve(run, UUID.randomUUID(), deadline).noop());
            final var status = lab.status(run);
            assertTrue(status.complete());
            assertEquals(roots * 6L, status.physicalRows());
            assertEquals(roots, status.manifestoColetas());
            assertEquals(roots, status.coletaFretes());
            capturePlans(session, run, output);
            session.rollback();
            assertEquals(before, RelationalLaboratoryLocalIntegrationIT.counts(session));
            final long afterHeap = heap();
            mapper.writeValue(
                    output.resolve("result.json").toFile(),
                    Map.of(
                            "scaleRoots",
                            roots,
                            "probes",
                            probes,
                            "diagnostics",
                            new MeasurementDiagnostics(
                                    beforeHeap,
                                    afterHeap,
                                    Math.max(peak, afterHeap),
                                    System.nanoTime() - started),
                            "statusBeforeRollback",
                            status,
                            "residue",
                            0,
                            "heapPlateauProven",
                            false));
        }
    }

    private static void capturePlans(
            final ColetaTemporalLaboratorySession session, final UUID run, final Path output)
            throws Exception {
        try (var connection = session.getConnection();
                var setting = connection.createStatement()) {
            setting.setQueryTimeout(10);
            try {
                setting.execute("SET STATISTICS XML ON");
            } catch (final SQLException failure) {
                if (failure.getErrorCode() != 262) {
                    throw failure;
                }
                Files.writeString(
                        output.resolve("showplan-unavailable.txt"),
                        "SHOWPLAN_DENIED_NO_GRANT_REQUESTED",
                        StandardCharsets.UTF_8);
                return;
            }
            final List<String> queries =
                    List.of(
                            """
                    SELECT COUNT_BIG(*) FROM core.relational_lab_link l
                    JOIN core.relational_lab_root r ON r.run_id=l.run_id AND r.source_key=l.origin_key
                        AND r.entity_name=CASE l.relation_kind WHEN 'MC' THEN N'manifestos' ELSE N'coletas' END
                    WHERE l.run_id=? AND l.active=1
                    """,
                            """
                    SELECT COUNT_BIG(*) FROM (SELECT s.source_key,s.component_kind,s.component_key
                        FROM stg.relational_lab_component s JOIN ctl.relational_lab_capture c ON c.execution_id=s.execution_id
                        WHERE c.run_id=? GROUP BY s.source_key,s.component_kind,s.component_key) d
                    """,
                            """
                    SELECT COUNT_BIG(*) FROM (SELECT TOP(4) backlog_id FROM ctl.relational_lab_backlog
                        WHERE run_id=? AND state IN(N'PENDING',N'DEFERRED') AND eligible_at<=SYSUTCDATETIME()
                        AND attempts<3 ORDER BY eligible_at,backlog_id) b
                    """);
            try {
                for (int index = 0; index < queries.size(); index++) {
                    try (var sql = connection.prepareStatement(queries.get(index))) {
                        sql.setQueryTimeout(30);
                        sql.setString(1, run.toString());
                        boolean result = sql.execute();
                        boolean planFound = false;
                        while (true) {
                            if (result) {
                                try (var rows = sql.getResultSet()) {
                                    if (rows.getMetaData().getColumnLabel(1).contains("Showplan")) {
                                        assertTrue(rows.next());
                                        final String plan = rows.getString(1);
                                        assertTrue(plan.contains("ShowPlanXML"));
                                        Files.writeString(
                                                output.resolve("query-" + index + ".sqlplan"),
                                                plan,
                                                StandardCharsets.UTF_8);
                                        planFound = true;
                                    }
                                }
                            } else if (sql.getUpdateCount() == -1) {
                                break;
                            }
                            result = sql.getMoreResults();
                        }
                        assertTrue(
                                planFound, "Actual SQL plan required when SHOWPLAN is available");
                    }
                }
            } finally {
                setting.execute("SET STATISTICS XML OFF");
            }
        }
    }

    private static long heap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static final class Probe implements RelationalSyntheticSource.Observer {
        private final ManagedPageGauge gauge = new ManagedPageGauge();
        private DataExportPageResponse active;
        private WeakReference<DataExportPageResponse> last;
        private int staged;
        private int maximumBatch;
        private int batchesInFlight;
        private int maximumBatchesInFlight;
        private long peak = heap();

        @Override
        public void beforeFetch() {
            gauge.beforeFetch();
        }

        @Override
        public void pageFetched(final DataExportPageResponse page, final long bytes) {
            gauge.pageFetched(page, bytes);
            active = page;
            staged = 0;
            peak = Math.max(peak, heap());
            if (page.recordCount() == 0) {
                release();
            }
        }

        @Override
        public void batchStarted(final int records) {
            batchesInFlight++;
            maximumBatchesInFlight = Math.max(maximumBatchesInFlight, batchesInFlight);
            maximumBatch = Math.max(maximumBatch, records);
        }

        @Override
        public void batchStaged(final int records) {
            staged += records;
            assertTrue(staged <= active.recordCount());
            if (staged == active.recordCount()) {
                gauge.pageConsumed(active, staged);
                release();
            }
            batchesInFlight--;
        }

        private void release() {
            last = new WeakReference<>(active);
            gauge.pageReleased(active);
            active = null;
        }

        @Override
        public void captureClosed() {
            if (active != null) {
                release();
            }
            assertTrue(last != null);
        }
    }
}
