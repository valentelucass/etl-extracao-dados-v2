package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Optional input control cancels only this already authorized one-shot invocation. */
final class RuntimeCancellationControl implements AutoCloseable {
    private final CancellationSignal signal;
    private final CountDownLatch finished = new CountDownLatch(1);
    private final Thread shutdown;
    private final Thread reader;

    RuntimeCancellationControl(final CancellationSignal cancellation, final InputStream controls) {
        signal = Objects.requireNonNull(cancellation);
        shutdown =
                new Thread(
                        () -> {
                            cancellation.cancel();
                            try {
                                finished.await(5, TimeUnit.SECONDS);
                            } catch (final InterruptedException interrupted) {
                                Thread.currentThread().interrupt();
                            }
                        },
                        "runtime-one-shot-shutdown");
        Runtime.getRuntime().addShutdownHook(shutdown);
        if (controls == null) {
            reader = null;
        } else {
            reader = new Thread(() -> read(cancellation, controls), "runtime-one-shot-control");
            reader.setDaemon(true);
            reader.start();
        }
    }

    static void read(final CancellationSignal cancellation, final InputStream controls) {
        try {
            final int first = controls.read();
            if (first == -1) {
                return;
            }
            // The channel only stops this invocation. Malformed input also stops it safely.
            final byte[] expected = "cancel\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            if (first != expected[0]) {
                cancellation.cancel();
                return;
            }
            for (int i = 1; i < expected.length; i++) {
                if (controls.read() != expected[i]) {
                    cancellation.cancel();
                    return;
                }
            }
            cancellation.cancel();
        } catch (final IOException failure) {
            cancellation.cancel();
        }
    }

    CancellationSignal signal() {
        return signal;
    }

    @Override
    public void close() {
        finished.countDown();
        if (reader != null) {
            reader.interrupt();
        }
        try {
            Runtime.getRuntime().removeShutdownHook(shutdown);
        } catch (final IllegalStateException shuttingDown) {
            // The bounded hook is already waiting for this invocation to finish.
        }
    }
}
