package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class QualificationConcurrencyTest {
    @Test
    void closesTheExecutorAndWaitsForItsTermination() {
        final var executor = new ControlledExecutor(true, null);

        try (var scope = new QualificationConcurrency.ExecutorScope(executor)) {
            assertFalse(scope.executor().isShutdown());
        }

        assertTrue(executor.isShutdown());
        assertTrue(executor.isTerminated());
    }

    @Test
    void reportsAnUnreleasedThreadWhenThereIsNoPrimaryFailure() {
        final var executor = new ControlledExecutor(false, null);

        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () -> {
                            try (var scope = new QualificationConcurrency.ExecutorScope(executor)) {
                                assertFalse(scope.executor().isShutdown());
                            }
                        });

        assertEquals("QUAL_CONCURRENCY_THREAD_UNRELEASED", failure.getMessage());
        assertTrue(executor.isShutdown());
        assertTrue(executor.awaited);
    }

    @Test
    void preservesTheSqlFailureAndSuppressesTheUnreleasedThread() {
        final var primary = new SQLException("synthetic primary SQL failure");
        final var executor = new ControlledExecutor(false, null);

        final var failure =
                assertThrows(
                        SQLException.class,
                        () -> {
                            try (var scope = new QualificationConcurrency.ExecutorScope(executor)) {
                                assertFalse(scope.executor().isShutdown());
                                throw primary;
                            }
                        });

        assertSame(primary, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertTrue(failure.getSuppressed()[0] instanceof IllegalStateException);
        assertEquals("QUAL_CONCURRENCY_THREAD_UNRELEASED", failure.getSuppressed()[0].getMessage());
        assertTrue(executor.isShutdown());
    }

    @Test
    void preservesAnErrorAndSuppressesTheCleanupFailure() {
        final var primary = new AssertionError("synthetic primary error");
        final var executor = new ControlledExecutor(false, null);

        final var failure =
                assertThrows(
                        AssertionError.class,
                        () -> {
                            try (var scope = new QualificationConcurrency.ExecutorScope(executor)) {
                                assertFalse(scope.executor().isShutdown());
                                throw primary;
                            }
                        });

        assertSame(primary, failure);
        assertEquals(1, failure.getSuppressed().length);
        assertEquals("QUAL_CONCURRENCY_THREAD_UNRELEASED", failure.getSuppressed()[0].getMessage());
        assertTrue(executor.isShutdown());
    }

    @Test
    void restoresInterruptionAndRetainsTheCleanupCause() {
        final var interruption = new InterruptedException("synthetic interruption");
        final var executor = new ControlledExecutor(false, interruption);

        try {
            assertFalse(Thread.currentThread().isInterrupted());
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () -> {
                                try (var scope =
                                        new QualificationConcurrency.ExecutorScope(executor)) {
                                    assertFalse(scope.executor().isShutdown());
                                }
                            });

            assertEquals("QUAL_CONCURRENCY_THREAD_INTERRUPTED", failure.getMessage());
            assertSame(interruption, failure.getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(executor.isShutdown());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void keepsThePrimaryFailureWhenCleanupIsInterrupted() {
        final var primary = new SQLException("synthetic primary SQL failure");
        final var interruption = new InterruptedException("synthetic interruption");
        final var executor = new ControlledExecutor(false, interruption);

        try {
            assertFalse(Thread.currentThread().isInterrupted());
            final var failure =
                    assertThrows(
                            SQLException.class,
                            () -> {
                                try (var scope =
                                        new QualificationConcurrency.ExecutorScope(executor)) {
                                    assertFalse(scope.executor().isShutdown());
                                    throw primary;
                                }
                            });

            assertSame(primary, failure);
            assertEquals(1, failure.getSuppressed().length);
            assertEquals(
                    "QUAL_CONCURRENCY_THREAD_INTERRUPTED", failure.getSuppressed()[0].getMessage());
            assertSame(interruption, failure.getSuppressed()[0].getCause());
            assertTrue(Thread.currentThread().isInterrupted());
            assertTrue(executor.isShutdown());
        } finally {
            Thread.interrupted();
        }
    }

    private static final class ControlledExecutor extends AbstractExecutorService {
        private final boolean terminationAllowed;
        private final InterruptedException interruption;
        private boolean shutdown;
        private boolean awaited;

        private ControlledExecutor(
                final boolean terminationAllowed, final InterruptedException interruption) {
            this.terminationAllowed = terminationAllowed;
            this.interruption = interruption;
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && awaited && terminationAllowed;
        }

        @Override
        public boolean awaitTermination(final long timeout, final TimeUnit unit)
                throws InterruptedException {
            assertTrue(shutdown);
            awaited = true;
            if (interruption != null) {
                throw interruption;
            }
            return terminationAllowed;
        }

        @Override
        public void execute(final Runnable command) {
            throw new UnsupportedOperationException("No worker is needed for cleanup tests.");
        }
    }
}
