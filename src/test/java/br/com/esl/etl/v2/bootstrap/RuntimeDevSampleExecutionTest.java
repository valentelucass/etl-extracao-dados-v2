package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Real application/mapper under offline protocol fixtures; never an integrated SQL/source proof.
 */
class RuntimeDevSampleExecutionTest {
    static final Instant NOW = Instant.parse("2036-03-21T12:00:00Z");

    static RuntimeConfiguration configuration() {
        final var source =
                new DataExportSourceConfiguration(
                        "RootPartition",
                        "RootPartition",
                        new DataExportClientSettings(
                                java.net.URI.create("https://fixture.invalid"),
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(30),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                                10485760),
                        new EslResiliencePolicy(
                                Duration.ofSeconds(2),
                                1,
                                2,
                                2,
                                Duration.ofSeconds(30),
                                Duration.ofSeconds(60),
                                Duration.ofSeconds(60),
                                Duration.ZERO,
                                0,
                                1,
                                Duration.ofSeconds(1)));
        return new RuntimeConfiguration(
                RuntimeEnvironment.LOCAL_SHADOW,
                ZoneId.of("America/Sao_Paulo"),
                Clock.fixed(NOW, ZoneId.of("UTC")),
                Optional.of(source),
                Optional.empty(),
                ShadowStorageProperties.enabledC78Local(
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE)));
    }

    static RuntimeOperationalRequest request(
            final RuntimeConfiguration configuration, final int per, final int pages)
            throws Exception {
        return request(configuration, per, pages, 12);
    }

    static RuntimeOperationalRequest request(
            final RuntimeConfiguration configuration,
            final int per,
            final int pages,
            final int maximumRows)
            throws Exception {
        final var day = LocalDate.parse("2036-03-19");
        final var zone = ZoneId.of("America/Sao_Paulo");
        final var node =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("invocationId", UUID.randomUUID().toString())
                        .put("executionId", UUID.randomUUID().toString())
                        .put("cycleId", UUID.randomUUID().toString())
                        .put("template", "COLETAS")
                        .put("mode", "BACKFILL")
                        .put("start", day.atStartOfDay(zone).toInstant().toString())
                        .put(
                                "endExclusive",
                                day.plusDays(1).atStartOfDay(zone).toInstant().toString())
                        .put("replayOf", "")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("businessStart", day.toString())
                        .put("businessEnd", day.toString())
                        .put("leaseSeconds", "60")
                        .put("pageSize", Integer.toString(per))
                        .put("maximumPages", Integer.toString(pages))
                        .put("maximumRows", Integer.toString(maximumRows))
                        .put("maximumDistinctRoots", Integer.toString(per))
                        .put("qualityVersion", "synthetic-quality-1")
                        .put("qualityFingerprint", "b".repeat(64))
                        .put("compatibilityVersion", "strict-1");
        return RuntimeOperationalRequest.fromDocument(configuration, node);
    }

    static RuntimeOperationalExecution.SourceGateways oneEntity(
            final RuntimeBootstrapTestFixture fixture, final boolean cancel) {
        return oneEntity(fixture, cancel, 2);
    }

    static RuntimeOperationalExecution.SourceGateways oneEntity(
            final RuntimeBootstrapTestFixture fixture, final boolean cancel, final int count) {
        return (configuration, request, cancellation, observation) -> {
            final var original = fixture.gateways(observation);
            final var adapter =
                    new DataExportContractAdapter(
                            observation.observationLimits(), observation.responsePathBoundary());
            return RuntimeBootstrapTestFixture.boundGateways(
                    page -> {
                        final var row = original.dataGateway().fetch(page).records().get(0);
                        final var array = JsonNodeFactory.instance.arrayNode();
                        final var records =
                                new java.util.ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                        for (int i = 0; i < count; i++) {
                            array.add(row);
                            records.add(row);
                        }
                        return RuntimeBootstrapTestFixture.observedPage(
                                List.copyOf(records),
                                adapter.response(array, observation.expectedResponseForm(), "/id"),
                                observation.observationLimits(),
                                observation.responsePathBoundary());
                    },
                    template -> {
                        final var info = original.templateInfoGateway().fetchInfo(template);
                        if (cancel) {
                            cancellation.cancel();
                        }
                        return info;
                    },
                    observation);
        };
    }

    @Test
    void existingSampleRouteStagesRealMappedPageAndNeverReturnsCompletion() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var rows = new AtomicInteger();
        final var result =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        request(config, 1, 1),
                        fixture.dataSource(),
                        oneEntity(fixture, false),
                        batch -> rows.addAndGet(batch.size()),
                        () -> {});
        assertEquals(1, fixture.fetches);
        assertEquals(2, rows.get());
        assertTrue(result.partialPageBoundaryObserved());
        assertNotEquals(RuntimeExitCategory.SUCCESS, result.category());
        final var expected = result.expectation().orElseThrow();
        assertEquals(2, expected.physicalRows());
        assertEquals(2, expected.expectedRoots().get(0).physicalRows().orElseThrow());
        assertEquals(java.util.Map.of(1, 1), expected.batchPages());
        assertFalse(result.toString().contains(expected.executionId().toString()));
    }

    @Test
    void cancellationNeverBecomesStagedOrCompletedSample() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var rows = new AtomicInteger();
        final var result =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        request(config, 1, 1),
                        fixture.dataSource(),
                        oneEntity(fixture, true),
                        batch -> rows.addAndGet(batch.size()),
                        () -> {});
        assertEquals(0, rows.get());
        assertFalse(result.partialPageBoundaryObserved());
        assertTrue(result.expectation().isEmpty());
        assertNotEquals(RuntimeExitCategory.SUCCESS, result.category());
    }

    @Test
    void stagingFailureCannotBecomeACompleteOrPromotedSample() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var result =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        request(config, 1, 1),
                        fixture.dataSource(),
                        oneEntity(fixture, false),
                        batch -> {
                            throw new IllegalStateException("COMPONENT_STAGE_FAILED");
                        },
                        () -> {});
        assertFalse(result.partialPageBoundaryObserved());
        assertTrue(result.expectation().isEmpty());
        assertNotEquals(RuntimeExitCategory.SUCCESS, result.category());
    }

    @Test
    void twelveExpandedRowsRemainOneIdentityAndThirteenAreNeverPrefixStaged() throws Exception {
        final var config = configuration();
        final var rows = new AtomicInteger();
        final var twelve = new RuntimeBootstrapTestFixture(NOW);
        final var accepted =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        request(config, 1, 1),
                        twelve.dataSource(),
                        oneEntity(twelve, false, 12),
                        batch -> rows.addAndGet(batch.size()),
                        () -> {});
        assertEquals(12, rows.get());
        assertEquals(12, accepted.expectation().orElseThrow().physicalRows());
        assertEquals(1, accepted.expectation().orElseThrow().expectedRoots().size());
        final var thirteen = new RuntimeBootstrapTestFixture(NOW);
        rows.set(0);
        final var refused =
                RuntimeDevSampleExecution.execute(
                        true,
                        config,
                        request(config, 1, 1),
                        thirteen.dataSource(),
                        oneEntity(thirteen, false, 13),
                        batch -> rows.addAndGet(batch.size()),
                        () -> {});
        assertEquals(0, rows.get());
        assertTrue(refused.expectation().isEmpty());
        assertNotEquals(RuntimeExitCategory.SUCCESS, refused.category());
    }

    @Test
    void thousandAndThirteenRequestsRemainDescriptiveButAreNotSample12Execution() throws Exception {
        final var config = configuration();
        for (int maximum : List.of(13, 1000)) {
            final var original = request(config, 1, 1, maximum);
            RuntimeDevSampleExecution.validateScope(config, original);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            RuntimeDevSampleExecution.execute(
                                    true, config, original, null, null, null, null));
        }
    }

    @Test
    void expandedScopeAndMissingOptInRefuseBeforeSuppliersOrDataSource() throws Exception {
        final var config = configuration();
        assertThrows(
                IllegalStateException.class,
                () ->
                        RuntimeDevSampleExecution.execute(
                                false, config, request(config, 1, 1), null, null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeDevSampleExecution.execute(
                                true, config, request(config, 2, 1), null, null, null, null));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        RuntimeDevSampleExecution.execute(
                                true, config, request(config, 1, 2), null, null, null, null));
    }

    @Test
    void historicalSessionDefaultsToMissingOwnerTransactionCapability() throws Exception {
        try (var session =
                new RuntimeDevEngineBankBinding.Session() {
                    public javax.sql.DataSource dataSource() {
                        throw new AssertionError("NOT_A_SQL_PROOF");
                    }

                    public void checkpoint() {}

                    public void close() {}
                }) {
            final var failure =
                    assertThrows(
                            RuntimeDevEngineBankBinding.Refusal.class,
                            session::sampleStagingGateway);
            assertEquals(RuntimeDevEngineBankBinding.MissingGate.PHYSICAL_CONTRACT, failure.gate());
        }
    }
}
