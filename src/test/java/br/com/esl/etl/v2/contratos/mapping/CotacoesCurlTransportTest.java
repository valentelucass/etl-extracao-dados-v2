package br.com.esl.etl.v2.contratos.mapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

class CotacoesCurlTransportTest {
    private static final URI INFO =
            URI.create("https://example.invalid/api/analytics/reports/6906/info");
    private static final String CREDENTIAL = "SYNTHETIC_CREDENTIAL_SENTINEL";

    @Test
    void passesSensitiveConfigurationOnlyThroughStdinWithFixedReadOnlyArguments() throws Exception {
        final var process = new FakeProcess("{}".getBytes(StandardCharsets.UTF_8), "200", false);
        final var transport =
                new CotacoesCurlTransport(
                        CREDENTIAL,
                        arguments -> {
                            assertEquals(CotacoesCurlTransport.arguments(30), arguments);
                            assertFalse(arguments.toString().contains(CREDENTIAL));
                            assertFalse(arguments.toString().contains("example.invalid"));
                            assertFalse(arguments.contains("--location"));
                            assertEquals("--disable", arguments.get(1));
                            return process;
                        });
        assertEquals("COTACOES_RESPONSE_REDACTED", transport.get(INFO, 30).toString());
        final String stdin = process.stdin.toString(StandardCharsets.UTF_8);
        assertTrue(stdin.contains(INFO.toString()));
        assertTrue(stdin.contains(CREDENTIAL));
        assertFalse(transport.toString().contains(CREDENTIAL));
        assertFalse(stdin.contains("data ="));
    }

    @Test
    void rejectsCredentialInjectionAndOtherTemplatesBeforeLaunching() {
        final var called = new AtomicBoolean();
        final CotacoesCurlTransport.Launcher launcher =
                arguments -> {
                    called.set(true);
                    throw new IOException();
                };
        for (final String credential :
                List.of("", "bad\r\nurl=private", "bad\"value", "bad value")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new CotacoesCurlTransport(credential, launcher));
        }
        final var transport = new CotacoesCurlTransport(CREDENTIAL, launcher);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        transport.get(
                                URI.create(
                                        "https://example.invalid/api/analytics/reports/6908/info"),
                                30));
        assertFalse(called.get());
    }

    @Test
    void killsOnlyOwnedProcessOnTimeoutAndNeverExposesDiagnostics() {
        final var process = new FakeProcess(new byte[0], "PRIVATE_DIAGNOSTIC_SENTINEL", true);
        final var transport = new CotacoesCurlTransport(CREDENTIAL, arguments -> process);
        final var error = assertThrows(IOException.class, () -> transport.get(INFO, 1));
        assertTrue(process.destroyed.get());
        assertFalse(error.toString().contains("PRIVATE_DIAGNOSTIC_SENTINEL"));
        assertEquals(null, error.getCause());
    }

    @Test
    void capsStdoutAndStderrAndRejectsMissingOrAmbiguousHttpStatus() {
        for (final var process :
                List.of(
                        new FakeProcess(new byte[65_537], "200", false),
                        new FakeProcess(new byte[0], "x".repeat(1_025), false),
                        new FakeProcess(new byte[0], "", false),
                        new FakeProcess(new byte[0], "PRIVATE_SENTINEL200", false))) {
            final var transport = new CotacoesCurlTransport(CREDENTIAL, arguments -> process);
            final var error = assertThrows(IOException.class, () -> transport.get(INFO, 30));
            assertFalse(error.toString().contains("PRIVATE_SENTINEL"));
        }
    }

    @Test
    void rejectsUnsafeTimeoutAndRedactsLaunchFailures() {
        assertThrows(IllegalArgumentException.class, () -> CotacoesCurlTransport.arguments(31));
        assertThrows(IllegalArgumentException.class, () -> CotacoesCurlTransport.arguments(0));
        final var transport =
                new CotacoesCurlTransport(
                        CREDENTIAL,
                        arguments -> {
                            throw new IOException("PRIVATE_EXECUTABLE_PATH_SENTINEL");
                        });
        final var error = assertThrows(IOException.class, () -> transport.get(INFO, 30));
        assertFalse(error.toString().contains("PRIVATE_EXECUTABLE_PATH_SENTINEL"));
    }

    /** A process-shaped fixture; this class never starts curl, a shell or a server. */
    private static final class FakeProcess extends Process {
        private final byte[] body;
        private final String diagnostics;
        private final boolean timeout;
        private final ByteArrayOutputStream stdin = new ByteArrayOutputStream();
        private final AtomicBoolean destroyed = new AtomicBoolean();

        private FakeProcess(final byte[] body, final String diagnostics, final boolean timeout) {
            this.body = body;
            this.diagnostics = diagnostics;
            this.timeout = timeout;
        }

        @Override
        public OutputStream getOutputStream() {
            return stdin;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(body);
        }

        @Override
        public InputStream getErrorStream() {
            return new ByteArrayInputStream(diagnostics.getBytes(StandardCharsets.UTF_8));
        }

        @Override
        public int waitFor() {
            return 0;
        }

        @Override
        public boolean waitFor(final long duration, final TimeUnit unit) {
            return !timeout;
        }

        @Override
        public int exitValue() {
            return 0;
        }

        @Override
        public void destroy() {
            destroyed.set(true);
        }

        @Override
        public Process destroyForcibly() {
            destroy();
            return this;
        }

        @Override
        public boolean isAlive() {
            return timeout && !destroyed.get();
        }
    }
}
