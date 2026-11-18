package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManifestosSourceCaptureMainTest {
    @TempDir Path directory;

    @Test
    void threeDistinctFullWeeksHaveDistinctQueriesAndSameBoundedSourceFingerprint()
            throws Exception {
        String fingerprint = null;
        final var queries = new java.util.HashSet<String>();
        for (final String[] period :
                new String[][] {
                    {"2026-10-12", "2026-10-18"},
                    {"2026-10-19", "2026-10-25"},
                    {"2026-10-26", "2026-11-01"}
                }) {
            final var fixture = new ManifestosContentFixture(directory, 1);
            fixture.request.put("periodStart", period[0]).put("periodEnd", period[1]);
            ((ObjectNode) fixture.request.required("filters"))
                    .put(
                            ManifestosIndependentRequest.SERVICE_DATE_FILTER,
                            period[0] + " - " + period[1]);
            final var result =
                    ManifestosSourceCaptureMain.plan(
                            input(fixture), ManifestosContentFixture.budget());
            if (fingerprint == null) {
                fingerprint = result.required("sourceConfigurationSha256").textValue();
            }
            assertEquals(fingerprint, result.required("sourceConfigurationSha256").textValue());
            assertTrue(queries.add(result.required("finiteDataRequests").get(0).textValue()));
            assertEquals(16, result.required("finiteDataRequests").size());
            assertEquals(17, result.required("maximumHttpRequests").intValue());
            assertEquals(210, result.required("sourceSeconds").intValue());
            assertFalse(result.path("sourceQueryAuthorized").booleanValue());
            assertEquals(0, result.required("HTTP").intValue());
        }
    }

    @Test
    void planRefusesUnscopedOriginAndExtraAuthorityBeforeAnyCaptureComposition() throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 1);
        final var plain = input(fixture);
        ((ObjectNode) plain.required("sourceSettings")).put("apiOrigin", "http://capture.invalid");
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosSourceCaptureMain.plan(plain, ManifestosContentFixture.budget()));
        final var extra = input(fixture).put("sourceQueryAuthorized", true);
        assertThrows(
                IllegalArgumentException.class,
                () -> ManifestosSourceCaptureMain.plan(extra, ManifestosContentFixture.budget()));
    }

    @Test
    void captureWithoutRootPhysicalInputsRefusesAndNeverPrintsPrivateParserDetails()
            throws Exception {
        final var fixture = new ManifestosContentFixture(directory, 1);
        final var input = input(fixture).put("schema", "ROOT_MANIFESTOS_SOURCE_EXECUTION_INPUT_V1");
        final var pin =
                fixture.pin("input.json", ExportValidationSupport.JSON.writeValueAsBytes(input));
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                2,
                ManifestosSourceCaptureMain.run(
                        new String[] {
                            "capture", "--input", pin.path().toString(), "--sha256", pin.sha256()
                        },
                        new PrintStream(out),
                        new PrintStream(err)));
        assertEquals("", out.toString());
        assertEquals("MANIFESTOS_SOURCE_EXPLICIT_FIELDS" + System.lineSeparator(), err.toString());
    }

    @Test
    void boundedPlanModeSerializesSixDisjointScopesWithOriginalLimitsAndNoHttp() throws Exception {
        String fingerprint = null;
        final var queries = new HashSet<String>();
        for (final String start : List.of("2026-10-12", "2026-10-19", "2026-10-26")) {
            final var request = ManifestosBoundedWeekTest.week(start);
            final var input = boundedInput(request);
            final var result = runLocal("bounded-plan", input);
            assertEquals("MANIFESTOS_BOUNDED_WEEK_PLAN_V1", result.path("schema").textValue());
            if (fingerprint == null) {
                fingerprint = result.path("sourceConfigurationSha256").textValue();
            }
            assertEquals(fingerprint, result.path("sourceConfigurationSha256").textValue());
            for (final var part : result.required("parts")) {
                final var derived = part.required("request");
                final var plan = part.required("capturePlan");
                assertEquals(fingerprint, plan.path("sourceConfigurationSha256").textValue());
                assertEquals(16, plan.required("finiteDataRequests").size());
                assertTrue(queries.add(plan.required("finiteDataRequests").get(0).textValue()));
                for (int page = 1; page <= 16; page++) {
                    assertEquals(
                            ManifestosIndependentRequest.query(
                                            java.net.URI.create(
                                                    "https://capture.invalid/api/analytics/reports/6399/data"),
                                            derived,
                                            page,
                                            100)
                                    .toASCIIString(),
                            plan.path("finiteDataRequests").get(page - 1).textValue());
                }
                assertEquals(17, plan.path("maximumHttpRequests").intValue());
                assertEquals(16, plan.path("maximumDataPages").intValue());
                assertEquals(210, plan.path("sourceSeconds").intValue());
                assertEquals(12000, plan.path("maximumRows").intValue());
                assertEquals(8388608, plan.path("maximumResponseBytes").longValue());
                assertEquals(8388608, plan.path("maximumRetainedBytes").longValue());
                assertEquals(1073741824L, plan.path("originalMaximumBytes").longValue());
                assertEquals(300, plan.path("originalMaximumSeconds").intValue());
                assertEquals(0, plan.path("retries").intValue());
                assertSourceZero(plan);
            }
            assertSourceZero(result);
            assertFalse(result.path("comparisonPassed").booleanValue());
        }
        assertEquals(6, queries.size());
    }

    @Test
    void verifyWeekModeUsesTwoPinnedSummariesWithoutHttpAuthorityOrNewFiles() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-26");
        final var input = verifyInput(request);
        final long before = fileCount();
        final var result = runLocal("verify-week", input);
        assertEquals(before + 1, fileCount()); // Only the fixture's CLI input was written.
        assertEquals("MANIFESTOS_BOUNDED_WEEK_VERIFICATION_V1", result.path("schema").textValue());
        assertTrue(result.path("comparisonPassed").booleanValue());
        assertTrue(result.path("coverageComplete").booleanValue());
        assertEquals(30, result.path("sourceRows").longValue());
        final var serializedInput =
                ExportValidationSupport.JSON.readTree(
                        ExportValidationSupport.JSON.writeValueAsBytes(input));
        assertEquals(serializedInput.required("comparisonPins"), result.required("comparisonPins"));
        assertEquals(serializedInput.required("requestPin"), result.required("requestPin"));
        assertFalse(result.path("globalDistinctIdsSummed").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.path("entityQualified").booleanValue());
        assertFalse(result.path("productionAuthorized").booleanValue());
        assertSourceZero(result);
    }

    @Test
    void boundedPlanRejectsUnscopedOriginAndWrongSchema() throws Exception {
        final var input = boundedInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        ((ObjectNode) input.required("sourceSettings"))
                .put("apiOrigin", "https://capture.invalid/path");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.boundedPlan(
                                input, ManifestosContentFixture.budget()));
        final var wrong =
                boundedInput(ManifestosBoundedWeekTest.week("2026-10-12")).put("schema", "OTHER");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.boundedPlan(
                                wrong, ManifestosContentFixture.budget()));
    }

    @Test
    void sourceZeroModesRejectPhysicalAuthorityAndInlineUnpinnedComparisons() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        final var plan = boundedInput(request).put("sourceQueryAuthorized", true);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.boundedPlan(
                                plan, ManifestosContentFixture.budget()));
        final var verify = verifyInput(request);
        verify.set("comparisons", ManifestosBoundedWeekTest.summaries(request));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.verifyWeek(
                                verify, ManifestosContentFixture.budget()));
        verify.remove("comparisons");
        verify.put("physicalContextPin", "unauthorized");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.verifyWeek(
                                verify, ManifestosContentFixture.budget()));
    }

    @Test
    void verifyWeekRejectsWrongSchemaMissingPinsAndDuplicatePartPins() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        final var wrong = verifyInput(request).put("schema", "OTHER");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.verifyWeek(
                                wrong, ManifestosContentFixture.budget()));
        final var missing = verifyInput(request);
        ((com.fasterxml.jackson.databind.node.ArrayNode) missing.required("comparisonPins"))
                .remove(1);
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.verifyWeek(
                                missing, ManifestosContentFixture.budget()));
        final var duplicate = verifyInput(request);
        final var pins =
                (com.fasterxml.jackson.databind.node.ArrayNode)
                        duplicate.required("comparisonPins");
        pins.set(1, pins.get(0).deepCopy());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.verifyWeek(
                                duplicate, ManifestosContentFixture.budget()));
    }

    @Test
    void verifyWeekRejectsChangedSummaryPinWithoutPrintingResult() throws Exception {
        final var input = verifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        final var first = ExportValidationSupport.Pin.from(input.required("comparisonPins").get(0));
        Files.writeString(first.path(), "{\"success\":false}", StandardCharsets.UTF_8);
        assertLocalRefused("verify-week", input, "EXPORT_PIN_CHANGED");
    }

    @Test
    void verifyWeekPostcheckDetectsEitherSummaryOrRequestChangingAfterProofValidation()
            throws Exception {
        for (int changed = 0; changed < 3; changed++) {
            final var input = verifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
            final var selected =
                    changed < 2
                            ? input.required("comparisonPins").get(changed)
                            : input.required("requestPin");
            final var pin = ExportValidationSupport.Pin.from(selected);
            final var failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    ManifestosSourceCaptureMain.verifyWeekWithPostcheckProbe(
                                            input,
                                            ManifestosContentFixture.budget(),
                                            () -> {
                                                try {
                                                    Files.writeString(
                                                            pin.path(),
                                                            "{}",
                                                            StandardCharsets.UTF_8);
                                                } catch (final java.io.IOException writeFailed) {
                                                    throw new java.io.UncheckedIOException(
                                                            writeFailed);
                                                }
                                            }));
            assertEquals("EXPORT_PIN_CHANGED", failure.getMessage());
        }
    }

    @Test
    void bothSourceZeroModesRejectChangedWeeklyRequestPin() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        for (final String mode : List.of("bounded-plan", "verify-week")) {
            final var input =
                    "bounded-plan".equals(mode) ? boundedInput(request) : verifyInput(request);
            final var pin = ExportValidationSupport.Pin.from(input.required("requestPin"));
            Files.writeString(pin.path(), "{}", StandardCharsets.UTF_8);
            assertLocalRefused(mode, input, "EXPORT_PIN_CHANGED");
        }
    }

    @Test
    void verifyWeekRefusesSummaryAbove262144BytesBeforeParsing() throws Exception {
        final var input = verifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        final var oversized = pin(" ".repeat(262145).getBytes(StandardCharsets.UTF_8));
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.required("comparisonPins"))
                .set(0, ExportValidationSupport.JSON.valueToTree(oversized));
        assertLocalRefused("verify-week", input, "EXPORT_INPUT_BOUND");
    }

    @Test
    void verifyWeekRejectsTrailingJsonInPinnedSummary() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        final var input = verifyInput(request);
        final var trailing =
                pin(
                        (ManifestosBoundedWeekTest.summaries(request).get(0).toString() + "{}")
                                .getBytes(StandardCharsets.UTF_8));
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.required("comparisonPins"))
                .set(0, ExportValidationSupport.JSON.valueToTree(trailing));
        assertLocalRefused("verify-week", input, "EXPORT_JSON_TRAILING");
    }

    @Test
    void verifyWeekFailureReturnsNoPartialSuccessMetadata() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        final var input = verifyInput(request);
        final var failed = (ObjectNode) ManifestosBoundedWeekTest.summaries(request).get(1);
        failed.put("comparisonPassed", false);
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.required("comparisonPins"))
                .set(
                        1,
                        ExportValidationSupport.JSON.valueToTree(
                                pin(ExportValidationSupport.JSON.writeValueAsBytes(failed))));
        assertLocalRefused("verify-week", input, "MANIFESTOS_WEEK_INCOMPLETE_PROOF");
    }

    @Test
    void boundedDailyPlanModeSerializesTwentyOneDailyScopesWithUnchangedLimitsAndNoHttp()
            throws Exception {
        final var queries = new HashSet<String>();
        String fingerprint = null;
        for (final String start : List.of("2026-10-12", "2026-10-19", "2026-10-26")) {
            final var result =
                    runLocal(
                            "bounded-daily-plan",
                            boundedInput(ManifestosBoundedWeekTest.week(start)));
            assertEquals("MANIFESTOS_BOUNDED_WEEK_PLAN_V1", result.path("schema").textValue());
            assertEquals(
                    "DAILY_SEVEN_DISJOINT_SERVICE_DATES",
                    result.path("partitionPolicy").textValue());
            assertEquals(7, result.path("partCount").intValue());
            assertEquals(7, result.path("parts").size());
            if (fingerprint == null) {
                fingerprint = result.path("sourceConfigurationSha256").textValue();
            }
            assertEquals(fingerprint, result.path("sourceConfigurationSha256").textValue());
            for (int index = 0; index < 7; index++) {
                final var part = result.path("parts").get(index);
                final String day = java.time.LocalDate.parse(start).plusDays(index).toString();
                assertEquals(index + 1, part.path("partIndex").intValue());
                assertEquals(day, part.path("periodStart").textValue());
                assertEquals(day, part.path("periodEnd").textValue());
                final var plan = part.required("capturePlan");
                assertEquals(fingerprint, plan.path("sourceConfigurationSha256").textValue());
                assertEquals(16, plan.path("finiteDataRequests").size());
                assertTrue(queries.add(plan.path("finiteDataRequests").get(0).textValue()));
                for (int page = 1; page <= 16; page++) {
                    assertEquals(
                            ManifestosIndependentRequest.query(
                                            java.net.URI.create(
                                                    "https://capture.invalid/api/analytics/reports/6399/data"),
                                            part.required("request"),
                                            page,
                                            100)
                                    .toASCIIString(),
                            plan.path("finiteDataRequests").get(page - 1).textValue());
                }
                assertEquals(17, plan.path("maximumHttpRequests").intValue());
                assertEquals(16, plan.path("maximumDataPages").intValue());
                assertEquals(210, plan.path("sourceSeconds").intValue());
                assertEquals(12000, plan.path("maximumRows").intValue());
                assertEquals(8388608, plan.path("maximumResponseBytes").longValue());
                assertEquals(8388608, plan.path("maximumRetainedBytes").longValue());
                assertEquals(1073741824L, plan.path("originalMaximumBytes").longValue());
                assertEquals(300, plan.path("originalMaximumSeconds").intValue());
                assertEquals(0, plan.path("retries").intValue());
                assertSourceZero(plan);
            }
            assertSourceZero(result);
            assertFalse(result.path("comparisonPassed").booleanValue());
        }
        assertEquals(21, queries.size());
    }

    @Test
    void dailyPlanModeUsesTheSameStrictSchemaAndRefusesAuthorityAndNonWeekInputs()
            throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        assertLocalRefused(
                "bounded-daily-plan",
                boundedInput(request).put("schema", "OTHER"),
                "MANIFESTOS_BOUNDED_PLAN_SCHEMA");
        assertLocalRefused(
                "bounded-daily-plan",
                boundedInput(request).put("sourceQueryAuthorized", true),
                "MANIFESTOS_SOURCE_EXPLICIT_FIELDS");
        assertLocalRefused(
                "bounded-daily-plan",
                boundedInput(ManifestosBoundedWeekTest.week("2026-10-13")),
                "MANIFESTOS_WEEK_MONDAY_THROUGH_SUNDAY");
        final var invalidOrigin = boundedInput(request);
        ((ObjectNode) invalidOrigin.required("sourceSettings"))
                .put("apiOrigin", "http://capture.invalid");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ManifestosSourceCaptureMain.boundedDailyPlan(
                                invalidOrigin, ManifestosContentFixture.budget()));
    }

    @Test
    void verifyWeekModeAcceptsSevenPinnedDailyProofsWithoutHttpOrAuthority() throws Exception {
        final var input = dailyVerifyInput(ManifestosBoundedWeekTest.week("2026-10-26"));
        final var result = runLocal("verify-week", input);
        final var serializedInput =
                ExportValidationSupport.JSON.readTree(
                        ExportValidationSupport.JSON.writeValueAsBytes(input));
        assertEquals(serializedInput.required("comparisonPins"), result.required("comparisonPins"));
        assertEquals(serializedInput.required("requestPin"), result.required("requestPin"));
        assertEquals(7, result.path("partCount").intValue());
        assertEquals(7, result.path("parts").size());
        assertEquals(280, result.path("sourceRows").longValue());
        assertEquals(280, result.path("uiRows").longValue());
        assertEquals(
                "DAILY_SEVEN_DISJOINT_SERVICE_DATES", result.path("partitionPolicy").textValue());
        assertTrue(result.path("comparisonPassed").booleanValue());
        assertTrue(result.path("coverageComplete").booleanValue());
        assertFalse(result.path("globalDistinctIdsSummed").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.path("entityQualified").booleanValue());
        assertFalse(result.path("productionAuthorized").booleanValue());
        assertSourceZero(result);
    }

    @Test
    void verifyWeekPinBundleAllowsOnlyTwoOrSevenAndRejectsAboveSevenBeforeReadingPins()
            throws Exception {
        final var original = dailyVerifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        for (final int count : List.of(0, 1, 3, 4, 5, 6, 8)) {
            final var input = original.deepCopy();
            final var pins = input.putArray("comparisonPins");
            for (int index = 0; index < count; index++) {
                pins.add(original.path("comparisonPins").get(index % 7).deepCopy());
            }
            assertLocalRefused("verify-week", input, "MANIFESTOS_WEEK_EXACT_TWO_OR_SEVEN_PINS");
        }
    }

    @Test
    void sevenPinPrecheckDetectsChangesInEveryDailySummary() throws Exception {
        for (int changed = 0; changed < 7; changed++) {
            final var input = dailyVerifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
            final var selected =
                    ExportValidationSupport.Pin.from(input.required("comparisonPins").get(changed));
            Files.writeString(selected.path(), "{}", StandardCharsets.UTF_8);
            assertLocalRefused("verify-week", input, "EXPORT_PIN_CHANGED");
        }
    }

    @Test
    void sevenPinPostcheckDetectsChangesInAllDailySummariesAndWeeklyRequest() throws Exception {
        for (int changed = 0; changed < 8; changed++) {
            final var input = dailyVerifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
            final var selected =
                    changed < 7
                            ? input.required("comparisonPins").get(changed)
                            : input.required("requestPin");
            final var pin = ExportValidationSupport.Pin.from(selected);
            final var failure =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    ManifestosSourceCaptureMain.verifyWeekWithPostcheckProbe(
                                            input,
                                            ManifestosContentFixture.budget(),
                                            () -> {
                                                try {
                                                    Files.writeString(
                                                            pin.path(),
                                                            "{}",
                                                            StandardCharsets.UTF_8);
                                                } catch (final java.io.IOException writeFailed) {
                                                    throw new java.io.UncheckedIOException(
                                                            writeFailed);
                                                }
                                            }));
            assertEquals("EXPORT_PIN_CHANGED", failure.getMessage());
        }
    }

    @Test
    void sevenPinBundleRefusesOversizedSeventhSummaryAtTheExisting262144ByteCap() throws Exception {
        final var input = dailyVerifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        final var oversized = pin(" ".repeat(262145).getBytes(StandardCharsets.UTF_8));
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.required("comparisonPins"))
                .set(6, ExportValidationSupport.JSON.valueToTree(oversized));
        assertLocalRefused("verify-week", input, "EXPORT_INPUT_BOUND");
    }

    @Test
    void sevenPinBundleAcceptsSummaryExactlyAtTheExisting262144ByteCap() throws Exception {
        final var request = ManifestosBoundedWeekTest.week("2026-10-12");
        final var input = dailyVerifyInput(request);
        final byte[] summary =
                ExportValidationSupport.JSON.writeValueAsBytes(
                        ManifestosBoundedWeekTest.dailySummaries(request).get(6));
        final byte[] padded = new byte[262144];
        java.util.Arrays.fill(padded, (byte) ' ');
        System.arraycopy(summary, 0, padded, 0, summary.length);
        ((com.fasterxml.jackson.databind.node.ArrayNode) input.required("comparisonPins"))
                .set(6, ExportValidationSupport.JSON.valueToTree(pin(padded)));
        assertTrue(runLocal("verify-week", input).path("comparisonPassed").booleanValue());
    }

    @Test
    void verifyWeekRefusesSevenPinsOutOfChronologicalOrderWithoutPartialSuccess() throws Exception {
        final var input = dailyVerifyInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        final var pins =
                (com.fasterxml.jackson.databind.node.ArrayNode) input.required("comparisonPins");
        final var first = pins.get(0).deepCopy();
        pins.set(0, pins.get(1).deepCopy());
        pins.set(1, first);
        assertLocalRefused("verify-week", input, "MANIFESTOS_WEEK_DAILY_EXACT_ORDERED_COVERAGE");
    }

    @Test
    void boundedDailyPlanRejectsChangedWeeklyRequestPin() throws Exception {
        final var input = boundedInput(ManifestosBoundedWeekTest.week("2026-10-12"));
        final var selected = ExportValidationSupport.Pin.from(input.required("requestPin"));
        Files.writeString(selected.path(), "{}", StandardCharsets.UTF_8);
        assertLocalRefused("bounded-daily-plan", input, "EXPORT_PIN_CHANGED");
    }

    private ObjectNode dailyVerifyInput(final ObjectNode request) throws Exception {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_BOUNDED_WEEK_VERIFY_INPUT_V1");
        input.set(
                "requestPin",
                ExportValidationSupport.JSON.valueToTree(
                        pin(ExportValidationSupport.JSON.writeValueAsBytes(request))));
        final var pins = input.putArray("comparisonPins");
        for (final var summary : ManifestosBoundedWeekTest.dailySummaries(request)) {
            pins.add(
                    ExportValidationSupport.JSON.valueToTree(
                            pin(ExportValidationSupport.JSON.writeValueAsBytes(summary))));
        }
        return input;
    }

    private ObjectNode boundedInput(final ObjectNode request) throws Exception {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_BOUNDED_WEEK_PLAN_INPUT_V1");
        input.set(
                "requestPin",
                ExportValidationSupport.JSON.valueToTree(
                        pin(ExportValidationSupport.JSON.writeValueAsBytes(request))));
        input.putObject("sourceSettings")
                .put("sourceInstance", "fixture")
                .put("tenantScope", "fixture")
                .put("apiOrigin", "https://capture.invalid");
        return input;
    }

    private ObjectNode verifyInput(final ObjectNode request) throws Exception {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_BOUNDED_WEEK_VERIFY_INPUT_V1");
        input.set(
                "requestPin",
                ExportValidationSupport.JSON.valueToTree(
                        pin(ExportValidationSupport.JSON.writeValueAsBytes(request))));
        final var pins = input.putArray("comparisonPins");
        for (final var summary : ManifestosBoundedWeekTest.summaries(request)) {
            pins.add(
                    ExportValidationSupport.JSON.valueToTree(
                            pin(ExportValidationSupport.JSON.writeValueAsBytes(summary))));
        }
        return input;
    }

    private ExportValidationSupport.Pin pin(final byte[] bytes) throws Exception {
        final Path path = directory.resolve(UUID.randomUUID() + ".json").toAbsolutePath();
        Files.write(path, bytes);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }

    private ObjectNode runLocal(final String mode, final ObjectNode input) throws Exception {
        final var pin = pin(ExportValidationSupport.JSON.writeValueAsBytes(input));
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                0,
                ManifestosSourceCaptureMain.run(
                        new String[] {
                            mode, "--input", pin.path().toString(), "--sha256", pin.sha256()
                        },
                        new PrintStream(out),
                        new PrintStream(err)),
                err.toString());
        assertEquals("", err.toString());
        return (ObjectNode) ExportValidationSupport.JSON.readTree(out.toByteArray());
    }

    private void assertLocalRefused(final String mode, final ObjectNode input, final String code)
            throws Exception {
        final var pin = pin(ExportValidationSupport.JSON.writeValueAsBytes(input));
        final var out = new ByteArrayOutputStream();
        final var err = new ByteArrayOutputStream();
        assertEquals(
                2,
                ManifestosSourceCaptureMain.run(
                        new String[] {
                            mode, "--input", pin.path().toString(), "--sha256", pin.sha256()
                        },
                        new PrintStream(out),
                        new PrintStream(err)));
        assertEquals("", out.toString());
        assertEquals(code + System.lineSeparator(), err.toString());
    }

    private static void assertSourceZero(final JsonNode result) {
        assertFalse(result.path("sourceQueryAuthorized").booleanValue());
        for (final String field : List.of("Source", "HTTP", "SQL")) {
            assertTrue(result.path(field).isIntegralNumber());
            assertEquals(0, result.path(field).intValue());
        }
    }

    private long fileCount() throws Exception {
        try (var files = Files.list(directory)) {
            return files.count();
        }
    }

    private ObjectNode input(final ManifestosContentFixture fixture) throws Exception {
        final var request =
                fixture.pin(
                        "request.json",
                        ExportValidationSupport.JSON.writeValueAsBytes(fixture.request));
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_SOURCE_PLAN_INPUT_V1");
        input.set("requestPin", ExportValidationSupport.JSON.valueToTree(request));
        input.putObject("sourceSettings")
                .put("sourceInstance", "fixture")
                .put("tenantScope", "fixture")
                .put("apiOrigin", "https://capture.invalid");
        return input;
    }
}
