package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * Authored proof shapes only. Source/closure bits are synthetic test inputs, never physical
 * attestations.
 */
final class ManifestosDayFixture {
    static final String FINGERPRINT = "b".repeat(64);
    final ManifestosContentFixture data;
    final ExportValidationSupport.Pin code;
    final ExportValidationSupport.Pin closure;
    final Path privateRoot;
    ExportValidationSupport.Pin workbook;

    ManifestosDayFixture(final Path directory, final int rows) throws Exception {
        data = new ManifestosContentFixture(directory, rows);
        code = pin(ExportValidationSupport.JSON.createObjectNode().put("syntheticUnit", true));
        closure =
                pin(
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("schema", "SYNTHETIC_NO_PHYSICAL_EXECUTION")
                                .put("success", true)
                                .put("originalClosedKnown", true)
                                .put("stateRestored", true)
                                .put("slotRetained", false));
        privateRoot = directory.resolve(UUID.randomUUID() + "-private").toAbsolutePath();
        Files.createDirectory(privateRoot);
        refreshWorkbook();
    }

    void refreshWorkbook() throws Exception {
        workbook = data.pin("native.xlsx", data.workbookBytes());
    }

    ObjectNode capture(final int page, final List<ObjectNode> records) throws Exception {
        final var request = data.request.deepCopy().put("attemptId", UUID.randomUUID().toString());
        final var requestPin = pin(request);
        final var raw =
                data.pin("page.json", ExportValidationSupport.JSON.writeValueAsBytes(records));
        final var grant =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "ROOT_MANIFESTOS_FINITE_SOURCE_GRANT_V1")
                        .put("approved", true)
                        .put("codeUnitSha256", code.sha256())
                        .put("sourceConfigurationSha256", FINGERPRINT)
                        .put("pageScopePolicy", "EXACT_INFO_THEN_ONE_RAW_PAGE_V1")
                        .put("pageIndex", page)
                        .put("effectiveHTTPMaximum", 2);
        grant.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        final var roots = new HashSet<String>();
        records.forEach(
                record -> roots.add(record.path("sequence_code").bigIntegerValue().toString()));
        final var capture =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_RAW_PAGE_CAPTURE_V1")
                        .put("pageComplete", true)
                        .put("rawBytesRetained", true)
                        .put("wireOutcomeUnknown", false)
                        .put("pageIndex", page)
                        .put("per", 100)
                        .put("Source", 1)
                        .put("HTTP", 2)
                        .put("httpAdmitted", 2)
                        .put("SQL", 0)
                        .put("rows", records.size())
                        .put("roots", roots.size())
                        .put("terminalEmptyPageObserved", records.isEmpty())
                        .put("sourceConfigurationSha256", FINGERPRINT)
                        .put("rowTypesQualified", false)
                        .put("contentAccepted", false)
                        .put("entityQualified", false)
                        .put("productionAuthorized", false);
        capture.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
        capture.set("grantPin", ExportValidationSupport.JSON.valueToTree(pin(grant)));
        capture.set(
                "infoPin",
                ExportValidationSupport.JSON.valueToTree(
                        pin(ExportValidationSupport.JSON.createObjectNode())));
        capture.set("dataPin", ExportValidationSupport.JSON.valueToTree(raw));
        return capture;
    }

    ObjectNode pageInput(final ExportValidationSupport.Pin capture) {
        final var input =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_PAGE_DIAGNOSTIC_INPUT_V1")
                        .put("sourceConfigurationSha256", FINGERPRINT)
                        .put("privateRoot", privateRoot.toString());
        input.set("capturePin", ExportValidationSupport.JSON.valueToTree(capture));
        input.set("captureClosurePin", ExportValidationSupport.JSON.valueToTree(closure));
        input.set("nativeWorkbookPin", ExportValidationSupport.JSON.valueToTree(workbook));
        input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(code));
        return input;
    }

    ObjectNode entry(final int page, final List<ObjectNode> records) throws Exception {
        final var capture = pin(capture(page, records));
        final var result = ExportValidationSupport.JSON.createObjectNode().put("pageIndex", page);
        result.set("capturePin", ExportValidationSupport.JSON.valueToTree(capture));
        result.set("captureClosurePin", ExportValidationSupport.JSON.valueToTree(closure));
        if (!records.isEmpty()) {
            result.set(
                    "comparisonPin",
                    ExportValidationSupport.JSON.valueToTree(
                            pin(
                                    ManifestosPageDiagnosticMain.page(
                                            pageInput(capture),
                                            ManifestosContentFixture.budget()))));
            result.set("comparisonClosurePin", ExportValidationSupport.JSON.valueToTree(closure));
        }
        return result;
    }

    ObjectNode input(final List<ObjectNode> entries) throws Exception {
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "MANIFESTOS_DAY_ASSEMBLY_INPUT_V1")
                        .put("sourceConfigurationSha256", FINGERPRINT)
                        .put("privateRoot", privateRoot.toString());
        result.set("requestPin", ExportValidationSupport.JSON.valueToTree(pin(data.request)));
        result.set("nativeWorkbookPin", ExportValidationSupport.JSON.valueToTree(workbook));
        result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(code));
        final var pages = result.putArray("pages");
        entries.forEach(pages::add);
        return result;
    }

    ObjectNode completeInput() throws Exception {
        return input(
                List.of(
                        entry(1, List.of(data.records.get(0))),
                        entry(2, List.of(data.records.get(1))),
                        entry(3, List.of())));
    }

    ExportValidationSupport.Pin pin(final JsonNode value) throws Exception {
        return data.pin("proof.json", ExportValidationSupport.JSON.writeValueAsBytes(value));
    }

    ObjectNode read(final JsonNode pin) throws Exception {
        return (ObjectNode)
                ExportValidationSupport.Pin.from(pin)
                        .json(8388608, ManifestosContentFixture.budget());
    }
}
