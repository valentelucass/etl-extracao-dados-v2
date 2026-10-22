package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** Exercita a ligação pura ao Runner; os callbacks apenas devolvem páginas sintéticas em RAM. */
class ContractRemoteEvidenceWindowBridgeTest {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void visitsEachSelectedDayAndKeepsReorderedExpandedDailyRows() throws Exception {
        final JsonNode first = row("{\"id\":900001,\"sequence_code\":700001}");
        final JsonNode second = row("{\"id\":900002,\"sequence_code\":700002}");
        final ContractDateWindow window = window(1, 2);
        final List<ContractDateWindow> visited = new ArrayList<>();
        final List<JsonNode> result =
                ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                        DataExportTemplate.COLETAS,
                        window,
                        pages(first, first, second),
                        day -> {
                            visited.add(day);
                            return day.startInclusive().getDayOfMonth() == 1
                                    ? pages(second, second)
                                    : pages(first);
                        });

        assertTrue(visited.equals(window.asDailyWindows()));
        assertTrue(result.equals(List.of(second, second, first)));
    }

    @Test
    void refusesTheDailyRowsBeforeReturningWhenTheFullWindowHasDifferentIds() throws Exception {
        final ContractDateWindow window = window(1, 2);
        final List<ContractDateWindow> visited = new ArrayList<>();
        final List<ContractObservedPage> full =
                pages(
                        row("{\"id\":900001,\"sequence_code\":700001}"),
                        row("{\"id\":900002,\"sequence_code\":700002}"));
        final List<ContractObservedPage> other =
                pages(
                        row("{\"id\":910001,\"sequence_code\":700001}"),
                        row("{\"id\":910002,\"sequence_code\":700002}"));
        final AtomicBoolean returned = new AtomicBoolean();
        final IllegalStateException failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                                    DataExportTemplate.COLETAS,
                                    window,
                                    full,
                                    day -> {
                                        visited.add(day);
                                        return other;
                                    });
                            returned.set(true);
                        });

        assertEquals("COLETAS_FULL_WINDOW_DAILY_IDENTITY_MISMATCH", failure.getMessage());
        assertTrue(visited.equals(window.asDailyWindows()));
        assertFalse(returned.get());
    }

    @Test
    void keepsASingleColetaDayWithoutFetchingDailyAgain() throws Exception {
        final JsonNode record = row("{\"id\":900001,\"sequence_code\":700001}");
        final List<JsonNode> result =
                ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                        DataExportTemplate.COLETAS,
                        window(1, 1),
                        pages(record),
                        day -> {
                            throw new AssertionError("DAILY_TRAVERSAL_NOT_EXPECTED");
                        });

        assertTrue(result.equals(List.of(record)));
    }

    @Test
    void keepsFreightWindowsWithoutFetchingDaily() throws Exception {
        final JsonNode record = row("{\"id\":900001,\"corporation_sequence_number\":700001}");
        final List<JsonNode> result =
                ContractRemoteEvidenceRunner.dataExportRowsForIdentity(
                        DataExportTemplate.FRETES,
                        window(1, 2),
                        pages(record),
                        day -> {
                            throw new AssertionError("DAILY_TRAVERSAL_NOT_EXPECTED");
                        });

        assertTrue(result.equals(List.of(record)));
    }

    private static ContractDateWindow window(final int first, final int last) {
        return new ContractDateWindow(LocalDate.of(2026, 9, first), LocalDate.of(2026, 9, last));
    }

    private static List<ContractObservedPage> pages(final JsonNode... rows) {
        return List.of(
                new ContractObservedPage(1, 2, List.of(rows)),
                new ContractObservedPage(2, 2, List.of()));
    }

    private JsonNode row(final String value) throws Exception {
        return json.readTree(value);
    }
}
