package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

/** Public metadata controls; real Main/JAR/files/leases, without a source or SQL execution. */
@EnabledOnOs(OS.WINDOWS)
class MainDevWorkspaceProcessTest {
    @TempDir(cleanup = CleanupMode.NEVER)
    Path directory;

    @Test
    void realEngineEntryRequiresItsOwnOptInBeforeBankAdmission() throws Exception {
        final var actual = child(bundle(), true, List.of(), List.of("--dev-engine"));
        assertEquals(20, actual.exit());
        assertEquals("", actual.stdout());
        assertTrue(actual.stderr().contains("DEV_ENGINE_EXPLICIT_OPT_IN_REQUIRED"));
    }

    @Test
    void realEngineEntryUsesConcreteBankAdmissionAndRefusesBeforeEffects() throws Exception {
        final var bundle = bundle();
        final var actual =
                child(
                        bundle,
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of("--dev-engine"));
        assertEquals(20, actual.exit(), actual.stderr());
        assertEquals("", actual.stderr());
        final var receipt = new ObjectMapper().readTree(actual.stdout());
        assertEquals("DEV_ENGINE_GATE_REFUSED_ZERO_EFFECTS", receipt.path("status").asText());
        assertEquals("DEV_ENGINE_GATE_ADMISSION", receipt.path("reasonCode").asText());
        assertFalse(receipt.path("executorInvoked").asBoolean());
        assertFalse(receipt.path("engineExecuted").asBoolean());
        assertFalse(receipt.path("sourceExecuted").asBoolean());
        assertFalse(receipt.path("captureExecuted").asBoolean());
        assertFalse(receipt.path("goalComplete").asBoolean());
        try (var write =
                java.nio.channels.FileChannel.open(
                        bundle.resolve(RuntimeDevWorkspaceBundle.CONFIGURATION),
                        java.nio.file.StandardOpenOption.WRITE)) {
            assertTrue(write.isOpen());
        }
    }

    @Test
    void engineAndCatalogCannotShareTheConsumedCatalogDispatch() throws Exception {
        final var actual =
                child(
                        bundle(),
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of(
                                "--dev-engine",
                                "--dev-catalog",
                                "public-absent",
                                "--dispatch-sha256",
                                "0".repeat(64)));
        assertEquals(2, actual.exit());
        assertTrue(actual.stderr().contains("DEV_WORKSPACE_CLI_INVALID"));
        assertEquals("", actual.stdout());
    }

    @Test
    void explicitDispatchUsesSameConcreteAdapterAndRejectsAbsentOrCatalogAuthority()
            throws Exception {
        final var bundle = bundle();
        final var missing =
                child(
                        bundle,
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of(
                                "--dev-engine",
                                        bundle.resolve("absent-root-dispatch.json").toString(),
                                "--dispatch-sha256", "0".repeat(64)));
        assertEquals(20, missing.exit(), missing.stderr());
        assertEquals(
                "DEV_ENGINE_GATE_ADMISSION",
                new ObjectMapper().readTree(missing.stdout()).path("reasonCode").asText());
        final var catalog = bundle.resolve("catalog-negative-control.json");
        Files.writeString(
                catalog,
                "{\"schema\":\"V2_DEV_C78_CATALOG_DISPATCH_V1\",\"dispatchId\":\"C78CAT01-negative-control\"}");
        final var historical =
                child(
                        bundle,
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of(
                                "--dev-engine",
                                catalog.toString(),
                                "--dispatch-sha256",
                                hash(catalog)));
        assertEquals(20, historical.exit(), historical.stderr());
        assertEquals(
                "DEV_ENGINE_GATE_ADMISSION",
                new ObjectMapper().readTree(historical.stdout()).path("reasonCode").asText());
        assertEquals("", historical.stderr());
    }

