package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.closed;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.digest;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.fields;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.flag;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.number;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.samePin;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.scope;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ManifestosDayAssembler.track;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Explicit Root authorization to compare immutable closed RAW under a separately qualified
 * consumer. Runtime byte custody stays mandatory in the enclosing Original; metadata alone never
 * selects a JVM.
 */
final class ManifestosRawRevalidationBinding {
    final ExportValidationSupport.Pin pin;
    final ExportValidationSupport.Pin producerUnit;
    final ExportValidationSupport.Pin consumerUnit;
    final ExportValidationSupport.Pin producerTest;
    final ExportValidationSupport.Pin consumerTest;
    final String producerFingerprint;
    private final JsonNode binding;
    private final Map<ExportValidationSupport.Pin, JsonNode> captureSummaries;

    private ManifestosRawRevalidationBinding(
            final ExportValidationSupport.Pin pin,
            final JsonNode binding,
            final ExportValidationSupport.Pin producerUnit,
            final ExportValidationSupport.Pin consumerUnit,
            final ExportValidationSupport.Pin producerTest,
            final ExportValidationSupport.Pin consumerTest,
            final Map<ExportValidationSupport.Pin, JsonNode> captureSummaries) {
        this.pin = pin;
        this.binding = binding;
        this.producerUnit = producerUnit;
        this.consumerUnit = consumerUnit;
        this.producerTest = producerTest;
        this.consumerTest = consumerTest;
        producerFingerprint = digest(binding, "producerSourceConfigurationSha256");
        this.captureSummaries = Map.copyOf(captureSummaries);
    }

    static ManifestosRawRevalidationBinding open(
            final JsonNode input,
            final ExportValidationSupport.Pin consumerUnit,
            final String consumerFingerprint,
            final List<ExportValidationSupport.Pin> pins,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        if (!input.has("rawRevalidationBindingPin")) {
            return null;
        }
        final var pin = track(input.required("rawRevalidationBindingPin"), pins);
        final var binding = pin.json(262144, budget);
        fields(
                binding,
                Set.of(
                        "schema",
                        "approved",
                        "revalidationOnly",
                        "Source",
                        "HTTP",
                        "SQL",
                        "producerUnitPin",
                        "closedProducerTestRootPin",
                        "producerSourceConfigurationSha256",
                        "consumerUnitPin",
                        "closedConsumerTestRootPin",
                        "consumerSourceConfigurationSha256",
                        "consumerRuntimePin",
                        "consumerRuntimeClosurePin",
                        "catalogSha256",
                        "semanticScopeSha256",
                        "captures"));
        require(
                "ROOT_MANIFESTOS_CLOSED_RAW_REVALIDATION_BINDING_V1"
                        .equals(text(binding, "schema")),
                "MANIFESTOS_REVALIDATION_SCHEMA");
        flag(binding, "approved", true);
        flag(binding, "revalidationOnly", true);
        zeroSource(binding);
        samePin(binding.required("consumerUnitPin"), consumerUnit);
        require(
                consumerFingerprint.equals(digest(binding, "consumerSourceConfigurationSha256"))
                        && consumerFingerprint.equals(
                                digest(binding, "producerSourceConfigurationSha256"))
                        && ManifestosContentAdapter.CATALOG_SHA.equals(
                                digest(binding, "catalogSha256")),
                "MANIFESTOS_REVALIDATION_CONSUMER_SCOPE");
        digest(binding, "semanticScopeSha256");
        final var producerUnit = track(binding.required("producerUnitPin"), pins);
        final var producerTest = track(binding.required("closedProducerTestRootPin"), pins);
        final var consumerTest = track(binding.required("closedConsumerTestRootPin"), pins);
        producerUnit.read(8388608, budget);
        consumerUnit.read(8388608, budget);
        testRoot(producerTest, producerUnit, pins, budget);
        testRoot(consumerTest, consumerUnit, pins, budget);
        final var runtime =
                track(binding.required("consumerRuntimePin"), pins).json(2097152, budget);
        require(
                "ROOT_QUALIFIED_COMPLETE_RUNTIME_PACK0747_V1".equals(text(runtime, "schema"))
                        && runtime.path("dependencyJars").isArray()
                        && runtime.path("dependencyJars").size() == 8
                        && number(runtime, "entriesCount") >= 1546,
                "MANIFESTOS_REVALIDATION_CONSUMER_RUNTIME");
        for (final String flag :
                List.of(
                        "success",
                        "everyEntryReopenedAndHashed",
                        "allBuildDirectoryFilesIncluded",
                        "allOwnedQualifiedClassesIncluded")) {
            flag(runtime, flag, true);
        }
        flag(runtime, "classBytesModified", false);
        sameRootPin(runtime.required("unit"), consumerUnit);
        sameRootPin(runtime.required("test"), consumerTest);
        closedOriginal(
                track(binding.required("consumerRuntimeClosurePin"), pins).json(262144, budget));
        final var captures = binding.required("captures");
        require(
                captures.isArray() && !captures.isEmpty() && captures.size() <= 16,
                "MANIFESTOS_REVALIDATION_FINITE_CAPTURE_LIST");
        final var seen = new HashSet<ExportValidationSupport.Pin>();
        final var summaries = new HashMap<ExportValidationSupport.Pin, JsonNode>();
        for (final var entry : captures) {
            fields(entry, Set.of("capturePin", "captureClosurePin", "captureSummaryPin"));
            final var capture = track(entry.required("capturePin"), pins);
            require(seen.add(capture), "MANIFESTOS_REVALIDATION_DUPLICATE_CAPTURE");
            capture.read(262144, budget);
            sourceClosed(track(entry.required("captureClosurePin"), pins).json(262144, budget));
            final var summary =
                    track(entry.required("captureSummaryPin"), pins).json(262144, budget);
            require(
                    "ROOT_MANIFESTOS_RAW_PAGE_CAPTURE_CLOSED_CANDIDATE_V1"
                                    .equals(text(summary, "schema"))
                            && number(summary, "SourceLogicalCapture") == 1
                            && number(summary, "HTTP") == 2
                            && number(summary, "httpAdmitted") == 2
                            && number(summary, "SQL") == 0
                            && number(summary, "OriginalPid") > 0
                            && number(summary, "rows") <= 12000
                            && number(summary, "pageIndex") >= 1
                            && number(summary, "pageIndex") <= 16,
                    "MANIFESTOS_REVALIDATION_SOURCE_SUMMARY_COUNTERS");
            for (final String flag :
                    List.of("success", "sourceCountersFinal", "allOccurrencesRetained")) {
                flag(summary, flag, true);
            }
            for (final String flag :
                    List.of("contentAccepted", "entityQualified", "comparisonPassed")) {
                flag(summary, flag, false);
            }
            flag(summary, "terminalEmptyPageObserved", number(summary, "rows") == 0);
            sameRootPin(summary.required("test"), producerTest);
            sameRootPin(summary.required("sourceResult"), capture);
            summaries.put(capture, summary);
        }
        return new ManifestosRawRevalidationBinding(
                pin, binding, producerUnit, consumerUnit, producerTest, consumerTest, summaries);
    }

