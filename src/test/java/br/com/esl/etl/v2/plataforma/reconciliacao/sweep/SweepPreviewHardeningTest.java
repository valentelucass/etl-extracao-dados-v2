package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.BINDING;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.POLICY;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SCOPE;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.SNAPSHOT;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.proofWith;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validEvidence;
import static br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewTestFixture.validScope;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.AnnotatedElement;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class SweepPreviewHardeningTest {
    @Test
    void applicabilityVocabularyHasExactlyFourStates() {
        assertEquals(
                Set.of("ENABLED", "DISABLED", "BLOCKED", "NOT_APPLICABLE"),
                Arrays.stream(SweepApplicability.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
    }

    @Test
    void everyProofPositionIsBoundToPolicyScopeSnapshotAndBinding() {
        final FailClosedSweepPreviewKernel kernel = new FailClosedSweepPreviewKernel();
        for (int index = 0; index < 4; index++) {
            assertEquals(
                    SweepPreviewBlockReason.EVIDENCE_POLICY_MISMATCH,
                    kernel.assess(validScope(), replaceProof(index, changedProof(index, 0)))
                            .reason());
            assertEquals(
                    SweepPreviewBlockReason.EVIDENCE_SCOPE_MISMATCH,
                    kernel.assess(validScope(), replaceProof(index, changedProof(index, 1)))
                            .reason());
            assertEquals(
                    SweepPreviewBlockReason.SNAPSHOT_FINGERPRINT_MISMATCH,
                    kernel.assess(validScope(), replaceProof(index, changedProof(index, 2)))
                            .reason());
            assertEquals(
                    SweepPreviewBlockReason.BINDING_FINGERPRINT_MISMATCH,
                    kernel.assess(validScope(), replaceProof(index, changedProof(index, 3)))
                            .reason());
        }
    }

    @Test
    void scopeRejectsStaleCanonicalPolicyAndBindingAfterPolicyMutation() {
        assertStale(
                SweepApplicability.DISABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                0,
                0,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.CHILD,
                true,
                true,
                false,
                0,
                0,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                false,
                true,
                false,
                0,
                0,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                false,
                false,
                0,
                0,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                true,
                0,
                0,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                1,
                0,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                0,
                1,
                0,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                0,
                0,
                1,
                0);
        assertStale(
                SweepApplicability.ENABLED,
                SweepScope.ResponsibilityKind.ROOT,
                true,
                true,
                false,
                0,
                0,
                0,
                1);
    }

    @Test
    void proofRejectsSameOccurrenceAndRunAndInvalidOrdinal() {
        assertThrows(
                IllegalArgumentException.class,
                () -> proofWith(1, '5', '5', POLICY, SCOPE, SNAPSHOT, BINDING));
        assertThrows(
                IllegalArgumentException.class,
                () -> proofWith(0, '5', '9', POLICY, SCOPE, SNAPSHOT, BINDING));
    }

    @Test
    void proofOrderAndCrossDimensionCollisionsAreRejectedByTheKernel() {
        final FailClosedSweepPreviewKernel kernel = new FailClosedSweepPreviewKernel();
        assertEquals(
                SweepPreviewBlockReason.TRAVERSAL_EVIDENCE_NOT_INDEPENDENT,
                kernel.assess(
                                validScope(),
                                replaceProof(
                                        1,
                                        proofWith(3, '6', 'a', POLICY, SCOPE, SNAPSHOT, BINDING)))
                        .reason());
        assertEquals(
                SweepPreviewBlockReason.CROSS_EVIDENCE_NOT_INDEPENDENT,
                kernel.assess(
                                validScope(),
                                replaceProof(
                                        2,
                                        proofWith(3, '9', 'b', POLICY, SCOPE, SNAPSHOT, BINDING)))
                        .reason());
    }

    @Test
    void valueObjectStringsRedactAllFingerprints() {
        final SweepScope scope = validScope();
        final SweepPreviewEvidence evidence = validEvidence();
        final String rendered =
                scope
                        + "|"
                        + evidence
                        + "|"
                        + evidence.firstTraversalEvidence()
                        + "|"
                        + evidence.secondTraversalEvidence()
                        + "|"
                        + evidence.firstAbsenceEvidence()
                        + "|"
                        + evidence.secondAbsenceEvidence()
                        + "|"
                        + new FailClosedSweepPreviewKernel().assess(scope, evidence);
        for (final String secret :
                Set.of(
                        POLICY,
                        BINDING,
                        SCOPE,
                        SNAPSHOT,
                        "5".repeat(64),
                        "6".repeat(64),
                        "7".repeat(64),
                        "8".repeat(64),
                        "9".repeat(64),
                        "a".repeat(64),
                        "b".repeat(64),
                        "c".repeat(64))) {
            assertFalse(rendered.contains(secret));
        }
    }

    @Test
    void publicSurfaceAndStoredStateAreConstantSpaceAndCapabilityFreeTransitively() {
        final ArrayDeque<Class<?>> pending = new ArrayDeque<>();
        pending.add(SweepApplicability.class);
        pending.add(SweepScope.class);
        pending.add(SweepPreviewBlockReason.class);
        pending.add(SweepPreviewEvidence.class);
        pending.add(SweepPreviewAssessment.class);
        pending.add(FailClosedSweepPreviewKernel.class);
        final Set<Class<?>> visited = new HashSet<>();
        while (!pending.isEmpty()) {
            final Class<?> type = pending.removeFirst();
            if (!visited.add(type)) {
                continue;
            }
            assertSafeName(type.getName());
            assertSafeType(type);
            pending.addAll(Arrays.asList(type.getDeclaredClasses()));
            for (final Field field : type.getDeclaredFields()) {
                if (!field.isSynthetic()) {
                    assertSafeName(field.getName());
                    if (!Modifier.isStatic(field.getModifiers())
                            || (!field.getType().isEnum()
                                    && field.getType() != java.util.regex.Pattern.class
                                    && field.getType() != String.class
                                    && !field.getType().isPrimitive())) {
                        assertSafeType(field.getGenericType());
                    }
                }
            }
            for (final RecordComponent component : nullToEmpty(type.getRecordComponents())) {
                assertSafeType(component.getGenericType());
                assertSafeAnnotations(component);
            }
            for (final Method method : type.getDeclaredMethods()) {
                if (!method.isSynthetic() && !isCompilerEnumArrayMethod(type, method)) {
                    assertSafeName(method.getName());
                    assertSafeExecutable(method);
                    assertSafeType(method.getGenericReturnType());
                }
            }
            Arrays.stream(type.getDeclaredConstructors()).forEach(this::assertSafeExecutable);
            Arrays.stream(type.getGenericInterfaces()).forEach(this::assertSafeType);
            assertSafeType(type.getGenericSuperclass());
            assertSafeAnnotations(type);
        }
    }

    @Test
    void nullsAndIncoherentAssessmentFailClosed() {
        assertThrows(
                NullPointerException.class,
                () -> new FailClosedSweepPreviewKernel().assess(null, validEvidence()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SweepPreviewAssessment(
                                SweepPreviewAssessment.Disposition.BLOCKED,
                                SweepPreviewBlockReason.NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY));
    }

    private static boolean isCompilerEnumArrayMethod(final Class<?> owner, final Method method) {
        return owner.isEnum()
                && (method.getName().equals("values") || method.getName().equals("$values"));
    }

    private void assertSafeExecutable(final Executable executable) {
        assertSafeName(executable.getDeclaringClass().getName());
        assertSafeAnnotations(executable);
        Arrays.stream(executable.getGenericParameterTypes()).forEach(this::assertSafeType);
        Arrays.stream(executable.getGenericExceptionTypes()).forEach(this::assertSafeType);
    }

    private void assertSafeAnnotations(final AnnotatedElement element) {
        Arrays.stream(element.getAnnotations())
                .forEach(annotation -> assertSafeType(annotation.annotationType()));
    }

    private void assertSafeType(final Type type) {
        if (type == null) {
            return;
        }
        final String name = type.getTypeName().toLowerCase(Locale.ROOT);
        assertFalse(
                name.matches(
                        ".*(contractpromotionpermit|runtimeaction|sourcedataeffect|runtimerole|capability).*"));
        assertFalse(
                name.matches(
                        ".*(collection|list|map|set|iterable|stream|iterator|spliterator|connection|sql).*"));
        if (type instanceof Class<?> clazz) {
            assertFalse(clazz.isArray(), "array type: " + name);
            assertFalse(Collection.class.isAssignableFrom(clazz));
            assertFalse(Map.class.isAssignableFrom(clazz));
            assertFalse(Iterable.class.isAssignableFrom(clazz));
            assertFalse(Stream.class.isAssignableFrom(clazz));
        }
    }

    private void assertSafeName(final String candidate) {
        if (candidate.equals("PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY")
                || candidate.equals("NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY")) {
            return;
        }
        final String name = candidate.toLowerCase(Locale.ROOT);
        assertFalse(
                name.matches(
                        ".*(apply|delete|deactivate|prune|persist|mutate|execute|sql|connection|"
                                + "callback|consumer|runnable|callable|permit|runtimeaction|"
                                + "sourcedataeffect|runtimerole|capability).*"),
                "forbidden member name: " + candidate);
    }

    private static RecordComponent[] nullToEmpty(final RecordComponent[] components) {
        return components == null ? new RecordComponent[0] : components;
    }

    private static void assertStale(
            final SweepApplicability applicability,
            final SweepScope.ResponsibilityKind kind,
            final boolean hierarchySafe,
            final boolean ownerPresent,
            final boolean emptyAllowed,
            final long invalid,
            final long quarantine,
            final long volume,
            final long history) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new SweepScope(
                                applicability,
                                kind,
                                hierarchySafe,
                                ownerPresent,
                                emptyAllowed,
                                invalid,
                                quarantine,
                                volume,
                                history,
                                POLICY,
                                BINDING,
                                SCOPE,
                                SNAPSHOT));
    }

    private static SweepPreviewEvidence.Proof changedProof(final int index, final int dimension) {
        final long ordinal = index + 1L;
        final char occurrence = "5678".charAt(index);
        final char run = "9abc".charAt(index);
        return proofWith(
                ordinal,
                occurrence,
                run,
                dimension == 0 ? "d".repeat(64) : POLICY,
                dimension == 1 ? "d".repeat(64) : SCOPE,
                dimension == 2 ? "d".repeat(64) : SNAPSHOT,
                dimension == 3 ? "d".repeat(64) : BINDING);
    }

    private static SweepPreviewEvidence replaceProof(
            final int index, final SweepPreviewEvidence.Proof replacement) {
        final SweepPreviewEvidence base = validEvidence();
        return SweepPreviewTestFixture.evidence(
                base.executionMode(),
                base.completenessStatus(),
                base.evidenceStatus(),
                base.firstTraversalComplete(),
                base.secondTraversalComplete(),
                base.expectedPageCount(),
                base.firstVisitedPageCount(),
                base.secondVisitedPageCount(),
                base.missingPageCount(),
                base.capReached(),
                base.timeoutCount(),
                base.cancellationCount(),
                base.emptySource(),
                base.localTerminalObserved(),
                base.hasNextFalseObserved(),
                base.invalidCount(),
                base.quarantineCount(),
                base.volumeMismatchCount(),
                base.historyGapCount(),
                index == 0 ? replacement : base.firstTraversalEvidence(),
                index == 1 ? replacement : base.secondTraversalEvidence(),
                index == 2 ? replacement : base.firstAbsenceEvidence(),
                index == 3 ? replacement : base.secondAbsenceEvidence());
    }
}
