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
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
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
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Coletas6908SampleRunnerTest {
    private static final LocalDate DAY = LocalDate.parse("2036-03-19");
    private static final Instant NOW = Instant.parse("2036-03-21T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneId.of("UTC"));
    @TempDir Path directory;

    @Test
    void sharedSaReceiptAdmitsOnlyExplicitShadowIsolationWithoutGlobalClaims() throws Exception {
        final var cli = sharedSaCli();
        Coletas6908SamplePreflight.validate(
                cli.gateFile(),
                Path.of(cli.args()[2]),
                Path.of(cli.args()[4]),
                Path.of(cli.args()[6]),
                cli.config(),
                RuntimeOperationalRequest.read(cli.config(), Path.of(cli.args()[6])));
        assertFalse(cli.gate().has("noConsumers"));
        assertFalse(cli.gate().has("loopbackOnly"));
    }

    @Test
    void sharedCliRunsActualOperationalMapperAndStagingWithOneEntityAndOwnClose() throws Exception {
        final var cli = sharedSaCli();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var opened = new AtomicInteger();
        final var keys = new ArrayList<SecretKey>();
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                0,
                Coletas6908PilotMain.run(
                        cli.args(),
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of("V2_SHADOW_JDBC_URL", cli.config().shadowStorage().jdbcUrl()),
                        CLOCK,
                        (p, c, r, e) -> {
                            throw new AssertionError("LEGACY_TRIAL_MUST_NOT_RUN");
                        },
                        (p, c, r, e, admission) ->
                                Coletas6908SampleRunner.runInSharedShadow(
                                        p,
                                        c,
                                        r,
                                        e,
                                        properties,
                                        key -> {
                                            keys.add(key);
                                            return "synthetic-offline-only";
                                        },
                                        changedRows(fixture, 3, false, false),
                                        (target, provider, work) -> {
                                            opened.incrementAndGet();
                                            provider.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
                                            return work.execute(trial);
                                        },
                                        admission)));
        assertEquals(1, opened.get());
        assertEquals(
                List.of(SecretKey.DATA_EXPORT_TOKEN, SecretKey.SHADOW_SAMPLE_SA_PASSWORD), keys);
        assertEquals(1, fixture.fetches);
        assertEquals(3, trial.rows());
        assertTrue(trial.verified);
        assertTrue(trial.closed);
        assertTrue(output.toString().contains("distinctRoots=1"));
        assertTrue(output.toString().contains("promoted=false windowCompleteness=false"));
        assertEquals("", errors.toString());
    }

    @Test
    void sharedDeadlineDuringTrialCloseRefusesReceiptAfterActualStagingReadback() throws Exception {
        saConfiguration();
        final var clock = new MutableClock();
        final var config =
                new RuntimeConfigurationFactory()
                        .load(
                                directory.resolve("runtime.properties"),
                                new Properties(),
                                Map.of(),
                                clock);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Coletas6908SampleRunner.runInSharedShadow(
                                        plan(1),
                                        config,
                                        request(config, 1),
                                        Map.of(
                                                "V2_SHADOW_JDBC_URL",
                                                config.shadowStorage().jdbcUrl()),
                                        properties,
                                        key -> "synthetic-offline-only",
                                        changedRows(fixture, 1, false, false),
                                        (target, provider, work) ->
                                                work.execute(
                                                        closeThen(
                                                                trial,
                                                                () ->
                                                                        clock.advance(
                                                                                Duration.ofSeconds(
                                                                                        60)))),
                                        new Coletas6908SamplePreflight.SharedAdmission(
                                                "SYNTHETIC_OFFLINE_ONLY", NOW.plusSeconds(60))));
        assertEquals("COL_SAMPLE_SHARED_DEADLINE_OR_CANCELLED", failure.getMessage());
        assertEquals(0, failure.getSuppressed().length);
        assertEquals(1, fixture.fetches);
        assertEquals(1, trial.rows());
        assertTrue(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void sharedDeadlineDuringBankExecutorReturnRefusesAlreadyClosedTrialReceipt() throws Exception {
        saConfiguration();
        final var clock = new MutableClock();
        final var config =
                new RuntimeConfigurationFactory()
                        .load(
                                directory.resolve("runtime.properties"),
                                new Properties(),
                                Map.of(),
                                clock);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var failure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Coletas6908SampleRunner.runInSharedShadow(
                                        plan(1),
                                        config,
                                        request(config, 1),
                                        Map.of(
                                                "V2_SHADOW_JDBC_URL",
                                                config.shadowStorage().jdbcUrl()),
                                        properties,
                                        key -> "synthetic-offline-only",
                                        changedRows(fixture, 1, false, false),
                                        (target, provider, work) -> {
                                            final var receipt = work.execute(trial);
                                            clock.advance(Duration.ofSeconds(60));
                                            return receipt;
                                        },
                                        new Coletas6908SamplePreflight.SharedAdmission(
                                                "SYNTHETIC_OFFLINE_ONLY", NOW.plusSeconds(60))));
        assertEquals("COL_SAMPLE_SHARED_DEADLINE_OR_CANCELLED", failure.getMessage());
        assertTrue(trial.verified);
        assertTrue(trial.closed);
        assertEquals(1, fixture.fetches);
    }

    @Test
    void sharedCliDeadlineAtExecutorReturnEmitsNoSuccessAfterActualStagingReadback()
            throws Exception {
        final var cli = sharedSaCli();
        final var clock = new MutableClock();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        cli.args(),
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of("V2_SHADOW_JDBC_URL", cli.config().shadowStorage().jdbcUrl()),
                        clock,
                        (p, c, r, e) -> {
                            throw new AssertionError("LEGACY_TRIAL_MUST_NOT_RUN");
                        },
                        (p, c, r, e, admission) -> {
                            final var receipt =
                                    Coletas6908SampleRunner.runInSharedShadow(
                                            p,
                                            c,
                                            r,
                                            e,
                                            properties,
                                            key -> "synthetic-offline-only",
                                            changedRows(fixture, 1, false, false),
                                            (target, provider, work) -> work.execute(trial),
                                            admission);
                            clock.advance(Duration.ofSeconds(60));
                            return receipt;
                        }));
        assertEquals("", output.toString());
        assertTrue(errors.toString().contains("COL_SAMPLE_STOP phase=TRIAL"));
        assertEquals(1, fixture.fetches);
        assertTrue(trial.verified);
        assertTrue(trial.closed);
    }

    @Test
    void legacySampleKeepsItsOwnDeadlineWhenSharedClockAdvancesDuringClose() throws Exception {
        configuration();
        final var clock = new MutableClock();
        final var config =
                new RuntimeConfigurationFactory()
                        .load(
                                directory.resolve("runtime.properties"),
                                new Properties(),
                                Map.of(),
                                clock);
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var receipt =
                Coletas6908SampleRunner.run(
                        plan(1),
                        config,
                        request(config, 1),
                        changedRows(fixture, 1, false, false),
                        closeThen(trial, () -> clock.advance(Duration.ofSeconds(60))),
                        new RuntimeHttpAttempts(2));
        assertEquals(NOW.plusSeconds(60), clock.instant());
        assertEquals("SAMPLE_STAGED_READBACK_ROLLED_BACK", receipt.status());
        assertFalse(receipt.promoted());
        assertTrue(trial.closed);
    }

    @Test
    void sharedCancellationDuringCloseRefusesReceiptAndClosesOnlyOwnTrial() throws Exception {
        final var config = saConfiguration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        try {
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    Coletas6908SampleRunner.runInSharedShadow(
                                            plan(1),
                                            config,
                                            request(config, 1),
                                            Map.of(
                                                    "V2_SHADOW_JDBC_URL",
                                                    config.shadowStorage().jdbcUrl()),
                                            properties,
                                            key -> "synthetic-offline-only",
                                            changedRows(fixture, 1, false, false),
                                            (target, provider, work) ->
                                                    work.execute(
                                                            closeThen(
                                                                    trial,
                                                                    () ->
                                                                            Thread.currentThread()
                                                                                    .interrupt())),
                                            new Coletas6908SamplePreflight.SharedAdmission(
                                                    "SYNTHETIC_OFFLINE_ONLY",
                                                    NOW.plusSeconds(60))));
            assertEquals("COL_SAMPLE_SHARED_DEADLINE_OR_CANCELLED", failure.getMessage());
            assertTrue(trial.closed);
            assertTrue(trial.verified);
            assertEquals(1, fixture.fetches);
        } finally {
            Thread.interrupted();
        }
    }

    private static Coletas6908SampleRunner.Trial closeThen(
            final FakeTrial trial, final Runnable afterClose) {
        return new Coletas6908SampleRunner.Trial() {
            @Override
            public DataSource dataSource() {
                return trial.dataSource();
            }

            @Override
            public ColetaStagingGateway staging() {
                return trial.staging();
            }

            @Override
            public void checkpoint() {
                trial.checkpoint();
            }

            @Override
            public Coletas6908SampleRunner.Comparison verifyStaging(
                    final Binding binding,
                    final List<ExpectedRoot> expected,
                    final Map<Integer, Integer> pages) {
                return trial.verifyStaging(binding, expected, pages);
            }

            @Override
            public void close() throws SQLException {
                trial.close();
                afterClose.run();
            }
        };
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;
        private final ZoneId zone;

        private MutableClock() {
            this(new AtomicReference<>(NOW), ZoneId.of("UTC"));
        }

        private MutableClock(final AtomicReference<Instant> now, final ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        private void advance(final Duration duration) {
            now.updateAndGet(value -> value.plus(duration));
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(final ZoneId zone) {
            return new MutableClock(now, zone);
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    @Test
    void sharedCliRejectsWrongScopeWriterTlsBindingsAndGlobalClaimsBeforeExecutors()
            throws Exception {
        final var cli = sharedSaCli();
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var executions = new AtomicInteger();
        final var invalid = new ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        for (final String key :
                List.of(
                        "ownConnectionLoopbackOnly",
                        "noConflictingShadowWriters",
                        "v1OutsideProtectedFootprint",
                        "v1WindowVerified",
                        "tlsVerified",
                        "rollbackOnly")) {
            invalid.add(cli.gate().deepCopy().put(key, false));
            invalid.add(cli.gate().deepCopy().put(key, "true"));
        }
        invalid.add(cli.gate().deepCopy().put("target", "localhost/ETL_SISTEMA"));
        invalid.add(cli.gate().deepCopy().put("target", "remote/ETL_SISTEMA_V2_SHADOW"));
        invalid.add(cli.gate().deepCopy().put("isolationScope", "ETL_SISTEMA"));
        invalid.add(cli.gate().deepCopy().put("writerIsolationMode", "APPLOCK_ONLY"));
        invalid.add(cli.gate().deepCopy().put("mode", "EXCLUSIVE_INSTANCE"));
        invalid.add(cli.gate().deepCopy().put("isolationReference", 17));
        invalid.add(cli.gate().deepCopy().put("isolationReference", " "));
        invalid.add(cli.gate().deepCopy().put("noConsumers", true));
        invalid.add(cli.gate().deepCopy().put("loopbackOnly", false));
        invalid.add(cli.gate().deepCopy().put("windowsAuthVerified", false));
        invalid.add(cli.gate().deepCopy().put("executionDeadlineUtc", NOW.toString()));
        invalid.add(
                cli.gate().deepCopy().put("executionDeadlineUtc", NOW.plusSeconds(61).toString()));
        invalid.add(cli.gate().deepCopy().put("executionDeadlineUtc", 17));
        final var missingDeadline = cli.gate().deepCopy();
        missingDeadline.remove("executionDeadlineUtc");
        invalid.add(missingDeadline);
        invalid.add(cli.gate().deepCopy().put("validUntilUtc", NOW.plusSeconds(30).toString()));
        invalid.add(cli.gate().deepCopy().put("configSha256", "0".repeat(64)));
        final var errors = new ByteArrayOutputStream();
        for (final var gate : invalid) {
            Files.writeString(cli.gateFile(), gate.toString());
            assertEquals(
                    2,
                    Coletas6908PilotMain.run(
                            cli.args(),
                            new PrintStream(new ByteArrayOutputStream()),
                            new PrintStream(errors),
                            properties,
                            Map.of(),
                            CLOCK,
                            (p, c, r, e) -> {
                                executions.incrementAndGet();
                                throw new AssertionError();
                            },
                            (p, c, r, e, a) -> {
                                executions.incrementAndGet();
                                throw new AssertionError();
                            }));
        }
        assertEquals(0, executions.get());
        assertTrue(errors.toString().contains("phase=PREFLIGHT"));
    }

    @Test
    void sharedCliRejectsInsecureActualConfigurationEvenWithClaimedVerifiedReceipt()
            throws Exception {
        final var cli = sharedSaCli();
        final var file = Path.of(cli.args()[2]);
        final var original = Files.readString(file);
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var executed = new AtomicInteger();
        for (final var unsafe :
                List.of(
                        original.replace("encrypt=true", "encrypt=false"),
                        original.replace(
                                "trustServerCertificate=false", "trustServerCertificate=true"),
                        original.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA"),
                        original.replace(
                                "jdbc:sqlserver://localhost", "jdbc:sqlserver://remote"))) {
            Files.writeString(file, unsafe);
            Files.writeString(
                    cli.gateFile(),
                    cli.gate()
                            .deepCopy()
                            .put("configSha256", Coletas6908SamplePreflight.sha256(file))
                            .toString());
            assertEquals(
                    2,
                    Coletas6908PilotMain.run(
                            cli.args(),
                            new PrintStream(new ByteArrayOutputStream()),
                            new PrintStream(new ByteArrayOutputStream()),
                            properties,
                            Map.of(),
                            CLOCK,
                            (p, c, r, e) -> {
                                executed.incrementAndGet();
                                throw new AssertionError();
                            },
                            (p, c, r, e, a) -> {
                                executed.incrementAndGet();
                                throw new AssertionError();
                            }));
        }
        assertEquals(0, executed.get());
    }

    @Test
    void sharedCliNeverFallsBackToLegacyReceiptOrMissingSharedOptIn() throws Exception {
        final var legacy = saCli();
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        final var executions = new AtomicInteger();
        final Coletas6908PilotMain.SampleExecutor forbidden =
                (p, c, r, e) -> {
                    executions.incrementAndGet();
                    throw new AssertionError();
                };
        assertCliRefused(legacy.args(), properties, forbidden);
        final var shared = sharedSaCli();
        properties.remove("coletas6908.sample.shared.enabled");
        assertCliRefused(shared.args(), properties, forbidden);
        assertEquals(0, executions.get());
    }

    @Test
    void sharedPhysicalEntryRequiresPerOneSaFlagAndLiveWindowBeforeSecretsOrTrial()
            throws Exception {
        final var config = saConfiguration();
        final var properties = sampleProperties();
        final var resolved = new AtomicInteger();
        final var trials = new AtomicInteger();
        final SecretProvider provider =
                key -> {
                    resolved.incrementAndGet();
                    return "synthetic-offline-only";
                };
        final Coletas6908SampleRunner.TrialExecution forbidden =
                (target, secrets, work) -> {
                    trials.incrementAndGet();
                    throw new AssertionError();
                };
        final var admission =
                new Coletas6908SamplePreflight.SharedAdmission(
                        "SYNTHETIC_OFFLINE_ONLY", NOW.plusSeconds(60));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Coletas6908SampleRunner.runInSharedShadow(
                                plan(1),
                                config,
                                request(config, 1),
                                Map.of(),
                                properties,
                                provider,
                                null,
                                forbidden,
                                admission));
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Coletas6908SampleRunner.runInSharedShadow(
                                plan(2),
                                config,
                                request(config, 2),
                                Map.of(),
                                properties,
                                provider,
                                null,
                                forbidden,
                                admission));
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908SampleRunner.runInSharedShadow(
                                plan(1),
                                config,
                                request(config, 1),
                                Map.of(),
                                properties,
                                provider,
                                null,
                                forbidden,
                                new Coletas6908SamplePreflight.SharedAdmission(
                                        "SYNTHETIC_OFFLINE_ONLY", NOW)));
        assertEquals(0, resolved.get());
        assertEquals(0, trials.get());
    }

    @Test
    void sharedCancellationDuringSourceClosesOwnTrialWithoutReadbackOrPromotion() throws Exception {
        final var config = saConfiguration();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var properties = sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        try {
            assertThrows(
                    IllegalStateException.class,
                    () ->
                            Coletas6908SampleRunner.runInSharedShadow(
                                    plan(1),
                                    config,
                                    request(config, 1),
                                    Map.of("V2_SHADOW_JDBC_URL", config.shadowStorage().jdbcUrl()),
                                    properties,
                                    key -> "synthetic-offline-only",
                                    (c, r, t, o) -> {
                                        Thread.currentThread().interrupt();
                                        return changedRows(fixture, 1, false, false)
                                                .create(c, r, t, o);
                                    },
                                    (target, provider, work) -> work.execute(trial),
                                    new Coletas6908SamplePreflight.SharedAdmission(
                                            "SYNTHETIC_OFFLINE_ONLY", NOW.plusSeconds(60))));
            assertTrue(trial.closed);
            assertFalse(trial.verified);
            assertEquals(0, trial.rows());
        } finally {
            Thread.interrupted();
        }
    }

    SaCli sharedSaCli() throws Exception {
        final var cli = saCli();
        final var document = requestDocument(1);
        final var frozen = RuntimeOperationalRequest.fromDocument(cli.config(), document);
        Files.writeString(Path.of(cli.args()[4]), planDocument().put("per", 1).toString());
        Files.writeString(Path.of(cli.args()[6]), document.toString());
        final var gate = cli.gate().deepCopy();
        gate.remove(List.of("noConsumers", "loopbackOnly"));
        gate.put("version", "coletas-6908-sample-shared-preflight-sa-v1")
                .put("mode", "SHARED_INSTANCE_SHADOW_SAMPLE_ONLY")
                .put("isolationScope", "ETL_SISTEMA_V2_SHADOW")
                .put("writerIsolationMode", "TRANSACTION_OBJECT_X_LOCKS")
                .put("ownConnectionLoopbackOnly", true)
                .put("noConflictingShadowWriters", true)
                .put("v1OutsideProtectedFootprint", true)
                .put("v1WindowVerified", true)
                .put("isolationReference", "SYNTHETIC_OFFLINE_ONLY")
                .put("executionDeadlineUtc", NOW.plusSeconds(60).toString())
                .put("planSha256", Coletas6908SamplePreflight.sha256(Path.of(cli.args()[4])))
                .put("requestSha256", Coletas6908SamplePreflight.sha256(Path.of(cli.args()[6])))
                .put("contractFingerprint", frozen.binding.contractFingerprint().sha256())
                .put(
                        "configurationFingerprint",
                        frozen.binding.configurationFingerprint().sha256());
        Files.writeString(cli.gateFile(), gate.toString());
        return new SaCli(cli.config(), cli.gateFile(), gate, cli.args());
    }

    @Test
    void defaultSaEntriesReachBankSecretRefusalBeforeAnyDriverConnection() throws Exception {
        final var config = saConfiguration();
        final var connected = new AtomicInteger();
        final java.sql.Driver driver =
                new java.sql.Driver() {
                    @Override
                    public java.sql.Connection connect(
                            final String url, final Properties properties) throws SQLException {
                        connected.incrementAndGet();
                        throw new SQLException("SYNTHETIC_DRIVER_MUST_NOT_BE_REACHED");
                    }

                    @Override
                    public boolean acceptsURL(final String url) {
                        return true;
                    }

                    @Override
                    public java.sql.DriverPropertyInfo[] getPropertyInfo(
                            final String url, final Properties properties) {
                        return new java.sql.DriverPropertyInfo[0];
                    }

                    @Override
                    public int getMajorVersion() {
                        return 0;
                    }

                    @Override
                    public int getMinorVersion() {
                        return 0;
                    }

                    @Override
                    public boolean jdbcCompliant() {
                        return false;
                    }

                    @Override
                    public java.util.logging.Logger getParentLogger() {
                        return java.util.logging.Logger.getGlobal();
                    }
                };
        final var properties = sampleProperties();
        final var previous = new HashMap<String, String>();
        properties.stringPropertyNames().forEach(key -> previous.put(key, System.getProperty(key)));
        previous.put(
                "coletas6908.sample.shared.enabled",
                System.getProperty("coletas6908.sample.shared.enabled"));
        java.sql.DriverManager.registerDriver(driver);
        try {
            properties
                    .stringPropertyNames()
                    .forEach(key -> System.setProperty(key, properties.getProperty(key)));
            for (final boolean shared : List.of(false, true)) {
                System.setProperty("coletas6908.sample.shared.enabled", Boolean.toString(shared));
                final int per = shared ? 1 : 5;
                final var frozen = request(config, per);
                final var reads = new ArrayList<Object>();
                final Map<String, String> environment =
                        new java.util.AbstractMap<>() {
                            @Override
                            public String get(final Object key) {
                                reads.add(key);
                                if ("V2_SHADOW_JDBC_URL".equals(key)) {
                                    return config.shadowStorage().jdbcUrl();
                                }
                                return "V2_DATAEXPORT_TOKEN".equals(key)
                                        ? "synthetic-offline-only"
                                        : null;
                            }

                            @Override
                            public java.util.Set<Entry<String, String>> entrySet() {
                                throw new AssertionError("NO_ENVIRONMENT_ENUMERATION");
                            }
                        };
                final var refused =
                        assertThrows(
                                IllegalStateException.class,
                                () -> {
                                    if (shared) {
                                        Coletas6908SampleRunner.runInSharedShadow(
                                                plan(per),
                                                config,
                                                frozen,
                                                environment,
                                                new Coletas6908SamplePreflight.SharedAdmission(
                                                        "SYNTHETIC_OFFLINE_ONLY",
                                                        NOW.plusSeconds(60)));
                                    } else {
                                        Coletas6908SampleRunner.runInLocalShadow(
                                                plan(per), config, frozen, environment);
                                    }
                                });
                assertEquals("COL_SAMPLE_SA_SECRET_UNAVAILABLE", refused.getMessage());
                assertEquals(0, connected.get());
                assertEquals(null, refused.getCause());
                assertEquals(0, refused.getSuppressed().length);
                assertEquals(
                        List.of(
                                "V2_SHADOW_JDBC_URL",
                                "V2_DATAEXPORT_TOKEN",
                                "V2_GRAPHQL_TOKEN",
                                "V2_SHADOW_SAMPLE_SA_PASSWORD"),
                        reads);
            }
        } finally {
            java.sql.DriverManager.deregisterDriver(driver);
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
    void defaultSharedEntryDoesNotReadEnvironmentBeforeFlagsBindingAndDeadline() throws Exception {
        final var config = saConfiguration();
        final var frozen = request(config, 1);
        final var admission =
                new Coletas6908SamplePreflight.SharedAdmission(
                        "SYNTHETIC_OFFLINE_ONLY", NOW.plusSeconds(60));
        final var reads = new ArrayList<Object>();
        final Map<String, String> environment =
                new java.util.AbstractMap<>() {
                    @Override
                    public String get(final Object key) {
                        reads.add(key);
                        return null;
                    }

                    @Override
                    public java.util.Set<Entry<String, String>> entrySet() {
                        throw new AssertionError("NO_ENVIRONMENT_ENUMERATION");
                    }
                };
        final var required = sampleProperties();
        required.setProperty("coletas6908.sample.shared.enabled", "true");
        final var previous = new HashMap<String, String>();
        required.stringPropertyNames().forEach(key -> previous.put(key, System.getProperty(key)));
        try {
            for (final String missing : required.stringPropertyNames()) {
                required.stringPropertyNames()
                        .forEach(key -> System.setProperty(key, required.getProperty(key)));
                System.clearProperty(missing);
                reads.clear();
                assertThrows(
                        RuntimeException.class,
                        () ->
                                Coletas6908SampleRunner.runInSharedShadow(
                                        plan(1), config, frozen, environment, admission));
                assertTrue(
                        reads.isEmpty(),
                        "Rejected shared flags must precede provider construction");
            }
            required.stringPropertyNames()
                    .forEach(key -> System.setProperty(key, required.getProperty(key)));
            final var expired =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    Coletas6908SampleRunner.runInSharedShadow(
                                            plan(1),
                                            config,
                                            frozen,
                                            environment,
                                            new Coletas6908SamplePreflight.SharedAdmission(
                                                    "SYNTHETIC_OFFLINE_ONLY", NOW)));
            assertEquals("COL_SAMPLE_SHARED_DEADLINE_OR_CANCELLED", expired.getMessage());
            assertTrue(reads.isEmpty());
            final var wrongBinding =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    Coletas6908SampleRunner.runInSharedShadow(
                                            plan(1), config, frozen, environment, admission));
            assertEquals("COL_SAMPLE_TARGET_BINDING_REQUIRED", wrongBinding.getMessage());
            assertEquals(List.of("V2_SHADOW_JDBC_URL"), reads);
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
    void defaultEntryDoesNotReadEnvironmentSecretsBeforeFlagsAndTargetBinding() throws Exception {
        final var config = saConfiguration();
        final var frozen = request(config, 5);
        final var reads = new ArrayList<Object>();
        final Map<String, String> environment =
                new java.util.AbstractMap<>() {
                    @Override
                    public String get(final Object key) {
                        reads.add(key);
                        return null;
                    }

                    @Override
                    public java.util.Set<Entry<String, String>> entrySet() {
                        throw new AssertionError("NO_ENVIRONMENT_ENUMERATION");
                    }
                };
        final var required = sampleProperties();
        final var previous = new HashMap<String, String>();
        required.stringPropertyNames().forEach(key -> previous.put(key, System.getProperty(key)));
        try {
            for (final String missing : required.stringPropertyNames()) {
                required.stringPropertyNames()
                        .forEach(key -> System.setProperty(key, required.getProperty(key)));
                System.clearProperty(missing);
                reads.clear();
                assertThrows(
                        RuntimeException.class,
                        () ->
                                Coletas6908SampleRunner.runInLocalShadow(
                                        plan(5), config, frozen, environment));
                assertTrue(
                        reads.isEmpty(),
                        "Missing execution opt-in must not read any environment entry");
            }
            required.stringPropertyNames()
                    .forEach(key -> System.setProperty(key, required.getProperty(key)));
            reads.clear();
            final var rejected =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    Coletas6908SampleRunner.runInLocalShadow(
                                            plan(5), config, frozen, environment));
            assertEquals("COL_SAMPLE_TARGET_BINDING_REQUIRED", rejected.getMessage());
            assertEquals(List.of("V2_SHADOW_JDBC_URL"), reads);
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
    void saCliRunsActualSampleCompositionWithFakeBoundariesAndRollback() throws Exception {
        final var cli = saCli();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new FakeTrial(fixture.dataSource());
        final var opened = new AtomicInteger();
        final var secretKeys = new ArrayList<SecretKey>();
        final var properties = sampleProperties();
        final var environment =
                Map.of("V2_SHADOW_JDBC_URL", cli.config().shadowStorage().jdbcUrl());
        final SecretProvider secrets =
                key -> {
                    secretKeys.add(key);
                    return "synthetic-offline-only";
                };
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                0,
                Coletas6908PilotMain.run(
                        cli.args(),
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        environment,
                        CLOCK,
                        (plan, config, frozen, env) ->
                                Coletas6908SampleRunner.runInLocalShadow(
                                        plan,
                                        config,
                                        frozen,
                                        env,
                                        properties,
                                        secrets,
                                        (c, r, t, observation) -> fixture.gateways(observation),
                                        (target, provider, work) -> {
                                            assertTrue(target.usesSampleSaAuthentication());
                                            assertFalse(target.usesIntegratedSecurity());
                                            provider.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
                                            opened.incrementAndGet();
                                            return work.execute(trial);
                                        })));
        assertEquals(1, opened.get());
        assertEquals(
                List.of(SecretKey.DATA_EXPORT_TOKEN, SecretKey.SHADOW_SAMPLE_SA_PASSWORD),
                secretKeys);
        assertEquals(1, fixture.fetches);
        assertTrue(trial.verified);
        assertTrue(trial.closed);
        assertEquals(5, trial.rows());
        assertTrue(output.toString().contains("SAMPLE_STAGED_READBACK_ROLLED_BACK"));
        assertTrue(output.toString().contains("promoted=false windowCompleteness=false"));
        assertTrue(output.toString().contains("total=0")); // Fake HTTP is not physical traffic.
        assertEquals("", errors.toString());
        assertFalse(output.toString().contains("synthetic-offline-only"));
    }

    @Test
    void saCliRequiresMatchingIdentityTlsReceiptAndAllPhysicalGatesBeforeTrial() throws Exception {
        final var cli = saCli();
        final var properties = sampleProperties();
        final var opened = new AtomicInteger();
        final Coletas6908PilotMain.SampleExecutor forbidden =
                (p, c, r, e) -> {
                    opened.incrementAndGet();
                    throw new AssertionError("INVALID_PREFLIGHT_MUST_NOT_OPEN_TRIAL");
                };
        final var invalid = new ArrayList<com.fasterxml.jackson.databind.node.ObjectNode>();
        invalid.add(
                cli.gate()
                        .deepCopy()
                        .put("version", "coletas-6908-sample-preflight-v1")
                        .put("windowsAuthVerified", true));
        invalid.add(cli.gate().deepCopy().put("windowsAuthVerified", true));
        invalid.add(cli.gate().deepCopy().put("sqlPrincipal", "other"));
        for (final String key :
                List.of(
                        "sqlAuthVerified",
                        "tlsVerified",
                        "loopbackOnly",
                        "noConsumers",
                        "rollbackOnly")) {
            invalid.add(cli.gate().deepCopy().put(key, false));
            invalid.add(cli.gate().deepCopy().put(key, "true"));
        }
        invalid.add(cli.gate().deepCopy().put("schemaEvidence", "UNPROVED"));
        invalid.add(cli.gate().deepCopy().put("target", "localhost/ETL_SISTEMA"));
        invalid.add(cli.gate().deepCopy().put("configSha256", "0".repeat(64)));
        invalid.add(cli.gate().deepCopy().put("validUntilUtc", NOW.minusSeconds(1).toString()));
        for (final var gate : invalid) {
            Files.writeString(cli.gateFile(), gate.toString());
            assertCliRefused(cli.args(), properties, forbidden);
        }
        Files.writeString(cli.gateFile(), cli.gate().toString());
        for (final String value : List.of("false", "TRUE", "invalid")) {
            properties.setProperty("coletas6908.sample.sa.enabled", value);
            assertCliRefused(cli.args(), properties, forbidden);
        }
        properties.remove("coletas6908.sample.sa.enabled");
        assertCliRefused(cli.args(), properties, forbidden);
        assertEquals(0, opened.get());
    }

    @Test
    void saPhysicalEntryRejectsFlagsBindingAndMissingSourceBeforeTrialOrSaSecret()
            throws Exception {
        final var config = saConfiguration();
        final var frozen = request(config, 5);
        final var opened = new AtomicInteger();
        final var secretsRead = new ArrayList<SecretKey>();
        final SecretProvider secrets =
                key -> {
                    secretsRead.add(key);
                    throw new IllegalStateException("SYNTHETIC_MISSING_SOURCE_SECRET");
                };
        final Coletas6908SampleRunner.TrialExecution forbidden =
                (t, s, w) -> {
                    opened.incrementAndGet();
                    throw new AssertionError("REFUSAL_MUST_PRECEDE_JDBC");
                };
        final var properties = sampleProperties();
        for (final String missing : properties.stringPropertyNames()) {
            final var incomplete = new Properties();
            incomplete.putAll(properties);
            incomplete.remove(missing);
            assertThrows(
                    RuntimeException.class,
                    () ->
                            Coletas6908SampleRunner.runInLocalShadow(
                                    plan(5),
                                    config,
                                    frozen,
                                    Map.of(),
                                    incomplete,
                                    secrets,
                                    null,
                                    forbidden));
        }
        assertTrue(secretsRead.isEmpty());
        final var targetFailure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                Coletas6908SampleRunner.runInLocalShadow(
                                        plan(5),
                                        config,
                                        frozen,
                                        Map.of(),
                                        properties,
                                        secrets,
                                        null,
                                        forbidden));
        assertEquals("COL_SAMPLE_TARGET_BINDING_REQUIRED", targetFailure.getMessage());
        assertTrue(secretsRead.isEmpty());
        final var sourceFailure =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                Coletas6908SampleRunner.runInLocalShadow(
                                        plan(5),
                                        config,
                                        frozen,
                                        Map.of(
                                                "V2_SHADOW_JDBC_URL",
                                                config.shadowStorage().jdbcUrl()),
                                        properties,
                                        secrets,
                                        null,
                                        forbidden));
        assertEquals("SYNTHETIC_MISSING_SOURCE_SECRET", sourceFailure.getMessage());
        assertEquals(List.of(SecretKey.DATA_EXPORT_TOKEN), secretsRead);
        assertEquals(0, opened.get());
        assertThrows(
                IllegalArgumentException.class,
                () -> Coletas6908PilotRunner.preflight(plan(5), config, frozen));
    }

    private void assertCliRefused(
            final String[] args,
            final Properties properties,
            final Coletas6908PilotMain.SampleExecutor executor) {
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                2,
                Coletas6908PilotMain.run(
                        args,
                        new PrintStream(new ByteArrayOutputStream()),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        executor));
        assertEquals("COL_SAMPLE_STOP phase=PREFLIGHT", errors.toString().strip());
    }

    @Test
    void saCliRejectsUnsafeConfigurationBeforeExecutorAndCredentialResolution() throws Exception {
        final var cli = saCli();
        final var file = directory.resolve("runtime.properties");
        final String original = Files.readString(file);
        final var opened = new AtomicInteger();
        final Coletas6908PilotMain.SampleExecutor forbidden =
                (p, c, r, e) -> {
                    opened.incrementAndGet();
                    throw new AssertionError("UNSAFE_CONFIGURATION_MUST_NOT_OPEN_JDBC");
                };
        for (final String changed :
                List.of(
                        original.replace("encrypt=true", "encrypt=false"),
                        original.replace(
                                "trustServerCertificate=false", "trustServerCertificate=true"),
                        original.replace(
                                "jdbc:sqlserver://localhost;",
                                "jdbc:sqlserver://remote.example.test;"),
                        original.replace(
                                "databaseName=ETL_SISTEMA_V2_SHADOW", "databaseName=ETL_SISTEMA"),
                        original.replace(
                                "runtime.environment=LOCAL_SHADOW",
                                "runtime.environment=PRODUCTION"),
                        original.replace("integratedSecurity=false", "integratedSecurity=true"),
                        original.replace(
                                "integratedSecurity=false", "integratedSecurity=false;user=sa"),
                        original + "shadow.password=synthetic-offline-only\n")) {
            assertFalse(original.equals(changed), "Every negative must change the configuration");
            Files.writeString(file, changed);
            assertThrows(
                    RuntimeException.class,
                    () ->
                            new RuntimeConfigurationFactory()
                                    .load(file, new Properties(), Map.of(), CLOCK));
            assertCliRefused(cli.args(), sampleProperties(), forbidden);
        }
        assertEquals(0, opened.get());
    }

    static Properties sampleProperties() {
        final var properties = new Properties();
        properties.setProperty("shadow.local.integration.enabled", "true");
        properties.setProperty("shadow.local.integration.profile.active", "true");
        properties.setProperty("coletas6908.sample.enabled", "true");
        properties.setProperty("coletas6908.sample.sa.enabled", "true");
        properties.setProperty("jdk.httpclient.redirects.retrylimit", "1");
        properties.setProperty("jdk.httpclient.disableRetryConnect", "true");
        properties.setProperty("jdk.httpclient.enableAllMethodRetry", "false");
        return properties;
    }

    private RuntimeConfiguration saConfiguration() throws Exception {
        configuration();
        final var file = directory.resolve("runtime.properties");
        Files.writeString(
                file,
                Files.readString(file)
                                .replace("integratedSecurity=true", "integratedSecurity=false")
                        + "\nshadow.sample-sa.enabled=true\n");
        return new RuntimeConfigurationFactory().load(file, new Properties(), Map.of(), CLOCK);
    }

    private SaCli saCli() throws Exception {
        final var config = saConfiguration();
        final var doc = requestDocument(5);
        final var frozen = RuntimeOperationalRequest.fromDocument(config, doc);
        final var configFile = directory.resolve("runtime.properties");
        final var planFile = directory.resolve("sa-plan.json");
        final var requestFile = directory.resolve("sa-request.json");
        final var gateFile = directory.resolve("sa-bank.json");
        Files.writeString(planFile, planDocument().toString());
        Files.writeString(requestFile, doc.toString());
        final var gate =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "coletas-6908-sample-preflight-sa-v1")
                        .put("status", "READY_SINGLE_SAMPLE_TRIAL")
                        .put("target", "localhost/ETL_SISTEMA_V2_SHADOW")
                        .put("schemaEvidence", "MATCHES_VERSIONED_PROCEDURES")
                        .put("sqlAuthVerified", true)
                        .put("sqlPrincipal", "sa")
                        .put("tlsVerified", true)
                        .put("loopbackOnly", true)
                        .put("noConsumers", true)
                        .put("rollbackOnly", true)
                        .put("reservationReference", "SYNTHETIC_OFFLINE_ONLY")
                        .put("issuedAtUtc", NOW.minusSeconds(1).toString())
                        .put("validUntilUtc", NOW.plusSeconds(60).toString())
                        .put("configSha256", Coletas6908SamplePreflight.sha256(configFile))
                        .put("planSha256", Coletas6908SamplePreflight.sha256(planFile))
                        .put("requestSha256", Coletas6908SamplePreflight.sha256(requestFile))
                        .put("contractFingerprint", frozen.binding.contractFingerprint().sha256())
                        .put(
                                "configurationFingerprint",
                                frozen.binding.configurationFingerprint().sha256());
        Files.writeString(gateFile, gate.toString());
        return new SaCli(
                config,
                gateFile,
                gate,
                new String[] {
                    "--sample",
                    "--config",
                    configFile.toString(),
                    "--plan",
                    planFile.toString(),
                    "--request",
                    requestFile.toString(),
                    "--bank-preflight",
                    gateFile.toString()
                });
    }

    record SaCli(
            RuntimeConfiguration config,
            Path gateFile,
            com.fasterxml.jackson.databind.node.ObjectNode gate,
            String[] args) {}

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
        properties.setProperty("coletas6908.sample.sa.enabled", "true");
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
                "SA opt-in cannot silently run the Windows configuration");
        assertEquals(1, invoked.get(), "Authentication mismatch must not open a trial");
        assertEquals("COL_SAMPLE_STOP phase=PREFLIGHT", errors.toString().strip());
        properties.remove("coletas6908.sample.sa.enabled");
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

    static com.fasterxml.jackson.databind.node.ObjectNode planDocument() {
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

    static com.fasterxml.jackson.databind.node.ObjectNode requestDocument(final int per) {
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

    static RuntimeOperationalExecution.SourceGateways changedRows(
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

    static final class FakeTrial implements Coletas6908SampleRunner.Trial {
        private final DataSource source;
        private final List<ColetaStageBatch> batches = new ArrayList<>();
        private Map<Integer, Integer> batchPages;
        boolean closed;
        boolean verified;
        private boolean mismatch;
        private boolean closeFailure;
        private boolean transitionFailure;

        FakeTrial(final DataSource source) {
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

        int rows() {
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
