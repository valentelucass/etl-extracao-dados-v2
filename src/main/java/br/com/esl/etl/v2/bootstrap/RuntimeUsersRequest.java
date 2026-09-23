package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Closed GraphQL request. The interval identifies an observation, never a source time filter. */
record RuntimeUsersRequest(
        UUID invocation,
        SourceContractRelease release,
        ContractCompatibilityPolicy compatibility,
        ContractExecutionBinding binding,
        RuntimeExecutionPlan plan,
        GraphQlExtractionLimits limits,
        DataQualityPolicyReference quality) {

    static RuntimeUsersRequest read(final RuntimeConfiguration configuration, final JsonNode root)
            throws Exception {
        final Set<String> keys =
                Set.of(
                        "protocol",
                        "operation",
                        "invocationId",
                        "executionId",
                        "cycleId",
                        "mode",
                        "start",
                        "endExclusive",
                        "replayOf",
                        "idempotencyKey",
                        "leaseSeconds",
                        "pageSize",
                        "maximumPages",
                        "maximumNodes",
                        "qualityVersion",
                        "qualityFingerprint",
                        "compatibilityVersion");
        final var actual = new HashSet<String>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!root.isObject() || !actual.equals(keys)) {
            throw new IllegalArgumentException("USERS_REQUEST_SCHEMA");
        }
        final var material = new TreeMap<String, String>();
        for (final String key : keys) {
            if (!root.get(key).isTextual() || root.get(key).textValue().length() > 200) {
                throw new IllegalArgumentException("USERS_REQUEST_STRING_REQUIRED");
            }
            material.put(key, root.get(key).textValue());
        }
        if (!"GRAPHQL".equals(material.get("protocol"))
                || !GraphQlReadOperation.USERS_SNAPSHOT.name().equals(material.get("operation"))
                || !"20".equals(material.get("pageSize"))) {
            throw new IllegalArgumentException("USERS_OPERATION_REQUIRED");
        }
        final var source =
                configuration
                        .graphQl()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "GRAPHQL_CONFIGURATION_REQUIRED"));
        for (final String scope : List.of(source.sourceInstance(), source.tenantScope())) {
            if (Set.of("DEFAULT", "GLOBAL", "SINGLETON")
                    .contains(scope.toUpperCase(java.util.Locale.ROOT))) {
                throw new IllegalArgumentException("USERS_EXPLICIT_SCOPE_REQUIRED");
            }
        }
        configuration
                .dataExport()
                .ifPresent(
                        other -> {
                            if (!source.sourceInstance().equals(other.sourceInstance())
                                    || !source.tenantScope().equals(other.tenantScope())
                                    || !source.resiliencePolicy()
                                            .equals(other.resiliencePolicy())) {
                                throw new IllegalArgumentException(
                                        "USERS_SHARED_SOURCE_CONFIGURATION_MISMATCH");
                            }
                        });
        final var mode = ExecutionMode.valueOf(material.get("mode"));
        if (mode != ExecutionMode.BACKFILL && mode != ExecutionMode.REPLAY) {
            throw new IllegalArgumentException("USERS_OBSERVATION_OR_REPLAY_REQUIRED");
        }
        final Instant start = Instant.parse(material.get("start")),
                end = Instant.parse(material.get("endExclusive"));
        if (start.getNano() % 1_000_000 != 0 || end.getNano() % 1_000_000 != 0) {
            throw new IllegalArgumentException("USERS_OBSERVATION_PRECISION");
        }
        final var execution = UUID.fromString(material.get("executionId"));
        final var cycle = UUID.fromString(material.get("cycleId"));
        final var invocation = UUID.fromString(material.remove("invocationId"));
        final var limits =
                new GraphQlExtractionLimits(
                        Integer.parseInt(material.get("maximumPages")),
                        Long.parseLong(material.get("maximumNodes")));
        if (limits.maxPages() > source.resiliencePolicy().maxRequestsPerWorkload()) {
            throw new IllegalArgumentException("USERS_REQUEST_BUDGET_MISMATCH");
        }
        final var release =
                GraphQlFirstWaveContractCatalog.release(GraphQlReadOperation.USERS_SNAPSHOT);
        final var compatibility =
                ContractCompatibilityPolicy.create(
                        material.get("compatibilityVersion"),
                        release.contractFingerprint(),
                        List.of());
        final var binding =
                ContractExecutionBinding.create(
                        execution,
                        release,
                        compatibility,
                        GraphQlRuntimeConfigurationFingerprint.from(source));
        material.put("workloadTarget", configuration.shadowStorage().jdbcUrl());
        final var fingerprint =
                new ImmutableFingerprint(
                        "runtime-users-request-v1",
                        HexFormat.of()
                                .formatHex(
                                        MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        new ObjectMapper()
                                                                .writeValueAsString(material)
                                                                .getBytes(
                                                                        StandardCharsets.UTF_8))));
        final var id = new RuntimeWorkloadId("usuarios");
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "GRAPHQL",
                        source.sourceInstance(),
                        source.tenantScope(),
                        "usuarios",
                        binding.contractFingerprint(),
                        binding.configurationFingerprint(),
                        Duration.ofSeconds(Long.parseLong(material.get("leaseSeconds"))));
        final String replay = material.get("replayOf");
        final var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        cycle,
                                        configuration.environment().name(),
                                        fingerprint,
                                        configuration.clock().instant(),
                                        new RuntimeExecutionRequest(
                                                execution,
                                                id,
                                                mode,
                                                RuntimeWindowStrategy.FULL,
                                                start,
                                                end,
                                                material.get("idempotencyKey"),
                                                replay.isEmpty()
                                                        ? Optional.empty()
                                                        : Optional.of(UUID.fromString(replay)))));
        return new RuntimeUsersRequest(
                invocation,
                release,
                compatibility,
                binding,
                plan,
                limits,
                new DataQualityPolicyReference(
                        material.get("qualityVersion"), material.get("qualityFingerprint")));
    }

    @Override
    public String toString() {
        return "RuntimeUsersRequest[redacted]";
    }
}
