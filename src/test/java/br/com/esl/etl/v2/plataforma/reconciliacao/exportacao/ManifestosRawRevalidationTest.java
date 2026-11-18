package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Authored metadata and bytes only. These tests never attest a physical Original, TEST or runtime.
 */
class ManifestosRawRevalidationTest {
    @TempDir Path directory;

    @Test
    void sameClosedRawCanBeComparedAndAssembledUnderDistinctQualifiedConsumerWithoutNewSource()
            throws Exception {
        final var scenario = new Scenario(2);
        final var captured = scenario.capturePins.get(0);
        final var rawPin =
                ExportValidationSupport.Pin.from(scenario.captures.get(0).required("dataPin"));
        final var raw = rawPin.json(8388608, ManifestosContentFixture.budget());
        final var result = scenario.page(0);
        assertTrue(result.path("summary").path("pageValuesEqual").booleanValue());
        assertEquals(
                scenario.fixture.code.sha256(),
                result.path("summary").path("producerUnitSha256").textValue());
        assertEquals(
                scenario.consumer.sha256(),
                result.path("summary").path("consumerUnitSha256").textValue());
        assertEquals(
                scenario.consumer.sha256(),
                result.path("summary").path("codeUnitSha256").textValue());
        assertEquals(
                captured,
                ExportValidationSupport.Pin.from(
                        result.path("executionBinding").required("capturePin")));
        assertEquals(raw, rawPin.json(8388608, ManifestosContentFixture.budget()));
        final var day =
                ManifestosDayAssembler.assemble(
                        scenario.dayInput(), ManifestosContentFixture.budget());
        assertTrue(day.path("summary").path("comparisonPassed").booleanValue());
        assertTrue(day.path("summary").path("sourceTerminalEmptyPageObserved").booleanValue());
        assertEquals(50, day.path("summary").path("equalTotals").intValue());
        assertEquals(0, day.path("summary").path("HTTP").intValue());
        assertFalse(day.path("summary").path("productionAuthorized").booleanValue());
    }

    @Test
    void distinctConsumerWithoutExplicitBindingKeepsTheLegacySameUnitGuard() throws Exception {
        final var scenario = new Scenario(1);
        final var input = scenario.pageInput(0);
        input.remove("rawRevalidationBindingPin");
        refused(input);
    }

    @Test
    void missingApprovalWrongCatalogOrNonzeroSourceCannotAuthorizeRevalidation() throws Exception {
        for (final String change : List.of("approval", "catalog", "Source", "HTTP", "SQL")) {
            final var scenario = new Scenario(1);
            if ("approval".equals(change)) {
                scenario.authorization.put("approved", false);
            } else if ("catalog".equals(change)) {
                scenario.authorization.put("catalogSha256", "f".repeat(64));
            } else {
                scenario.authorization.put(change, 1);
            }
            scenario.resealAuthorization();
            refused(scenario.pageInput(0));
        }
    }

    @Test
    void producerAndConsumerTestRootsMustBeClosedTestExecutionsOfTheirExactUnits()
            throws Exception {
        for (final String side : List.of("producer", "consumer")) {
            for (final String change :
                    List.of("unit", "mode", "open", "failed", "parent", "configuration")) {
                final var scenario = new Scenario(1);
                final String key =
                        "producer".equals(side)
                                ? "closedProducerTestRootPin"
                                : "closedConsumerTestRootPin";
                final var root = scenario.fixture.read(scenario.authorization.required(key));
                switch (change) {
                    case "unit" ->
                            root.set(
                                    "unit",
                                    ExportValidationSupport.JSON.valueToTree(scenario.otherUnit()));
                    case "mode" -> root.put("mode", "COMPILE");
                    case "open" -> root.put("originalClosedKnown", false);
                    case "failed" -> root.put("syntheticTestsPassed", false);
                    case "parent" ->
                            root.set(
                                    "genericParentResult",
                                    ExportValidationSupport.JSON.valueToTree(
                                            scenario.fixture.pin(
                                                    scenario.closed(false)
                                                            .put("slotRetained", true))));
                    default ->
                            root.set(
                                    "configuration",
                                    ExportValidationSupport.JSON.valueToTree(
                                            scenario.fixture.pin(
                                                    ExportValidationSupport.JSON
                                                            .createObjectNode()
                                                            .put("mode", "TEST")
                                                            .set(
                                                                    "unit",
                                                                    ExportValidationSupport.JSON
                                                                            .valueToTree(
                                                                                    scenario
                                                                                            .otherUnit())))));
                }
                scenario.authorization.set(
                        key, ExportValidationSupport.JSON.valueToTree(scenario.fixture.pin(root)));
                scenario.resealAuthorization();
                refused(scenario.pageInput(0));
            }
        }
    }

