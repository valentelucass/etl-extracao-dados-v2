package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * One nonempty API page against all native occurrences of its roots. The returned evidence is
 * PRIVATE.
 */
public final class ManifestosPageAdapter {
    private ManifestosPageAdapter() {}

    /**
     * Persist the whole result only in private storage; only its summary child is suitable for
     * public output.
     */
    public static ObjectNode comparePage(
            final JsonNode request,
            final ExportValidationSupport.Pin rawApiArray,
            final ExportValidationSupport.Pin fullNativeWorkbook,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        return comparePageWithPostcheckProbe(
                request, rawApiArray, fullNativeWorkbook, budget, () -> {});
    }

    static ObjectNode comparePageWithPostcheckProbe(
            final JsonNode request,
            final ExportValidationSupport.Pin rawApiArray,
            final ExportValidationSupport.Pin fullNativeWorkbook,
            final ExportValidationSupport.Budget budget,
            final Runnable beforePostcheck)
            throws Exception {
        ManifestosIndependentRequest.validate(request);
        final var admitted = ((ObjectNode) request).deepCopy();
        ManifestosIndependentRequest.validate(admitted);
        final var catalog = ManifestosContentAdapter.catalog();
        final var fields = catalog.required("columns");
        final var columns = new ArrayList<ExactExportTable.Column>();
        final var rawColumns = new ArrayList<ExactExportTable.Column>();
        final var fieldNames = new HashSet<String>();
        for (final var field : fields) {
            final int coordinate = field.required("nativeColumn").intValue();
            columns.add(
                    new ExactExportTable.Column(
                            text(field, "title"),
                            coordinate,
                            coordinate,
                            field.required("total").booleanValue(),
                            text(field, "totalKind")));
            rawColumns.add(
                    new ExactExportTable.Column(
                            text(field, "title"), coordinate, coordinate, false));
            fieldNames.add(text(field, "sourceField"));
        }
        final var api = new ExactExportTable(columns, 2, budget);
        final var ui = new ExactExportTable(columns, 2, budget);
        final var apiRaw = new ExactExportTable(rawColumns, 2, budget);
        final var uiRaw = new ExactExportTable(rawColumns, 2, budget);
        final var sourceRoots = new TreeMap<String, Long>();
        final byte[] sourceBytes = rawApiArray.read(8388608, budget);
        final String sourceText =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(sourceBytes))
                        .toString();
        long sourceRows = 0;
        try (var parser = ExportValidationSupport.JSON.createParser(sourceText)) {
            require(
                    parser.nextToken() == JsonToken.START_ARRAY,
                    "MANIFESTOS_PAGE_RAW_ARRAY_REQUIRED");
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                budget.check();
                require(++sourceRows <= 12000, "MANIFESTOS_PAGE_ROW_BOUND");
                final var record = ManifestosContentAdapter.readRecord(parser);
                require(record.keySet().equals(fieldNames), "MANIFESTOS_PAGE_EXACT91_FIELDS");
                final var visual = new ArrayList<ExactExportTable.Atom>();
                final var original = new ArrayList<ExactExportTable.Atom>();
                for (final var field : fields) {
                    final var value = record.get(text(field, "sourceField"));
                    visual.add(ManifestosContentAdapter.sourceValue(field, value, catalog));
                    original.add(ManifestosContentAdapter.original(value));
                }
                sourceRoots.merge(visual.get(2).value(), 1L, Long::sum);
                require(sourceRoots.size() <= 100, "MANIFESTOS_PAGE_100_ROOT_BOUND");
                api.add(visual);
                apiRaw.add(original);
            }
            require(parser.nextToken() == null, "MANIFESTOS_PAGE_RAW_ARRAY_EOF");
        }
        require(sourceRows > 0, "MANIFESTOS_PAGE_EMPTY_REQUIRES_DAY_PROOF");
        final var nativeRows =
                BoundedExportWorkbook.readManifestosRows(
                        fullNativeWorkbook.read(8388608, budget), columns, budget);
        final var nativeAllRoots = new TreeMap<String, Long>();
        final var nativeSelectedRoots = new TreeMap<String, Long>();
        long selectedRows = 0;
        int timestampCells = 0;
        int noncanonicalTimestamps = 0;
        for (final var row : nativeRows) {
            budget.check();
            require("1900".equals(row.epoch()), "MANIFESTOS_QUALIFIED_EPOCH_1900_ONLY");
            final var key =
                    ManifestosContentAdapter.nativeValue(
                            fields.get(2),
                            row.cells().getOrDefault(3, new ExactExportTable.Atom("ABSENT", "")),
                            catalog);
            nativeAllRoots.merge(key.value(), 1L, Long::sum);
            if (!sourceRoots.containsKey(key.value())) {
                continue;
            }
            selectedRows++;
            nativeSelectedRoots.merge(key.value(), 1L, Long::sum);
            final var visual = new ArrayList<ExactExportTable.Atom>();
            final var original = new ArrayList<ExactExportTable.Atom>();
            for (final var field : fields) {
                final var atom =
                        row.cells()
                                .getOrDefault(
                                        field.required("nativeColumn").intValue(),
                                        new ExactExportTable.Atom("ABSENT", ""));
                final var converted = ManifestosContentAdapter.nativeValue(field, atom, catalog);
                visual.add(converted);
                original.add(atom);
                if ("timestamp".equals(text(field, "kind")) && "NUMBER".equals(atom.type())) {
                    timestampCells++;
                    if (!ManifestosContentAdapter.canonicalWholeSecondEncoding(
                            atom.value(), converted.value())) {
                        noncanonicalTimestamps++;
                    }
                }
            }
            ui.add(visual);
            uiRaw.add(original);
        }
        for (final var table : List.of(api, ui, apiRaw, uiRaw)) {
            table.seal();
        }
        final var comparison = api.compare(ui);
        final boolean valuesEqual =
                sourceRows == selectedRows
                        && sourceRoots.equals(nativeSelectedRoots)
                        && comparison.fullTuplesEqual()
                        && comparison.columnsEqual().size() == 91
                        && comparison.columnsEqual().stream().allMatch(Boolean::booleanValue)
                        && comparison.apiOnlyOccurrences() == 0
                        && comparison.uiOnlyOccurrences() == 0
                        && comparison.changedIds() == 0
                        && comparison.changedMultiplicityIds() == 0
                        && comparison.apiOnlyIds() == 0
                        && comparison.uiOnlyIds() == 0;
        final var semanticScope = admitted.deepCopy();
        semanticScope.remove("attemptId");
        final long missingRoots =
                sourceRoots.keySet().stream()
                        .filter(key -> !nativeAllRoots.containsKey(key))
                        .count();
        final var summary =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_PAGE_COMPARISON_SUMMARY_V1")
                        .put("profile", ManifestosIndependentRequest.PROFILE)
                        .put("attemptId", text(admitted, "attemptId"))
                        .put("periodStart", text(admitted, "periodStart"))
                        .put("periodEnd", text(admitted, "periodEnd"))
                        .put("catalogSha256", ManifestosContentAdapter.CATALOG_SHA)
                        .put(
                                "requestSha256",
                                ExportValidationSupport.sha256(
                                        ExportValidationSupport.canonicalBytes(admitted)))
                        .put(
                                "semanticScopeSha256",
                                ExportValidationSupport.sha256(
                                        ExportValidationSupport.canonicalBytes(semanticScope)))
                        .put("rawApiArraySha256", rawApiArray.sha256())
                        .put("nativeWorkbookSha256", fullNativeWorkbook.sha256())
                        .put("pageValuesEqual", valuesEqual)
                        .put("columns", 91)
                        .put("totalColumns", 50)
                        .put("sourceRows", sourceRows)
                        .put("nativeAllRows", nativeRows.size())
                        .put("nativeSelectedRows", selectedRows)
                        .put("nativeOutsidePageRows", nativeRows.size() - selectedRows)
                        .put("sourceDistinctRoots", sourceRoots.size())
                        .put("nativeAllDistinctRoots", nativeAllRoots.size())
                        .put("nativeSelectedDistinctRoots", nativeSelectedRoots.size())
                        .put("sourceRootsMissingFromNative", missingRoots)
                        .put("sourceArrayReadToEof", true)
                        .put("nativeReadToEof", true)
                        .put("rowTypesQualified", true)
                        .put("nativeSelectedRowTypesQualified", true)
                        .put("nativeAllRootTypesQualified", true)
                        .put("allPageOccurrencesCompared", true)
                        .put("allNativeRootValuesCompared", false)
                        .put("nativeSelectedTimestampCells", timestampCells)
                        .put("nativeSelectedNoncanonicalTimestamps", noncanonicalTimestamps)
                        .put("fullPeriodComparison", false)
                        .put("coverageComplete", false)
                        .put("comparisonPassed", false)
                        .put("totalsAreShardDiagnostics", true)
                        .put("sourceTerminalEmptyPageVerified", false)
                        .put("privateEvidenceRequiredForComposition", true)
                        .put("serviceDateVerifiedFromRows", false)
                        .put("globalDistinctIdsSummed", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false)
                        .put("sourceQueryAuthorized", false)
                        .put("mainResumeAllowed", false)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0);
        summary.putObject("limits")
                .put("maximumBytesPerInput", 8388608)
                .put("maximumRowsPerInput", 12000)
                .put("maximumSourceDistinctRoots", 100)
                .put("maximumLogicalCellVisits", 1200000)
                .put(
                        "logicalCellVisits",
                        91L * nativeRows.size() + 182L * (sourceRows + selectedRows))
                .put("cellVisitModel", "FULL_NATIVE_READ_PLUS_TWO_AXES_PER_SELECTED_SIDE");
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_PAGE_COMPARISON_PRIVATE_V1")
                        .put("containsPrivateRootEvidence", true);
        result.set("summary", summary);
        result.set("request", admitted);
        result.set("rawApiArrayPin", ExportValidationSupport.JSON.valueToTree(rawApiArray));
        result.set(
                "nativeWorkbookPin", ExportValidationSupport.JSON.valueToTree(fullNativeWorkbook));
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(comparison));
        final var roots =
                result.putObject("privateRootEvidence")
                        .put("keyModel", "SEQUENCE_CODE_EXACT_CANONICAL_INTEGER_V1")
                        .put("ordering", "LEXICOGRAPHIC_JAVA_UTF16_CANONICAL_INTEGER_TEXT");
        roots.set("sourceRoots", roots(sourceRoots));
        roots.set("nativeAllRoots", roots(nativeAllRoots));
        roots.set("nativeSelectedRoots", roots(nativeSelectedRoots));
        roots.put(
                "nativeAllRootsSha256",
                ExportValidationSupport.sha256(
                        ExportValidationSupport.canonicalBytes(roots.required("nativeAllRoots"))));
        summary.put("nativeAllRootsSha256", roots.path("nativeAllRootsSha256").textValue());
        final var projection = result.putObject("fullValueProjection");
        projection.set("api", api.fullValueProjection());
        projection.set("ui", ui.fullValueProjection());
        final var original = result.putObject("originalValueProjection");
        original.set("api", apiRaw.fullValueProjection());
        original.set("ui", uiRaw.fullValueProjection());
        final var shards = result.putArray("aggregateShards");
        int totalIndex = 0;
        for (int index = 0; index < fields.size(); index++) {
            final var field = fields.get(index);
            if (field.path("total").booleanValue()) {
                final var shard =
                        shards.addObject()
                                .put("totalIndex", totalIndex)
                                .put("nativeColumn", field.path("nativeColumn").intValue())
                                .put("sourceField", text(field, "sourceField"))
                                .put(
                                        "emptyAggregatePolicy",
                                        field.path("emptyAggregatePolicy").asText("NONE"));
                for (final String side : List.of("api", "ui")) {
                    final var total =
                            ((ObjectNode)
                                            projection
                                                    .required(side)
                                                    .required("totals")
                                                    .get(totalIndex))
                                    .deepCopy();
                    total.setAll(("api".equals(side) ? api : ui).totalAtomEvidence(index));
                    shard.set(side, total);
                }
                totalIndex++;
            }
        }
        beforePostcheck.run();
        rawApiArray.read(8388608, budget);
        fullNativeWorkbook.read(8388608, budget);
        ManifestosContentAdapter.catalog();
        budget.check();
        summary.put("pinsVerifiedBeforeAndAfter", true);
        require(
                ExportValidationSupport.JSON.writeValueAsBytes(result).length <= 8388608,
                "MANIFESTOS_PAGE_PRIVATE_RESULT_BOUND");
        budget.check();
        return result;
    }

    private static ArrayNode roots(final Map<String, Long> roots) {
        final var result = ExportValidationSupport.JSON.createArrayNode();
        roots.forEach(
                (key, count) ->
                        result.addObject()
                                .put("type", "NUMBER")
                                .put("value", key)
                                .put("occurrences", count));
        return result;
    }
}
