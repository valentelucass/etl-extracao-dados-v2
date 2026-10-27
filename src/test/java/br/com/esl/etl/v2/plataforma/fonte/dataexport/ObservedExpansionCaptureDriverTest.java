package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/** Prepared only. Exercises real factory/parser/guard/core with synthetic in-memory wire. */
class ObservedExpansionCaptureDriverTest {
    @TempDir Path temporaryConfigurationDirectory;

    @ParameterizedTest
    @EnumSource(ObservedExpansionCaptureTemplate.class)
    void allFourTraverseInfoGuardCoreEnvelopeAndKnownEmptyTerminal(
            final ObservedExpansionCaptureTemplate template) throws Exception {
        final var f = new ObservedHttpTransportTestSupport.Fixture(template);
        ObservedCaptureDriverFixtures.info(f);
        final String body =
                ObservedCaptureDriverFixtures.rows(ObservedCaptureDriverFixtures.row(template), 1);
        ObservedCaptureDriverFixtures.json(f, body);
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var driver = ObservedCaptureDriverFixtures.driver(f, configuration());
        assertEquals(0, f.client.requests.size());
        final var result = driver.collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.LOCAL_TERMINAL, result.outcome());
        assertEquals(1, result.infoAttempts());
        assertEquals(2, result.dataAttempts());
        assertEquals(3, f.client.requests.size());
        assertEquals(2, result.pages().size());
        final var page = result.pages().get(0);
        assertTrue(page.schemaAccepted());
        final var core = page.core().orElseThrow();
        final var envelope = page.envelope().orElseThrow();
        assertSame(page.rawPage(), core.rawPage());
        assertSame(core, envelope.coreProjection());
        assertSame(page.rawPage(), envelope.rawPage());
        assertEquals("NOT_PERFORMED_BY_CORE", core.sourceSchemaValidation());
        assertEquals("observed-expansion-admission-v1", envelope.projectionVersion());
        assertTrue(envelope.json().contains("\"core\":" + core.projectionJson()));
        assertArrayEquals(body.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
        assertEquals(0, result.pages().get(1).core().orElseThrow().physicalRows().orElseThrow());
        assertThrows(IllegalStateException.class, driver::collect);
        assertEquals(3, f.client.requests.size());
        assertTrue(f.nanos.get() >= Duration.ofSeconds(6).toNanos());
        for (int index = 1; index < f.client.requests.size(); index++) {
            final var request =
                    ObservedCaptureDriverFixtures.JSON.readTree(f.client.requestBodies.get(index));
            assertFalse(request.has("order_by"));
            assertEquals("50", request.path("per").asText());
            assertEquals(Integer.toString(index), request.path("page").asText());
        }
    }

