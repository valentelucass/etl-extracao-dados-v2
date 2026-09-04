package br.com.esl.etl.v2.plataforma.contrato;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.BinaryNode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractResponseProfilerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ContractResponseProfiler profiler =
            ContractResponseProfiler.forSyntheticFixtures(
                    ContractObservationLimits.runtimeDefaults());

    @Test
    void profilesNestedRelativePresenceArraysNullabilityAndEscapedPathsWithoutValues()
            throws Exception {
        final JsonNode records =
                objectMapper.readTree(
                        """
                        [
                          {"id":"synthetic-a","nested":{"code":1},"tags":[{"name":"x"}],"a/b":true},
                          {"id":"synthetic-b","nested":null,"tags":[],"til~de":2.5}
                        ]
                        """);

        final ContractResponse response =
                profiler.profile(records, "$", ContractResponse.Cardinality.ARRAY, "/id");

        assertEquals(ContractResponse.ObservationState.POPULATED, response.observationState());
        assertField(
                response,
                "/id",
                ContractResponse.Cardinality.SCALAR,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.STRING);
        assertField(
                response,
                "/nested",
                ContractResponse.Cardinality.OBJECT,
                ContractResponse.Presence.REQUIRED,
                true,
                ContractResponse.JsonType.OBJECT);
        assertField(
                response,
                "/nested/code",
                ContractResponse.Cardinality.SCALAR,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.INTEGER);
        assertField(
                response,
                "/tags",
                ContractResponse.Cardinality.ARRAY,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.ARRAY);
        assertField(
                response,
                "/tags/*",
                ContractResponse.Cardinality.OBJECT,
                ContractResponse.Presence.OPTIONAL,
                false,
                ContractResponse.JsonType.OBJECT);
        assertField(
                response,
                "/tags/*/name",
                ContractResponse.Cardinality.SCALAR,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.STRING);
        assertTrue(response.find("/a~1b").isPresent());
        assertTrue(response.find("/til~0de").isPresent());
        assertFalse(response.toString().contains("synthetic-a"));
        assertFalse(response.toString().contains("synthetic-b"));
    }

    @Test
    void ignoresPropertyRecordAndScalarValuesButPreservesTheirStructuralTypes() throws Exception {
        final ContractResponse first = profile("[{\"id\":\"value-a\",\"count\":1}]");
        final ContractResponse second = profile("[{\"count\":999,\"id\":\"value-b\"}]");

        final SourceContractRelease firstRelease = release(first);
        final SourceContractRelease secondRelease = release(second);

        assertEquals(first, second);
        assertEquals(firstRelease.responseFingerprint(), secondRelease.responseFingerprint());
        assertEquals(firstRelease.contractFingerprint(), secondRelease.contractFingerprint());
    }

    @Test
    void separatesIntegerNumberAndMixedCardinality() throws Exception {
        final ContractResponse response =
                profile(
                        """
                        [
                          {"id":"a","value":1,"shape":"x"},
                          {"id":"b","value":1.5,"shape":{"nested":true}}
                        ]
                        """);

        assertEquals(
                List.of(ContractResponse.JsonType.INTEGER, ContractResponse.JsonType.NUMBER),
                response.find("/value").orElseThrow().jsonTypes());
        assertEquals(
                ContractResponse.Cardinality.MIXED,
                response.find("/shape").orElseThrow().cardinality());
    }

    @Test
    void representsAnEmptyArrayWithoutClaimingFieldEvidence() throws Exception {
        final ContractResponse response = profile("[]");

        assertEquals(ContractResponse.ObservationState.EMPTY, response.observationState());
        assertTrue(response.fields().isEmpty());
        assertEquals("/id", response.keyPath());
    }

    @Test
    void profilesGraphQlEnvelopeAncestorsSiblingsAndTerminalEmptyWithoutValues() throws Exception {
        final JsonNode firstEnvelope =
                objectMapper.readTree(
                        """
                        {
                          "data": {
                            "individual": {
                              "edges": [{"id":"synthetic-a","status":"OPEN"}],
                              "pageInfo": {"hasNextPage": true}
                            }
                          },
                          "errors": []
                        }
                        """);
        final JsonNode secondEnvelope =
                objectMapper.readTree(
                        """
                        {
                          "errors": [],
                          "data": {
                            "individual": {
                              "pageInfo": {"hasNextPage": false},
                              "edges": [{"status":"CLOSED","id":"synthetic-b"}]
                            }
                          }
                        }
                        """);

        final ContractResponse first =
                profiler.profileWithEnvelope(
                        firstEnvelope.at("/data/individual/edges"),
                        "/data/individual/edges",
                        ContractResponse.Cardinality.ARRAY,
                        "/id",
                        firstEnvelope);
        final ContractResponse second =
                profiler.profileWithEnvelope(
                        secondEnvelope.at("/data/individual/edges"),
                        "/data/individual/edges",
                        ContractResponse.Cardinality.ARRAY,
                        "/id",
                        secondEnvelope);

        assertEquals(first, second);
        assertField(
                first,
                ContractResponse.FieldScope.ENVELOPE,
                "/data",
                ContractResponse.Cardinality.OBJECT,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.OBJECT);
        assertField(
                first,
                ContractResponse.FieldScope.ENVELOPE,
                "/data/individual/edges",
                ContractResponse.Cardinality.ARRAY,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.ARRAY);
        assertField(
                first,
                ContractResponse.FieldScope.ENVELOPE,
                "/data/individual/pageInfo/hasNextPage",
                ContractResponse.Cardinality.SCALAR,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.BOOLEAN);
        assertField(
                first,
                ContractResponse.FieldScope.ENVELOPE,
                "/errors",
                ContractResponse.Cardinality.ARRAY,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.ARRAY);
        assertField(
                first,
                ContractResponse.FieldScope.RECORD,
                "/id",
                ContractResponse.Cardinality.SCALAR,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.STRING);
        assertFalse(first.toString().contains("synthetic-a"));

        final JsonNode terminalEnvelope =
                objectMapper.readTree(
                        """
                        {
                          "data": {
                            "individual": {
                              "edges": [],
                              "pageInfo": {"hasNextPage": false}
                            }
                          },
                          "errors": []
                        }
                        """);
        final ContractResponse terminal =
                profiler.profileWithEnvelope(
                        terminalEnvelope.at("/data/individual/edges"),
                        "/data/individual/edges",
                        ContractResponse.Cardinality.ARRAY,
                        "/id",
                        terminalEnvelope);

        assertEquals(ContractResponse.ObservationState.EMPTY, terminal.observationState());
        assertTrue(
                terminal.fields().stream()
                        .noneMatch(field -> field.scope() == ContractResponse.FieldScope.RECORD));
        assertField(
                terminal,
                ContractResponse.FieldScope.ENVELOPE,
                "/data/individual/edges",
                ContractResponse.Cardinality.ARRAY,
                ContractResponse.Presence.REQUIRED,
                false,
                ContractResponse.JsonType.ARRAY);
    }

    @Test
    void profilesInvalidObservedKeysForClassifiedDriftAndRejectsAmbiguousCasing() throws Exception {
        final SourceContractRelease baseline = release(profile("[{\"id\":\"synthetic-key\"}]"));
        final ContractValidator validator =
                new ContractValidator(baseline, ContractTestSupport.policy(baseline));
        for (final String json :
                List.of(
                        "[{\"other\":true}]",
                        "[{\"id\":null}]",
                        "[{\"id\":{}}]",
                        "[{\"id\":[]}]")) {
            final ContractDiff diff = validator.classifyResponse(profile(json));
            assertTrue(
                    diff.changes().stream()
                            .anyMatch(change -> change.kind() == ContractChange.Kind.KEY_CHANGED));
        }

        assertThrows(
                IllegalArgumentException.class, () -> profile("[{\"id\":\"a\",\"ID\":\"b\"}]"));
    }

    @Test
    void runtimeBoundaryKeepsDynamicMapsOpaqueAndRequiresPreapprovedAdditivePaths()
            throws Exception {
        final ContractResponse opaqueBaseline =
                profile("[{\"id\":\"synthetic-a\",\"attributes\":{}}]");
        final SourceContractRelease opaqueRelease = release(opaqueBaseline);
        final ContractCompatibilityPolicy closedContainerPolicy =
                ContractTestSupport.policy(opaqueRelease);
        final ContractResponseProfiler closedContainerRuntime =
                ContractResponseProfiler.forRuntime(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(
                                opaqueRelease, closedContainerPolicy));

        assertThrows(
                ContractDriftException.class,
                () ->
                        closedContainerRuntime.profile(
                                objectMapper.readTree(
                                        "[{\"id\":\"synthetic-b\",\"attributes\":{\"customerAlpha\":\"x\"}}]"),
                                "$",
                                ContractResponse.Cardinality.ARRAY,
                                "/id"));

        final ContractCompatibilityPolicy opaquePolicy =
                ContractCompatibilityPolicy.create(
                        "policy-opaque-v1",
                        opaqueRelease.contractFingerprint(),
                        List.of(),
                        List.of(
                                new ContractOpaquePath(
                                        ContractResponse.FieldScope.RECORD, "/attributes")));
        final ContractResponseProfiler opaqueRuntime =
                ContractResponseProfiler.forRuntime(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(opaqueRelease, opaquePolicy));
        final ContractResponse opaqueObserved =
                opaqueRuntime.profile(
                        objectMapper.readTree(
                                "[{\"id\":\"synthetic-b\",\"attributes\":{\"customerAlpha\":\"x\"}}]"),
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        "/id");

        assertEquals(opaqueBaseline, opaqueObserved);
        assertTrue(
                opaqueObserved.fields().stream()
                        .noneMatch(field -> field.path().contains("customer")));

        final ContractResponseProfiler shallowOpaqueRuntime =
                ContractResponseProfiler.forRuntime(
                        new ContractObservationLimits(2, 10, 100),
                        ContractResponsePathBoundary.forRuntime(opaqueRelease, opaquePolicy));
        final IllegalArgumentException deepOpaque =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                shallowOpaqueRuntime.profile(
                                        objectMapper.readTree(
                                                "[{\"id\":\"synthetic\",\"attributes\":{\"dynamicOne\":{\"dynamicTwo\":true}}}]"),
                                        "$",
                                        ContractResponse.Cardinality.ARRAY,
                                        "/id"));
        assertFalse(deepOpaque.getMessage().contains("dynamic"));

        final ContractResponseProfiler narrowOpaqueRuntime =
                ContractResponseProfiler.forRuntime(
                        new ContractObservationLimits(12, 10, 5),
                        ContractResponsePathBoundary.forRuntime(opaqueRelease, opaquePolicy));
        final IllegalArgumentException wideOpaque =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                narrowOpaqueRuntime.profile(
                                        objectMapper.readTree(
                                                "[{\"id\":\"synthetic\",\"attributes\":{"
                                                        + "\"dynamicOne\":1,\"dynamicTwo\":2,"
                                                        + "\"dynamicThree\":3}}]"),
                                        "$",
                                        ContractResponse.Cardinality.ARRAY,
                                        "/id"));
        assertFalse(wideOpaque.getMessage().contains("dynamic"));

        final ContractResponse emptyArrayBaseline =
                profile("[{\"id\":\"synthetic-a\",\"tags\":[]}]");
        final SourceContractRelease emptyArrayRelease = release(emptyArrayBaseline);
        final ContractCompatibilityPolicy emptyArrayClosedPolicy =
                ContractTestSupport.policy(emptyArrayRelease);
        final ContractCompatibilityPolicy invalidArrayOpaquePolicy =
                ContractCompatibilityPolicy.create(
                        "policy-invalid-array-opaque-v1",
                        emptyArrayRelease.contractFingerprint(),
                        List.of(),
                        List.of(
                                new ContractOpaquePath(
                                        ContractResponse.FieldScope.RECORD, "/tags")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractResponsePathBoundary.forRuntime(
                                emptyArrayRelease, invalidArrayOpaquePolicy));
        final JsonNode populatedArray =
                objectMapper.readTree(
                        "[{\"id\":\"synthetic-a\",\"tags\":[{\"code\":\"x\"}]},{\"id\":\"synthetic-b\",\"tags\":[]}]");
        assertThrows(
                ContractDriftException.class,
                () ->
                        ContractResponseProfiler.forRuntime(
                                        ContractObservationLimits.runtimeDefaults(),
                                        ContractResponsePathBoundary.forRuntime(
                                                emptyArrayRelease, emptyArrayClosedPolicy))
                                .profile(
                                        populatedArray,
                                        "$",
                                        ContractResponse.Cardinality.ARRAY,
                                        "/id"));
        final ContractResponse populatedArrayFixture =
                profiler.profile(populatedArray, "$", ContractResponse.Cardinality.ARRAY, "/id");
        final List<ContractAllowance> arrayAllowances =
                new ContractValidator(emptyArrayRelease, emptyArrayClosedPolicy)
                        .classifyResponse(populatedArrayFixture).changes().stream()
                                .map(ContractAllowance::forChange)
                                .toList();
        final ContractCompatibilityPolicy arrayPolicy =
                ContractCompatibilityPolicy.create(
                        "policy-array-addition-v1",
                        emptyArrayRelease.contractFingerprint(),
                        arrayAllowances);
        final ContractResponse admittedArray =
                ContractResponseProfiler.forRuntime(
                                ContractObservationLimits.runtimeDefaults(),
                                ContractResponsePathBoundary.forRuntime(
                                        emptyArrayRelease, arrayPolicy))
                        .profile(populatedArray, "$", ContractResponse.Cardinality.ARRAY, "/id");
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(emptyArrayRelease, arrayPolicy)
                        .validateResponse(admittedArray)
                        .status());

        final SourceContractRelease closedRelease = release(profile("[{\"id\":\"synthetic-a\"}]"));
        final ContractCompatibilityPolicy closedPolicy = ContractTestSupport.policy(closedRelease);
        final ContractResponseProfiler closedRuntime =
                ContractResponseProfiler.forRuntime(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(closedRelease, closedPolicy));
        for (final String dynamicKey : List.of("customerAlpha", "customerBeta")) {
            final ContractDriftException rejected =
                    assertThrows(
                            ContractDriftException.class,
                            () ->
                                    closedRuntime.profile(
                                            objectMapper.readTree(
                                                    "[{\"id\":\"synthetic\",\""
                                                            + dynamicKey
                                                            + "\":true}]"),
                                            "$",
                                            ContractResponse.Cardinality.ARRAY,
                                            "/id"));
            assertFalse(rejected.getMessage().contains(dynamicKey));
        }

        final ContractResponse additiveFixture =
                profile("[{\"id\":\"synthetic-a\",\"extra\":true},{\"id\":\"synthetic-b\"}]");
        final ContractChange additiveChange =
                new ContractValidator(closedRelease, closedPolicy)
                        .classifyResponse(additiveFixture)
                        .changes()
                        .get(0);
        final ContractCompatibilityPolicy additivePolicy =
                ContractCompatibilityPolicy.create(
                        "policy-additive-v1",
                        closedRelease.contractFingerprint(),
                        List.of(ContractAllowance.forChange(additiveChange)));
        final ContractResponseProfiler additiveRuntime =
                ContractResponseProfiler.forRuntime(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(closedRelease, additivePolicy));

        final ContractResponse admitted =
                additiveRuntime.profile(
                        objectMapper.readTree(
                                "[{\"id\":\"synthetic-c\",\"extra\":false},{\"id\":\"synthetic-d\"}]"),
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        "/id");

        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(closedRelease, additivePolicy)
                        .validateResponse(admitted)
                        .status());
        final ContractResponse allPresentOnSmallPage =
                additiveRuntime.profile(
                        objectMapper.readTree("[{\"id\":\"synthetic-e\",\"extra\":true}]"),
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        "/id");
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(closedRelease, additivePolicy)
                        .validateResponse(allPresentOnSmallPage)
                        .status());

        final ContractResponse nestedOptionalFixture =
                profile(
                        "[{\"id\":\"synthetic-a\",\"extra\":{\"flag\":true}},{\"id\":\"synthetic-b\"}]");
        final ContractCompatibilityPolicy nestedOptionalPolicy =
                ContractCompatibilityPolicy.create(
                        "policy-nested-additive-v1",
                        closedRelease.contractFingerprint(),
                        new ContractValidator(closedRelease, closedPolicy)
                                .classifyResponse(nestedOptionalFixture).changes().stream()
                                        .map(ContractAllowance::forChange)
                                        .toList());
        final ContractResponse nestedAllPresent =
                ContractResponseProfiler.forRuntime(
                                ContractObservationLimits.runtimeDefaults(),
                                ContractResponsePathBoundary.forRuntime(
                                        closedRelease, nestedOptionalPolicy))
                        .profile(
                                objectMapper.readTree(
                                        "[{\"id\":\"synthetic-c\",\"extra\":{\"flag\":false}}]"),
                                "$",
                                ContractResponse.Cardinality.ARRAY,
                                "/id");
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(closedRelease, nestedOptionalPolicy)
                        .validateResponse(nestedAllPresent)
                        .status());

        final ContractResponse recordEnvelopeFixture =
                profile(
                        "[{\"id\":\"synthetic-a\",\"envelope\":{\"flag\":true}},{\"id\":\"synthetic-b\"}]");
        final List<ContractChange> recordEnvelopeChanges =
                new ContractValidator(closedRelease, closedPolicy)
                        .classifyResponse(recordEnvelopeFixture)
                        .changes();
        assertTrue(
                recordEnvelopeChanges.stream()
                        .allMatch(
                                change ->
                                        change.responseFieldScope()
                                                .equals(
                                                        java.util.Optional.of(
                                                                ContractResponse.FieldScope
                                                                        .RECORD))));
        final ContractCompatibilityPolicy recordEnvelopePolicy =
                ContractCompatibilityPolicy.create(
                        "policy-record-envelope-v1",
                        closedRelease.contractFingerprint(),
                        recordEnvelopeChanges.stream().map(ContractAllowance::forChange).toList());
        final ContractResponse recordEnvelopeObserved =
                ContractResponseProfiler.forRuntime(
                                ContractObservationLimits.runtimeDefaults(),
                                ContractResponsePathBoundary.forRuntime(
                                        closedRelease, recordEnvelopePolicy))
                        .profile(
                                objectMapper.readTree(
                                        "[{\"id\":\"synthetic-c\",\"envelope\":{\"flag\":false}},{\"id\":\"synthetic-d\"}]"),
                                "$",
                                ContractResponse.Cardinality.ARRAY,
                                "/id");
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(closedRelease, recordEnvelopePolicy)
                        .validateResponse(recordEnvelopeObserved)
                        .status());
    }

    @Test
    void enforcesDepthPathAndNodeLimitsBeforeUnboundedGrowth() throws Exception {
        final JsonNode nested = objectMapper.readTree("[{\"id\":\"a\",\"one\":{\"two\":1}}]");
        final JsonNode twoFields = objectMapper.readTree("[{\"id\":\"a\",\"other\":1}]");

        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractResponseProfiler.forSyntheticFixtures(
                                        new ContractObservationLimits(1, 10, 100))
                                .profile(nested, "$", ContractResponse.Cardinality.ARRAY, "/id"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractResponseProfiler.forSyntheticFixtures(
                                        new ContractObservationLimits(10, 1, 100))
                                .profile(
                                        twoFields, "$", ContractResponse.Cardinality.ARRAY, "/id"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ContractResponseProfiler.forSyntheticFixtures(
                                        new ContractObservationLimits(10, 10, 1))
                                .profile(
                                        twoFields, "$", ContractResponse.Cardinality.ARRAY, "/id"));
    }

    @Test
    void rejectsInvalidRootUnsupportedNodesAndInvalidLimitConfigurations() throws Exception {
        final JsonNode array = objectMapper.readTree("[]");
        final JsonNode object = objectMapper.readTree("{\"id\":\"a\"}");

        assertThrows(
                IllegalArgumentException.class,
                () -> profiler.profile(object, "$", ContractResponse.Cardinality.ARRAY, "/id"));
        assertThrows(
                IllegalArgumentException.class,
                () -> profiler.profile(array, "$", ContractResponse.Cardinality.OBJECT, "/id"));
        assertThrows(
                IllegalArgumentException.class,
                () -> profiler.profile(object, "$", ContractResponse.Cardinality.SCALAR, "/id"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        profiler.profile(
                                BinaryNode.valueOf(new byte[] {1}),
                                "$",
                                ContractResponse.Cardinality.OBJECT,
                                "/id"));
        assertThrows(IllegalArgumentException.class, () -> new ContractObservationLimits(0, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractObservationLimits(
                                ContractObservationLimits.ABSOLUTE_MAXIMUM_DEPTH + 1, 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractObservationLimits(
                                1, ContractObservationLimits.ABSOLUTE_MAXIMUM_PATHS + 1, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new ContractObservationLimits(
                                1, 1, ContractObservationLimits.ABSOLUTE_MAXIMUM_NODES + 1));

        final JsonNode envelope = objectMapper.readTree("{\"data\":{\"records\":[]}}");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        profiler.profileWithEnvelope(
                                array,
                                "/data/other",
                                ContractResponse.Cardinality.ARRAY,
                                "/id",
                                envelope));
    }

    @Test
    void approvedAdditiveShapeAcceptsOnlyPageLocalSubsetsIncludingAllNull() throws Exception {
        final SourceContractRelease release =
                release(profile("[{\"id\":\"synthetic-a\"},{\"id\":\"synthetic-b\"}]"));
        final ContractCompatibilityPolicy closed =
                ContractCompatibilityPolicy.create(
                        "closed-v1", release.contractFingerprint(), List.of());

        final ContractResponse nullableFixture =
                profile(
                        "[{\"id\":\"synthetic-c\",\"extra\":\"x\"},"
                                + "{\"id\":\"synthetic-d\",\"extra\":null},"
                                + "{\"id\":\"synthetic-e\"}]");
        final ContractChange nullableChange =
                new ContractValidator(release, closed)
                        .classifyResponse(nullableFixture)
                        .changes()
                        .get(0);
        final ContractCompatibilityPolicy nullablePolicy =
                ContractCompatibilityPolicy.create(
                        "nullable-v1",
                        release.contractFingerprint(),
                        List.of(ContractAllowance.forChange(nullableChange)));
        final ContractResponseProfiler nullableRuntime =
                ContractResponseProfiler.forRuntime(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, nullablePolicy));
        final ContractResponse allNullPage =
                nullableRuntime.profile(
                        objectMapper.readTree("[{\"id\":\"synthetic-f\",\"extra\":null}]"),
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        "/id");
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(release, nullablePolicy)
                        .validateResponse(allNullPage)
                        .status());

        final ContractResponse unionFixture =
                profile(
                        "[{\"id\":\"synthetic-g\",\"variant\":\"x\"},"
                                + "{\"id\":\"synthetic-h\",\"variant\":1},"
                                + "{\"id\":\"synthetic-i\"}]");
        final ContractChange unionChange =
                new ContractValidator(release, closed)
                        .classifyResponse(unionFixture)
                        .changes()
                        .get(0);
        final ContractCompatibilityPolicy unionPolicy =
                ContractCompatibilityPolicy.create(
                        "union-v1",
                        release.contractFingerprint(),
                        List.of(ContractAllowance.forChange(unionChange)));
        final ContractResponseProfiler unionRuntime =
                ContractResponseProfiler.forRuntime(
                        ContractObservationLimits.runtimeDefaults(),
                        ContractResponsePathBoundary.forRuntime(release, unionPolicy));
        final ContractResponse stringOnlyPage =
                unionRuntime.profile(
                        objectMapper.readTree("[{\"id\":\"synthetic-j\",\"variant\":\"only\"}]"),
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        "/id");
        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(release, unionPolicy)
                        .validateResponse(stringOnlyPage)
                        .status());

        final ContractResponse outsideApprovedType =
                unionRuntime.profile(
                        objectMapper.readTree("[{\"id\":\"synthetic-k\",\"variant\":true}]"),
                        "$",
                        ContractResponse.Cardinality.ARRAY,
                        "/id");
        assertEquals(
                ContractDriftException.Reason.BREAKING_CHANGE,
                assertThrows(
                                ContractDriftException.class,
                                () ->
                                        new ContractValidator(release, unionPolicy)
                                                .validateResponse(outsideApprovedType))
                        .reason());
    }

    @Test
    void nullOnlyBaselineCanEvolveToAnExactlyApprovedObjectSubtree() throws Exception {
        final SourceContractRelease release =
                release(
                        profile(
                                "[{\"id\":\"synthetic-a\",\"note\":null},"
                                        + "{\"id\":\"synthetic-b\",\"note\":null}]"));
        final ContractCompatibilityPolicy closed =
                ContractCompatibilityPolicy.create(
                        "closed-v1", release.contractFingerprint(), List.of());
        final ContractResponse objectFixture =
                profile(
                        "[{\"id\":\"synthetic-c\",\"note\":{\"code\":\"x\"}},"
                                + "{\"id\":\"synthetic-d\",\"note\":{\"code\":\"y\"}}]");
        final List<ContractAllowance> allowances =
                new ContractValidator(release, closed)
                        .classifyResponse(objectFixture).changes().stream()
                                .map(ContractAllowance::forChange)
                                .toList();
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "object-v1", release.contractFingerprint(), allowances);
        final ContractResponse runtimeObservation =
                ContractResponseProfiler.forRuntime(
                                ContractObservationLimits.runtimeDefaults(),
                                ContractResponsePathBoundary.forRuntime(release, policy))
                        .profile(
                                objectMapper.readTree(
                                        "[{\"id\":\"synthetic-e\",\"note\":{\"code\":\"z\"}}]"),
                                "$",
                                ContractResponse.Cardinality.ARRAY,
                                "/id");

        assertEquals(
                ContractValidationResult.Status.ACCEPTED_WITH_ALERT,
                new ContractValidator(release, policy)
                        .validateResponse(runtimeObservation)
                        .status());
    }

    private ContractResponse profile(final String json) throws Exception {
        return profiler.profile(
                objectMapper.readTree(json), "$", ContractResponse.Cardinality.ARRAY, "/id");
    }

    private SourceContractRelease release(final ContractResponse response) {
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                "dataexport-synthetic",
                "v1",
                new ContractMetadata(
                        List.of(
                                new ContractMetadata.Element(
                                        ContractMetadata.ElementKind.DATA_FIELD,
                                        "id",
                                        ContractMetadata.DeclaredType.STRING)),
                        java.util.Optional.empty()),
                response);
    }

    private static void assertField(
            final ContractResponse response,
            final String path,
            final ContractResponse.Cardinality cardinality,
            final ContractResponse.Presence presence,
            final boolean nullable,
            final ContractResponse.JsonType type) {
        final ContractResponse.Field field = response.find(path).orElseThrow();
        assertEquals(cardinality, field.cardinality());
        assertEquals(presence, field.presence());
        assertEquals(nullable, field.nullable());
        assertEquals(List.of(type), field.jsonTypes());
    }

    private static void assertField(
            final ContractResponse response,
            final ContractResponse.FieldScope scope,
            final String path,
            final ContractResponse.Cardinality cardinality,
            final ContractResponse.Presence presence,
            final boolean nullable,
            final ContractResponse.JsonType type) {
        final ContractResponse.Field field = response.find(scope, path).orElseThrow();
        assertEquals(cardinality, field.cardinality());
        assertEquals(presence, field.presence());
        assertEquals(nullable, field.nullable());
        assertEquals(List.of(type), field.jsonTypes());
    }
}
