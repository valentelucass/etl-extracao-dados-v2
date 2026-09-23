package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/** Exercises the actual P05 admission fence without fixture authoring or JDBC. */
class SequenceScaleAdmissionTest {
    @Test
    void admitsExactlyFourOrderedScalesAfterEachCompletedBody() {
        final var suite = new SequenceScaleIT();
        for (final int roots : new int[] {2, 4, 8, 16}) {
            suite.beginScale(roots);
            suite.completeScale();
        }
        assertThrows(AssertionError.class, () -> suite.beginScale(32));
    }

    @Test
    void failedOrInterruptedBodyBlocksAnotherScaleAndARepeat() {
        final var suite = new SequenceScaleIT();
        suite.beginScale(2);
        assertThrows(AssertionError.class, () -> suite.beginScale(4));
        assertThrows(AssertionError.class, () -> suite.beginScale(2));
    }

    @Test
    void junitFailureAfterBodyCompletionStillBlocksTheNextScale() {
        final var suite = new SequenceScaleIT();
        suite.beginScale(2);
        suite.completeScale();
        suite.failureWatcher.testFailed(null, new AssertionError("SYNTHETIC_LATE_FAILURE"));
        assertThrows(AssertionError.class, () -> suite.beginScale(4));
    }

    @Test
    void outOfOrderAdmissionFailsBeforeAnyIoAndRemainsBlocked() {
        final var suite = new SequenceScaleIT();
        assertThrows(AssertionError.class, () -> suite.beginScale(4));
        assertThrows(AssertionError.class, () -> suite.beginScale(2));
    }
}
