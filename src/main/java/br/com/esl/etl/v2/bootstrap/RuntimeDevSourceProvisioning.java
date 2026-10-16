package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttemptObserver;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayFactory;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.MonotonicTicker;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceSleeper;

import com.sun.nio.file.ExtendedOpenOption;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Curated DEV-only translation of the provisioned V2 file, after actual source admission. */
final class RuntimeDevSourceProvisioning implements SecretProvider, AutoCloseable {
    static final Path PROVISIONED_FILE =
            Path.of("C:/Users/suporte/Documents/projetos/etl-extracao-dados-v2/.env");
    static final int MAX_FILE_BYTES = 65_536;
    private static final Set<String> KEYS =
            Set.of("API_BASEURL", "API_BASE_URL", "API_DATAEXPORT_TOKEN");
    private final RuntimeDevEngineBankBinding.Admission gate;
    private final boolean enabled;
    private FileChannel lease;
    private Map<String, String> selected;
    private boolean closed;

    RuntimeDevSourceProvisioning(
            final RuntimeDevEngineBankBinding.Admission gate, final boolean enabled) {
        this.gate = java.util.Objects.requireNonNull(gate);
        this.enabled = enabled;
    }

    boolean enabled() {
        return enabled;
    }

    RuntimeOperationalExecution.SourceGateways gateways(
            final DataExportHttpAttemptObserver attempts) {
        return (configuration, request, cancellation, observation) -> {
            // Even the real factory constructor materializes the token. Gate first.
            verifyGate();
            final var frozen = configuration.dataExport().orElseThrow();
            verifyMetadata(frozen);
            final var actual = bind(frozen);
            final var factory =
                    new DataExportHttpGatewayFactory(actual, this, configuration.clock());
            final var governor =
                    new EslRequestGovernor(
                            actual.resiliencePolicy(),
                            MonotonicTicker.systemTicker(),
                            ResilienceSleeper.threadSleeper());
            return factory.forWorkload(
                    governor.beginCycle(cancellation),
                    RuntimeVertical.workload(request.template),
                    attempts,
                    observation);
        };
    }

    static void verifyMetadata(final DataExportSourceConfiguration source) {
        if (source.resiliencePolicy()
                        .minimumRequestInterval()
                        .compareTo(java.time.Duration.ofSeconds(2))
                < 0) {
            throw new IllegalStateException("DEV_SOURCE_DOCUMENTED_MINIMUM_INTERVAL_REQUIRED");
        }
        if (source.resiliencePolicy().maxInFlight() != 1
                || source.settings().retryPolicy().maxAttempts() != 1) {
            throw new IllegalStateException("DEV_SOURCE_SERIAL_NO_RETRY_REQUIRED");
        }
    }

    private DataExportSourceConfiguration bind(final DataExportSourceConfiguration frozen) {
        final var values = values();
        final URI actual;
        try {
            actual = URI.create(values.get("API_BASE_URL"));
        } catch (final RuntimeException malformed) {
            throw new IllegalStateException("DEV_SOURCE_PROVISIONED_URI_INVALID");
        }
        final var original = frozen.settings();
        if (!actual.equals(original.baseUri())) {
            throw new IllegalStateException("DEV_SOURCE_ORIGIN_DIFFERS_FROM_FROZEN_PLAN");
        }
        // These are ETL partitions from the frozen plan, not a claim about supplier identity.
        return new DataExportSourceConfiguration(
                frozen.sourceInstance(),
                frozen.tenantScope(),
                new DataExportClientSettings(
                        actual,
                        original.sourceZone(),
                        original.requestTimeout(),
                        original.preferredTransport(),
                        original.retryPolicy(),
                        original.maxResponseBytes()),
                frozen.resiliencePolicy());
    }

    @Override
    public String require(final SecretKey key) {
        if (key != SecretKey.DATA_EXPORT_TOKEN) {
            throw new IllegalArgumentException("DEV_SOURCE_SECRET_KEY_SCOPE");
        }
        return values().get("API_DATAEXPORT_TOKEN");
    }

    private void verifyGate() {
        if (closed || !enabled) {
            throw new IllegalStateException("DEV_SOURCE_CURATED_OPT_IN_REQUIRED");
        }
        try {
            gate.verify();
        } catch (final SQLException refused) {
            throw new IllegalStateException("DEV_SOURCE_ADMISSION_BEFORE_PROVISIONING", refused);
        }
    }