    @Test
    void consumerRuntimeMustBeCompleteUnmodifiedClosedAndBoundToConsumerTestAndUnit()
            throws Exception {
        for (final String change : List.of("test", "unit", "modified", "incomplete", "closure")) {
            final var scenario = new Scenario(1);
            final var runtime =
                    scenario.fixture.read(scenario.authorization.required("consumerRuntimePin"));
            switch (change) {
                case "test" ->
                        runtime.set(
                                "test",
                                scenario.authorization.required("closedProducerTestRootPin"));
                case "unit" ->
                        runtime.set(
                                "unit",
                                ExportValidationSupport.JSON.valueToTree(scenario.fixture.code));
                case "modified" -> runtime.put("classBytesModified", true);
                case "incomplete" -> runtime.put("allOwnedQualifiedClassesIncluded", false);
                default ->
                        scenario.authorization.set(
                                "consumerRuntimeClosurePin",
                                ExportValidationSupport.JSON.valueToTree(
                                        scenario.fixture.pin(
                                                scenario.closed(false).put("success", false))));
            }
            scenario.authorization.set(
                    "consumerRuntimePin",
                    ExportValidationSupport.JSON.valueToTree(scenario.fixture.pin(runtime)));
            scenario.resealAuthorization();
            refused(scenario.pageInput(0));
        }
    }

    @Test
    void exactCaptureAndItsClosedParentMustAppearOnceInTheFiniteAuthorization() throws Exception {
        for (final String change :
                List.of(
                        "absent",
                        "closure",
                        "duplicate",
                        "unclosed",
                        "inventedCounters",
                        "wrongCaps",
                        "inferred")) {
            final var scenario = new Scenario(1);
            final var entries =
                    (com.fasterxml.jackson.databind.node.ArrayNode)
                            scenario.authorization.required("captures");
            if ("absent".equals(change)) {
                entries.remove(0);
            } else if ("duplicate".equals(change)) {
                entries.add(entries.get(0).deepCopy());
            } else {
                final var closure = scenario.closed(true);
                if ("unclosed".equals(change)) {
                    closure.put("stateRestored", false);
                } else if ("inventedCounters".equals(change)) {
                    closure.put("Source", 1).put("HTTP", 2);
                } else if ("wrongCaps".equals(change)) {
                    closure.put("HTTPMaximum", 2);
                } else if ("inferred".equals(change)) {
                    closure.put("sourceCountersNotInferred", false);
                } else {
                    closure.put("differentClosedOccurrence", true);
                }
                ((ObjectNode) entries.get(0))
                        .set(
                                "captureClosurePin",
                                ExportValidationSupport.JSON.valueToTree(
                                        scenario.fixture.pin(closure)));
            }
            scenario.resealAuthorization();
            refused(scenario.pageInput(0));
        }
    }

