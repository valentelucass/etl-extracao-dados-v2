package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.Binding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator.ExpectedRoot;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportUnavailableException;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
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
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Coletas6908SampleRunnerTest {
    private static final LocalDate DAY = LocalDate.parse("2036-03-19");
    private static final Instant NOW = Instant.parse("2036-03-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));
    @TempDir Path directory;

    @Test
    void physicalEntryRejectsUnverifiedTlsBeforeOptInOrJdbc() throws Exception {
        configuration();
        final var file = directory.resolve("runtime.properties");
        final String verified = Files.readString(file);
        for (final String unverified :
                List.of(
                        verified.replace("encrypt=true", "encrypt=false"),
                        verified.replace(
                                "trustServerCertificate=false", "trustServerCertificate=true"))) {
            assertFalse(unverified.equals(verified), "Each case must change the TLS configuration");
            Files.writeString(file, unverified);
            final var config =
                    new RuntimeConfigurationFactory().load(file, new Properties(), Map.of(), CLOCK);
            final var frozen = request(config, 5);
            final var rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    Coletas6908SampleRunner.runInLocalShadow(
                                            plan(5), config, frozen, Map.of()));
            assertEquals("VERIFIED_LOCAL_WORKLOAD_TLS_REQUIRED", rejected.getMessage());
        }
    }

    @Test
    void physicalEntryRejectsEveryMissingFlagTargetMismatchAndMissingSecretBeforeSql()
            throws Exception {
        final var config = configuration();
        final var frozen = request(config, 5);
        final Map<String, String> required =
                Map.of(
                        "shadow.local.integration.enabled", "true",
                        "shadow.local.integration.profile.active", "true",
                        "coletas6908.sample.enabled", "true",
                        "jdk.httpclient.redirects.retrylimit", "1",
                        "jdk.httpclient.disableRetryConnect", "true",
                        "jdk.httpclient.enableAllMethodRetry", "false");
        final var previous = new HashMap<String, String>();
        required.keySet().forEach(key -> previous.put(key, System.getProperty(key)));
        try {
            for (final String missing : required.keySet()) {
                required.forEach(System::setProperty);
                System.clearProperty(missing);
                final var rejected =
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        Coletas6908SampleRunner.runInLocalShadow(
                                                plan(5), config, frozen, Map.of()));
                assertEquals("COL_SAMPLE_OPT_IN_REQUIRED", rejected.getMessage());
            }
            required.forEach(System::setProperty);
            final var mismatch =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    Coletas6908SampleRunner.runInLocalShadow(
                                            plan(5), config, frozen, Map.of()));
            assertEquals("COL_SAMPLE_TARGET_BINDING_REQUIRED", mismatch.getMessage());
            for (final Map<String, String> environment :
                    List.of(
                            Map.of("V2_SHADOW_JDBC_URL", config.shadowStorage().jdbcUrl()),
                            Map.of(
                                    "V2_SHADOW_JDBC_URL",
                                    config.shadowStorage().jdbcUrl(),
                                    "V2_DATAEXPORT_TOKEN",
                                    " "))) {
                final var missingSecret =
                        assertThrows(
                                IllegalStateException.class,
                                () ->
                                        Coletas6908SampleRunner.runInLocalShadow(
                                                plan(5), config, frozen, environment));
                assertFalse(missingSecret.getMessage().contains("COL_SAMPLE_SQL_STOP"));
                assertTrue(missingSecret.getMessage().contains("provider protegido"));
            }
        } finally {
            previous.forEach(
                    (key, value) -> {
                        if (value == null) {
                            System.clearProperty(key);
                        } else {
                            System.setProperty(key, value);
                        }
                    });
        }
    }

    @Test
    void planOnlyCliReportsDisabledExecutionAndRejectsInvalidPlanWithoutCallingTrial()
            throws Exception {
        final var file = directory.resolve("plan-only.json");
        Files.writeString(file, planDocument().toString());
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        final var called = new AtomicInteger();
        final Coletas6908PilotMain.SampleExecutor executor =
                (plan, config, frozen, environment) -> {
                    called.incrementAndGet();
                    throw new AssertionError("PLAN_ONLY_MUST_NOT_OPEN_TRIAL");
                };
        assertEquals(
                0,
                Coletas6908PilotMain.run(
                        new String[] {"--plan", file.toString()},
                        new PrintStream(output),
                        new PrintStream(errors),
                        new Properties(),
                        Map.of(),
                        CLOCK,
                        executor));
        assertTrue(output.toString().contains("execution=DISABLED"));
        assertEquals(0, called.get());
        Files.writeString(file, planDocument().put("target", "PRODUCTION").toString());
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        new String[] {"--plan", file.toString()},
                        new PrintStream(output),
                        new PrintStream(errors),
                        new Properties(),
                        Map.of(),
                        CLOCK,
                        executor));
        assertTrue(errors.toString().contains("COL_PILOT_INVALID_PLAN"));
        assertEquals(0, called.get());
    }

    @Test
    void unstartedExtractionCannotExposeReadbackBindingOrExpectedRoots() throws Exception {
        final var extraction =
                new Coletas6908SampleExtraction(
                        plan(5),
                        batch -> {
                            throw new AssertionError("UNSTARTED_EXTRACTION_MUST_NOT_STAGE");
                        },
                        () -> {});
        assertFalse(extraction.boundaryObserved());
        assertThrows(IllegalStateException.class, extraction::expectedRoots);
        assertThrows(IllegalStateException.class, extraction::executionId);
        assertThrows(IllegalStateException.class, extraction::physicalRows);
        assertThrows(IllegalStateException.class, extraction::batchPages);
    }

    @Test
    void oneActualPopulatedPageStagesReadsBackAndClosesWithoutTerminalOrPromotion()
            throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var receipt =
                Coletas6908SampleRunner.run(
                        plan(5),
                        config,
                        request(config, 5),
                        (c, r, t, observation) -> fixture.gateways(observation),
                        trial,
                        new RuntimeHttpAttempts(8));
        assertEquals(1, fixture.fetches);
        assertEquals(5, trial.rows());
        assertTrue(trial.closed);
        assertTrue(trial.verified);
        assertEquals("SAMPLE_STAGED_READBACK_ROLLED_BACK", receipt.status());
        assertEquals("PARTIAL_SINGLE_PAGE", receipt.scope());
        assertEquals(1, receipt.pagesFetched());
        assertEquals(5, receipt.physicalRows());
        assertEquals(2, receipt.distinctRoots());
        assertFalse(receipt.promoted());
        assertFalse(receipt.windowCompleteness());
        assertFalse(receipt.childCompleteness());
        assertEquals(0, receipt.presenceComparedCells());
    }

    @Test
    void expandedEntityKeepsPhysicalMultiplicityAndBatchToPageMap() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var receipt =
                Coletas6908SampleRunner.run(
                        plan(5),
                        config,
                        request(config, 5),
                        changedRows(fixture, 101, false, false),
                        trial,
                        new RuntimeHttpAttempts(8));
        assertEquals(101, receipt.physicalRows());
        assertEquals(1, receipt.distinctRoots());
        assertEquals(Map.of(1, 1, 2, 1), trial.batchPages);
        assertTrue(trial.closed);
    }

    @Test
    void sixDistinctEntitiesNeverReachMapperStagingOrReadback() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                changedRows(fixture, 6, true, false),
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertEquals(0, trial.rows());
        assertFalse(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void invalidConsumedTypeCannotBecomePartialSampleReceipt() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                changedRows(fixture, 1, false, true),
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertEquals(0, trial.rows());
        assertFalse(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void rateLimitHasNoStageReadbackOrReceipt() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final RuntimeOperationalExecution.SourceGateways limited =
                (c, r, t, observation) -> {
                    final var original = fixture.gateways(observation);
                    return RuntimeBootstrapTestFixture.boundGateways(
                            page -> {
                                throw new DataExportUnavailableException(
                                        6908, 429, Optional.empty());
                            },
                            original.templateInfoGateway(),
                            observation);
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                limited,
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertEquals(0, trial.rows());
        assertFalse(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void mismatchedStagingReadbackCannotProduceReceipt() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        trial.mismatch = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                (c, r, t, observation) -> fixture.gateways(observation),
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertTrue(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void emptySourcePageCannotSealSampleTraversalOrReachReadback() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final RuntimeOperationalExecution.SourceGateways empty =
                (c, r, t, observation) -> {
                    final var original = fixture.gateways(observation);
                    return RuntimeBootstrapTestFixture.boundGateways(
                            page -> original.dataGateway().fetch(page.withPage(2)),
                            original.templateInfoGateway(),
                            observation);
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                empty,
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertEquals(1, fixture.fetches);
        assertEquals(0, trial.rows());
        assertFalse(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void uncertainRollbackCannotProducePositiveReceipt() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        trial.closeFailure = true;
        assertThrows(
                SQLException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                (c, r, t, observation) -> fixture.gateways(observation),
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertTrue(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void failedSqlTransitionAfterSampleBoundaryCannotBecomeReadbackReceipt() throws Exception {
        final var config = configuration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        trial.transitionFailure = true;
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.run(
                                plan(5),
                                config,
                                request(config, 5),
                                (c, r, t, observation) -> fixture.gateways(observation),
                                trial,
                                new RuntimeHttpAttempts(8)));
        assertEquals(5, trial.rows());
        assertFalse(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void rejectsUnboundedSampleScopeBeforeOpeningTrial() throws Exception {
        final var config = configuration();
        assertThrows(
                IllegalArgumentException.class,
                () -> Coletas6908SampleRunner.preflight(plan(6), config, request(config, 6)));
    }

    @Test
    void knownOccurrenceCannotEnterDurableRecoveryOrFetchAgain() throws Exception {
        final var config = configuration();
        final var frozen = request(config, 5);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final RuntimeOperationalExecution.SourceGateways source =
                (c, r, t, observation) -> fixture.gateways(observation);
        final var first = new FakeTrial(fixture.dataSource());
        Coletas6908SampleRunner.run(
                plan(5), config, frozen, source, first, new RuntimeHttpAttempts(8));
        final var next = new FakeTrial(fixture.dataSource());
        final var rejected =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Coletas6908SampleRunner.run(
                                        plan(5),
                                        config,
                                        frozen,
                                        source,
                                        next,
                                        new RuntimeHttpAttempts(8)));
        assertEquals("COL_SAMPLE_FRESH_OCCURRENCE_REQUIRED", rejected.getMessage());
        assertEquals(1, fixture.fetches);
        assertEquals(0, next.rows());
        assertFalse(next.verified);
        assertTrue(next.closed);
    }

    @Test
    void cliNeedsOptInAndFreshBankEvidenceBeforeCallingExecutor() throws Exception {
        final var config = configuration();
        final var doc = requestDocument(5);
        final var frozen = RuntimeOperationalRequest.fromDocument(config, doc);
        final var planFile = directory.resolve("plan.json");
        final var requestFile = directory.resolve("request.json");
        final var gateFile = directory.resolve("bank.json");
        Files.writeString(requestFile, doc.toString());
        Files.writeString(planFile, planDocument().toString());
        final var gate =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "coletas-6908-sample-preflight-v1")
                        .put("status", "READY_SINGLE_SAMPLE_TRIAL")
                        .put("target", "localhost/ETL_SISTEMA_V2_SHADOW")
                        .put("schemaEvidence", "MATCHES_VERSIONED_PROCEDURES")
                        .put("windowsAuthVerified", true)
                        .put("loopbackOnly", true)
                        .put("noConsumers", true)
                        .put("rollbackOnly", true)
                        .put("reservationReference", "SYNTHETIC_OFFLINE_ONLY")
                        .put("issuedAtUtc", NOW.minusSeconds(1).toString())
                        .put("validUntilUtc", NOW.plusSeconds(60).toString())
                        .put(
                                "configSha256",
                                Coletas6908SamplePreflight.sha256(
                                        directory.resolve("runtime.properties")))
                        .put("planSha256", Coletas6908SamplePreflight.sha256(planFile))
                        .put("requestSha256", Coletas6908SamplePreflight.sha256(requestFile))
                        .put("contractFingerprint", frozen.binding.contractFingerprint().sha256())
                        .put(
                                "configurationFingerprint",
                                frozen.binding.configurationFingerprint().sha256());
        Files.writeString(gateFile, gate.toString());
        final String[] args = {
            "--sample",
            "--config",
            directory.resolve("runtime.properties").toString(),
            "--plan",
            planFile.toString(),
            "--request",
            requestFile.toString(),
            "--bank-preflight",
            gateFile.toString()
        };
        final var invoked = new AtomicInteger();
        final Coletas6908PilotMain.SampleExecutor executor =
                (p, c, r, env) -> {
                    invoked.incrementAndGet();
                    return new Coletas6908SampleRunner.Receipt(
                            "SAMPLE_STAGED_READBACK_ROLLED_BACK",
                            "PARTIAL_SINGLE_PAGE",
                            1,
                            1,
                            1,
                            0,
                            false,
                            false,
                            false,
                            "RUNTIME_HTTP_ATTEMPTS metadata=1 data=1 total=2");
                };
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        final var properties = new Properties();
        assertEquals(
                3,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(0, invoked.get());
        properties.setProperty("shadow.local.integration.enabled", "true");
        properties.setProperty("shadow.local.integration.profile.active", "true");
        properties.setProperty("coletas6908.sample.enabled", "true");
        assertEquals(
                3,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(0, invoked.get());
        properties.setProperty("jdk.httpclient.redirects.retrylimit", "1");
        properties.setProperty("jdk.httpclient.disableRetryConnect", "true");
        properties.setProperty("jdk.httpclient.enableAllMethodRetry", "false");
        assertEquals(
                0,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(1, invoked.get());
        Files.writeString(requestFile, doc.toString());
        for (final JsonNode reservation :
                List.of(
                        JsonNodeFactory.instance.numberNode(7),
                        JsonNodeFactory.instance.booleanNode(true),
                        JsonNodeFactory.instance.booleanNode(false),
                        JsonNodeFactory.instance.nullNode(),
                        JsonNodeFactory.instance.arrayNode(),
                        JsonNodeFactory.instance.objectNode(),
                        JsonNodeFactory.instance.textNode(" \t"))) {
            final var invalidGate = gate.deepCopy();
            invalidGate.set("reservationReference", reservation);
            Files.writeString(gateFile, invalidGate.toString());
            errors.reset();
            assertEquals(
                    2,
                    Coletas6908PilotMain.run(
                            args,
                            new PrintStream(output),
                            new PrintStream(errors),
                            properties,
                            Map.of(),
                            CLOCK,
                            executor),
                    "A ledger reservation must be a nonblank JSON string: "
                            + reservation.getNodeType());
            assertEquals(1, invoked.get(), "Invalid evidence must not open the trial");
            assertEquals("COL_SAMPLE_STOP phase=PREFLIGHT", errors.toString().strip());
        }
        for (final String physicalGate :
                List.of("windowsAuthVerified", "loopbackOnly", "noConsumers", "rollbackOnly")) {
            final var refusedGate = gate.deepCopy().put(physicalGate, false);
            Files.writeString(gateFile, refusedGate.toString());
            errors.reset();
            assertEquals(
                    2,
                    Coletas6908PilotMain.run(
                            args,
                            new PrintStream(output),
                            new PrintStream(errors),
                            properties,
                            Map.of(),
                            CLOCK,
                            executor),
                    physicalGate);
            assertEquals(1, invoked.get(), "A refused physical gate must not open the trial");
            assertEquals("COL_SAMPLE_STOP phase=PREFLIGHT", errors.toString().strip());
        }
        for (final String malformed :
                List.of(
                        "",
                        " ".repeat(8193),
                        gate.toString() + " {}",
                        gate.toString().replaceFirst("\\{", "{\"version\":\"duplicate\","))) {
            Files.writeString(gateFile, malformed);
            assertEquals(
                    2,
                    Coletas6908PilotMain.run(
                            args,
                            new PrintStream(output),
                            new PrintStream(errors),
                            properties,
                            Map.of(),
                            CLOCK,
                            executor));
            assertEquals(1, invoked.get());
        }
        Files.writeString(gateFile, gate.toString());
        errors.reset();
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        (p, c, r, env) -> {
                            throw new SQLException("SYNTHETIC_PRIVATE_TRIAL_DETAIL");
                        }));
        assertEquals("COL_SAMPLE_STOP phase=TRIAL", errors.toString().strip());
        assertFalse(errors.toString().contains("SYNTHETIC_PRIVATE_TRIAL_DETAIL"));
        assertTrue(output.toString().contains("promoted=false windowCompleteness=false"));
        properties.setProperty("shadow.audit.enabled", "true");
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(1, invoked.get());
        properties.remove("shadow.audit.enabled");
        gate.put("validUntilUtc", NOW.minusSeconds(1).toString());
        Files.writeString(gateFile, gate.toString());
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(1, invoked.get());
        gate.put("validUntilUtc", NOW.plusSeconds(60).toString())
                .put("schemaEvidence", "UNRECONCILED");
        Files.writeString(gateFile, gate.toString());
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(1, invoked.get());
        gate.put("schemaEvidence", "MATCHES_VERSIONED_PROCEDURES");
        Files.writeString(gateFile, gate.toString());
        Files.writeString(requestFile, doc.toString() + " ");
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals(1, invoked.get());
    }

    private RuntimeConfiguration configuration() throws Exception {
        final String text =
                Files.readString(Path.of("config/application.example.properties"))
                        .replace("dataexport.enabled=false", "dataexport.enabled=true")
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
        final var file = directory.resolve("runtime.properties");
        Files.writeString(file, text);
        return new RuntimeConfigurationFactory().load(file, new Properties(), Map.of(), CLOCK);
    }

    private static Coletas6908PilotPlan plan(final int per) {
        return new Coletas6908PilotPlan(
                "synthetic-source",
                "synthetic-tenant",
                DAY,
                per,
                2,
                1000,
                10485760,
                Duration.ofSeconds(60));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode planDocument() {
        return JsonNodeFactory.instance
                .objectNode()
                .put("version", "coletas-6908-traversal-v1")
                .put("templateId", 6908)
                .put("target", "LOCAL_SHADOW")
                .put("mode", "BOUNDED_TRAVERSAL_PARITY")
                .put("sourceInstance", "synthetic-source")
                .put("tenantScope", "synthetic-tenant")
                .put("businessDate", DAY.toString())
                .put("page", 1)
                .put("per", 5)
                .put("maxPages", 2)
                .put("maxPhysicalRows", 1000)
                .put("maxResponseBytes", 10485760)
                .put("deadlineSeconds", 60);
    }

    private static RuntimeOperationalRequest request(
            final RuntimeConfiguration config, final int per) throws Exception {
        return RuntimeOperationalRequest.fromDocument(config, requestDocument(per));
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode requestDocument(final int per) {
        final var zone = ZoneId.of("America/Sao_Paulo");
        return JsonNodeFactory.instance
                .objectNode()
                .put("invocationId", UUID.randomUUID().toString())
                .put("executionId", UUID.randomUUID().toString())
                .put("cycleId", UUID.randomUUID().toString())
                .put("template", "COLETAS")
                .put("mode", "BACKFILL")
                .put("start", DAY.atStartOfDay(zone).toInstant().toString())
                .put("endExclusive", DAY.plusDays(1).atStartOfDay(zone).toInstant().toString())
                .put("replayOf", "")
                .put("idempotencyKey", UUID.randomUUID().toString())
                .put("businessStart", DAY.toString())
                .put("businessEnd", DAY.toString())
                .put("leaseSeconds", "60")
                .put("pageSize", Integer.toString(per))
                .put("maximumPages", "2")
                .put("maximumRows", "1000")
                .put("maximumDistinctRoots", Integer.toString(per))
                .put("qualityVersion", "synthetic-quality-1")
                .put("qualityFingerprint", "b".repeat(64))
                .put("compatibilityVersion", "strict-1");
    }

    private static RuntimeOperationalExecution.SourceGateways changedRows(
            final RuntimeBootstrapTestFixture fixture,
            final int count,
            final boolean distinct,
            final boolean quarantine) {
        return (c, r, t, observation) -> {
            final var original = fixture.gateways(observation);
            final var adapter =
                    new DataExportContractAdapter(
                            observation.observationLimits(), observation.responsePathBoundary());
            return RuntimeBootstrapTestFixture.boundGateways(
                    page -> {
                        final var originalRows = original.dataGateway().fetch(page).records();
                        final var array = JsonNodeFactory.instance.arrayNode();
                        final List<JsonNode> rows = new ArrayList<>();
                        for (int index = 0; index < count; index++) {
                            final com.fasterxml.jackson.databind.node.ObjectNode copy =
                                    originalRows.get(0).deepCopy();
                            if (distinct) {
                                copy.put("id", 9000 + index);
                            }
                            if (quarantine) {
                                copy.put("status", 123);
                            }
                            rows.add(copy);
                            array.add(copy);
                        }
                        return RuntimeBootstrapTestFixture.observedPage(
                                rows,
                                adapter.response(array, observation.expectedResponseForm(), "/id"),
                                observation.observationLimits(),
                                observation.responsePathBoundary());
                    },
                    original.templateInfoGateway(),
                    observation);
        };
    }

    private static final class FakeTrial implements Coletas6908SampleRunner.Trial {
        private final DataSource source;
        private final List<ColetaStageBatch> batches = new ArrayList<>();
        private Map<Integer, Integer> batchPages;
        private boolean closed;
        private boolean verified;
        private boolean mismatch;
        private boolean closeFailure;
        private boolean transitionFailure;

        private FakeTrial(final DataSource source) {
            this.source = source;
        }

        @Override
        public DataSource dataSource() {
            if (transitionFailure) {
                return (DataSource)
                        java.lang.reflect.Proxy.newProxyInstance(
                                DataSource.class.getClassLoader(),
                                new Class<?>[] {DataSource.class},
                                (proxy, method, args) -> {
                                    final Object value = invoke(source, method, args);
                                    if (!method.getName().equals("getConnection")) {
                                        return value;
                                    }
                                    return java.lang.reflect.Proxy.newProxyInstance(
                                            java.sql.Connection.class.getClassLoader(),
                                            new Class<?>[] {java.sql.Connection.class},
                                            (connection, operation, parameters) -> {
                                                if (operation.getName().equals("prepareCall")
                                                        && ((String) parameters[0])
                                                                .contains(
                                                                        "usp_control_plane_transition_execution")) {
                                                    throw new SQLException(
                                                            "SYNTHETIC_FAILED_BOUNDARY_TRANSITION");
                                                }
                                                return invoke(value, operation, parameters);
                                            });
                                });
            }
            return source;
        }

        private static Object invoke(
                final Object receiver,
                final java.lang.reflect.Method method,
                final Object[] arguments)
                throws Throwable {
            try {
                return method.invoke(receiver, arguments);
            } catch (final java.lang.reflect.InvocationTargetException failure) {
                throw failure.getCause();
            }
        }

        @Override
        public ColetaStagingGateway staging() {
            final var delegate = new JdbcSqlServerColetaStagingGateway(source);
            return new ColetaStagingGateway() {
                @Override
                public void stage(final ColetaStageBatch batch) {
                    stage(batch, CancellationToken.none());
                }

                @Override
                public void stage(
                        final ColetaStageBatch batch, final CancellationToken cancellation) {
                    delegate.stage(batch, cancellation);
                    batches.add(batch);
                }
            };
        }

        @Override
        public void checkpoint() {
            if (closed) {
                throw new IllegalStateException("SYNTHETIC_TRIAL_CLOSED");
            }
        }

        @Override
        public Coletas6908SampleRunner.Comparison verifyStaging(
                final Binding binding,
                final List<ExpectedRoot> expected,
                final Map<Integer, Integer> pages) {
            verified = true;
            assertEquals(1, binding.sourcePage());
            batchPages = pages;
            final Map<String, Integer> observed = new HashMap<>();
            for (final var batch : batches) {
                assertEquals(1, pages.get(batch.batchNumber()));
                for (int index = 0; index < batch.size(); index++) {
                    observed.merge(
                            batch.recordAt(index).sourceKey().storageValue(), 1, Integer::sum);
                }
            }
            final Map<String, Integer> required = new HashMap<>();
            expected.forEach(
                    root ->
                            required.put(
                                    root.identity().sourceKey().storageValue(),
                                    root.physicalRows().orElseThrow()));
            return new Coletas6908SampleRunner.Comparison(
                    !mismatch && observed.equals(required), rows(), 0);
        }

        private int rows() {
            return batches.stream().mapToInt(ColetaStageBatch::size).sum();
        }

        @Override
        public void close() throws SQLException {
            closed = true;
            if (closeFailure) {
                throw new SQLException("SYNTHETIC_ROLLBACK_UNKNOWN");
            }
        }
    }
}
