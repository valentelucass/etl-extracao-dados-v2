package br.com.esl.etl.v2.bootstrap;

import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.CLOCK;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.DATE;
import static br.com.esl.etl.v2.bootstrap.ExpansionLaboratoryLocalIntegrationIT.scalar;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.medicao.ManagedPageGauge;
import br.com.esl.etl.v2.contratos.medicao.MeasurementDiagnostics;
import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteRules.FiscalPolicy;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionMaterializations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionReferences;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionRelations;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionStatus;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * The wide 151-field captures justify smaller local scales; no heap plateau or production SLO
 * claim.
 */
class ExpansionLaboratoryScaleIT {
    @ParameterizedTest
    @ValueSource(ints = {16, 64, 256, 64})
    @Timeout(240)
    void measuresFourPipelinesDependenciesAndBothLoads(final int roots) throws Exception {
        final Path output =
                Path.of(
                        "target",
                        "expansion-measurements",
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
                        "maximumPageBytes",
                        65536,
                        "seconds",
                        240,
                        "heapMiB",
                        512,
                        "rollbackOnly",
                        true,
                        "target",
                        "localhost/ETL_SISTEMA_V2_SHADOW",
                        "reason",
                        "four wide typed sources and two financial loads"));
        final long started = System.nanoTime(), beforeHeap = heap();
        long peak = beforeHeap;
        final CancellationToken deadline =
                () ->
                        Thread.currentThread().isInterrupted()
                                || System.nanoTime() - started
                                        >= java.time.Duration.ofSeconds(240).toNanos();
        final var probes = new ArrayList<Map<String, Object>>(6);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            final String before = RelationalLaboratoryLocalIntegrationIT.counts(session);
            final UUID run = UUID.randomUUID();
            final var policy =
                    new ExpansionPolicy(
                            DATE,
                            DATE.plusDays(3),
                            DATE.plusDays(14),
                            16,
                            1000,
                            10000,
                            FiscalPolicy.UNRESOLVED);
            new JdbcExpansionLaboratory(session, CLOCK).start(run, policy);
            final var runtime =
                    new LocalExpansionRuntime(session, run, policy, CLOCK, Clock.systemUTC());
            for (final var template :
                    List.of(
                            DataExportTemplate.CONTAS_A_PAGAR,
                            DataExportTemplate.FATURAS_POR_CLIENTE,
                            DataExportTemplate.INVENTARIO,
                            DataExportTemplate.SINISTROS)) {
                final var probe = new Probe();
                final long preparations = session.preparedStatements();
                final var source =
                        ExpansionLaboratoryFixtures.source(template, roots, 16).observed(probe);
                final var capture =
                        runtime.capture(
                                template, DATE, ExecutionMode.BOOTSTRAP, null, source, deadline);
                assertEquals(roots * 2L, capture.receipt().inserts());
                assertEquals(roots, capture.receipt().duplicates());
                probes.add(
                        measure(
                                template,
                                probe,
                                roots * 3,
                                source.metrics().maximumPageBytes(),
                                session.preparedStatements() - preparations));
                peak = Math.max(peak, probe.peak);
            }
            final var dependencies =
                    new LocalExpansionDependencyRuntime(
                            session, run, policy, CLOCK, Clock.systemUTC());
            for (final var template :
                    List.of(DataExportTemplate.FRETES, DataExportTemplate.LOCALIZACAO_CARGAS)) {
                final int count = roots - (template == DataExportTemplate.FRETES ? 1 : 0);
                final var probe = new Probe();
                final long preparations = session.preparedStatements();
                var source =
                        ExpansionDependencyFixtures.source(template, count, 16).observed(probe);
                if (template == DataExportTemplate.FRETES) {
                    source =
                            source.withFinancialBindings(
                                    ExpansionDependencyFixtures::financialTerms);
                }
                assertEquals(
                        count,
                        dependencies
                                .capture(
                                        template,
                                        DATE,
                                        ExecutionMode.BOOTSTRAP,
                                        null,
                                        source,
                                        deadline)
                                .receipt()
                                .inserts());
                probes.add(
                        measure(
                                template,
                                probe,
                                count * 2,
                                source.metrics().maximumPageBytes(),
                                session.preparedStatements() - preparations));
                peak = Math.max(peak, probe.peak);
            }
            final var relations = new JdbcExpansionRelations(session, CLOCK);
            for (int first = 1; first <= roots; first += 14) {
                relations.bind(
                        run,
                        ExpansionLaboratoryRelationFixtures.bindingBatch(
                                DATE, first, Math.min(14, roots - first + 1)),
                        deadline);
            }
            assertEquals(7, relations.resolve(run).missing());
            actualPlans(
                    session,
                    run,
                    output,
                    "relational",
                    List.of(
                            "SELECT COUNT_BIG(*) FROM core.expansion_lab_link l JOIN "
                                    + "core.expansion_lab_component c ON c.component_id=l.component_id WHERE l.run_id=?",
                            "SELECT COUNT_BIG(*) FROM (SELECT vertical,root_type,root_key,part_type,part_key,"
                                    + "component_type,component_key FROM stg.expansion_lab_observation WHERE run_id=? "
                                    + "GROUP BY vertical,root_type,root_key,part_type,part_key,component_type,"
                                    + "component_key) d",
                            "SELECT COUNT_BIG(*) FROM (SELECT TOP(31) queue_id FROM ctl.expansion_lab_queue "
                                    + "WHERE run_id=? AND state IN('READY','EMPTY',"
                                    + "'TEMPORARY') AND next_at<='20360415' AND attempts<maximum_attempts ORDER BY "
                                    + "next_at,queue_id) q"));
            assertEquals(
                    1,
                    new ExpansionLaboratoryHydrator(relations, dependencies)
                            .hydrate(run, 1, deadline)
                            .completed());
            new JdbcExpansionReferences(session, CLOCK)
                    .importPackaged(run, 1, DATE, DATE.plusDays(10));
            final var material = new JdbcExpansionMaterializations(session, CLOCK);
            assertEquals(
                    roots,
                    material.invoices(
                                    run,
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.BOOTSTRAP,
                                    true,
                                    DATE,
                                    DATE.plusDays(3))
                            .ready());
            assertEquals(
                    roots,
                    material.revenue(
                                    run,
                                    UUID.randomUUID(),
                                    1,
                                    ExecutionMode.BOOTSTRAP,
                                    true,
                                    DATE,
                                    DATE.plusDays(3))
                            .ready());
            assertEquals(
                    roots * 100L,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(operational_value)) FROM mart.expansion_lab_invoice "
                                    + "WHERE run_id=? AND disposition='READY' AND currency='BRL' AND unit='MAJOR'",
                            run));
            assertEquals(
                    roots * 120L,
                    scalar(
                            session,
                            "SELECT CONVERT(BIGINT,SUM(revenue_value)) FROM mart.expansion_lab_revenue WHERE "
                                    + "run_id=? AND disposition='READY' AND currency='BRL' AND unit='MAJOR'",
                            run));
            final var status = new JdbcExpansionStatus(session).read(run);
            assertTrue(status.complete());
            assertEquals(roots * 16L - 1, status.observations());
            final long prepared = session.preparedStatements(),
                    created = session.createdStatements();
            actualPlans(
                    session,
                    run,
                    output,
                    "materialization",
                    List.of(
                            "DECLARE @run UNIQUEIDENTIFIER=?,"
                                    + "@receipt UNIQUEIDENTIFIER=NEWID(); EXEC mart.usp_materialize_expansion_invoices "
                                    + "@run,@receipt,1,'REPLAY',1,'20360401','20360404','20360415';",
                            "DECLARE @run UNIQUEIDENTIFIER=?,"
                                    + "@receipt UNIQUEIDENTIFIER=NEWID(); EXEC mart.usp_materialize_expansion_revenue "
                                    + "@run,@receipt,1,'REPLAY',1,'20360401','20360404','20360415';"));
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
                            "jdbcPreparedStatementsBeforePlanProbes",
                            prepared,
                            "jdbcCreatedStatementsBeforePlanProbes",
                            created,
                            "diagnostics",
                            new MeasurementDiagnostics(
                                    beforeHeap,
                                    afterHeap,
                                    Math.max(peak, afterHeap),
                                    System.nanoTime() - started),
                            "statusBeforeRollback",
                            status,
                            "rollbackAggregatePreserved",
                            true,
                            "heapPlateauProven",
                            false));
        }
    }

    private static Map<String, Object> measure(
            final DataExportTemplate template,
            final Probe probe,
            final int rows,
            final int bytes,
            final long preparations) {
        final var measured = probe.gauge.counts();
        assertEquals(rows, measured.records());
        assertEquals((rows + 15) / 16 + 1, measured.fetchedPages());
        assertEquals(1, measured.maximumInFlightPages());
        assertEquals(0, measured.finalInFlightPages());
        assertEquals(measured.acquisitions(), measured.releases());
        assertEquals(0, measured.finalRetainedPages());
        assertTrue(probe.maximumBatch <= 16);
        assertEquals(1, probe.maximumBatchesInFlight);
        assertEquals(0, probe.batchesInFlight);
        assertTrue(bytes <= 65536);
        return Map.of(
                "entity",
                template.name(),
                "managedPages",
                measured,
                "maximumBatchRows",
                probe.maximumBatch,
                "maximumBatchesInFlight",
                probe.maximumBatchesInFlight,
                "maximumPageBytes",
                bytes,
                "jdbcStatementPreparations",
                preparations,
                "heapSamplePeakBytes",
                probe.peak);
    }

    private static void actualPlans(
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

    private static final class Probe
            implements ExpansionSyntheticSource.Observer, ExpansionDependencySource.Observer {
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
