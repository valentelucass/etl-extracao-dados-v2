package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Cleanup component only: no parent admission, actual driver, SQL or source. */
class C78DevRootActorCleanupTest {
    @Test
    void incompleteOrExpiredProofRefusesBeforeStartingAnyNativeHelper() {
        for (final List<String> arguments :
                List.of(List.of("one", "two", "three"), List.of("one", "two", "three", "four"))) {
            assertEquals(
                    "ROOT_GATE_ACTOR_UNAVAILABLE",
                    assertThrows(
                                    IOException.class,
                                    () ->
                                            C78DevRootActor.verify(
                                                    ProcessHandle.current(),
                                                    Instant.EPOCH,
                                                    arguments,
                                                    arguments.size() == 4 ? 0 : 1000))
                            .getMessage());
        }
    }

    @Test
    void oversizedProofRefusesBeforeStartingAnyNativeHelper() {
        assertEquals(
                "ROOT_GATE_ACTOR_LIMIT",
                assertThrows(
                                IOException.class,
                                () ->
                                        C78DevRootActor.verify(
                                                ProcessHandle.current(),
                                                Instant.EPOCH,
                                                List.of("x".repeat(9000), "two", "three", "four"),
                                                1000))
                        .getMessage());
    }

    @Test
    void knownTerminationClosesAllStreamsAndKeepsThePrimaryFailure() {
        final var helper = new TerminatingProcess();
        final var primary = new IOException("OWN_TEST_PRIMARY");
        assertSame(primary, C78DevRootActor.closeHelper(helper, primary));
        assertEquals(1, helper.terminations);
        assertEquals(1, helper.waits);
        assertEquals(3, helper.closed.get());
        assertEquals(0, primary.getSuppressed().length);
    }

    @Test
    void uncertainTerminationIsNeverReportedAsKnownClosed() {
        final var helper = new TerminatingProcess();
        helper.terminationObserved = false;
        final var primary = new IOException("OWN_TEST_PRIMARY");
        assertSame(primary, C78DevRootActor.closeHelper(helper, primary));
        assertEquals(1, primary.getSuppressed().length);
        assertEquals("ROOT_GATE_ACTOR_CLOSE_UNCERTAIN", primary.getSuppressed()[0].getMessage());
        assertEquals(3, helper.closed.get());
    }

    @Test
    void interruptedTerminationRetainsInterruptAndStillClosesEveryStream() {
        final var helper = new TerminatingProcess();
        helper.interruptWait = true;
        try {
            final var failure = C78DevRootActor.closeHelper(helper, null);
            assertEquals("ROOT_GATE_ACTOR_CLOSE_INTERRUPTED", failure.getMessage());
            assertTrue(Thread.currentThread().isInterrupted());
            assertEquals(3, helper.closed.get());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void closesAllThreeStreamsAndPreservesPrimaryWithSuppressedFailures() {
        final var helper = new FailedCloseProcess();
        final var primary = new IOException("OWN_TEST_PRIMARY");
        final var result = C78DevRootActor.closeHelper(helper, primary);
        assertSame(primary, result);
        assertEquals(3, primary.getSuppressed().length);
        assertEquals(3, helper.closed.get());
        for (final var suppressed : primary.getSuppressed()) {
            assertTrue(suppressed.getCause() instanceof IOException);
        }
    }

    @Test
    void cleanupFailureWithoutPrimaryKeepsFirstAndSuppressesOthers() {
        final var helper = new FailedCloseProcess();
        final var result = C78DevRootActor.closeHelper(helper, null);
        assertEquals("ROOT_GATE_ACTOR_STDOUT_CLOSE", result.getMessage());
        assertEquals(2, result.getSuppressed().length);
        assertEquals(3, helper.closed.get());
    }

    @Test
    void terminationFailureStillClosesAllStreamsAndRetainsPrimary() {
        final var helper =
                new FailedCloseProcess() {
                    @Override
                    public boolean isAlive() {
                        return true;
                    }

                    @Override
                    public Process destroyForcibly() {
                        throw new IllegalStateException("OWN_TEST_CLOSE");
                    }
                };
        final var primary = new IOException("OWN_TEST_PRIMARY");
        assertSame(primary, C78DevRootActor.closeHelper(helper, primary));
        assertEquals(4, primary.getSuppressed().length);
        assertEquals(3, helper.closed.get());
    }

    private static class FailedCloseProcess extends Process {
        final AtomicInteger closed = new AtomicInteger();

        @Override
        public InputStream getInputStream() {
            return input();
        }

        @Override
        public InputStream getErrorStream() {
            return input();
        }

        private InputStream input() {
            return new InputStream() {
                @Override
                public int read() {
                    return -1;
                }

                @Override
                public void close() throws IOException {
                    closed.incrementAndGet();
                    throw new IOException("OWN_TEST_STREAM_CLOSE");
                }
            };
        }

        @Override
        public OutputStream getOutputStream() {
            return new OutputStream() {
                @Override
                public void write(int value) {}

                @Override
                public void close() throws IOException {
                    closed.incrementAndGet();
                    throw new IOException("OWN_TEST_STREAM_CLOSE");
                }
            };
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {}

        @Override
        public boolean isAlive() {
            return false;
        }
    }

    /** A cleanup double, never a running process or a Root/parent identity. */
    private static final class TerminatingProcess extends FailedCloseProcess {
        boolean terminationObserved = true;
        boolean interruptWait;
        int terminations;
        int waits;

        @Override
        public boolean isAlive() {
            return true;
        }

        @Override
        public Process destroyForcibly() {
            terminations++;
            return this;
        }

        @Override
        public boolean waitFor(final long timeout, final TimeUnit unit)
                throws InterruptedException {
            assertEquals(1L, timeout);
            assertEquals(TimeUnit.SECONDS, unit);
            waits++;
            if (interruptWait) {
                throw new InterruptedException("OWN_TEST_WAIT_INTERRUPTED");
            }
            return terminationObserved;
        }

        @Override
        public InputStream getInputStream() {
            return closingInput();
        }

        @Override
        public InputStream getErrorStream() {
            return closingInput();
        }

        private InputStream closingInput() {
            return new java.io.ByteArrayInputStream(new byte[0]) {
                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }

        @Override
        public OutputStream getOutputStream() {
            return new java.io.ByteArrayOutputStream() {
                @Override
                public void close() {
                    closed.incrementAndGet();
                }
            };
        }
    }
}
