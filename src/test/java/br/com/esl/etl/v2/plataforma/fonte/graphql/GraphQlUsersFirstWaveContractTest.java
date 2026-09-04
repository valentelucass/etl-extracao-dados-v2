package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractClassification;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractValidator;
import br.com.esl.etl.v2.plataforma.contrato.FirstWaveContractManifestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import org.junit.jupiter.api.Test;

class GraphQlUsersFirstWaveContractTest {

    private static final int MAXIMUM_FIXTURE_BYTES = 64 * 1024;
    private static final GraphQlReadOperation OPERATION = GraphQlReadOperation.USERS_SNAPSHOT;

    @Test
    void individualSemanticsAreVersionedWithoutInventedTemporalOrDataExportContract()
            throws Exception {
        final JsonNode manifest = manifest();
        final GraphQlPageRequest request = GraphQlTestSupport.request(OPERATION);
        final JsonNode serialized =
                GraphQlTestSupport.MAPPER.readTree(
                        new GraphQlRequestJsonSerializer(GraphQlTestSupport.MAPPER)
                                .serialize(request));

        assertEquals("individual", OPERATION.connectionName());
        assertEquals("enabled=true", OPERATION.fixedParametersContract());
        assertTrue(serialized.at("/variables/params/enabled").asBoolean());
        assertEquals(20, serialized.at("/variables/first").asInt());
        assertEquals(ContractClassification.TRANSITIONAL, OPERATION.contractClassification());
        assertEquals(
                SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF,
                OPERATION.completenessStatus());
        assertFalse(OPERATION.documentText().contains("updatedAt"));
        assertFalse(OPERATION.documentText().contains("9901"));
        assertFalse(OPERATION.documentText().contains("mutation"));

        FirstWaveContractManifestSupport.assertSemantics(
                manifest, OPERATION.contractSemanticsFingerprint());
        FirstWaveContractManifestSupport.assertAspect(
                manifest, "temporalTranslation", ContractClassification.ABSENT);
        FirstWaveContractManifestSupport.assertAspect(
                manifest, "ordering", ContractClassification.ABSENT);
        FirstWaveContractManifestSupport.assertAspect(
                manifest, "nullableNamePolicy", ContractClassification.PROVEN);
        assertEquals(
                "LOCAL_V2-033_CURRENT_HISTORY",
                manifest.path("aspects").path("nullableNamePolicy").path("scope").asText());
        assertTrue(
                manifest.path("aspects")
                        .path("nullableNamePolicy")
                        .path("contract")
                        .asText()
                        .contains(
                                "REMOTE_SCHEMA_NULLABILITY_AND_PUBLISHED_CONSUMER_POLICY_UNVERIFIED"));
        FirstWaveContractManifestSupport.assertAspect(
                manifest, "dataExportTemplate", ContractClassification.ABSENT);
        FirstWaveContractManifestSupport.assertAspect(
                manifest, "completeness", ContractClassification.ABSENT);
        FirstWaveContractManifestSupport.assertFailClosedCapabilities(manifest);
    }

    @Test
    void firstPageWithNullableOrAbsentNameProducesTheVersionedRelease() throws Exception {
        final JsonNode first = readFixture("users-first.json");
        final GraphQlPageResponse baseline = page(first);
        final SourceContractRelease release = GraphQlFirstWaveContractCatalog.release(OPERATION);
        final ContractResponse observed =
                GraphQlContractAdapter.forSyntheticFixtures(
                                ContractObservationLimits.runtimeDefaults())
                        .response(OPERATION, baseline);

        FirstWaveContractManifestSupport.assertRelease(manifest(), release);
        assertEquals(release.response(), observed);
        assertEquals(
                java.util.List.of(
                        ContractResponse.JsonType.STRING, ContractResponse.JsonType.INTEGER),
                release.response().find("/node/id").orElseThrow().jsonTypes());
        final var name = release.response().find("/node/name").orElseThrow();
        assertEquals(
                br.com.esl.etl.v2.plataforma.contrato.ContractResponse.Presence.OPTIONAL,
                name.presence());
        assertTrue(name.nullable());
    }

