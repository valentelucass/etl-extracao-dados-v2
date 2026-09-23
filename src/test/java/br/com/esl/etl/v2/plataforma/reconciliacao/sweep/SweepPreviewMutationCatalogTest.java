package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceCompleteness;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceCounts;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceExpectedPages;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceMode;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceProof;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceRuntime;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceStatus;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.FailClosedSweepPreviewKernelTest.replaceTraversal;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.BINDING;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.POLICY;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SCOPE;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SNAPSHOT;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.proofWith;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.scope;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.scopeWithKind;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validEvidence;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validScope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

class SweepPreviewMutationCatalogTest {
    private static final String RESOURCE =
            "/contracts/v2-013/q-swp-fnd-01/kernel-cases-v01.synthetic.json";
    private static final String RESOURCE_SHA256 =
            "596aba786543ab2dcc77190a14c1f918ccc4bd2f502312644d5dd2a8abd6f215";
    private static final ObjectMapper MAPPER =
            JsonMapper.builder()
                    .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES)
                    .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .build();
    private static final Map<String, ExpectedCase> EXPECTED = expectedCases();

    @Test
    void catalogBytesSchemaAndInventoryAreClosed() throws Exception {
        final byte[] bytes = resourceBytes();
        assertEquals(RESOURCE_SHA256, sha256(bytes));
        assertFalse(
                bytes.length >= 3
                        && bytes[0] == (byte) 0xEF
                        && bytes[1] == (byte) 0xBB
                        && bytes[2] == (byte) 0xBF);
        decodeUtf8Strict(bytes);

        final JsonNode root = MAPPER.readTree(bytes);
        assertEquals(Set.of("schemaVersion", "task", "fixtureKind", "cases"), fieldNames(root));
        for (final JsonNode item : root.path("cases")) {
            assertEquals(Set.of("id", "mutation", "expectedReason"), fieldNames(item));
        }
        final Catalog catalog = MAPPER.treeToValue(root, Catalog.class);
        assertEquals("2026-09-06.v2-013.q-swp-fnd-01.1", catalog.schemaVersion());
        assertEquals("V2-013/FUNDACAO_KERNEL_LOCAL", catalog.task());
        assertEquals("SYNTHETIC_LOCAL_PREVIEW_ONLY", catalog.fixtureKind());
        final Map<String, ExpectedCase> actual = new LinkedHashMap<>();
        for (final CaseSpec item : catalog.cases()) {
            final ExpectedCase previous =
                    actual.put(item.id(), new ExpectedCase(item.mutation(), item.expectedReason()));
            if (previous != null) {
                throw new AssertionError("Duplicate case id");
            }
        }
        assertEquals(EXPECTED, actual);
        assertEquals(53, actual.size());
        assertEquals(53, actual.values().stream().map(ExpectedCase::mutation).distinct().count());
    }

    @TestFactory
    Stream<DynamicTest> executesOneHappyCaseAndFiftyTwoRealFailClosedMutations() throws Exception {
        final Catalog catalog = MAPPER.readValue(resourceBytes(), Catalog.class);
        return catalog.cases().stream()
                .map(
                        item ->
                                DynamicTest.dynamicTest(
                                        item.id() + ":" + item.mutation(),
                                        () ->
                                                assertEquals(
                                                        item.expectedReason(),
                                                        execute(item.mutation()))));
    }

    private static String execute(final Mutation mutation) {
        try {
            final SweepPreviewEvidence valid = validEvidence();
            final FailClosedSweepPreviewKernel kernel = new FailClosedSweepPreviewKernel();
            final Evaluation evaluation =
                    switch (mutation) {
                        case NONE -> new Evaluation(validScope(), valid);
                        case MODE_INCREMENTAL ->
                                new Evaluation(
                                        validScope(),
                                        replaceMode(valid, ExecutionMode.INCREMENTAL));
                        case MODE_BOOTSTRAP ->
                                new Evaluation(
                                        validScope(), replaceMode(valid, ExecutionMode.BOOTSTRAP));
                        case MODE_BACKFILL ->
                                new Evaluation(
                                        validScope(), replaceMode(valid, ExecutionMode.BACKFILL));
                        case MODE_REPLAY ->
                                new Evaluation(
                                        validScope(), replaceMode(valid, ExecutionMode.REPLAY));
                        case APPLICABILITY_DISABLED ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.DISABLED,
                                                true,
                                                true,
                                                false,
                                                0,
                                                0,
                                                0,
                                                0),
                                        valid);
                        case APPLICABILITY_BLOCKED ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.BLOCKED,
                                                true,
                                                true,
                                                false,
                                                0,
                                                0,
                                                0,
                                                0),
                                        valid);
                        case APPLICABILITY_NOT_APPLICABLE ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.NOT_APPLICABLE,
                                                true,
                                                true,
                                                false,
                                                0,
                                                0,
                                                0,
                                                0),
                                        valid);
                        case KIND_HISTORY -> withKind(SweepScope.ResponsibilityKind.HISTORY, valid);
                        case KIND_ONE_TO_ONE_COMPONENT ->
                                withKind(SweepScope.ResponsibilityKind.ONE_TO_ONE_COMPONENT, valid);
                        case KIND_CONDITIONAL_COMPONENT ->
                                withKind(
                                        SweepScope.ResponsibilityKind.CONDITIONAL_COMPONENT, valid);
                        case KIND_UNRESOLVED_CANDIDATE ->
                                withKind(SweepScope.ResponsibilityKind.UNRESOLVED_CANDIDATE, valid);
                        case KIND_CHILD -> withKind(SweepScope.ResponsibilityKind.CHILD, valid);
                        case KIND_OBSERVATION_CHANNEL ->
                                withKind(SweepScope.ResponsibilityKind.OBSERVATION_CHANNEL, valid);
                        case KIND_REFERENCE ->
                                withKind(SweepScope.ResponsibilityKind.REFERENCE, valid);
                        case KIND_SOURCE_LINE ->
                                withKind(SweepScope.ResponsibilityKind.SOURCE_LINE, valid);
                        case COMPLETENESS_BLOCKED ->
                                new Evaluation(
                                        validScope(),
                                        replaceCompleteness(
                                                valid,
                                                SourceCompletenessStatus
                                                        .BLOCKED_NO_COMPLETENESS_PROOF));
                        case EVIDENCE_FAILED ->
                                new Evaluation(
                                        validScope(),
                                        replaceStatus(
                                                valid, SweepPreviewEvidence.EvidenceStatus.FAILED));
                        case EVIDENCE_UNVERIFIED ->
                                new Evaluation(
                                        validScope(),
                                        replaceStatus(
                                                valid,
                                                SweepPreviewEvidence.EvidenceStatus.UNVERIFIED));
                        case TERMINAL_ONLY -> new Evaluation(validScope(), terminalOnly(valid));
                        case FIRST_TRAVERSAL_INCOMPLETE ->
                                new Evaluation(
                                        validScope(),
                                        replaceTraversal(valid, false, true, 2, 2, 2, 0));
                        case SECOND_TRAVERSAL_INCOMPLETE ->
                                new Evaluation(
                                        validScope(),
                                        replaceTraversal(valid, true, false, 2, 2, 2, 0));
                        case VISITED_PAGE_MISMATCH ->
                                new Evaluation(
                                        validScope(),
                                        replaceTraversal(valid, true, true, 2, 1, 2, 0));
                        case MISSING_PAGE ->
                                new Evaluation(
                                        validScope(),
                                        replaceTraversal(valid, true, true, 2, 2, 2, 1));
                        case CAP_REACHED ->
                                new Evaluation(
                                        validScope(), replaceRuntime(valid, true, 0, 0, false));
                        case TIMEOUT ->
                                new Evaluation(
                                        validScope(), replaceRuntime(valid, false, 1, 0, false));
                        case CANCELLATION ->
                                new Evaluation(
                                        validScope(), replaceRuntime(valid, false, 0, 1, false));
                        case EMPTY_ANOMALOUS ->
                                new Evaluation(
                                        validScope(), replaceRuntime(valid, false, 0, 0, true));
                        case INVALID_OVER_LIMIT ->
                                new Evaluation(validScope(), replaceCounts(valid, 1, 0, 0, 0));
                        case QUARANTINE_OVER_LIMIT ->
                                new Evaluation(validScope(), replaceCounts(valid, 0, 1, 0, 0));
                        case VOLUME_OVER_LIMIT ->
                                new Evaluation(validScope(), replaceCounts(valid, 0, 0, 1, 0));
                        case HISTORY_OVER_LIMIT ->
                                new Evaluation(validScope(), replaceCounts(valid, 0, 0, 0, 1));
                        case HIERARCHY_UNSAFE ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.ENABLED,
                                                false,
                                                true,
                                                false,
                                                0,
                                                0,
                                                0,
                                                0),
                                        valid);
                        case OWNER_MISSING ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.ENABLED,
                                                true,
                                                false,
                                                false,
                                                0,
                                                0,
                                                0,
                                                0),
                                        valid);
                        case POLICY_MISMATCH ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                0,
                                                proofWith(
                                                        1,
                                                        '5',
                                                        '9',
                                                        "d".repeat(64),
                                                        SCOPE,
                                                        SNAPSHOT,
                                                        BINDING)));
                        case SCOPE_MISMATCH ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                1,
                                                proofWith(
                                                        2,
                                                        '6',
                                                        'a',
                                                        POLICY,
                                                        "d".repeat(64),
                                                        SNAPSHOT,
                                                        BINDING)));
                        case SNAPSHOT_MISMATCH ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                2,
                                                proofWith(
                                                        3,
                                                        '7',
                                                        'b',
                                                        POLICY,
                                                        SCOPE,
                                                        "d".repeat(64),
                                                        BINDING)));
                        case BINDING_MISMATCH ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                3,
                                                proofWith(
                                                        4,
                                                        '8',
                                                        'c',
                                                        POLICY,
                                                        SCOPE,
                                                        SNAPSHOT,
                                                        "d".repeat(64))));
                        case TRAVERSAL_SAME_OCCURRENCE ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                1,
                                                proofWith(
                                                        2, '5', 'a', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case TRAVERSAL_SAME_RUN ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                1,
                                                proofWith(
                                                        2, '6', '9', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case ABSENCE_SAME_OCCURRENCE ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                3,
                                                proofWith(
                                                        4, '7', 'c', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case ABSENCE_SAME_RUN ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                3,
                                                proofWith(
                                                        4, '8', 'b', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case CROSS_SAME_OCCURRENCE ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                2,
                                                proofWith(
                                                        3, '5', 'b', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case CROSS_SAME_RUN ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                2,
                                                proofWith(
                                                        3, '7', '9', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case ZERO_PAGES ->
                                new Evaluation(
                                        validScope(),
                                        replaceTraversal(valid, true, true, 0, 0, 0, 0));
                        case NEGATIVE_COUNT ->
                                new Evaluation(validScope(), replaceCounts(valid, -1, 0, 0, 0));
                        case OBSERVATION_LIMIT_OVERFLOW ->
                                new Evaluation(
                                        validScope(),
                                        replaceExpectedPages(
                                                valid,
                                                SweepPreviewEvidence.MAX_OBSERVATION_COUNT + 1));
                        case POLICY_LIMIT_OVERFLOW ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.ENABLED,
                                                true,
                                                true,
                                                false,
                                                SweepScope.MAX_POLICY_LIMIT + 1,
                                                0,
                                                0,
                                                0),
                                        valid);
                        case MALFORMED_FINGERPRINT ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                0,
                                                proofWith(
                                                        1, '5', '9', POLICY, SCOPE, SNAPSHOT,
                                                        "bad")));
                        case PROOF_OCCURRENCE_EQUALS_RUN ->
                                new Evaluation(
                                        validScope(),
                                        replaceProof(
                                                valid,
                                                0,
                                                proofWith(
                                                        1, '5', '5', POLICY, SCOPE, SNAPSHOT,
                                                        BINDING)));
                        case MULTIPLE_FAILURES ->
                                new Evaluation(
                                        scope(
                                                SweepApplicability.DISABLED,
                                                false,
                                                false,
                                                false,
                                                0,
                                                0,
                                                0,
                                                0),
                                        replaceMode(
                                                replaceCounts(valid, 1, 1, 1, 1),
                                                ExecutionMode.REPLAY));
                        case STALE_POLICY_FINGERPRINT -> new Evaluation(stalePolicyScope(), valid);
                        case STALE_BINDING_FINGERPRINT ->
                                new Evaluation(staleBindingScope(), valid);
                    };
            return kernel.assess(evaluation.scope(), evaluation.evidence()).reason().name();
        } catch (final IllegalArgumentException expected) {
            return "CONSTRUCTION_REJECTED";
        }
    }

    private static Evaluation withKind(
            final SweepScope.ResponsibilityKind kind, final SweepPreviewEvidence evidence) {
        return new Evaluation(
                scopeWithKind(SweepApplicability.ENABLED, kind, true, true, false, 0, 0, 0, 0),
                evidence);
    }

    private static SweepPreviewEvidence terminalOnly(final SweepPreviewEvidence base) {
        return SweepPreviewTestFixture.evidence(
                ExecutionMode.SWEEP,
                SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF,
                SweepPreviewEvidence.EvidenceStatus.PROVEN,
                true,
                true,
                2,
                2,
                2,
                0,
                false,
                0,
                0,
                false,
                true,
                true,
                0,
                0,
                0,
                0,
                base.firstTraversalEvidence(),
                base.secondTraversalEvidence(),
                base.firstAbsenceEvidence(),
                base.secondAbsenceEvidence());
    }

    private static SweepScope stalePolicyScope() {
        return new SweepScope(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                1,
                0,
                0,
                0,
                POLICY,
                BINDING,
                SCOPE,
                SNAPSHOT);
    }

    private static SweepScope staleBindingScope() {
        final String changedSnapshot = "d".repeat(64);
        return new SweepScope(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                0,
                0,
                0,
                0,
                POLICY,
                BINDING,
                SCOPE,
                changedSnapshot);
    }

    private static byte[] resourceBytes() throws IOException {
        try (InputStream input =
                SweepPreviewMutationCatalogTest.class.getResourceAsStream(RESOURCE)) {
            if (input == null) {
                throw new IOException("Synthetic fixture missing");
            }
            return input.readAllBytes();
        }
    }

    private static void decodeUtf8Strict(final byte[] bytes) throws CharacterCodingException {
        StandardCharsets.UTF_8
                .newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes));
    }

    private static Set<String> fieldNames(final JsonNode node) {
        final java.util.HashSet<String> names = new java.util.HashSet<>();
        node.fieldNames()
                .forEachRemaining(
                        name -> {
                            if (!names.add(name)) {
                                throw new AssertionError("Duplicate field");
                            }
                        });
        return Set.copyOf(names);
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static Map<String, ExpectedCase> expectedCases() {
        final LinkedHashMap<String, ExpectedCase> expected = new LinkedHashMap<>();
        add(expected, "SWP-HAPPY-001", Mutation.NONE, "NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY");
        add(expected, "SWP-MODE-001", Mutation.MODE_INCREMENTAL, "MODE_NOT_SWEEP");
        add(expected, "SWP-MODE-002", Mutation.MODE_BOOTSTRAP, "MODE_NOT_SWEEP");
        add(expected, "SWP-MODE-003", Mutation.MODE_BACKFILL, "MODE_NOT_SWEEP");
        add(expected, "SWP-MODE-004", Mutation.MODE_REPLAY, "MODE_NOT_SWEEP");
        add(expected, "SWP-APP-001", Mutation.APPLICABILITY_DISABLED, "APPLICABILITY_NOT_ENABLED");
        add(expected, "SWP-APP-002", Mutation.APPLICABILITY_BLOCKED, "APPLICABILITY_NOT_ENABLED");
        add(
                expected,
                "SWP-APP-003",
                Mutation.APPLICABILITY_NOT_APPLICABLE,
                "APPLICABILITY_NOT_ENABLED");
        add(expected, "SWP-KIND-001", Mutation.KIND_HISTORY, "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(
                expected,
                "SWP-KIND-002",
                Mutation.KIND_ONE_TO_ONE_COMPONENT,
                "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(
                expected,
                "SWP-KIND-003",
                Mutation.KIND_CONDITIONAL_COMPONENT,
                "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(
                expected,
                "SWP-KIND-004",
                Mutation.KIND_UNRESOLVED_CANDIDATE,
                "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(expected, "SWP-KIND-005", Mutation.KIND_CHILD, "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(
                expected,
                "SWP-KIND-006",
                Mutation.KIND_OBSERVATION_CHANNEL,
                "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(expected, "SWP-KIND-007", Mutation.KIND_REFERENCE, "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(
                expected,
                "SWP-KIND-008",
                Mutation.KIND_SOURCE_LINE,
                "RESPONSIBILITY_KIND_NOT_ELIGIBLE");
        add(
                expected,
                "SWP-PROOF-001",
                Mutation.COMPLETENESS_BLOCKED,
                "SOURCE_COMPLETENESS_NOT_PROVEN");
        add(expected, "SWP-PROOF-002", Mutation.EVIDENCE_FAILED, "EVIDENCE_STATUS_NOT_PROVEN");
        add(expected, "SWP-PROOF-003", Mutation.EVIDENCE_UNVERIFIED, "EVIDENCE_STATUS_NOT_PROVEN");
        add(expected, "SWP-PROOF-004", Mutation.TERMINAL_ONLY, "SOURCE_COMPLETENESS_NOT_PROVEN");
        add(expected, "SWP-TRAV-001", Mutation.FIRST_TRAVERSAL_INCOMPLETE, "TRAVERSAL_INCOMPLETE");
        add(expected, "SWP-TRAV-002", Mutation.SECOND_TRAVERSAL_INCOMPLETE, "TRAVERSAL_INCOMPLETE");
        add(expected, "SWP-TRAV-003", Mutation.VISITED_PAGE_MISMATCH, "TRAVERSAL_INCOMPLETE");
        add(expected, "SWP-TRAV-004", Mutation.MISSING_PAGE, "MISSING_PAGE");
        add(expected, "SWP-RUNTIME-001", Mutation.CAP_REACHED, "CAP_REACHED");
        add(expected, "SWP-RUNTIME-002", Mutation.TIMEOUT, "TIMEOUT_OR_CANCELLATION");
        add(expected, "SWP-RUNTIME-003", Mutation.CANCELLATION, "TIMEOUT_OR_CANCELLATION");
        add(expected, "SWP-RUNTIME-004", Mutation.EMPTY_ANOMALOUS, "ANOMALOUS_EMPTY_SOURCE");
        add(expected, "SWP-LIMIT-001", Mutation.INVALID_OVER_LIMIT, "INVALID_LIMIT_EXCEEDED");
        add(expected, "SWP-LIMIT-002", Mutation.QUARANTINE_OVER_LIMIT, "QUARANTINE_LIMIT_EXCEEDED");
        add(expected, "SWP-LIMIT-003", Mutation.VOLUME_OVER_LIMIT, "VOLUME_LIMIT_EXCEEDED");
        add(expected, "SWP-LIMIT-004", Mutation.HISTORY_OVER_LIMIT, "HISTORY_LIMIT_EXCEEDED");
        add(expected, "SWP-GOV-001", Mutation.HIERARCHY_UNSAFE, "HIERARCHY_UNSAFE");
        add(expected, "SWP-GOV-002", Mutation.OWNER_MISSING, "NOMINAL_OWNER_MISSING");
        add(expected, "SWP-BIND-001", Mutation.POLICY_MISMATCH, "EVIDENCE_POLICY_MISMATCH");
        add(expected, "SWP-BIND-002", Mutation.SCOPE_MISMATCH, "EVIDENCE_SCOPE_MISMATCH");
        add(expected, "SWP-BIND-003", Mutation.SNAPSHOT_MISMATCH, "SNAPSHOT_FINGERPRINT_MISMATCH");
        add(expected, "SWP-BIND-004", Mutation.BINDING_MISMATCH, "BINDING_FINGERPRINT_MISMATCH");
        add(
                expected,
                "SWP-INDEP-001",
                Mutation.TRAVERSAL_SAME_OCCURRENCE,
                "TRAVERSAL_EVIDENCE_NOT_INDEPENDENT");
        add(
                expected,
                "SWP-INDEP-002",
                Mutation.TRAVERSAL_SAME_RUN,
                "TRAVERSAL_EVIDENCE_NOT_INDEPENDENT");
        add(
                expected,
                "SWP-INDEP-003",
                Mutation.ABSENCE_SAME_OCCURRENCE,
                "ABSENCE_EVIDENCE_NOT_INDEPENDENT");
        add(
                expected,
                "SWP-INDEP-004",
                Mutation.ABSENCE_SAME_RUN,
                "ABSENCE_EVIDENCE_NOT_INDEPENDENT");
        add(
                expected,
                "SWP-INDEP-005",
                Mutation.CROSS_SAME_OCCURRENCE,
                "CROSS_EVIDENCE_NOT_INDEPENDENT");
        add(expected, "SWP-INDEP-006", Mutation.CROSS_SAME_RUN, "CROSS_EVIDENCE_NOT_INDEPENDENT");
        add(expected, "SWP-TRAV-005", Mutation.ZERO_PAGES, "TRAVERSAL_INCOMPLETE");
        add(expected, "SWP-CONSTRUCT-001", Mutation.NEGATIVE_COUNT, "CONSTRUCTION_REJECTED");
        add(
                expected,
                "SWP-CONSTRUCT-002",
                Mutation.OBSERVATION_LIMIT_OVERFLOW,
                "CONSTRUCTION_REJECTED");
        add(expected, "SWP-CONSTRUCT-003", Mutation.POLICY_LIMIT_OVERFLOW, "CONSTRUCTION_REJECTED");
        add(expected, "SWP-CONSTRUCT-004", Mutation.MALFORMED_FINGERPRINT, "CONSTRUCTION_REJECTED");
        add(
                expected,
                "SWP-CONSTRUCT-005",
                Mutation.PROOF_OCCURRENCE_EQUALS_RUN,
                "CONSTRUCTION_REJECTED");
        add(expected, "SWP-PRECEDENCE-001", Mutation.MULTIPLE_FAILURES, "MODE_NOT_SWEEP");
        add(
                expected,
                "SWP-CONSTRUCT-006",
                Mutation.STALE_POLICY_FINGERPRINT,
                "CONSTRUCTION_REJECTED");
        add(
                expected,
                "SWP-CONSTRUCT-007",
                Mutation.STALE_BINDING_FINGERPRINT,
                "CONSTRUCTION_REJECTED");
        return Map.copyOf(expected);
    }

    private static void add(
            final Map<String, ExpectedCase> target,
            final String id,
            final Mutation mutation,
            final String reason) {
        target.put(id, new ExpectedCase(mutation, reason));
    }

    private record Catalog(
            String schemaVersion, String task, String fixtureKind, List<CaseSpec> cases) {}

    private record CaseSpec(String id, Mutation mutation, String expectedReason) {}

    private record ExpectedCase(Mutation mutation, String expectedReason) {}

    private record Evaluation(SweepScope scope, SweepPreviewEvidence evidence) {}

    private enum Mutation {
        NONE,
        MODE_INCREMENTAL,
        MODE_BOOTSTRAP,
        MODE_BACKFILL,
        MODE_REPLAY,
        APPLICABILITY_DISABLED,
        APPLICABILITY_BLOCKED,
        APPLICABILITY_NOT_APPLICABLE,
        KIND_HISTORY,
        KIND_ONE_TO_ONE_COMPONENT,
        KIND_CONDITIONAL_COMPONENT,
        KIND_UNRESOLVED_CANDIDATE,
        KIND_CHILD,
        KIND_OBSERVATION_CHANNEL,
        KIND_REFERENCE,
        KIND_SOURCE_LINE,
        COMPLETENESS_BLOCKED,
        EVIDENCE_FAILED,
        EVIDENCE_UNVERIFIED,
        TERMINAL_ONLY,
        FIRST_TRAVERSAL_INCOMPLETE,
        SECOND_TRAVERSAL_INCOMPLETE,
        VISITED_PAGE_MISMATCH,
        MISSING_PAGE,
        CAP_REACHED,
        TIMEOUT,
        CANCELLATION,
        EMPTY_ANOMALOUS,
        INVALID_OVER_LIMIT,
        QUARANTINE_OVER_LIMIT,
        VOLUME_OVER_LIMIT,
        HISTORY_OVER_LIMIT,
        HIERARCHY_UNSAFE,
        OWNER_MISSING,
        POLICY_MISMATCH,
        SCOPE_MISMATCH,
        SNAPSHOT_MISMATCH,
        BINDING_MISMATCH,
        TRAVERSAL_SAME_OCCURRENCE,
        TRAVERSAL_SAME_RUN,
        ABSENCE_SAME_OCCURRENCE,
        ABSENCE_SAME_RUN,
        CROSS_SAME_OCCURRENCE,
        CROSS_SAME_RUN,
        ZERO_PAGES,
        NEGATIVE_COUNT,
        OBSERVATION_LIMIT_OVERFLOW,
        POLICY_LIMIT_OVERFLOW,
        MALFORMED_FINGERPRINT,
        PROOF_OCCURRENCE_EQUALS_RUN,
        MULTIPLE_FAILURES,
        STALE_POLICY_FINGERPRINT,
        STALE_BINDING_FINGERPRINT
    }
}
