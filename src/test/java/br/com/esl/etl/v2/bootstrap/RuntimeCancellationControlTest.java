package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class RuntimeCancellationControlTest {
    @Test
    void optionalReaderCancelsItsOwnSignalAndClosesAfterCompletion() {
        try (var control =
                new RuntimeCancellationControl(
                        new CancellationSignal(),
                        new ByteArrayInputStream("cancel\n".getBytes(StandardCharsets.US_ASCII)))) {
            org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(
                    java.time.Duration.ofSeconds(1),
                    () -> {
                        while (!control.signal().isCancellationRequested()) {
                            Thread.sleep(1);
                        }
                    });
            assertTrue(control.signal().isCancellationRequested());
        }
    }

    @Test
    void endOfInputDoesNotCancelAnOrdinaryOneShotRun() {
        final var signal = new CancellationSignal();
        RuntimeCancellationControl.read(signal, new ByteArrayInputStream(new byte[0]));
        assertFalse(signal.isCancellationRequested());
    }

    @Test
    void controlCanOnlyCancelAndConsumesAtMostOneBoundedCommand() {
        for (final String command :
                java.util.List.of("cancel\n", "wrong", "can", "cancel\n" + "x".repeat(1000))) {
            final var signal = new CancellationSignal();
            final var input = new ByteArrayInputStream(command.getBytes(StandardCharsets.US_ASCII));
            RuntimeCancellationControl.read(signal, input);
            assertTrue(signal.isCancellationRequested());
            assertTrue(command.length() - input.available() <= 7);
        }
    }

    @Test
    void inputFailureCancelsWithoutLeakingItsMessage() {
        final var signal = new CancellationSignal();
        RuntimeCancellationControl.read(
                signal,
                new java.io.InputStream() {
                    @Override
                    public int read() throws java.io.IOException {
                        throw new java.io.IOException("sensitive-control-input");
                    }
                });
        assertTrue(signal.isCancellationRequested());
        try (var control = new RuntimeCancellationControl(new CancellationSignal(), null)) {
            assertFalse(control.signal().isCancellationRequested());
        }
    }
}
