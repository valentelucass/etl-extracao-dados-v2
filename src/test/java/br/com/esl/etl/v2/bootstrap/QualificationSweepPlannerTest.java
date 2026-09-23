package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.SyntheticCollectionSnapshot;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationSweepPlannerTest {
    private static SyntheticCollectionSnapshot snapshot() {
        return new SyntheticCollectionSnapshot(
                UUID.randomUUID(), LocalDate.of(2036, 4, 3), 6, true);
    }

    @Test
    void allThirtyThreeCatalogueDecisionsReachKernelWithoutNominalActivation() {
        final var planner = new SweepResponsibilityPlanner();
        final var snapshot = snapshot();
        final var bindings =
                planner.bindings(snapshot.scope().scopeFingerprint(), snapshot.fingerprint());
        final var previews =
                planner.preview(
                        bindings,
                        snapshot.verifiedEvidence(
                                List.of(
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID(),
                                        UUID.randomUUID()),
                                9));
        assertEquals(33, previews.size());
        assertEquals(
                33, previews.stream().map(value -> value.responsibility().id()).distinct().count());
        assertTrue(
                previews.stream()
                        .allMatch(
                                row ->
                                        row.responsibility().applicability()
                                                        != SweepApplicability.ENABLED
                                                && row.assessment().disposition()
                                                        == SweepPreviewAssessment.Disposition
                                                                .BLOCKED));
        assertTrue(
                previews.stream()
                        .anyMatch(
                                row ->
                                        row.responsibility().id().equals("SWP-MANIFESTOS-PICK")
                                                && row.responsibility()
                                                        .parent()
                                                        .equals("SWP-MANIFESTOS-ROOT")
                                                && row.responsibility()
                                                        .hierarchyPolicy()
                                                        .contains("PROPAGATION_PENDING")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"CYCLE", "ORPHAN", "SCOPE", "PARENT", "DUPLICATE", "COUNT"})
    void malformedHierarchyNeverReachesCapture(final String failure) {
        final var planner = new SweepResponsibilityPlanner();
        final var snapshot = snapshot();
        final var bindings =
                new ArrayList<>(
                        planner.bindings(
                                snapshot.scope().scopeFingerprint(), snapshot.fingerprint()));
        final var root = bindings.get(0);
        final var child = bindings.get(1);
        switch (failure) {
            case "CYCLE" ->
                    bindings.set(
                            0,
                            new SweepResponsibilityPlanner.Binding(
                                    root.id(), child.id(), root.scope(), root.snapshot()));
            case "ORPHAN" ->
                    bindings.set(
                            1,
                            new SweepResponsibilityPlanner.Binding(
                                    child.id(), "SWP-UNKNOWN", child.scope(), child.snapshot()));
            case "SCOPE" ->
                    bindings.set(
                            1,
                            new SweepResponsibilityPlanner.Binding(
                                    child.id(), child.parent(), "a".repeat(64), child.snapshot()));
            case "PARENT" ->
                    bindings.set(
                            1,
                            new SweepResponsibilityPlanner.Binding(
                                    child.id(), "", child.scope(), child.snapshot()));
            case "DUPLICATE" -> bindings.set(1, root);
            case "COUNT" -> bindings.remove(1);
            default -> throw new AssertionError();
        }
        assertThrows(IllegalArgumentException.class, () -> planner.validate(bindings));
    }

    @Test
    void proofFromAnotherSnapshotCannotBeReboundToNominalPreview() {
        final var planner = new SweepResponsibilityPlanner();
        final var first = snapshot();
        final var second = snapshot();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        planner.preview(
                                planner.bindings(
                                        first.scope().scopeFingerprint(), first.fingerprint()),
                                second.verifiedEvidence(
                                        List.of(
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID(),
                                                UUID.randomUUID()),
                                        3)));
    }
}
