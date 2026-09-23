package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ColetasSourceReplayTest {
    private static final String ROW =
            """
            {"id":1,"sequence_code":101,"status":"finished","request_date":"2036-03-20",
             "service_date":"2036-03-20","finish_date":"2036-03-20","cancellation_reason":null}
            """;
    private static final String NODE =
            """
            {"id":"1","sequenceCode":101,"status":"finished","requestDate":"2036-03-20",
             "serviceDate":"2036-03-20","finishDate":"2036-03-20","cancellationReason":null,
             "statusUpdatedAt":"2036-03-20T12:00:00Z"}
            """;

    @Test
    void replaysExactExpandedBodiesAndReportsBoundWithoutInventingTerminal() throws Exception {
        final var result = replay(List.of("[" + ROW + "," + ROW + "]"), List.of(graph(NODE)));
        assertEquals(true, result.get("accepted"));
        assertEquals(2, result.get("stagedRows"));
        assertEquals(1, result.get("distinctRoots"));
        assertEquals(0, result.get("preservationDifferences"));
        assertEquals(false, result.get("dataTerminalObserved"));
        assertEquals(true, result.get("captureLimitReached"));
        final var json = new ObjectMapper().valueToTree(result);
        assertEquals(2, json.at("/comparison/matchedPhysicalRows").intValue());
        assertEquals(0, json.at("/comparison/canonicalIdDifferences").intValue());
        assertEquals(0, json.at("/comparison/fieldDifferences/status").intValue());
        assertEquals(2, json.at("/comparison/fieldDifferences/status_updated_at").intValue());
        assertFalse(json.path("snapshotProven").booleanValue());
        assertFalse(json.path("representativeParityAccepted").booleanValue());
        assertFalse(json.path("operationalBindingValidated").booleanValue());
        assertFalse(json.path("sqlExecuted").booleanValue());
        assertFalse(json.at("/comparison/globalSetEqualityProven").booleanValue());
    }

    @Test
    void acceptsOnlyAnActuallyCapturedEmptyPageAsTerminal() throws Exception {
        final var result = replay(List.of("[" + ROW + "]", "[]"), List.of());
        assertEquals(true, result.get("dataTerminalObserved"));
        assertEquals(false, result.get("captureLimitReached"));
        assertEquals(1, result.get("stagedRows"));
        assertEquals(0, result.get("graphPages"));
    }

    @Test
    void reportsRepeatedRootsAndDifferencesInsteadOfPromotingAliasToIdentity() throws Exception {
        final String different =
                NODE.replace("\"id\":\"1\"", "\"id\":\"PRIVATE_SENTINEL\"")
                        .replace("finished", "pending")
                        .replace("\"cancellationReason\":null", "\"cancellationReason\":\"\"");
        final var result =
                replay(List.of("[" + ROW + "]", "[" + ROW + "]"), List.of(graph(different)));
        final var json = new ObjectMapper().valueToTree(result);
        assertEquals(1, result.get("repeatedRootsAcrossPages"));
        assertEquals(2, json.at("/comparison/canonicalIdDifferences").intValue());
        assertEquals(2, json.at("/comparison/fieldDifferences/status").intValue());
        assertEquals(2, json.at("/comparison/fieldDifferences/cancellation_reason").intValue());
        assertFalse(json.toString().contains("PRIVATE_SENTINEL"));
        assertFalse(json.toString().contains("2036-03-20"));
    }

    @Test
    void missingReferenceRowsRemainOutsideComparison() throws Exception {
        final var result =
                replay(List.of("[" + ROW + "]"), List.of(graph(NODE.replace("101", "102"))));
        final var json = new ObjectMapper().valueToTree(result);
        assertEquals(0, json.at("/comparison/matchedPhysicalRows").intValue());
        assertEquals(1, json.at("/comparison/rowsOutsideGraphSample").intValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "[{}]",
                "[{\"id\":null}]",
                "[{\"id\":\"1\"}]",
                "[{\"id\":1},{\"id\":2},{\"id\":3}]",
                "[{\"id\":1,\"id\":2}]",
                "[] []",
                "[{\"id\":1,\"unexpected\":true}]",
                "[{\"id\":1,\"status\":2}]"
            })
    void rejectsInvalidSourcePages(final String page) {
        assertThrows(Exception.class, () -> replay(List.of(page), List.of()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{}",
                "{\"errors\":[]}",
                "{\"data\":{\"pick\":{\"edges\":[],\"pageInfo\":{\"hasNextPage\":true,\"endCursor\":null}}}}"
            })
    void rejectsInvalidReferencePages(final String page) {
        assertThrows(Exception.class, () -> replay(List.of("[]"), List.of(page)));
    }

    @Test
    void rejectsMissingSelectedFieldAndAmbiguousReference() {
        assertThrows(
                Exception.class,
                () ->
                        replay(
                                List.of("[]"),
                                List.of(graph(NODE.replace("\"status\":\"finished\",", "")))));
        final String duplicate =
                graph(NODE).replace("}],\"pageInfo", "},{\"node\":" + NODE + "}],\"pageInfo");
        assertThrows(Exception.class, () -> replay(List.of("[]"), List.of(duplicate)));
        final String alias = duplicate.replaceFirst("\"id\":\"1\"", "\"id\":\"2\"");
        assertThrows(Exception.class, () -> replay(List.of("[]"), List.of(alias)));
    }

    @Test
    void rejectsRepeatedCursorAndPageAfterTerminal() {
        final String next =
                graph(NODE)
                        .replace(
                                "false,\"endCursor\":null",
                                "true,\"endCursor\":\"PRIVATE_CURSOR\"");
        final String other = next.replace("\"id\":\"1\"", "\"id\":\"2\"").replace("101", "102");
        assertThrows(Exception.class, () -> replay(List.of("[]"), List.of(next, other)));
        assertThrows(
                Exception.class, () -> replay(List.of("[]"), List.of(graph(NODE), graph(NODE))));
        assertThrows(Exception.class, () -> replay(List.of("[]", "[]"), List.of()));
    }

    @Test
    void rejectsProtocolBoundsTruncationTrailingInputAndMalformedUtf8() throws Exception {
        final byte[] valid = wire(List.of("[]"), List.of());
        assertThrows(
                Exception.class,
                () ->
                        ColetasSourceReplay.replay(
                                new ByteArrayInputStream(Arrays.copyOf(valid, valid.length - 1))));
        assertThrows(
                Exception.class,
                () ->
                        ColetasSourceReplay.replay(
                                new ByteArrayInputStream(Arrays.copyOf(valid, valid.length + 1))));
        final byte[] invalid = valid.clone();
        invalid[0] = 1;
        assertThrows(
                Exception.class,
                () -> ColetasSourceReplay.replay(new ByteArrayInputStream(invalid)));
        final byte[] oversized = valid.clone();
        oversized[4] = 1;
        assertThrows(
                Exception.class,
                () -> ColetasSourceReplay.replay(new ByteArrayInputStream(oversized)));
        final byte[] badUtf8 = valid.clone();
        badUtf8[8] = (byte) 0xff;
        assertThrows(
                Exception.class,
                () -> ColetasSourceReplay.replay(new ByteArrayInputStream(badUtf8)));
        assertThrows(Exception.class, () -> replay(List.of(), List.of()));
        assertThrows(Exception.class, () -> replay(List.of("[]", "[]", "[]"), List.of()));
    }

    @Test
    void metadataDriftRejectsTheReplay() throws Exception {
        final byte[] changed =
                wire(
                        List.of("[]"),
                        List.of(),
                        metadata().replace("sequence_code", "unknown_field"));
        assertThrows(
                Exception.class,
                () -> ColetasSourceReplay.replay(new ByteArrayInputStream(changed)));
    }

    @Test
    void existingLosslessScalarFixtureSurvivesTheNewStdinBoundary() throws Exception {
        final String page;
        try (var input =
                Objects.requireNonNull(
                        getClass()
                                .getResourceAsStream(
                                        "/contracts/bloco62/6908-page.synthetic.json"))) {
            page = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        final var result = replay(List.of(page), List.of());
        assertEquals(true, result.get("accepted"));
        assertEquals(5, result.get("stagedRows"));
        assertTrue(result.containsKey("contractFingerprint"));
    }

    private static String graph(final String node) {
        return "{\"data\":{\"pick\":{\"edges\":[{\"node\":"
                + node
                + "}],\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}}";
    }

    private static java.util.Map<String, Object> replay(
            final List<String> pages, final List<String> graph) throws IOException {
        return ColetasSourceReplay.replay(new ByteArrayInputStream(wire(pages, graph)));
    }

    private static String metadata() throws IOException {
        try (var input =
                Objects.requireNonNull(
                        ColetasSourceReplayTest.class.getResourceAsStream(
                                "/contracts/bloco62/6908-info.sanitized.json"))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] wire(final List<String> pages, final List<String> graph)
            throws IOException {
        return wire(pages, graph, metadata());
    }

    private static byte[] wire(
            final List<String> pages, final List<String> graph, final String metadata)
            throws IOException {
        final var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(6201);
            frame(out, metadata);
            out.writeInt(pages.size());
            for (final String page : pages) {
                frame(out, page);
            }
            out.writeInt(graph.size());
            for (final String page : graph) {
                frame(out, page);
            }
        }
        return bytes.toByteArray();
    }

    private static void frame(final DataOutputStream out, final String value) throws IOException {
        final byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        out.writeInt(bytes.length);
        out.write(bytes);
    }
}
