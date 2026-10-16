package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.plataforma.configuracao.EnvironmentSecretProvider;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneCounts;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePage;
import br.com.esl.etl.v2.plataforma.controle.ControlPlanePageTerminality;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneStart;
import br.com.esl.etl.v2.plataforma.controle.ControlPlaneTransition;
import br.com.esl.etl.v2.plataforma.controle.ExecutionState;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportColetasContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttempt;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpGatewayBundle;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportMetadataField;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplateInfo;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportUnavailableException;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RuntimeBootstrapTestFixture;
import br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedRuntimeFixture;
import br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedSqlSession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.EslRequestGovernor;
import br.com.esl.etl.v2.plataforma.resiliencia.EslWorkload;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceBudgetExceededException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Coletas6908TestRetainedTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-05T18:00:00Z"), ZoneOffset.UTC);
    @TempDir Path directory;

    @Test
    void configuredGovernorEnforcesProviderTwoSecondIntervalWithoutRetry() {
        final var configuration = configuration();
        final var ticker = new AtomicLong();
        final var governor =
                new EslRequestGovernor(
                        configuration.resiliencePolicy(),
                        ticker::get,
                        delay -> ticker.addAndGet(delay.toNanos()));
        final var workload =
                governor.beginCycle(CancellationToken.none()).beginWorkload(EslWorkload.COLETAS);
        try (var info = workload.acquire()) {
            info.checkpoint();
            assertEquals(0, ticker.get());
        }
        try (var page = workload.acquire()) {
            page.checkpoint();
            assertEquals(Duration.ofSeconds(2).toNanos(), ticker.get());
        }
        assertThrows(ResilienceBudgetExceededException.class, workload::acquire);
        assertEquals(1, configuration.settings().retryPolicy().maxAttempts());
    }

    @Test
    void requestIsClosedAndBindsEveryLimitBeforeAnyFactory() throws Exception {
        final var first = parse(document());
        final var changed = document().put("executionId", UUID.randomUUID().toString());
        assertNotEquals(first.fingerprint, parse(changed).fingerprint);
        for (final String key :
                List.of(
                        "retainData",
                        "per",
                        "page",
                        "maximumRows",
                        "maximumHttpCalls",
                        "maximumResponseBytes",
                        "requestTimeoutSeconds",
                        "deadlineSeconds",
                        "sourceInstance",
                        "tenantScope",
                        "businessDate")) {
            assertThrows(
                    IllegalArgumentException.class, () -> parse(document().put(key, "invalid")));
        }
        assertThrows(
                IllegalArgumentException.class, () -> parse(document().put("newFlag", "true")));
        assertThrows(IllegalArgumentException.class, () -> parse(document().put("per", 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> Coletas6908TestRetainedRequest.parse(new byte[8193]));
        final String duplicate = document().toString().replaceFirst("\\{", "{\"per\":\"1\",");
        assertThrows(
                java.io.IOException.class,
                () ->
                        Coletas6908TestRetainedRequest.parse(
                                duplicate.getBytes(StandardCharsets.UTF_8)));
        assertThrows(
                java.io.IOException.class,
                () ->
                        Coletas6908TestRetainedRequest.parse(
                                (document() + "{}").getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void smallCaptureRetainsNoTerminalClaimAndUsesRealBoundConfiguration() throws Exception {
        final var events = new ArrayList<Object>();
        final var batches = new ArrayList<ColetaStageBatch>();
        final var request = parse(document());
        final var config = configuration();
        final var captured = source(List.of(row()), false, null);
        final var result =
                Coletas6908TestRetainedRunner.captureAndStage(
                        request,
                        config,
                        control(events),
                        batches::add,
                        CLOCK,
                        () -> {},
                        () -> captured);
        assertEquals(1, result.records().size());
        assertEquals(1, batches.size());
        final var start =
                events.stream()
                        .filter(ControlPlaneStart.class::isInstance)
                        .map(ControlPlaneStart.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                Coletas6908TestRetainedSource.binding(request, config).configurationFingerprint(),
                start.configuration());
        assertEquals("LOCAL_SHADOW", start.partition().environment());
        final var page =
                events.stream()
                        .filter(ControlPlanePage.class::isInstance)
                        .map(ControlPlanePage.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals(ControlPlanePageTerminality.NONE, page.terminality());
        final var counts =
                events.stream()
                        .filter(ControlPlaneCounts.class::isInstance)
                        .map(ControlPlaneCounts.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals("TEST_CAPTURE", counts.phase());
        final var terminal = (ControlPlaneTransition) events.get(events.size() - 1);
        assertEquals(ExecutionState.CANCELLED, terminal.nextState());
        assertEquals("TEST_PAGE_LIMIT", terminal.reasonCode());
        assertFalse(
                events.stream()
                        .filter(ControlPlaneTransition.class::isInstance)
                        .map(ControlPlaneTransition.class::cast)
                        .anyMatch(t -> t.nextState() == ExecutionState.EXTRACTED));
    }

    @Test
    void thousandPhysicalRowsAreTenBoundedBatchesWithOneRoot() throws Exception {
        final var events = new ArrayList<Object>();
        final var batches = new ArrayList<ColetaStageBatch>();
        Coletas6908TestRetainedRunner.captureAndStage(
                parse(document()),
                configuration(),
                control(events),
                batches::add,
                CLOCK,
                () -> {},
                () -> source(Collections.nCopies(1000, row()), false, null));
        assertEquals(10, batches.size());
        for (int index = 0; index < 10; index++) {
            assertEquals(index + 1, batches.get(index).batchNumber());
            assertEquals(100, batches.get(index).size());
            assertEquals(1, batches.get(index).recordAt(0).inputOrdinal());
            assertEquals(100, batches.get(index).recordAt(99).inputOrdinal());
        }
        final var counts =
                events.stream()
                        .filter(ControlPlaneCounts.class::isInstance)
                        .map(ControlPlaneCounts.class::cast)
                        .findFirst()
                        .orElseThrow();
        assertEquals(999, counts.duplicateRows());
        assertEquals(1, counts.validRows());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1001})
    void refusesEmptyAndExcessivePages(final int size) {
        assertThrows(
                RuntimeException.class,
                () -> source(Collections.nCopies(size, row()), false, null));
    }

    @Test
    void mappingAndIdentityFailuresNeverReachStagingOrTerminal() throws Exception {
        for (final List<JsonNode> rows :
                List.of(
                        List.<JsonNode>of(row(), row().put("id", 2)),
                        List.<JsonNode>of(row().putNull("id")),
                        List.<JsonNode>of(
                                row().put("id", new java.math.BigInteger("9".repeat(300)))))) {
            final var events = new ArrayList<Object>();
            final var batches = new ArrayList<ColetaStageBatch>();
            final var request = parse(document());
            assertThrows(
                    RuntimeException.class,
                    () ->
                            Coletas6908TestRetainedRunner.captureAndStage(
                                    request,
                                    configuration(),
                                    control(events),
                                    batches::add,
                                    CLOCK,
                                    () -> {},
                                    () -> source(rows, false, null)));
            assertTrue(batches.isEmpty());
            assertEquals(
                    ExecutionState.EXTRACTING,
                    ((ControlPlaneTransition) events.get(events.size() - 1)).nextState());
        }
    }

    @Test
    void metadataDriftStopsBeforeDataAndHttpFailureIsNotRetried() {
        assertThrows(RuntimeException.class, () -> source(List.of(row()), true, null));
        assertThrows(
                Coletas6908TestRetainedSource.SourceFailure.class,
                () ->
                        source(
                                List.of(row()),
                                false,
                                new DataExportUnavailableException(
                                        6908, 429, Optional.of("SYNTHETIC_SECRET_HEADER"))));
    }

    @Test
    void stagingFailureCannotWriteCancellationOrPretendRetention() throws Exception {
        final var events = new ArrayList<Object>();
        final var request = parse(document());
        assertThrows(
                IllegalStateException.class,
                () ->
                        Coletas6908TestRetainedRunner.captureAndStage(
                                request,
                                configuration(),
                                control(events),
                                batch -> {
                                    throw new IllegalStateException("SYNTHETIC_SQL_FAILURE");
                                },
                                CLOCK,
                                () -> {},
                                () -> source(List.of(row()), false, null)));
        assertTrue(events.get(events.size() - 1) instanceof ControlPlanePage);
    }

    @Test
    void attemptOrderAndThirdCallAreDeniedBeforeSend() {
        final var attempts = new Coletas6908TestRetainedSource.Attempts(() -> {});
        assertThrows(
                IllegalStateException.class,
                () -> attempts.beforeAttempt(new DataExportHttpAttempt(6389, "info")));
        assertThrows(
                IllegalStateException.class,
                () ->
                        attempts.beforeAttempt(
                                new DataExportHttpAttempt(6908, "data-GET_WITH_QUERY")));
        attempts.beforeAttempt(new DataExportHttpAttempt(6908, "info"));
        attempts.beforeAttempt(new DataExportHttpAttempt(6908, "data-GET_WITH_QUERY"));
        assertThrows(
                IllegalStateException.class,
                () -> attempts.beforeAttempt(new DataExportHttpAttempt(6908, "info")));
        assertEquals(2, attempts.info + attempts.data);
    }

    @Test
    void deadlineCombinesExplicitUtcAndMonotonicBudget() {
        final var tick = new AtomicLong();
        final var deadline =
                new Coletas6908TestRetainedMain.Deadline("2026-10-05T18:01:00Z", CLOCK, tick::get);
        deadline.run();
        tick.set(Duration.ofSeconds(60).toNanos());
        assertThrows(IllegalStateException.class, deadline::run);
        for (final String text :
                List.of(
                        "2026-10-05T18:01:01Z",
                        "2026-10-05T18:00:00Z",
                        "2026-10-05T18:01:00",
                        "10/05/2026")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new Coletas6908TestRetainedMain.Deadline(text, CLOCK, () -> 0));
        }
    }

    @Test
    void sourceIsStrictHttpsAndFactoryConstructionIsOffline() {
        for (final String url :
                List.of(
                        "http://fixture.invalid",
                        "https://u:p@fixture.invalid",
                        "https://fixture.invalid?q=1",
                        "https://fixture.invalid#fragment")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            Coletas6908TestRetainedSource.configuration(
                                    Map.of("V2_DATAEXPORT_BASE_URL", url)));
        }
        final var attempts = new Coletas6908TestRetainedSource.Attempts(() -> {});
        Coletas6908TestRetainedSource.gateways(
                configuration(),
                new EnvironmentSecretProvider(Map.of("V2_DATAEXPORT_TOKEN", "SYNTHETIC_TOKEN")),
                CLOCK,
                attempts,
                () -> {});
        assertEquals(0, attempts.info + attempts.data);
        assertEquals(1, configuration().settings().retryPolicy().maxAttempts());
        assertEquals(2, configuration().resiliencePolicy().maxRequestsPerCycle());
    }

    @Test
    void mainPreflightNeedsNoNetworkAndReservedRunRequiresExplicitAdmission() throws Exception {
        final Path path = directory.resolve("request.json");
        Files.writeString(path, document().toString());
        final var buffer = new ByteArrayOutputStream();
        try (var output = new PrintStream(buffer, true, StandardCharsets.UTF_8)) {
            assertEquals(
                    0,
                    Coletas6908TestRetainedMain.run(
                            new String[] {"--preflight", "--request", path.toString()},
                            environment(),
                            options(),
                            CLOCK,
                            output));
            assertTrue(
                    buffer.toString(StandardCharsets.UTF_8)
                            .contains("TEST_RETAINED_PREFLIGHT_ONLY"));
            buffer.reset();
            assertEquals(
                    1,
                    Coletas6908TestRetainedMain.run(
                            new String[] {"--run-reserved", "--request", path.toString()},
                            environment(),
                            options(),
                            CLOCK,
                            output));
            assertTrue(
                    buffer.toString(StandardCharsets.UTF_8).contains("TEST_RESERVATION_REQUIRED"));
            assertFalse(buffer.toString(StandardCharsets.UTF_8).contains("SYNTHETIC_PASSWORD"));
            assertFalse(buffer.toString(StandardCharsets.UTF_8).contains("fixture.invalid"));
        }
        final var options = options();
        options.remove("test.retained.local.enabled");
        assertThrows(
                IllegalArgumentException.class,
                () -> Coletas6908TestRetainedMain.requireOptions(options));
        final var tls = options();
        tls.setProperty("javax.net.ssl.trustStore", "SYNTHETIC_OVERRIDE");
        assertThrows(
                IllegalArgumentException.class,
                () -> Coletas6908TestRetainedMain.requireOptions(tls));
    }

    private static Coletas6908TestRetainedSource.Capture source(
            final List<JsonNode> rows, final boolean drift, final RuntimeException failure) {
        final var attempts = new Coletas6908TestRetainedSource.Attempts(() -> {});
        try {
            return Coletas6908TestRetainedSource.capture(
                    gateways(rows, drift, failure, attempts), CLOCK, attempts, () -> {});
        } catch (final RuntimeException error) {
            if (drift || failure != null) {
                assertEquals(0, attempts.data);
            }
            throw error;
        }
    }

    private static DataExportHttpGatewayBundle gateways(
            final List<JsonNode> rows,
            final boolean drift,
            final RuntimeException failure,
            final Coletas6908TestRetainedSource.Attempts attempts) {
        final var boundary =
                ContractResponsePathBoundary.forRuntime(
                        DataExportColetasContractCatalog.release(),
                        Coletas6908TestRetainedSource.policy());
        return new DataExportHttpGatewayBundle(
                request -> {
                    assertEquals(1, request.page());
                    assertEquals(1, request.pageSize());
                    attempts.beforeAttempt(new DataExportHttpAttempt(6908, "data-GET_WITH_QUERY"));
                    final var array = JSON.createArrayNode();
                    rows.forEach(array::add);
                    final var observation =
                            new DataExportContractAdapter(
                                            ContractObservationLimits.runtimeDefaults(), boundary)
                                    .response(array, DataExportResponseForm.ROOT_ARRAY, "/id");
                    return RuntimeBootstrapTestFixture.observedPage(
                            rows,
                            observation,
                            ContractObservationLimits.runtimeDefaults(),
                            boundary);
                },
                ignored -> {
                    attempts.beforeAttempt(new DataExportHttpAttempt(6908, "info"));
                    if (failure != null) {
                        throw failure;
                    }
                    return info(drift);
                });
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 1000})
    void completeRunnerUsesRealJdbcAdaptersAndBoundedOwnerSessionOffline(final int rows)
            throws Exception {
        final var fixture =
                new br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedRuntimeFixture(
                        rows);
        final var request = parse(document());
        final var receipt =
                Coletas6908TestRetainedRunner.execute(
                        request,
                        configuration(),
                        new EnvironmentSecretProvider(environment()),
                        CLOCK,
                        () -> {},
                        () -> fixture.open(request.executionId),
                        (config, secrets, clock, attempts, deadline) ->
                                gateways(Collections.nCopies(rows, row()), false, null, attempts));
        assertEquals(
                "CAPTURE_COMMITTED_PARTIAL_AWAITING_INDEPENDENT_READBACK", receipt.get("status"));
        assertEquals(true, receipt.get("committed"));
        assertEquals(true, receipt.get("sessionPostCommitReadback"));
        assertEquals(false, receipt.get("independentPostCommitReadback"));
        assertEquals(true, receipt.get("closeObserved"));
        assertEquals(1, fixture.commits());
        assertEquals(1, fixture.closes());
        assertEquals(0, fixture.rollbacks());
        assertEquals(2, receipt.get("callsAttempted"));
        assertEquals((long) Math.ceil(rows / 100.0), receipt.get("suppressedCommits"));
        assertTrue(((Number) receipt.get("sqlExecutions")).longValue() <= 1100);
        if (rows == 1000) {
            assertEquals(1066L, receipt.get("sqlExecutions"));
        }
    }

    @Test
    void callerDeadlineExpiresDuringFinalReadbackWithoutCompensatingKnownCommit() throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final var tick = new AtomicLong();
        fixture.onReadbackClose =
                post -> {
                    if (post) {
                        tick.set(Duration.ofSeconds(60).toNanos());
                    }
                };
        final var deadline =
                new Coletas6908TestRetainedMain.Deadline("2026-10-05T18:01:00Z", CLOCK, tick::get);
        assertCallerRefusal(fixture, deadline, true);
    }

    @Test
    void callerAdmissionRevokedDuringFinalReadbackRefusesReceiptWithKnownCommit() throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final boolean[] admitted = {true};
        fixture.onReadbackClose =
                post -> {
                    if (post) {
                        admitted[0] = false;
                    }
                };
        assertCallerRefusal(
                fixture,
                () -> {
                    if (!admitted[0]) {
                        throw new IllegalStateException("SYNTHETIC_ADMISSION_REVOKED");
                    }
                },
                true);
    }

    @Test
    void callerAdmissionRevokedDuringPrecommitReadbackNeverCommits() throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final boolean[] admitted = {true};
        fixture.onReadbackClose = post -> admitted[0] = false;
        assertCallerRefusal(
                fixture,
                () -> {
                    if (!admitted[0]) {
                        throw new IllegalStateException("SYNTHETIC_ADMISSION_REVOKED");
                    }
                },
                false);
    }

    @Test
    void validCallerAdmissionReachesReceiptAfterClosedReadbacks() throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final var receipt = executeCallerFixture(fixture, () -> {});
        assertEquals(
                "CAPTURE_COMMITTED_PARTIAL_AWAITING_INDEPENDENT_READBACK", receipt.get("status"));
        assertEquals(true, receipt.get("sessionPostCommitReadback"));
        assertEquals(true, receipt.get("committed"));
        assertEquals(false, receipt.get("commitUncertain"));
        assertEquals(true, receipt.get("closeObserved"));
        assertEquals(2, fixture.readbackCloses);
        assertEquals(1, fixture.jdbc.commits());
        assertEquals(0, fixture.jdbc.rollbacks());
        assertEquals(1, fixture.jdbc.closes());
    }

    @Test
    void callerDeadlineExpiresDuringOwnPhysicalCloseRefusesSuccessAndPreservesReadback()
            throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final var tick = new AtomicLong();
        fixture.onPhysicalClose =
                () -> {
                    assertEquals(2, fixture.readbackCloses);
                    tick.set(Duration.ofSeconds(60).toNanos());
                };
        final var deadline =
                new Coletas6908TestRetainedMain.Deadline("2026-10-05T18:01:00Z", CLOCK, tick::get);
        final var receipt = executeCallerFixture(fixture, deadline);
        assertEquals("STOP_NO_RETRY", receipt.get("status"));
        assertEquals("TEST_DEADLINE_EXPIRED", receipt.get("failureCode"));
        assertOwnCloseFacts(fixture, receipt);
    }

    @Test
    void callerAdmissionRevokedDuringOwnPhysicalCloseRefusesSuccessAndPreservesReadback()
            throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final boolean[] admitted = {true};
        fixture.onPhysicalClose =
                () -> {
                    assertEquals(2, fixture.readbackCloses);
                    admitted[0] = false;
                };
        final var receipt =
                executeCallerFixture(
                        fixture,
                        () -> {
                            if (!admitted[0]) {
                                throw new IllegalStateException("SYNTHETIC_ADMISSION_REVOKED");
                            }
                        });
        assertEquals("STOP_NO_RETRY", receipt.get("status"));
        assertEquals("SOURCE_OR_SQL_REFUSED", receipt.get("failureCode"));
        assertOwnCloseFacts(fixture, receipt);
    }

    @Test
    void validCallerDeadlineAfterOwnPhysicalCloseAllowsPartialReceipt() throws Exception {
        final var fixture = new CallerAdmissionFixture();
        final var tick = new AtomicLong();
        fixture.onPhysicalClose =
                () -> {
                    assertEquals(2, fixture.readbackCloses);
                    tick.set(Duration.ofSeconds(59).toNanos());
                };
        final var deadline =
                new Coletas6908TestRetainedMain.Deadline("2026-10-05T18:01:00Z", CLOCK, tick::get);
        final var receipt = executeCallerFixture(fixture, deadline);
        assertEquals(
                "CAPTURE_COMMITTED_PARTIAL_AWAITING_INDEPENDENT_READBACK", receipt.get("status"));
        assertOwnCloseFacts(fixture, receipt);
    }

    private static void assertOwnCloseFacts(
            final CallerAdmissionFixture fixture, final Map<String, Object> receipt) {
        assertEquals(true, receipt.get("committed"));
        assertEquals(false, receipt.get("commitUncertain"));
        assertEquals(true, receipt.get("sessionPostCommitReadback"));
        assertEquals(false, receipt.get("independentPostCommitReadback"));
        assertEquals(true, receipt.get("closeObserved"));
        assertEquals(2, fixture.readbackCloses);
        assertEquals(1, fixture.jdbc.commits());
        assertEquals(0, fixture.jdbc.rollbacks());
        assertEquals(1, fixture.jdbc.closes());
        assertEquals(2, receipt.get("callsAttempted"));
    }

    private static void assertCallerRefusal(
            final CallerAdmissionFixture fixture, final Runnable admission, final boolean committed)
            throws Exception {
        final var receipt = executeCallerFixture(fixture, admission);
        assertEquals("STOP_NO_RETRY", receipt.get("status"));
        assertEquals("ADMISSION_REVOKED", receipt.get("failureCode"));
        assertEquals(false, receipt.get("sessionPostCommitReadback"));
        assertEquals(false, receipt.get("independentPostCommitReadback"));
        assertEquals(committed, receipt.get("committed"));
        assertEquals(false, receipt.get("commitUncertain"));
        assertEquals(true, receipt.get("closeObserved"));
        assertEquals(committed ? 1 : 0, fixture.jdbc.commits());
        assertEquals(committed ? 0 : 1, fixture.jdbc.rollbacks());
        assertEquals(1, fixture.jdbc.closes());
        assertEquals(2, receipt.get("callsAttempted"));
    }

    private static Map<String, Object> executeCallerFixture(
            final CallerAdmissionFixture fixture, final Runnable admission) throws Exception {
        final var request = parse(document());
        return Coletas6908TestRetainedRunner.execute(
                request,
                configuration(),
                new EnvironmentSecretProvider(environment()),
                CLOCK,
                admission,
                () -> fixture.open(request.executionId),
                (config, secrets, clock, attempts, deadline) ->
                        gateways(List.of(row()), false, null, attempts));
    }

    /**
     * Wrap only the offline fixture's readback and physical close; session and JDBC adapters remain
     * real.
     */
    private static final class CallerAdmissionFixture {
        final TestRetainedRuntimeFixture jdbc = new TestRetainedRuntimeFixture(1);
        final Connection connection;
        Consumer<Boolean> onReadbackClose = post -> {};
        Runnable onPhysicalClose = () -> {};
        int readbackCloses;

        CallerAdmissionFixture() throws ReflectiveOperationException {
            final var field = TestRetainedRuntimeFixture.class.getDeclaredField("fake");
            field.setAccessible(true);
            final var fake = field.get(jdbc);
            final var physical = fake.getClass().getDeclaredField("connection");
            physical.setAccessible(true);
            final var delegated = (Connection) physical.get(fake);
            final var contract =
                    Class.forName(
                            "br.com.esl.etl.v2.plataforma.persistencia.controle.TestRetainedSqlContract");
            final var sqlField = contract.getDeclaredField("READBACK");
            sqlField.setAccessible(true);
            final var readback = (String) sqlField.get(null);
            connection =
                    (Connection)
                            Proxy.newProxyInstance(
                                    Connection.class.getClassLoader(),
                                    new Class<?>[] {Connection.class},
                                    (proxy, method, args) -> {
                                        final var result = invoke(delegated, method, args);
                                        if (method.getName().equals("close")) {
                                            onPhysicalClose.run();
                                        }
                                        if (!method.getName().equals("prepareStatement")
                                                || !readback.equals(args[0])) {
                                            return result;
                                        }
                                        return Proxy.newProxyInstance(
                                                PreparedStatement.class.getClassLoader(),
                                                new Class<?>[] {PreparedStatement.class},
                                                (statement, operation, values) -> {
                                                    final var rows =
                                                            invoke(result, operation, values);
                                                    if (!operation
                                                            .getName()
                                                            .equals("executeQuery")) {
                                                        return rows;
                                                    }
                                                    return Proxy.newProxyInstance(
                                                            ResultSet.class.getClassLoader(),
                                                            new Class<?>[] {ResultSet.class},
                                                            (records, read, columns) -> {
                                                                final var value =
                                                                        invoke(rows, read, columns);
                                                                if (read.getName()
                                                                        .equals("close")) {
                                                                    readbackCloses++;
                                                                    onReadbackClose.accept(
                                                                            jdbc.commits() > 0);
                                                                }
                                                                return value;
                                                            });
                                                });
                                    });
        }

        TestRetainedSqlSession open(final UUID occurrence) throws SQLException {
            try {
                final var admitted =
                        TestRetainedSqlSession.class.getDeclaredMethod(
                                "admitted",
                                Connection.class,
                                TestRetainedSqlSession.Limits.class,
                                UUID.class,
                                LongSupplier.class,
                                long.class);
                admitted.setAccessible(true);
                return (TestRetainedSqlSession)
                        admitted.invoke(
                                null,
                                connection,
                                new TestRetainedSqlSession.Limits(1200, 1000, 10, 60),
                                occurrence,
                                (LongSupplier) () -> 0L,
                                0L);
            } catch (final InvocationTargetException failure) {
                if (failure.getCause() instanceof SQLException sql) {
                    throw sql;
                }
                throw new IllegalStateException(
                        "SYNTHETIC_FIXTURE_ADMISSION_FAILED", failure.getCause());
            } catch (final ReflectiveOperationException failure) {
                throw new IllegalStateException("SYNTHETIC_FIXTURE_BINDING_FAILED", failure);
            }
        }

        private static Object invoke(final Object target, final Method method, final Object[] args)
                throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (final InvocationTargetException failure) {
                throw failure.getCause();
            }
        }
    }

    @Test
    void uncertainCommitAndCloseFailurePreserveBankCauseAndNeverClaimIndependentReadback()
            throws Exception {
        final var request = parse(document());
        for (final String failure : List.of("commit", "close", "post")) {
            final var fixture =
                    new br.com.esl.etl.v2.plataforma.persistencia.controle
                            .TestRetainedRuntimeFixture(1);
            if (failure.equals("commit")) {
                fixture.failCommit();
            } else if (failure.equals("close")) {
                fixture.failClose();
            } else {
                fixture.failPost();
            }
            final var receipt =
                    Coletas6908TestRetainedRunner.execute(
                            request,
                            configuration(),
                            new EnvironmentSecretProvider(environment()),
                            CLOCK,
                            () -> {},
                            () -> fixture.open(request.executionId),
                            (config, secrets, clock, attempts, deadline) ->
                                    gateways(List.of(row()), false, null, attempts));
            assertEquals("STOP_NO_RETRY", receipt.get("status"));
            assertEquals(false, receipt.get("independentPostCommitReadback"));
            assertEquals(1, fixture.commits());
            assertEquals(1, fixture.closes());
            if (failure.equals("commit")) {
                assertEquals("COMMIT_UNCERTAIN", receipt.get("failureCode"));
                assertEquals(true, receipt.get("commitUncertain"));
            }
        }
    }

    @Test
    void typedFailureStatusIsRetainedWithoutReadingArbitraryExceptionText() {
        final var receipt = new java.util.LinkedHashMap<String, Object>();
        final var http =
                new DataExportUnavailableException(
                        6908, 429, Optional.of("SYNTHETIC_SECRET_HEADER"));
        Coletas6908TestRetainedMain.describeFailure(receipt, http);
        assertEquals(429, receipt.get("failureHttpStatus"));
        assertFalse(receipt.toString().contains("SYNTHETIC_SECRET_HEADER"));
        final var hostile =
                new RuntimeException() {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getMessage() {
                        throw new AssertionError("ARBITRARY_TEXT_READ");
                    }
                };
        assertEquals("SOURCE_OR_SQL_REFUSED", Coletas6908TestRetainedMain.failureCode(hostile));
    }

    private static DataExportTemplateInfo info(final boolean drift) {
        final var fields = new ArrayList<DataExportMetadataField>();
        final var filters = new ArrayList<DataExportMetadataField>();
        for (final var element : DataExportColetasContractCatalog.release().metadata().elements()) {
            if (element.kind() == ContractMetadata.ElementKind.DATA_FIELD) {
                fields.add(
                        new DataExportMetadataField(
                                element.path(), Optional.empty(), Optional.empty()));
            } else {
                final String type =
                        switch (element.path()) {
                            case "by_updated_at" -> "datetime";
                            case "status", "corporation_id" -> "select";
                            default -> "date";
                        };
                filters.add(
                        new DataExportMetadataField(
                                element.path(), Optional.of(type), Optional.empty()));
            }
        }
        if (drift) {
            fields.remove(0);
        }
        return new DataExportTemplateInfo(
                DataExportTemplate.COLETAS, 200, Optional.empty(), fields, filters);
    }

    private static ObjectNode row() {
        final var row = JSON.createObjectNode();
        for (final var field : DataExportColetasContractCatalog.release().metadata().elements()) {
            if (field.kind() == ContractMetadata.ElementKind.DATA_FIELD) {
                row.putNull(field.path());
            }
        }
        return row.put("id", 1).put("sequence_code", 11).put("request_date", "2026-10-01");
    }

    private static ControlPlane control(final List<Object> events) {
        return (ControlPlane)
                Proxy.newProxyInstance(
                        ControlPlane.class.getClassLoader(),
                        new Class<?>[] {ControlPlane.class},
                        (proxy, method, arguments) -> {
                            events.add(arguments[0]);
                            return null;
                        });
    }

    private static br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration
            configuration() {
        return Coletas6908TestRetainedSource.configuration(environment());
    }

    private static Map<String, String> environment() {
        return Map.of(
                "V2_DATAEXPORT_BASE_URL",
                "https://fixture.invalid",
                "V2_DATAEXPORT_TOKEN",
                "SYNTHETIC_TOKEN",
                "V2_SHADOW_SAMPLE_SA_PASSWORD",
                "SYNTHETIC_PASSWORD");
    }

    private static Properties options() {
        final var values = new Properties();
        values.setProperty("test.retained.local.enabled", "true");
        values.setProperty("test.retained.sql-pin.enabled", "true");
        values.setProperty("jdk.httpclient.redirects.retrylimit", "1");
        values.setProperty("jdk.httpclient.disableRetryConnect", "true");
        values.setProperty("jdk.httpclient.enableAllMethodRetry", "false");
        return values;
    }

    private static Coletas6908TestRetainedRequest parse(final ObjectNode node) throws Exception {
        return Coletas6908TestRetainedRequest.parse(JSON.writeValueAsBytes(node));
    }

    static ObjectNode document() {
        return JSON.createObjectNode()
                .put("schema", "test-retained-coletas-v1")
                .put("executionId", "a27f7498-d51a-4cf8-93cf-e51308e971af")
                .put("cycleId", "ba1cb84e-2d22-4a0e-889d-1b5a139526da")
                .put("sourceInstance", Coletas6908TestRetainedRequest.SOURCE)
                .put("tenantScope", Coletas6908TestRetainedRequest.TENANT)
                .put("businessDate", "2026-10-01")
                .put("retainData", "true")
                .put("per", "1")
                .put("page", "1")
                .put("maximumRows", "1000")
                .put("maximumHttpCalls", "2")
                .put("maximumResponseBytes", "10485760")
                .put("requestTimeoutSeconds", "30")
                .put("deadlineSeconds", "60");
    }
}