    void verifyCapture(
            final ExportValidationSupport.Pin capturePin,
            final ExportValidationSupport.Pin closurePin,
            final JsonNode capture,
            final JsonNode request)
            throws Exception {
        boolean admitted = false;
        for (final var entry : binding.required("captures")) {
            if (capturePin.equals(ExportValidationSupport.Pin.from(entry.required("capturePin")))) {
                samePin(entry.required("captureClosurePin"), closurePin);
                admitted = true;
            }
        }
        require(admitted, "MANIFESTOS_REVALIDATION_CAPTURE_NOT_ADMITTED");
        samePin(capture.required("codeUnitPin"), producerUnit);
        final var summary = captureSummaries.get(capturePin);
        sameRootPin(
                summary.required("request"),
                ExportValidationSupport.Pin.from(capture.required("requestPin")));
        sameRootPin(
                summary.required("dataPin"),
                ExportValidationSupport.Pin.from(capture.required("dataPin")));
        require(
                number(summary, "rows") == number(capture, "rows")
                        && number(summary, "pageIndex") == number(capture, "pageIndex")
                        && text(summary, "periodStart").equals(text(request, "periodStart"))
                        && text(summary, "periodEnd").equals(text(request, "periodEnd")),
                "MANIFESTOS_REVALIDATION_SOURCE_SUMMARY_BINDING");
        require(
                digest(binding, "semanticScopeSha256").equals(scope(request)),
                "MANIFESTOS_REVALIDATION_EXACT_DAY_SCOPE");
    }

    void bind(final ObjectNode execution, final ObjectNode summary) {
        execution.set("rawRevalidationBindingPin", ExportValidationSupport.JSON.valueToTree(pin));
        execution.set("producerUnitPin", ExportValidationSupport.JSON.valueToTree(producerUnit));
        execution.set("consumerUnitPin", ExportValidationSupport.JSON.valueToTree(consumerUnit));
        execution.set(
                "closedProducerTestRootPin",
                ExportValidationSupport.JSON.valueToTree(producerTest));
        execution.set(
                "closedConsumerTestRootPin",
                ExportValidationSupport.JSON.valueToTree(consumerTest));
        summary.put("rawRevalidatedUnderConsumer", true)
                .put("producerUnitSha256", producerUnit.sha256())
                .put("consumerUnitSha256", consumerUnit.sha256())
                .put("rawRevalidationBindingSha256", pin.sha256());
    }

