package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class GraphQlFixtureContractTest {

    private static final int MAXIMUM_FIXTURE_BYTES = 64 * 1024;
    private static final String USER_NODE = "{\"id\":930001,\"name\":\"Synthetic User\"}";
    private static final String PICK_NODE_WITH_ITEM =
            "{\"id\":\"synthetic-pick-a\",\"sequenceCode\":\"synthetic-sequence-a\","
                    + "\"pickItems\":[{\"id\":\"synthetic-pick-item-a\"}]}";
    private static final String PICK_NODE_WITHOUT_ITEM =
            "{\"id\":\"synthetic-pick-b\",\"sequenceCode\":\"synthetic-sequence-b\","
                    + "\"pickItems\":[]}";
    private static final String FREIGHT_NODE =
            "{\"id\":\"synthetic-freight-a\","
                    + "\"accountingCreditId\":\"synthetic-credit-a\","
                    + "\"accountingCreditInstallmentId\":\"synthetic-installment-a\","
                    + "\"referenceNumber\":\"synthetic-reference-a\","
                    + "\"cte\":{\"key\":\"synthetic-cte-key-a\"},"
                    + "\"total\":100.00,"
                    + "\"corporationSequenceNumber\":\"synthetic-corporation-sequence-a\","
                    + "\"pickItemId\":\"synthetic-pick-item-a\"}";

    @Test
    void allThreeSanitizedFixturesPassParserContractGateAndTerminalStreaming() throws Exception {
        assertTerminalFixture(
                GraphQlReadOperation.USERS_SNAPSHOT,
                "users-terminal.json",
                GraphQlTestSupport.page(
                        GraphQlReadOperation.USERS_SNAPSHOT, false, null, USER_NODE),
                1);
        assertPicksTwoPageFixture();
        assertTerminalFixture(
                GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
                "freights-terminal.json",
                GraphQlTestSupport.page(
                        GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR,
                        false,
                        null,
                        FREIGHT_NODE),
                1);
    }

    @Test
    void bothSidecarsAcceptOneHundredNodesAndRejectPageSizeOneHundredAndOne() {
        assertSidecarPageCeiling(
                GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR, PICK_NODE_WITH_ITEM);
        assertSidecarPageCeiling(GraphQlReadOperation.FREIGHTS_TRANSITIONAL_SIDECAR, FREIGHT_NODE);
    }

    private static void assertTerminalFixture(
            final GraphQlReadOperation operation,
            final String resourceName,
            final GraphQlPageResponse baseline,
            final int expectedNodes)
            throws IOException {
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.fixture(operation, baseline);
        final GraphQlPageRequest request = GraphQlTestSupport.request(operation);
        final GraphQlPageResponse parsed =
                new GraphQlResponseParser(fixture.configuration())
                        .parse(readFixture(resourceName), request);
        final CancellationToken cancellation = CancellationToken.none();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(cancellation, ignored -> parsed),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);

        final GraphQlExtractionResult result =
                new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), Clock.systemUTC())
                        .stream(
                                fixture.guard().executionContext(),
                                request,
                                new GraphQlExtractionLimits(1, expectedNodes),
                                cancellation,
                                ignored -> {});

        assertEquals(expectedNodes, result.nodesDelivered());
        assertEquals(1, result.pagesFetched());
        assertPromotionPolicy(operation, fixture);
    }

    private static void assertPicksTwoPageFixture() throws IOException {
        final GraphQlReadOperation operation = GraphQlReadOperation.PICKS_TRANSITIONAL_SIDECAR;
        final GraphQlTestSupport.Fixture fixture =
                GraphQlTestSupport.fixture(
                        operation,
                        GraphQlTestSupport.page(operation, false, null, PICK_NODE_WITH_ITEM));
        final GraphQlPageRequest firstRequest = GraphQlTestSupport.request(operation);
        final GraphQlResponseParser parser = new GraphQlResponseParser(fixture.configuration());
        final Queue<GraphQlPageResponse> pages = new ArrayDeque<>();
        pages.add(parser.parse(readFixture("picks-first.json"), firstRequest));
        pages.add(
                parser.parse(
                        readFixture("picks-terminal.json"),
                        firstRequest.next(GraphQlCursor.observed("synthetic-cursor-a"))));
        final CancellationToken cancellation = CancellationToken.none();
        final GraphQlGateway secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(cancellation, ignored -> pages.remove()),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);

        final GraphQlExtractionResult result =
                new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), Clock.systemUTC())
                        .stream(
                                fixture.guard().executionContext(),
                                firstRequest,
                                new GraphQlExtractionLimits(2, 2),
                                cancellation,
                                ignored -> {});

        assertEquals(2, result.pagesFetched());
        assertEquals(2, result.nodesDelivered());
        assertTrue(pages.isEmpty());
        assertEquals(
                ContractDriftException.Reason.SOURCE_STATE_NOT_PROMOTABLE,
                assertThrows(ContractDriftException.class, fixture.guard()::complete).reason());
    }

    private static void assertPromotionPolicy(
            final GraphQlReadOperation operation, final GraphQlTestSupport.Fixture fixture) {
        if (operation == GraphQlReadOperation.USERS_SNAPSHOT) {
            assertEquals(SourceDataEffect.SHADOW_UPSERT, fixture.guard().complete().dataEffect());
            return;
        }
        assertEquals(
                ContractDriftException.Reason.SOURCE_STATE_NOT_PROMOTABLE,
                assertThrows(ContractDriftException.class, fixture.guard()::complete).reason());
    }

    private static void assertSidecarPageCeiling(
            final GraphQlReadOperation operation, final String nodeDocument) {
        final GraphQlTestSupport.Fixture fixture =
                GraphQlTestSupport.fixture(
                        operation, GraphQlTestSupport.page(operation, false, null, nodeDocument));
        final GraphQlPageRequest request =
                GraphQlPageRequest.initial(
                        operation, GraphQlTestSupport.request(operation).parameters(), 100);
        final String edges =
                IntStream.range(0, 100)
                        .mapToObj(ignored -> "{\"node\":" + nodeDocument + "}")
                        .collect(Collectors.joining(",", "[", "]"));
        final GraphQlPageResponse page =
                new GraphQlResponseParser(fixture.configuration())
                        .parse(envelope(operation, edges), request);

        assertEquals(100, page.nodeCount());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        GraphQlPageRequest.initial(
                                operation,
                                GraphQlTestSupport.request(operation).parameters(),
                                101));
    }

    private static String envelope(final GraphQlReadOperation operation, final String edges) {
        return "{\"data\":{\""
                + operation.connectionName()
                + "\":{\"edges\":"
                + edges
                + ",\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}}";
    }

    private static String readFixture(final String name) throws IOException {
        try (InputStream input =
                GraphQlFixtureContractTest.class.getResourceAsStream(
                        "/contracts/graphql/" + name)) {
            if (input == null) {
                throw new IOException("Fixture GraphQL sintética ausente.");
            }
            final byte[] bytes = input.readNBytes(MAXIMUM_FIXTURE_BYTES + 1);
            assertTrue(bytes.length <= MAXIMUM_FIXTURE_BYTES);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
