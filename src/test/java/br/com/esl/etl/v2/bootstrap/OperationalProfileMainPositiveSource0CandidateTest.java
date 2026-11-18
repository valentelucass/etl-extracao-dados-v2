package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAction;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeAuthorizationScope;
import br.com.esl.etl.v2.plataforma.autorizacao.RuntimeUsersAuthorityFixture;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfigurationFactory;
import br.com.esl.etl.v2.plataforma.orquestracao.IndependentExportPlanBinding;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ColetasIndependentRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.IndependentExportRequest;
import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.MainProfileCaptureFixture;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

/**
 * Invokes the real Main CLI, real configuration/request/selection validation, real durable
 * authority issuer/consumer against synthetic JDBC, real capture and real comparison. Only the
 * physical wire and governed delivery destination/consumer are authored fixtures. No network,
 * native process, actual SQL, secret provider or retained input is used.
 */
@Timeout(45)
class OperationalProfileMainPositiveSource0CandidateTest {
    @org.junit.jupiter.api.BeforeEach
    void traceStartedCase(final org.junit.jupiter.api.TestInfo test) {
        System.err.println(
                "SOURCE0_MAIN_CASE_BEGIN "
                        + test.getTestMethod().orElseThrow().getName()
                        + " "
                        + java.time.Instant.now());
    }

    @org.junit.jupiter.api.AfterEach
    void traceFinishedCase(final org.junit.jupiter.api.TestInfo test) {
        System.err.println(
                "SOURCE0_MAIN_CASE_END "
                        + test.getTestMethod().orElseThrow().getName()
                        + " "
                        + java.time.Instant.now());
    }

    @TempDir Path directory;

    record Answer(int code, String out, String err) {
        JsonNode json() throws Exception {
            return ExportValidationSupport.JSON.readTree(out);
        }
    }

    final class Harness {
        final Clock clock = new MainProfileCaptureFixture.VirtualClock();
        final MainProfileCaptureFixture capture;
        final Path config;
        final Path operational;
        ExportValidationSupport.Pin selectionPin;
        RuntimeUsersAuthorityFixture authority;
        String authorityFailure;
        String deliveryMode = "COMPLETE";
        int invocations;
        int captureExecutions;
        int deliveryCalls;
        boolean consumedBeforeCapture;
        boolean consumedBeforeDelivery;

        Harness(final String profile, final String mode) throws Exception {
            this(profile, mode, false);
        }

        Harness(final String profile, final String mode, final boolean typedStage)
                throws Exception {
            this(profile, mode, typedStage, null);
        }

