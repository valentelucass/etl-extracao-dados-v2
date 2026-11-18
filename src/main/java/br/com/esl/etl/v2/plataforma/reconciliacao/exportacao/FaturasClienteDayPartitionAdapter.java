package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** One civil day against the immutable whole weekly workbook; never a weekly approval. */
public final class FaturasClienteDayPartitionAdapter {
    public static final String POLICY = "SERVICE_AT_CIVIL_DATE_SEVEN_EXHAUSTIVE_DAYS_V1";

    private FaturasClienteDayPartitionAdapter() {}

    public static ObjectNode compare(
            final JsonNode request,
            final JsonNode sourcePinValues,
            final ExportValidationSupport.Pin workbook,
            final ExportValidationSupport.Pin catalogPin,
            final String selectedDay,
            final boolean emptyDayProofVerified,
            final ExportValidationSupport.Budget budget)
            throws Exception {
        FaturasClienteIndependentRequest.validate(request);
        final var start = LocalDate.parse(text(request, "periodStart"));
        final var end = LocalDate.parse(text(request, "periodEnd"));
        final var day = LocalDate.parse(selectedDay);
        require(
                day.toString().equals(selectedDay)
                        && ChronoUnit.DAYS.between(start, end) == 6
                        && !day.isBefore(start)
                        && !day.isAfter(end),
                "FAT_DAY_EXACT_WEEK_AND_SELECTED_CIVIL_DAY");
        final int weekday = Math.toIntExact(ChronoUnit.DAYS.between(start, day)) + 1;
        final var sources = sources(sourcePinValues);
        require(
                sources.isEmpty() == emptyDayProofVerified,
                "FAT_DAY_ZERO_REQUIRES_EXACT_EMPTY_PROOF");
        final var catalog = FaturasClienteContentAdapter.catalog(catalogPin, budget);
        final var fields = catalog.required("columns");
        final var columns = new ArrayList<ExactExportTable.Column>();
        final var originals = new ArrayList<ExactExportTable.Column>();
        int serviceIndex = -1;
        for (final var field : fields) {
            final int coordinate = field.path("nativeColumn").intValue();
            final boolean total = field.path("total").booleanValue();
            columns.add(
                    new ExactExportTable.Column(
                            text(field, "title"),
                            coordinate,
                            coordinate,
                            total,
                            total ? "BINARY64_UNITS_129" : "NONE"));
            originals.add(
                    new ExactExportTable.Column(
                            text(field, "title"), coordinate, coordinate, false));
            if ("service_at".equals(text(field, "sourceField"))) {
                serviceIndex = coordinate - 1;
            }
        }
        require(serviceIndex >= 0, "FAT_DAY_SERVICE_COORDINATE_REQUIRED");
        final var left = new KeylessExactExportTable(columns, budget);
        final var right = new KeylessExactExportTable(columns, budget);
        final var leftOriginal = new KeylessExactExportTable(originals, budget);
        final var rightOriginal = new KeylessExactExportTable(originals, budget);
        final long[] apiNumeric = new long[52];
        final long[] uiNumeric = new long[52];
        final long[] wholeNumeric = new long[52];
        long sourceRows = 0;
        long sourceBytes = 0;
        for (final var source : sources) {
            final byte[] bytes = source.read(8388608, budget);
            sourceBytes = Math.addExact(sourceBytes, bytes.length);
            require(sourceBytes <= 8388608, "FAT_DAY_ALL_SOURCE_BYTES_8MIB");
            require(bytes[bytes.length - 1] == '\n', "FAT_DAY_SOURCE_FINAL_LF");
            long fileRows = 0;
            try (var reader =
                    new BufferedReader(
                            new InputStreamReader(
                                    new ByteArrayInputStream(bytes),
                                    StandardCharsets.UTF_8
                                            .newDecoder()
                                            .onMalformedInput(CodingErrorAction.REPORT)
                                            .onUnmappableCharacter(CodingErrorAction.REPORT)))) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    budget.check();
                    require(
                            !line.isBlank() && line.length() <= 1049600 && ++sourceRows <= 12000,
                            "FAT_DAY_SOURCE_LINE_AND_ROWS_BOUND");
                    fileRows++;
                    final var record = FaturasClienteContentAdapter.record(line);
                    require(
                            record.keySet().equals(FaturasClienteContentAdapter.KINDS.keySet()),
                            "FAT_DAY_EXACT52_SOURCE_FIELDS");
                    final var presented = new ArrayList<ExactExportTable.Atom>();
                    final var raw = new ArrayList<ExactExportTable.Atom>();
                    for (int index = 0; index < fields.size(); index++) {
                        budget.check();
                        final var field = fields.get(index);
                        final var value = record.get(text(field, "sourceField"));
                        presented.add(
                                FaturasClienteContentAdapter.sourceValue(field, value, catalog));
                        raw.add(FaturasClienteContentAdapter.original(value));
                    }
                    require(
                            serviceDay(presented.get(serviceIndex), start, end).equals(day),
                            "FAT_DAY_SOURCE_OCCURRENCE_OUTSIDE_SELECTED_DAY");
                    countNumeric(fields, presented, apiNumeric);
                    left.add(presented);
                    leftOriginal.add(raw);
                }
            }
            require(fileRows > 0, "FAT_DAY_NONEMPTY_ORIGINAL_PIN_REQUIRED");
        }
        final var nativeRows =
                BoundedExportWorkbook.readFaturasClienteRows(
                        workbook.read(8388608, budget), columns, budget);
        require(
                "1900".equals(nativeRows.epoch()) && !nativeRows.rows().isEmpty(),
                "FAT_DAY_NATIVE_FULL_WEEK_EPOCH1900");
        long selectedNative = 0;
        final long[] nativeDayCounts = new long[7];
        for (final var row : nativeRows.rows()) {
            require(row.cells().size() == 52, "FAT_DAY_NATIVE_EXACT52_CELLS");
            final var presented = new ArrayList<ExactExportTable.Atom>();
            for (int index = 0; index < fields.size(); index++) {
                budget.check();
                presented.add(
                        FaturasClienteContentAdapter.nativeValue(
                                fields.get(index), row.cells().get(index), catalog));
            }
            final var civilDay = serviceDay(presented.get(serviceIndex), start, end);
            nativeDayCounts[Math.toIntExact(ChronoUnit.DAYS.between(start, civilDay))]++;
            countNumeric(fields, presented, wholeNumeric);
            if (civilDay.equals(day)) {
                countNumeric(fields, presented, uiNumeric);
                right.add(presented);
                rightOriginal.add(row.cells());
                selectedNative++;
            }
        }
        for (final var table : List.of(left, right, leftOriginal, rightOriginal)) {
            table.seal();
        }
        final var comparison = left.compare(right);
        // A zero-occurrence day keeps totalsReady=false; no synthetic numeric occurrence is added.
        final boolean equal =
                comparison.fullTuplesEqual()
                        && comparison.apiRows() == comparison.uiRows()
                        && comparison.apiOnlyOccurrences() == 0
                        && comparison.uiOnlyOccurrences() == 0
                        && comparison.columnsEqual().stream().allMatch(Boolean::booleanValue)
                        && comparison.exactDecimalTotalsEqual().stream()
                                .allMatch(Boolean::booleanValue)
                        && comparison.carrierTotalsEqual().stream().allMatch(Boolean::booleanValue);
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "FATURAS_CLIENTE_DAY_CONTENT_DIAGNOSTIC_V1")
                        .put("profile", FaturasClienteIndependentRequest.PROFILE)
                        .put("attemptId", text(request, "attemptId"))
                        .put("periodStart", start.toString())
                        .put("periodEnd", end.toString())
                        .put("day", day.toString())
                        .put("weekdayIndex", weekday)
                        .put("partitionPolicy", POLICY)
                        .put("partitionCount", 7)
                        .put("dayComparisonComplete", true)
                        .put("dayContentEqualUnderCandidatePresentation", equal)
                        .put(
                                "status",
                                equal ? "DAY_EQUAL_UNDER_CANDIDATE_PRESENTATION" : "DAY_DIFFERENCE")
                        .put("sourceDayRows", sourceRows)
                        .put("nativeWholeWeekRows", nativeRows.rows().size())
                        .put("selectedNativeDayRows", selectedNative)
                        .put("periodComparisonComplete", false)
                        .put("periodAccepted", false)
                        .put("contentEqualUnderCandidatePresentation", false)
                        .put("emptyDaySourceProofVerified", emptyDayProofVerified)
                        .put("allOriginalSourcesReadToEof", true)
                        .put("allNativeRowsReadToEof", true)
                        .put("allNativeRowsAssignedExactlyOneOfSevenDays", true)
                        .put("sourceOrNativeViewWritten", false)
                        .put("partialTotalsOnly", true)
                        .put("columns", 52)
                        .put("totalColumns", catalog.path("totalColumnCount").intValue())
                        .put("catalogNature", text(catalog, "catalogNature"))
                        .put(
                                "filterScopePolicy",
                                FaturasClienteIndependentRequest.FILTER_SCOPE_POLICY)
                        .put("updatedAtFilter", "")
                        .put("updatedAtScopeEnforced", false)
                        .put("serviceAtScopePolicy", "SOURCE_OFFSET_CIVIL_DATE_V1")
                        .put("serviceAtVerifiedForEveryRowBothSides", true)
                        .put("keylessPhysicalOccurrences", true)
                        .put("rootIdentityQualified", false)
                        .put("idOrMinutaCandidateUsedAsIdentity", false)
                        .put("deduplicationPerformed", false)
                        .put("catalogBindingPoliciesQualified", false)
                        .put("trustedSourceCompleteness", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false)
                        .put("mainResumeAllowed", false)
                        .put("floatArithmeticUsed", false)
                        .put("epsilonUsed", false)
                        .put("diagnosticOnly", true)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("catalogSha256", catalogPin.sha256())
                        .put("workbookSha256", workbook.sha256())
                        .put("retainedSourceBytesTotal", sourceBytes);
        result.set("comparison", ExportValidationSupport.JSON.valueToTree(comparison));
        result.putObject("fullValueProjection").set("api", left.fullValueProjection());
        ((ObjectNode) result.get("fullValueProjection")).set("ui", right.fullValueProjection());
        result.putObject("originalValueProjection").set("api", leftOriginal.fullValueProjection());
        ((ObjectNode) result.get("originalValueProjection"))
                .set("ui", rightOriginal.fullValueProjection());
        result.set("sourcePins", sourcePinValues.deepCopy());
        final var days = result.putArray("nativeWholeWeekDayCounts");
        for (int index = 0; index < 7; index++) {
            days.addObject()
                    .put("day", start.plusDays(index).toString())
                    .put("weekdayIndex", index + 1)
                    .put("rows", nativeDayCounts[index]);
        }
        final var numeric = result.putArray("totalNumericCounts");
        for (int index = 0; index < 52; index++) {
            final var field = fields.get(index);
            if (field.path("total").booleanValue()) {
                numeric.addObject()
                        .put("nativeColumn", index + 1)
                        .put("sourceField", text(field, "sourceField"))
                        .put("sourceDayNumericRows", apiNumeric[index])
                        .put("selectedNativeDayNumericRows", uiNumeric[index])
                        .put("nativeWholeWeekNumericRows", wholeNumeric[index]);
            }
        }
        final var diagnostics = result.putArray("columnDiagnostics");
        for (final var field : fields) {
            diagnostics.add(field.deepCopy());
        }
        for (final var source : sources) {
            source.read(8388608, budget);
        }
        workbook.read(8388608, budget);
        FaturasClienteContentAdapter.catalog(catalogPin, budget);
        result.put("pinsVerifiedBeforeAndAfter", true);
        return result;
    }

    static List<ExportValidationSupport.Pin> sources(final JsonNode values) {
        require(
                values != null && values.isArray() && values.size() <= 7,
                "FAT_DAY_ZERO_TO_SEVEN_ORIGINALS");
        final var result = new ArrayList<ExportValidationSupport.Pin>();
        final var unique = new HashSet<Path>();
        for (final var value : values) {
            final var fields = new HashSet<String>();
            value.fieldNames().forEachRemaining(fields::add);
            require(
                    value.isObject() && fields.equals(java.util.Set.of("path", "bytes", "sha256")),
                    "FAT_DAY_EXACT_NONEMPTY_SOURCE_PIN");
            final var pin = ExportValidationSupport.Pin.from(value);
            require(unique.add(pin.path()), "FAT_DAY_DUPLICATE_ORIGINAL_PATH");
            result.add(pin);
        }
        return List.copyOf(result);
    }

    private static void countNumeric(
            final JsonNode fields, final List<ExactExportTable.Atom> values, final long[] counts) {
        for (int index = 0; index < 52; index++) {
            if (fields.get(index).path("total").booleanValue()
                    && "NUMBER".equals(values.get(index).type())) {
                counts[index]++;
            }
        }
    }

    private static LocalDate serviceDay(
            final ExactExportTable.Atom value, final LocalDate start, final LocalDate end) {
        require(
                "TEXT".equals(value.type()) && value.value().length() >= 10,
                "FAT_DAY_SERVICE_VALUE");
        final var day = LocalDate.parse(value.value().substring(0, 10));
        require(!day.isBefore(start) && !day.isAfter(end), "FAT_DAY_SERVICE_OUTSIDE_FULL_WEEK");
        return day;
    }
}