    private synchronized Map<String, String> values() {
        verifyGate();
        if (selected != null) {
            return selected;
        }
        // An explicitly curated file is authoritative here; ambiguous native overrides refuse.
        for (final String name : Set.of("V2_DATAEXPORT_BASE_URL", "V2_DATAEXPORT_TOKEN")) {
            if (System.getenv().containsKey(name) || System.getProperties().containsKey(name)) {
                throw new IllegalStateException("DEV_SOURCE_NATIVE_OVERRIDE_AMBIGUOUS");
            }
        }
        byte[] raw = null;
        try {
            final Path path = PROVISIONED_FILE.toAbsolutePath().normalize();
            if (!path.toRealPath().equals(path)) {
                throw new IOException("DEV_SOURCE_PROVISIONED_LINK_REFUSED");
            }
            lease =
                    FileChannel.open(
                            path,
                            Set.of(
                                    StandardOpenOption.READ,
                                    LinkOption.NOFOLLOW_LINKS,
                                    ExtendedOpenOption.NOSHARE_WRITE,
                                    ExtendedOpenOption.NOSHARE_DELETE));
            if (lease.size() > MAX_FILE_BYTES) {
                throw new IOException("DEV_SOURCE_PROVISIONED_FILE_LIMIT");
            }
            final var buffer = ByteBuffer.allocate((int) lease.size());
            raw = buffer.array();
            while (buffer.hasRemaining()) {
                if (lease.read(buffer) < 0) {
                    throw new IOException("DEV_SOURCE_PROVISIONED_FILE_TRUNCATED");
                }
            }
            selected = select(raw);
            return selected;
        } catch (final IOException | RuntimeException bad) {
            try {
                close();
            } catch (final IOException cleanup) {
                final var sanitized = new IllegalStateException("DEV_SOURCE_PROVISIONING_FAILED");
                sanitized.addSuppressed(cleanup);
                throw sanitized;
            }
            // Decoder/parser/URI exception messages can contain input. Never propagate them.
            throw new IllegalStateException("DEV_SOURCE_PROVISIONING_FAILED");
        } finally {
            if (raw != null) {
                java.util.Arrays.fill(raw, (byte) 0);
            }
        }
    }

    // Metadata/parser tests use local bytes only; no secret provider or source request is admitted.
    static Map<String, String> select(final byte[] raw) throws IOException {
        if (raw.length > MAX_FILE_BYTES) {
            throw new IOException("DEV_SOURCE_PROVISIONED_FILE_LIMIT");
        }
        final String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(raw))
                        .toString();
        final var result = new HashMap<String, String>();
        for (final String original : text.split("\\n", -1)) {
            String line = original.strip();
            if (line.startsWith("\uFEFF")) {
                line = line.substring(1).stripLeading();
            }
            if (line.startsWith("export ")) {
                line = line.substring(7).stripLeading();
            }
            final int split = line.indexOf('=');
            if (split <= 0) {
                continue;
            }
            final String key = line.substring(0, split).strip();
            if (!KEYS.contains(key)) {
                continue;
            }
            String value = line.substring(split + 1).strip();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                            || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            if (value.indexOf('\u0000') >= 0 || value.indexOf('\r') >= 0 || value.contains("${")) {
                throw new IOException("DEV_SOURCE_PROVISIONED_LITERAL_REQUIRED");
            }
            if (!value.isBlank()) {
                result.put(key, value);
            }
        }
        final String base = result.getOrDefault("API_BASEURL", result.get("API_BASE_URL"));
        if (base == null || !result.containsKey("API_DATAEXPORT_TOKEN")) {
            throw new IOException("DEV_SOURCE_PROVISIONED_KEYS_ABSENT");
        }
        // Priority is between aliases; duplicate selection is last-nonempty within each key.
        final var canonical = new HashMap<String, String>();
        canonical.put("API_BASE_URL", base);
        canonical.put("API_DATAEXPORT_TOKEN", result.get("API_DATAEXPORT_TOKEN"));
        result.clear();
        return canonical;
    }

    @Override
    public synchronized void close() throws IOException {
        closed = true;
        if (selected != null) {
            selected.clear();
            selected = null;
        }
        if (lease != null) {
            try {
                lease.close();
            } finally {
                lease = null;
            }
        }
    }
}