        Harness(
                final String profile,
                final String mode,
                final boolean typedStage,
                final String missingReference)
                throws Exception {
            System.err.println("SOURCE0_MAIN_HARNESS_BEGIN " + java.time.Instant.now());
            final var own = Files.createDirectory(directory.resolve(UUID.randomUUID().toString()));
            capture = new MainProfileCaptureFixture(own, profile, clock);
            capture.mode = mode;
            config = own.resolve("runtime.properties");
            String properties =
                    Files.readString(
                                    Path.of(
                                            "config/independent-export-operational.example.properties"))
                            .replaceAll("(?m)^# (dataexport\\.[^\\r\\n]+)$", "$1")
                            .replace("dataexport.enabled=false", "dataexport.enabled=true")
                            .replace(
                                    "https://root-must-select.invalid",
                                    "https://source.synthetic.invalid")
                            .replace("ROOT_MUST_SELECT", "SYNTHETIC_SCOPE")
                            .replace("shadow.audit.enabled=false", "shadow.audit.enabled=true");
            properties +=
                    "\nshadow.target-kind=LOCAL_EPHEMERAL\n"
                            + "shadow.jdbc-url=jdbc:sqlserver://localhost;databaseName=ETL_SISTEMA_V2_SHADOW;"
                            + "integratedSecurity=true;encrypt=true;trustServerCertificate=false\n";
            Files.writeString(config, properties);
            final var configuration =
                    new RuntimeConfigurationFactory()
                            .load(config, new Properties(), Map.of(), clock);
            assertEquals(
                    1,
                    configuration
                            .dataExport()
                            .orElseThrow()
                            .settings()
                            .retryPolicy()
                            .maxAttempts());
            assertEquals(
                    Duration.ofSeconds(3),
                    configuration
                            .dataExport()
                            .orElseThrow()
                            .resiliencePolicy()
                            .minimumRequestInterval());
            final var request = capture.request;
            final var now = clock.instant().truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
            final var document =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("invocationId", UUID.randomUUID().toString())
                            .put("executionId", request.path("attemptId").asText())
                            .put("cycleId", UUID.randomUUID().toString())
                            .put("template", request.path("entity").asText())
                            .put("mode", "BACKFILL")
                            .put("start", now.toString())
                            .put("endExclusive", now.plusSeconds(3600).toString())
                            .put("replayOf", "")
                            .put("idempotencyKey", UUID.randomUUID().toString())
                            .put("businessStart", request.path("periodStart").asText())
                            .put("businessEnd", request.path("periodEnd").asText())
                            .put("leaseSeconds", "240")
                            .put("pageSize", "1")
                            .put("maximumPages", "2")
                            .put("maximumRows", "4")
                            .put("maximumDistinctRoots", "4")
                            .put("qualityVersion", "synthetic-quality-1")
                            .put("qualityFingerprint", "b".repeat(64))
                            .put("compatibilityVersion", "strict-1");
            operational = own.resolve("operational.json");
            Files.writeString(operational, document.toString());
            final String sourceSha =
                    DataExportRuntimeConfigurationFingerprint.from(
                                    configuration.dataExport().orElseThrow())
                            .sha256();
            if (typedStage) {
                deliveryMode = "TYPED_STAGE";
                br.com.esl.etl.v2.plataforma.reconciliacao.exportacao
                        .MainProfileGovernedStageFixture.configure(capture, sourceSha);
                if (missingReference != null) {
                    final var recipePath =
                            ExportValidationSupport.Pin.from(
                                            capture.selection.required("dashboardInputRecipePin"))
                                    .path();
                    final var recipe =
                            (ObjectNode)
                                    ExportValidationSupport.JSON.readTree(
                                            Files.readAllBytes(recipePath));
                    final var contextPath =
                            ExportValidationSupport.Pin.from(recipe.required("referenceContextPin"))
                                    .path();
                    final var context =
                            (ObjectNode)
                                    ExportValidationSupport.JSON.readTree(
                                            Files.readAllBytes(contextPath));
                    ((ObjectNode) context.required("datasets")).remove(missingReference);
                    Files.write(contextPath, jsonBytes(context));
                    recipe.set("referenceContextPin", node(pin(contextPath)));
                    Files.write(recipePath, jsonBytes(recipe));
                    capture.selection.set("dashboardInputRecipePin", node(pin(recipePath)));
                }
            }
            selectionPin = capture.freeze(sourceSha);
            System.err.println("SOURCE0_MAIN_HARNESS_READY " + java.time.Instant.now());
        }

        Answer run() throws Exception {
            System.err.println("SOURCE0_MAIN_RUN_BEGIN " + java.time.Instant.now());
            final var out = new ByteArrayOutputStream();
            final var err = new ByteArrayOutputStream();
            final RuntimeIndependentExportCommand.Invocation invocation =
                    (configuration, requestPath, pin, selected, nanos, started, profile) -> {
                        invocations++;
                        final var request =
                                RuntimeOperationalRequest.read(configuration, requestPath);
                        final var plan =
                                IndependentExportPlanBinding.bind(
                                        request.plan,
                                        pin.sha256(),
                                        DataExportRuntimeConfigurationFingerprint.from(
                                                        configuration.dataExport().orElseThrow())
                                                .sha256(),
                                        profile);
                        authority =
                                new RuntimeUsersAuthorityFixture(
                                        RuntimeAuthorizationScope.from(
                                                request.invocation, RuntimeAction.RUN, plan));
                        if (authorityFailure != null) {
                            authority.reject(authorityFailure);
                        }
                        capture.consumed = authority::consumed;
                        final var root =
                                RuntimeCompositionRoot.forIndependentExportFixture(
                                        configuration,
                                        authority::authority,
                                        (config, selectedPin, selection, startNanos, instant) -> {
                                            captureExecutions++;
                                            consumedBeforeCapture = authority.consumed();
                                            assertTrue(consumedBeforeCapture);
                                            return capture.execute(
                                                    config,
                                                    selectedPin,
                                                    selection,
                                                    startNanos,
                                                    instant);
                                        },
                                        context -> {
                                            deliveryCalls++;
                                            consumedBeforeDelivery = authority.consumed();
                                            assertTrue(consumedBeforeDelivery);
                                            return "UNAVAILABLE".equals(deliveryMode)
                                                    ? Optional.empty()
                                                    : "TYPED_STAGE".equals(deliveryMode)
                                                            ? new CapturedDashboardStageDelivery()
                                                                    .deliver(context)
                                                            : deliver(context);
                                        });
                        return root.executeIndependentExport(
                                requestPath, pin, selected, nanos, started, profile);
                    };
            final int code =
                    Main.run(
                            new String[] {
                                "run",
                                "--profile",
                                capture.request.path("profile").asText(),
                                "--config",
                                config.toString(),
                                "--operational-request",
                                operational.toString(),
                                "--selection",
                                selectionPin.path().toString(),
                                "--sha256",
                                selectionPin.sha256()
                            },
                            new PrintStream(out),
                            new PrintStream(err),
                            new Main.RuntimeDependencies(
                                    new Properties(),
                                    Map.of(),
                                    clock,
                                    new RuntimeConfigurationFactory(),
                                    invocation));
            System.err.println("SOURCE0_MAIN_RUN_END " + java.time.Instant.now());
            return new Answer(code, out.toString(), err.toString());
        }

