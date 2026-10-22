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
import com.fasterxml.jackson.databind.JsonNode;
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

class QualificationVariantInputsProgressTest {

    private static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-04-01T12:00:00Z"), ZoneOffset.UTC);

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void perOneKeepsExactCopiesTogetherAndRetainsTheRepresentativePatch(
            final DataExportTemplate template) {
        verifyTraversal(template, 7, 2, 1, List.of(2, 2));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void aShortFinalPageKeepsItsLastRootWhole(final DataExportTemplate template) {
        verifyTraversal(template, 7, 3, 4, List.of(4, 2));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void maximumRootWindowStaysInsideTheOriginalPhysicalPageBudget(
            final DataExportTemplate template) {
        verifyTraversal(template, 1, 32, 16, List.of(16, 16, 16, 16));
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"FRETES", "LOCALIZACAO_CARGAS"})
    void anEmptyWindowStillCompletesWithoutDeliveringAPage(final DataExportTemplate template) {
        verifyTraversal(template, 7, 0, 1, List.of());
    }

    private static void verifyTraversal(
            final DataExportTemplate template,
            final int first,
            final int roots,
            final int per,
            final List<Integer> expectedPhysicalCounts) {
        final var source = QualificationVariantInputs.source(template, first, roots, per);
        final var release = source.contractRelease(template);
        final var configuration =
                new ImmutableFingerprint("variant-source-progress", "d".repeat(64));
        final var policy =
                ContractCompatibilityPolicy.create(
                        "variant-source-progress-policy", release.contractFingerprint(), List.of());
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
        final List<String> observedKeys = new ArrayList<>();
        final var result =
                new DataExportPageStreamer(
                                source.gateway(template, guard, release, configuration),
                                DataExportExtractionAudit.noop(),
                                CLOCK)
                        .stream(
                                guard.executionContext(),
                                request,
                                new DataExportExtractionLimits(
                                        roots + 1, Math.max(1, roots * 2), per),
                                page -> {
                                    final var rows = page.response().records();
                                    physicalCounts.add(rows.size());
                                    assertTrue(rows.size() <= Math.max(2, per));
                                    assertTrue(
                                            rows.stream()
                                                            .map(
                                                                    row ->
                                                                            row.path(
                                                                                            template
                                                                                                    .paginationEntityField())
                                                                                    .asText())
                                                            .distinct()
                                                            .count()
                                                    <= per);
                                    for (int index = 0; index < rows.size(); index += 2) {
                                        assertEquals(rows.get(index), rows.get(index + 1));
                                        assertPatch(template, rows.get(index));
                                        observedKeys.add(
                                                rows.get(index)
                                                        .path(template.paginationEntityField())
                                                        .asText());
                                    }
                                });

        final List<String> expectedKeys = new ArrayList<>();
        for (int ordinal = first; ordinal < first + roots; ordinal++) {
            expectedKeys.add(
                    Integer.toString(
                            (template == DataExportTemplate.FRETES ? 300000 : 600000) + ordinal));
        }
        assertEquals(expectedPhysicalCounts, physicalCounts);
        assertEquals(expectedKeys, observedKeys);
        assertEquals(roots * 2, result.recordsDelivered());
        assertEquals(expectedPhysicalCounts.size() + 1, result.pagesFetched());
        assertEquals(result.pagesFetched(), result.terminalPage());
        assertEquals(result.pagesFetched(), source.metrics().fetchedPages());
        assertTrue(source.metrics().maximumPageBytes() <= 65536);
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
    }

    private static void assertPatch(final DataExportTemplate template, final JsonNode row) {
        if (template == DataExportTemplate.FRETES) {
            assertEquals("2036-04-01T08:15:30.1234567-03:00", row.path("criado_em").asText());
            assertEquals("2036-04-03T10:00:00-03:00", row.path("finished_at").asText());
            assertEquals(
                    "2036-04-03T09:00:00-03:00",
                    row.path("fit_dpn_performance_finished_at").asText());
        } else {
            assertEquals("Entrega urbana", row.path("service_type").asText());
            assertEquals("FILIAL EMISSORA SINTÉTICA", row.path("fit_crn_psn_nickname").asText());
            assertEquals(
                    "2036-04-04T10:00:00-03:00",
                    row.path("fit_dpn_delivery_prediction_at").asText());
            assertEquals("REGIÃO DESTINO SINTÉTICA", row.path("fit_dyn_name").asText());
            assertEquals("FILIAL DESTINO SINTÉTICA", row.path("fit_dyn_drt_nickname").asText());
            assertEquals("CLASSIFICAÇÃO SINTÉTICA", row.path("fit_fsn_name").asText());
            assertEquals("REGIÃO ORIGEM SINTÉTICA", row.path("fit_o_n_name").asText());
            assertEquals("FILIAL ORIGEM SINTÉTICA", row.path("fit_o_n_drt_nickname").asText());
            assertEquals("PÁTIO SINTÉTICO", row.path("fit_fln_cln_nickname").asText());
        }
    }
}