    @Test
    void realSourceSummaryMustBindProducerTestRawRequestDataAndFinalObservedCounters()
            throws Exception {
        for (final String change :
                List.of(
                        "test",
                        "sourceResult",
                        "dataPin",
                        "request",
                        "counter",
                        "final",
                        "retained")) {
            final var scenario = new Scenario(1);
            final var entry = (ObjectNode) scenario.authorization.path("captures").get(0);
            final var summary = scenario.fixture.read(entry.required("captureSummaryPin"));
            switch (change) {
                case "test" ->
                        summary.set(
                                "test",
                                scenario.authorization.required("closedConsumerTestRootPin"));
                case "sourceResult", "dataPin", "request" ->
                        summary.set(
                                change,
                                ExportValidationSupport.JSON.valueToTree(scenario.otherUnit()));
                case "counter" -> summary.put("SourceLogicalCapture", 0);
                case "final" -> summary.put("sourceCountersFinal", false);
                default -> summary.put("allOccurrencesRetained", false);
            }
            entry.set(
                    "captureSummaryPin",
                    ExportValidationSupport.JSON.valueToTree(scenario.fixture.pin(summary)));
            scenario.resealAuthorization();
            refused(scenario.pageInput(0));
        }
    }

    @Test
    void revalidationPreservesActualSourceOneHttpTwoKnownOutcomeAndProducerGrant()
            throws Exception {
        for (final String change :
                List.of("HTTP", "Source", "unknown", "unit", "grant", "request")) {
            final var scenario = new Scenario(1);
            final var capture = scenario.captures.get(0);
            switch (change) {
                case "HTTP", "Source" -> capture.put(change, 0);
                case "unknown" -> capture.put("wireOutcomeUnknown", true);
                case "unit" ->
                        capture.set(
                                "codeUnitPin",
                                ExportValidationSupport.JSON.valueToTree(scenario.consumer));
                case "grant" -> {
                    final var grant = scenario.fixture.read(capture.required("grantPin"));
                    grant.put("codeUnitSha256", scenario.consumer.sha256());
                    capture.set(
                            "grantPin",
                            ExportValidationSupport.JSON.valueToTree(scenario.fixture.pin(grant)));
                }
                default ->
                        capture.set(
                                "requestPin",
                                ExportValidationSupport.JSON.valueToTree(
                                        scenario.fixture.pin(
                                                scenario.fixture
                                                        .data
                                                        .request
                                                        .deepCopy()
                                                        .put("periodStart", "2026-10-14")
                                                        .put("periodEnd", "2026-10-14"))));
            }
            scenario.resealCapture(0);
            refused(scenario.pageInput(0));
        }
    }

