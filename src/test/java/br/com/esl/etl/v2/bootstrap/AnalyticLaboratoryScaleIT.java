package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.medicao.ManagedPageGauge;
import br.com.esl.etl.v2.contratos.medicao.MeasurementDiagnostics;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticQueries;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticScenario;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Eleven wide inputs; 64 timed out in full regression, so the final reservation uses 4/16/32/16.
 */
class AnalyticLaboratoryScaleIT {
    @org.junit.jupiter.api.Test
    @Timeout(120)
    void rasterReleasesCappedParentPageBeforeFetchingChildren() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final UUID run = UUID.randomUUID();
            AnalyticLaboratoryRasterIT.start(session, run);
            final var probe = new Probe();
            final var result =
                    new LocalRasterRuntime(
                                    session, Clock.systemUTC(), AnalyticScenarioRuntime.ZONE, probe)
                            .capture(
                                    run,
                                    ExecutionMode.BOOTSTRAP,
                                    new br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow(
                                            AnalyticScenarioRuntime.START,
                                            AnalyticScenarioRuntime.END),
                                    AnalyticRasterFixtures.source(256, 1),
                                    10,
                                    10000,
                                    CancellationToken.none());
            assertTrue(result.calls() > 1);
            assertEquals(256 * 6L, probe.raster.counts().records());
            assertEquals(1, probe.raster.counts().maximumInFlightPages());
            assertEquals(0, probe.raster.counts().finalInFlightPages());
            assertEquals(probe.raster.counts().acquisitions(), probe.raster.counts().releases());
            assertEquals(probe.raster.counts().bytes(), result.responseBytes());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 16, 32, 16})
    @Timeout(240)
    void measuresActualScenarioPagesLoadsConsumersAndPlans(final int roots) throws Exception {
        final Path output =
                Path.of(
                        "target",
                        "analytic-measurements",
                        "scale-" + roots + "-" + UUID.randomUUID());
        Files.createDirectories(output);
        final var mapper = new ObjectMapper();
        mapper.writeValue(
                output.resolve("reservation.json").toFile(),
                Map.of(
                        "rootsPerVertical",
                        roots,
                        "pageSize",
                        16,
                        "seconds",
                        240,
                        "heapMiB",
                        512,
                        "rollbackOnly",
                        true,
                        "target",
                        "localhost/ETL_SISTEMA_V2_SHADOW",
                        "reason",
                        "eleven inputs and five facts; observe nine DE captures and Raster; hydration is separate"));
        final long started = System.nanoTime(), beforeHeap = heap();
        final CancellationToken deadline =
                () ->
                        Thread.currentThread().isInterrupted()
                                || System.nanoTime() - started >= Duration.ofSeconds(240).toNanos();
        final var probe = new Probe();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = RelationalLaboratoryLocalIntegrationIT.counts(session);
            final var runtime = new AnalyticScenarioRuntime(session, Clock.systemUTC(), probe);
            final var run = runtime.start(roots, 16);
            final var cycle =
                    runtime.capture(run, ExecutionMode.BOOTSTRAP, 1, false, null, deadline);
            assertTrue(cycle.status().complete());
            assertEquals(roots * 2L, cycle.freight().ready());
            assertEquals(roots, cycle.manifests().ready());
            assertTrue(cycle.collectors().ready() > 0);
            new JdbcAnalyticScenario(session).verifyFixtureFacts(run.id(), roots);
            final var pages = probe.gauge.counts();
            // The nine DE inputs include COT (3 rows/root) and the missing Frete hydration row.
            assertEquals(27L * roots - 1, pages.records() + probe.quotes.records);
            assertEquals(3L * roots, probe.quotes.records);
            assertTrue(probe.quotes.maximumBatch <= 16);
            assertTrue(probe.quotes.maximumBytes <= 65536);
            assertEquals(0, probe.quotes.inFlight);
            assertEquals(1, probe.quotes.maximumInFlight);
            assertTrue(!probe.quotes.pageActive);
            assertEquals(roots, probe.users.records);
            assertTrue(probe.users.maximumBatch <= 20);
            assertTrue(probe.users.maximumBytes <= 65536);
            assertEquals(0, probe.users.inFlight);
            assertEquals(1, probe.users.maximumInFlight);
            assertTrue(!probe.users.pageActive);
            for (final var counts : List.of(pages, probe.raster.counts())) {
                assertEquals(1, counts.maximumInFlightPages());
                assertEquals(0, counts.finalInFlightPages());
                assertEquals(counts.acquisitions(), counts.releases());
                assertEquals(0, counts.maximumRetainedPages());
                assertEquals(0, counts.finalRetainedPages());
            }
            assertTrue(probe.maximumPageBytes <= 65536);
            assertTrue(probe.maximumBatch <= 16);
            assertEquals(1, probe.maximumBatchesInFlight);
            assertEquals(0, probe.batchesInFlight);
            assertEquals(roots * 6L, probe.raster.counts().records());
            assertEquals(probe.raster.counts().bytes(), cycle.raster().responseBytes());
            assertEquals(probe.raster.counts().fetchedPages(), cycle.raster().calls());
            assertTrue(cycle.raster().maximumBatch() <= 16);
            assertTrue(cycle.raster().executedBatches() > 0);
            final var queries = new ArrayList<Map<String, Object>>(5);
            final var reader = new JdbcAnalyticQueries(session);
            for (final var contract :
                    List.of(
                            AnalyticSqlContract.SQL_02,
                            AnalyticSqlContract.SQL_08,
                            AnalyticSqlContract.SQL_09,
                            AnalyticSqlContract.SQL_13,
                            AnalyticSqlContract.SQL_16)) {
                final long queryStart = System.nanoTime(), calls = session.preparedStatements();
                final var result = reader.read(run.id(), contract, 2, 4096, deadline, row -> {});
                assertTrue(result.rows() > 0);
                queries.add(
                        Map.of(
                                "contract",
                                contract.id(),
                                "receipt",
                                result,
                                "durationNanos",
                                System.nanoTime() - queryStart,
                                "jdbcPreparations",
                                session.preparedStatements() - calls));
            }
            final long prepared = session.preparedStatements(),
                    created = session.createdStatements();
            actualPlans(
                    session,
                    run.id(),
                    output,
                    "analytic",
                    List.of(
                            "SELECT COUNT_BIG(*) FROM (SELECT s.trip_key FROM stg.analytic_raster_trip s JOIN ctl."
                                    + "analytic_raster_capture c ON c.capture_id=s.capture_id WHERE c.run_id=? GROUP BY s.tr"
                                    + "ip_key) d",
                            "SELECT COUNT_BIG(*) FROM core.analytic_raster_trip t JOIN core.analytic_raster_stop s"
                                    + " ON s.run_id=t.run_id AND s.trip_key=t.trip_key WHERE t.run_id=? AND t.active=1 AND s"
                                    + ".active=1",
                            "SELECT COUNT_BIG(*) FROM ref.analytic_lab_dimension_binding WHERE run_id=? AND entity"
                                    + "='MAN' AND role='TRACTOR' AND revision=1 AND valid_from<='20360401' AND valid_to_excl"
                                    + "usive>'20360401' AND active=1",
                            "DECLARE @run UNIQUEIDENTIFIER=?,@receipt UNIQUEIDENTIFIER=NEWID(); EXEC mart.usp_mate"
                                    + "rialize_analytic_freight @run,@receipt,2,'REPLAY',1,'20360401','20360404','20360415';",
                            "DECLARE @run UNIQUEIDENTIFIER=?,@receipt UNIQUEIDENTIFIER=NEWID(); EXEC mart.usp_mate"
                                    + "rialize_analytic_collectors @run,@receipt,2,'REPLAY',1,'20360401','20360404','2036041"
                                    + "5';",
                            "DECLARE @run UNIQUEIDENTIFIER=?,@receipt UNIQUEIDENTIFIER=NEWID(); EXEC mart.usp_mate"
                                    + "rialize_analytic_manifests @run,@receipt,2,'REPLAY',1,'20360401','20360404','20360415"
                                    + "';"));
            session.rollback();
            assertEquals(before, RelationalLaboratoryLocalIntegrationIT.counts(session));
            final long afterHeap = heap();
            mapper.writeValue(
                    output.resolve("result.json").toFile(),
                    Map.of(
                            "scaleRoots",
                            roots,
                            "deNineCapturePages",
                            pages,
                            "deBatches",
                            Map.of(
                                    "count",
                                    probe.batches,
                                    "maximumRows",
                                    probe.maximumBatch,
                                    "maximumInFlight",
                                    probe.maximumBatchesInFlight,
                                    "maximumPageBytes",
                                    probe.maximumPageBytes),
                            "raster",
                            Map.of(
                                    "pages",
                                    probe.raster.counts(),
                                    "maximumPageBytes",
                                    cycle.raster().maximumPageBytes(),
                                    "maximumBatchRows",
                                    cycle.raster().maximumBatch(),
                                    "executedBatches",
                                    cycle.raster().executedBatches()),
                            "loads",
                            Map.of(
                                    "MAT01",
                                    cycle.freight(),
                                    "MAT02",
                                    cycle.collectors(),
                                    "MAT05",
                                    cycle.manifests()),
                            "queries",
                            queries,
                            "jdbcBeforePlanProbes",
                            Map.of("prepared", prepared, "created", created),
                            "diagnostics",
                            new MeasurementDiagnostics(
                                    beforeHeap,
                                    afterHeap,
                                    Math.max(Math.max(probe.peak, beforeHeap), afterHeap),
                                    System.nanoTime() - started),
                            "rollbackAggregatePreserved",
                            true,
                            "heapPlateauProven",
                            false));
        }
    }

    static void actualPlans(
            final ColetaTemporalLaboratorySession session,
            final UUID run,
            final Path output,
            final String kind,
            final List<String> queries)
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
                        "SHOWPLAN_DENIED_NO_GRANTS",
                        StandardCharsets.UTF_8);
                return;
            }
            try {
                for (int index = 0; index < queries.size(); index++) {
                    int plans = 0;
                    try (var sql = connection.prepareStatement(queries.get(index))) {
                        sql.setQueryTimeout(30);
                        sql.setString(1, run.toString());
                        boolean result = sql.execute();
                        while (true) {
                            if (result) {
                                try (var rows = sql.getResultSet()) {
                                    if (rows.getMetaData().getColumnLabel(1).contains("Showplan")) {
                                        while (rows.next()) {
                                            final String plan = rows.getString(1);
                                            assertTrue(plan.contains("ShowPlanXML"));
                                            Files.writeString(
                                                    output.resolve(
                                                            kind
                                                                    + "-"
                                                                    + index
                                                                    + "-"
                                                                    + (++plans)
                                                                    + ".sqlplan"),
                                                    plan,
                                                    StandardCharsets.UTF_8);
                                        }
                                    }
                                }
                            } else if (sql.getUpdateCount() == -1) {
                                break;
                            }
                            result = sql.getMoreResults();
                        }
                    }
                    assertTrue(plans > 0, "Actual plans required when SHOWPLAN is available");
                }
            } finally {
                setting.execute("SET STATISTICS XML OFF");
            }
        }
    }

    private static long heap() {
        return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
    }

    private static final class Probe implements AnalyticScenarioObserver {
        private final ManagedPageGauge gauge = new ManagedPageGauge();
        private final ManagedPageGauge raster = new ManagedPageGauge();
        private final UserProbe users = new UserProbe();
        private final UserProbe quotes = new UserProbe();
        private byte[] rasterBody;
        private long rasterStaged;
        private long maximumPageBytes;
        private int batches;
        private DataExportPageResponse active;
        private WeakReference<DataExportPageResponse> last;
        private int staged;
        private int maximumBatch;
        private int batchesInFlight;
        private int maximumBatchesInFlight;
        private long peak = heap();

        @Override
        public AnalyticScenarioObserver forInput(final Input input) {
            return switch (input) {
                case USER -> users;
                case COT -> quotes;
                default -> this;
            };
        }

        @Override
        public void beforeFetch() {
            gauge.beforeFetch();
        }

        @Override
        public void pageFetched(final DataExportPageResponse page, final long bytes) {
            gauge.pageFetched(page, bytes);
            maximumPageBytes = Math.max(maximumPageBytes, bytes);
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
            batches++;
            maximumBatchesInFlight = Math.max(maximumBatchesInFlight, batchesInFlight);
            maximumBatch = Math.max(maximumBatch, records);
        }

        @Override
        public void batchStaged(final int records) {
            if (active != null) {
                staged += records;
                assertTrue(staged <= active.recordCount());
                if (staged == active.recordCount()) {
                    gauge.pageConsumed(active, staged);
                    release();
                }
            } else {
                assertTrue(rasterBody != null);
                rasterStaged += records;
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
            if (gauge.counts().acquisitions() > 0) {
                assertTrue(last != null);
            }
            assertTrue(rasterBody == null);
            assertEquals(0, batchesInFlight);
        }

        @Override
        public void rasterBeforeFetch() {
            raster.beforeFetch();
        }

        @Override
        public void rasterPageFetched(final byte[] body) {
            assertTrue(rasterBody == null);
            rasterBody = body;
            rasterStaged = 0;
            raster.pageFetched(body, body.length);
            peak = Math.max(peak, heap());
        }

        @Override
        public void rasterPageConsumed(final byte[] body, final long records) {
            assertTrue(rasterBody == body);
            assertEquals(records, rasterStaged);
            if (records > 0) {
                raster.pageConsumed(body, records);
            }
        }

        @Override
        public void rasterPageReleased(final byte[] body) {
            assertTrue(rasterBody == body);
            raster.pageReleased(body);
            rasterBody = null;
        }
    }

    /** USER and COT expose byte callbacks; this probe never invents a transport page. */
    private static final class UserProbe implements AnalyticScenarioObserver {
        private long records;
        private int maximumBatch;
        private long maximumBytes;
        private int inFlight;
        private int maximumInFlight;
        private boolean pageActive;

        @Override
        public void beforeFetch() {
            assertTrue(!pageActive);
            assertEquals(0, inFlight);
        }

        @Override
        public void pageBytes(final long bytes) {
            assertTrue(bytes > 0);
            maximumBytes = Math.max(maximumBytes, bytes);
            pageActive = true;
        }

        @Override
        public void batchStarted(final int count) {
            assertTrue(pageActive);
            maximumBatch = Math.max(maximumBatch, count);
            maximumInFlight = Math.max(maximumInFlight, ++inFlight);
        }

        @Override
        public void batchStaged(final int count) {
            records += count;
            inFlight--;
            pageActive = false;
        }

        @Override
        public void captureClosed() {
            assertEquals(0, inFlight);
            pageActive = false;
        }
    }
}
