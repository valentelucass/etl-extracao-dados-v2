package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportUnavailableException;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Coletas6908PilotRunnerTest {
    private static final LocalDate DAY = LocalDate.parse("2036-03-19");
    private static final Instant NOW = Instant.parse("2036-03-21T12:00:00Z");
    @TempDir Path directory;

    @Test
    void traversesTerminalBeforeRealPermitsAndReturnsOnlyAfterTrialClose() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var request = request(config);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var result =
                Coletas6908PilotRunner.run(
                        plan(),
                        config,
                        request,
                        (c, r, t, observation) -> fixture.gateways(observation),
                        trial);
        assertEquals(2, fixture.fetches);
        assertTrue(trial.closed);
        assertEquals(1, trial.prepareCount);
        assertEquals(1, trial.publishCount);
        assertTrue(trial.stagedRows > 0);
        assertEquals("SIMULATED_PROMOTION_ROLLED_BACK", result.status());
        assertEquals("PARIDADE_DA_TRAVESSIA_LIMITADA_OBSERVADA", result.parity());
        assertEquals(trial.stagedRows, result.physicalRows());
        assertEquals(trial.stagedRows, result.observedPhysicalRows());
        assertEquals(0, result.overlapRoots());
        assertEquals(0, result.presenceComparedCells());
        assertFalse(result.windowCompleteness());
        assertFalse(result.childCompleteness());
    }

    @Test
    void keepsBatchToSourcePageAcrossExpandedAndSecondPopulatedPages() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var request = request(config, 2, 3, 110);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final RuntimeOperationalExecution.SourceGateways expanded =
                (c, r, t, observation) -> {
                    final var original = fixture.gateways(observation);
                    final var adapter =
                            new DataExportContractAdapter(
                                    observation.observationLimits(),
                                    observation.responsePathBoundary());
                    final var raw = readSyntheticPage();
                    return RuntimeBootstrapTestFixture.boundGateways(
                            page -> {
                                final var rows = JsonNodeFactory.instance.arrayNode();
                                if (page.page() == 1) {
                                    for (int index = 0; index < 101; index++) {
                                        rows.add(raw.get(0).deepCopy());
                                    }
                                } else if (page.page() == 2) {
                                    rows.add(raw.get(3).deepCopy());
                                }
                                final var list =
                                        new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                                rows.forEach(list::add);
                                return RuntimeBootstrapTestFixture.observedPage(
                                        list,
                                        adapter.response(
                                                rows, observation.expectedResponseForm(), "/id"),
                                        observation.observationLimits(),
                                        observation.responsePathBoundary());
                            },
                            original.templateInfoGateway(),
                            observation);
                };
        final var result =
                Coletas6908PilotRunner.run(plan(2, 3, 110), config, request, expanded, trial);
        assertEquals(3, result.pagesFetched());
        assertEquals(102, result.physicalRows());
        assertEquals(Map.of(1, 1, 2, 1, 3, 2), trial.batchPages);
        assertEquals(2, result.expectedRoots());
        assertEquals(0, result.presenceComparedCells());
    }

    @Test
    void stopsOnRateLimitBeforeTerminalWithoutPromotion() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final RuntimeOperationalExecution.SourceGateways rateLimited =
                (c, r, t, observation) -> {
                    final var original = fixture.gateways(observation);
                    return RuntimeBootstrapTestFixture.boundGateways(
                            page -> {
                                if (page.page() == 2) {
                                    throw new DataExportUnavailableException(
                                            6908, 429, Optional.empty());
                                }
                                return original.dataGateway().fetch(page);
                            },
                            original.templateInfoGateway(),
                            observation);
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908PilotRunner.run(
                                plan(), config, request(config), rateLimited, trial));
        assertTrue(trial.closed);
        assertEquals(0, trial.prepareCount);
        assertEquals(0, trial.publishCount);
    }

    @Test
    void globalDeadlineStopsBeforeSourceAndClosesTrial() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var shortPlan =
                new Coletas6908PilotPlan(
                        "synthetic-source",
                        "synthetic-tenant",
                        DAY,
                        3,
                        2,
                        100,
                        10 * 1024 * 1024,
                        Duration.ofNanos(1));
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908PilotRunner.run(
                                shortPlan,
                                config,
                                request(config),
                                (c, r, t, observation) -> fixture.gateways(observation),
                                trial));
        assertTrue(trial.closed);
        assertEquals(0, fixture.fetches);
    }

    @Test
    void cancellationDuringTraversalStopsBeforePromotion() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final RuntimeOperationalExecution.SourceGateways cancelled =
                (c, r, t, observation) -> {
                    final var original = fixture.gateways(observation);
                    return RuntimeBootstrapTestFixture.boundGateways(
                            page -> {
                                if (page.page() == 2) {
                                    throw new ResilienceCancelledException();
                                }
                                return original.dataGateway().fetch(page);
                            },
                            original.templateInfoGateway(),
                            observation);
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908PilotRunner.run(
                                plan(), config, request(config), cancelled, trial));
        assertTrue(trial.closed);
        assertEquals(0, trial.prepareCount);
        assertEquals(0, trial.publishCount);
    }

    @Test
    void failedTrialCloseCannotProduceRollbackReceipt() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource(), true);
        assertThrows(
                SQLException.class,
                () ->
                        Coletas6908PilotRunner.run(
                                plan(),
                                config,
                                request(config),
                                (c, r, t, observation) -> fixture.gateways(observation),
                                trial));
        assertEquals(1, trial.publishCount);
        assertTrue(trial.closed);
    }

    @Test
    void refusesPageCapWithoutTerminalAndNeverPrepares() throws Exception {
        final RuntimeConfiguration config = configuration();
        final var request = request(config);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final RuntimeOperationalExecution.SourceGateways repeated =
                (c, r, t, observation) -> {
                    final var original = fixture.gateways(observation);
                    return RuntimeBootstrapTestFixture.boundGateways(
                            page -> original.dataGateway().fetch(page.withPage(1)),
                            original.templateInfoGateway(),
                            observation);
                };
        assertThrows(
                IllegalStateException.class,
                () -> Coletas6908PilotRunner.run(plan(), config, request, repeated, trial));
        assertTrue(trial.closed);
        assertEquals(0, trial.prepareCount);
        assertEquals(0, trial.publishCount);
    }

    @Test
    void refusesLegacySinglePagePlanAndExecuteFlag() throws Exception {
        final var invalid =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "coletas-6908-page-v1")
                        .put("templateId", 6908)
                        .put("target", "LOCAL_SHADOW")
                        .put("mode", "PAGE_PARITY")
                        .put("sourceInstance", "synthetic-source")
                        .put("tenantScope", "synthetic-tenant")
                        .put("businessDate", DAY.toString())
                        .put("page", 1)
                        .put("per", 3)
                        .put("maxPages", 2)
                        .put("maxPhysicalRows", 100)
                        .put("maxResponseBytes", 10 * 1024 * 1024)
                        .put("deadlineSeconds", 60);
        final Path path = directory.resolve("plan.json");
        Files.writeString(path, invalid.toString());
        assertThrows(IllegalArgumentException.class, () -> Coletas6908PilotPlan.read(path));
        assertEquals(
                3,
                Coletas6908PilotMain.run(
                        new String[] {"--execute", path.toString()}, System.out, System.err));
    }

    private Coletas6908PilotPlan plan() {
        return plan(3, 2, 100);
    }

    private Coletas6908PilotPlan plan(final int per, final int pages, final int rows) {
        return new Coletas6908PilotPlan(
                "synthetic-source",
                "synthetic-tenant",
                DAY,
                per,
                pages,
                rows,
                10 * 1024 * 1024,
                Duration.ofSeconds(60));
    }

    private static com.fasterxml.jackson.databind.JsonNode readSyntheticPage() {
        try (var input =
                Coletas6908PilotRunnerTest.class.getResourceAsStream(
                        "/contracts/bloco62/6908-page.synthetic.json")) {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(input);
        } catch (final java.io.IOException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private RuntimeConfiguration configuration() throws Exception {
        String text = Files.readString(Path.of("config/application.example.properties"));
        text =
                text.replace("dataexport.enabled=false", "dataexport.enabled=true")
                        .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true")
                        .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                        .replaceAll("(?m)^# (shadow\\.[^\\r\\n]+)$", "$1")
                        .replace("<host-autorizado>", "source.example.test")
                        .replace("<identificador-estavel-nao-secreto>", "synthetic-source")
                        .replace("<escopo-nao-secreto>", "synthetic-tenant")
                        .replace("127.0.0.1:<porta-local>", "localhost")
                        .replace("trustServerCertificate=true", "trustServerCertificate=false")
                        .replace(
                                "dataexport.retry.max-attempts=3",
                                "dataexport.retry.max-attempts=1");
        final Path path = directory.resolve("runtime.properties");
        Files.writeString(path, text);
        return new RuntimeConfigurationFactory()
                .load(path, new Properties(), Map.of(), Clock.fixed(NOW, ZoneId.of("UTC")));
    }

    private RuntimeOperationalRequest request(final RuntimeConfiguration config) throws Exception {
        return request(config, 3, 2, 100);
    }

    private RuntimeOperationalRequest request(
            final RuntimeConfiguration config, final int per, final int pages, final int rows)
            throws Exception {
        final var start = DAY.atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        final var end = DAY.plusDays(1).atStartOfDay(ZoneId.of("America/Sao_Paulo")).toInstant();
        final var doc =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("invocationId", UUID.randomUUID().toString())
                        .put("executionId", UUID.randomUUID().toString())
                        .put("cycleId", UUID.randomUUID().toString())
                        .put("template", "COLETAS")
                        .put("mode", "BACKFILL")
                        .put("start", start.toString())
                        .put("endExclusive", end.toString())
                        .put("replayOf", "")
                        .put("idempotencyKey", UUID.randomUUID().toString())
                        .put("businessStart", DAY.toString())
                        .put("businessEnd", DAY.toString())
                        .put("leaseSeconds", "60")
                        .put("pageSize", Integer.toString(per))
                        .put("maximumPages", Integer.toString(pages))
                        .put("maximumRows", Integer.toString(rows))
                        .put("maximumDistinctRoots", Integer.toString(per))
                        .put("qualityVersion", "synthetic-quality-1")
                        .put("qualityFingerprint", "b".repeat(64))
                        .put("compatibilityVersion", "strict-1");
        return RuntimeOperationalRequest.fromDocument(config, doc);
    }

    private static final class FakeTrial implements Coletas6908PilotRunner.Trial {
        private final DataSource dataSource;
        private final ColetaStagingGateway staging;
        private final ColetaPromotionGateway promotion;
        private final List<ColetaStageBatch> batches = new ArrayList<>();
        private boolean closed;
        private int stagedRows;
        private int prepareCount;
        private int publishCount;
        private final Map<Integer, Integer> batchPages = new HashMap<>();
        private final boolean closeFailure;

        private FakeTrial(final DataSource dataSource) {
            this(dataSource, false);
        }

        private FakeTrial(final DataSource dataSource, final boolean closeFailure) {
            this.dataSource = dataSource;
            this.closeFailure = closeFailure;
            final var stageDelegate = new JdbcSqlServerColetaStagingGateway(dataSource);
            staging =
                    new ColetaStagingGateway() {
                        @Override
                        public void stage(final ColetaStageBatch batch) {
                            stage(batch, CancellationToken.none());
                        }

                        @Override
                        public void stage(
                                final ColetaStageBatch batch, final CancellationToken token) {
                            stageDelegate.stage(batch, token);
                            batches.add(batch);
                            stagedRows += batch.size();
                        }
                    };
            final var promoteDelegate = new JdbcSqlServerColetaPromotionGateway(dataSource);
            promotion =
                    new ColetaPromotionGateway() {
                        @Override
                        public void prepareCandidateSet(
                                final br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit
                                        permit) {
                            prepareCandidateSet(permit, CancellationToken.none());
                        }

                        @Override
                        public void prepareCandidateSet(
                                final br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit
                                        permit,
                                final CancellationToken token) {
                            promoteDelegate.prepareCandidateSet(permit, token);
                            prepareCount++;
                        }

                        @Override
                        public br.com.esl.etl.v2.plataforma.persistencia.staging
                                        .StagingPublicationResult
                                applyReconcileAndPublish(
                                        final br.com.esl.etl.v2.plataforma.contrato
                                                        .ContractPromotionPermit
                                                permit,
                                        final br.com.esl.etl.v2.plataforma.qualidade
                                                        .DataQualityPromotionPermit
                                                quality) {
                            return applyReconcileAndPublish(
                                    permit, quality, CancellationToken.none());
                        }

                        @Override
                        public br.com.esl.etl.v2.plataforma.persistencia.staging
                                        .StagingPublicationResult
                                applyReconcileAndPublish(
                                        final br.com.esl.etl.v2.plataforma.contrato
                                                        .ContractPromotionPermit
                                                permit,
                                        final br.com.esl.etl.v2.plataforma.qualidade
                                                        .DataQualityPromotionPermit
                                                quality,
                                        final CancellationToken token) {
                            final var result =
                                    promoteDelegate.applyReconcileAndPublish(
                                            permit, quality, token);
                            publishCount++;
                            return result;
                        }
                    };
        }

        @Override
        public DataSource dataSource() {
            return dataSource;
        }

        @Override
        public ColetaStagingGateway staging() {
            return staging;
        }

        @Override
        public ColetaPromotionGateway promotion() {
            return promotion;
        }

        @Override
        public void checkpoint() {
            if (closed) {
                throw new IllegalStateException("TRIAL_CLOSED");
            }
        }

        @Override
        public void close() throws SQLException {
            closed = true;
            if (closeFailure) {
                throw new SQLException("SYNTHETIC_CLOSE_UNCERTAIN");
            }
        }

        @Override
        public Coletas6908PilotRunner.Comparison verify(
                final Binding binding,
                final List<ExpectedRoot> expected,
                final Map<Integer, Integer> batchPages) {
            final Map<String, Integer> observed = new HashMap<>();
            for (final var batch : batches) {
                assertTrue(batchPages.containsKey(batch.batchNumber()));
                this.batchPages.put(batch.batchNumber(), batchPages.get(batch.batchNumber()));
                for (int index = 0; index < batch.size(); index++) {
                    final var row = batch.recordAt(index);
                    observed.merge(row.sourceKey().storageValue(), 1, Integer::sum);
                }
            }
            final Map<String, Integer> required = new HashMap<>();
            for (final var root : expected) {
                assertEquals(1, root.sourcePages().size());
                required.put(
                        root.identity().sourceKey().storageValue(),
                        root.physicalRows().orElseThrow());
            }
            return new Coletas6908PilotRunner.Comparison(
                    required.equals(observed) && binding.sourcePage() == 0, stagedRows, 0);
        }
    }
}
