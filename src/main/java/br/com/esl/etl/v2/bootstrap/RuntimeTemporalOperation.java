package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.autorizacao.WindowsSqlRuntimeAuthorization;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.persistencia.controle.BoundedRuntimeDataSource;
import br.com.esl.etl.v2.plataforma.persistencia.controle.JdbcSqlServerTemporalPlan;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Explicit temporal persistence via RUN; PLAN remains offline. Each window consumes its own
 * receipt.
 */
final class RuntimeTemporalOperation {
    private static final Set<String> KEYS =
            Set.of(
                    "version",
                    "purpose",
                    "workload",
                    "dependsOn",
                    "invocation",
                    "firstUnpublished",
                    "observedAt",
                    "zone",
                    "mode",
                    "strategy",
                    "cadence",
                    "civilBoundary",
                    "lookbackSeconds",
                    "stabilizationSeconds",
                    "slaSeconds",
                    "deadlineSeconds",
                    "concurrency",
                    "maximumBacklog",
                    "maximumReconciliation",
                    "maximumDegraded",
                    "blackouts");
    final RuntimeTemporalPolicy policy;
    final RuntimeTemporalPlanner.Result result;
    final String workload;
    final UUID invocation;
    final LocalDate first;
    final Instant observed;
    private final com.fasterxml.jackson.databind.node.ObjectNode document;

