package br.com.esl.etl.v2.contratos.mapping;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Future Windows transport: sensitive configuration is sent only on the owned process stdin. */
final class CotacoesCurlTransport implements CotacoesSourceRunner.Transport {
    private final String credential;
    private final Launcher launcher;

    CotacoesCurlTransport(final String credential) {
        this(credential, arguments -> new ProcessBuilder(arguments).start());
    }

    CotacoesCurlTransport(final String credential, final Launcher launcher) {
        CotacoesSourceInput.require(
                credential != null
                        && credential.length() >= 1
                        && credential.length() <= 2_048
                        && credential.matches("[A-Za-z0-9._~+/-]+=*"),
                "CREDENTIAL_MISSING_OR_INVALID");
        this.credential = credential;
        this.launcher = launcher;
    }

    static List<String> arguments(final int timeout) {
        CotacoesSourceInput.require(timeout >= 1 && timeout <= 30, "TIMEOUT_REJECTED");
        return List.of(
                "curl.exe",
                "--disable",
                "--config",
                "-",
                "--globoff",
                "--silent",
                "--request",
                "GET",
                "--proto",
                "=https",
                "--max-redirs",
                "0",
                "--retry",
                "0",
                "--connect-timeout",
                Integer.toString(Math.min(10, timeout)),
                "--max-time",
                Integer.toString(timeout),
                "--max-filesize",
                "65536",
                "--output",
                "-",
                "--write-out",
                "%{stderr}%{http_code}");
    }

    @Override
    public CotacoesSourceRunner.Response get(final URI uri, final int timeoutSeconds)
            throws IOException {
        CotacoesSourceInput.require(
                "https".equals(uri.getScheme())
                        && uri.getHost() != null
                        && uri.getRawUserInfo() == null
                        && uri.getRawFragment() == null
                        && ("/api/analytics/reports/6906/info".equals(uri.getPath())
                                || "/api/analytics/reports/6906/data".equals(uri.getPath())),
                "TRANSPORT_SCOPE_REJECTED");
        final long started = System.nanoTime();
        final var readers = Executors.newFixedThreadPool(2);
        Process process = null;
        try {
            process = launcher.start(arguments(timeoutSeconds));
            final Process owned = process;
            final Future<byte[]> body = readers.submit(bounded(owned, false));
            final Future<byte[]> status = readers.submit(bounded(owned, true));
            try (var input = process.getOutputStream()) {
                input.write(configuration(uri).getBytes(StandardCharsets.UTF_8));
            }
            final long remaining =
                    TimeUnit.SECONDS.toNanos(timeoutSeconds) - (System.nanoTime() - started);
            if (remaining <= 0 || !process.waitFor(remaining, TimeUnit.NANOSECONDS)) {
                throw new IOException("CURL_TIMEOUT_UNKNOWN_RESULT");
            }
            if (process.exitValue() != 0) {
                throw new IOException("CURL_FAILURE_UNKNOWN_RESULT");
            }
            final byte[] bytes = body.get(1, TimeUnit.SECONDS);
            final String code =
                    new String(status.get(1, TimeUnit.SECONDS), StandardCharsets.US_ASCII);
            if (!code.matches("[0-9]{3}")) {
                throw new IOException("CURL_STATUS_UNKNOWN");
            }
            return new CotacoesSourceRunner.Response(Integer.parseInt(code), bytes);
        } catch (final InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("CURL_INTERRUPTED_UNKNOWN_RESULT");
        } catch (final Exception error) {
            // Never attach curl diagnostics, URI, credential or an exception containing them.
            throw new IOException("CURL_FAILED_RECONCILE_RESERVATION");
        } finally {
            if (process != null) {
                if (process.isAlive()) {
                    process.destroyForcibly();
                }
                try {
                    process.getInputStream().close();
                    process.getErrorStream().close();
                } catch (final IOException ignored) {
                    /* The reservation already records uncertainty. */
                }
            }
            readers.shutdownNow();
        }
    }

    private static Callable<byte[]> bounded(final Process process, final boolean diagnostics) {
        return () -> {
            try (var stream = diagnostics ? process.getErrorStream() : process.getInputStream()) {
                final int cap = diagnostics ? 1_024 : CotacoesSourceInput.RESPONSE_BYTES;
                final byte[] bytes = stream.readNBytes(cap + 1);
                if (bytes.length > cap) {
                    process.destroyForcibly();
                    throw new IOException("CURL_STREAM_CAP");
                }
                return bytes;
            }
        };
    }

    private String configuration(final URI uri) {
        return "url = "
                + quote(uri.toASCIIString())
                + "\nheader = "
                + quote("Authorization: Bearer " + credential)
                + "\nheader = \"Accept: application/json\"\n";
    }

    private static String quote(final String value) {
        CotacoesSourceInput.require(
                value.chars().noneMatch(c -> c < 32 || c == 127), "CONFIG_CONTROL_REJECTED");
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    @FunctionalInterface
    interface Launcher {
        Process start(List<String> arguments) throws IOException;
    }

    @Override
    public String toString() {
        return "COTACOES_CURL_TRANSPORT_REDACTED";
    }
}
