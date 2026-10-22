package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RelationalLaboratoryFixturesProgressTest {

    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-04-01T12:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COLETAS", "FRETES"})
    void perOneKeepsBothPhysicalCopiesTogetherAndReachesAnEmptyTerminal(
            final DataExportTemplate template) {
        verifyTraversal(
                template,
                2,
                1,
                true,
                List.of(2, 2),
                List.of(key(template, 7), key(template, 7), key(template, 8), key(template, 8)));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COLETAS", "FRETES"})
    void aShortEntityPagePreservesItsExpansionBeforeTheTerminal(final DataExportTemplate template) {
        verifyTraversal(
                template,
                3,
                2,
                true,
                List.of(4, 2),
                List.of(
                        key(template, 7),
                        key(template, 7),
                        key(template, 8),
                        key(template, 8),
                        key(template, 9),
                        key(template, 9)));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COLETAS", "FRETES"})
    void perOneWithoutExpansionPreservesTheOriginalRootSequence(final DataExportTemplate template) {
        verifyTraversal(
                template, 2, 1, false, List.of(1, 1), List.of(key(template, 7), key(template, 8)));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COLETAS", "FRETES"})
    void anEmptyDatasetStillCompletesWithoutDeliveringAnyPage(final DataExportTemplate template) {
        verifyTraversal(template, 0, 1, true, List.of(), List.of());
    }

    private static void verifyTraversal(
            final DataExportTemplate template,
            final int roots,
            final int per,
            final boolean duplicates,
            final List<Integer> expectedPhysicalCounts,
            final List<String> expectedKeys) {
        final var source =
                RelationalLaboratoryFixtures.source(template, DATE, 7, roots, per, duplicates);
        final var release = source.contractRelease(template);
        final var configuration = new ImmutableFingerprint("fixture-progress", "d".repeat(64));
        final var policy =
                ContractCompatibilityPolicy.create(
                        "fixture-progress-policy", release.contractFingerprint(), List.of());
        final var binding =
                ContractExecutionBinding.create(UUID.randomUUID(), release, policy, configuration);
        final var guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var request =
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(DATE, DATE),
                        Optional.empty(),
                        1,
                        per,
                        template.defaultOrderBy());
        final List<Integer> physicalCounts = new ArrayList<>();
        final List<String> keys = new ArrayList<>();
        final var result =
                new DataExportPageStreamer(
                                source.gateway(template, guard, release, configuration),
                                DataExportExtractionAudit.noop(),
                                CLOCK)
                        .stream(
                                guard.executionContext(),
                                request,
                                new DataExportExtractionLimits(4, 6, per),
                                page -> {
                                    physicalCounts.add(page.response().recordCount());
                                    final List<String> pageKeys =
                                            page.response().records().stream()
                                                    .map(
                                                            row ->
                                                                    row.path(
                                                                                    template
                                                                                            .paginationEntityField())
                                                                            .asText())
                                                    .toList();
                                    assertTrue(pageKeys.stream().distinct().count() <= per);
                                    keys.addAll(pageKeys);
                                });

        assertEquals(expectedPhysicalCounts, physicalCounts);
        assertEquals(expectedKeys, keys);
        assertEquals(expectedKeys.size(), result.recordsDelivered());
        assertEquals(expectedPhysicalCounts.size() + 1, result.pagesFetched());
        assertEquals(result.pagesFetched(), result.terminalPage());
        assertEquals(result.pagesFetched(), source.metrics().fetchedPages());
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
    }

    private static String key(final DataExportTemplate template, final int ordinal) {
        return switch (template) {
            case MANIFESTOS -> Integer.toString(ordinal);
            case COLETAS -> Integer.toString(200_000 + ordinal);
            case FRETES -> Integer.toString(300_000 + ordinal);
            default -> throw new IllegalArgumentException("FIXTURE_PROGRESS_TEMPLATE_DENIED");
        };
    }
}