    @Test
    void engineDispatchRequiresCanonicalDigestAndUnambiguousArguments() throws Exception {
        final var bundle = bundle();
        for (final String digest : List.of("invalid", "A".repeat(64))) {
            final var actual =
                    child(
                            bundle,
                            true,
                            List.of("-Druntime.dev.engine.enabled=true"),
                            List.of("--dev-engine", "public-absent", "--dispatch-sha256", digest));
            assertEquals(20, actual.exit());
            assertEquals("", actual.stdout());
            assertTrue(actual.stderr().contains("DEV_ENGINE_DISPATCH_DIGEST_INVALID"));
        }
        final var ambiguous =
                child(
                        bundle,
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of("--dev-engine", "public-absent"));
        assertEquals(2, ambiguous.exit());
        assertTrue(ambiguous.stderr().contains("DEV_WORKSPACE_CLI_INVALID"));
    }

    @Test
    void engineRejectsAnotherProfileAndBundleTargetBeforeBankEffects() throws Exception {
        final var bundle = bundle();
        final var profile =
                child(
                        bundle,
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of("--dev-engine"),
                        "TEST_RETAINED_LOCAL_SQL_PIN_V1");
        assertEquals(20, profile.exit());
        assertTrue(profile.stderr().contains("DEV_WORKSPACE_EXPLICIT_OPT_IN_REQUIRED"));
        final var manifest = bundle.resolve("manifest.json");
        final var json =
                (com.fasterxml.jackson.databind.node.ObjectNode)
                        new ObjectMapper().readTree(Files.readAllBytes(manifest));
        json.put("target", "localhost:1433/ETL_SISTEMA");
        new ObjectMapper().writeValue(manifest.toFile(), json);
        final var target =
                child(
                        bundle,
                        true,
                        List.of("-Druntime.dev.engine.enabled=true"),
                        List.of("--dev-engine"));
        assertEquals(20, target.exit());
        assertTrue(target.stderr().contains("DEV_WORKSPACE_MANIFEST_SCOPE"));
        assertEquals("", target.stdout());
    }

    @Test
    void allNegativeAdmissionGatesCloseBeforeSessionDatasourceSourceAndExecutor() throws Exception {
        final var input = engineInput();
        final var scope = input.scope();
        final var request = input.request();
        for (final var gate : RuntimeDevEngineBankBinding.MissingGate.values()) {
            final var closed = new java.util.concurrent.atomic.AtomicBoolean();
            final RuntimeDevEngineBankBinding negativeControl =
                    actual -> {
                        assertEquals(scope, actual);
                        return new RuntimeDevEngineBankBinding.Admission() {
                            @Override
                            public void verify() throws java.sql.SQLException {
                                throw new RuntimeDevEngineBankBinding.Refusal(gate);
                            }

                            @Override
                            public RuntimeDevEngineBankBinding.Session open() {
                                throw new AssertionError("SESSION_EFFECT_BEFORE_GATE");
                            }

                            @Override
                            public void close() {
                                closed.set(true);
                            }
                        };
                    };
            final var actual =
                    RuntimeDevEngineExecution.execute(scope, request, negativeControl, System.err);
            assertEquals(20, actual.category().code());
            final var receipt = new ObjectMapper().readTree(actual.receipt());
            assertEquals("DEV_ENGINE_GATE_" + gate.name(), receipt.path("reasonCode").asText());
            assertFalse(receipt.path("executorInvoked").asBoolean());
            assertTrue(closed.get());
        }
    }

