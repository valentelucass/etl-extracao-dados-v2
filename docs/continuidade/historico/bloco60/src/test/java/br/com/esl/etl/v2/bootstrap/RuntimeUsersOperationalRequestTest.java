package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.configuracao.GraphQlClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.GraphQlSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageTargetKind;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeUsersOperationalRequestTest {
    static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");

    @Test
    void dataExportAttemptObserverCannotClaimZeroGraphQlAttempts() throws Exception {
        final var request = RuntimeOperationalRequest.fromDocument(configuration(), document());
        assertEquals(
                "RUNTIME_HTTP_ATTEMPTS protocol=GRAPHQL total=UNOBSERVED",
                RuntimeOperationalExecution.sourceAttemptsSummary(
                        request, new RuntimeHttpAttempts(20)));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "protocol",
                "operation",
                "pageSize",
                "template",
                "query",
                "cursor",
                "enabled",
                "updatedAt",
                "incremental",
                "bootstrap",
                "sweep",
                "replay",
                "replay-self",
                "precision",
                "interval",
                "pages",
                "nodes",
                "budget",
                "number",
                "missing"
            })
    void rejectsIncompatibleClosedRequests(final String scenario) {
        final var node = document();
        switch (scenario) {
            case "protocol" -> node.put("protocol", "DATA_EXPORT");
            case "operation" -> node.put("operation", "FREIGHTS_TRANSITIONAL_SIDECAR");
            case "pageSize" -> node.put("pageSize", "21");
            case "incremental" -> node.put("mode", "INCREMENTAL");
            case "bootstrap" -> node.put("mode", "BOOTSTRAP");
            case "sweep" -> node.put("mode", "SWEEP");
            case "replay" -> node.put("mode", "REPLAY");
            case "replay-self" ->
                    node.put("mode", "REPLAY").put("replayOf", node.get("executionId").asText());
            case "precision" -> node.put("start", "2036-03-19T18:42:10.000000001Z");
            case "interval" -> node.put("endExclusive", node.get("start").asText());
            case "pages" -> node.put("maximumPages", "0");
            case "nodes" -> node.put("maximumNodes", "0");
            case "budget" -> node.put("maximumPages", "21");
            case "number" -> node.put("pageSize", 20);
            case "missing" -> node.remove("operation");
            default -> node.put(scenario, "forbidden");
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.fromDocument(configuration(), node));
    }

    @Test
    void freezesBindingsAndDoesNotIncludeNewAuthorizationInOccurrenceFingerprint()
            throws Exception {
        final var node = document();
        final var original = RuntimeOperationalRequest.fromDocument(configuration(), node);
        node.put("invocationId", UUID.randomUUID().toString());
        assertEquals(
                original.plan.fingerprint(),
                RuntimeOperationalRequest.fromDocument(configuration(), node).plan.fingerprint());
        node.put("maximumPages", "3");
        assertNotEquals(
                original.plan.fingerprint(),
                RuntimeOperationalRequest.fromDocument(configuration(), node).plan.fingerprint());
        final var cfg = configuration();
        final var src = cfg.graphQl().orElseThrow();
        final var changed =
                new RuntimeConfiguration(
                        cfg.environment(),
                        cfg.businessZone(),
                        cfg.clock(),
                        cfg.dataExport(),
                        Optional.of(
                                new GraphQlSourceConfiguration(
                                        "different-source",
                                        src.tenantScope(),
                                        src.settings(),
                                        src.resiliencePolicy())),
                        cfg.shadowStorage());
        assertNotEquals(
                original.binding.configurationFingerprint(),
                RuntimeOperationalRequest.fromDocument(changed, node)
                        .binding
                        .configurationFingerprint());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "DEFAULT", "GLOBAL", "SINGLETON"})
    void requiresExplicitGraphQlConfigurationAndNamespace(final String scenario) {
        final var cfg = configuration();
        final var source = cfg.graphQl().orElseThrow();
        final var graph =
                scenario.equals("missing")
                        ? Optional.<GraphQlSourceConfiguration>empty()
                        : Optional.of(
                                new GraphQlSourceConfiguration(
                                        source.sourceInstance(),
                                        scenario,
                                        source.settings(),
                                        source.resiliencePolicy()));
        final var invalid =
                new RuntimeConfiguration(
                        cfg.environment(),
                        cfg.businessZone(),
                        cfg.clock(),
                        cfg.dataExport(),
                        graph,
                        cfg.shadowStorage());
        assertThrows(
                IllegalArgumentException.class,
                () -> RuntimeOperationalRequest.fromDocument(invalid, document()));
    }

    @Test
    void sharedProtocolsMustHaveSameNamespaceAndGovernorPolicy() throws Exception {
        final var cfg = configuration();
        final var graph = cfg.graphQl().orElseThrow();
        final var settings =
                new br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings(
                        URI.create("https://source.example.test/api/analytics/reports"),
                        cfg.businessZone(),
                        Duration.ofSeconds(10),
                        br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport
                                .GET_WITH_QUERY,
                        new br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy(
                                1, Duration.ZERO, Duration.ZERO),
                        65536);
        for (final String scenario : java.util.List.of("valid", "source", "tenant", "policy")) {
            final var policy =
                    scenario.equals("policy")
                            ? new EslResiliencePolicy(
                                    Duration.ZERO,
                                    1,
                                    19,
                                    19,
                                    Duration.ofSeconds(10),
                                    Duration.ofSeconds(30),
                                    Duration.ofSeconds(60),
                                    Duration.ofSeconds(1),
                                    0,
                                    3,
                                    Duration.ofSeconds(10))
                            : graph.resiliencePolicy();
            final var de =
                    new br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration(
                            scenario.equals("source") ? "other" : graph.sourceInstance(),
                            scenario.equals("tenant") ? "other" : graph.tenantScope(),
                            settings,
                            policy);
            final var both =
                    new RuntimeConfiguration(
                            cfg.environment(),
                            cfg.businessZone(),
                            cfg.clock(),
                            Optional.of(de),
                            cfg.graphQl(),
                            cfg.shadowStorage());
            if (scenario.equals("valid")) {
                assertNotNull(RuntimeOperationalRequest.fromDocument(both, document()).users);
            } else {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> RuntimeOperationalRequest.fromDocument(both, document()),
                        scenario);
            }
        }
    }

    @Test
    void acceptsUsersWithoutDataExportConfiguration() throws Exception {
        final var request = RuntimeOperationalRequest.fromDocument(configuration(), document());
        assertNotNull(request);
        request.plan.forEach(
                item -> {
                    assertEquals("GRAPHQL", item.definition().sourceKind());
                    assertEquals("usuarios", item.partition().entity());
                });
    }

    static RuntimeConfiguration configuration() {
        final var policy =
                new EslResiliencePolicy(
                        Duration.ZERO,
                        1,
                        20,
                        20,
                        Duration.ofSeconds(10),
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(60),
                        Duration.ofSeconds(1),
                        0,
                        3,
                        Duration.ofSeconds(10));
        return new RuntimeConfiguration(
                RuntimeEnvironment.LOCAL_SHADOW,
                ZoneId.of("America/Sao_Paulo"),
                Clock.fixed(NOW, ZoneOffset.UTC),
                Optional.empty(),
                Optional.of(
                        new GraphQlSourceConfiguration(
                                "synthetic-source",
                                "synthetic-tenant",
                                new GraphQlClientSettings(
                                        URI.create("https://source.example.test/graphql"),
                                        Duration.ofSeconds(10),
                                        new GraphQlRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                                        65536),
                                policy)),
                ShadowStorageProperties.enabled(
                        ShadowStorageTargetKind.LOCAL_EPHEMERAL,
                        "jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                                + "integratedSecurity=true;encrypt=true;trustServerCertificate=false",
                        null));
    }

    static ObjectNode document() {
        return JsonNodeFactory.instance
                .objectNode()
                .put("protocol", "GRAPHQL")
                .put("operation", "USERS_SNAPSHOT")
                .put("invocationId", UUID.randomUUID().toString())
                .put("executionId", UUID.randomUUID().toString())
                .put("cycleId", UUID.randomUUID().toString())
                .put("mode", "BACKFILL")
                .put("start", NOW.toString())
                .put("endExclusive", NOW.plusSeconds(3600).toString())
                .put("replayOf", "")
                .put("idempotencyKey", UUID.randomUUID().toString())
                .put("leaseSeconds", "60")
                .put("pageSize", "20")
                .put("maximumPages", "4")
                .put("maximumNodes", "80")
                .put("qualityVersion", "synthetic-quality-1")
                .put("qualityFingerprint", "b".repeat(64))
                .put("compatibilityVersion", "strict-1");
    }
}
