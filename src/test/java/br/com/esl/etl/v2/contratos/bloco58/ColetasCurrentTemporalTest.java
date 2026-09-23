package br.com.esl.etl.v2.contratos.bloco58;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.bloco58.ColetasCharacterization.Fault;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportColetasContractCatalog;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/** B63: original ROOT_ARRAY through the existing bounded B58 pipeline; no SQL or source. */
class ColetasCurrentTemporalTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @ParameterizedTest
    @CsvSource({
        "done,Coletada,true,Coleta Realizada",
        "finished,Finalizada,true,Coleta Realizada",
        "canceled,Cancelada,true,motivo sintetico",
        "cancelled,Cancelada,true,motivo sintetico",
        "pending,Pendente,false,motivo sintetico",
        "future,NULL,false,motivo sintetico"
    })
    void currentReleasePreservesStatusAndCivilFreshnessWithoutStatusTimestamp(
            final String status, final String label, final boolean terminal, final String action) {
        final String row =
                "{\"id\":1,\"status\":\""
                        + status
                        + "\",\"finish_date\":\"2018-11-04\",\"updated_at\":\"2099-01-01T00:00:00Z\","
                        + "\"cancellation_reason\":\"motivo sintetico\"}";
        final var report =
                run(
                        List.of("[" + row + "]", "[]"),
                        1,
                        3,
                        20,
                        (page, index) ->
                                JSON.createObjectNode()
                                        .put("/quarantine", "NONE")
                                        .put("/status/label", label)
                                        .put("/status/terminal", Boolean.toString(terminal))
                                        .put("/status/action", action)
                                        .put("/status_updated_at/presence", "ABSENT")
                                        .put("/status_updated_at/mappedPresence", "ABSENT")
                                        .put("/freshness/raw", "NULL")
                                        .put("/freshness/origin", "FINISH_DATE")
                                        .put("/freshness/typed", "2018-11-04T03:00:00Z")
                                        .put("/updated_at/preserved", "\"2099-01-01T00:00:00Z\""));
        report.expect(Layer.NONE, 1, true);
        assertTrue(report.matches(), report::toString);
        assertFalse(report.sanitized().path("snapshotProven").booleanValue());
        assertEquals("NOT_EXECUTED", report.sanitized().path("providerEvidence").textValue());
    }

    @Test
    void expandedRepeatedRowsAndReversedStatusOrderReachStagingUnreduced() {
        for (final List<String> statuses :
                List.of(List.of("done", "pending"), List.of("pending", "done"))) {
            final String first = row(statuses.get(0));
            final String second = row(statuses.get(1));
            final var report =
                    run(
                            List.of("[" + first + "," + first + "]", "[" + second + "]", "[]"),
                            1,
                            3,
                            10,
                            (page, index) ->
                                    JSON.createObjectNode()
                                            .put("/id/typed", "INTEGER:1")
                                            .put("/status/code", statuses.get(page - 1))
                                            .put(
                                                    "/status/terminal",
                                                    Boolean.toString(
                                                            statuses.get(page - 1).equals("done")))
                                            .put("/freshness/typed", "2026-09-09T03:00:00Z")
                                            .put("/status_updated_at/presence", "ABSENT")
                                            .put("/finish_date/preserved", "\"2026-09-09\""));
            report.expect(Layer.NONE, 3, true);
            assertTrue(report.matches(), report::toString);
            assertEquals(3, report.pagesRead());
        }
        // Deliberately no winner assertion: V004/V010 own conflict/dedupe, not this staging sink.
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"invalid\"", "42"})
    void statusTimestampIsNeverAdmittedByCurrentReleaseEvenIfNull(final String value) {
        final var report =
                run(
                        List.of("[{\"id\":1,\"status_updated_at\":" + value + "}]"),
                        1,
                        1,
                        10,
                        (page, index) -> expected());
        report.expect(Layer.CONTRACT, 0, false);
        assertTrue(report.matches(), report::toString);
    }

    @ParameterizedTest
    @ValueSource(strings = {"42", "false", "{}", "[]"})
    void invalidBusinessDateWireTypeIsRefusedBeforeMapper(final String value) {
        final var report =
                run(
                        List.of("[{\"id\":1,\"finish_date\":" + value + "}]"),
                        1,
                        1,
                        10,
                        (page, index) -> expected());
        assertEquals(0, report.stagedRows());
        assertTrue(report.refusal() == Layer.CONTRACT || report.refusal() == Layer.PARSER);
        assertFalse(report.sanitized().path("localTerminal").booleanValue());
    }

    @Test
    void localEnvelopeCannotMasqueradeAsCurrentRootArray() {
        final var report = run(List.of("{\"data\":[]}"), 1, 1, 10, (page, index) -> expected());
        assertEquals(0, report.stagedRows());
        assertTrue(report.refusal() != Layer.NONE);
        assertFalse(report.sanitized().path("localTerminal").booleanValue());
    }

    @Test
    void distinctIdsEnforcePerDespiteSmallPhysicalPage() {
        final var report =
                run(List.of("[{\"id\":1},{\"id\":2}]"), 1, 1, 10, (page, index) -> expected());
        report.expect(Layer.PAGE_LIMIT, 0, false);
        assertTrue(report.matches(), report::toString);
    }

    @Test
    void capturedPagesOrPageCapCannotFabricateTerminal() {
        for (final int cap : List.of(1, 3)) {
            final var report =
                    run(List.of("[" + row("done") + "]"), 1, cap, 10, (page, index) -> expected());
            assertEquals(1, report.stagedRows());
            assertTrue(report.refusal() != Layer.NONE);
            assertFalse(report.sanitized().path("localTerminal").booleanValue());
            assertFalse(report.sanitized().path("snapshotProven").booleanValue());
        }
    }

    @Test
    void expectationCannotMistakeFallbackForStatusTimestampParity() {
        final var report =
                run(
                        List.of("[" + row("done") + "]", "[]"),
                        1,
                        2,
                        10,
                        (page, index) ->
                                JSON.createObjectNode()
                                        .put("/status_updated_at/presence", "VALUE")
                                        .put("/freshness/origin", "STATUS_UPDATED_AT"));
        assertEquals(1, report.stagedRows());
        assertFalse(report.matches());
        assertEquals(2, report.sanitized().path("differences").size());
        assertFalse(report.sanitized().path("snapshotProven").booleanValue());
    }

    private static LocalCharacterization.Report run(
            final List<String> pages,
            final int per,
            final int maximumPages,
            final int maximumRows,
            final BiFunction<Integer, Integer, JsonNode> expected) {
        return ColetasCharacterization.pipeline(
                number -> {
                    if (number > pages.size()) {
                        throw new IOException("CAPTURE_LIMIT");
                    }
                    return new ByteArrayInputStream(
                            pages.get(number - 1).getBytes(StandardCharsets.UTF_8));
                },
                DataExportColetasContractCatalog.release(),
                per,
                maximumPages,
                maximumRows,
                Fault.NONE,
                expected);
    }

    private static JsonNode expected() {
        return JSON.createObjectNode()
                .put("/quarantine", "NONE")
                .put("/freshness/origin", "FINISH_DATE");
    }

    private static String row(final String status) {
        return "{\"id\":1,\"status\":\"" + status + "\",\"finish_date\":\"2026-09-09\"}";
    }
}
