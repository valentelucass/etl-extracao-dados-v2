package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.plataforma.identidade.FirstWaveIdentityContract;
import br.com.esl.etl.v2.plataforma.identidade.ScopedSourceIdentity;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Offline port invariants only; booleans do not qualify a physical owner or supplier admission. */
class RuntimeDevSampleFinishPortTest {
    static ExpectedRoot root(final int rows) {
        return new ExpectedRoot(
                new ScopedSourceIdentity(
                        "component",
                        "component",
                        FirstWaveIdentityContract.Entity.COLETAS,
                        new ScopedSourceIdentity.SourceKey(
                                ScopedSourceIdentity.WireType.INTEGER, "INTEGER:731")),
                OptionalInt.of(rows),
                Map.of(),
                Set.of(1));
    }

    @Test
    void expectationIsCopiedBoundedAndRedacted() {
        final var id = UUID.randomUUID();
        final var roots = new ArrayList<>(List.of(root(2)));
        final var pages = new LinkedHashMap<>(Map.of(1, 1));
        final var expected = new RuntimeDevEngineBankBinding.SampleExpectation(id, 2, roots, pages);
        roots.clear();
        pages.clear();
        assertEquals(id, expected.executionId());
        assertEquals(2, expected.physicalRows());
        assertEquals(1, expected.expectedRoots().size());
        assertEquals(Map.of(1, 1), expected.batchPages());
        assertThrows(UnsupportedOperationException.class, () -> expected.expectedRoots().clear());
        assertThrows(UnsupportedOperationException.class, () -> expected.batchPages().clear());
        assertFalse(expected.toString().contains(id.toString()));
        assertFalse(expected.toString().contains("731"));
    }

    @Test
    void invalidMultiplicityPageAndAboveTwelveAreRefused() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleExpectation(
                                UUID.randomUUID(), 13, List.of(root(13)), Map.of(1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleExpectation(
                                UUID.randomUUID(), 2, List.of(root(1)), Map.of(1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleExpectation(
                                UUID.randomUUID(), 2, List.of(root(2)), Map.of(1, 2)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleFinish(
                                Optional.empty(),
                                RuntimeExitCategory.SUCCESS,
                                RuntimeDevEngineBankBinding.SampleOutcome.FAILED));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleFinish(
                                Optional.empty(),
                                RuntimeExitCategory.DEGRADED,
                                RuntimeDevEngineBankBinding.SampleOutcome.PARTIAL_BOUNDARY));
    }

    @Test
    void ownerEvidenceMustMatchCountsAndEveryCleanupStep() {
        final var expected =
                new RuntimeDevEngineBankBinding.SampleExpectation(
                        UUID.randomUUID(), 2, List.of(root(2)), Map.of(1, 1));
        final var finish =
                new RuntimeDevEngineBankBinding.SampleFinish(
                        Optional.of(expected),
                        RuntimeExitCategory.DEGRADED,
                        RuntimeDevEngineBankBinding.SampleOutcome.PARTIAL_BOUNDARY);
        assertTrue(
                new RuntimeDevEngineBankBinding.SampleReadback(
                                true, true, true, true, true, 2, 1, 1, 100)
                        .matches(finish));
        assertFalse(
                new RuntimeDevEngineBankBinding.SampleReadback(
                                true, true, true, true, true, 1, 1, 1, 100)
                        .matches(finish));
        assertFalse(
                new RuntimeDevEngineBankBinding.SampleReadback(
                                true, true, true, false, true, 2, 1, 1, 100)
                        .matches(finish));
        assertFalse(
                new RuntimeDevEngineBankBinding.SampleReadback(
                                true, true, true, true, false, 2, 1, 1, 100)
                        .matches(finish));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleReadback(
                                true, true, true, true, true, 2, 1, 1, 129));
        assertFalse(finish.toString().contains(expected.executionId().toString()));
    }

    @Test
    void historicalSessionFinishRefusesWithoutDataSourceOrCheckpoint() {
        final RuntimeDevEngineBankBinding.Session old =
                new RuntimeDevEngineBankBinding.Session() {
                    @Override
                    public javax.sql.DataSource dataSource() {
                        throw new AssertionError("DATA_SOURCE_NOT_ALLOWED");
                    }

                    @Override
                    public void checkpoint() {
                        throw new AssertionError("CHECKPOINT_NOT_ALLOWED");
                    }

                    @Override
                    public void close() {}
                };
        final var refused =
                assertThrows(
                        RuntimeDevEngineBankBinding.Refusal.class,
                        () ->
                                old.finishSample(
                                        new RuntimeDevEngineBankBinding.SampleFinish(
                                                Optional.empty(),
                                                RuntimeExitCategory.CANCELLED,
                                                RuntimeDevEngineBankBinding.SampleOutcome
                                                        .CANCELLED)));
        assertEquals(RuntimeDevEngineBankBinding.MissingGate.PHYSICAL_CONTRACT, refused.gate());
    }

    @Test
    void oversizedRootCollectionIsRejectedBeforeCopyingAnyIdentity() {
        final List<ExpectedRoot> oversized =
                new java.util.AbstractList<>() {
                    @Override
                    public ExpectedRoot get(final int index) {
                        throw new AssertionError("NO_IDENTITY_MATERIALIZATION");
                    }

                    @Override
                    public int size() {
                        return 1000;
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new RuntimeDevEngineBankBinding.SampleExpectation(
                                UUID.randomUUID(), 1, oversized, Map.of(1, 1)));
    }
}