    @Test
    void refusalDuringOpenCannotProduceZeroEffectReceiptBeforeExecutor() throws Exception {
        final var input = engineInput();
        final var openAttempted = new java.util.concurrent.atomic.AtomicBoolean();
        final var closed = new java.util.concurrent.atomic.AtomicBoolean();
        final RuntimeDevEngineBankBinding negativeControl =
                scope ->
                        new RuntimeDevEngineBankBinding.Admission() {
                            @Override
                            public void verify() {}

                            @Override
                            public RuntimeDevEngineBankBinding.Session open()
                                    throws java.sql.SQLException {
                                openAttempted.set(true);
                                final var failure =
                                        new RuntimeDevEngineBankBinding.Refusal(
                                                RuntimeDevEngineBankBinding.MissingGate
                                                        .PHYSICAL_CONTRACT);
                                failure.initCause(
                                        new java.sql.SQLException(
                                                "UNIT_TEST_PRIVATE_MESSAGE_DO_NOT_PUBLISH"));
                                throw failure;
                            }

                            @Override
                            public void close() {
                                closed.set(true);
                            }
                        };
        final var actual =
                RuntimeDevEngineExecution.execute(
                        input.scope(), input.request(), negativeControl, System.err);
        final var receipt = new ObjectMapper().readTree(actual.receipt());
        assertEquals(20, actual.category().code());
        assertEquals("DEV_ENGINE_OUTCOME_NOT_VERIFIED", receipt.path("status").asText());
        assertTrue(receipt.path("openAttempted").asBoolean());
        assertFalse(receipt.has("engineExecuted"));
        assertFalse(receipt.has("sourceExecuted"));
        assertFalse(receipt.has("captureExecuted"));
        assertEquals(
                RuntimeDevEngineBankBinding.Refusal.class.getName(),
                receipt.path("failureType").asText());
        assertEquals(2, receipt.path("failureCauseTypes").size());
        assertTrue(receipt.path("failureV2Frame").asText().startsWith("br.com.esl.etl.v2."));
        assertFalse(actual.receipt().contains("UNIT_TEST_PRIVATE_MESSAGE_DO_NOT_PUBLISH"));
        assertTrue(openAttempted.get());
        assertTrue(closed.get());
        Files.writeString(directory.resolve("negative-open-attempt.json"), actual.receipt());
    }

    @Test
    void uncertainAdmissionCleanupCannotProduceZeroEffectReceipt() throws Exception {
        final var input = engineInput();
        final RuntimeDevEngineBankBinding negativeControl =
                scope ->
                        new RuntimeDevEngineBankBinding.Admission() {
                            @Override
                            public void verify() throws java.sql.SQLException {
                                throw new RuntimeDevEngineBankBinding.Refusal(
                                        RuntimeDevEngineBankBinding.MissingGate.SOURCE_QUOTA);
                            }

                            @Override
                            public RuntimeDevEngineBankBinding.Session open() {
                                throw new AssertionError("SESSION_EFFECT_BEFORE_GATE");
                            }

                            @Override
                            public void close() throws java.sql.SQLException {
                                throw new java.sql.SQLException("UNIT_TEST_CLEANUP_UNCERTAIN");
                            }
                        };
        final var actual =
                RuntimeDevEngineExecution.execute(
                        input.scope(), input.request(), negativeControl, System.err);
        final var receipt = new ObjectMapper().readTree(actual.receipt());
        assertEquals(20, actual.category().code());
        assertEquals("DEV_ENGINE_OUTCOME_NOT_VERIFIED", receipt.path("status").asText());
        assertFalse(receipt.path("cleanupVerified").asBoolean());
        assertFalse(receipt.has("engineExecuted"));
        assertEquals(1, receipt.path("cleanupSuppressedCount").asInt());
        assertEquals(
                "java.sql.SQLException", receipt.path("cleanupSuppressedTypes").get(0).asText());
        assertFalse(actual.receipt().contains("UNIT_TEST_CLEANUP_UNCERTAIN"));
        Files.writeString(directory.resolve("negative-cleanup-uncertain.json"), actual.receipt());
    }

