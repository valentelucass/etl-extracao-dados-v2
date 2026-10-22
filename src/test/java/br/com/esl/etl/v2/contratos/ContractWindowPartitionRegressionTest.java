package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Calendar partition and pure Runner composition only; no source client or external fixture. */
class ContractWindowPartitionRegressionTest {
    @Test
    void visitsEachColetaDayExactlyOnceAcrossTheYearBoundary() {
        final var first = day("2026-12-31");
        final var second = day("2027-01-01");
        final var last = day("2027-01-02");
        final List<ContractDateWindow> expected = List.of(first, second, last);
        final List<ContractDateWindow> visited = new ArrayList<>();
        final List<JsonNode> rows = List.of(row(1, first), row(2, second), row(3, last));

        final var result =
                ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                        DataExportTemplate.COLETAS,
                        new ContractDateWindow(first.startInclusive(), last.endInclusive()),
                        pages(rows),
                        selected -> {
                            visited.add(selected);
                            final int index = expected.indexOf(selected);
                            assertTrue(index >= 0);
                            assertEquals(
                                    selected.startInclusive().toString(),
                                    rows.get(index).path("request_date").textValue());
                            return pages(List.of(rows.get(index)));
                        });

        assertEquals(expected, visited);
        assertEquals(rows, result);
    }

    @Test
    void keepsAnEmptyLeapDayBetweenPopulatedDailyPartitions() {
        final var first = day("2028-02-28");
        final var leap = day("2028-02-29");
        final var last = day("2028-03-01");
        final JsonNode expanded = row(1, first);
        final JsonNode later = row(2, last);
        final List<JsonNode> full = List.of(expanded, expanded, later);
        final List<ContractDateWindow> visited = new ArrayList<>();

        final var result =
                ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                        DataExportTemplate.COLETAS,
                        new ContractDateWindow(first.startInclusive(), last.endInclusive()),
                        pages(full),
                        selected -> {
                            visited.add(selected);
                            if (selected.equals(first)) {
                                return pages(List.of(expanded, expanded));
                            }
                            if (selected.equals(leap)) {
                                return pages(List.of());
                            }
                            assertEquals(last, selected);
                            return pages(List.of(later));
                        });

        assertEquals(List.of(first, leap, last), visited);
        assertEquals(full, result);
    }

    @Test
    void aDailyFailureStopsBeforeTheNextDayAndCannotReturnPartialRows() {
        final var first = day("2028-03-30");
        final var second = day("2028-03-31");
        final var last = day("2028-04-01");
        final var refusal = new IllegalStateException("OWN_DAILY_TRAVERSAL_FAILURE");
        final List<ContractDateWindow> visited = new ArrayList<>();
        final var returned = new AtomicBoolean();
        final List<JsonNode> full = List.of(row(1, first), row(2, second), row(3, last));

        final var observed =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                                    DataExportTemplate.COLETAS,
                                    new ContractDateWindow(
                                            first.startInclusive(), last.endInclusive()),
                                    pages(full),
                                    selected -> {
                                        visited.add(selected);
                                        if (selected.equals(first)) {
                                            return pages(List.of(full.get(0)));
                                        }
                                        assertEquals(second, selected);
                                        throw refusal;
                                    });
                            returned.set(true);
                        });

        assertSame(refusal, observed);
        assertEquals(List.of(first, second), visited);
        assertFalse(returned.get());
    }

    @Test
    void freightThirtyDayBlocksRemainContiguousAcrossALeapDayAndRemainder() {
        final var window =
                new ContractDateWindow(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 4, 1));
        final var first =
                new ContractDateWindow(LocalDate.of(2028, 2, 1), LocalDate.of(2028, 3, 1));
        final var second =
                new ContractDateWindow(LocalDate.of(2028, 3, 2), LocalDate.of(2028, 3, 31));
        final var last = day("2028-04-01");
        final var blocks = window.asWindowsOfAtMostDays(30);

        assertEquals(List.of(first, second, last), blocks);
        assertEquals(List.of(first), first.asWindowsOfAtMostDays(30));
        assertEquals(List.of(second), second.asWindowsOfAtMostDays(30));
        assertEquals(
                61,
                blocks.stream()
                        .mapToLong(
                                block ->
                                        ChronoUnit.DAYS.between(
                                                        block.startInclusive(),
                                                        block.endInclusive())
                                                + 1)
                        .sum());
        for (int index = 1; index < blocks.size(); index++) {
            assertEquals(
                    blocks.get(index - 1).endInclusive().plusDays(1),
                    blocks.get(index).startInclusive());
        }
        final List<JsonNode> freightRows =
                List.of(
                        JsonNodeFactory.instance
                                .objectNode()
                                .put("id", 910001)
                                .put("corporation_sequence_number", 710001));
        assertEquals(
                freightRows,
                ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                        DataExportTemplate.FRETES,
                        window,
                        pages(freightRows),
                        selected -> {
                            throw new AssertionError("DAILY_FREIGHT_TRAVERSAL_NOT_EXPECTED");
                        }));
    }

    private static ContractDateWindow day(final String value) {
        final LocalDate date = LocalDate.parse(value);
        return new ContractDateWindow(date, date);
    }

    private static JsonNode row(final int ordinal, final ContractDateWindow selected) {
        return JsonNodeFactory.instance
                .objectNode()
                .put("id", 910000 + ordinal)
                .put("sequence_code", 710000 + ordinal)
                .put("request_date", selected.startInclusive().toString());
    }

    private static List<ContractObservedPage> pages(final List<JsonNode> rows) {
        final var first = new ContractObservedPage(1, 3, rows);
        return rows.isEmpty()
                ? List.of(first)
                : List.of(first, new ContractObservedPage(2, 3, List.of()));
    }
}
