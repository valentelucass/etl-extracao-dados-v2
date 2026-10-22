package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractGate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AnalyticFixturePaginationProgressTest {

    @Test
    void quotationPerOneKeepsThreeCopiesOnTheSamePageAndReachesTheEmptyTerminal() {
        quotation(1, 3, 1);
    }

    @Test
    void quotationPerTwoDoesNotSplitTheRepeatedRoot() {
        quotation(1, 3, 2);
    }

    @Test
    void quotationMaximumPerKeepsTheExistingPhysicalAndByteCaps() {
        quotation(999900, 31, 16);
    }

    @Test
    void collectionPerOneKeepsThreeCopiesOnTheSamePageAndReachesTheEmptyTerminal() {
        collection(1, 3, 1);
    }

    @Test
    void collectionPerTwoDoesNotSplitTheRepeatedRoot() {
        collection(1, 3, 2);
    }

    @Test
    void collectionMaximumPerKeepsTheExistingPhysicalAndByteCaps() {
        collection(8162, 31, 16);
    }

    @Test
    void zeroRootsInEitherFixtureProducesOnlyTheEmptyTerminalPage() {
        quotation(1, 0, 1);
        collection(1, 0, 1);
    }

    @Test
    void preservesScopeCapsAndRejectsOverflowingCollectionScope() {
        assertThrows(
                IllegalArgumentException.class, () -> AnalyticQuotesFixtures.source(1, 2049, 1));
        assertThrows(IllegalArgumentException.class, () -> AnalyticQuotesFixtures.source(1, 1, 17));
        assertThrows(
                IllegalArgumentException.class,
                () -> AnalyticCollectionsFixtures.source(1, 4097, 1));
        assertThrows(
                IllegalArgumentException.class, () -> AnalyticCollectionsFixtures.source(1, 1, 17));
        assertThrows(
                IllegalArgumentException.class,
                () -> AnalyticCollectionsFixtures.source(Integer.MAX_VALUE, 1, 1));
    }

    private static void quotation(final int first, final int roots, final int per) {
        final var source = AnalyticQuotesFixtures.source(first, roots, per);
        final var release = source.contractRelease();
        final var guard = guard(release);
        final var gateway =
                DataExportContractGate.enforce(
                                source.bundle(guard, configuration()),
                                DataExportTemplate.COTACOES,
                                guard)
                        .dataGateway();
        guard.validateMetadata(release.metadata());
        final var trace = stream(DataExportTemplate.COTACOES, gateway, guard, roots, per);
        assertEquals(trace.pages(), source.metrics().fetchedPages());
        assertTrue(source.metrics().maximumPageBytes() <= 65536);
        for (int row = 0; row < trace.rows().size(); row++) {
            assertEquals(
                    AnalyticQuotesFixtures.data().put("sequence_code", first + row / 3),
                    trace.rows().get(row));
        }
    }

    private static void collection(final int first, final int roots, final int per) {
        final var source = AnalyticCollectionsFixtures.source(first, roots, per);
        final var release = source.contractRelease(DataExportTemplate.COLETAS);
        final var guard = guard(release);
        final var gateway =
                source.gateway(DataExportTemplate.COLETAS, guard, release, configuration());
        final var trace = stream(DataExportTemplate.COLETAS, gateway, guard, roots, per);
        assertEquals(trace.pages(), source.metrics().fetchedPages());
        assertTrue(source.metrics().maximumPageBytes() <= 65536);
        for (int row = 0; row < trace.rows().size(); row++) {
            final int root = first + row / 3;
            assertEquals(
                    AnalyticCollectionsFixtures.data()
                            .put("id", 200000 + root)
                            .put("sequence_code", 100000 + root)
                            .put("synthetic_item_key", root),
                    trace.rows().get(row));
        }
    }

    private static Trace stream(
            final DataExportTemplate template,
            final DataExportGateway gateway,
            final ContractRunGuard guard,
            final int roots,
            final int per) {
        final int rootsPerPage = Math.max(1, per / 3);
        final int dataPages = (roots + rootsPerPage - 1) / rootsPerPage;
        final LocalDate date =
                template == DataExportTemplate.COLETAS
                        ? LocalDate.parse(
                                AnalyticCollectionsFixtures.data().path("request_date").asText())
                        : LocalDate.of(2036, 4, 1);
        final var rows = new ArrayList<JsonNode>();
        final var pageRows = new ArrayList<Integer>();
        final var result =
                new DataExportPageStreamer(
                                gateway, DataExportExtractionAudit.noop(), Clock.systemUTC())
                        .stream(
                                guard.executionContext(),
                                new DataExportPageRequest(
                                        template,
                                        new BusinessDateRange(date, date),
                                        Optional.empty(),
                                        1,
                                        per,
                                        template.defaultOrderBy()),
                                new DataExportExtractionLimits(
                                        dataPages + 1, Math.max(1, roots * 3L), per),
                                read -> {
                                    final var page = read.response().records();
                                    pageRows.add(page.size());
                                    assertEquals(0, page.size() % 3);
                                    assertTrue(page.size() <= Math.max(3, per));
                                    for (int index = 0; index < page.size(); index += 3) {
                                        assertEquals(page.get(index), page.get(index + 1));
                                        assertEquals(page.get(index), page.get(index + 2));
                                    }
                                    rows.addAll(page);
                                });
        assertEquals(dataPages + 1, result.pagesFetched());
        assertEquals(dataPages + 1, result.terminalPage());
        assertEquals(roots * 3L, result.recordsDelivered());
        assertEquals(roots * 3, rows.size());
        assertEquals(dataPages, pageRows.size());
        if (roots > 0) {
            assertEquals(Math.min(roots, rootsPerPage) * 3, pageRows.get(0));
        }
        return new Trace(result.pagesFetched(), rows);
    }

    private static ImmutableFingerprint configuration() {
        return new ImmutableFingerprint("fixture-progress", "d".repeat(64));
    }

    private static ContractRunGuard guard(final SourceContractRelease release) {
        final var policy =
                ContractCompatibilityPolicy.create(
                        "fixture-progress", release.contractFingerprint(), List.of());
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

    private record Trace(int pages, List<JsonNode> rows) {}
}