    void verifyPageBinding(final JsonNode execution, final JsonNode summary) {
        samePin(execution.required("rawRevalidationBindingPin"), pin);
        samePin(execution.required("producerUnitPin"), producerUnit);
        samePin(execution.required("consumerUnitPin"), consumerUnit);
        samePin(execution.required("closedProducerTestRootPin"), producerTest);
        samePin(execution.required("closedConsumerTestRootPin"), consumerTest);
        flag(summary, "rawRevalidatedUnderConsumer", true);
        require(
                producerUnit.sha256().equals(digest(summary, "producerUnitSha256"))
                        && consumerUnit.sha256().equals(digest(summary, "consumerUnitSha256"))
                        && pin.sha256().equals(digest(summary, "rawRevalidationBindingSha256")),
                "MANIFESTOS_REVALIDATION_PAGE_IDENTITY");
    }

    private static void testRoot(
            final ExportValidationSupport.Pin pin,
            final ExportValidationSupport.Pin unit,
            final List<ExportValidationSupport.Pin> pins,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        final var test = pin.json(2097152, budget);
        require(
                "ROOT_INDEPENDENT_EXPORT_MAVEN_EXECUTED0721_V5".equals(text(test, "schema"))
                        && "TEST".equals(text(test, "mode"))
                        && number(test, "OriginalPid") > 0
                        && number(test, "syntheticTestCount") >= 467,
                "MANIFESTOS_REVALIDATION_CLOSED_TEST_ROOT");
        for (final String flag :
                List.of(
                        "success",
                        "syntheticTestsPassed",
                        "originalClosedKnown",
                        "stateRestored")) {
            flag(test, flag, true);
        }
        zeroSource(test);
        sameRootPin(test.required("unit"), unit);
        final var configurationPin = rootPin(test.required("configuration"));
        pins.add(configurationPin);
        final var configuration = configurationPin.json(2097152, budget);
        sameRootPin(configuration.required("unit"), unit);
        require(
                "TEST".equals(text(configuration, "mode")),
                "MANIFESTOS_REVALIDATION_TEST_CONFIGURATION");
        final var closurePin = rootPin(test.required("genericParentResult"));
        pins.add(closurePin);
        closedOriginal(closurePin.json(262144, budget));
    }

    private static void sourceClosed(final JsonNode closure) {
        closed(closure);
        require(
                "ROOT_MANIFESTOS_FINITE_DAILY_PART_SOURCE_CLOSURE0747_V1"
                                .equals(text(closure, "schema"))
                        && number(closure, "OriginalCalls") == 1
                        && number(closure, "SQL") == 0
                        && number(closure, "SourceMaximum") == 1
                        && number(closure, "HTTPMaximum") == 17
                        && !closure.has("Source")
                        && !closure.has("HTTP"),
                "MANIFESTOS_REVALIDATION_REAL_SOURCE_CLOSURE");
        flag(closure, "OriginalJobStarted", true);
        flag(closure, "sourceCountersNotInferred", true);
    }

    private static void closedOriginal(final JsonNode closure) {
        closed(closure);
        require(
                number(closure, "OriginalCalls") == 1
                        && number(closure, "SQL") == 0
                        && number(closure, "Source") == 0
                        && number(closure, "HTTP") == 0,
                "MANIFESTOS_REVALIDATION_PARENT_CLOSURE");
    }

    private static void zeroSource(final JsonNode node) {
        require(
                number(node, "Source") == 0
                        && number(node, "HTTP") == 0
                        && number(node, "SQL") == 0,
                "MANIFESTOS_REVALIDATION_SOURCE0_ONLY");
    }

    private static ExportValidationSupport.Pin rootPin(final JsonNode node) {
        require(
                node.path("bytes").isIntegralNumber() && node.path("bytes").canConvertToLong(),
                "MANIFESTOS_REVALIDATION_ROOT_PIN_SIZE");
        final String digest = text(node, "sha256");
        require(digest.matches("[a-fA-F0-9]{64}"), "MANIFESTOS_REVALIDATION_ROOT_PIN_DIGEST");
        return new ExportValidationSupport.Pin(
                Path.of(text(node, "path")),
                digest.toLowerCase(Locale.ROOT),
                node.path("bytes").longValue());
    }

    private static void sameRootPin(
            final JsonNode node, final ExportValidationSupport.Pin expected) {
        require(expected.equals(rootPin(node)), "MANIFESTOS_REVALIDATION_TEST_UNIT_PIN");
    }
}