    @Test
    void openedSessionRefusalBeforeExecutorStillHasUnverifiedOutcome() throws Exception {
        final var input = engineInput();
        final var closed = new java.util.concurrent.atomic.AtomicBoolean();
        final RuntimeDevEngineBankBinding negativeControl =
                scope ->
                        new RuntimeDevEngineBankBinding.Admission() {
                            @Override
                            public void verify() {}

                            @Override
                            public RuntimeDevEngineBankBinding.Session open() {
                                return new RuntimeDevEngineBankBinding.Session() {
                                    @Override
                                    public javax.sql.DataSource dataSource() {
                                        throw new AssertionError("DATASOURCE_BEFORE_SESSION_GATE");
                                    }

                                    @Override
                                    public void checkpoint() throws java.sql.SQLException {
                                        throw new RuntimeDevEngineBankBinding.Refusal(
                                                RuntimeDevEngineBankBinding.MissingGate
                                                        .WRITER_PROTECTION);
                                    }

                                    @Override
                                    public void close() {
                                        closed.set(true);
                                    }
                                };
                            }

                            @Override
                            public void close() {}
                        };
        final var actual =
                RuntimeDevEngineExecution.execute(
                        input.scope(), input.request(), negativeControl, System.err);
        final var receipt = new ObjectMapper().readTree(actual.receipt());
        assertEquals(20, actual.category().code());
        assertEquals("DEV_ENGINE_OUTCOME_NOT_VERIFIED", receipt.path("status").asText());
        assertTrue(receipt.path("openAttempted").asBoolean());
        assertFalse(receipt.path("executorInvoked").asBoolean());
        assertFalse(receipt.has("engineExecuted"));
        assertTrue(closed.get());
        Files.writeString(
                directory.resolve("negative-opened-session-checkpoint.json"), actual.receipt());
    }

    private EngineInput engineInput() throws Exception {
        final var bundle = bundle();
        final var base =
                new br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory()
                        .load(
                                bundle.resolve(RuntimeDevWorkspaceBundle.CONFIGURATION),
                                new java.util.Properties(),
                                java.util.Map.of(),
                                java.time.Clock.systemUTC());
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var configuration = RuntimeDevPreparedInvocation.composeC78(base, target);
        final var request =
                RuntimeOperationalRequest.read(
                        configuration, bundle.resolve(RuntimeDevWorkspaceBundle.REQUEST));
        final var prepared = new RuntimeDevPreparedInvocation(configuration, request, target);
        final var scope =
                new RuntimeDevEngineBankBinding.Scope(
                        configuration,
                        prepared.scope,
                        target,
                        "0".repeat(64),
                        "0".repeat(64),
                        "0".repeat(64),
                        request.plan.fingerprint().sha256(),
                        request.template,
                        request.pageSize,
                        request.limits);
        return new EngineInput(scope, request);
    }

    private record EngineInput(
            RuntimeDevEngineBankBinding.Scope scope, RuntimeOperationalRequest request) {}

    @Test
    void realMainPreparesC78TargetButNeverClaimsEffectAdmissionOrEngineExecution()
            throws Exception {
        final var bundle = bundle();
        final var refused = child(bundle, false);
        assertEquals(20, refused.exit());
        assertEquals("", refused.stdout());
        assertTrue(refused.stderr().contains("DEV_WORKSPACE_EXPLICIT_OPT_IN_REQUIRED"));
        final var actual = child(bundle, true);
        assertEquals(20, actual.exit(), actual.stderr());
        assertEquals("", actual.stderr());
        final var receipt = new ObjectMapper().readTree(actual.stdout());
        assertEquals(
                "DEV_MAIN_PREPARED_EFFECT_ADMISSION_REQUIRED", receipt.path("status").asText());
        assertTrue(receipt.path("effectiveShadowAudit").asBoolean());
        assertTrue(receipt.path("effectiveC78Metadata").asBoolean());
        assertFalse(receipt.path("effectiveIntegratedSecurity").asBoolean());
        assertFalse(receipt.path("sampleProfileInherited").asBoolean());
        assertFalse(receipt.path("authorityAdmitted").asBoolean());
        assertFalse(receipt.path("sqlAdmitted").asBoolean());
        assertFalse(receipt.path("sourceAdmitted").asBoolean());
        assertFalse(receipt.path("engineExecuted").asBoolean());
        assertEquals(1, receipt.path("plannedWorkloads").asInt());
        // A fresh write handle proves the Main released its no-share-write/delete leases.
        try (var write =
                java.nio.channels.FileChannel.open(
                        bundle.resolve(RuntimeDevWorkspaceBundle.CONFIGURATION),
                        java.nio.file.StandardOpenOption.WRITE)) {
            assertTrue(write.isOpen());
        }
    }

