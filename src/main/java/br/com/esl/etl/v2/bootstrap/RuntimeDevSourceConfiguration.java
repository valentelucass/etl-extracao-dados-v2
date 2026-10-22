package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurrentSourceInfoEvidence;
import com.sun.nio.file.ExtendedOpenOption;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/** DEV source metadata only. The legacy credential file is never opened by this stage. */
final class RuntimeDevSourceConfiguration implements AutoCloseable {
    static final String REFERENCE_KEY = "runtime.dev.source.metadata-reference";
    static final String HASH_KEY = "runtime.dev.source.metadata-sha256";
    static final Path REFERENCE =
            Path.of(
                    "C:/Users/suporte/Documents/Armazenamento-Projetos/etl-extracao-dados-v2/"
                            + "execucao-esl-shadow-20261008-164952/hermes863b2573942e/"
                            + "runtime01/deployment02/runtime.private.properties");
    static final String REFERENCE_SHA256 =
            "451255e86128d6893c781a7ba1399b2b43e19963c983f358bc2bee8f798b37d9";
    private static final Set<String> PLAN_KEYS =
            Set.of(
                    "runtime.environment",
                    "runtime.business-timezone",
                    "dataexport.enabled",
                    "dataexport.source-instance",
                    "dataexport.tenant-scope",
                    "dataexport.timezone",
                    "dataexport.transport",
                    "dataexport.timeout-seconds",
                    "dataexport.max-response-bytes",
                    "dataexport.retry.max-attempts",
                    "dataexport.retry.initial-delay-ms",
                    "dataexport.retry.max-delay-ms",
                    "dataexport.resilience.minimum-request-interval-ms",
                    "dataexport.resilience.max-in-flight",
                    "dataexport.resilience.max-requests-per-cycle",
                    "dataexport.resilience.max-requests-per-workload",
                    "dataexport.resilience.step-timeout-seconds",
                    "dataexport.resilience.cycle-timeout-seconds",
                    "dataexport.resilience.max-retry-after-ms",
                    "dataexport.resilience.max-repartitions",
                    "dataexport.resilience.circuit-failure-threshold",
                    "dataexport.resilience.circuit-cooldown-seconds",
                    "graphql.enabled",
                    "shadow.audit.enabled");
    final RuntimeConfiguration configuration;
    final boolean curatedSource;
    final boolean sampleOptIn;
    private final FileChannel metadataLease;

    private RuntimeDevSourceConfiguration(
            final RuntimeConfiguration configuration,
            final FileChannel metadataLease,
            final boolean curatedSource,
            final boolean sampleOptIn) {
        this.configuration = configuration;
        this.metadataLease = metadataLease;
        this.curatedSource = curatedSource;
        this.sampleOptIn = sampleOptIn;
    }