    @Test
    void consumerFingerprintAndExplicitConsumerUnitCannotBeSubstituted() throws Exception {
        final var scenario = new Scenario(1);
        refused(scenario.pageInput(0).put("sourceConfigurationSha256", "d".repeat(64)));
        final var input = scenario.pageInput(0);
        input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(scenario.otherUnit()));
        refused(input);
    }

    @Test
    void oldPassedPageCannotBePromotedAsIfItHadBeenComparedByTheNewConsumer() throws Exception {
        final var scenario = new Scenario(1);
        final var oldInput = scenario.fixture.pageInput(scenario.capturePins.get(0));
        oldInput.set(
                "captureClosurePin",
                ExportValidationSupport.JSON.valueToTree(scenario.sourceClosure));
        final var oldProof =
                ManifestosPageDiagnosticMain.page(oldInput, ManifestosContentFixture.budget());
        final var day = scenario.dayInput();
        ((ObjectNode) day.path("pages").get(0))
                .set(
                        "comparisonPin",
                        ExportValidationSupport.JSON.valueToTree(scenario.fixture.pin(oldProof)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosDayAssembler.assemble(day, ManifestosContentFixture.budget()));
    }

    @Test
    void terminalMustStillBeAnActualEmptyArrayAndEmptyDayHasNoManufacturedTotals()
            throws Exception {
        final var scenario = new Scenario(0);
        final var valid =
                ManifestosDayAssembler.assemble(
                        scenario.dayInput(), ManifestosContentFixture.budget());
        assertTrue(valid.path("summary").path("emptyDayProven").booleanValue());
        assertEquals(0, valid.path("totalComparisonsEqual").size());
        final var nonempty = ExportValidationSupport.JSON.createArrayNode();
        nonempty.addObject().put("notEmpty", true);
        scenario.captures
                .get(0)
                .set(
                        "dataPin",
                        ExportValidationSupport.JSON.valueToTree(scenario.fixture.pin(nonempty)));
        scenario.resealCapture(0);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosDayAssembler.assemble(
                                scenario.dayInput(), ManifestosContentFixture.budget()));
    }

    @Test
    void authorizationAndHistoricalRootMetadataAreRecheckedWithoutReopeningOldCompiledFiles()
            throws Exception {
        final var scenario = new Scenario(1);
        final var input = scenario.dayInput();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosDayAssembler.assembleWithPostcheckProbe(
                                input,
                                ManifestosContentFixture.budget(),
                                () -> {
                                    try {
                                        Files.writeString(scenario.authorizationPin.path(), "{}");
                                    } catch (final java.io.IOException failure) {
                                        throw new java.io.UncheckedIOException(failure);
                                    }
                                }));
        final var changed = new Scenario(1);
        final var testPin =
                ExportValidationSupport.Pin.from(
                        changed.authorization.required("closedProducerTestRootPin"));
        Files.writeString(testPin.path(), "{}");
        refused(changed.pageInput(0));
    }

    private void refused(final ObjectNode input) {
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosPageDiagnosticMain.page(input, ManifestosContentFixture.budget()));
    }

    private final class Scenario {
        static final String CONSUMER_FINGERPRINT = ManifestosDayFixture.FINGERPRINT;
        final ManifestosDayFixture fixture;
        final ExportValidationSupport.Pin consumer;
        final ExportValidationSupport.Pin sourceClosure;
        final ExportValidationSupport.Pin comparisonClosure;
        final List<ObjectNode> captures = new ArrayList<>();
        final List<ExportValidationSupport.Pin> capturePins = new ArrayList<>();
        final ObjectNode authorization;
        ExportValidationSupport.Pin authorizationPin;

        Scenario(final int rows) throws Exception {
            final Path root = Files.createTempDirectory(directory, "synthetic-revalidation-");
            fixture = new ManifestosDayFixture(root, rows);
            consumer =
                    fixture.pin(
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("syntheticConsumer", true));
            sourceClosure = fixture.pin(closed(true));
            comparisonClosure = fixture.pin(closed(false));
            final var producerTest = testRoot(fixture.code);
            final var consumerTest = testRoot(consumer);
            final var runtime =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_QUALIFIED_COMPLETE_RUNTIME_PACK0747_V1")
                            .put("syntheticFixtureNoPhysicalExecution", true)
                            .put("success", true)
                            .put("entriesCount", 1546)
                            .put("everyEntryReopenedAndHashed", true)
                            .put("allBuildDirectoryFilesIncluded", true)
                            .put("allOwnedQualifiedClassesIncluded", true)
                            .put("classBytesModified", false);
            runtime.set("unit", ExportValidationSupport.JSON.valueToTree(consumer));
            runtime.set("test", ExportValidationSupport.JSON.valueToTree(consumerTest));
            for (int index = 0; index < 8; index++) {
                runtime.withArray("dependencyJars").addObject();
            }
            authorization =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_MANIFESTOS_CLOSED_RAW_REVALIDATION_BINDING_V1")
                            .put("approved", true)
                            .put("revalidationOnly", true)
                            .put("Source", 0)
                            .put("HTTP", 0)
                            .put("SQL", 0)
                            .put(
                                    "producerSourceConfigurationSha256",
                                    ManifestosDayFixture.FINGERPRINT)
                            .put("consumerSourceConfigurationSha256", CONSUMER_FINGERPRINT)
                            .put("catalogSha256", ManifestosContentAdapter.CATALOG_SHA)
                            .put(
                                    "semanticScopeSha256",
                                    ManifestosDayAssembler.scope(fixture.data.request));
            authorization.set(
                    "producerUnitPin", ExportValidationSupport.JSON.valueToTree(fixture.code));
            authorization.set(
                    "consumerUnitPin", ExportValidationSupport.JSON.valueToTree(consumer));
            authorization.set(
                    "closedProducerTestRootPin",
                    ExportValidationSupport.JSON.valueToTree(producerTest));
            authorization.set(
                    "closedConsumerTestRootPin",
                    ExportValidationSupport.JSON.valueToTree(consumerTest));
            authorization.set(
                    "consumerRuntimePin",
                    ExportValidationSupport.JSON.valueToTree(fixture.pin(runtime)));
            authorization.set(
                    "consumerRuntimeClosurePin",
                    ExportValidationSupport.JSON.valueToTree(comparisonClosure));
            if (rows > 0) {
                captures.add(fixture.capture(1, fixture.data.records));
            }
            captures.add(fixture.capture(captures.size() + 1, List.of()));
            for (int index = 0; index < captures.size(); index++) {
                captures.get(index)
                        .set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(fixture.code));
                capturePins.add(fixture.pin(captures.get(index)));
                final var entry = authorization.withArray("captures").addObject();
                entry.set(
                        "capturePin",
                        ExportValidationSupport.JSON.valueToTree(capturePins.get(index)));
                entry.set(
                        "captureClosurePin",
                        ExportValidationSupport.JSON.valueToTree(sourceClosure));
                entry.set(
                        "captureSummaryPin",
                        ExportValidationSupport.JSON.valueToTree(
                                fixture.pin(captureSummary(index, producerTest))));
            }
            resealAuthorization();
        }

        ObjectNode closed(final boolean source) {
            final var closure =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("syntheticFixtureNoPhysicalExecution", true)
                            .put("success", true)
                            .put("originalClosedKnown", true)
                            .put("stateRestored", true)
                            .put("slotRetained", false)
                            .put("OriginalCalls", 1)
                            .put("SQL", 0);
            return source
                    ? closure.put(
                                    "schema",
                                    "ROOT_MANIFESTOS_FINITE_DAILY_PART_SOURCE_CLOSURE0747_V1")
                            .put("SourceMaximum", 1)
                            .put("HTTPMaximum", 17)
                            .put("sourceCountersNotInferred", true)
                            .put("OriginalJobStarted", true)
                    : closure.put("schema", "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1")
                            .put("Source", 0)
                            .put("HTTP", 0);
        }

        ObjectNode captureSummary(final int index, final ExportValidationSupport.Pin producerTest) {
            final var capture = captures.get(index);
            final var summary =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_MANIFESTOS_RAW_PAGE_CAPTURE_CLOSED_CANDIDATE_V1")
                            .put("success", true)
                            .put("sourceCountersFinal", true)
                            .put("allOccurrencesRetained", true)
                            .put("contentAccepted", false)
                            .put("entityQualified", false)
                            .put("comparisonPassed", false)
                            .put("SourceLogicalCapture", 1)
                            .put("HTTP", 2)
                            .put("httpAdmitted", 2)
                            .put("SQL", 0)
                            .put("OriginalPid", 1)
                            .put("rows", capture.path("rows").longValue())
                            .put("pageIndex", index + 1)
                            .put("terminalEmptyPageObserved", capture.path("rows").longValue() == 0)
                            .put(
                                    "periodStart",
                                    fixture.data.request.path("periodStart").textValue())
                            .put("periodEnd", fixture.data.request.path("periodEnd").textValue());
            summary.set(
                    "sourceResult",
                    ExportValidationSupport.JSON.valueToTree(capturePins.get(index)));
            summary.set("test", ExportValidationSupport.JSON.valueToTree(producerTest));
            summary.set("dataPin", capture.required("dataPin"));
            summary.set("request", capture.required("requestPin"));
            return summary;
        }

        ExportValidationSupport.Pin testRoot(final ExportValidationSupport.Pin unit)
                throws Exception {
            final var configuration =
                    ExportValidationSupport.JSON.createObjectNode().put("mode", "TEST");
            configuration.set("unit", ExportValidationSupport.JSON.valueToTree(unit));
            final var root =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_INDEPENDENT_EXPORT_MAVEN_EXECUTED0721_V5")
                            .put("mode", "TEST")
                            .put("syntheticFixtureNoPhysicalExecution", true)
                            .put("OriginalPid", 1)
                            .put("syntheticTestCount", 467)
                            .put("success", true)
                            .put("syntheticTestsPassed", true)
                            .put("originalClosedKnown", true)
                            .put("stateRestored", true)
                            .put("Source", 0)
                            .put("HTTP", 0)
                            .put("SQL", 0);
            root.set("unit", ExportValidationSupport.JSON.valueToTree(unit));
            root.set(
                    "configuration",
                    ExportValidationSupport.JSON.valueToTree(fixture.pin(configuration)));
            root.set(
                    "genericParentResult",
                    ExportValidationSupport.JSON.valueToTree(comparisonClosure));
            return fixture.pin(root);
        }

        ExportValidationSupport.Pin otherUnit() throws Exception {
            return fixture.pin(
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("differentSyntheticUnit", true));
        }

        void resealAuthorization() throws Exception {
            authorizationPin = fixture.pin(authorization);
        }

        void resealCapture(final int index) throws Exception {
            capturePins.set(index, fixture.pin(captures.get(index)));
            ((ObjectNode) authorization.path("captures").get(index))
                    .set(
                            "capturePin",
                            ExportValidationSupport.JSON.valueToTree(capturePins.get(index)));
            ((ObjectNode) authorization.path("captures").get(index))
                    .set(
                            "captureSummaryPin",
                            ExportValidationSupport.JSON.valueToTree(
                                    fixture.pin(
                                            captureSummary(
                                                    index,
                                                    ExportValidationSupport.Pin.from(
                                                            authorization.required(
                                                                    "closedProducerTestRootPin"))))));
            resealAuthorization();
        }

        ObjectNode pageInput(final int index) {
            final var input =
                    fixture.pageInput(capturePins.get(index))
                            .put("sourceConfigurationSha256", CONSUMER_FINGERPRINT);
            input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(consumer));
            input.set("captureClosurePin", ExportValidationSupport.JSON.valueToTree(sourceClosure));
            input.set(
                    "rawRevalidationBindingPin",
                    ExportValidationSupport.JSON.valueToTree(authorizationPin));
            return input;
        }

        ObjectNode page(final int index) throws Exception {
            return ManifestosPageDiagnosticMain.page(
                    pageInput(index), ManifestosContentFixture.budget());
        }

        ObjectNode dayInput() throws Exception {
            final var entries = new ArrayList<ObjectNode>();
            for (int index = 0; index < captures.size(); index++) {
                final var entry =
                        ExportValidationSupport.JSON.createObjectNode().put("pageIndex", index + 1);
                entry.set(
                        "capturePin",
                        ExportValidationSupport.JSON.valueToTree(capturePins.get(index)));
                entry.set(
                        "captureClosurePin",
                        ExportValidationSupport.JSON.valueToTree(sourceClosure));
                if (captures.get(index).path("rows").longValue() > 0) {
                    entry.set(
                            "comparisonPin",
                            ExportValidationSupport.JSON.valueToTree(fixture.pin(page(index))));
                    entry.set(
                            "comparisonClosurePin",
                            ExportValidationSupport.JSON.valueToTree(comparisonClosure));
                }
                entries.add(entry);
            }
            final var input =
                    fixture.input(entries).put("sourceConfigurationSha256", CONSUMER_FINGERPRINT);
            input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(consumer));
            input.set(
                    "rawRevalidationBindingPin",
                    ExportValidationSupport.JSON.valueToTree(authorizationPin));
            return input;
        }
    }
}