    RuntimeTemporalOperation(final JsonNode root) {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("TEMPORAL_DOCUMENT_SCHEMA");
        }
        final var keys = new java.util.HashSet<String>();
        root.fieldNames().forEachRemaining(keys::add);
        if (!root.isObject()
                || !keys.equals(KEYS)
                || !Set.of("SYNTHETIC_BLOCO54_PERSIST_ONLY", "SYNTHETIC_BLOCO55_BACKFILL")
                        .contains(root.path("purpose").asText())) {
            throw new IllegalArgumentException("TEMPORAL_DOCUMENT_SCHEMA");
        }
        for (final String key : KEYS) {
            if (!root.get(key).isTextual() || root.get(key).textValue().length() > 4096) {
                throw new IllegalArgumentException("TEMPORAL_DOCUMENT_STRING_REQUIRED");
            }
        }
        document = ((com.fasterxml.jackson.databind.node.ObjectNode) root).deepCopy();
        workload = root.get("workload").textValue();
        final boolean extension =
                Set.of("manifestos", "cotacoes", "localizacao_cargas").contains(workload);
        if (!("coletas".equals(workload) && root.get("dependsOn").textValue().isEmpty()
                || "fretes".equals(workload) && "coletas".equals(root.get("dependsOn").textValue())
                || extension && root.get("dependsOn").textValue().isEmpty())) {
            throw new IllegalArgumentException("TEMPORAL_LABORATORY_DAG");
        }
        if (extension
                && (!"SYNTHETIC_BLOCO55_BACKFILL".equals(root.get("purpose").textValue())
                        || !("bloco55-" + workload + "-civil-v1")
                                .equals(root.get("version").textValue())
                        || !"BACKFILL".equals(root.get("mode").textValue())
                        || !"America/Sao_Paulo".equals(root.get("zone").textValue())
                        || !"00:00".equals(root.get("civilBoundary").textValue())
                        || !"0".equals(root.get("lookbackSeconds").textValue()))) {
            throw new IllegalArgumentException("B55_TEMPORAL_CONTRACT_REQUIRED");
        }
        invocation = UUID.fromString(root.get("invocation").textValue());
        first = LocalDate.parse(root.get("firstUnpublished").textValue());
        observed = Instant.parse(root.get("observedAt").textValue());
        final var blackouts = new java.util.ArrayList<RuntimeTemporalPolicy.Blackout>();
        final String blackout = root.get("blackouts").textValue();
        if (!blackout.isEmpty()) {
            final var intervals = blackout.split(",", -1);
            if (intervals.length > 64) {
                throw new IllegalArgumentException("TEMPORAL_BLACKOUT_LIMIT");
            }
            for (final String interval : intervals) {
                final var dates = interval.split("/", -1);
                if (dates.length != 2) {
                    throw new IllegalArgumentException("TEMPORAL_BLACKOUT_SCHEMA");
                }
                blackouts.add(
                        new RuntimeTemporalPolicy.Blackout(
                                LocalDate.parse(dates[0]), LocalDate.parse(dates[1])));
            }
        }
        policy =
                new RuntimeTemporalPolicy(
                        root.get("version").textValue(),
                        ZoneId.of(root.get("zone").textValue()),
                        ExecutionMode.valueOf(root.get("mode").textValue()),
                        RuntimeWindowStrategy.valueOf(root.get("strategy").textValue()),
                        RuntimeTemporalPolicy.Cadence.valueOf(root.get("cadence").textValue()),
                        LocalTime.parse(root.get("civilBoundary").textValue()),
                        seconds(root, "lookbackSeconds"),
                        seconds(root, "stabilizationSeconds"),
                        seconds(root, "slaSeconds"),
                        seconds(root, "deadlineSeconds"),
                        number(root, "concurrency"),
                        number(root, "maximumBacklog"),
                        number(root, "maximumReconciliation"),
                        number(root, "maximumDegraded"),
                        blackouts);
        if (policy.maximumBacklog() > 4
                || policy.concurrency() > 2
                || policy.mode() != ExecutionMode.BACKFILL
                        && policy.mode() != ExecutionMode.INCREMENTAL) {
            throw new IllegalArgumentException("TEMPORAL_LABORATORY_BUDGET");
        }
        result = new RuntimeTemporalPlanner().plan(policy, first, observed);
    }

    static RuntimeTemporalOperation read(final Path file) {
        try (var input = Files.newInputStream(file)) {
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalArgumentException("TEMPORAL_DOCUMENT_LIMIT");
            }
            return new RuntimeTemporalOperation(
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_TRAILING_TOKENS)
                            .build()
                            .readTree(bytes));
        } catch (final java.io.IOException failure) {
            throw new IllegalArgumentException("TEMPORAL_DOCUMENT_UNAVAILABLE", failure);
        }
    }

    String preview() {
        return "TEMPORAL_PREVIEW effects=0 windows="
                + result.windows().size()
                + " backlog="
                + result.backlogRemaining()
                + " blackout="
                + result.blockedByBlackout()
                + " policy="
                + policy.fingerprint().sha256()
                + " workload="
                + workload;
    }

    String operationalRequests(final RuntimeConfiguration configuration, final Path blueprint) {
        return operationalRequests(configuration, blueprint, 0);
    }

    String operationalRequests(
            final RuntimeConfiguration configuration,
            final Path blueprint,
            final int selectedWindow) {
        if (selectedWindow < 0 || selectedWindow > result.windows().size()) {
            throw new IllegalArgumentException("TEMPORAL_WINDOW_OUT_OF_PLAN");
        }
        try (var input = Files.newInputStream(blueprint)) {
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalArgumentException("TEMPORAL_BLUEPRINT_LIMIT");
            }
            final var parsed =
                    RuntimeOperationalRequest.parseDocument(
                            new String(bytes, StandardCharsets.UTF_8));
            if (!(parsed instanceof com.fasterxml.jackson.databind.node.ObjectNode object)) {
                throw new IllegalArgumentException("TEMPORAL_BLUEPRINT_SCHEMA");
            }
            final var requests =
                    com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
            for (int ordinal = 1; ordinal <= result.windows().size(); ordinal++) {
                if (selectedWindow != 0 && selectedWindow != ordinal) {
                    continue;
                }
                final var request =
                        RuntimeTemporalBinding.request(configuration, document, object, ordinal);
                RuntimeOperationalRequest.fromDocument(configuration, request);
                requests.add(request);
            }
            final String output = requests.toString();
            if (output.getBytes(StandardCharsets.UTF_8).length > 16384) {
                throw new IllegalArgumentException("TEMPORAL_REQUEST_OUTPUT_LIMIT");
            }
            return output;
        } catch (final Exception failure) {
            throw new IllegalArgumentException("TEMPORAL_BLUEPRINT_INVALID", failure);
        }
    }

    int persist(final RuntimeConfiguration configuration) {
        return persist(configuration, ignored -> {});
    }

    int persist(
            final RuntimeConfiguration configuration,
            final java.util.function.Consumer<String> diagnostic) {
        if (result.windows().isEmpty()) {
            throw new IllegalArgumentException("TEMPORAL_NO_DUE_WINDOW");
        }
        new RuntimeCompositionRoot(configuration).validateConfiguration();
        BoundedRuntimeDataSource.validateTarget(configuration.shadowStorage());
        if (!"LOCAL_SHADOW".equals(configuration.environment().name())) {
            throw new IllegalArgumentException("TEMPORAL_LOCAL_ONLY");
        }
        final var source = configuration.dataExport().orElseThrow();
        if (!"LOCAL_V2".equals(source.sourceInstance())
                || !"LOCAL_V2".equals(source.tenantScope())) {
            throw new IllegalArgumentException("TEMPORAL_LOCAL_NAMESPACE_REQUIRED");
        }
        final String namespace =
                hash("LOCAL_SHADOW|LOCAL_V2|LOCAL_V2|" + workload + "|" + policy.mode());
        return new SqlPersistence().persist(configuration, diagnostic, namespace);
    }

    private final class SqlPersistence {
        private int persist(
                final RuntimeConfiguration configuration,
                final java.util.function.Consumer<String> diagnostic,
                final String namespace) {
            final var authority = WindowsSqlRuntimeAuthorization.fromAdministeredArtifact();
            int persisted = 0;
            for (final var window : result.windows()) {
                final var frozen = freeze(configuration, window);
                final var scope = frozen.scope();
                final var cycle = frozen.cycle();
                final var capability = authority.authorize(scope);
                persisted +=
                        authority.consumeAndExecute(
                                capability,
                                scope,
                                () -> {
                                    final var store =
                                            new JdbcSqlServerTemporalPlan(
                                                    new BoundedRuntimeDataSource(
                                                            configuration.shadowStorage()));
                                    final int count =
                                            store.persist(
                                                    cycle,
                                                    namespace,
                                                    policy,
                                                    new RuntimeTemporalPlanner.Result(
                                                            List.of(window), false, false));
                                    final var reconciliation =
                                            new br.com.esl.etl.v2.plataforma.orquestracao
                                                            .RuntimeTemporalCoordinator(store)
                                                    .reconcile(
                                                            namespace,
                                                            policy,
                                                            result.windows()
                                                                    .get(0)
                                                                    .partitionStart(),
                                                            plannedOccurrences(configuration));
                                    diagnostic.accept(
                                            "TEMPORAL_RECONCILIATION contiguous="
                                                    + reconciliation.contiguousEnd()
                                                    + " pending="
                                                    + reconciliation.pending().size()
                                                    + " degraded="
                                                    + reconciliation.degraded()
                                                    + " limit="
                                                    + reconciliation.limitReached());
                                    return count;
                                });
            }
            return persisted;
        }
    }

    record Frozen(UUID cycle, String namespace, RuntimeAuthorizationScope scope) {}

    Frozen freeze(
            final RuntimeConfiguration configuration, final RuntimeTemporalPlanner.Window window) {
        if (!result.windows().contains(window)) {
            throw new IllegalArgumentException("TEMPORAL_WINDOW_NOT_IN_PLAN");
        }
        final String namespace =
                hash("LOCAL_SHADOW|LOCAL_V2|LOCAL_V2|" + workload + "|" + policy.mode());
        final String windowKey =
                namespace + "|" + window.partitionStart() + "|" + window.endExclusive();
        final UUID execution = uuid(windowKey),
                cycle = uuid(windowKey + "|" + policy.fingerprint().sha256());
        final var id = new RuntimeWorkloadId(workload);
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "LOCAL_V2",
                        "LOCAL_V2",
                        workload,
                        policy.fingerprint(),
                        policy.fingerprint(),
                        Duration.ofSeconds(30));
        final var intent =
                new ImmutableFingerprint(
                        "temporal-intent-v1", hash(windowKey + "|" + policy.material()));
        final var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        cycle,
                                        "LOCAL_SHADOW",
                                        intent,
                                        configuration.clock().instant(),
                                        new RuntimeExecutionRequest(
                                                execution,
                                                id,
                                                policy.mode(),
                                                policy.strategy(),
                                                window.partitionStart(),
                                                window.endExclusive(),
                                                cycle.toString())));
        final var scope =
                RuntimeAuthorizationScope.from(
                        uuid(invocation + "|" + windowKey), RuntimeAction.RUN, plan);
        return new Frozen(cycle, namespace, scope);
    }

    List<UUID> plannedOccurrences(final RuntimeConfiguration configuration) {
        return result.windows().stream()
                .map(window -> freeze(configuration, window).scope().executionId())
                .toList();
    }

    private static UUID uuid(final String text) {
        return UUID.nameUUIDFromBytes(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(final String text) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            java.security.MessageDigest.getInstance("SHA-256")
                                    .digest(text.getBytes(StandardCharsets.UTF_16LE)));
        } catch (final java.security.NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA256_REQUIRED", failure);
        }
    }

    private static int number(final JsonNode root, final String key) {
        return Integer.parseInt(root.get(key).textValue());
    }

    private static Duration seconds(final JsonNode root, final String key) {
        return Duration.ofSeconds(Long.parseLong(root.get(key).textValue()));
    }
}
