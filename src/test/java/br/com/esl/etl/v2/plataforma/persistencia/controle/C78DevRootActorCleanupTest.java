package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.atomic.AtomicInteger;

/** Cleanup component only: no parent admission, actual driver, SQL or source. */
class C78DevRootActorCleanupTest {
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
}
