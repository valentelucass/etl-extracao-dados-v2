package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionPlan;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Closed request document; source contract comes from the packaged first-wave catalog. */
final class RuntimeOperationalRequest {
    final RuntimeUsersRequest users;
    final UUID invocation;
    final DataExportTemplate template;
    final SourceContractRelease release;
    final ContractCompatibilityPolicy compatibility;
    final ContractExecutionBinding binding;
    final RuntimeExecutionPlan plan;
    final BusinessDateRange dates;
    final Optional<SourceDateTimeRange> updatedAt;
    final DataExportExtractionLimits limits;
    final int pageSize;
    final DataQualityPolicyReference quality;
    final RuntimeOperationalRequest dependency;
    final RuntimeTemporalBinding temporal;
    final long referenceReleaseId;

    private RuntimeOperationalRequest(
            final RuntimeConfiguration configuration,
            final com.fasterxml.jackson.databind.JsonNode root)
            throws Exception {
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("OPERATIONAL_REQUEST_SCHEMA");
        }
        if (root.has("protocol") || root.has("operation")) {
            users = RuntimeUsersRequest.read(configuration, root);
            invocation = users.invocation();
            release = users.release();
            compatibility = users.compatibility();
            binding = users.binding();
            plan = users.plan();
            quality = users.quality();
            template = null;
            dates = null;
            updatedAt = Optional.empty();
            limits = null;
            pageSize = 20;
            dependency = null;
            temporal = null;
            referenceReleaseId = 0;
            return;
        }
        users = null;
        final Set<String> keys =
                new java.util.HashSet<>(
                        Set.of(
                                "invocationId",
                                "executionId",
                                "cycleId",
                                "template",
                                "mode",
                                "start",
                                "endExclusive",
                                "replayOf",
                                "idempotencyKey",
                                "businessStart",
                                "businessEnd",
                                "leaseSeconds",
                                "pageSize",
                                "maximumPages",
                                "maximumRows",
                                "maximumDistinctRoots",
                                "qualityVersion",
                                "qualityFingerprint",
                                "compatibilityVersion"));
        if (root.has("dependencyRequest")) {
            keys.add("dependencyRequest");
        }
        if ("COTACOES".equals(root.path("template").asText())) {
            keys.add("referenceReleaseId");
        }
        if (root.has("temporalPolicy") || root.has("temporalWindow")) {
            keys.add("temporalPolicy");
            keys.add("temporalWindow");
        }
        final var actual = new java.util.HashSet<String>();
        root.fieldNames().forEachRemaining(actual::add);
        if (!root.isObject() || !actual.equals(keys)) {
            throw new IllegalArgumentException("OPERATIONAL_REQUEST_SCHEMA");
        }
        for (final String key : keys) {
            if (!root.get(key).isTextual()) {
                throw new IllegalArgumentException("OPERATIONAL_REQUEST_STRING_REQUIRED");
            }
        }
        invocation = UUID.fromString(root.get("invocationId").textValue());
        temporal = RuntimeTemporalBinding.read(configuration, root);
        final UUID execution = UUID.fromString(root.get("executionId").textValue()),
                cycle = UUID.fromString(root.get("cycleId").textValue());
        template = DataExportTemplate.valueOf(root.get("template").textValue());
        referenceReleaseId =
                template == DataExportTemplate.COTACOES
                        ? Long.parseLong(root.get("referenceReleaseId").textValue())
                        : 0;
        if (template == DataExportTemplate.COTACOES && referenceReleaseId < 1) {
            throw new IllegalArgumentException("EXPLICIT_TARIFF_RELEASE_REQUIRED");
        }
        final com.fasterxml.jackson.databind.node.ObjectNode predecessor;
        if (keys.contains("dependencyRequest")) {
            final var nested =
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_TRAILING_TOKENS)
                            .build()
                            .readTree(root.get("dependencyRequest").textValue());
            if (template != DataExportTemplate.FRETES
                    || nested == null
                    || !nested.isObject()
                    || nested.has("dependencyRequest")
                    || !"COLETAS".equals(nested.path("template").asText())) {
                throw new IllegalArgumentException("ONLY_COLETAS_TO_FRETES_DEPENDENCY");
            }
            for (final String field :
                    List.of("start", "endExclusive", "mode", "businessStart", "businessEnd")) {
                if (!root.get(field).equals(nested.get(field))) {
                    throw new IllegalArgumentException("DEPENDENCY_WINDOW_MISMATCH");
                }
            }
            predecessor = (com.fasterxml.jackson.databind.node.ObjectNode) nested;
            predecessor.put(
                    "invocationId",
                    UUID.nameUUIDFromBytes(
                                    (invocation + "|dependency-status")
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8))
                            .toString());
            dependency = new RuntimeOperationalRequest(configuration, predecessor);
        } else {
            predecessor = null;
            dependency = null;
        }
        final ExecutionMode mode = ExecutionMode.valueOf(root.get("mode").textValue());
        if (template.laboratoryBackfillOnly() && mode != ExecutionMode.BACKFILL) {
            throw new IllegalArgumentException("VERTICAL_BACKFILL_ONLY");
        }
        final Instant start = Instant.parse(root.get("start").textValue()),
                end = Instant.parse(root.get("endExclusive").textValue());
        if (start.getNano() % 1000000 != 0
                || end.getNano() % 1000000 != 0
                || mode == ExecutionMode.SWEEP) {
            throw new IllegalArgumentException("OPERATIONAL_WINDOW_INVALID");
        }
        final String replay = root.get("replayOf").textValue();
        final var source =
                configuration
                        .dataExport()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "DATAEXPORT_CONFIGURATION_REQUIRED"));
        release = RuntimeLaboratoryContract.resolve(configuration, template);
        compatibility =
                ContractCompatibilityPolicy.create(
                        root.get("compatibilityVersion").textValue(),
                        release.contractFingerprint(),
                        List.of());
        binding =
                ContractExecutionBinding.create(
                        execution,
                        release,
                        compatibility,
                        DataExportRuntimeConfigurationFingerprint.from(source));
        final var sorted = new java.util.TreeMap<String, String>();
        keys.forEach(key -> sorted.put(key, root.get(key).textValue()));
        if (temporal != null) {
            sorted.put("temporalPolicy", temporal.canonicalDocument());
        }
        if (predecessor != null) {
            // The dependency occurrence is frozen; a fresh status authorization does not change the
            // plan.
            final var dependencyMaterial = predecessor.deepCopy();
            dependencyMaterial.remove("invocationId");
            final var dependencyFields = new java.util.TreeMap<String, String>();
            dependencyMaterial
                    .fields()
                    .forEachRemaining(
                            entry ->
                                    dependencyFields.put(
                                            entry.getKey(), entry.getValue().textValue()));
            sorted.put(
                    "dependencyRequest",
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .writeValueAsString(dependencyFields));
        }
        // A fresh authorization can recover the same occurrence after consume/dispatch ack loss.
        // Invocation identity remains bound separately in RuntimeAuthorizationScope.
        sorted.remove("invocationId");
        // Includes every filter/limit and the database destination, so a changed request cannot
        // reuse a capability.
        sorted.put("workloadTarget", configuration.shadowStorage().jdbcUrl());
        if (template.laboratoryBackfillOnly()) {
            sorted.put("templateSemantics", template.contractSemanticsFingerprint().sha256());
        }
        final String canonical =
                new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(sorted);
        final var fingerprint =
                new ImmutableFingerprint(
                        "runtime-request-v1",
                        java.util.HexFormat.of()
                                .formatHex(
                                        java.security.MessageDigest.getInstance("SHA-256")
                                                .digest(
                                                        canonical.getBytes(
                                                                java.nio.charset.StandardCharsets
                                                                        .UTF_8))));
        final String entity = RuntimeVertical.entity(template);
        final var id = new RuntimeWorkloadId(entity);
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        source.sourceInstance(),
                        source.tenantScope(),
                        entity,
                        binding.contractFingerprint(),
                        binding.configurationFingerprint(),
                        Duration.ofSeconds(Long.parseLong(root.get("leaseSeconds").textValue())));
        plan =
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
                                                RuntimeWindowStrategy.INTERVAL,
                                                start,
                                                end,
                                                root.get("idempotencyKey").textValue(),
                                                replay.isEmpty()
                                                        ? Optional.empty()
                                                        : Optional.of(UUID.fromString(replay)))));
        dates =
                new BusinessDateRange(
                        LocalDate.parse(root.get("businessStart").textValue()),
                        LocalDate.parse(root.get("businessEnd").textValue()));
        // The source accepts inclusive whole seconds. Keep the durable partition half-open;
        // only incremental extraction overlaps its start, never the publication frontier.
        updatedAt =
                temporal != null && mode == ExecutionMode.INCREMENTAL
                        ? Optional.of(
                                new SourceDateTimeRange(
                                        temporal.window.extractionStart(), end.minusSeconds(1)))
                        : Optional.empty();
        if (temporal == null
                && laboratory()
                && (!start.equals(
                                dates.startInclusive()
                                        .atStartOfDay(source.settings().sourceZone())
                                        .toInstant())
                        || !end.equals(
                                dates.endInclusive()
                                        .plusDays(1)
                                        .atStartOfDay(source.settings().sourceZone())
                                        .toInstant()))) {
            throw new IllegalArgumentException("LABORATORY_CIVIL_WINDOW_MISMATCH");
        }
        pageSize = Integer.parseInt(root.get("pageSize").textValue());
        limits =
                new DataExportExtractionLimits(
                        Integer.parseInt(root.get("maximumPages").textValue()),
                        Long.parseLong(root.get("maximumRows").textValue()),
                        Integer.parseInt(root.get("maximumDistinctRoots").textValue()));
        if (laboratory()
                && (pageSize > 16
                        || Integer.parseInt(root.get("maximumPages").textValue()) > 4
                        || Long.parseLong(root.get("maximumRows").textValue()) > 16
                        || Integer.parseInt(root.get("maximumDistinctRoots").textValue()) > 16)) {
            throw new IllegalArgumentException("LABORATORY_EXTRACTION_LIMIT");
        }
        quality =
                new DataQualityPolicyReference(
                        root.get("qualityVersion").textValue(),
                        root.get("qualityFingerprint").textValue());
    }

    static RuntimeOperationalRequest read(
            final RuntimeConfiguration configuration, final Path file) {
        try (var input = Files.newInputStream(file)) {
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalArgumentException("OPERATIONAL_REQUEST_LIMIT");
            }
            final var mapper =
                    JsonMapper.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .enable(
                                    com.fasterxml.jackson.databind.DeserializationFeature
                                            .FAIL_ON_TRAILING_TOKENS)
                            .build();
            return new RuntimeOperationalRequest(configuration, mapper.readTree(bytes));
        } catch (final Exception failure) {
            throw new IllegalArgumentException("OPERATIONAL_REQUEST_INVALID", failure);
        }
    }

    DataExportPageRequest firstPage() {
        if (users != null) {
            throw new IllegalStateException("USERS_HAS_NO_DATA_EXPORT_REQUEST");
        }
        return new DataExportPageRequest(
                template, dates, updatedAt, 1, pageSize, template.defaultOrderBy());
    }

    boolean laboratory() {
        return release.contractVersion().equals("bloco54-synthetic-v1")
                || release.contractVersion().equals("bloco55-synthetic-v1");
    }

    static com.fasterxml.jackson.databind.JsonNode parseDocument(final String text)
            throws Exception {
        if (text == null || text.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 16384) {
            throw new IllegalArgumentException("OPERATIONAL_REQUEST_LIMIT");
        }
        return JsonMapper.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .enable(
                        com.fasterxml.jackson.databind.DeserializationFeature
                                .FAIL_ON_TRAILING_TOKENS)
                .build()
                .readTree(text);
    }

    static RuntimeOperationalRequest fromDocument(
            final RuntimeConfiguration configuration,
            final com.fasterxml.jackson.databind.JsonNode document)
            throws Exception {
        return new RuntimeOperationalRequest(configuration, document);
    }

    @Override
    public String toString() {
        return "RuntimeOperationalRequest[redacted]";
    }
}