    @Test
    void boundedFirstAndTerminalFixturesTraverseButNeverProveCompleteness() throws Exception {
        final JsonNode firstFixture = readFixture("users-first.json");
        final GraphQlPageResponse baseline = page(firstFixture);
        final GraphQlTestSupport.Fixture fixture = GraphQlTestSupport.fixture(OPERATION, baseline);
        final GraphQlPageRequest firstRequest = GraphQlTestSupport.request(OPERATION);
        final GraphQlResponseParser parser = new GraphQlResponseParser(fixture.configuration());
        final Queue<GraphQlPageResponse> pages = new ArrayDeque<>();
        final GraphQlPageResponse firstPage =
                parser.parse(
                        GraphQlTestSupport.MAPPER.writeValueAsString(firstFixture), firstRequest);
        final GraphQlPageResponse terminalPage =
                parser.parse(
                        GraphQlTestSupport.MAPPER.writeValueAsString(
                                readFixture("users-terminal.json")),
                        firstRequest.next(GraphQlCursor.observed("synthetic-users-cursor-a")));
        final ContractValidator validator =
                new ContractValidator(
                        fixture.release(),
                        ContractCompatibilityPolicy.create(
                                "graphql-first-wave-page-policy-v1",
                                fixture.release().contractFingerprint(),
                                java.util.List.of()));
        assertEquals(
                java.util.List.of(),
                validator
                        .classifyResponse(firstPage.contractObservation().orElseThrow())
                        .changes());
        assertEquals(
                java.util.List.of(),
                validator
                        .classifyResponse(terminalPage.contractObservation().orElseThrow())
                        .changes());
        pages.add(firstPage);
        pages.add(terminalPage);
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
                                new GraphQlExtractionLimits(2, 4),
                                cancellation,
                                ignored -> {});

        assertEquals(2, result.pagesFetched());
        assertEquals(4, result.nodesDelivered());
        assertEquals(
                GraphQlTraversalVerification.LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED,
                result.traversalVerification());
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
        assertTrue(OPERATION.completenessStatus().permits(SourceDataEffect.SHADOW_UPSERT));
        assertFalse(OPERATION.completenessStatus().permits(SourceDataEffect.SWEEP_OR_DEACTIVATION));
        assertTrue(pages.isEmpty());
        assertEquals(SourceDataEffect.SHADOW_UPSERT, fixture.guard().complete().dataEffect());
    }

    private static GraphQlPageResponse page(final JsonNode envelope) {
        final ArrayNode nodes = GraphQlTestSupport.MAPPER.createArrayNode();
        envelope.at("/data/individual/edges").forEach(edge -> nodes.add(edge.path("node")));
        final JsonNode pageInfo = envelope.at("/data/individual/pageInfo");
        final Optional<GraphQlCursor> cursor =
                pageInfo.path("endCursor").isNull()
                        ? Optional.empty()
                        : Optional.of(GraphQlCursor.observed(pageInfo.path("endCursor").asText()));
        return new GraphQlPageResponse(nodes, pageInfo.path("hasNextPage").asBoolean(), cursor);
    }

    private static JsonNode manifest() throws IOException {
        return FirstWaveContractManifestSupport.contract("graphql-individual");
    }

    private static JsonNode readFixture(final String name) throws IOException {
        try (InputStream input =
                GraphQlUsersFirstWaveContractTest.class.getResourceAsStream(
                        "/contracts/graphql/" + name)) {
            if (input == null) {
                throw new IOException("Fixture GraphQL V2-025a ausente.");
            }
            final byte[] bytes = input.readNBytes(MAXIMUM_FIXTURE_BYTES + 1);
            if (bytes.length > MAXIMUM_FIXTURE_BYTES) {
                throw new IOException("Fixture GraphQL V2-025a excede o limite.");
            }
            return GraphQlTestSupport.MAPPER.readTree(bytes);
        }
    }
}
