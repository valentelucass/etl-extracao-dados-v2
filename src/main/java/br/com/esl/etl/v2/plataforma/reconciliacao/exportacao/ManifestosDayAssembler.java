package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Source0 composition of pinned, closed page proofs. It never sends or retries a request. */
public final class ManifestosDayAssembler {
    private ManifestosDayAssembler() {}

    public static ObjectNode assemble(
            final JsonNode input, final ExportValidationSupport.Budget budget) throws Exception {
        return assembleWithPostcheckProbe(input, budget, () -> {});
    }

    static ObjectNode assembleWithPostcheckProbe(
            final JsonNode input,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        final var expectedFields =
                new HashSet<>(
                        Set.of(
                                "schema",
                                "requestPin",
                                "nativeWorkbookPin",
                                "codeUnitPin",
                                "sourceConfigurationSha256",
                                "privateRoot",
                                "pages"));
        if (input.has("rawRevalidationBindingPin")) {
            expectedFields.add("rawRevalidationBindingPin");
        }
        fields(input, expectedFields);
        require(
                "MANIFESTOS_DAY_ASSEMBLY_INPUT_V1".equals(text(input, "schema")),
                "MANIFESTOS_DAY_INPUT_SCHEMA");
        final var pins = new ArrayList<ExportValidationSupport.Pin>();
        final var requestPin = track(input.required("requestPin"), pins);
        final var request = requestPin.json(262144, budget);
        day(request);
        final String scope = scope(request);
        final var workbookPin = track(input.required("nativeWorkbookPin"), pins);
        final byte[] workbookBytes = workbookPin.read(8388608, budget);
        final var codePin = track(input.required("codeUnitPin"), pins);
        codePin.read(8388608, budget);
        final String fingerprint = digest(input, "sourceConfigurationSha256");
        final var revalidation =
                ManifestosRawRevalidationBinding.open(input, codePin, fingerprint, pins, budget);
        final var catalog = ManifestosContentAdapter.catalog();
        final var totalFields = new ArrayList<JsonNode>();
        catalog.required("columns")
                .forEach(
                        field -> {
                            if (field.path("total").booleanValue()) {
                                totalFields.add(field);
                            }
                        });
        require(totalFields.size() == 50, "MANIFESTOS_DAY_TOTAL_CATALOG");
        final var apiTotals = new ArrayList<Accumulator>();
        final var uiTotals = new ArrayList<Accumulator>();
        for (int index = 0; index < 50; index++) {
            apiTotals.add(new Accumulator());
            uiTotals.add(new Accumulator());
        }
        final var entries = input.required("pages");
        require(
                entries.isArray() && entries.size() >= 1 && entries.size() <= 16,
                "MANIFESTOS_DAY_PAGE_BOUND");
        final var union = new TreeMap<String, Long>();
        Map<String, Long> nativeAll = null;
        long totalRows = 0;
        int nonemptyPages = 0;
        final var pagePins = ExportValidationSupport.JSON.createArrayNode();
        for (int index = 0; index < entries.size(); index++) {
            budget.check();
            final var entry = entries.get(index);
            require(number(entry, "pageIndex") == index + 1, "MANIFESTOS_DAY_CONTIGUOUS_PAGES");
            final var capturePin = track(entry.required("capturePin"), pins);
            final var captureClosure = track(entry.required("captureClosurePin"), pins);
            final var capture =
                    capture(
                            capturePin,
                            captureClosure,
                            codePin,
                            fingerprint,
                            pins,
                            budget,
                            revalidation);
            require(
                    number(capture, "pageIndex") == index + 1,
                    "MANIFESTOS_DAY_CAPTURE_PAGE_BINDING");
            final var capturedRequestPin = track(capture.required("requestPin"), pins);
            final var capturedRequest = capturedRequestPin.json(262144, budget);
            require(scope.equals(scope(capturedRequest)), "MANIFESTOS_DAY_REQUEST_SCOPE");
            final var dataPin = track(capture.required("dataPin"), pins);
            final long rows = number(capture, "rows");
            if (rows == 0) {
                fields(entry, Set.of("pageIndex", "capturePin", "captureClosurePin"));
                require(
                        index == entries.size() - 1 && number(capture, "roots") == 0,
                        "MANIFESTOS_DAY_UNIQUE_TERMINAL_PAGE");
                final var empty = utf8Json(dataPin, budget);
                require(
                        empty.isArray() && empty.isEmpty(),
                        "MANIFESTOS_DAY_TERMINAL_ARRAY_NOT_EMPTY");
                pagePins.add(ExportValidationSupport.JSON.valueToTree(capturePin));
                continue;
            }
            require(index < entries.size() - 1, "MANIFESTOS_DAY_TERMINAL_EMPTY_REQUIRED");
            fields(
                    entry,
                    Set.of(
                            "pageIndex",
                            "capturePin",
                            "captureClosurePin",
                            "comparisonPin",
                            "comparisonClosurePin"));
            nonemptyPages++;
            final var comparisonPin = track(entry.required("comparisonPin"), pins);
            closed(track(entry.required("comparisonClosurePin"), pins).json(262144, budget));
            final var proof = comparisonPin.json(8388608, budget);
            require(
                    "MANIFESTOS_PAGE_COMPARISON_PRIVATE_V1".equals(text(proof, "schema")),
                    "MANIFESTOS_DAY_PAGE_PROOF_SCHEMA");
            final var summary = proof.required("summary");
            for (final String flag :
                    List.of(
                            "pageValuesEqual",
                            "sourceArrayReadToEof",
                            "nativeReadToEof",
                            "allPageOccurrencesCompared",
                            "rowTypesQualified",
                            "pinsVerifiedBeforeAndAfter")) {
                flag(summary, flag, true);
            }
            for (final String flag :
                    List.of(
                            "fullPeriodComparison",
                            "coverageComplete",
                            "comparisonPassed",
                            "contentAccepted",
                            "entityQualified",
                            "productionAuthorized")) {
                flag(summary, flag, false);
            }
            require(
                    ManifestosContentAdapter.CATALOG_SHA.equals(digest(summary, "catalogSha256"))
                            && scope.equals(digest(summary, "semanticScopeSha256"))
                            && rows == number(summary, "sourceRows")
                            && rows == number(summary, "nativeSelectedRows")
                            && number(summary, "columns") == 91
                            && fingerprint.equals(digest(summary, "sourceConfigurationSha256"))
                            && codePin.sha256().equals(digest(summary, "codeUnitSha256"))
                            && dataPin.sha256().equals(digest(summary, "rawApiArraySha256"))
                            && workbookPin.sha256().equals(digest(summary, "nativeWorkbookSha256")),
                    "MANIFESTOS_DAY_PAGE_SUMMARY_BINDING");
            samePin(proof.required("rawApiArrayPin"), dataPin);
            samePin(proof.required("nativeWorkbookPin"), workbookPin);
            require(scope.equals(scope(proof.required("request"))), "MANIFESTOS_DAY_PAGE_REQUEST");
            require(
                    ExportValidationSupport.sha256(
                                    ExportValidationSupport.canonicalBytes(capturedRequest))
                            .equals(
                                    ExportValidationSupport.sha256(
                                            ExportValidationSupport.canonicalBytes(
                                                    proof.required("request")))),
                    "MANIFESTOS_DAY_EXACT_CAPTURE_REQUEST");
            final var binding = proof.required("executionBinding");
            require(
                    "MANIFESTOS_PAGE_DIAGNOSTIC_BINDING_V1".equals(text(binding, "schema"))
                            && number(binding, "pageIndex") == index + 1
                            && fingerprint.equals(digest(binding, "sourceConfigurationSha256")),
                    "MANIFESTOS_DAY_BUILD_FINGERPRINT_BINDING");
            samePin(binding.required("codeUnitPin"), codePin);
            samePin(binding.required("capturePin"), capturePin);
            samePin(binding.required("captureClosurePin"), captureClosure);
            samePin(binding.required("requestPin"), capturedRequestPin);
            if (revalidation != null) {
                revalidation.verifyPageBinding(binding, summary);
            } else {
                require(
                        !binding.has("rawRevalidationBindingPin"),
                        "MANIFESTOS_DAY_REVALIDATION_BINDING_REQUIRED");
            }
            final var roots = proof.required("privateRootEvidence");
            require(
                    "SEQUENCE_CODE_EXACT_CANONICAL_INTEGER_V1".equals(text(roots, "keyModel")),
                    "MANIFESTOS_DAY_ROOT_MODEL");
            final var source = roots(roots.required("sourceRoots"));
            final var selected = roots(roots.required("nativeSelectedRoots"));
            final var all = roots(roots.required("nativeAllRoots"));
            require(
                    !source.isEmpty()
                            && source.size() <= 100
                            && source.equals(selected)
                            && count(source) == rows
                            && number(capture, "roots") == source.size()
                            && count(all) == number(summary, "nativeAllRows"),
                    "MANIFESTOS_DAY_ROOT_MULTIPLICITIES");
            require(
                    number(summary, "sourceDistinctRoots") == source.size()
                            && number(summary, "nativeAllDistinctRoots") == all.size()
                            && number(summary, "nativeSelectedDistinctRoots") == selected.size()
                            && number(summary, "nativeOutsidePageRows") == count(all) - rows
                            && number(summary, "sourceRootsMissingFromNative") == 0,
                    "MANIFESTOS_DAY_ROOT_SUMMARY_COUNTS");
            final String rootHash =
                    ExportValidationSupport.sha256(
                            ExportValidationSupport.canonicalBytes(
                                    roots.required("nativeAllRoots")));
            require(
                    rootHash.equals(digest(roots, "nativeAllRootsSha256"))
                            && rootHash.equals(digest(summary, "nativeAllRootsSha256")),
                    "MANIFESTOS_DAY_NATIVE_ROOT_HASH");
            if (nativeAll == null) {
                nativeAll = all;
            } else {
                require(nativeAll.equals(all), "MANIFESTOS_DAY_NATIVE_ROOT_SNAPSHOT");
            }
            for (final var root : source.entrySet()) {
                require(
                        union.putIfAbsent(root.getKey(), root.getValue()) == null,
                        "MANIFESTOS_DAY_OVERLAPPING_PAGE_ROOTS");
                require(
                        root.getValue().equals(all.get(root.getKey())),
                        "MANIFESTOS_DAY_SELECTED_ROOT_COVERAGE");
            }
            final var comparison = proof.required("comparison");
            flag(comparison, "fullTuplesEqual", true);
            require(
                    number(comparison, "apiRows") == rows && number(comparison, "uiRows") == rows,
                    "MANIFESTOS_DAY_COMPARISON_ROWS");
            require(allTrue(comparison.required("columnsEqual"), 91), "MANIFESTOS_DAY_91_BAGS");
            for (final String field :
                    List.of(
                            "apiOnlyOccurrences",
                            "uiOnlyOccurrences",
                            "changedIds",
                            "changedMultiplicityIds",
                            "apiOnlyIds",
                            "uiOnlyIds")) {
                require(number(comparison, field) == 0, "MANIFESTOS_DAY_PAGE_DIFFERENCE");
            }
            final var projections = proof.required("fullValueProjection");
            for (final String axis :
                    List.of(
                            "columnBags",
                            "totals",
                            "fullTupleBag",
                            "keyedTupleBags",
                            "idMultiplicityBag")) {
                require(
                        projections
                                .required("api")
                                .required(axis)
                                .equals(projections.required("ui").required(axis)),
                        "MANIFESTOS_DAY_VISUAL_PROJECTION_DIFFERENCE");
            }
            final var shards = proof.required("aggregateShards");
            require(shards.isArray() && shards.size() == 50, "MANIFESTOS_DAY_50_SHARDS");
            for (int total = 0; total < 50; total++) {
                final var field = totalFields.get(total);
                final var shard = shards.get(total);
                require(
                        number(shard, "totalIndex") == total
                                && number(shard, "nativeColumn") == number(field, "nativeColumn")
                                && text(shard, "sourceField").equals(text(field, "sourceField")),
                        "MANIFESTOS_DAY_TOTAL_COORDINATE");
                for (final String side : List.of("api", "ui")) {
                    final var value = shard.required(side);
                    final var projection = projections.required(side).required("totals").get(total);
                    require(
                            projection != null
                                    && text(value, "model").equals(text(field, "totalKind"))
                                    && number(projection, "index")
                                            == number(field, "nativeColumn") - 1
                                    && text(projection, "name").equals(text(field, "title")),
                            "MANIFESTOS_DAY_TOTAL_MODEL");
                    projection
                            .fields()
                            .forEachRemaining(
                                    part ->
                                            require(
                                                    part.getValue()
                                                            .equals(value.get(part.getKey())),
                                                    "MANIFESTOS_DAY_SHARD_PROJECTION_BINDING"));
                    ("api".equals(side) ? apiTotals : uiTotals).get(total).add(value, rows);
                }
            }
            totalRows = Math.addExact(totalRows, rows);
            require(totalRows <= 12000, "MANIFESTOS_DAY_ROW_BOUND");
            pagePins.add(ExportValidationSupport.JSON.valueToTree(comparisonPin));
        }
        final boolean emptyDay = nonemptyPages == 0;
        if (emptyDay) {
            require(entries.size() == 1, "MANIFESTOS_DAY_EMPTY_PAGE_ONE_ONLY");
            final var columns = new ArrayList<ExactExportTable.Column>();
            for (final var field : catalog.required("columns")) {
                final int coordinate = field.path("nativeColumn").intValue();
                columns.add(
                        new ExactExportTable.Column(
                                text(field, "title"), coordinate, coordinate, false));
            }
            require(
                    BoundedExportWorkbook.readManifestosRows(workbookBytes, columns, budget)
                            .isEmpty(),
                    "MANIFESTOS_DAY_EMPTY_NATIVE_NOT_EMPTY");
            nativeAll = Map.of();
        }
        require(
                nativeAll != null && union.equals(nativeAll) && totalRows == count(nativeAll),
                "MANIFESTOS_DAY_NATIVE_ONLY_OR_MISSING_COVERAGE");
        final var summary =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_DAY_COMPARISON_SUMMARY_V1")
                        .put("comparisonPassed", true)
                        .put("dayValuesEqual", true)
                        .put("fullPeriodComparison", true)
                        .put("coverageComplete", true)
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"))
                        .put("sourceRows", totalRows)
                        .put("uiRows", totalRows)
                        .put("distinctRoots", union.size())
                        .put("nonemptyPages", nonemptyPages)
                        .put("terminalPageIndex", entries.size())
                        .put("emptyDayProven", emptyDay)
                        .put("sourceTerminalEmptyPageObserved", true)
                        .put("nativeReadToEof", true)
                        .put("columns", 91)
                        .put("equalColumns", 91)
                        .put("totalColumns", 50)
                        .put("totalComparisonsApplicable", !emptyDay)
                        .put("catalogSha256", ManifestosContentAdapter.CATALOG_SHA)
                        .put("sourceConfigurationSha256", fingerprint)
                        .put("codeUnitSha256", codePin.sha256())
                        .put("semanticScopeSha256", scope)
                        .put("globalDistinctIdsSummed", false)
                        .put("serviceDateVerifiedFromRows", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false)
                        .put("sourceQueryAuthorized", false)
                        .put("mainResumeAllowed", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_DAY_COMPARISON_PRIVATE_V1")
                        .put("containsPrivateRootEvidence", true);
        result.set("summary", summary);
        result.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        result.set("nativeWorkbookPin", ExportValidationSupport.JSON.valueToTree(workbookPin));
        result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
        if (revalidation != null) {
            revalidation.bind(result, summary);
        }
        result.set("proofPins", pagePins);
        final var rootEvidence = result.putArray("privateRootEvidence");
        union.forEach(
                (key, rows) ->
                        rootEvidence
                                .addObject()
                                .put("type", "NUMBER")
                                .put("value", key)
                                .put("occurrences", rows));
        final var totals = result.putArray("aggregateTotals");
        final var equal = result.putArray("totalComparisonsEqual");
        int numericEqual = 0;
        int absenceEqual = 0;
        if (!emptyDay) {
            for (int index = 0; index < 50; index++) {
                final var field = totalFields.get(index);
                final var left = apiTotals.get(index);
                final var right = uiTotals.get(index);
                require(
                        left.numeric == right.numeric && left.nonvalues.equals(right.nonvalues),
                        "MANIFESTOS_DAY_TOTAL_NONVALUE_BAGS");
                final boolean numeric = left.numeric > 0;
                final boolean absence =
                        !numeric
                                && ManifestosContentAdapter.absenceTotalPolicy(field)
                                && left.allAbsent
                                && right.allAbsent;
                require(numeric || absence, "MANIFESTOS_DAY_TOTAL_UNQUALIFIED");
                final boolean same =
                        "BINARY64_UNITS_129".equals(text(field, "totalKind"))
                                ? left.units.equals(right.units)
                                : left.numerator
                                        .multiply(right.denominator)
                                        .equals(right.numerator.multiply(left.denominator));
                require(same, "MANIFESTOS_DAY_TOTAL_DIFFERENT");
                equal.add(true);
                numericEqual += numeric ? 1 : 0;
                absenceEqual += absence ? 1 : 0;
                final var total =
                        totals.addObject()
                                .put("nativeColumn", number(field, "nativeColumn"))
                                .put("sourceField", text(field, "sourceField"))
                                .put("model", text(field, "totalKind"))
                                .put(
                                        "comparisonKind",
                                        numeric
                                                ? "NUMERIC_TOTAL_EQUAL"
                                                : "ABSENCE_ONLY_EQUAL_NO_NUMERIC_SUM");
                total.set("api", left.projection());
                total.set("ui", right.projection());
            }
        }
        summary.put("numericTotalsEqual", numericEqual)
                .put("absenceOnlyTotalsEqual", absenceEqual)
                .put("equalTotals", equal.size());
        beforePostcheck.run();
        for (final var pin : pins) {
            pin.read(8388608, budget);
        }
        ManifestosContentAdapter.catalog();
        result.set("evidencePins", ExportValidationSupport.JSON.valueToTree(pins));
        summary.put("pinsVerifiedBeforeAndAfter", true);
        require(
                ExportValidationSupport.JSON.writeValueAsBytes(result).length <= 8388608,
                "MANIFESTOS_DAY_RESULT_BOUND");
        budget.check();
        return result;
    }

    static JsonNode capture(
            final ExportValidationSupport.Pin capturePin,
            final ExportValidationSupport.Pin closurePin,
            final ExportValidationSupport.Pin codePin,
            final String fingerprint,
            final List<ExportValidationSupport.Pin> pins,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return capture(capturePin, closurePin, codePin, fingerprint, pins, budget, null);
    }

    static JsonNode capture(
            final ExportValidationSupport.Pin capturePin,
            final ExportValidationSupport.Pin closurePin,
            final ExportValidationSupport.Pin codePin,
            final String fingerprint,
            final List<ExportValidationSupport.Pin> pins,
            final ExportValidationSupport.Budget budget,
            final ManifestosRawRevalidationBinding revalidation)
            throws Exception {
        final var producerUnit = revalidation == null ? codePin : revalidation.producerUnit;
        final String producerFingerprint =
                revalidation == null ? fingerprint : revalidation.producerFingerprint;
        closed(closurePin.json(262144, budget));
        final var capture = capturePin.json(262144, budget);
        require(
                "MANIFESTOS_RAW_PAGE_CAPTURE_V1".equals(text(capture, "schema")),
                "MANIFESTOS_DAY_CAPTURE_SCHEMA");
        flag(capture, "pageComplete", true);
        flag(capture, "rawBytesRetained", true);
        flag(capture, "wireOutcomeUnknown", false);
        require(
                number(capture, "pageIndex") >= 1
                        && number(capture, "pageIndex") <= 16
                        && number(capture, "per") == 100
                        && number(capture, "Source") == 1
                        && number(capture, "HTTP") == 2
                        && number(capture, "httpAdmitted") == 2
                        && number(capture, "SQL") == 0
                        && number(capture, "rows") <= 12000
                        && number(capture, "roots") <= 100
                        && producerFingerprint.equals(digest(capture, "sourceConfigurationSha256")),
                "MANIFESTOS_DAY_CAPTURE_FINAL_COUNTERS");
        flag(capture, "terminalEmptyPageObserved", number(capture, "rows") == 0);
        for (final String name :
                List.of("contentAccepted", "entityQualified", "productionAuthorized")) {
            flag(capture, name, false);
        }
        final var requestPin = track(capture.required("requestPin"), pins);
        final var capturedRequest = requestPin.json(262144, budget);
        day(capturedRequest);
        if (revalidation != null) {
            revalidation.verifyCapture(capturePin, closurePin, capture, capturedRequest);
        }
        final var grant = track(capture.required("grantPin"), pins).json(262144, budget);
        require(
                "ROOT_MANIFESTOS_FINITE_SOURCE_GRANT_V1".equals(text(grant, "schema"))
                        && producerUnit.sha256().equals(digest(grant, "codeUnitSha256"))
                        && producerFingerprint.equals(digest(grant, "sourceConfigurationSha256"))
                        && "EXACT_INFO_THEN_ONE_RAW_PAGE_V1"
                                .equals(grant.path("pageScopePolicy").asText())
                        && number(grant, "pageIndex") == number(capture, "pageIndex")
                        && number(grant, "effectiveHTTPMaximum") == 2,
                "MANIFESTOS_DAY_CAPTURE_BUILD_GRANT");
        flag(grant, "approved", true);
        samePin(grant.required("requestPin"), requestPin);
        track(capture.required("infoPin"), pins).read(262144, budget);
        track(capture.required("dataPin"), pins).read(8388608, budget);
        return capture;
    }

    static void fields(final JsonNode node, final Set<String> expected) {
        require(node != null && node.isObject(), "MANIFESTOS_DAY_OBJECT_REQUIRED");
        final var actual = new HashSet<String>();
        node.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), "MANIFESTOS_DAY_EXPLICIT_FIELDS");
    }

    static void closed(final JsonNode closure) {
        for (final String name : List.of("success", "originalClosedKnown", "stateRestored")) {
            flag(closure, name, true);
        }
        flag(closure, "slotRetained", false);
    }

    static void flag(final JsonNode node, final String name, final boolean expected) {
        require(
                node.path(name).isBoolean() && node.path(name).booleanValue() == expected,
                "MANIFESTOS_DAY_BOOLEAN_PROOF");
    }

    static long number(final JsonNode node, final String name) {
        final var value = node.path(name);
        require(
                value.isIntegralNumber() && value.canConvertToLong() && value.longValue() >= 0,
                "MANIFESTOS_DAY_INTEGER_PROOF");
        return value.longValue();
    }

    static String digest(final JsonNode node, final String name) {
        final String value = text(node, name);
        require(value.matches("[a-f0-9]{64}"), "MANIFESTOS_DAY_DIGEST");
        return value;
    }

    static void day(final JsonNode request) {
        ManifestosIndependentRequest.validate(request);
        require(
                text(request, "periodStart").equals(text(request, "periodEnd")),
                "MANIFESTOS_DAY_SINGLE_DATE");
    }

    static String scope(final JsonNode request) throws Exception {
        day(request);
        final var semantic = ((ObjectNode) request).deepCopy();
        semantic.remove("attemptId");
        return ExportValidationSupport.sha256(ExportValidationSupport.canonicalBytes(semantic));
    }

    static ExportValidationSupport.Pin track(
            final JsonNode node, final List<ExportValidationSupport.Pin> pins) {
        final var pin = ExportValidationSupport.Pin.from(node);
        if (!pins.contains(pin)) {
            pins.add(pin);
        }
        return pin;
    }

    static void samePin(final JsonNode node, final ExportValidationSupport.Pin pin) {
        require(pin.equals(ExportValidationSupport.Pin.from(node)), "MANIFESTOS_DAY_PIN_BINDING");
    }

    private static JsonNode utf8Json(
            final ExportValidationSupport.Pin pin, final ExportValidationSupport.Budget budget)
            throws Exception {
        final String content =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(pin.read(8388608, budget)))
                        .toString();
        try (var parser = ExportValidationSupport.JSON.createParser(content)) {
            final JsonNode node = ExportValidationSupport.JSON.readTree(parser);
            require(node != null && parser.nextToken() == null, "MANIFESTOS_DAY_EMPTY_RAW_EOF");
            return node;
        }
    }

    private static Map<String, Long> roots(final JsonNode entries) {
        require(entries.isArray() && entries.size() <= 12000, "MANIFESTOS_DAY_ROOT_ARRAY");
        final var roots = new TreeMap<String, Long>();
        String previous = null;
        for (final var entry : entries) {
            fields(entry, Set.of("type", "value", "occurrences"));
            final String key = text(entry, "value");
            require(
                    "NUMBER".equals(text(entry, "type"))
                            && key.length() <= 1024
                            && new BigInteger(key).toString().equals(key)
                            && (previous == null || previous.compareTo(key) < 0),
                    "MANIFESTOS_DAY_CANONICAL_ROOT");
            final long occurrences = number(entry, "occurrences");
            require(
                    occurrences > 0
                            && occurrences <= 12000
                            && roots.putIfAbsent(key, occurrences) == null,
                    "MANIFESTOS_DAY_ROOT_COUNT");
            previous = key;
        }
        require(count(roots) <= 12000, "MANIFESTOS_DAY_NATIVE_ROW_BOUND");
        return roots;
    }

    private static long count(final Map<String, Long> roots) {
        return roots.values().stream().mapToLong(Long::longValue).sum();
    }

    private static boolean allTrue(final JsonNode values, final int count) {
        if (!values.isArray() || values.size() != count) {
            return false;
        }
        for (final var value : values) {
            if (!value.isBoolean() || !value.booleanValue()) {
                return false;
            }
        }
        return true;
    }

    private static final class Accumulator {
        private BigInteger numerator = BigInteger.ZERO,
                denominator = BigInteger.ONE,
                units = BigInteger.ZERO;
        private long numeric;
        private boolean allAbsent = true;
        private final Map<ExactExportTable.Atom, Long> nonvalues =
                new TreeMap<>(
                        Comparator.comparing(ExactExportTable.Atom::type)
                                .thenComparing(ExactExportTable.Atom::value)
                                .thenComparing(ExactExportTable.Atom::carrier));

        void add(final JsonNode shard, final long rows) {
            final long numbers = number(shard, "numericOccurrences"),
                    missing = number(shard, "nonvalueOccurrences");
            require(
                    numbers + missing == rows
                            && number(shard.required("nonvalueBag"), "occurrences") == missing,
                    "MANIFESTOS_DAY_NUMERIC_OCCURRENCES");
            flag(shard, "allAtomsQualified", true);
            flag(shard, "ready", numbers > 0);
            final var atoms = shard.required("nonvalueAtoms");
            require(atoms.isArray() && atoms.size() <= 12000, "MANIFESTOS_DAY_NONVALUE_ARRAY");
            final var part = new java.util.HashMap<ExactExportTable.Atom, Long>();
            long counted = 0;
            for (final var value : atoms) {
                final var atom =
                        new ExactExportTable.Atom(
                                text(value, "type"),
                                value.required("value").textValue(),
                                value.required("carrier").textValue());
                final long occurrences = number(value, "occurrences");
                require(
                        List.of("ABSENT", "NULL", "BLANK").contains(atom.type())
                                && occurrences > 0
                                && part.putIfAbsent(atom, occurrences) == null,
                        "MANIFESTOS_DAY_UNQUALIFIED_NONVALUE");
                counted = Math.addExact(counted, occurrences);
            }
            final boolean absent =
                    numbers == 0
                            && rows > 0
                            && part.size() == 1
                            && part.getOrDefault(new ExactExportTable.Atom("ABSENT", ""), 0L)
                                    == rows;
            flag(shard, "absenceOnlyABSENT", absent);
            require(
                    counted == missing && (numbers > 0 || absent),
                    "MANIFESTOS_DAY_NONNUMERIC_SHARD_NOT_NEUTRAL");
            final BigInteger n = integer(shard, "exactDecimalNumerator"),
                    d = integer(shard, "exactDecimalDenominator");
            final BigInteger u = integer(shard, "sumOfEncodedCellUnits2PowMinus1074");
            require(
                    d.signum() > 0 && (numbers > 0 || n.signum() == 0 && u.signum() == 0),
                    "MANIFESTOS_DAY_ACCUMULATOR_SHAPE");
            require(
                    "BINARY64_UNITS_129".equals(text(shard, "model"))
                            ? n.equals(u.multiply(d))
                            : u.signum() == 0,
                    "MANIFESTOS_DAY_ACCUMULATOR_MODEL");
            numerator = numerator.multiply(d).add(n.multiply(denominator));
            denominator = denominator.multiply(d);
            final BigInteger gcd = numerator.gcd(denominator);
            numerator = numerator.divide(gcd);
            denominator = denominator.divide(gcd);
            units = units.add(u);
            numeric = Math.addExact(numeric, numbers);
            allAbsent &= absent;
            part.forEach((atom, count) -> nonvalues.merge(atom, count, Math::addExact));
            require(
                    numerator.abs().bitLength() <= 4096
                            && denominator.bitLength() <= 4096
                            && units.abs().bitLength() <= 2112,
                    "MANIFESTOS_DAY_EXACT_TOTAL_BOUND");
        }

        ObjectNode projection() {
            final var result =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("numericOccurrences", numeric)
                            .put("ready", numeric > 0)
                            .put("exactDecimalNumerator", numerator.toString())
                            .put("exactDecimalDenominator", denominator.toString())
                            .put("sumOfEncodedCellUnits2PowMinus1074", units.toString())
                            .put("absenceOnlyABSENT", allAbsent);
            final var atoms = result.putArray("nonvalueAtoms");
            nonvalues.forEach(
                    (atom, count) ->
                            atoms.addObject()
                                    .put("type", atom.type())
                                    .put("value", atom.value())
                                    .put("carrier", atom.carrier())
                                    .put("occurrences", count));
            return result;
        }

        private static BigInteger integer(final JsonNode node, final String name) {
            final String value = text(node, name);
            require(
                    value.length() <= 2048 && value.matches("-?(0|[1-9][0-9]*)"),
                    "MANIFESTOS_DAY_EXACT_INTEGER");
            return new BigInteger(value);
        }
    }
}
