package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.CharacterizationFixtures.input;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.contratos.bloco58.UsuariosCharacterization.Fault;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

class UsuariosTraversalCharacterizationTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, JsonNode> REPORTS = new TreeMap<>();

    static String envelope(final String nodes, final boolean next, final String cursor) {
        return "{\"data\":{\"individual\":{\"edges\":[{\"node\":"
                + nodes
                + "}],\"pageInfo\":{\"hasNextPage\":"
                + next
                + ",\"endCursor\":"
                + cursor
                + "}}}}";
    }

    private static JsonNode expected() {
        return JSON.createObjectNode()
                .put("/quarantine", "NONE")
                .put("/id/typed", "INTEGER:0")
                .put("/name/presence", "ABSENT");
    }

    private static void check(
            final String id,
            final LocalCharacterization.Report report,
            final Layer refusal,
            final int staged,
            final boolean terminal) {
        report.expect(refusal, staged, terminal);
        REPORTS.put(id, report.sanitized());
        assertTrue(report.matches(), report::toString);
    }

    @ParameterizedTest
    @EnumSource(Fault.class)
    void cancellationAndStageFailuresKeepIncompleteUnusable(final Fault fault) {
        final var result =
                UsuariosCharacterization.pipeline(
                        page ->
                                input(
                                        envelope(
                                                "{\"id\":0}",
                                                page == 1,
                                                page == 1 ? "\"SYNTH_CURSOR_A\"" : "null")),
                        3,
                        40,
                        fault,
                        (page, row) -> expected());
        final Layer refusal =
                switch (fault) {
                    case NONE -> Layer.NONE;
                    case STAGE_FIRST, STAGE_SECOND -> Layer.STAGING;
                    default -> Layer.CANCELLED;
                };
        final int staged =
                switch (fault) {
                    case NONE -> 2;
                    case CANCEL_AFTER_STAGE, STAGE_SECOND -> 1;
                    default -> 0;
                };
        check("SYNTH_" + fault.name(), result, refusal, staged, fault == Fault.NONE);
    }

    @Test
    void twentyNodesAreOneSynchronousBatchAndShortPageContinues() {
        final String edge = "{\"node\":{\"id\":0}}";
        final String full =
                "{\"data\":{\"individual\":{\"edges\":["
                        + (edge + ",").repeat(19)
                        + edge
                        + "],\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":\"SYNTH_CURSOR_A\"}}}}";
        final var report =
                UsuariosCharacterization.pipeline(
                        page -> input(page == 1 ? full : envelope("{\"id\":0}", false, "null")),
                        2,
                        21,
                        Fault.NONE,
                        (page, row) -> expected());
        check("SYNTH_TWENTY_THEN_SHORT", report, Layer.NONE, 21, true);
        assertEquals(2, report.pagesRead());
    }

    @Test
    void repeatedAndCyclicCursorAndCapsNeverComplete() {
        final var repeated =
                UsuariosCharacterization.pipeline(
                        page -> input(envelope("{\"id\":0}", true, "\"SYNTH_CURSOR_A\"")),
                        4,
                        100,
                        Fault.NONE,
                        (page, row) -> expected());
        check("SYNTH_REPEATED_CURSOR", repeated, Layer.TRAVERSAL, 1, false);
        final var cycle =
                UsuariosCharacterization.pipeline(
                        page ->
                                input(
                                        envelope(
                                                "{\"id\":0}",
                                                true,
                                                page == 2
                                                        ? "\"SYNTH_CURSOR_B\""
                                                        : "\"SYNTH_CURSOR_A\"")),
                        4,
                        100,
                        Fault.NONE,
                        (page, row) -> expected());
        check("SYNTH_CYCLIC_CURSOR", cycle, Layer.TRAVERSAL, 2, false);
        final var cap =
                UsuariosCharacterization.pipeline(
                        page -> input(envelope("{\"id\":0}", true, "\"SYNTH_CURSOR_A\"")),
                        1,
                        20,
                        Fault.NONE,
                        (page, row) -> expected());
        check("SYNTH_PAGE_CAP", cap, Layer.TRAVERSAL, 0, false);
        final var rowCap =
                UsuariosCharacterization.pipeline(
                        page ->
                                input(
                                        envelope(
                                                "{\"id\":0}",
                                                page == 1,
                                                page == 1 ? "\"SYNTH_CURSOR_A\"" : "null")),
                        2,
                        1,
                        Fault.NONE,
                        (page, row) -> expected());
        check("SYNTH_NODE_CAP", rowCap, Layer.TRAVERSAL, 1, false);
    }

    static Stream<Arguments> malformed() {
        final String valid = envelope("{\"id\":0}", false, "null");
        final String edge = "{\"node\":{\"id\":0}}";
        return Stream.of(
                Arguments.of(
                        "SYNTH_ERRORS_PARTIAL",
                        valid.replace(
                                "{\"data\":",
                                "{\"errors\":[{\"message\":\"SYNTH_PRIVATE\"}],\"data\":"),
                        Layer.PARSER),
                Arguments.of(
                        "SYNTH_ERRORS_NULL",
                        valid.replace("{\"data\":", "{\"errors\":null,\"data\":"),
                        Layer.PARSER),
                Arguments.of("SYNTH_DATA_NULL", "{\"data\":null}", Layer.PARSER),
                Arguments.of(
                        "SYNTH_CONNECTION",
                        valid.replace("\"individual\"", "\"wrong\""),
                        Layer.PARSER),
                Arguments.of("SYNTH_NODE_NULL", valid.replace("{\"id\":0}", "null"), Layer.PARSER),
                Arguments.of(
                        "SYNTH_EDGE_EXTRA",
                        valid.replace("{\"node\":", "{\"extra\":0,\"node\":"),
                        Layer.PARSER),
                Arguments.of(
                        "SYNTH_PAGE_INFO",
                        valid.replace("\"hasNextPage\":false", "\"hasNextPage\":null"),
                        Layer.PARSER),
                Arguments.of(
                        "SYNTH_CURSOR_MISSING", envelope("{\"id\":0}", true, "null"), Layer.PARSER),
                Arguments.of("SYNTH_EMPTY_PAGE", valid.replace(edge, ""), Layer.TRAVERSAL),
                Arguments.of(
                        "SYNTH_TWENTY_ONE",
                        valid.replace(edge, (edge + ",").repeat(20) + edge),
                        Layer.PARSER),
                Arguments.of(
                        "SYNTH_UNREQUESTED_FIELD",
                        valid.replace("{\"id\":0}", "{\"id\":0,\"updatedAt\":\"SYNTH\"}"),
                        Layer.CONTRACT),
                Arguments.of(
                        "SYNTH_DUPLICATE",
                        valid.replace("\"id\":0", "\"id\":0,\"id\":1"),
                        Layer.INPUT),
                Arguments.of("SYNTH_TRAILING", valid + " {}", Layer.INPUT));
    }

    @ParameterizedTest(name = "synthetic protocol case {index}")
    @MethodSource("malformed")
    void rejectsBeforeStaging(final String id, final String document, final Layer layer) {
        final var report =
                UsuariosCharacterization.pipeline(
                        page -> input(document), 2, 40, Fault.NONE, (page, row) -> expected());
        check(id, report, layer, 0, false);
        assertTrue(!report.toString().contains("SYNTH_PRIVATE"));
    }

    @AfterAll
    static void save() throws Exception {
        CharacterizationFixtures.write("usuarios-traversal", REPORTS);
    }
}
