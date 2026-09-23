package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.medicao.ExecutionWidePageRetentionMutant;
import br.com.esl.etl.v2.contratos.medicao.ManagedPageGauge;
import br.com.esl.etl.v2.contratos.medicao.MeasurementAssessment;
import br.com.esl.etl.v2.contratos.medicao.MeasurementDiagnostics;
import br.com.esl.etl.v2.contratos.medicao.MeasurementEvaluator;
import br.com.esl.etl.v2.contratos.medicao.MeasurementEvidence;
import br.com.esl.etl.v2.contratos.medicao.MeasurementPlan;
import br.com.esl.etl.v2.contratos.medicao.MeasurementRun;
import br.com.esl.etl.v2.contratos.medicao.MeasurementStreamer;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class DataExportPageStreamerMultiscaleMeasurementTest {

    private static final int RECORDS_PER_PAGE = 8;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-09-06T12:00:00Z"), ZoneOffset.UTC);

    public static MeasurementRun runHealthyScenarioForReceipt(final int dataPages) {
        final DataExportMeasurementFixture fixture = newFixture(dataPages, "receipt");
        return fixture.execute(new ConsumingPageObserver());
    }

    @ParameterizedTest(name = "DataExportPageStreamer with {0} data pages")
    @ValueSource(ints = {16, 256, 4096})
    void measuresTheRealStreamerAtEveryGovernedScale(final int dataPages) {
        final DataExportMeasurementFixture fixture = newFixture(dataPages, "bounded");
        final MeasurementRun run = fixture.execute(new ConsumingPageObserver());

        final MeasurementEvidence evidence = run.evidence();
        final DataExportExtractionResult extraction = fixture.extractionResult();
        final MeasurementAssessment independentlyEvaluated =
                new MeasurementEvaluator().evaluate(evidence);

        assertEquals(fixture.plan(), evidence.plan());
        assertEquals(dataPages + 1, extraction.pagesFetched());
        assertEquals((long) dataPages * RECORDS_PER_PAGE, extraction.recordsDelivered());
        assertEquals(dataPages + 1, extraction.terminalPage());
        assertEquals(fixture.plan().expectedFetchedPages(), evidence.fetchedPages());
        assertEquals(fixture.plan().expectedConsumedPages(), evidence.consumedPages());
        assertEquals(fixture.plan().expectedRecords(), evidence.records());
        assertTrue(evidence.bytes() > 0L);
        assertEquals(1L, evidence.maxInFlightPages());
        assertEquals(0L, evidence.finalInFlightPages());
        assertEquals((long) dataPages + 1L, evidence.acquisitions());
        assertEquals(evidence.fetchedPages(), evidence.acquisitions());
        assertEquals(evidence.acquisitions(), evidence.releases());
        assertEquals(0L, evidence.maxRetainedPages());
        assertEquals(0L, evidence.finalRetainedPages());
        assertEquals(run.assessment(), independentlyEvaluated);
        assertTrue(independentlyEvaluated.proven());
        assertEquals(
                MeasurementAssessment.Disposition.PROVEN_SYNTHETICALLY,
                independentlyEvaluated.disposition());
        assertEquals(
                MeasurementAssessment.Reason.MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY,
                independentlyEvaluated.reason());
        assertDiagnosticsWereCollected(run.diagnostics());
        assertEquals((long) dataPages + 1L, fixture.gatewayFetches());
        assertEquals((long) dataPages * RECORDS_PER_PAGE, fixture.generatedRecords());
    }

    @Test
    void lazyGatewayRejectsTheNextFetchUntilTheCurrentPageIsReleased() {
        final MeasurementPlan plan =
                new MeasurementPlan(MeasurementPlan.StreamerKind.DATA_EXPORT, 16, RECORDS_PER_PAGE);
        final ManagedPageGauge gauge = new ManagedPageGauge();
        final LazySyntheticGateway gateway = new LazySyntheticGateway(plan, gauge);
        final DataExportPageRequest firstRequest = request();

        assertEquals(0L, gateway.fetches());
        assertEquals(0L, gateway.generatedRecords());

        final DataExportPageResponse firstPage = gateway.fetch(firstRequest);
        try {
            assertEquals(RECORDS_PER_PAGE, firstPage.recordCount());
            assertEquals(1L, gateway.fetches());
            assertEquals(RECORDS_PER_PAGE, gateway.generatedRecords());
            assertThrows(
                    IllegalStateException.class, () -> gateway.fetch(firstRequest.withPage(2)));
            assertEquals(1L, gateway.fetches());
            assertEquals(RECORDS_PER_PAGE, gateway.generatedRecords());
        } finally {
            gauge.pageReleased(firstPage);
        }

        assertEquals(0L, gauge.snapshot(plan).finalInFlightPages());
    }

    @Test
    void genericEvaluatorRejectsRealExecutionWidePageRetentionIndependentlyOfInFlight() {
        final int dataPages = 16;
        final DataExportMeasurementFixture fixture = newFixture(dataPages, "retention-mutant");
        final ExecutionWidePageRetentionMutant mutant =
                new ExecutionWidePageRetentionMutant(fixture.gauge());

        try {
            final MeasurementRun run = fixture.execute(mutant);
            final MeasurementEvidence evidence = run.evidence();
            final MeasurementAssessment assessment = new MeasurementEvaluator().evaluate(evidence);

            assertEquals(run.assessment(), assessment);
            assertFalse(assessment.proven());
            assertEquals(MeasurementAssessment.Disposition.REJECTED, assessment.disposition());
            assertEquals(
                    MeasurementAssessment.Reason.EXECUTION_WIDE_PAGE_RETENTION_DETECTED,
                    assessment.reason());
            assertEquals(dataPages, mutant.retainedPageCount());
            assertEquals(dataPages, evidence.maxRetainedPages());
            assertEquals(dataPages, evidence.finalRetainedPages());
            assertEquals(1L, evidence.maxInFlightPages());
            assertEquals(0L, evidence.finalInFlightPages());
            assertEquals((long) dataPages + 1L, evidence.acquisitions());
            assertEquals(evidence.acquisitions(), evidence.releases());
            assertEquals(evidence.fetchedPages(), evidence.acquisitions());
            assertDiagnosticsWereCollected(run.diagnostics());
        } finally {
            mutant.clear();
        }

        assertEquals(0, mutant.retainedPageCount());
        assertEquals(0L, fixture.gauge().snapshot(fixture.plan()).finalRetainedPages());
    }

    private static void assertDiagnosticsWereCollected(final MeasurementDiagnostics diagnostics) {
        assertTrue(diagnostics.heapBeforeBytes() >= 0L);
        assertTrue(diagnostics.heapAfterBytes() >= 0L);
        assertTrue(diagnostics.heapPeakBytes() >= 0L);
        assertTrue(diagnostics.durationNanos() >= 0L);
    }

    private static DataExportPageRequest request() {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 6)),
                Optional.empty(),
                1,
                RECORDS_PER_PAGE,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }

    private static DataExportMeasurementFixture newFixture(
            final int dataPages, final String occurrence) {
        final MeasurementPlan plan =
                new MeasurementPlan(
                        MeasurementPlan.StreamerKind.DATA_EXPORT, dataPages, RECORDS_PER_PAGE);
        return new DataExportMeasurementFixture(plan, occurrence);
    }

    private static final class DataExportMeasurementFixture {

        private final MeasurementPlan plan;
        private final ManagedPageGauge gauge;
        private final LazySyntheticGateway gateway;
        private final ContractExecutionContext executionContext;
        private DataExportExtractionResult extractionResult;

        private DataExportMeasurementFixture(final MeasurementPlan plan, final String occurrence) {
            this.plan = plan;
            gauge = new ManagedPageGauge();
            gateway = new LazySyntheticGateway(plan, gauge);
            executionContext =
                    ContractTestSupport.executionContext(
                            UUID.nameUUIDFromBytes(
                                    ("v2-050-data-export-" + occurrence + "-" + plan.dataPages())
                                            .getBytes(StandardCharsets.UTF_8)));
        }

        private MeasurementRun execute(final Consumer<Object> pageObserver) {
            final DataExportPageStreamer productiveStreamer =
                    new DataExportPageStreamer(
                            gateway, DataExportExtractionAudit.noop(), FIXED_CLOCK);
            final MeasurementStreamer<DataExportPageResponse> measuredStreamer =
                    consumer -> {
                        try {
                            extractionResult =
                                    productiveStreamer.stream(
                                            executionContext,
                                            request(),
                                            new DataExportExtractionLimits(
                                                    plan.expectedFetchedPages(),
                                                    plan.expectedRecords(),
                                                    plan.recordsPerPage()),
                                            page -> consume(page, consumer));
                        } finally {
                            gateway.releaseTerminalPage();
                        }
                    };

            return MeasurementRun.execute(plan, gauge, measuredStreamer, pageObserver);
        }

        private void consume(
                final DataExportReadPage readPage,
                final Consumer<? super DataExportPageResponse> consumer) {
            final DataExportPageResponse realPage = readPage.response();
            try {
                consumer.accept(realPage);
                gauge.pageConsumed(realPage, realPage.recordCount());
            } finally {
                gauge.pageReleased(realPage);
            }
        }

        private MeasurementPlan plan() {
            return plan;
        }

        private ManagedPageGauge gauge() {
            return gauge;
        }

        private DataExportExtractionResult extractionResult() {
            if (extractionResult == null) {
                throw new IllegalStateException("Data Export measurement did not run.");
            }
            return extractionResult;
        }

        private long gatewayFetches() {
            return gateway.fetches();
        }

        private long generatedRecords() {
            return gateway.generatedRecords();
        }
    }

    private static final class LazySyntheticGateway implements DataExportGateway {

        private final MeasurementPlan plan;
        private final ManagedPageGauge gauge;
        private long fetches;
        private long generatedRecords;
        private DataExportPageResponse terminalPage;

        private LazySyntheticGateway(final MeasurementPlan plan, final ManagedPageGauge gauge) {
            this.plan = plan;
            this.gauge = gauge;
        }

        @Override
        public DataExportPageResponse fetch(final DataExportPageRequest pageRequest) {
            gauge.beforeFetch();
            final int expectedPage = Math.toIntExact(fetches + 1L);
            if (pageRequest.page() != expectedPage) {
                throw new IllegalStateException("Unexpected synthetic Data Export page.");
            }

            final List<JsonNode> records = new ArrayList<>(plan.recordsPerPage());
            long pageBytes = 2L;
            if (pageRequest.page() <= plan.dataPages()) {
                for (int index = 0; index < plan.recordsPerPage(); index++) {
                    final JsonNode record =
                            JsonNodeFactory.instance
                                    .objectNode()
                                    .put("id", "synthetic-" + pageRequest.page() + "-" + index);
                    records.add(record);
                    pageBytes =
                            Math.addExact(
                                    pageBytes,
                                    record.toString().getBytes(StandardCharsets.UTF_8).length);
                    if (index > 0) {
                        pageBytes = Math.incrementExact(pageBytes);
                    }
                }
            }

            final DataExportPageResponse response = new DataExportPageResponse(records);
            fetches = Math.incrementExact(fetches);
            generatedRecords = Math.addExact(generatedRecords, records.size());
            gauge.pageFetched(response, pageBytes);
            if (response.recordCount() == 0) {
                terminalPage = response;
            }
            return response;
        }

        private void releaseTerminalPage() {
            final DataExportPageResponse pageToRelease = terminalPage;
            terminalPage = null;
            if (pageToRelease != null) {
                gauge.pageReleased(pageToRelease);
            }
        }

        private long fetches() {
            return fetches;
        }

        private long generatedRecords() {
            return generatedRecords;
        }
    }

    private static final class ConsumingPageObserver implements Consumer<Object> {

        @Override
        public void accept(final Object page) {
            if (!(page instanceof DataExportPageResponse)) {
                throw new IllegalArgumentException("Expected a real Data Export page.");
            }
        }
    }
}
