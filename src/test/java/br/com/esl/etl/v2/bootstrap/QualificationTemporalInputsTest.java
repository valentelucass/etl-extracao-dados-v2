package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class QualificationTemporalInputsTest {
    private static final LocalDate CURRENT = LocalDate.of(2036, 4, 2);
    private static final BusinessDateRange TWO_DAYS =
            new BusinessDateRange(CURRENT.minusDays(1), CURRENT);

    @Test
    void lateArrivalRetainsItsOldBusinessDateAcrossPhysicalPages() throws Exception {
        final var calls = new AtomicInteger();
        final var source =
                QualificationTemporalInputs.quotes(CURRENT, TWO_DAYS, true, false, calls);
        final var pages = source.bundle(guard(), configuration()).dataGateway();
        final var observed = new java.util.ArrayList<Integer>();
        for (int page = 1; page <= 6; page++) {
            final var response = pages.fetch(request(TWO_DAYS, page, 2));
            response.records().forEach(row -> observed.add(row.path("sequence_code").intValue()));
        }
        assertEquals(List.of(81, 81, 81, 82, 82, 82, 83, 83, 83), observed);
        assertEquals(6, calls.get());
    }

    @Test
    void boundedWindowAndIncompleteSecondPageFailBeforeFalseTerminal() throws Exception {
        final var calls = new AtomicInteger();
        final var source =
                QualificationTemporalInputs.quotes(CURRENT, TWO_DAYS, false, true, calls);
        final var pages = source.bundle(guard(), configuration()).dataGateway();
        assertEquals(2, pages.fetch(request(TWO_DAYS, 1, 2)).recordCount());
        assertEquals(
                "QUAL_TEMPORAL_SYNTHETIC_PAGE_INCOMPLETE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> pages.fetch(request(TWO_DAYS, 2, 2)))
                        .getMessage());
        assertEquals(2, calls.get());

        final var complete =
                QualificationTemporalInputs.quotes(
                        CURRENT, TWO_DAYS, false, false, new AtomicInteger());
        final var other = complete.bundle(guard(), configuration()).dataGateway();
        assertEquals(
                "QUAL_TEMPORAL_SOURCE_WINDOW_MISMATCH",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        other.fetch(
                                                request(
                                                        new BusinessDateRange(CURRENT, CURRENT),
                                                        1,
                                                        2)))
                        .getMessage());
        assertEquals(
                "QUAL_TEMPORAL_SOURCE_WINDOW_MISMATCH",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> other.fetch(request(TWO_DAYS, 1, 3)))
                        .getMessage());
    }

    private static DataExportPageRequest request(
            final BusinessDateRange range, final int page, final int pageSize) {
        return new DataExportPageRequest(
                DataExportTemplate.COTACOES,
                range,
                Optional.empty(),
                page,
                pageSize,
                DataExportTemplate.COTACOES.defaultOrderBy());
    }

    private static ImmutableFingerprint configuration() {
        return new ImmutableFingerprint("synthetic-temporal", "d".repeat(64));
    }

    private static ContractRunGuard guard() {
        final var release = AnalyticQuotesSyntheticSource.release();
        final var policy =
                ContractCompatibilityPolicy.create(
                        "synthetic-temporal-policy", release.contractFingerprint(), List.of());
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
}
