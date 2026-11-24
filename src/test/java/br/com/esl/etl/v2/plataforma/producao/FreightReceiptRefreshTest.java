package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotExtractor;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FreightReceiptRefreshTest {
    private final LocalDate today = LocalDate.of(2026, 11, 24);
    private final LocalDate floor = today.minusDays(90);
    private final Instant cutoff = Instant.parse("2026-11-24T16:00:00Z");
    private final ObjectNode state = CurlSnapshotWire.JSON.createObjectNode();
    private final FreightReceiptRefresh receipts = new FreightReceiptRefresh(state);

    private ObjectNode event(LocalDate day, Instant time) {
        return CurlSnapshotWire.JSON
                .createObjectNode()
                .put("ice_ore_code", 114)
                .put("ice_fit_service_at", day + "T13:00:00-03:00")
                .put("occurrence_at", time.toString());
    }

    @Test
    void aFreshUpdateWithABackdatedOccurrenceTriggersANewFreightRead() {
        var day = today.minusDays(10);
        receipts.confirmedFreightRead(day, cutoff.minusSeconds(600));
        var oldEvent = event(day, cutoff.minusSeconds(800));
        assertFalse(receipts.observe(oldEvent, floor, today, null));
        assertTrue(receipts.observe(oldEvent, floor, today, cutoff));
        receipts.confirmedFreightRead(day, cutoff.minusSeconds(300));
        assertEquals(List.of(day), receipts.nextDays(2));
        receipts.confirmedFreightRead(day, cutoff.plusSeconds(60));
        assertEquals(0, receipts.pendingCount());
    }

    @Test
    void additionalNotesKeepTheOriginalQueuePositionAndConsumeOneFreightRead() {
        LocalDate first = today.minusDays(4), second = today.minusDays(7);
        receipts.observe(event(first, cutoff.minusSeconds(10)), floor, today, null);
        receipts.observe(event(second, cutoff.minusSeconds(8)), floor, today, null);
        receipts.observe(event(first, cutoff), floor, today, null);
        assertEquals(List.of(first), receipts.nextDays(1));
        assertEquals(2, receipts.pendingCount());
        assertFalse(receipts.observe(event(today.plusDays(1), cutoff), floor, today, null));
        assertFalse(receipts.observe(event(floor.minusDays(1), cutoff), floor, today, null));
        assertFalse(
                receipts.observe(
                        event(today, cutoff).put("ice_ore_code", 110), floor, today, null));
    }

    @Test
    void paginationCanResumeAfterTheBatchBudgetAndAnInterruptedRead() throws Exception {
        var pages = new ArrayList<Integer>();
        var failing = new boolean[] {true};
        var wire =
                new ReportSnapshotExtractor.Wire() {
                    public JsonNode info(ReportSnapshotTemplate t) {
                        throw new AssertionError();
                    }

                    public JsonNode page(
                            ReportSnapshotTemplate t, BusinessDateRange d, int p, int n) {
                        throw new AssertionError();
                    }

                    public JsonNode receiptPage(
                            BusinessDateRange history,
                            SourceDateTimeRange interval,
                            int page,
                            int per) {
                        pages.add(page);
                        if (page == 17 && failing[0]) {
                            throw new IllegalStateException("synthetic interruption");
                        }
                        var data = CurlSnapshotWire.JSON.createArrayNode();
                        if (page < 18) {
                            data.add(event(today.minusDays(11), cutoff.minusSeconds(600)));
                        }
                        return CurlSnapshotWire.JSON.createObjectNode().set("data", data);
                    }
                };
        receipts.discover(wire, floor, today, cutoff, () -> {});
        assertEquals(17, state.required("window").required("page").asInt());
        assertFalse(state.has("through"));
        assertThrows(
                IllegalStateException.class,
                () -> receipts.discover(wire, floor, today, cutoff, () -> {}));
        assertEquals(17, state.required("window").required("page").asInt());
        var resumed = new FreightReceiptRefresh(state.deepCopy());
        failing[0] = false;
        resumed.discover(wire, floor, today, cutoff, () -> {});
        assertEquals(List.of(16, 17, 17, 18), pages.subList(15, pages.size()));
        assertEquals(1, resumed.pendingCount());
        int completed = pages.size();
        resumed.discover(wire, floor, today, cutoff, () -> {});
        assertEquals(completed, pages.size());
    }
}
