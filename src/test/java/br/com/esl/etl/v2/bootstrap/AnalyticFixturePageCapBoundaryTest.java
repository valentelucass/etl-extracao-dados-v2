package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractGate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFailureCategory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportReadPage;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** Exercises the existing scenario page cap using only packaged fixture transports. */
class AnalyticFixturePageCapBoundaryTest {
    private static final int PER = 16;
    private static final int MAX_PAGES = 64;

    @Test
    void quotation315RootsHasRoomForTheEmptyPage64() {
        quotation(315, 945, true);
    }

    @Test
    void quotation316RootsCannotCompleteWithoutAnUnreservedPage65() {
        quotation(316, 948, false);
    }

    @Test
    void quotation480RootsStopsAfter960RowsWithoutAnUnreservedPage65() {
        quotation(480, 960, false);
    }

    @Test
    void collection315RootsHasRoomForTheEmptyPage64() {
        collection(315, 945, true);
    }

    @Test
    void collection316RootsCannotCompleteWithoutAnUnreservedPage65() {
        collection(316, 948, false);
    }

    @Test
    void collection480RootsStopsAfter960RowsWithoutAnUnreservedPage65() {
        collection(480, 960, false);
    }

    private static void quotation(
            final int roots, final long expectedRows, final boolean completes) {
        final var source = AnalyticQuotesFixtures.source(1, roots, PER);
        final var release = source.contractRelease();
        final var guard = guard(release);
        final var gateway =
                DataExportContractGate.enforce(
                                source.bundle(guard, configuration()),
                                DataExportTemplate.COTACOES,
                                guard)
                        .dataGateway();
        guard.validateMetadata(release.metadata());
        exercise(DataExportTemplate.COTACOES, gateway, guard, roots, expectedRows, completes);
        assertEquals(MAX_PAGES, source.metrics().fetchedPages());
        assertTrue(source.metrics().maximumPageBytes() <= 65536);
    }

    private static void collection(
            final int roots, final long expectedRows, final boolean completes) {
        final var source = AnalyticCollectionsFixtures.source(1, roots, PER);
        final var release = source.contractRelease(DataExportTemplate.COLETAS);
        final var guard = guard(release);
        final var gateway =
                source.gateway(DataExportTemplate.COLETAS, guard, release, configuration());
        exercise(DataExportTemplate.COLETAS, gateway, guard, roots, expectedRows, completes);
        assertEquals(MAX_PAGES, source.metrics().fetchedPages());
        assertTrue(source.metrics().maximumPageBytes() <= 65536);
    }

    private static void exercise(
            final DataExportTemplate template,
            final DataExportGateway gateway,
            final ContractRunGuard guard,
            final int roots,
            final long expectedRows,
            final boolean completes) {
        final boolean collection = template == DataExportTemplate.COLETAS;
        final var seed =
                collection ? AnalyticCollectionsFixtures.data() : AnalyticQuotesFixtures.data();
        final LocalDate date =
                collection
                        ? LocalDate.parse(seed.path("request_date").asText())
                        : LocalDate.of(2036, 4, 1);
        final var audit = new CapturingAudit();
        final var deliveredRows = new AtomicLong();
        final var deliveredPages = new AtomicInteger();
        final Consumer<DataExportReadPage> consumer =
                read -> {
                    assertEquals(deliveredPages.incrementAndGet(), read.request().page());
                    assertTrue(read.response().recordCount() > 0);
                    assertTrue(read.response().recordCount() <= 15);
                    assertEquals(0, read.response().recordCount() % 3);
                    read.response()
                            .forEachRecord(
                                    record -> {
                                        final int root =
                                                (int) (deliveredRows.getAndIncrement() / 3) + 1;
                                        final var expected = seed.deepCopy();
                                        if (collection) {
                                            expected.put("id", 200000 + root)
                                                    .put("sequence_code", 100000 + root)
                                                    .put("synthetic_item_key", root);
                                        } else {
                                            expected.put("sequence_code", root);
                                        }
                                        assertEquals(expected, record);
                                    });
                };
        final var request =
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(date, date),
                        Optional.empty(),
                        1,
                        PER,
                        template.defaultOrderBy());
        final var limits = new DataExportExtractionLimits(MAX_PAGES, roots * 3L, PER);
        final var streamer = new DataExportPageStreamer(gateway, audit, Clock.systemUTC());
        if (completes) {
            final var result = streamer.stream(guard.executionContext(), request, limits, consumer);
            assertEquals(MAX_PAGES, result.pagesFetched());
            assertEquals(MAX_PAGES, result.terminalPage());
            assertEquals(expectedRows, result.recordsDelivered());
            assertEquals(1, audit.completed);
            assertNull(audit.failure);
            assertEquals(63, deliveredPages.get());
            assertEquals(0, audit.pages.get(MAX_PAGES - 1).recordCount());
        } else {
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    streamer.stream(
                                            guard.executionContext(), request, limits, consumer));
            assertEquals(
                    "A extração Data Export excedeu o limite de 64 páginas sem página terminal"
                            + " vazia.",
                    failure.getMessage());
            assertEquals(0, audit.completed);
            assertNotNull(audit.failure);
            assertEquals(MAX_PAGES, audit.failure.pagesFetched());
            assertEquals(expectedRows, audit.failure.recordsDelivered());
            assertEquals(
                    DataExportFailureCategory.RUNTIME_FAILURE, audit.failure.failureCategory());
            assertEquals(MAX_PAGES, deliveredPages.get());
            assertFalse(audit.pages.stream().anyMatch(page -> page.recordCount() == 0));
            final var terminal = assertThrows(ContractDriftException.class, () -> guard.complete());
            assertEquals(
                    ContractDriftException.Reason.VALIDATION_ALREADY_TERMINAL, terminal.reason());
        }
        assertEquals(1, audit.started);
        assertEquals(completes ? 0 : 1, audit.failed);
        assertEquals(expectedRows, deliveredRows.get());
        assertEquals(MAX_PAGES, audit.pages.size());
        for (int page = 0; page < audit.pages.size(); page++) {
            assertEquals(page + 1, audit.pages.get(page).page());
            assertEquals(PER, audit.pages.get(page).requestedPageSize());
            assertTrue(audit.pages.get(page).distinctEntityCount() <= 5);
        }
    }

    private static ImmutableFingerprint configuration() {
        return new ImmutableFingerprint("fixture-page-cap", "d".repeat(64));
    }

    private static ContractRunGuard guard(final SourceContractRelease release) {
        final var policy =
                ContractCompatibilityPolicy.create(
                        "fixture-page-cap", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, configuration());
        return new ContractRunGuard(
                binding,
                release,
                policy,
                ContractTestSupport.controlPlaneStart(binding),
                ignored -> {});
    }

    private static final class CapturingAudit implements DataExportExtractionAudit {
        private int started;
        private int completed;
        private int failed;
        private ExecutionFailed failure;
        private final List<PageRead> pages = new ArrayList<>();

        @Override
        public void executionStarted(final ExecutionStarted event) {
            started++;
        }

        @Override
        public void pageRead(final PageRead event) {
            pages.add(event);
        }

        @Override
        public void executionCompleted(final DataExportExtractionResult result) {
            completed++;
        }

        @Override
        public void executionFailed(final ExecutionFailed event) {
            failed++;
            failure = event;
        }
    }
}