    @Test
    void twoNonemptyPagesOverOneHundredKeepEveryDuplicateAndGlobalOccurrence() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        ObservedCaptureDriverFixtures.info(f);
        final var row = ObservedCaptureDriverFixtures.row(f.plan.template());
        ObservedCaptureDriverFixtures.json(f, ObservedCaptureDriverFixtures.rows(row, 105));
        ObservedCaptureDriverFixtures.json(f, ObservedCaptureDriverFixtures.rows(row, 2));
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.LOCAL_TERMINAL, result.outcome());
        assertEquals(4, f.client.requests.size());
        final var first = result.pages().get(0).core().orElseThrow();
        final var second = result.pages().get(1).core().orElseThrow();
        assertEquals(105, first.records().size());
        assertEquals(2, second.records().size());
        assertEquals(1, first.records().get(0).occurrence());
        assertEquals(105, first.records().get(104).occurrence());
        assertEquals(106, second.records().get(0).occurrence());
        assertEquals(107, second.records().get(1).occurrence());
        assertEquals(ObservedExpansionPageProjection.Status.MAPPED, first.status());
    }

    @Test
    void fourthNonemptyPageIsPartialAndNeverRequestsFifthData() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.FAT);
        ObservedCaptureDriverFixtures.info(f);
        final String row =
                ObservedCaptureDriverFixtures.rows(
                        ObservedCaptureDriverFixtures.row(f.plan.template()), 1);
        for (int index = 0; index < 4; index++) {
            ObservedCaptureDriverFixtures.json(f, row);
        }
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.PARTIAL_PAGE_LIMIT, result.outcome());
        assertEquals(4, result.dataAttempts());
        assertEquals(5, f.client.requests.size());
    }

    @Test
    void schemaRefusalPreservesRawUnknownCountAndNeverInvokesCoreOrEmitsEnvelope()
            throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.CAP);
        ObservedCaptureDriverFixtures.info(f);
        final var row = ObservedCaptureDriverFixtures.row(f.plan.template());
        row.put("new_unobserved_field", ObservedCaptureDriverFixtures.BUSINESS_MARKER);
        final String wire = "[" + row + "]";
        ObservedCaptureDriverFixtures.json(f, wire);
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(
                ObservedExpansionCaptureResult.Outcome.SCHEMA_STRUCTURE_ISSUE, result.outcome());
        assertEquals(2, f.client.requests.size());
        final var page = result.pages().get(0);
        assertFalse(page.schemaAccepted());
        assertTrue(page.core().isEmpty());
        assertTrue(page.envelope().isEmpty());
        assertArrayEquals(wire.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
        final var receipt = ObservedCaptureDriverFixtures.JSON.readTree(result.technicalJson());
        assertTrue(receipt.path("pages").get(0).path("mappedPhysicalRows").isNull());
    }

    @Test
    void genuineMapperStructureIssueAfterSchemaAcceptanceStopsWithUnknownCount() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.SIN);
        ObservedCaptureDriverFixtures.info(f);
        final var row = ObservedCaptureDriverFixtures.row(f.plan.template());
        row.put("insurance_claim_commentary", "x".repeat(4001));
        ObservedCaptureDriverFixtures.json(f, "[" + row + "]");
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.CORE_STRUCTURE_ISSUE, result.outcome());
        assertEquals(2, f.client.requests.size());
        final var page = result.pages().get(0);
        assertTrue(page.schemaAccepted());
        assertTrue(page.envelope().isPresent());
        final var core = page.core().orElseThrow();
        assertEquals(ObservedExpansionPageProjection.Issue.MAPPER_REJECTED, core.pageIssue());
        assertTrue(core.physicalRows().isEmpty());
        assertTrue(core.records().isEmpty());
    }

    @Test
    void fieldIssueKeepsAllSixtyRowsAndSevenLongCommentaryLiteralsInRam() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.SIN);
        ObservedCaptureDriverFixtures.info(f);
        final var rows = ObservedCaptureDriverFixtures.JSON.createArrayNode();
        final String longComment = "x".repeat(1025);
        for (int index = 0; index < 60; index++) {
            final var row = ObservedCaptureDriverFixtures.row(f.plan.template());
            row.put("insurance_claim_commentary", index < 7 ? longComment : "offline");
            rows.add(row);
        }
        ObservedCaptureDriverFixtures.json(f, rows.toString());
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        final var core = result.pages().get(0).core().orElseThrow();
        assertEquals(60, core.records().size());
        int longComments = 0;
        for (final var record : core.records()) {
            final var field =
                    record.fields().stream()
                            .filter(value -> value.path().equals("insurance_claim_commentary"))
                            .findFirst()
                            .orElseThrow();
            if (field.value().raw().equals(longComment)) {
                assertTrue(field.value().issue() != null);
                longComments++;
            }
        }
        assertEquals(7, longComments);
        assertEquals(ObservedExpansionCaptureResult.Outcome.LOCAL_TERMINAL, result.outcome());
    }

    @Test
    void info429AndMalformedRetryAfterStopSharedFactoryIrreversibly() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        429,
                        Map.of(
                                "Content-Type",
                                List.of("application/json"),
                                "Retry-After",
                                List.of("not-a-delay")),
                        new byte[] {'[', ']'},
                        false));
        final Path configuration = configuration();
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.INFO_429, result.outcome());
        assertTrue(f.factory.observedSourceStopped());
        assertEquals(1, f.client.requests.size());
        assertTrue(result.pages().isEmpty());
        assertThrows(
                IllegalStateException.class,
                () -> ObservedCaptureDriverFixtures.driver(f, configuration));
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void infoDriftPreventsEveryDataAttempt() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        ObservedCaptureDriverFixtures.json(f, "{\"fields\":[\"wrong_field\"],\"filters\":[]}");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.INFO_SCHEMA_REJECTED, result.outcome());
        assertEquals(1, f.client.requests.size());
        assertEquals(0, result.dataAttempts());
    }

    @Test
    void sourceFailureAfterMappedPageDoesNotBecomeEof() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        ObservedCaptureDriverFixtures.info(f);
        ObservedCaptureDriverFixtures.json(
                f,
                ObservedCaptureDriverFixtures.rows(
                        ObservedCaptureDriverFixtures.row(f.plan.template()), 1));
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        500,
                        Map.of("Content-Type", List.of("application/json")),
                        new byte[] {'[', ']'},
                        false));
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.DATA_FAILED, result.outcome());
        assertEquals(1, result.pages().size());
        assertEquals(3, f.client.requests.size());
    }

    @Test
    void materializedMismatchAndForbiddenOrderAreRefusedBeforeInfo() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var p = f.plan;
        final var ordered =
                new ObservedExpansionCapturePlan(
                        p.captureExecutionId(),
                        p.template(),
                        p.sourceInstance(),
                        p.tenantScope(),
                        p.businessWindow(),
                        p.sourceZone(),
                        p.transport(),
                        ObservedExpansionCapturePlan.OrderBy.explicit(
                                p.template().knownOrderExpression()),
                        p.envelope(),
                        50,
                        p.limits());
        final Path configuration = configuration();
        assertThrows(
                IllegalArgumentException.class,
                () -> ObservedCaptureDriverFixtures.driver(f, configuration, ordered));
        Files.writeString(
                configuration,
                Files.readString(configuration).replace("tenant-one", "other-tenant"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ObservedCaptureDriverFixtures.driver(f, configuration));
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void technicalOutputWhitelistContainsNoBodyProjectionFieldValueSecretOrBusinessHash()
            throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        ObservedCaptureDriverFixtures.info(f);
        final var row = ObservedCaptureDriverFixtures.row(f.plan.template());
        final var stringField =
                ObservedExpansionSchemaFixtures.schema(f.plan.template()).fields().values().stream()
                        .filter(
                                value ->
                                        value.wireTypes()
                                                .contains(
                                                        ObservedExpansionSchemaCatalog.Wire.STRING))
                        .findFirst()
                        .orElseThrow();
        row.put(stringField.path(), ObservedCaptureDriverFixtures.BUSINESS_MARKER);
        ObservedCaptureDriverFixtures.json(f, "[" + row + "]");
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        final String text = result.technicalJson();
        assertFalse(text.contains(ObservedCaptureDriverFixtures.BUSINESS_MARKER));
        assertFalse(text.contains("synthetic-token"));
        assertFalse(text.contains("synthetic.invalid"));
        final var root = ObservedCaptureDriverFixtures.JSON.readTree(text);
        final Set<String> keys = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(keys::add);
        assertEquals(
                Set.of(
                        "receiptVersion",
                        "captureExecutionId",
                        "templateId",
                        "outcome",
                        "observerAcceptedInfoAttempts",
                        "observerAcceptedDataAttempts",
                        "receivedPages",
                        "physicalAuthority",
                        "coreSchemaValidation",
                        "globalCompletenessProven",
                        "pages"),
                keys);
        assertEquals("NOT_GRANTED_BY_DRIVER", root.path("physicalAuthority").asText());
        assertFalse(root.path("globalCompletenessProven").asBoolean());
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(
            strings = {"cl_overflow", "cl_mismatch", "stream_error", "cancelled", "timeout"})
    void info429StopsAtResponseInfoEvenIfHeadersBodyOrCheckpointFail(final String failureKind)
            throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var sibling = f.prepare();
        final var headers = new java.util.HashMap<String, List<String>>();
        headers.put("Content-Type", List.of("application/json"));
        if (failureKind.equals("cl_overflow")) {
            headers.put("Content-Length", List.of("8388609"));
        }
        if (failureKind.equals("cl_mismatch")) {
            headers.put("Content-Length", List.of("3"));
        }
        f.client.responses.add(
                new ObservedHttpTransportTestSupport.Wire(
                        429, headers, new byte[] {'[', ']'}, failureKind.equals("stream_error")));
        if (failureKind.equals("cancelled")) {
            f.client.beforeCompletion = () -> f.cancelled.set(true);
        }
        if (failureKind.equals("timeout")) {
            f.client.beforeCompletion = () -> f.nanos.addAndGet(Duration.ofSeconds(3).toNanos());
        }
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.INFO_SOURCE_STOPPED, result.outcome());
        assertTrue(f.factory.observedSourceStopped());
        assertTrue(result.pages().isEmpty());
        final var error =
                assertThrows(IllegalStateException.class, () -> sibling.fetch(f.plan.request(1)));
        assertEquals("OBS_CAPTURE_TRANSPORT_SEQUENCE", error.getMessage());
        final var fresh = br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken.none();
        assertThrows(
                IllegalStateException.class,
                () ->
                        f.factory.prepareObservedTransport(
                                f.plan,
                                f.governor.beginCycle(fresh),
                                f.governor,
                                fresh,
                                DataExportHttpAttemptObserver.noop()));
        assertEquals(1, f.client.requests.size());
    }

    @Test
    void loadedEndpointDifferenceCannotHideBehindMatchingSourceTenantAndGovernor()
            throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final Path configuration = configuration();
        Files.writeString(
                configuration,
                Files.readString(configuration).replace("synthetic.invalid", "another.invalid"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ObservedCaptureDriverFixtures.driver(f, configuration));
        assertEquals(0, f.client.requests.size());
    }

    @Test
    void envelopePerPageOverheadCannotExceedFrozenProjectionBound() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var row = ObservedCaptureDriverFixtures.row(f.plan.template());
        final String wire = "[" + row + "]";
        final var baselineCore =
                new ObservedExpansionPageProjector(f.plan)
                        .projectNext(
                                new ObservedExpansionRawPage(
                                        f.plan.request(1),
                                        java.time.Instant.EPOCH,
                                        wire.getBytes(StandardCharsets.UTF_8)));
        final int perPage = Math.toIntExact(baselineCore.projectionByteLength());
        assertTrue(perPage >= 1024);
        final var l = f.plan.limits();
        final var limits =
                new ObservedExpansionCaptureLimits(
                        l.maximumPageBytes(),
                        perPage,
                        l.maximumRawBytes(),
                        l.maximumProjectionTotalBytes(),
                        l.maximumPhysicalRowsPerPage(),
                        l.maximumRowsPerExecution(),
                        l.maximumPages(),
                        l.maximumJsonNodes());
        final var p = withLimits(f.plan, limits);
        ObservedCaptureDriverFixtures.info(f);
        ObservedCaptureDriverFixtures.json(f, wire);
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration(), p).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.ENVELOPE_FAILED, result.outcome());
        assertTrue(result.pages().get(0).core().isPresent());
        assertTrue(result.pages().get(0).envelope().isEmpty());
        assertEquals(2, f.client.requests.size());
    }

    @Test
    void totalEnvelopeBudgetIncludesWrapperBeyondCoreText() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final String wire = "[" + ObservedCaptureDriverFixtures.row(f.plan.template()) + "]";
        final var baselineCore =
                new ObservedExpansionPageProjector(f.plan)
                        .projectNext(
                                new ObservedExpansionRawPage(
                                        f.plan.request(1),
                                        java.time.Instant.EPOCH,
                                        wire.getBytes(StandardCharsets.UTF_8)));
        final int coreBytes = Math.toIntExact(baselineCore.projectionByteLength());
        final var l = f.plan.limits();
        final var limits =
                new ObservedExpansionCaptureLimits(
                        l.maximumPageBytes(),
                        coreBytes + 512,
                        l.maximumRawBytes(),
                        2L * coreBytes + 100,
                        l.maximumPhysicalRowsPerPage(),
                        l.maximumRowsPerExecution(),
                        l.maximumPages(),
                        l.maximumJsonNodes());
        final var p = withLimits(f.plan, limits);
        ObservedCaptureDriverFixtures.info(f);
        ObservedCaptureDriverFixtures.json(f, wire);
        ObservedCaptureDriverFixtures.json(f, wire);
        ObservedCaptureDriverFixtures.json(f, "[]");
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration(), p).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.ENVELOPE_FAILED, result.outcome());
        assertTrue(result.pages().get(0).envelope().isPresent());
        assertTrue(result.pages().get(1).core().isPresent());
        assertTrue(result.pages().get(1).envelope().isEmpty());
        assertEquals(3, f.client.requests.size());
    }

    @Test
    void normalInfoProviderCanaryNeverReachesTechnicalOutputOrData() throws Exception {
        final var f =
                new ObservedHttpTransportTestSupport.Fixture(ObservedExpansionCaptureTemplate.INV);
        final var provider =
                new IllegalStateException(
                        ObservedCaptureDriverFixtures.BUSINESS_MARKER,
                        new IllegalArgumentException(
                                ObservedCaptureDriverFixtures.BUSINESS_MARKER));
        provider.addSuppressed(new IllegalStateException("synthetic-token"));
        f.client.failure = provider;
        final var result = ObservedCaptureDriverFixtures.driver(f, configuration()).collect();
        assertEquals(ObservedExpansionCaptureResult.Outcome.INFO_FAILED, result.outcome());
        assertFalse(result.technicalJson().contains(ObservedCaptureDriverFixtures.BUSINESS_MARKER));
        assertFalse(result.technicalJson().contains("synthetic-token"));
        assertTrue(result.pages().isEmpty());
        assertEquals(1, f.client.requests.size());
    }

    private static ObservedExpansionCapturePlan withLimits(
            final ObservedExpansionCapturePlan p, final ObservedExpansionCaptureLimits limits) {
        return new ObservedExpansionCapturePlan(
                p.captureExecutionId(),
                p.template(),
                p.sourceInstance(),
                p.tenantScope(),
                p.businessWindow(),
                p.sourceZone(),
                p.transport(),
                p.orderBy(),
                p.envelope(),
                p.per(),
                limits);
    }

    @ParameterizedTest
    @EnumSource(ObservedExpansionCaptureTemplate.class)
    void finalGuardInterfaceUppercaseDigestAdmitsWithoutChangingRawOrCore(
            final ObservedExpansionCaptureTemplate template) throws Exception {
        final var plan = ObservedHttpTransportTestSupport.plan(template);
        final var guard = ObservedExpansionSchemaFixtures.bound(plan);
        final String text = "[" + ObservedExpansionSchemaFixtures.row(template) + "]";
        final var raw =
                new ObservedExpansionRawPage(
                        plan.request(1),
                        java.time.Instant.EPOCH,
                        text.getBytes(StandardCharsets.UTF_8));
        final var inspection = guard.inspect(raw);
        assertTrue(inspection.schemaMatched());
        final var step = guard.project(inspection);
        final var core = step.projection();
        final var capability = guard.admit(inspection, step);
        assertTrue(capability.schemaSha256().matches("[0-9A-F]{64}"));
        assertEquals(
                inspection.schemaResourceSha256().toUpperCase(java.util.Locale.ROOT),
                capability.schemaSha256());
        // This call is the causal seam: predecessor compared uppercase capability to lowercase
        // inspection.
        final var page = ObservedExpansionCaptureResult.Page.admitted(inspection, step, capability);
        assertSame(raw, page.rawPage());
        assertSame(core, page.core().orElseThrow());
        assertSame(capability, page.envelope().orElseThrow());
        final var json = ObservedCaptureDriverFixtures.JSON.readTree(capability.json());
        final var keys = new java.util.HashSet<String>();
        json.fieldNames().forEachRemaining(keys::add);
        assertEquals(Set.of("projectionVersion", "schemaRelease", "schemaSha256", "core"), keys);
        assertEquals("observed-expansion-admission-v1", json.path("projectionVersion").asText());
        assertEquals(capability.schemaSha256(), json.path("schemaSha256").asText());
        assertEquals("NOT_PERFORMED_BY_CORE", core.sourceSchemaValidation());
        assertTrue(capability.json().contains("\"core\":" + core.projectionJson()));
        assertArrayEquals(text.getBytes(StandardCharsets.UTF_8), page.rawPage().body());
    }

    @Test
    void guardOwnedProjectorRejectsForeignProducerAndReplay() throws Exception {
        final var plan =
                ObservedHttpTransportTestSupport.plan(ObservedExpansionCaptureTemplate.INV);
        final var guard = ObservedExpansionSchemaFixtures.bound(plan);
        final String text = "[" + ObservedExpansionSchemaFixtures.row(plan.template()) + "]";
        final var raw =
                new ObservedExpansionRawPage(
                        plan.request(1),
                        java.time.Instant.EPOCH,
                        text.getBytes(StandardCharsets.UTF_8));
        final var inspection = guard.inspect(raw);
        final var foreign = new ObservedExpansionPageProjector(plan).projectNextWithProof(raw);
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspection, foreign));
        final var real = guard.project(inspection);
        final var capability = guard.admit(inspection, real);
        assertEquals("observed-expansion-projector-proof-v1", capability.projectorProofVersion());
        assertTrue(real.proof().consumed());
        assertSame(real.proof().before(), capability.coreProgressBefore());
        assertSame(real.proof().after(), capability.coreProgressAfter());
        assertThrows(IllegalArgumentException.class, () -> guard.admit(inspection, real));
    }

    @Test
    void guardAdmissionRequiresCompleteOrderedPrefixOfGenuineSteps() throws Exception {
        final var plan =
                ObservedHttpTransportTestSupport.plan(ObservedExpansionCaptureTemplate.INV);
        final var guard = ObservedExpansionSchemaFixtures.bound(plan);
        final byte[] body =
                ("[" + ObservedExpansionSchemaFixtures.row(plan.template()) + "]")
                        .getBytes(StandardCharsets.UTF_8);
        final var first =
                guard.inspect(
                        new ObservedExpansionRawPage(
                                plan.request(1), java.time.Instant.EPOCH, body));
        final var stepOne = guard.project(first);
        final var second =
                guard.inspect(
                        new ObservedExpansionRawPage(
                                plan.request(2), java.time.Instant.EPOCH, body));
        final var stepTwo = guard.project(second);
        assertThrows(IllegalArgumentException.class, () -> guard.admit(second, stepTwo));
        final var capOne = guard.admit(first, stepOne);
        final var capTwo = guard.admit(second, stepTwo);
        assertEquals(1, capOne.admittedPages());
        assertEquals(2, capTwo.admittedPages());
        assertEquals(
                stepOne.proof().after().nextOccurrence(),
                stepTwo.proof().before().nextOccurrence());
        assertEquals(2L * body.length, capTwo.admittedRawBytes());
    }

    @Test
    void pageAdmissionSurfaceHasNoDtoOnlyOverload() {
        for (final var method : ObservedExpansionCaptureResult.Page.class.getDeclaredMethods()) {
            if (method.getName().equals("admitted")) {
                assertEquals(
                        ObservedExpansionPageProjector.ProvenProjection.class,
                        method.getParameterTypes()[1]);
            }
        }
    }

    private Path configuration() throws Exception {
        return ObservedCaptureDriverFixtures.configuration(temporaryConfigurationDirectory);
    }
}