    @Test
    void tamperedConfigurationIsRefusedBeforeConfigurationOrRequestMaterialization()
            throws Exception {
        final var bundle = bundle();
        Files.writeString(
                bundle.resolve(RuntimeDevWorkspaceBundle.CONFIGURATION),
                "\n#public-control-tamper\n",
                java.nio.file.StandardOpenOption.APPEND);
        final var actual = child(bundle, true);
        assertEquals(20, actual.exit());
        assertEquals("", actual.stdout());
        assertTrue(actual.stderr().contains("DEV_WORKSPACE_FILE_HASH_CHANGED"));
    }

    @Test
    void anUnreviewedRuntimePropertyStillFailsTheOriginalSecretInputPolicy() throws Exception {
        final var actual =
                child(bundle(), true, List.of("-Druntime.unreviewed=public-control"), List.of());
        assertEquals(2, actual.exit());
        assertEquals("", actual.stdout());
        assertTrue(actual.stderr().contains("SecretInputPolicy.rejectSecretSystemProperties"));
        assertFalse(actual.stderr().contains("public-control"));
    }

    @Test
    void catalogCannotUseAnOwnPublicFileAsRootAuthority() throws Exception {
        final var root = bundle();
        final Path refused = directory.resolve("public-not-root.json");
        Files.writeString(refused, "{}");
        final var actual =
                child(
                        root,
                        true,
                        List.of(),
                        List.of(
                                "--dev-catalog",
                                refused.toString(),
                                "--dispatch-sha256",
                                "0".repeat(64)));
        assertEquals(2, actual.exit());
        assertEquals("", actual.stdout());
        assertTrue(actual.stderr().contains("RuntimeDevCatalogDispatch."), actual.stderr());
        assertEquals("{}", Files.readString(refused));
        assertFalse(Files.exists(directory.resolve("runtime-claim.json")));
    }

