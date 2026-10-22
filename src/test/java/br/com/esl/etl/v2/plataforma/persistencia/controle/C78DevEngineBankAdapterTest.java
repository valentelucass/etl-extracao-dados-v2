package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.MissingGate;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Refusal;
import br.com.esl.etl.v2.bootstrap.RuntimeDevEngineBankBinding.Scope;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.configuracao.C78DevPeerProof;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import br.com.esl.etl.v2.plataforma.configuracao.C78LocalTlsScope;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeEnvironment;
import br.com.esl.etl.v2.plataforma.configuracao.ShadowStorageProperties;
import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimePlanningRequest;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWindowStrategy;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadDefinition;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadId;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * Synthetic controls test lifecycle only; no authority, source, SQL or real provider is exercised.
 */
class C78DevEngineBankAdapterTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String HASH = "a".repeat(64);

    static Scope scope() {
        final var id = new RuntimeWorkloadId("coletas");
        final var hash = new ImmutableFingerprint("synthetic-v1", HASH);
        final Instant now = Instant.parse("2024-01-01T00:00:00Z");
        final var definition =
                new RuntimeWorkloadDefinition(
                        id,
                        "DATA_EXPORT",
                        "synthetic-source",
                        "synthetic-tenant",
                        "coletas",
                        hash,
                        hash,
                        Duration.ofSeconds(30));
        final var request =
                new RuntimeExecutionRequest(
                        UUID.randomUUID(),
                        id,
                        ExecutionMode.BACKFILL,
                        RuntimeWindowStrategy.INTERVAL,
                        now,
                        now.plusSeconds(60),
                        "synthetic-idempotency");
        final var plan =
                RuntimeWorkloadRegistry.of(definition)
                        .plan(
                                new RuntimePlanningRequest(
                                        UUID.randomUUID(), "LOCAL_SHADOW", hash, now, request));
        final var target = C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE);
        final var configuration =
                new RuntimeConfiguration(
                        RuntimeEnvironment.LOCAL_SHADOW,
                        ZoneId.of("America/Sao_Paulo"),
                        Clock.systemUTC(),
                        Optional.empty(),
                        Optional.empty(),
                        ShadowStorageProperties.enabledC78Local(target));
        return new Scope(
                configuration,
                RuntimeAuthorizationScope.from(UUID.randomUUID(), RuntimeAction.RUN, plan),
                target,
                HASH,
                HASH,
                HASH,
                HASH,
                DataExportTemplate.COLETAS,
                1,
                new DataExportExtractionLimits(1, 10, 1));
    }

    @Test
    void descriptorsAndFakeParentCannotSupplyIndependentAuthority() throws Exception {
        final Scope scope = scope();
        try (var absent = new C78DevEngineBankAdapter().prepare(scope)) {
            assertEquals(MissingGate.ADMISSION, assertThrows(Refusal.class, absent::verify).gate());
            assertEquals(MissingGate.ADMISSION, assertThrows(Refusal.class, absent::open).gate());
        }
        final Fixture fixture = new Fixture(scope);
        try (var admission =
                new C78DevEngineBankAdapter(fixture.path, fixture.sha()).prepare(scope)) {
            assertEquals(
                    MissingGate.ADMISSION, assertThrows(Refusal.class, admission::verify).gate());
            assertFalse(Files.exists(fixture.claim()));
        }
    }

    @Test
    void refusedIndependentQuotaNeverReadsSecretOrOpensConnector() throws Exception {
        final var calls = new AtomicInteger();
        final Scope scope = scope();
        final var quota =
                new SyntheticAdmission() {
                    @Override
                    public void verify() throws SQLException {
                        throw new Refusal(MissingGate.SOURCE_QUOTA);
                    }
                };
        final var adapter =
                new C78DevEngineBankAdapter(
                        Path.of("missing"),
                        HASH,
                        quota,
                        () -> {
                            calls.incrementAndGet();
                            return key -> "synthetic";
                        },
                        (s, p, n) -> {
                            calls.incrementAndGet();
                            throw new AssertionError();
                        });
        try (var admission = adapter.prepare(scope)) {
            assertEquals(
                    MissingGate.SOURCE_QUOTA, assertThrows(Refusal.class, admission::open).gate());
        }
        assertEquals(0, calls.get());
    }

    @Test
    void rawLimitsRejectStringsFractionsAndOverflow() throws Exception {
        for (final String field :
                List.of(
                        "connections",
                        "statements",
                        "querySeconds",
                        "unitSeconds",
                        "HTTP",
                        "rows",
                        "commit",
                        "promotion",
                        "retry")) {
            for (int variant = 0; variant < 3; variant++) {
                final Fixture f = new Fixture(scope());
                final var limits = (ObjectNode) f.dispatch.path("limits");
                if (variant == 0) {
                    limits.put(field, "1");
                }
                if (variant == 1) {
                    limits.put(field, 1.5);
                }
                if (variant == 2) {
                    limits.put(field, Long.MAX_VALUE);
                }
                f.save();
                try (var control = f.control()) {
                    assertEquals(
                            MissingGate.BUDGET,
                            assertThrows(Refusal.class, control::verify).gate());
                }
            }
        }
    }

    @Test
    void rawParentPidRejectsCoercions() throws Exception {
        for (int variant = 0; variant < 3; variant++) {
            final Fixture f = new Fixture(scope());
            if (variant == 0) {
                f.dispatch.put("parentPID", "123");
            }
            if (variant == 1) {
                f.dispatch.put("parentPID", 1.5);
            }
            if (variant == 2) {
                f.dispatch.put(
                        "parentPID", new java.math.BigInteger("999999999999999999999999999"));
            }
            f.save();
            try (var control = f.control()) {
                assertEquals(
                        MissingGate.ADMISSION, assertThrows(Refusal.class, control::verify).gate());
            }
        }
    }

    @Test
    void currentSchemaDriftRefusesPhysicalContract() throws Exception {
        final Fixture f = new Fixture(scope());
        final ObjectNode contracts =
                JSON.createObjectNode().put("schema", "BANCO_CATALOG39_OFFLINE_COMPARISON_V1");
        contracts.putArray("columns").addObject().put("status", "REVIEW_REQUIRED");
        contracts
                .putArray("javaCalls")
                .addObject()
                .put("reportCovered", true)
                .put("arityMatches", true);
        f.reference("physicalContract", contracts);
        f.save();
        try (var control = f.control()) {
            assertEquals(
                    MissingGate.PHYSICAL_CONTRACT,
                    assertThrows(Refusal.class, control::verify).gate());
        }
    }

    @Test
    void supplierQuotaFieldsAreNotAuthorityAndSourceRefusalIsIndependent() throws Exception {
        final Fixture f = new Fixture(scope());
        f.dispatch.remove("sourceQuota");
        f.dispatch.remove("sourceCaller");
        f.save();
        try (var control = f.control()) {
            // Local descriptor inspection is not a source permit, with or without invented fields.
            control.verify();
        }
        final var providers = new AtomicInteger();
        final var openings = new AtomicInteger();
        final var sourceUnknown =
                new SyntheticAdmission() {
                    @Override
                    public void verify() throws SQLException {
                        throw C78DevEngineAdmissionProducer.missing(
                                MissingGate.SOURCE_QUOTA, "CURRENT_OPERATIONAL_SCOPE_NOT_PROVED");
                    }
                };
        try (var admission =
                f.adapter(sourceUnknown, providers, openings, new SyntheticJdbc())
                        .prepare(f.scope)) {
            assertEquals(
                    MissingGate.SOURCE_QUOTA,
                    assertThrows(Refusal.class, admission::verify).gate());
            assertEquals(0, providers.get());
            assertEquals(0, openings.get());
        }
    }

    @Test
    void boundedReaderRejectsOversizeBeforeDigestAndLeaseDeniesMutation() throws Exception {
        final Path directory = output().resolve("bounded-" + UUID.randomUUID());
        Files.createDirectories(directory);
        final Path file = directory.resolve("input.json");
        Files.write(file, new byte[2_000_001]);
        final Method pinned =
                C78DevEngineDispatch.class.getDeclaredMethod(
                        "pinned", FileChannel.class, String.class);
        pinned.setAccessible(true);
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            final var failure =
                    assertThrows(
                            InvocationTargetException.class,
                            () -> pinned.invoke(null, channel, HASH));
            assertTrue(failure.getCause() instanceof IOException);
            assertEquals("DEV_ENGINE_INPUT_LIMIT", failure.getCause().getMessage());
        }
        final Method lease = C78DevEngineDispatch.class.getDeclaredMethod("lease", Path.class);
        lease.setAccessible(true);
        try (var channel = (FileChannel) lease.invoke(null, file)) {
            assertThrows(IOException.class, () -> Files.writeString(file, "mutated"));
            final Path moved = directory.resolveSibling(directory.getFileName() + "-moved");
            assertTrue(
                    directory
                            .toAbsolutePath()
                            .normalize()
                            .startsWith(output().toAbsolutePath().normalize()));
            assertTrue(
                    moved.toAbsolutePath()
                            .normalize()
                            .startsWith(output().toAbsolutePath().normalize()));
            assertThrows(IOException.class, () -> Files.move(directory, moved));
            assertTrue(channel.isOpen());
        }
        Files.writeString(file, "owned fixture after lease closure");
    }

    @Test
    void verifyAfterOpenRechecksWithoutReopeningAndCommitIsRefused() throws Exception {
        final Fixture f = new Fixture(scope());
        final var independent = new SyntheticAdmission();
        final var providers = new AtomicInteger();
        final var openings = new AtomicInteger();
        final var jdbc = new SyntheticJdbc();
        final var original = System.in;
        final var out = System.out;
        System.setIn(f.frames());
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        try (var admission = f.adapter(independent, providers, openings, jdbc).prepare(f.scope)) {
            admission.verify();
            try (var session = admission.open()) {
                final int verified = independent.verifications;
                admission.verify();
                admission.verify();
                assertTrue(independent.verifications >= verified + 2);
                assertEquals(1, providers.get());
                assertEquals(1, openings.get());
                assertThrows(Refusal.class, admission::open);
                try (var borrower = session.dataSource().getConnection()) {
                    assertThrows(SQLException.class, borrower::commit);
                    assertThrows(SQLException.class, borrower::rollback);
                    assertThrows(SQLException.class, () -> borrower.setAutoCommit(true));
                    assertThrows(SQLException.class, () -> borrower.unwrap(Connection.class));
                }
                assertEquals(0, jdbc.commits);
            }
        } finally {
            System.setIn(original);
            System.setOut(out);
        }
        assertEquals(1, jdbc.rollbacks);
        assertEquals(1, jdbc.closes);
    }

    @Test
    void providerRuntimeCauseIsPreservedWithoutOpeningConnector() throws Exception {
        final Fixture f = new Fixture(scope());
        final var cause = new IllegalStateException("synthetic-provider-failure");
        final var original = System.in;
        final var out = System.out;
        System.setIn(f.frames());
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        try (var admission =
                new C78DevEngineBankAdapter(
                                f.path,
                                f.sha(),
                                new SyntheticAdmission(),
                                () -> {
                                    throw cause;
                                },
                                (s, p, n) -> {
                                    throw new AssertionError();
                                })
                        .prepare(f.scope)) {
            assertSame(cause, assertThrows(SQLException.class, admission::open).getCause());
            assertThrows(Refusal.class, admission::verify);
            assertThrows(Refusal.class, admission::open);
        } finally {
            System.setIn(original);
            System.setOut(out);
        }
    }

    @Test
    void cleanupPreservesRollbackCloseAndIndependentFailures() throws Exception {
        final Fixture f = new Fixture(scope());
        final var jdbc = new SyntheticJdbc();
        jdbc.failClose = true;
        final var independent =
                new SyntheticAdmission() {
                    @Override
                    public void close() throws SQLException {
                        throw new SQLException("synthetic-independent-close");
                    }
                };
        final var original = System.in;
        final var out = System.out;
        System.setIn(f.frames());
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        final var admission =
                f.adapter(independent, new AtomicInteger(), new AtomicInteger(), jdbc)
                        .prepare(f.scope);
        try {
            admission.open();
            final SQLException failure = assertThrows(SQLException.class, admission::close);
            assertEquals("synthetic-rollback", failure.getMessage());
            assertEquals(2, failure.getSuppressed().length);
            admission.close();
            assertEquals(1, jdbc.rollbacks);
            assertEquals(1, jdbc.closes);
        } finally {
            System.setIn(original);
            System.setOut(out);
        }
    }

    static Path output() throws IOException {
        final Path root = Path.of(Objects.requireNonNull(System.getProperty("bank.test.output")));
        Files.createDirectories(root);
        return root;
    }

    static class SyntheticAdmission implements RuntimeDevEngineBankBinding.Admission {
        int verifications;

        @Override
        public void verify() throws SQLException {
            verifications++;
        }

        @Override
        public RuntimeDevEngineBankBinding.Session open() {
            throw new AssertionError("not an authority or a real backend");
        }

        @Override
        public void close() throws SQLException {}
    }

    static class Fixture {
        final Scope scope;
        final Path unit;
        final Path path;
        final ObjectNode dispatch = JSON.createObjectNode();
        final ObjectNode quota = JSON.createObjectNode();

        Fixture(final Scope scope) throws Exception {
            this.scope = scope;
            unit = output().resolve("fixture-" + UUID.randomUUID());
            Files.createDirectories(unit.resolve("root"));
            path = unit.resolve("root/engine-dispatch01.json");
            final String id = "C78ENG01-" + UUID.randomUUID().toString().replace("-", "");
            Files.createDirectories(unit.resolve("banco01/occurrences/" + id));
            final var parent = ProcessHandle.current().parent().orElseThrow();
            final Instant now = Instant.now();
            dispatch.put("schema", "V2_DEV_C78_ENGINE_DISPATCH_V1")
                    .put("operation", "ENGINE_ROLLBACK_NO_PROMOTION")
                    .put("dispatchId", id)
                    .put("nonce", HASH)
                    .put("invocationId", scope.authorization().invocationId().toString())
                    .put("bundleManifestSHA256", HASH)
                    .put("configurationSHA256", HASH)
                    .put("requestSHA256", HASH)
                    .put("planSHA256", HASH)
                    .put("target", C78LocalSqlTarget.URL)
                    .put("leafSHA256", C78LocalTlsScope.LEAF_SHA256)
                    .put("parentPID", parent.pid())
                    .put("parentStartUtc", parent.info().startInstant().orElseThrow().toString())
                    .put("issuedUtc", now.minusMillis(10).toString())
                    .put("deadlineUtc", now.plusSeconds(40).toString())
                    .put(
                            "occurrenceDirectory",
                            unit.resolve("banco01/occurrences/" + id).toString());
            final var limits = dispatch.putObject("limits");
            limits.put("connections", 1)
                    .put("statements", 100)
                    .put("querySeconds", 10)
                    .put("unitSeconds", 60)
                    .put("HTTP", 2)
                    .put("rows", 10)
                    .put("commit", 0)
                    .put("promotion", 0)
                    .put("retry", 0);
            reference(
                    "rootAuthority",
                    JSON.createObjectNode()
                            .put("owner", "Root")
                            .put("operation", "ENGINE_ROLLBACK_NO_PROMOTION")
                            .put("dispatchId", id)
                            .put("authorizationReference", "SYNTHETIC_TEST_NOT_AUTHORITY"));
            reference(
                    "issuerClaim",
                    JSON.createObjectNode()
                            .put("dispatchId", id)
                            .put(
                                    "authoritySHA256",
                                    dispatch.path("rootAuthority").path("sha256").asText())
                            .put("state", "CONSUMED_BEFORE_DISPATCH_NO_RETRY"));
            final var reservation = JSON.createObjectNode().put("dispatchId", id);
            reservation.set("limits", limits.deepCopy());
            reference("reservation", reservation);
            final var contracts =
                    JSON.createObjectNode().put("schema", "BANCO_CATALOG39_OFFLINE_COMPARISON_V1");
            contracts.putArray("columns").addObject().put("status", "CONFIRMED");
            contracts
                    .putArray("javaCalls")
                    .addObject()
                    .put("reportCovered", true)
                    .put("arityMatches", true);
            reference("physicalContract", contracts);
            final var writer =
                    dispatch.putObject("writerProtectionPlan")
                            .put("mechanism", "OWNED_TRANSACTION_TABLE_X_READBACK");
            final var tables = writer.putArray("tables");
            C78DevEngineSession.TABLES.stream().sorted().forEach(tables::add);
            quota.put("status", "SOURCE_QUOTA_ADMITTED")
                    .put("template", 6908)
                    .put("remainingRequests", 2)
                    .put("coverageAndConflictStatus", "RECONCILED_NO_CONFLICT")
                    .put("observedAtUTC", now.minusSeconds(1).toString())
                    .put("acquiredAtUTC", now.toString())
                    .put("validFromUTC", now.minusSeconds(2).toString())
                    .put("validUntilUTC", now.plusSeconds(50).toString())
                    .put("coverageThroughUTC", now.toString());
            for (final String field :
                    List.of(
                            "existingChannelReference",
                            "accessAuthorityReference",
                            "originalObservationReference",
                            "authorizedIndependentReader",
                            "originalAcquisitionMethod",
                            "budgetAndExecutionAuthorityReferences")) {
                quota.put(field, "SYNTHETIC_NOT_AUTHORITY");
            }
            quota.put("operationBinding", id);
            final var caller = quota.putObject("callerBindings");
            for (final String field :
                    List.of(
                            "origin",
                            "company",
                            "integration",
                            "principal",
                            "effectiveEgressIp",
                            "quotaBucket")) {
                caller.put(field, "synthetic-" + field);
            }
            dispatch.set("sourceCaller", caller.deepCopy());
            reference("sourceQuota", quota);
            dispatch.putObject("nativeImage")
                    .put("pid", 77)
                    .put("rawCreation", "134360443019517054")
                    .put("sha256", HASH);
            save();
        }

        void reference(final String name, final ObjectNode value) throws Exception {
            final Path ref = unit.resolve("root/" + name + ".json");
            Files.writeString(ref, JSON.writeValueAsString(value));
            dispatch.putObject(name)
                    .put("path", ref.toString())
                    .put("sha256", hash(Files.readAllBytes(ref)));
        }

        void save() throws Exception {
            Files.writeString(path, JSON.writeValueAsString(dispatch));
        }

        String sha() throws Exception {
            return hash(Files.readAllBytes(path));
        }

        Path claim() {
            return Path.of(dispatch.path("occurrenceDirectory").asText())
                    .resolve("runtime-engine-claim.json");
        }

        C78DevEngineDispatch control() throws Exception {
            return C78DevEngineDispatch.read(path, sha(), scope);
        }

        C78DevEngineBankAdapter adapter(
                final SyntheticAdmission independent,
                final AtomicInteger provider,
                final AtomicInteger openings,
                final SyntheticJdbc jdbc)
                throws Exception {
            return new C78DevEngineBankAdapter(
                    path,
                    sha(),
                    independent,
                    () -> {
                        provider.incrementAndGet();
                        return key -> "synthetic";
                    },
                    (s, p, n) -> {
                        openings.incrementAndGet();
                        try {
                            n.verify(
                                    new C78DevPeerProof.Tuple(
                                            "127.0.0.1", 62006, "127.0.0.1", 1433));
                        } catch (IOException failure) {
                            throw new SQLException("synthetic native control", failure);
                        }
                        return jdbc.connection();
                    });
        }

        InputStream frames() {
            return new InputStream() {
                byte[] bytes = new byte[0];
                int index;
                int frame;

                @Override
                public int read() throws IOException {
                    if (index == bytes.length) {
                        if (frame == 2) {
                            return -1;
                        }
                        final var value =
                                JSON.createObjectNode()
                                        .put(
                                                "kind",
                                                frame++ == 0
                                                        ? "ENGINE_START_PERMIT"
                                                        : "ENGINE_PRELOGIN_PEER_PERMIT")
                                        .put("dispatchId", dispatch.path("dispatchId").asText())
                                        .put("dispatchSHA256", uncheckedSha())
                                        .put("nonce", HASH)
                                        .put("childPID", ProcessHandle.current().pid())
                                        .put("parentPID", dispatch.path("parentPID").longValue())
                                        .put("invocationId", dispatch.path("invocationId").asText())
                                        .put(
                                                "rootAuthoritySHA256",
                                                dispatch.path("rootAuthority")
                                                        .path("sha256")
                                                        .asText());
                        if (frame == 2) {
                            value.set(
                                    "tuple",
                                    JSON.valueToTree(
                                            new C78DevPeerProof.Tuple(
                                                    "127.0.0.1", 62006, "127.0.0.1", 1433)));
                            value.putObject("nativeProof")
                                    .put("pid", 77)
                                    .put("service", "MSSQLSERVER")
                                    .put("version", "16.0.1000.6")
                                    .put("sha256", HASH)
                                    .put("rawCreationPRE", "134360443019517054")
                                    .put("rawCreationPOST", "134360443019517054")
                                    .put("loadedImage", true)
                                    .put("microsoftCacheOnly", true)
                                    .put("reverseTupleMatches", 1)
                                    .put("evidenceSHA256", HASH)
                                    .put("observedUtc", Instant.now().toString());
                        }
                        bytes =
                                (JSON.writeValueAsString(value) + "\n")
                                        .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                        index = 0;
                    }
                    return bytes[index++] & 255;
                }
            };
        }

        String uncheckedSha() throws IOException {
            try {
                return sha();
            } catch (Exception failure) {
                throw new IOException(failure);
            }
        }

        static String hash(final byte[] raw) throws Exception {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        }
    }

    static class SyntheticJdbc {
        int commits, rollbacks, closes;
        boolean failClose;

        Connection connection() {
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "createStatement" -> statement();
                                        case "getAutoCommit" -> false;
                                        case "commit" -> {
                                            commits++;
                                            yield null;
                                        }
                                        case "isClosed" -> closes > 0;
                                        case "rollback" -> {
                                            rollbacks++;
                                            if (failClose) {
                                                throw new SQLException("synthetic-rollback");
                                            }
                                            yield null;
                                        }
                                        case "close" -> {
                                            closes++;
                                            if (failClose) {
                                                throw new SQLException("synthetic-close");
                                            }
                                            yield null;
                                        }
                                        default -> defaultValue(m.getReturnType());
                                    });
        }

        Statement statement() {
            return (Statement)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Statement.class},
                            (p, m, a) ->
                                    m.getName().equals("executeQuery")
                                            ? rows((String) a[0])
                                            : defaultValue(m.getReturnType()));
        }

        ResultSet rows(final String sql) {
            final int[] next = {0};
            return (ResultSet)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {ResultSet.class},
                            (p, m, a) ->
                                    switch (m.getName()) {
                                        case "next" ->
                                                !sql.startsWith("SELECT TOP") && next[0]++ == 0;
                                        case "getInt" ->
                                                sql.startsWith("SELECT @@") && ((Integer) a[0]) == 3
                                                        ? C78DevEngineSession.TABLES.size()
                                                        : ((Integer) a[0]) == 7 ? 0 : 1;
                                        case "getLong" -> ((Integer) a[0]) == 5 ? 77L : 75L;
                                        case "getString" ->
                                                ((Integer) a[0]) == 2 ? "16.0.1000.6" : "SQL";
                                        default -> defaultValue(m.getReturnType());
                                    });
        }

        static Object defaultValue(final Class<?> type) {
            if (type == boolean.class) {
                return false;
            }
            if (type == int.class) {
                return 0;
            }
            if (type == long.class) {
                return 0L;
            }
            return null;
        }
    }
}