        Optional<ExportValidationSupport.Pin> deliver(final GovernedExportDelivery.Context context)
                throws Exception {
            final var plan = context.plan();
            final var diagnostic = context.diagnostic();
            final var api =
                    ExportValidationSupport.Pin.from(diagnostic.path("api").required("dataPin"));
            final byte[] captured = api.read(8388608, context.budget());
            final var artifact = write(Path.of(plan.path("artifactPath").asText()), captured);
            // A separate consumer reads the produced bytes, materializes them, then acknowledges.
            byte[] observed = artifact.read(8388608, context.budget());
            if ("BAD_ROUNDTRIP".equals(deliveryMode)) {
                observed =
                        "SYNTHETIC_DIFFERENT".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            }
            final var roundtrip =
                    write(Path.of(plan.path("consumerRoundtripPath").asText()), observed);
            final var consumer =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "INDEPENDENT_GOVERNED_CAPTURE_CONSUMED_V1")
                            .put("attemptId", plan.path("attemptId").asText())
                            .put("profile", plan.path("profile").asText())
                            .put("acceptedAndReadBack", true)
                            .put("consumerClosedKnown", true)
                            .put("fixtureOnly", true)
                            .put("actualHTTP", 0)
                            .put("actualSQL", 0);
            consumer.set("artifactPin", node(artifact));
            consumer.set("roundtripPin", node(roundtrip));
            consumer.set("captureReceiptPin", node(context.captureReceiptPin()));
            consumer.set("deliveryPlanPin", node(context.planPin()));
            if ("WRONG_SCOPE".equals(deliveryMode)) {
                consumer.put("attemptId", UUID.randomUUID().toString());
            }
            final var consumerPin =
                    write(Path.of(plan.path("consumerReceiptPath").asText()), jsonBytes(consumer));
            final var producer =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "INDEPENDENT_GOVERNED_CAPTURE_PRODUCED_V1")
                            .put("attemptId", plan.path("attemptId").asText())
                            .put("profile", plan.path("profile").asText())
                            .put("producerClosedKnown", true)
                            .put("fixtureOnly", true);
            producer.set("deliveryPlanPin", node(context.planPin()));
            producer.set("selectionPin", node(context.selectionPin()));
            producer.set("captureReceiptPin", node(context.captureReceiptPin()));
            producer.set("diagnosticPin", node(context.diagnosticPin()));
            producer.set("apiDataPin", diagnostic.path("api").required("dataPin"));
            producer.set("uiDataPin", diagnostic.path("ui").required("dataPin"));
            producer.set("artifactPin", node(artifact));
            producer.set("consumerReceiptPin", node(consumerPin));
            return Optional.of(
                    write(Path.of(plan.path("producerReceiptPath").asText()), jsonBytes(producer)));
        }
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void fretesSingleOfficialActionCapturesComparesAndClosesActualFixtureDelivery()
            throws Exception {
        completed(IndependentExportRequest.PROFILE, 114, 50);
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void coletasSingleOfficialActionCapturesMapsAll31ComparesAndClosesFixtureDelivery()
            throws Exception {
        completed(ColetasIndependentRequest.PROFILE, 31, 8);
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void coletasMainProducesGovernanceFromRawReferencesAndClosesTheTypedConsumer()
            throws Exception {
        typedStageCompleted(ColetasIndependentRequest.PROFILE);
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void fretesMainUsesCorporateMinutaAndDerivedBillingThroughTheTypedDeliveryPort()
            throws Exception {
        typedStageCompleted(IndependentExportRequest.PROFILE);
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void missingColetasRegionReferencePreservesRealComparisonWithoutDeliverySuccess()
            throws Exception {
        missingReferencePreservesComparison(ColetasIndependentRequest.PROFILE, "logisticsRegions");
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void missingFretesCalendarPreservesRealComparisonWithoutRepeatingCapture() throws Exception {
        missingReferencePreservesComparison(IndependentExportRequest.PROFILE, "calendar");
    }

    private void missingReferencePreservesComparison(final String profile, final String missing)
            throws Exception {
        final var harness = new Harness(profile, "EQUAL", true, missing);
        final var result = harness.run();
        assertEquals(3, result.code(), result.err());
        final var outcome = result.json();
        assertFalse(outcome.path("governedDeliveryComplete").asBoolean());
        final var receipt = outcome.required("captureComparisonReceipt");
        assertEquals("CONTENT_EQUAL_FOR_THIS_PAIR", receipt.path("status").asText());
        final var diagnostic =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(5)));
        assertTrue(diagnostic.path("comparison").path("fullTuplesEqual").asBoolean());
        assertEquals(3, harness.capture.calls);
        assertEquals(1, harness.captureExecutions);
        assertFalse(Files.exists(harness.capture.directory.resolve("delivery-producer.json")));
        assertFalse(Files.exists(harness.capture.directory.resolve("delivery-capture.bin")));
        assertFalse(outcome.path("SQLPublicationVerified").asBoolean());
    }

    private void typedStageCompleted(final String profile) throws Exception {
        final var harness = new Harness(profile, "EQUAL", true);
        final var result = harness.run();
        assertEquals(0, result.code(), result.err());
        final var outcome = result.json();
        assertTrue(outcome.path("governedDeliveryComplete").asBoolean());
        assertEquals(CapturedDashboardStageDelivery.KIND, outcome.path("deliveryKind").asText());
        assertFalse(outcome.path("SQLPublicationVerified").asBoolean());
        assertFalse(outcome.path("dashboardConsumptionVerified").asBoolean());
        final var consumer =
                ExportValidationSupport.Pin.from(outcome.required("consumerReceiptPin"))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(5)));
        assertTrue(
                consumer.path("typedReadback")
                        .path("actualLocalStageReadbackVerified")
                        .asBoolean());
        assertEquals(1, consumer.path("typedReadback").path("typedRowsReadBack").asInt());
        final var diagnostic =
                ExportValidationSupport.Pin.from(
                                outcome.path("captureComparisonReceipt").required("diagnosticPin"))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(5)));
        assertTrue(
                diagnostic
                        .path("dashboardBridge")
                        .path("governanceDerivedFromRawReferenceDatasets")
                        .asBoolean());
        assertEquals(0, diagnostic.path("dashboardBridge").path("externalFactRecords").asInt());
        final var artifact = ExportValidationSupport.Pin.from(consumer.required("artifactPin"));
        final var row =
                ExportValidationSupport.JSON
                        .readTree(Files.readAllLines(artifact.path()).get(0))
                        .required("columns");
        if (IndependentExportRequest.PROFILE.equals(profile)) {
            assertEquals(9001, row.path("numero_minuta").asInt());
            assertEquals("2025-02-28", row.path("data_referencia_faturamento_date").asText());
        } else {
            assertEquals(9002, row.path("Numero Manifesto").asInt());
            assertEquals("SYNTHETIC RAW RULE REGION", row.path("Região Logística").asText());
        }
        assertEquals(3, harness.capture.calls);
        assertEquals(1, harness.deliveryCalls);
        assertTrue(harness.consumedBeforeCapture && harness.consumedBeforeDelivery);
    }

    private void completed(final String profile, final int columns, final int totals)
            throws Exception {
        final var harness = new Harness(profile, "EQUAL");
        final var result = harness.run();
        assertEquals(0, result.code(), result.err());
        final var outcome = result.json();
        assertEquals(
                "COMPLETED_GOVERNED_CAPTURE_DELIVERY_FOR_THIS_INVOCATION",
                outcome.path("status").asText());
        assertTrue(outcome.path("governedDeliveryComplete").asBoolean());
        assertFalse(outcome.path("mainResumeAllowed").asBoolean());
        assertFalse(outcome.path("dashboardConsumptionVerified").asBoolean());
        assertFalse(outcome.path("SQLPublicationVerified").asBoolean());
        final var receipt = outcome.required("captureComparisonReceipt");
        assertEquals(
                harness.capture.request.path("attemptId").asText(),
                receipt.path("attemptId").asText());
        final var diagnostic =
                ExportValidationSupport.Pin.from(receipt.required("diagnosticPin"))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(5)));
        assertEquals(profile, diagnostic.path("profile").asText());
        assertEquals(columns, diagnostic.path("commonColumns").asInt());
        assertEquals(totals, diagnostic.path("totalFields").asInt());
        assertTrue(diagnostic.path("comparison").path("fullTuplesEqual").asBoolean());
        assertEquals(0, diagnostic.path("comparison").path("changedIds").asLong());
        assertEquals(0, diagnostic.path("comparison").path("changedMultiplicityIds").asLong());
        assertEquals(columns, diagnostic.path("comparison").path("columnsEqual").size());
        assertEquals(totals, diagnostic.path("comparison").path("totalsEqual").size());
        diagnostic
                .path("comparison")
                .path("columnsEqual")
                .forEach(value -> assertTrue(value.asBoolean()));
        diagnostic
                .path("comparison")
                .path("totalsEqual")
                .forEach(value -> assertTrue(value.asBoolean()));
        diagnostic
                .path("comparison")
                .path("totalsReady")
                .forEach(value -> assertTrue(value.asBoolean()));
        assertEquals(1, harness.invocations);
        assertEquals(1, harness.captureExecutions);
        assertEquals(1, harness.deliveryCalls);
        assertEquals(3, harness.capture.calls);
        assertTrue(harness.authority.consumed());
        assertEquals(2, harness.authority.connections());
        assertTrue(harness.consumedBeforeCapture && harness.consumedBeforeDelivery);
        assertTrue(harness.capture.authorityWasConsumedBeforeEveryWire);
        assertTrue(harness.capture.minimumObservedGapNanos >= Duration.ofSeconds(3).toNanos());
        if (ColetasIndependentRequest.PROFILE.equals(profile)) {
            assertEquals(
                    List.of(
                            "GET /api/analytics/reports/6908/info",
                            "GET /api/analytics/reports/6908/data",
                            "GET /api/analytics/reports/6908/data"),
                    harness.capture.routes);
        } else {
            assertEquals(
                    List.of(
                            "POST /api/analytics/reports/6389/export",
                            "GET /api/analytics/report_files/7",
                            "GET /book.xlsx"),
                    harness.capture.routes);
        }
        final var persisted =
                ExportValidationSupport.Pin.from(outcome.required("resultPin"))
                        .json(262144, new ExportValidationSupport.Budget(Duration.ofSeconds(5)));
        final var expected = ((ObjectNode) outcome).deepCopy();
        expected.remove("resultPin");
        assertEquals(ExportValidationSupport.JSON.readTree(jsonBytes(expected)), persisted);
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void samePositiveComparisonCannotCompleteWithoutTheOwnerDeliveryProducer() throws Exception {
        final var harness = new Harness(IndependentExportRequest.PROFILE, "EQUAL");
        harness.deliveryMode = "UNAVAILABLE";
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals("PENDING_GOVERNED_DELIVERY", result.json().path("status").asText());
        assertFalse(result.json().path("governedDeliveryComplete").asBoolean());
        assertEquals(1, harness.deliveryCalls);
        assertFalse(Files.exists(harness.capture.directory.resolve("delivery-result.json")));
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void deniedDurableAuthorityCannotComposeCaptureOrDelivery() throws Exception {
        final var harness = new Harness(ColetasIndependentRequest.PROFILE, "EQUAL");
        harness.authorityFailure = "denied";
        final var result = harness.run();
        assertEquals(2, result.code());
        assertEquals(0, harness.captureExecutions);
        assertEquals(0, harness.capture.calls);
        assertEquals(0, harness.deliveryCalls);
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void fretesDifferenceNeverInvokesDeliveryOrReturnsZero() throws Exception {
        final var harness = new Harness(IndependentExportRequest.PROFILE, "DIFFERENCE");
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals(0, harness.deliveryCalls);
        assertEquals(
                "PENDING_DIFFERENCE_INVESTIGATION_AND_NEW_PAIR",
                result.json().path("status").asText());
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void coletasPageLimitCannotReplaceItsMissingTerminalEofWithSuccess() throws Exception {
        final var harness = new Harness(ColetasIndependentRequest.PROFILE, "PARTIAL");
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals(2, harness.capture.calls);
        assertEquals(0, harness.deliveryCalls);
        assertTrue(result.json().path("status").asText().contains("PARTIAL"));
        assertTrue(
                Files.exists(
                        harness.capture.directory.resolve("capture/coletas-mapped.partial.jsonl")));
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void fretesPendingGenerationStopsAtOnePollWithoutRepeatingPost() throws Exception {
        final var harness = new Harness(IndependentExportRequest.PROFILE, "PARTIAL");
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals(2, harness.capture.calls);
        assertEquals(0, harness.deliveryCalls);
        assertEquals(
                1,
                harness.capture.routes.stream().filter(route -> route.startsWith("POST ")).count());
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void stop429DoesNotRetryOrEnterDelivery() throws Exception {
        final var harness = new Harness(IndependentExportRequest.PROFILE, "STOP429");
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals(1, harness.capture.calls);
        assertEquals(0, harness.deliveryCalls);
        assertFalse(result.json().path("governedDeliveryComplete").asBoolean());
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void unknownWireOutcomeCannotBecomeSuccessOrRetry() throws Exception {
        final var harness = new Harness(ColetasIndependentRequest.PROFILE, "UNKNOWN");
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals(1, harness.capture.calls);
        assertEquals(0, harness.deliveryCalls);
        assertTrue(result.json().path("status").asText().contains("RECONCILIATION"));
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void missingIndependentUiReferenceCannotComplete() throws Exception {
        final var harness = new Harness(ColetasIndependentRequest.PROFILE, "NO_REFERENCE");
        final var result = harness.run();
        assertEquals(3, result.code());
        assertEquals(3, harness.capture.calls);
        assertEquals(0, harness.deliveryCalls);
        assertEquals("PENDING_INDEPENDENT_UI_REFERENCE", result.json().path("status").asText());
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void consumerReceiptFromAnotherAttemptIsRefusedEvenAfterEqualComparison() throws Exception {
        final var harness = new Harness(IndependentExportRequest.PROFILE, "EQUAL");
        harness.deliveryMode = "WRONG_SCOPE";
        final var result = harness.run();
        assertEquals(2, result.code());
        assertEquals(1, harness.deliveryCalls);
        assertFalse(Files.exists(harness.capture.directory.resolve("delivery-result.json")));
    }

    @org.junit.jupiter.api.parallel.Execution(
            org.junit.jupiter.api.parallel.ExecutionMode.CONCURRENT)
    @Test
    void changedConsumerBytesAreRefusedEvenWithClaimedAcceptedReadback() throws Exception {
        final var harness = new Harness(ColetasIndependentRequest.PROFILE, "EQUAL");
        harness.deliveryMode = "BAD_ROUNDTRIP";
        final var result = harness.run();
        assertEquals(2, result.code());
        assertEquals(1, harness.deliveryCalls);
        assertFalse(Files.exists(harness.capture.directory.resolve("delivery-result.json")));
    }

    private static ObjectNode node(final ExportValidationSupport.Pin pin) {
        return ExportValidationSupport.JSON.valueToTree(pin);
    }

    private static ExportValidationSupport.Pin pin(final Path path) throws Exception {
        final byte[] value = Files.readAllBytes(path);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(value), value.length);
    }

    private static byte[] jsonBytes(final JsonNode value) throws Exception {
        return ExportValidationSupport.JSON.writeValueAsBytes(value);
    }

    private static ExportValidationSupport.Pin write(final Path path, final byte[] bytes)
            throws Exception {
        try (var file =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                file.write(buffer);
            }
            file.force(true);
        }
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }
}
