package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;

import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/** Only memory/lifecycle forwarding under offline fixtures; no physical readback is claimed. */
class RuntimeDevSampleOwnerFinishTest {
    static class Owner implements RuntimeDevEngineBankBinding.Session {
        RuntimeDevEngineBankBinding.SampleFinish received;
        boolean closed;
        boolean finalTransactionZero = true;
        boolean refuse;

        public javax.sql.DataSource dataSource() {
            throw new AssertionError("NO_PHYSICAL_CONNECTION");
        }

        public void checkpoint() {
            assertFalse(closed);
        }

        public void close() {
            closed = true;
        }

        public RuntimeDevEngineBankBinding.SampleReadback finishSample(
                final RuntimeDevEngineBankBinding.SampleFinish finish) throws SQLException {
            assertFalse(closed);
            received = finish;
            closed = true;
            if (refuse) {
                throw new SQLException("COMPONENT_OWNER_FINISH_REFUSED");
            }
            final int rows = finish.expectation().map(e -> e.physicalRows()).orElse(0);
            return new RuntimeDevEngineBankBinding.SampleReadback(
                    finish.expectation().isPresent(),
                    true,
                    true,
                    finalTransactionZero,
                    true,
                    rows,
                    rows == 0 ? 0 : 1,
                    rows == 0 ? 0 : 1,
                    0); // Component fixture executed no SQL; never fabricate the122 count.
        }
    }

    @Test
    void actualExtractorExpectationAndOutcomeReachOwnerBeforeAnyReceipt() throws Exception {
        final var config = RuntimeDevSampleExecutionTest.configuration();
        final var request = RuntimeDevSampleExecutionTest.request(config, 1, 1);
        final var fixture = new RuntimeBootstrapTestFixture(RuntimeDevSampleExecutionTest.NOW);
        final var rows = new AtomicInteger();
        final var owner = new Owner();
        final var finished =
                RuntimeDevEngineExecution.executeSampleAndFinish(
                        owner,
                        () ->
                                RuntimeDevSampleExecution.execute(
                                        true,
                                        config,
                                        request,
                                        fixture.dataSource(),
                                        RuntimeDevSampleExecutionTest.oneEntity(fixture, false, 12),
                                        batch -> rows.addAndGet(batch.size()),
                                        () -> {}));
        assertEquals(12, rows.get());
        assertTrue(owner.closed);
        assertSame(
                finished.sample().expectation().orElseThrow(),
                owner.received.expectation().orElseThrow());
        assertEquals(
                RuntimeDevEngineBankBinding.SampleOutcome.PARTIAL_BOUNDARY,
                owner.received.outcome());
        assertNotEquals(RuntimeExitCategory.SUCCESS, owner.received.executorOutcome());
        assertTrue(finished.readback().matches(owner.received));
        assertFalse(
                owner.received
                        .toString()
                        .contains(
                                owner.received
                                        .expectation()
                                        .orElseThrow()
                                        .executionId()
                                        .toString()));
    }

    @Test
    void cancellationForwardsNoInventedExpectationAndStillFinishesOwner() throws Exception {
        final var owner = new Owner();
        final var finished =
                RuntimeDevEngineExecution.executeSampleAndFinish(
                        owner,
                        () ->
                                new RuntimeDevSampleExecution.Result(
                                        RuntimeExitCategory.CANCELLED, false, Optional.empty()));
        assertTrue(owner.closed);
        assertTrue(owner.received.expectation().isEmpty());
        assertEquals(RuntimeDevEngineBankBinding.SampleOutcome.CANCELLED, owner.received.outcome());
        assertFalse(finished.readback().expectationVerified());
    }

    @Test
    void missingFinalTransactionProofCannotReturnVerifiedResult() {
        final var owner = new Owner();
        owner.finalTransactionZero = false;
        assertThrows(
                SQLException.class,
                () ->
                        RuntimeDevEngineExecution.executeSampleAndFinish(
                                owner,
                                () ->
                                        new RuntimeDevSampleExecution.Result(
                                                RuntimeExitCategory.CANCELLED,
                                                false,
                                                Optional.empty())));
        assertTrue(owner.closed);
    }

    @Test
    void primaryFailureKeepsOwnerCleanupSuppressedWithoutMessagePublication() {
        final var owner = new Owner();
        owner.refuse = true;
        final var primary = new IllegalStateException("COMPONENT_SENSITIVE_DIAGNOSTIC_NOT_LOGGED");
        final var caught =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RuntimeDevEngineExecution.executeSampleAndFinish(
                                        owner,
                                        () -> {
                                            throw primary;
                                        }));
        assertSame(primary, caught);
        assertEquals(1, caught.getSuppressed().length);
        assertTrue(owner.closed);
        assertTrue(owner.received.expectation().isEmpty());
        assertEquals(RuntimeDevEngineBankBinding.SampleOutcome.FAILED, owner.received.outcome());
    }

    @Test
    void failedCheckpointStillForwardsActualOneRowExpectationAndCannotReturnReceipt()
            throws Exception {
        final var config = RuntimeDevSampleExecutionTest.configuration();
        final var request = RuntimeDevSampleExecutionTest.request(config, 1, 1);
        final var fixture = new RuntimeBootstrapTestFixture(RuntimeDevSampleExecutionTest.NOW);
        final var sample =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        request,
                        fixture.dataSource(),
                        RuntimeDevSampleExecutionTest.oneEntity(fixture, false, 1),
                        batch -> assertEquals(1, batch.size()),
                        () -> {});
        final var primary = new IllegalStateException("COMPONENT_CHECKPOINT_REFUSED");
        final var owner =
                new Owner() {
                    @Override
                    public void checkpoint() {
                        throw primary;
                    }
                };
        final var caught =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RuntimeDevEngineExecution.executeSampleAndFinish(
                                        owner, () -> sample));
        assertSame(primary, caught);
        assertTrue(owner.closed);
        assertSame(sample.expectation().orElseThrow(), owner.received.expectation().orElseThrow());
        assertEquals(1, owner.received.expectation().orElseThrow().physicalRows());
        assertEquals(sample.category(), owner.received.executorOutcome());
    }

    @Test
    void cleanupErrorCannotReplacePrimaryFailure() {
        final var cleanup = new AssertionError("COMPONENT_OWNER_CLEANUP_ERROR");
        final var owner =
                new Owner() {
                    @Override
                    public RuntimeDevEngineBankBinding.SampleReadback finishSample(
                            final RuntimeDevEngineBankBinding.SampleFinish finish) {
                        received = finish;
                        closed = true;
                        throw cleanup;
                    }
                };
        final var primary = new IllegalStateException("COMPONENT_EXECUTOR_FAILURE");
        final var caught =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RuntimeDevEngineExecution.executeSampleAndFinish(
                                        owner,
                                        () -> {
                                            throw primary;
                                        }));
        assertSame(primary, caught);
        assertSame(cleanup, caught.getSuppressed()[0]);
        assertTrue(owner.closed);
    }
}
