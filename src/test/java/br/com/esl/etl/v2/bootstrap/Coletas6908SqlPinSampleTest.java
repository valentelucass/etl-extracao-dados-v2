package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.SecretKey;
import br.com.esl.etl.v2.plataforma.configuracao.SecretProvider;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Coletas6908SqlPinSampleTest {
    private static final Instant NOW = Instant.parse("2036-03-21T12:00:00Z");
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-03-21T12:00:00Z"), ZoneId.of("UTC"));
    @TempDir Path directory;

    @Test
    void strictSampleCliMustNotSilentlyConsumePinOptIn() throws Exception {
        final var support = new Coletas6908SampleRunnerTest();
        support.directory = directory;
        final var cli = support.sharedSaCli();
        final var properties = Coletas6908SampleRunnerTest.sampleProperties();
        properties.setProperty("coletas6908.sample.shared.enabled", "true");
        properties.setProperty("coletas6908.sample.sql-pin.enabled", "true");
        final var calls = new AtomicInteger();
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        final int status =
                Coletas6908PilotMain.run(
                        cli.args(),
                        new PrintStream(output),
                        new PrintStream(errors),
                        properties,
                        Map.of(),
                        CLOCK,
                        (p, c, r, env) -> {
                            throw new AssertionError("WRONG_LEGACY_ENTRY");
                        },
                        (p, c, r, env, admission) -> {
                            calls.incrementAndGet();
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
                                    "OFFLINE_COUNTER_ONLY");
                        });
        assertEquals(3, status, "A pin opt-in cannot be silently ignored by the strict CLI");
        assertEquals(0, calls.get());
        assertEquals("", output.toString());
    }

    @Test
    void pinnedCliRunsRealMapperStagingReadbackAndOwnCloseWithoutMutatingHttpsDefaults()
            throws Exception {
        final var cli = pinnedCli();
        final var ssl = SSLContext.getDefault();
        final var httpTls = HttpsURLConnection.getDefaultSSLSocketFactory();
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new Coletas6908SampleRunnerTest.FakeTrial(fixture.dataSource());
        final var keys = new ArrayList<SecretKey>();
        final var calls = new AtomicInteger();
        final var out = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                0,
                run(
                        cli,
                        pinProperties(),
                        CLOCK,
                        out,
                        errors,
                        (p, c, r, env, admission) -> {
                            assertTrue(c.shadowStorage().usesSampleSqlPinAuthentication());
                            assertTrue(admission.sqlPinned());
                            return Coletas6908SampleRunner.runInPinnedSharedShadow(
                                    p,
                                    c,
                                    r,
                                    env,
                                    pinProperties(),
                                    key -> {
                                        keys.add(key);
                                        return "synthetic-offline-only";
                                    },
                                    Coletas6908SampleRunnerTest.changedRows(
                                            fixture, 3, false, false),
                                    (target, provider, work) -> {
                                        calls.incrementAndGet();
                                        assertTrue(target.usesSampleSqlPinAuthentication());
                                        provider.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD);
                                        return work.execute(trial);
                                    },
                                    admission);
                        }));
        assertEquals(1, calls.get());
        assertEquals(
                List.of(SecretKey.DATA_EXPORT_TOKEN, SecretKey.SHADOW_SAMPLE_SA_PASSWORD), keys);
        assertEquals(1, fixture.fetches);
        assertEquals(3, trial.rows());
        assertTrue(trial.verified);
        assertTrue(trial.closed);
        assertTrue(out.toString().contains("promoted=false windowCompleteness=false"));
        assertTrue(out.toString().contains("dnsHostnameValidated=false scope=JDBC_SAMPLE_ONLY"));
        assertTrue(out.toString().contains(Coletas6908SamplePreflight.PIN_SHA256));
        assertEquals("", errors.toString());
        assertSame(ssl, SSLContext.getDefault());
        assertSame(httpTls, HttpsURLConnection.getDefaultSSLSocketFactory());
    }

    @Test
    void missingPinOrExistingOptInsNeverReachExecutor() throws Exception {
        final var cli = pinnedCli();
        for (final String key :
                List.of(
                        "coletas6908.sample.sql-pin.enabled",
                        "coletas6908.sample.enabled",
                        "coletas6908.sample.sa.enabled",
                        "coletas6908.sample.shared.enabled",
                        "shadow.local.integration.enabled",
                        "shadow.local.integration.profile.active",
                        "jdk.httpclient.redirects.retrylimit",
                        "jdk.httpclient.disableRetryConnect",
                        "jdk.httpclient.enableAllMethodRetry")) {
            final var flags = pinProperties();
            flags.remove(key);
            refused(cli, flags);
        }
        for (final String value : List.of("false", "TRUE", "yes")) {
            final var flags = pinProperties();
            flags.setProperty("coletas6908.sample.sql-pin.enabled", value);
            refused(cli, flags);
        }
    }

    @Test
    void pinReceiptIsNotAnOldDnsTlsReceiptAndNeverFallsBack() throws Exception {
        final var cli = pinnedCli();
        for (final String version :
                List.of(
                        "coletas-6908-sample-shared-preflight-sa-v1",
                        "coletas-6908-sample-preflight-sa-v1",
                        "coletas-6908-sample-preflight-v1")) {
            Files.writeString(
                    cli.gateFile(), cli.gate().deepCopy().put("version", version).toString());
            refused(cli, pinProperties());
        }
        Files.writeString(cli.gateFile(), cli.gate().toString());
        final var oldArgs = cli.args().clone();
        oldArgs[0] = "--sample";
        final var strictFlags = pinProperties();
        strictFlags.remove("coletas6908.sample.sql-pin.enabled");
        refused(
                new Coletas6908SampleRunnerTest.SaCli(
                        cli.config(), cli.gateFile(), cli.gate(), oldArgs),
                strictFlags);
    }

    @Test
    void changedPinApprovalIdentityTlsOrIsolationRefusesBeforeTrial() throws Exception {
        final var cli = pinnedCli();
        final var invalid = new ArrayList<ObjectNode>();
        invalid.add(cli.gate().deepCopy().put("leafSha256", "0".repeat(64)));
        invalid.add(
                cli.gate()
                        .deepCopy()
                        .put(
                                "leafSha256",
                                Coletas6908SamplePreflight.PIN_SHA256.toLowerCase(
                                        java.util.Locale.ROOT)));
        invalid.add(cli.gate().deepCopy().put("manualApprovalReference", "OTHER_DECISION"));
        invalid.add(cli.gate().deepCopy().put("identityMode", "DNS_PKIX"));
        invalid.add(cli.gate().deepCopy().put("dnsHostnameValidated", true));
        invalid.add(cli.gate().deepCopy().put("dnsHostnameValidated", "false"));
        invalid.add(cli.gate().deepCopy().put("tlsVerified", true));
        invalid.add(cli.gate().deepCopy().put("windowsAuthVerified", false));
        invalid.add(cli.gate().deepCopy().put("sqlPrincipal", "other"));
        invalid.add(cli.gate().deepCopy().put("target", "remote/ETL_SISTEMA_V2_SHADOW"));
        invalid.add(cli.gate().deepCopy().put("target", "localhost/ETL_SISTEMA"));
        invalid.add(cli.gate().deepCopy().put("isolationScope", "ETL_SISTEMA"));
        invalid.add(cli.gate().deepCopy().put("noConsumers", true));
        invalid.add(cli.gate().deepCopy().put("loopbackOnly", true));
        invalid.add(cli.gate().deepCopy().put("schemaEvidence", "UNPROVED"));
        invalid.add(
                cli.gate().deepCopy().put("executionDeadlineUtc", NOW.plusSeconds(61).toString()));
        invalid.add(cli.gate().deepCopy().put("validUntilUtc", NOW.toString()));
        for (final String key :
                List.of(
                        "tlsEncrypted",
                        "tlsLeafPinVerified",
                        "tlsPeerLoopbackVerified",
                        "sqlAuthVerified",
                        "ownConnectionLoopbackOnly",
                        "noConflictingShadowWriters",
                        "v1OutsideProtectedFootprint",
                        "v1WindowVerified",
                        "rollbackOnly")) {
            invalid.add(cli.gate().deepCopy().put(key, false));
            invalid.add(cli.gate().deepCopy().put(key, "true"));
            final var missing = cli.gate().deepCopy();
            missing.remove(key);
            invalid.add(missing);
        }
        for (final var gate : invalid) {
            Files.writeString(cli.gateFile(), gate.toString());
            refused(cli, pinProperties());
        }
    }

    @Test
    void alteredActualTargetTlsAndProfileRefuseDespiteClaimedReceipt() throws Exception {
        final var cli = pinnedCli();
        final var path = Path.of(cli.args()[2]);
        final String valid = Files.readString(path);
        for (final String changed :
                List.of(
                        valid.replace(
                                "jdbc:sqlserver://localhost",
                                "jdbc:sqlserver://remote.example.test"),
                        valid.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA"),
                        valid.replace("encrypt=true", "encrypt=false"),
                        valid.replace(
                                "trustServerCertificate=false", "trustServerCertificate=true"),
                        valid.replace(
                                "runtime.environment=LOCAL_SHADOW",
                                "runtime.environment=PRODUCTION"),
                        valid.replace("integratedSecurity=false", "integratedSecurity=true")
                                .replace(
                                        "shadow.sample-sa.enabled=true",
                                        "shadow.sample-sa.enabled=false"))) {
            assertFalse(valid.equals(changed), "Every negative must change actual configuration");
            Files.writeString(path, changed);
            Files.writeString(
                    cli.gateFile(),
                    cli.gate()
                            .deepCopy()
                            .put("configSha256", Coletas6908SamplePreflight.sha256(path))
                            .toString());
            final var settings = new Properties();
            settings.load(new java.io.StringReader(changed));
            refused(
                    cli,
                    pinProperties(),
                    Map.of("V2_SHADOW_JDBC_URL", settings.getProperty("shadow.jdbc-url")));
        }
    }

    @Test
    void noGlobalTrustInputMayAccompanySqlOnlyPin() throws Exception {
        final var cli = pinnedCli();
        for (final String name :
                List.of(
                        "javax.net.ssl.trustStore",
                        "javax.net.ssl.trustStoreType",
                        "javax.net.ssl.trustStorePassword",
                        "javax.net.ssl.trustStoreProvider")) {
            final var flags = pinProperties();
            flags.setProperty(name, "synthetic-offline-only");
            refused(cli, flags);
        }
    }

    @Test
    void strictSharedLocalAndFullPilotRefusePinTargetBeforeSourceOrSql() throws Exception {
        final var cli = pinnedCli();
        final var plan = Coletas6908PilotPlan.read(Path.of(cli.args()[4]));
        final var request = RuntimeOperationalRequest.read(cli.config(), Path.of(cli.args()[6]));
        final SecretProvider forbidden =
                key -> {
                    throw new AssertionError("SECRET_BEFORE_REFUSAL");
                };
        final Coletas6908SampleRunner.TrialExecution noSql =
                (target, provider, work) -> {
                    throw new AssertionError("SQL_BEFORE_REFUSAL");
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Coletas6908SampleRunner.runInSharedShadow(
                                plan,
                                cli.config(),
                                request,
                                Map.of(),
                                pinProperties(),
                                forbidden,
                                null,
                                noSql,
                                new Coletas6908SamplePreflight.SharedAdmission(
                                        "OFFLINE", NOW.plusSeconds(30))));
        final var localFlags = pinProperties();
        localFlags.remove("coletas6908.sample.shared.enabled");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Coletas6908SampleRunner.runInLocalShadow(
                                plan,
                                cli.config(),
                                request,
                                Map.of(),
                                localFlags,
                                forbidden,
                                null,
                                noSql));
        assertThrows(
                RuntimeException.class,
                () -> Coletas6908PilotRunner.preflight(plan, cli.config(), request));
    }

    @Test
    void falseAdmissionAndDateBoundsCannotReadSecretsOrReachConnector() throws Exception {
        final var cli = pinnedCli();
        final var plan = Coletas6908PilotPlan.read(Path.of(cli.args()[4]));
        final var request = RuntimeOperationalRequest.read(cli.config(), Path.of(cli.args()[6]));
        final var secrets = new AtomicInteger();
        final var connectors = new AtomicInteger();
        final SecretProvider provider =
                key -> {
                    secrets.incrementAndGet();
                    return "offline-only";
                };
        final Coletas6908SampleRunner.TrialExecution trials =
                (target, ignored, work) -> {
                    connectors.incrementAndGet();
                    throw new AssertionError("CONNECTOR_AFTER_INVALID_ADMISSION");
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Coletas6908SampleRunner.runInPinnedSharedShadow(
                                plan,
                                cli.config(),
                                request,
                                Map.of(),
                                pinProperties(),
                                provider,
                                null,
                                trials,
                                new Coletas6908SamplePreflight.SharedAdmission(
                                        "OFFLINE", NOW.plusSeconds(30))));
        for (final Instant bad :
                List.of(
                        Instant.parse("2026-10-03T20:13:46Z"),
                        Instant.parse("2056-10-03T20:13:48Z"))) {
            final var changedClock = Clock.fixed(bad, ZoneId.of("UTC"));
            final var configuration = withClock(cli.config(), changedClock);
            final var error =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    Coletas6908SampleRunner.runInPinnedSharedShadow(
                                            plan,
                                            configuration,
                                            request,
                                            Map.of(),
                                            pinProperties(),
                                            provider,
                                            null,
                                            trials,
                                            new Coletas6908SamplePreflight.SharedAdmission(
                                                    "OFFLINE", bad.plusSeconds(30), true)));
            assertEquals("COL_SAMPLE_PIN_CERTIFICATE_VALIDITY_REQUIRED", error.getMessage());
        }
        assertEquals(0, secrets.get());
        assertEquals(0, connectors.get());
    }

    @Test
    void defaultPinnedEntryIsLazyBeforeMissingFlagsBindingAndSourceCredential() throws Exception {
        final var cli = pinnedCli();
        final var plan = Coletas6908PilotPlan.read(Path.of(cli.args()[4]));
        final var request = RuntimeOperationalRequest.read(cli.config(), Path.of(cli.args()[6]));
        final var admission =
                new Coletas6908SamplePreflight.SharedAdmission(
                        "OFFLINE", NOW.plusSeconds(30), true);
        final var watch = new SecretWatchingMap();
        final var flags = pinProperties();
        final var before = new HashMap<String, String>();
        for (final String key : flags.stringPropertyNames()) {
            before.put(key, System.getProperty(key));
        }
        try {
            for (final String key : flags.stringPropertyNames()) {
                System.setProperty(key, flags.getProperty(key));
            }
            for (final String key : flags.stringPropertyNames()) {
                System.clearProperty(key);
                assertThrows(
                        RuntimeException.class,
                        () ->
                                Coletas6908SampleRunner.runInPinnedSharedShadow(
                                        plan, cli.config(), request, watch, admission));
                assertEquals(0, watch.secretReads);
                System.setProperty(key, flags.getProperty(key));
            }
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            Coletas6908SampleRunner.runInPinnedSharedShadow(
                                    plan, cli.config(), request, watch, admission));
            assertEquals(
                    0,
                    watch.secretReads,
                    "Binding precedes provider construction/environment secret get");
            watch.put("V2_SHADOW_JDBC_URL", cli.config().shadowStorage().jdbcUrl());
            assertThrows(
                    RuntimeException.class,
                    () ->
                            Coletas6908SampleRunner.runInPinnedSharedShadow(
                                    plan, cli.config(), request, watch, admission));
            assertTrue(
                    watch.secretReads > 0,
                    "Only admitted entry reaches provider and missing source credential");
        } finally {
            for (final var entry : before.entrySet()) {
                if (entry.getValue() == null) {
                    System.clearProperty(entry.getKey());
                } else {
                    System.setProperty(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    @Test
    void pinnedDeadlineDuringActualRollbackCloseDoesNotPrintSuccess() throws Exception {
        final var cli = pinnedCli();
        final var now = new AtomicReference<>(NOW);
        final Clock mutable =
                new Clock() {
                    @Override
                    public ZoneId getZone() {
                        return ZoneId.of("UTC");
                    }

                    @Override
                    public Clock withZone(final ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return now.get();
                    }
                };
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new Coletas6908SampleRunnerTest.FakeTrial(fixture.dataSource());
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                2,
                run(
                        cli,
                        pinProperties(),
                        mutable,
                        output,
                        errors,
                        (p, c, r, env, admission) ->
                                Coletas6908SampleRunner.runInPinnedSharedShadow(
                                        p,
                                        c,
                                        r,
                                        env,
                                        pinProperties(),
                                        key -> "offline-only",
                                        Coletas6908SampleRunnerTest.changedRows(
                                                fixture, 1, false, false),
                                        (target, provider, work) ->
                                                work.execute(
                                                        closeThen(
                                                                trial,
                                                                () ->
                                                                        now.set(
                                                                                NOW.plusSeconds(
                                                                                        61)))),
                                        admission)));
        assertTrue(trial.verified);
        assertTrue(trial.closed);
        assertEquals(1, fixture.fetches);
        assertEquals("", output.toString());
        assertEquals("COL_SAMPLE_STOP phase=TRIAL", errors.toString().strip());
    }

    @Test
    void pinnedCliAlsoChecksDeadlineAfterExecutorReturn() throws Exception {
        final var cli = pinnedCli();
        final var now = new AtomicReference<>(NOW);
        final Clock mutable =
                new Clock() {
                    @Override
                    public ZoneId getZone() {
                        return ZoneId.of("UTC");
                    }

                    @Override
                    public Clock withZone(final ZoneId zone) {
                        return this;
                    }

                    @Override
                    public Instant instant() {
                        return now.get();
                    }
                };
        final var fixture = new RuntimeBootstrapTestFixture(NOW);
        final var trial = new Coletas6908SampleRunnerTest.FakeTrial(fixture.dataSource());
        final var output = new ByteArrayOutputStream();
        final var errors = new ByteArrayOutputStream();
        assertEquals(
                2,
                run(
                        cli,
                        pinProperties(),
                        mutable,
                        output,
                        errors,
                        (p, c, r, env, admission) -> {
                            final var receipt =
                                    Coletas6908SampleRunner.runInPinnedSharedShadow(
                                            p,
                                            c,
                                            r,
                                            env,
                                            pinProperties(),
                                            key -> "offline-only",
                                            Coletas6908SampleRunnerTest.changedRows(
                                                    fixture, 1, false, false),
                                            (target, provider, work) -> work.execute(trial),
                                            admission);
                            now.set(NOW.plusSeconds(61));
                            return receipt;
                        }));
        assertTrue(trial.verified);
        assertTrue(trial.closed);
        assertEquals(1, fixture.fetches);
        assertEquals("", output.toString());
        assertEquals("COL_SAMPLE_STOP phase=TRIAL", errors.toString().strip());
    }

    @Test
    void interruptedPinnedEntryDoesNotReadEnvironmentSecrets() throws Exception {
        final var cli = pinnedCli();
        final var plan = Coletas6908PilotPlan.read(Path.of(cli.args()[4]));
        final var request = RuntimeOperationalRequest.read(cli.config(), Path.of(cli.args()[6]));
        final var watch = new SecretWatchingMap();
        Thread.currentThread().interrupt();
        try {
            final var failure =
                    assertThrows(
                            IllegalStateException.class,
                            () ->
                                    Coletas6908SampleRunner.runInPinnedSharedShadow(
                                            plan,
                                            cli.config(),
                                            request,
                                            watch,
                                            new Coletas6908SamplePreflight.SharedAdmission(
                                                    "OFFLINE", NOW.plusSeconds(30), true)));
            assertEquals("COL_SAMPLE_SHARED_DEADLINE_OR_CANCELLED", failure.getMessage());
            assertEquals(0, watch.secretReads);
        } finally {
            Thread.interrupted();
        }
    }

    private Coletas6908SampleRunnerTest.SaCli pinnedCli() throws Exception {
        final var support = new Coletas6908SampleRunnerTest();
        support.directory = directory;
        final var strict = support.sharedSaCli();
        final var pinned = Coletas6908SampleRunner.pinnedConfiguration(strict.config());
        assertFalse(strict.config().shadowStorage().usesSampleSqlPinAuthentication());
        assertSame(strict.config().dataExport(), pinned.dataExport());
        assertSame(strict.config().graphQl(), pinned.graphQl());
        final var gate = strict.gate().deepCopy();
        gate.remove("tlsVerified");
        gate.put("version", Coletas6908SamplePreflight.PIN_VERSION)
                .put("identityMode", Coletas6908SamplePreflight.PIN_IDENTITY)
                .put("manualApprovalReference", Coletas6908SamplePreflight.PIN_APPROVAL)
                .put("leafSha256", Coletas6908SamplePreflight.PIN_SHA256)
                .put("dnsHostnameValidated", false)
                .put("tlsEncrypted", true)
                .put("tlsLeafPinVerified", true)
                .put("tlsPeerLoopbackVerified", true);
        final var frozen = RuntimeOperationalRequest.read(pinned, Path.of(strict.args()[6]));
        gate.put("contractFingerprint", frozen.binding.contractFingerprint().sha256())
                .put(
                        "configurationFingerprint",
                        frozen.binding.configurationFingerprint().sha256());
        Files.writeString(strict.gateFile(), gate.toString());
        final var args = strict.args().clone();
        args[0] = "--sample-sql-pin";
        return new Coletas6908SampleRunnerTest.SaCli(pinned, strict.gateFile(), gate, args);
    }

    private static Properties pinProperties() {
        final var p = Coletas6908SampleRunnerTest.sampleProperties();
        p.setProperty("coletas6908.sample.shared.enabled", "true");
        p.setProperty("coletas6908.sample.sql-pin.enabled", "true");
        return p;
    }

    private static int run(
            final Coletas6908SampleRunnerTest.SaCli cli,
            final Properties flags,
            final Clock clock,
            final ByteArrayOutputStream output,
            final ByteArrayOutputStream errors,
            final Coletas6908PilotMain.SharedSampleExecutor pinned) {
        return run(
                cli,
                flags,
                clock,
                output,
                errors,
                pinned,
                Map.of("V2_SHADOW_JDBC_URL", cli.config().shadowStorage().jdbcUrl()));
    }

    private static int run(
            final Coletas6908SampleRunnerTest.SaCli cli,
            final Properties flags,
            final Clock clock,
            final ByteArrayOutputStream output,
            final ByteArrayOutputStream errors,
            final Coletas6908PilotMain.SharedSampleExecutor pinned,
            final Map<String, String> environment) {
        return Coletas6908PilotMain.run(
                cli.args(),
                new PrintStream(output),
                new PrintStream(errors),
                flags,
                environment,
                clock,
                (p, c, r, env) -> {
                    throw new AssertionError("LEGACY_LOCAL_EXECUTOR");
                },
                (p, c, r, env, admission) -> {
                    throw new AssertionError("LEGACY_SHARED_EXECUTOR");
                },
                pinned);
    }

    private static void refused(
            final Coletas6908SampleRunnerTest.SaCli cli, final Properties flags) {
        refused(cli, flags, Map.of("V2_SHADOW_JDBC_URL", cli.config().shadowStorage().jdbcUrl()));
    }

    private static void refused(
            final Coletas6908SampleRunnerTest.SaCli cli,
            final Properties flags,
            final Map<String, String> environment) {
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        final var calls = new AtomicInteger();
        final int status =
                run(
                        cli,
                        flags,
                        CLOCK,
                        out,
                        err,
                        (p, c, r, env, admission) -> {
                            calls.incrementAndGet();
                            throw new AssertionError("EXECUTOR_BEFORE_REFUSAL");
                        },
                        environment);
        assertTrue(status == 2 || status == 3);
        assertEquals(0, calls.get());
        assertEquals("", out.toString());
    }

    private static RuntimeConfiguration withClock(final RuntimeConfiguration c, final Clock clock) {
        return new RuntimeConfiguration(
                c.environment(),
                c.businessZone(),
                clock,
                c.dataExport(),
                c.graphQl(),
                c.shadowStorage());
    }

    private static Coletas6908SampleRunner.Trial closeThen(
            final Coletas6908SampleRunner.Trial trial, final Runnable effect) {
        return new Coletas6908SampleRunner.Trial() {
            @Override
            public javax.sql.DataSource dataSource() {
                return trial.dataSource();
            }

            @Override
            public br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway staging() {
                return trial.staging();
            }

            @Override
            public void checkpoint() {
                trial.checkpoint();
            }

            @Override
            public Coletas6908SampleRunner.Comparison verifyStaging(
                    final br.com.esl.etl.v2.modulos.coletas.domain.ColetaObservedRootComparator
                                    .Binding
                            binding,
                    final List<
                                    br.com.esl.etl.v2.modulos.coletas.domain
                                            .ColetaObservedRootComparator.ExpectedRoot>
                            expected,
                    final Map<Integer, Integer> batchPages) {
                return trial.verifyStaging(binding, expected, batchPages);
            }

            @Override
            public void close() throws java.sql.SQLException {
                trial.close();
                effect.run();
            }
        };
    }

    private static final class SecretWatchingMap extends HashMap<String, String> {
        private static final long serialVersionUID = 1L;
        private int secretReads;

        @Override
        public String get(final Object key) {
            if (key instanceof String name
                    && (name.contains("TOKEN") || name.contains("PASSWORD"))) {
                secretReads++;
            }
            return super.get(key);
        }
    }
}
