package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeTemporalBindingTest {
    @TempDir Path directory;

    private RuntimeConfiguration configuration() throws Exception {
        final var file = directory.resolve("configuration.properties");
        Files.writeString(
                file,
                Files.readString(Path.of("config/application.example.properties"))
                        .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                        .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1")
                        .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true")
                        .replace("127.0.0.1:<porta-local>", "localhost")
                        .replace("trustServerCertificate=true", "trustServerCertificate=false"));
        return new RuntimeConfigurationFactory()
                .load(
                        file,
                        new Properties(),
                        Map.of(
                                "V2_DATAEXPORT_ENABLED",
                                "true",
                                "V2_DATAEXPORT_BASE_URL",
                                "http://127.0.0.1:19540",
                                "V2_DATAEXPORT_SOURCE_INSTANCE",
                                "LOCAL_V2",
                                "V2_DATAEXPORT_TENANT_SCOPE",
                                "LOCAL_V2",
                                "V2_DATAEXPORT_TIMEZONE",
                                "America/Sao_Paulo"),
                        Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC));
    }

    private ObjectNode policy() throws Exception {
        return (ObjectNode)
                new ObjectMapper()
                        .readTree(
                                Path.of("config/laboratory/bloco54-temporal-coletas.json")
                                        .toFile());
    }

    private ObjectNode blueprint() {
        return new ObjectMapper()
                .createObjectNode()
                .put("invocationId", UUID.randomUUID().toString())
                .put("executionId", UUID.randomUUID().toString())
                .put("cycleId", UUID.randomUUID().toString())
                .put("template", "COLETAS")
                .put("mode", "BACKFILL")
                .put("start", "2024-01-01T03:00:00Z")
                .put("endExclusive", "2024-01-02T03:00:00Z")
                .put("replayOf", "")
                .put("idempotencyKey", "blueprint")
                .put("businessStart", "2024-01-01")
                .put("businessEnd", "2024-01-01")
                .put("leaseSeconds", "30")
                .put("pageSize", "2")
                .put("maximumPages", "4")
                .put("maximumRows", "16")
                .put("maximumDistinctRoots", "16")
                .put("qualityVersion", "test-quality-v1")
                .put("qualityFingerprint", "b".repeat(64))
                .put("compatibilityVersion", "strict-v1");
    }

    @Test
    void recoveryRequestsExtractHistoricalBusinessDatesWithoutUpdatedAtRestriction()
            throws Exception {
        final var config = configuration();
        final var document =
                policy().put("lookbackSeconds", "0")
                        .put("firstUnpublished", "2024-02-20")
                        .put("observedAt", "2024-03-12T15:00:00Z");
        final var operation = new RuntimeTemporalOperation(document);
        final var file = directory.resolve("recovery-blueprint.json");
        Files.writeString(file, blueprint().toString());
        final var requests = RuntimePeriodRecoveryOperation.requests(config, operation, file);
        assertEquals(4, requests.size());
        for (final var window : operation.result.windows()) {
            final var request = requests.get(window.partitionStart());
            assertNotNull(request);
            assertEquals(
                    window.partitionStart().atZone(operation.policy.zone()).toLocalDate(),
                    request.dates.startInclusive());
            assertEquals(request.dates.startInclusive(), request.dates.endInclusive());
            assertEquals(java.util.Optional.empty(), request.updatedAt);
            request.plan.forEach(
                    item ->
                            assertEquals(
                                    operation.freeze(config, window).scope().executionId(),
                                    item.request().executionId()));
        }
        document.put("firstUnpublished", "2024-02-24");
        final var next =
                RuntimePeriodRecoveryOperation.requests(
                        config, new RuntimeTemporalOperation(document), file);
        assertEquals(
                java.util.Set.of(),
                requests.keySet().stream()
                        .filter(next::containsKey)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void recoveryRejectsIncrementalAndLookbackBeforeOpeningAnyConnection() throws Exception {
        final var config = configuration();
        final var file = directory.resolve("recovery-rejected-blueprint.json");
        Files.writeString(file, blueprint().toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimePeriodRecoveryOperation.requests(
                                config, new RuntimeTemporalOperation(policy()), file));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimePeriodRecoveryOperation.requests(
                                config,
                                new RuntimeTemporalOperation(
                                        policy().put("lookbackSeconds", "0")
                                                .put("mode", "INCREMENTAL")),
                                file));
    }

    @Test
    void recoveryAuthorizesEverySqlEffectAndRefusesScopeSubstitution() throws Exception {
        final var config = configuration();
        final var operation = new RuntimeTemporalOperation(policy().put("lookbackSeconds", "0"));
        final var file = directory.resolve("recovery-authority-blueprint.json");
        Files.writeString(file, blueprint().toString());
        final var requests = RuntimePeriodRecoveryOperation.requests(config, operation, file);
        final var window = operation.result.windows().get(0);
        final var frozen = operation.freeze(config, window);
        final var scopes =
                new java.util.ArrayList<
                        br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope>();
        final int[] effects = {0};
        final var store =
                new RuntimePeriodRecoveryOperation.AuthorizedStore(
                        operation,
                        frozen.namespace(),
                        requests,
                        recoveryStore(effects),
                        new RuntimePeriodRecoveryOperation.Authority() {
                            @Override
                            public <T> T execute(
                                    br.com.esl.etl.v2.plataforma.autorizacao
                                                    .RuntimeAuthorizationScope
                                            scope,
                                    java.util.function.Supplier<T> effect) {
                                scopes.add(scope);
                                return effect.get();
                            }
                        });
        store.readGapPage(frozen.namespace(), 8, window.partitionStart());
        final var single =
                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner.Result(
                        java.util.List.of(window), false, false);
        assertEquals(
                1, store.persist(frozen.cycle(), frozen.namespace(), operation.policy, single));
        store.readGapPage(frozen.namespace(), 8, window.partitionStart());
        final var next = operation.result.windows().get(1);
        store.readGapPage(frozen.namespace(), 8, next.partitionStart());
        assertEquals(
                java.util.List.of(
                        RuntimeAction.STATUS,
                        RuntimeAction.RUN,
                        RuntimeAction.STATUS,
                        RuntimeAction.STATUS),
                scopes.stream()
                        .map(
                                br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope
                                        ::action)
                        .toList());
        assertEquals(
                4,
                scopes.stream()
                        .map(
                                br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope
                                        ::invocationId)
                        .distinct()
                        .count());
        assertEquals(frozen.scope().executionId(), scopes.get(0).executionId());
        assertNotEquals(scopes.get(0).executionId(), scopes.get(3).executionId());
        assertEquals(4, effects[0]);
        assertThrows(
                IllegalArgumentException.class,
                () -> store.readGapPage("b".repeat(64), 8, window.partitionStart()));
        assertThrows(
                IllegalArgumentException.class,
                () -> store.readGapPage(frozen.namespace(), 9, window.partitionStart()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        store.readGapPage(
                                frozen.namespace(), 8, window.partitionStart().minusSeconds(1)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        store.persist(
                                UUID.randomUUID(), frozen.namespace(), operation.policy, single));
        assertEquals(4, effects[0]);
        assertEquals(4, scopes.size());
    }

    @Test
    void recoveryAuthorizationRefusalPreventsSqlEffects() throws Exception {
        final var config = configuration();
        final var operation = new RuntimeTemporalOperation(policy().put("lookbackSeconds", "0"));
        final var file = directory.resolve("recovery-denied-blueprint.json");
        Files.writeString(file, blueprint().toString());
        final var requests = RuntimePeriodRecoveryOperation.requests(config, operation, file);
        final var window = operation.result.windows().get(0);
        final var frozen = operation.freeze(config, window);
        final int[] effects = {0};
        final var store =
                new RuntimePeriodRecoveryOperation.AuthorizedStore(
                        operation,
                        frozen.namespace(),
                        requests,
                        recoveryStore(effects),
                        new RuntimePeriodRecoveryOperation.Authority() {
                            @Override
                            public <T> T execute(
                                    br.com.esl.etl.v2.plataforma.autorizacao
                                                    .RuntimeAuthorizationScope
                                            scope,
                                    java.util.function.Supplier<T> effect) {
                                throw new IllegalStateException("synthetic-authority-denial");
                            }
                        });
        assertThrows(
                IllegalStateException.class,
                () -> store.readGapPage(frozen.namespace(), 8, window.partitionStart()));
        assertThrows(
                IllegalStateException.class,
                () ->
                        store.persist(
                                frozen.cycle(),
                                frozen.namespace(),
                                operation.policy,
                                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner
                                        .Result(java.util.List.of(window), false, false)));
        assertEquals(0, effects[0]);
    }

    private static br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalStore recoveryStore(
            final int[] effects) {
        return new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalStore() {
            @Override
            public int persist(
                    UUID plan,
                    String namespace,
                    br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy policy,
                    br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner.Result
                            result) {
                effects[0]++;
                return 1;
            }

            @Override
            public java.util.List<Gap> readGapPage(String namespace, int maximum, Instant after) {
                effects[0]++;
                return java.util.List.of();
            }
        };
    }

    @Test
    void recoveryBlackoutAtFirstDayCannotReturnSuccessfulNoWork() throws Exception {
        final var config = configuration();
        final var operation =
                new RuntimeTemporalOperation(
                        policy().put("lookbackSeconds", "0")
                                .put("blackouts", "2024-02-28/2024-02-29"));
        final var output = new java.util.ArrayList<String>();
        assertEquals(
                40,
                RuntimePeriodRecoveryOperation.run(
                        config,
                        operation,
                        directory.resolve("unused-blueprint.json"),
                        output::add));
        assertEquals(
                java.util.List.of(
                        "PERIOD_RECOVERY no_due_window=1 backlog=true blackout=true scheduler=0"),
                output);
    }

    @Test
    void recoveryOccurrenceRemainsIdenticalWhenBatchAndObservationChange() throws Exception {
        final var config = configuration();
        final var document = policy().put("lookbackSeconds", "0");
        final var file = directory.resolve("recovery-stable-blueprint.json");
        Files.writeString(file, blueprint().toString());
        final var before =
                RuntimePeriodRecoveryOperation.requests(
                        config, new RuntimeTemporalOperation(document), file);
        document.put("firstUnpublished", "2024-02-29")
                .put("observedAt", "2024-03-03T15:00:00Z")
                .put("invocation", UUID.randomUUID().toString());
        final var after =
                RuntimePeriodRecoveryOperation.requests(
                        config, new RuntimeTemporalOperation(document), file);
        final var date = Instant.parse("2024-02-29T03:00:00Z");
        assertEquals(before.get(date).plan.fingerprint(), after.get(date).plan.fingerprint());
        assertNotEquals(before.get(date).invocation, after.get(date).invocation);
        assertNotEquals(
                before.get(date).invocation,
                before.get(Instant.parse("2024-02-28T03:00:00Z")).invocation);
    }

    @Test
    void recoveryMonthlyRequestKeepsEveryBusinessDayIncludingLeapDay() throws Exception {
        final var config = configuration();
        final var operation =
                new RuntimeTemporalOperation(
                        policy().put("lookbackSeconds", "0")
                                .put("cadence", "CIVIL_MONTH")
                                .put("firstUnpublished", "2024-02-01"));
        final var file = directory.resolve("recovery-monthly-blueprint.json");
        Files.writeString(file, blueprint().toString());
        final var requests = RuntimePeriodRecoveryOperation.requests(config, operation, file);
        assertEquals(1, requests.size());
        final var request = requests.values().iterator().next();
        assertEquals(java.time.LocalDate.of(2024, 2, 1), request.dates.startInclusive());
        assertEquals(java.time.LocalDate.of(2024, 2, 29), request.dates.endInclusive());
        assertEquals(java.util.Optional.empty(), request.updatedAt);
    }

    @Test
    void incrementalSourceUsesLookbackAndInclusiveSecondsWithoutMovingPartition() throws Exception {
        final var config = configuration();
        final var incremental = policy().put("mode", "INCREMENTAL");
        final var document = RuntimeTemporalBinding.request(config, incremental, blueprint(), 1);
        final var request = RuntimeOperationalRequest.fromDocument(config, document);
        final var page = request.firstPage();
        assertEquals("2024-02-28T03:00:00Z", document.get("start").textValue());
        assertEquals(
                Instant.parse("2024-02-28T02:00:00Z"),
                page.updatedAtWindow().orElseThrow().startInclusive());
        assertEquals(
                Instant.parse("2024-02-29T02:59:59Z"),
                page.updatedAtWindow().orElseThrow().endInclusive());
        assertEquals(
                "2024-02-27 23:00:00 - 2024-02-28 23:59:59",
                page.updatedAtWindow().orElseThrow().formatForSource(incrementalZone()));
        assertEquals(page.filters(), page.withPage(2).filters());
        final var noOverlap =
                RuntimeOperationalRequest.fromDocument(
                        config,
                        RuntimeTemporalBinding.request(
                                config,
                                incremental.deepCopy().put("lookbackSeconds", "0"),
                                blueprint(),
                                1));
        assertNotEquals(request.plan.fingerprint(), noOverlap.plan.fingerprint());
        assertEquals(
                Instant.parse(document.get("start").textValue()),
                noOverlap.firstPage().updatedAtWindow().orElseThrow().startInclusive());
        assertEquals(
                java.util.Optional.empty(),
                RuntimeOperationalRequest.fromDocument(
                                config,
                                RuntimeTemporalBinding.request(config, policy(), blueprint(), 1))
                        .firstPage()
                        .updatedAtWindow());
        final var fractional = incremental.deepCopy().put("civilBoundary", "00:00:00.500");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeOperationalRequest.fromDocument(
                                config,
                                RuntimeTemporalBinding.request(
                                        config, fractional, blueprint(), 1)));
    }

    private static java.time.ZoneId incrementalZone() {
        return java.time.ZoneId.of("America/Sao_Paulo");
    }

    @Test
    void onlyFirstIncrementalWindowInitializesItsExactFrontier() throws Exception {
        final var config = configuration();
        final var calls = new java.util.ArrayList<Object[]>();
        final var control =
                (br.com.esl.etl.v2.plataforma.controle.ControlPlane)
                        java.lang.reflect.Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {
                                    br.com.esl.etl.v2.plataforma.controle.ControlPlane.class
                                },
                                (proxy, method, arguments) -> {
                                    assertEquals("registerIncrementalFrontier", method.getName());
                                    calls.add(arguments);
                                    return null;
                                });
        for (final String mode : java.util.List.of("BACKFILL", "INCREMENTAL")) {
            for (int ordinal = 1; ordinal <= 2; ordinal++) {
                final var request =
                        RuntimeOperationalRequest.fromDocument(
                                config,
                                RuntimeTemporalBinding.request(
                                        config, policy().put("mode", mode), blueprint(), ordinal));
                request.plan.forEach(
                        item ->
                                request.temporal.initializeIncrementalFrontier(
                                        control, item.partition(), config.clock().instant()));
            }
        }
        assertEquals(1, calls.size());
        assertEquals(Instant.parse("2024-02-28T03:00:00Z"), calls.get(0)[1]);
        final var incremental =
                RuntimeOperationalRequest.fromDocument(
                        config,
                        RuntimeTemporalBinding.request(
                                config, policy().put("mode", "INCREMENTAL"), blueprint(), 1));
        final var partition =
                (br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey) calls.get(0)[0];
        for (final var changed :
                java.util.List.of(
                        new br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey(
                                partition.environment(),
                                partition.sourceInstance(),
                                partition.tenantScope(),
                                "fretes",
                                partition.mode(),
                                partition.partitionStart(),
                                partition.partitionEndExclusive()),
                        new br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey(
                                partition.environment(),
                                partition.sourceInstance(),
                                partition.tenantScope(),
                                partition.entity(),
                                partition.mode(),
                                partition.partitionStart().plusSeconds(1),
                                partition.partitionEndExclusive()),
                        new br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey(
                                partition.environment(),
                                partition.sourceInstance(),
                                partition.tenantScope(),
                                partition.entity(),
                                partition.mode(),
                                partition.partitionStart(),
                                partition.partitionEndExclusive().plusSeconds(1)),
                        new br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey(
                                partition.environment(),
                                partition.sourceInstance(),
                                partition.tenantScope(),
                                partition.entity(),
                                br.com.esl.etl.v2.plataforma.controle.ExecutionMode.BACKFILL,
                                partition.partitionStart(),
                                partition.partitionEndExclusive()))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            incremental.temporal.initializeIncrementalFrontier(
                                    control, changed, config.clock().instant()));
        }
        assertEquals(1, calls.size());
    }

    @Test
    void generatedWindowMatchesPersistedOccurrenceAndNewInvocationPreservesPlan() throws Exception {
        final var config = configuration();
        final var policy = policy();
        final var operation = new RuntimeTemporalOperation(policy);
        final var document = RuntimeTemporalBinding.request(config, policy, blueprint(), 2);
        final var request = RuntimeOperationalRequest.fromDocument(config, document);
        final var persisted = operation.freeze(config, operation.result.windows().get(1));
        assertEquals(
                persisted.scope().executionId().toString(),
                document.get("executionId").textValue());
        assertEquals(persisted.cycle(), request.plan.cycleId());
        assertEquals("2024-02-29", document.get("businessStart").textValue());
        assertEquals("2024-02-29", document.get("businessEnd").textValue());
        document.put("invocationId", UUID.randomUUID().toString());
        final var reauthorized = RuntimeOperationalRequest.fromDocument(config, document);
        assertEquals(request.plan.fingerprint(), reauthorized.plan.fingerprint());
        assertNotEquals(request.invocation, reauthorized.invocation);
        request.temporal.requireAction(RuntimeAction.RUN);
        request.temporal.requireAction(RuntimeAction.STATUS);
        assertThrows(
                IllegalArgumentException.class,
                () -> request.temporal.requireAction(RuntimeAction.FORCE_RUN));
    }

    @Test
    void rejectsChangedWindowOccurrenceCycleModeAndPolicyBeforeAuthorityOrSource()
            throws Exception {
        final var config = configuration();
        final var request = RuntimeTemporalBinding.request(config, policy(), blueprint(), 1);
        for (final var change :
                Map.of(
                                "executionId",
                                UUID.randomUUID().toString(),
                                "cycleId",
                                UUID.randomUUID().toString(),
                                "start",
                                "2024-02-29T03:00:00Z",
                                "mode",
                                "INCREMENTAL",
                                "temporalWindow",
                                "4",
                                "businessStart",
                                "2024-02-29")
                        .entrySet()) {
            final var changed = request.deepCopy().put(change.getKey(), change.getValue());
            assertThrows(
                    IllegalArgumentException.class,
                    () -> RuntimeOperationalRequest.fromDocument(config, changed));
        }
        final var changedPolicy = policy().put("slaSeconds", "3601");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeOperationalRequest.fromDocument(
                                config,
                                request.deepCopy()
                                        .put("temporalPolicy", changedPolicy.toString())));
        final var missing = request.deepCopy();
        missing.remove("temporalWindow");
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.fromDocument(config, missing));
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeTemporalBinding.request(config, policy(), blueprint(), 0));
    }

    @Test
    void selectedFreightWindowKeepsItsExplicitMatchingPredecessor() throws Exception {
        final var config = configuration();
        final var coleta = RuntimeTemporalBinding.request(config, policy(), blueprint(), 2);
        final var fretePolicy = policy().put("workload", "fretes").put("dependsOn", "coletas");
        final var file = directory.resolve("frete-blueprint.json");
        Files.writeString(
                file,
                blueprint()
                        .put("template", "FRETES")
                        .put("dependencyRequest", coleta.toString())
                        .toString());
        final var operation = new RuntimeTemporalOperation(fretePolicy);
        final var exported =
                new ObjectMapper().readTree(operation.operationalRequests(config, file, 2));
        assertEquals(1, exported.size());
        final var request = RuntimeOperationalRequest.fromDocument(config, exported.get(0));
        assertNotEquals(request.dependency.plan.cycleId(), request.plan.cycleId());
        assertEquals(
                coleta.get("executionId").textValue(),
                new ObjectMapper()
                        .readTree(exported.get(0).get("dependencyRequest").textValue())
                        .get("executionId")
                        .textValue());
        assertEquals("2", exported.get(0).get("temporalWindow").textValue());
        assertThrows(
                IllegalArgumentException.class,
                () -> operation.operationalRequests(config, file, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> operation.operationalRequests(config, file, 4));
        final var environment =
                Map.of(
                        "V2_DATAEXPORT_ENABLED",
                        "true",
                        "V2_DATAEXPORT_BASE_URL",
                        "http://127.0.0.1:19540",
                        "V2_DATAEXPORT_SOURCE_INSTANCE",
                        "LOCAL_V2",
                        "V2_DATAEXPORT_TENANT_SCOPE",
                        "LOCAL_V2",
                        "V2_DATAEXPORT_TIMEZONE",
                        "America/Sao_Paulo");
        final var policyFile = directory.resolve("frete-policy.json");
        Files.writeString(policyFile, fretePolicy.toString());
        for (final String selected : java.util.List.of("2", "0", "5", "+1")) {
            final var bytes = new java.io.ByteArrayOutputStream();
            try (var output = new java.io.PrintStream(bytes)) {
                final int code =
                        Main.run(
                                new String[] {
                                    "plan",
                                    "--config",
                                    directory.resolve("configuration.properties").toString(),
                                    "--temporal",
                                    policyFile.toString(),
                                    "--request",
                                    file.toString(),
                                    "--window",
                                    selected
                                },
                                output,
                                output,
                                new Main.RuntimeDependencies(
                                        new Properties(),
                                        environment,
                                        config.clock(),
                                        new RuntimeConfigurationFactory()));
                assertEquals(selected.equals("2") ? 0 : 2, code);
                if (code == 0) {
                    assertEquals(1, new ObjectMapper().readTree(bytes.toByteArray()).size());
                }
            }
        }
    }

    @Test
    void exportsOnlyBoundedRequestsOfflineAndRejectsUnboundedOrUnknownBlueprint() throws Exception {
        final var config = configuration();
        final var file = directory.resolve("blueprint.json");
        Files.writeString(file, blueprint().toString());
        final var operation = new RuntimeTemporalOperation(policy());
        final var exported =
                new ObjectMapper().readTree(operation.operationalRequests(config, file));
        assertEquals(3, exported.size());
        for (final var request : exported) {
            assertNotNull(RuntimeOperationalRequest.fromDocument(config, request).temporal);
        }
        Files.writeString(file, blueprint().put("unknown", "extra").toString());
        assertThrows(
                IllegalArgumentException.class, () -> operation.operationalRequests(config, file));
        Files.writeString(file, " ".repeat(16385));
        assertThrows(
                IllegalArgumentException.class, () -> operation.operationalRequests(config, file));
    }

    @Test
    void reconcilesOnlyContiguousPublishedWindowsAndRefusesPersistenceWithoutAuthority()
            throws Exception {
        final var config = configuration();
        final var bound =
                RuntimeOperationalRequest.fromDocument(
                                config,
                                RuntimeTemporalBinding.request(config, policy(), blueprint(), 2))
                        .temporal;
        final var published = new java.util.HashSet<Integer>();
        final var store =
                new br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalStore() {
                    @Override
                    public int persist(
                            UUID plan,
                            String namespace,
                            br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy policy,
                            br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner.Result
                                    result) {
                        throw new AssertionError("reconciliation must only read summaries");
                    }

                    @Override
                    public java.util.List<Gap> readGapPage(
                            String namespace, int maximum, Instant after) {
                        assertEquals(
                                bound.operation.freeze(config, bound.window).namespace(),
                                namespace);
                        assertEquals(8, maximum);
                        assertEquals(Instant.parse("2024-02-28T03:00:00Z"), after);
                        final var rows = new java.util.ArrayList<Gap>();
                        for (int i = 0; i < 3; i++) {
                            final var window = bound.operation.result.windows().get(i);
                            rows.add(
                                    new Gap(
                                            bound.operation
                                                    .freeze(config, window)
                                                    .scope()
                                                    .executionId(),
                                            window.partitionStart(),
                                            window.endExclusive(),
                                            published.contains(i) ? "PUBLISHED" : "NOT_STARTED"));
                        }
                        return rows;
                    }
                };
        final var output = new java.util.ArrayList<String>();
        published.add(1);
        bound.reconcile(config, store, output::add);
        assertEquals(
                "TEMPORAL_EXECUTION_RECONCILIATION contiguous=2024-02-28T03:00:00Z pending=1 degraded=0 limit=false",
                output.get(0));
        published.add(0);
        bound.reconcile(config, store, output::add);
        assertEquals(
                "TEMPORAL_EXECUTION_RECONCILIATION contiguous=2024-03-01T03:00:00Z pending=1 degraded=0 limit=false",
                output.get(1));
        assertThrows(
                br.com.esl.etl.v2.plataforma.autorizacao.DurableAuthorizationException.class,
                () -> bound.persistBeforeFirstAttempt(config, UUID.randomUUID()));
    }

    @Test
    void officialPlanExportsRequestsWithoutSourceOrSqlAndRejectsMalformedBlueprint()
            throws Exception {
        final var config = configuration();
        final var file = directory.resolve("cli-blueprint.json");
        Files.writeString(file, blueprint().toString());
        // The configuration values used here are the same finite synthetic namespace as the binding
        // tests.
        final var values =
                Map.of(
                        "V2_DATAEXPORT_ENABLED",
                        "true",
                        "V2_DATAEXPORT_BASE_URL",
                        "http://127.0.0.1:19540",
                        "V2_DATAEXPORT_SOURCE_INSTANCE",
                        "LOCAL_V2",
                        "V2_DATAEXPORT_TENANT_SCOPE",
                        "LOCAL_V2",
                        "V2_DATAEXPORT_TIMEZONE",
                        "America/Sao_Paulo");
        final var output = new java.io.ByteArrayOutputStream();
        final var error = new java.io.ByteArrayOutputStream();
        try (var out = new java.io.PrintStream(output);
                var err = new java.io.PrintStream(error)) {
            final var args =
                    new String[] {
                        "plan",
                        "--config",
                        directory.resolve("configuration.properties").toString(),
                        "--temporal",
                        "config/laboratory/bloco54-temporal-coletas.json",
                        "--request",
                        file.toString()
                    };
            final var dependencies =
                    new Main.RuntimeDependencies(
                            new Properties(),
                            values,
                            config.clock(),
                            new RuntimeConfigurationFactory());
            assertEquals(0, Main.run(args, out, err, dependencies));
            assertEquals(3, new ObjectMapper().readTree(output.toString()).size());
            assertEquals("", error.toString());
            Files.writeString(file, "{}");
            assertEquals(2, Main.run(args, out, err, dependencies));
        }
    }

    @Test
    void freightRequiresExplicitSameWindowPredecessorAndKeepsNamespaceDistinct() throws Exception {
        final var config = configuration();
        final var coletas = RuntimeTemporalBinding.request(config, policy(), blueprint(), 1);
        final var freightPolicy = policy().put("workload", "fretes").put("dependsOn", "coletas");
        final var fretes = RuntimeTemporalBinding.request(config, freightPolicy, blueprint(), 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.fromDocument(config, fretes));
        fretes.put("dependencyRequest", coletas.toString());
        final var bound = RuntimeOperationalRequest.fromDocument(config, fretes);
        assertNotNull(bound.dependency.temporal);
        assertNotEquals(coletas.get("executionId"), fretes.get("executionId"));
        final var changed = coletas.deepCopy().put("businessEnd", "2024-02-29");
        fretes.put("dependencyRequest", changed.toString());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.fromDocument(config, fretes));
    }
}
