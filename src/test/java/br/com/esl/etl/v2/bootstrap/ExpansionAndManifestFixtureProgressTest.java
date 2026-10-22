package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportReadPage;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExpansionAndManifestFixtureProgressTest {

    private static final LocalDate DATE = AnalyticScenarioFixtures.DATE;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-04-01T12:00:00Z"), ZoneOffset.UTC);
    private static final ImmutableFingerprint CONFIGURATION =
            new ImmutableFingerprint("fixture-progress", "d".repeat(64));

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void dependencyPerOneKeepsCopiesTogetherAndDeliversAllRoots(final DataExportTemplate template) {
        final var source = ExpansionDependencyFixtures.source(template, 7, 2, 1);
        final var release = source.contractRelease(template);
        final var guard = guard(release);
        final var pages =
                stream(
                        source.gateway(template, guard, release, CONFIGURATION),
                        guard,
                        template,
                        1,
                        4);

        assertEquals(List.of(2, 2), physicalCounts(pages));
        assertEquals(
                template == DataExportTemplate.FRETES
                        ? List.of("300007", "300007", "300008", "300008")
                        : List.of("600007", "600007", "600008", "600008"),
                keys(pages, template));
        assertEquals(3, source.metrics().fetchedPages());
        assertTrue(source.metrics().maximumPageBytes() <= 65_536);
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void dependencyMaximumPerPreservesItsPhysicalBudgetAndCompleteVolume(
            final DataExportTemplate template) {
        final var source = ExpansionDependencyFixtures.source(template, 1949, 100, 100);
        final var release = source.contractRelease(template);
        final var guard = guard(release);
        final var pages =
                stream(
                        source.gateway(template, guard, release, CONFIGURATION),
                        guard,
                        template,
                        100,
                        200);

        assertEquals(List.of(100, 100), physicalCounts(pages));
        final var observed = keys(pages, template);
        assertEquals(100, observed.stream().distinct().count());
        for (int root = 1949; root <= 2048; root++) {
            final String expected =
                    Integer.toString(
                            (template == DataExportTemplate.FRETES ? 300000 : 600000) + root);
            assertEquals(2, observed.stream().filter(expected::equals).count());
        }
        assertTrue(source.metrics().maximumPageBytes() <= 65_536);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void manifestSmallPerKeepsItsThreeCopiesTogether(final int per) {
        final var source = AnalyticScenarioFixtures.manifests(7, 2, per, 1, false);
        final var release = source.contractRelease(DataExportTemplate.MANIFESTOS);
        final var guard = guard(release);
        final var pages =
                stream(
                        source.gateway(
                                DataExportTemplate.MANIFESTOS, guard, release, CONFIGURATION),
                        guard,
                        DataExportTemplate.MANIFESTOS,
                        per,
                        6);

        assertEquals(List.of(3, 3), physicalCounts(pages));
        assertEquals(
                List.of("7", "7", "7", "8", "8", "8"), keys(pages, DataExportTemplate.MANIFESTOS));
        assertTrue(source.metrics().maximumPageBytes() <= 65_536);
    }

    @Test
    void manifestMaximumPerRetainsTheExistingPhysicalAndByteBounds() {
        final var source = AnalyticScenarioFixtures.manifests(7, 16, 16, 1, false);
        final var release = source.contractRelease(DataExportTemplate.MANIFESTOS);
        final var guard = guard(release);
        final var pages =
                stream(
                        source.gateway(
                                DataExportTemplate.MANIFESTOS, guard, release, CONFIGURATION),
                        guard,
                        DataExportTemplate.MANIFESTOS,
                        16,
                        48);

        assertEquals(List.of(15, 15, 15, 3), physicalCounts(pages));
        final var observed = keys(pages, DataExportTemplate.MANIFESTOS);
        assertEquals(16, observed.stream().distinct().count());
        for (int root = 7; root <= 22; root++) {
            final String expected = Integer.toString(root);
            assertEquals(3, observed.stream().filter(expected::equals).count());
        }
        assertTrue(source.metrics().maximumPageBytes() <= 65_536);
    }

    @Test
    void manifestCorrectionPreservesItsRevisionAndBusinessFieldsOnAllCopies() {
        final var source = AnalyticScenarioFixtures.manifests(7, 1, 1, 2, true);
        final var release = source.contractRelease(DataExportTemplate.MANIFESTOS);
        final var guard = guard(release);
        final var pages =
                stream(
                        source.gateway(
                                DataExportTemplate.MANIFESTOS, guard, release, CONFIGURATION),
                        guard,
                        DataExportTemplate.MANIFESTOS,
                        1,
                        3);

        assertEquals(List.of(3), physicalCounts(pages));
        for (final var row : pages.get(0).response().records()) {
            assertEquals("2036-04-02T12:00:00.000000002Z", row.path("finished_at").asText());
            assertEquals("2036-04-02T12:00:00Z", row.path("departured_at").asText());
            assertEquals("SYNTHETIC BRANCH B", row.path("mft_crn_psn_nickname").asText());
            assertEquals(100007, row.path("mft_pfs_pck_sequence_code").asInt());
            assertEquals("1".repeat(40) + "0007", row.path("mft_mfs_key").asText());
        }
    }

    private static List<DataExportReadPage> stream(
            final DataExportGateway gateway,
            final ContractRunGuard guard,
            final DataExportTemplate template,
            final int per,
            final int physicalRows) {
        final List<DataExportReadPage> pages = new ArrayList<>();
        final var result =
                new DataExportPageStreamer(gateway, DataExportExtractionAudit.noop(), CLOCK)
                        .stream(
                                guard.executionContext(),
                                new DataExportPageRequest(
                                        template,
                                        new BusinessDateRange(DATE, DATE),
                                        Optional.empty(),
                                        1,
                                        per,
                                        template.defaultOrderBy()),
                                new DataExportExtractionLimits(10, physicalRows, per),
                                page -> {
                                    assertTrue(
                                            keys(List.of(page), template).stream()
                                                            .distinct()
                                                            .count()
                                                    <= per);
                                    pages.add(page);
                                });
        assertEquals(physicalRows, result.recordsDelivered());
        assertEquals(pages.size() + 1, result.pagesFetched());
        assertEquals(result.pagesFetched(), result.terminalPage());
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
        return pages;
    }

    private static List<Integer> physicalCounts(final List<DataExportReadPage> pages) {
        return pages.stream().map(page -> page.response().recordCount()).toList();
    }

    private static List<String> keys(
            final List<DataExportReadPage> pages, final DataExportTemplate template) {
        return pages.stream()
                .flatMap(page -> page.response().records().stream())
                .map(row -> row.path(template.paginationEntityField()).asText())
                .toList();
    }

    private static ContractRunGuard guard(final SourceContractRelease release) {
        final var policy =
                ContractCompatibilityPolicy.create(
                        "fixture-progress-policy", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(UUID.randomUUID(), release, policy, CONFIGURATION);
        return new ContractRunGuard(
                binding,
                release,
                policy,
                ContractTestSupport.controlPlaneStart(binding),
                ignored -> {});
    }
}
