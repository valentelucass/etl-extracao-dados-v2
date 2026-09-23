package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuotesSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;

/** Three declared rows: old, current, and a late arrival with the old business date. */
final class QualificationTemporalInputs {
    private QualificationTemporalInputs() {}

    static AnalyticQuotesSyntheticSource quotes(
            final LocalDate current,
            final BusinessDateRange expected,
            final boolean lateAvailable,
            final boolean incomplete,
            final AtomicInteger requests) {
        final var seed = AnalyticQuotesFixtures.data();
        return AnalyticQuotesSyntheticSource.windowed(
                request -> {
                    requests.incrementAndGet();
                    if (!request.businessDateWindow().equals(expected) || request.pageSize() != 2) {
                        throw new IllegalArgumentException("QUAL_TEMPORAL_SOURCE_WINDOW_MISMATCH");
                    }
                    if (incomplete && request.page() == 2) {
                        throw new IllegalArgumentException(
                                "QUAL_TEMPORAL_SYNTHETIC_PAGE_INCOMPLETE");
                    }
                    final var rows = JsonNodeFactory.instance.arrayNode();
                    final int from = Math.multiplyExact(request.page() - 1, request.pageSize());
                    int selected = 0;
                    for (int root = 1; root <= 3; root++) {
                        final var date = root == 2 ? current : current.minusDays(1);
                        if (root == 3 && !lateAvailable
                                || date.isBefore(expected.startInclusive())
                                || date.isAfter(expected.endInclusive())) {
                            continue;
                        }
                        for (int duplicate = 0; duplicate < 3; duplicate++) {
                            if (selected >= from && selected < from + request.pageSize()) {
                                rows.add(
                                        seed.deepCopy()
                                                .put("sequence_code", 80 + root)
                                                .put(
                                                        "requested_at",
                                                        date + "T10:00:00.123456789-03:00")
                                                .put(
                                                        "qoe_qes_fit_fhe_cte_issued_at",
                                                        date + "T11:00:00.123456789-03:00")
                                                .put(
                                                        "qoe_qes_fit_nse_issued_at",
                                                        date + "T12:00:00.123456789-03:00"));
                            }
                            selected++;
                        }
                    }
                    return rows.toString();
                });
    }
}