    static RuntimeDevSourceConfiguration open(
            final RuntimeDevWorkspaceBundle bundle,
            final RuntimeConfigurationFactory factory,
            final Properties system,
            final Map<String, String> environment,
            final Clock clock)
            throws Exception {
        final Properties plan = new Properties();
        if (java.nio.file.Files.size(bundle.configuration()) > 65_536) {
            throw new IOException("DEV_SOURCE_PLAN_INPUT_LIMIT");
        }
        try (var input = java.nio.file.Files.newInputStream(bundle.configuration());
                var reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
            plan.load(reader);
        }
        final boolean curated = selectedOptIn(plan, "runtime.dev.source.provisioning.enabled");
        final boolean sample = selectedOptIn(plan, "runtime.dev.engine.sample.enabled");
        if (!curated) {
            if (sample) {
                throw new IOException("DEV_SAMPLE_CURATED_PROFILE_REQUIRED");
            }
            return new RuntimeDevSourceConfiguration(
                    factory.load(bundle.configuration(), system, environment, clock),
                    null,
                    false,
                    false);
        }
        final Path unit = bundle.configuration().getParent().getParent().getParent();
        final String namespace = unit.getFileName().toString();
        validateCurrentUnit(namespace, sample, system, environment);
        plan.remove("runtime.dev.source.provisioning.enabled");
        plan.remove("runtime.dev.engine.sample.enabled");
        if (!REFERENCE.equals(Path.of(plan.getProperty(REFERENCE_KEY, "")))
                || !REFERENCE_SHA256.equals(plan.getProperty(HASH_KEY))) {
            throw new IOException("DEV_SOURCE_REVIEWED_METADATA_REFERENCE_REQUIRED");
        }
        plan.remove(REFERENCE_KEY);
        plan.remove(HASH_KEY);
        final var merged = mergePlan(plan, environment);
        if (!REFERENCE.toRealPath().equals(REFERENCE)) {
            throw new IOException("DEV_SOURCE_METADATA_LINK_REFUSED");
        }
        final FileChannel lease =
                FileChannel.open(
                        REFERENCE,
                        Set.of(
                                StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS,
                                ExtendedOpenOption.NOSHARE_WRITE,
                                        ExtendedOpenOption.NOSHARE_DELETE));
        try {
            if (lease.size() > 65_536) {
                throw new IOException("DEV_SOURCE_METADATA_INPUT_LIMIT");
            }
            final var raw = ByteBuffer.allocate((int) lease.size());
            while (raw.hasRemaining()) {
                if (lease.read(raw) < 0) {
                    throw new IOException("DEV_SOURCE_METADATA_TRUNCATED");
                }
            }
            if (!HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(raw.array()))
                    .equals(REFERENCE_SHA256)) {
                throw new IOException("DEV_SOURCE_METADATA_CHANGED");
            }
            // The exact reviewed file contains nonsecret settings only. The factory's existing
            // key/secret policy remains in force; only new plan partitions and limits override.
            final var configuration = factory.load(REFERENCE, system, merged, clock);
            RuntimeDevSourceProvisioning.verifyMetadata(configuration.dataExport().orElseThrow());
            return new RuntimeDevSourceConfiguration(configuration, lease, true, sample);
        } catch (final Exception | Error failure) {
            try {
                lease.close();
            } catch (final IOException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    static void validateCurrentUnit(
            final String namespace,
            final boolean sample,
            final Properties system,
            final Map<String, String> environment)
            throws IOException {
        final boolean currentSample = sample && CurrentSourceInfoEvidence.supportsUnit(namespace);
        if (!"hermesf4eda62e83d1".equals(namespace) && !currentSample) {
            throw new IOException("DEV_SOURCE_METADATA_CURRENT_UNIT_REQUIRED");
        }
        if (currentSample) {
            for (final String name : Set.of("V2_DATAEXPORT_BASE_URL", "V2_DATAEXPORT_TOKEN")) {
                if (environment.containsKey(name) || system.containsKey(name)) {
                    throw new IOException("DEV_SOURCE_NATIVE_OVERRIDE_AMBIGUOUS");
                }
            }
        }
    }

    static boolean selectedOptIn(final Properties plan, final String key) throws IOException {
        final String value = plan.getProperty(key, "false");
        if (!Set.of("true", "false").contains(value)) {
            throw new IOException("DEV_SOURCE_EXPLICIT_BOOLEAN_REQUIRED");
        }
        return Boolean.getBoolean(key) || value.equals("true");
    }

    static Map<String, String> mergePlan(
            final Properties plan, final Map<String, String> environment) throws IOException {
        if (!PLAN_KEYS.equals(plan.stringPropertyNames())
                || !"true".equals(plan.getProperty("dataexport.enabled"))
                || !"false".equals(plan.getProperty("shadow.audit.enabled"))
                || !"false".equals(plan.getProperty("graphql.enabled"))) {
            throw new IOException("DEV_SOURCE_NONSECRET_PLAN_SCOPE");
        }
        final var result = new HashMap<>(environment);
        for (final String nativeKey : Set.of("V2_DATAEXPORT_BASE_URL", "V2_DATAEXPORT_TOKEN")) {
            if (environment.containsKey(nativeKey)) {
                throw new IOException("DEV_SOURCE_NATIVE_INPUT_CONFLICT");
            }
        }
        for (final String key : plan.stringPropertyNames()) {
            final String nativeKey =
                    "V2_"
                            + key.toUpperCase(java.util.Locale.ROOT)
                                    .replace('.', '_')
                                    .replace('-', '_');
            final String value = plan.getProperty(key);
            final String supplied = environment.get(nativeKey);
            if (supplied != null && !supplied.isBlank() && !supplied.equals(value)) {
                throw new IOException("DEV_SOURCE_PLAN_ENVIRONMENT_CONFLICT");
            }
            result.put(nativeKey, value);
        }
        return Map.copyOf(result);
    }

    @Override
    public void close() throws IOException {
        if (metadataLease != null) {
            metadataLease.close();
        }
    }
}
