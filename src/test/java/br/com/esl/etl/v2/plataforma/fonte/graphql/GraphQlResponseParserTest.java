package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.List;
import org.junit.jupiter.api.Test;

class GraphQlResponseParserTest {

    @Test
    void parsesOneBoundedRelayPageAndProducesSanitizedContractEvidence() {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.usersFixture();
        final GraphQlResponseParser parser = new GraphQlResponseParser(fixture.configuration());
        final GraphQlPageResponse page =
                parser.parse(
                        GraphQlTestSupport.usersEnvelope(
                                "[{\"node\":{\"id\":930001,\"name\":\"Synthetic\"}}]",
                                false,
                                "null"),
                        GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT));

        assertEquals(1, page.nodeCount());
        assertFalse(page.hasNextPage());
        assertTrue(page.endCursor().isEmpty());
        assertTrue(page.contractObservation().isPresent());
        assertEquals(GraphQlTestSupport.LIMITS, page.observationLimits().orElseThrow());
        assertFalse(page.contractObservation().orElseThrow().toString().contains("930001"));
        assertFalse(page.toString().contains("Synthetic"));

        final GraphQlPageResponse textualId =
                parser.parse(
                        GraphQlTestSupport.usersEnvelope(
                                "[{\"node\":{\"id\":\"synthetic-user-key\",\"name\":\"N\"}}]",
                                false,
                                "null"),
                        GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT));
        assertEquals(1, textualId.nodeCount());
    }

    @Test
    void rejectsGraphQlErrorsEvenWithPartialDataAndAllMalformedEnvelopeLayers() {
        final GraphQlResponseParser parser = parser();
        final GraphQlPageRequest request =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);
        final String validConnection =
                "{\"edges\":[{\"node\":{\"id\":930001,\"name\":\"N\"}}],"
                        + "\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}";

        final GraphQlResponseException errors =
                assertThrows(
                        GraphQlResponseException.class,
                        () ->
                                parser.parse(
                                        "{\"errors\":[{\"message\":\"sensitive\"}],\"data\":{}}",
                                        request));
        assertEquals(GraphQlResponseException.Reason.GRAPHQL_ERRORS, errors.reason());
        assertFalse(errors.getMessage().contains("sensitive"));

        for (final String invalid :
                List.of(
                        "null",
                        "[]",
                        "{}",
                        "{\"data\":null}",
                        "{\"data\":{},\"extensions\":{}}",
                        "{\"data\":{\"wrong\":" + validConnection + "}}",
                        "{\"data\":{\"individual\":null}}",
                        "{\"data\":{\"individual\":{\"edges\":[],\"pageInfo\":{},\"extra\":1}}}")) {
            assertThrows(RuntimeException.class, () -> parser.parse(invalid, request), invalid);
        }
    }

    @Test
    void rejectsInvalidEdgesNodesAndPageInfoWithoutPartialSuccess() {
        final GraphQlResponseParser parser = parser();
        final GraphQlPageRequest request =
                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT);
        for (final String edges :
                List.of(
                        "null",
                        "{}",
                        "[null]",
                        "[{}]",
                        "[{\"node\":null}]",
                        "[{\"node\":{}}]",
                        "[{\"node\":{\"id\":true,\"name\":\"N\"}}]",
                        "[{\"node\":{\"id\":\" \",\"name\":\"N\"}}]",
                        "[{\"node\":{\"id\":\"u\",\"name\":\"N\"},\"cursor\":\"x\"}]")) {
            assertThrows(
                    GraphQlResponseException.class,
                    () ->
                            parser.parse(
                                    GraphQlTestSupport.usersEnvelope(edges, false, "null"),
                                    request),
                    edges);
        }
        for (final String pageInfo :
                List.of(
                        "null",
                        "{}",
                        "{\"hasNextPage\":null,\"endCursor\":null}",
                        "{\"hasNextPage\":true,\"endCursor\":null}",
                        "{\"hasNextPage\":true,\"endCursor\":\" \"}",
                        "{\"hasNextPage\":true,\"endCursor\":1}",
                        "{\"hasNextPage\":false,\"endCursor\":null,\"extra\":1}")) {
            final String document =
                    "{\"data\":{\"individual\":{\"edges\":[{\"node\":{\"id\":930001,\"name\":\"N\"}}],\"pageInfo\":"
                            + pageInfo
                            + "}}}";
            assertThrows(GraphQlResponseException.class, () -> parser.parse(document, request));
        }
    }

    @Test
    void enforcesRequestedAndUsersPageSizesBeforeReturningThePage() {
        final GraphQlResponseParser parser = parser();
        final StringBuilder edges = new StringBuilder("[");
        for (int index = 0; index < 21; index++) {
            if (index > 0) {
                edges.append(',');
            }
            edges.append("{\"node\":{\"id\":").append(930001 + index).append(",\"name\":\"N\"}}");
        }
        edges.append(']');

        assertThrows(
                GraphQlResponseException.class,
                () ->
                        parser.parse(
                                GraphQlTestSupport.usersEnvelope(edges.toString(), false, "null"),
                                GraphQlTestSupport.request(GraphQlReadOperation.USERS_SNAPSHOT)));

        final GraphQlPageRequest one =
                GraphQlPageRequest.initial(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        GraphQlQueryParameters.enabledUsers(),
                        1);
        assertThrows(
                GraphQlResponseException.class,
                () ->
                        parser.parse(
                                GraphQlTestSupport.usersEnvelope(
                                        "[{\"node\":{\"id\":1,\"name\":\"A\"}},{\"node\":{\"id\":2,\"name\":\"B\"}}]",
                                        false,
                                        "null"),
                                one));
    }

    @Test
    void strictJsonAndRuntimePathBoundaryRejectDuplicateTrailingAndShapeDrift() throws Exception {
        assertTrue(GraphQlStrictJsonParser.readTree("{\"id\":1}", 2).isObject());
        assertThrows(
                JsonProcessingException.class,
                () -> GraphQlStrictJsonParser.readTree("{\"id\":1,\"id\":2}", 3));
        assertThrows(
                JsonProcessingException.class,
                () -> GraphQlStrictJsonParser.readTree("{\"id\":1} {\"id\":2}", 4));
        assertThrows(
                JsonProcessingException.class,
                () -> GraphQlStrictJsonParser.readTree("{\"data\":[1,2]}", 3));

        final GraphQlResponseParser parser = parser();
        final ContractDriftException drift =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                parser.parse(
                                        GraphQlTestSupport.usersEnvelope(
                                                "[{\"node\":{\"id\":930001,\"name\":\"N\",\"extra\":\"x\"}}]",
                                                false,
                                                "null"),
                                        GraphQlTestSupport.request(
                                                GraphQlReadOperation.USERS_SNAPSHOT)));
        assertEquals(ContractDriftException.Reason.BREAKING_CHANGE, drift.reason());
        assertFalse(drift.getMessage().contains("extra"));
    }

    private static GraphQlResponseParser parser() {
        return new GraphQlResponseParser(GraphQlTestSupport.usersFixture().configuration());
    }
}