    private Path bundle() throws Exception {
        final Path root = directory.resolve("bundle");
        Files.createDirectory(root);
        Files.createDirectory(root.resolve("lib"));
        final var entries = new ArrayList<java.util.Map<String, String>>();
        final var dependencies = new ArrayList<String>();
        for (final String name :
                List.of(
                        "com.fasterxml.jackson.annotation.JsonCreator",
                        "com.fasterxml.jackson.core.JsonFactory",
                        "com.fasterxml.jackson.databind.ObjectMapper",
                        "org.slf4j.LoggerFactory",
                        "com.microsoft.sqlserver.jdbc.SQLServerDriver")) {
            final Path source =
                    Path.of(
                            Class.forName(name, false, getClass().getClassLoader())
                                    .getProtectionDomain()
                                    .getCodeSource()
                                    .getLocation()
                                    .toURI());
            final Path target = root.resolve("lib").resolve(source.getFileName());
            Files.copy(source, target);
            final String relative = "lib/" + target.getFileName();
            dependencies.add(relative);
            entries.add(java.util.Map.of("path", relative, "sha256", hash(target)));
        }
        final var header = new Manifest();
        header.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        header.getMainAttributes().put(Attributes.Name.MAIN_CLASS, Main.class.getName());
        header.getMainAttributes().put(Attributes.Name.CLASS_PATH, String.join(" ", dependencies));
        final Path classes =
                Path.of(Main.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        assertTrue(Files.isDirectory(classes), "TEST_REQUIRES_REAL_COMPILED_MAIN_CLASSES");
        try (var archive =
                        new JarOutputStream(
                                Files.newOutputStream(root.resolve(RuntimeDevWorkspaceBundle.JAR)),
                                header);
                var sources = Files.walk(classes)) {
            for (final var file : sources.filter(Files::isRegularFile).toList()) {
                final String relative = classes.relativize(file).toString().replace('\\', '/');
                if (relative.startsWith("META-INF/")) {
                    continue;
                }
                archive.putNextEntry(new JarEntry(relative));
                Files.copy(file, archive);
                archive.closeEntry();
            }
        }
        final String base =
                """
                runtime.environment=LOCAL_SHADOW
                runtime.business-timezone=America/Sao_Paulo
                dataexport.enabled=true
                dataexport.base-url=https://source.example.test
                dataexport.source-instance=PUBLIC_CONTROL
                dataexport.tenant-scope=PUBLIC_CONTROL
                dataexport.timezone=America/Sao_Paulo
                dataexport.transport=GET_WITH_QUERY
                dataexport.timeout-seconds=5
                dataexport.retry.max-attempts=1
                dataexport.retry.initial-delay-ms=0
                dataexport.retry.max-delay-ms=0
                dataexport.max-response-bytes=1048576
                dataexport.resilience.minimum-request-interval-ms=2000
                dataexport.resilience.max-in-flight=1
                dataexport.resilience.max-requests-per-cycle=1
                dataexport.resilience.max-requests-per-workload=1
                dataexport.resilience.step-timeout-seconds=10
                dataexport.resilience.cycle-timeout-seconds=10
                dataexport.resilience.max-retry-after-ms=0
                dataexport.resilience.max-repartitions=0
                dataexport.resilience.circuit-failure-threshold=1
                dataexport.resilience.circuit-cooldown-seconds=1
                graphql.enabled=false
                shadow.audit.enabled=false
                """;
        Files.writeString(root.resolve(RuntimeDevWorkspaceBundle.CONFIGURATION), base);
        final var request = new LinkedHashMap<String, String>();
        for (final String key :
                List.of("invocationId", "executionId", "cycleId", "idempotencyKey")) {
            request.put(key, UUID.randomUUID().toString());
        }
        request.put("template", "COLETAS");
        request.put("mode", "BACKFILL");
        request.put("start", "2026-10-01T03:00:00Z");
        request.put("endExclusive", "2026-10-02T03:00:00Z");
        request.put("replayOf", "");
        request.put("businessStart", "2026-10-01");
        request.put("businessEnd", "2026-10-01");
        request.put("leaseSeconds", "30");
        request.put("pageSize", "1");
        request.put("maximumPages", "1");
        request.put("maximumRows", "1");
        request.put("maximumDistinctRoots", "1");
        request.put("qualityVersion", "public-control-v1");
        request.put("qualityFingerprint", "0".repeat(64));
        request.put("compatibilityVersion", "public-control-v1");
        new ObjectMapper()
                .writeValue(root.resolve(RuntimeDevWorkspaceBundle.REQUEST).toFile(), request);
        for (final String name :
                List.of(
                        RuntimeDevWorkspaceBundle.JAR,
                        RuntimeDevWorkspaceBundle.CONFIGURATION,
                        RuntimeDevWorkspaceBundle.REQUEST)) {
            entries.add(java.util.Map.of("path", name, "sha256", hash(root.resolve(name))));
        }
        new ObjectMapper()
                .writeValue(
                        root.resolve("manifest.json").toFile(),
                        java.util.Map.of(
                                "schema",
                                RuntimeDevWorkspaceBundle.SCHEMA,
                                "mode",
                                "PREPARE_ONLY",
                                "profile",
                                C78LocalSqlTarget.PROFILE,
                                "target",
                                "localhost:1433/ETL_SISTEMA_V2_SHADOW",
                                "leafSHA256",
                                C78LocalTlsScope.LEAF_SHA256,
                                "authorityAdmitted",
                                false,
                                "sourceAdmitted",
                                false,
                                "sqlAdmitted",
                                false,
                                "files",
                                entries));
        return root;
    }

    private Result child(final Path bundle, final boolean optIn) throws Exception {
        return child(bundle, optIn, List.of(), List.of());
    }

    private Result child(
            final Path bundle,
            final boolean optIn,
            final List<String> jvmExtra,
            final List<String> cliExtra)
            throws Exception {
        return child(bundle, optIn, jvmExtra, cliExtra, C78LocalSqlTarget.PROFILE);
    }

    private Result child(
            final Path bundle,
            final boolean optIn,
            final List<String> jvmExtra,
            final List<String> cliExtra,
            final String profile)
            throws Exception {
        final var arguments = new ArrayList<String>();
        arguments.add(Path.of(System.getProperty("java.home"), "bin", "java.exe").toString());
        arguments.addAll(List.of("-Xmx256m", "-Dfile.encoding=UTF-8"));
        arguments.addAll(jvmExtra);
        if (optIn) {
            arguments.addAll(
                    List.of(
                            "-Druntime.dev.workspace.enabled=true",
                            "-Dc78.local.shadow.enabled=true",
                            "-Dc78.local.shadow.sql-pin.enabled=true"));
        }
        arguments.addAll(
                List.of(
                        "-jar",
                        bundle.resolve(RuntimeDevWorkspaceBundle.JAR).toString(),
                        "run",
                        "--dev-bundle",
                        bundle.resolve("manifest.json").toString(),
                        "--profile",
                        profile));
        arguments.addAll(cliExtra);
        final var builder = new ProcessBuilder(arguments).directory(directory.toFile());
        builder.environment()
                .keySet()
                .removeIf(
                        key ->
                                key.toUpperCase(java.util.Locale.ROOT).startsWith("V2_")
                                        || List.of(
                                                        "JAVA_TOOL_OPTIONS",
                                                        "JDK_JAVA_OPTIONS",
                                                        "_JAVA_OPTIONS",
                                                        "CLASSPATH")
                                                .contains(key.toUpperCase(java.util.Locale.ROOT)));
        final var process = builder.start();
        final var readers = Executors.newFixedThreadPool(2);
        try {
            process.getOutputStream().close();
            final var out = readers.submit(capture(process.getInputStream()));
            final var err = readers.submit(capture(process.getErrorStream()));
            assertTrue(process.waitFor(15, TimeUnit.SECONDS), "OWN_DEV_MAIN_CHILD_DEADLINE");
            final var result =
                    new Result(
                            process.exitValue(),
                            out.get(3, TimeUnit.SECONDS),
                            err.get(3, TimeUnit.SECONDS));
            final String prefix = "child-" + process.pid();
            Files.writeString(
                    directory.resolve(prefix + ".stdout"),
                    result.stdout(),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            Files.writeString(
                    directory.resolve(prefix + ".stderr"),
                    result.stderr(),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            Files.writeString(
                    directory.resolve(prefix + ".json"),
                    new ObjectMapper()
                            .writeValueAsString(
                                    java.util.Map.of(
                                            "pid",
                                            process.pid(),
                                            "exit",
                                            result.exit(),
                                            "componentControlOnly",
                                            true)),
                    java.nio.file.StandardOpenOption.CREATE_NEW);
            return result;
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
            assertTrue(process.waitFor(3, TimeUnit.SECONDS), "OWN_DEV_MAIN_CHILD_CLOSE");
            process.getInputStream().close();
            process.getErrorStream().close();
            readers.shutdownNow();
            assertTrue(readers.awaitTermination(3, TimeUnit.SECONDS), "OWN_DEV_MAIN_READERS_CLOSE");
        }
    }

    private static Callable<String> capture(final InputStream stream) {
        return () -> {
            try (stream;
                    var bytes = new ByteArrayOutputStream()) {
                final byte[] buffer = new byte[4096];
                int count;
                boolean limited = false;
                while ((count = stream.read(buffer)) != -1) {
                    final int remaining = Math.max(0, 65_536 - bytes.size());
                    bytes.write(buffer, 0, Math.min(remaining, count));
                    limited |= count > remaining;
                }
                assertFalse(limited, "OWN_DEV_MAIN_OUTPUT_LIMIT");
                return bytes.toString(StandardCharsets.UTF_8);
            }
        };
    }

    private static String hash(final Path file) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
    }

    private record Result(int exit, String stdout, String stderr) {}
}
