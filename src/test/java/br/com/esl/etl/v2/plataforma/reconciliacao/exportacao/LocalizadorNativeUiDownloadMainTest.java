package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Artificial UI files, pins and ports only; never an executed ESL/Original attestation. */
class LocalizadorNativeUiDownloadMainTest {
    @TempDir Path directory;

    @Test
    void planIsSourceZeroAndHasOnlyOneUnauthenticatedDownloadWithNoMetadataRoute()
            throws Exception {
        final var fixture = new Fixture(directory);
        final var plan = LocalizadorNativeUiDownloadMain.plan(fixture.planInput());
        assertEquals(1, plan.path("maximumHttpRequests").intValue());
        assertEquals(0, plan.path("maximumMetadataHttpRequests").intValue());
        assertEquals(8388608, plan.path("maximumDownloadBytes").intValue());
        assertEquals("NONE", plan.path("downloadAuthentication").textValue());
        assertFalse(plan.has("metadataPath"));
        assertFalse(plan.has("metadataMethod"));
        assertEquals(0, plan.path("Source").intValue());
        assertFalse(plan.path("sourceAdmissionGranted").booleanValue());
        for (final String operation :
                List.of(
                        "metadata_then_download",
                        "requalify_retained_metadata_and_download",
                        "POST")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            LocalizadorNativeUiDownloadMain.plan(
                                    fixture.planInput().put("operation", operation)));
        }
    }

    @Test
    void exactStagedUiTargetIsTransferredOnceWithoutExposingTheUriOrClaimingNativeContent()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.observedUiLink("valid");
        fixture.downloadBytes = new byte[1048576 + 7];
        fixture.downloadBytes[0] = 'P';
        fixture.downloadBytes[1] = 'K';
        final var result = fixture.execute();
        assertTrue(result.path("downloadComplete").booleanValue());
        assertEquals(1, fixture.downloadCalls);
        assertEquals(1, result.path("HTTP").intValue());
        assertEquals(1, result.path("Source").intValue());
        assertEquals(0, result.path("metadataHttpThisInvocation").intValue());
        assertEquals("NONE", result.path("downloadAuthentication").textValue());
        assertTrue(result.path("uiObservedDownloadLinkUsed").booleanValue());
        assertFalse(result.path("nativeContentVerified").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertFalse(result.toString().contains("authored-signature"));
        assertFalse(result.toString().contains("targetUri"));
        assertFalse(result.has("metadataBodyPin"));
        assertArrayEquals(
                fixture.downloadBytes,
                ExportValidationSupport.Pin.from(result.required("dataPin"))
                        .read(8388608, budget()));
        assertTrue(fixture.downloadTarget.getRawPath().contains("localizador-de-cargas_"));
    }

    @Test
    void changedFilePeriodHrefStageCountersClosureOrLinkPinsRefuseBeforeHttp() throws Exception {
        for (final String mutation :
                List.of(
                        "file",
                        "period",
                        "href",
                        "stageHttp",
                        "closure",
                        "pin",
                        "host",
                        "path",
                        "flag",
                        "actor")) {
            final var fixture = new Fixture(directory);
            fixture.observedUiLink(mutation);
            final var result = fixture.execute();
            assertFalse(result.path("downloadComplete").booleanValue());
            assertEquals(0, fixture.downloadCalls);
            assertEquals(0, result.path("httpAdmitted").intValue());
        }
    }

    @Test
    void validBytesNeverPermitAChangedRootGrantOrATokenAuthenticatedDownload() throws Exception {
        for (final String mutation :
                List.of(
                        "approved",
                        "id",
                        "profile",
                        "http",
                        "metadata",
                        "auth",
                        "expired",
                        "pin")) {
            final var fixture = new Fixture(directory);
            fixture.observedUiLink("valid");
            final var oldPin =
                    ExportValidationSupport.Pin.from(
                            fixture.context.required("localizadorNativeGrantPin"));
            final var grant = (ObjectNode) oldPin.json(262144, budget());
            switch (mutation) {
                case "approved" -> grant.put("approved", false);
                case "id" -> grant.put("fileId", 43);
                case "profile" ->
                        grant.put("profile", "CONTAS_A_PAGAR_8636_ISSUE_DATE_NATIVE28_V1");
                case "http" -> grant.put("HTTPMaximum", 2);
                case "metadata" -> grant.put("INFOHTTPMaximum", 1);
                case "auth" -> grant.put("downloadAuthentication", "BEARER");
                case "expired" ->
                        grant.put("expiresUtc", fixture.clock.instant().minusSeconds(1).toString());
                default ->
                        grant.set(
                                "uiObservedLinkPin",
                                ExportValidationSupport.JSON.valueToTree(
                                        fixture.pin(new byte[] {1})));
            }
            fixture.context.set(
                    "localizadorNativeGrantPin",
                    ExportValidationSupport.JSON.valueToTree(fixture.pin(grant)));
            fixture.input.set(
                    "physicalContextPin",
                    ExportValidationSupport.JSON.valueToTree(fixture.pin(fixture.context)));
            final var result = fixture.execute();
            assertFalse(result.path("downloadComplete").booleanValue());
            assertEquals(0, fixture.downloadCalls);
        }
    }

    @Test
    void wrongUiReportProfileColumnCountOrFilledUpdatedFilterCannotSupplyTheIndependentNative()
            throws Exception {
        for (final String mutation : List.of("width", "report", "profile", "updated")) {
            final var fixture = new Fixture(directory);
            fixture.observedUiLink("valid");
            final var changed = fixture.acquisition.deepCopy();
            switch (mutation) {
                case "width" -> changed.put("observedColumns", 28);
                case "report" -> changed.put("reportId", 8636);
                case "profile" ->
                        changed.put("profile", "LOCALIZADOR_8656_OBSERVED26_COMPARISON_V1");
                default ->
                        ((ObjectNode) changed.required("filters"))
                                .put(LocalizadorIndependentRequest.UPDATED_AT_FILTER, "filled");
            }
            fixture.input.set(
                    "uiAcquisitionPin",
                    ExportValidationSupport.JSON.valueToTree(fixture.pin(changed)));
            assertThrows(IllegalArgumentException.class, fixture::execute);
            assertEquals(0, fixture.downloadCalls);
        }
    }

    @Test
    void anHttp403RetainsItsResponseAndNeverRegeneratesOrRetriesTheUiFile() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.observedUiLink("valid");
        fixture.downloadStatus = 403;
        final var result = fixture.execute();
        assertFalse(result.path("downloadComplete").booleanValue());
        assertEquals(1, fixture.downloadCalls);
        assertEquals(1, result.path("HTTP").intValue());
        assertTrue(result.has("downloadResponseBodyPin"));
        assertFalse(result.has("dataPin"));
        assertFalse(result.path("wireOutcomeUnknown").booleanValue());
        assertEquals(0, result.path("POST").intValue());
    }

    @Test
    void outputReuseCannotOverwriteAnEarlierTransferOrIssueASecondHttp() throws Exception {
        final var fixture = new Fixture(directory);
        fixture.observedUiLink("valid");
        final var result = fixture.execute();
        final var pin = ExportValidationSupport.Pin.from(result.required("executionReceiptPin"));
        final byte[] before = Files.readAllBytes(pin.path());
        assertThrows(IllegalArgumentException.class, fixture::execute);
        assertArrayEquals(before, Files.readAllBytes(pin.path()));
        assertEquals(1, fixture.downloadCalls);
    }

    @Test
    void anotherHostExtraInputOrLegacyOperationCannotExtendThePhysicalSurface() throws Exception {
        for (final String mutation : List.of("host", "extra", "operation")) {
            final var fixture = new Fixture(directory);
            fixture.observedUiLink("valid");
            switch (mutation) {
                case "host" -> fixture.input.putArray("downloadHosts").add("unobserved.invalid");
                case "extra" -> fixture.input.put("metadataPath", "/api/analytics/report_files/42");
                default -> fixture.input.put("operation", "metadata_then_download");
            }
            assertThrows(IllegalArgumentException.class, fixture::execute);
            assertEquals(0, fixture.downloadCalls);
        }
    }

    @Test
    void brazilianUiPeriodTextIsComparedCanonicallyWithoutRewritingEitherEvidenceFile()
            throws Exception {
        final var fixture = new Fixture(directory);
        final String uiText = "12/10/2026 - 18/10/2026";
        ((ObjectNode) fixture.acquisition.required("filters"))
                .put(LocalizadorIndependentRequest.SERVICE_AT_FILTER, uiText);
        ((ObjectNode) fixture.filterProof.required("filters"))
                .put(LocalizadorIndependentRequest.SERVICE_AT_FILTER, uiText);
        final var acquisitionPin = fixture.pin(fixture.acquisition);
        final var filterPin = fixture.pin(fixture.filterProof);
        fixture.input.set(
                "uiAcquisitionPin", ExportValidationSupport.JSON.valueToTree(acquisitionPin));
        fixture.input.set("uiFilterProofPin", ExportValidationSupport.JSON.valueToTree(filterPin));
        final byte[] before = Files.readAllBytes(acquisitionPin.path());
        fixture.observedUiLink("valid");
        final var result = fixture.execute();
        assertTrue(result.path("downloadComplete").booleanValue());
        assertArrayEquals(before, Files.readAllBytes(acquisitionPin.path()));
        assertEquals(
                uiText,
                acquisitionPin
                        .json(262144, budget())
                        .required("filters")
                        .path(LocalizadorIndependentRequest.SERVICE_AT_FILTER)
                        .textValue());
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }

    private static final class Fixture implements LocalizadorNativeUiDownloadMain.Port {
        final Path root;
        final Path output;
        final Clock clock = Clock.fixed(Instant.parse("2026-11-07T15:00:00Z"), ZoneOffset.UTC);
        final String actor = UUID.randomUUID().toString();
        final ObjectNode sourceSettings =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("sourceInstance", "authored-ui-fixture")
                        .put("tenantScope", "synthetic-scope")
                        .put("apiOrigin", "https://capture.invalid");
        final ObjectNode filters = ExportValidationSupport.JSON.createObjectNode();
        final ObjectNode acquisition;
        final ObjectNode filterProof;
        final ObjectNode context;
        final ObjectNode input;
        byte[] downloadBytes =
                "PK authored bytes; content verification belongs to the Native reader"
                        .getBytes(StandardCharsets.UTF_8);
        int downloadStatus = 200;
        int downloadCalls;
        URI downloadTarget;

        Fixture(final Path parent) throws Exception {
            root =
                    Files.createDirectory(parent.resolve(UUID.randomUUID().toString()))
                            .toAbsolutePath();
            output = Files.createDirectory(root.resolve("output"));
            LocalizadorIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
            filters.put(LocalizadorIndependentRequest.SERVICE_AT_FILTER, "2026-10-12 - 2026-10-18");
            acquisition =
                    evidence("ROOT_LOCALIZADOR_OFFICIAL_UI_ACQUISITION_V1")
                            .put("uiGenerated", true)
                            .put("observedRows", 3)
                            .put("observedColumns", 26);
            filterProof =
                    evidence("ROOT_LOCALIZADOR_UI_FILTER_PROOF_V1")
                            .put("configurationObserved", true);
            final var codePin =
                    pin(
                            ExportValidationSupport.JSON
                                    .createObjectNode()
                                    .put("authoredUnitOnly", true));
            context =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_INDEPENDENT_EXPORT_PHYSICAL_CONTEXT_V1")
                            .put("privateRoot", root.toString())
                            .put("executingRootUUID", actor)
                            .put(
                                    "sourceConfigurationSha256",
                                    LocalizadorNativeUiDownloadMain.plan(planInput())
                                            .path("sourceConfigurationSha256")
                                            .textValue())
                            .put("codeUnitSha256", codePin.sha256());
            context.putArray("downloadHosts").add(LocalizadorNativeUiDownloadMain.HOST);
            input = evidence("ROOT_OFFICIAL_UI_FILE_DOWNLOAD_INPUT_V1");
            input.remove("filters");
            input.remove("authoredMetadataOnly");
            input.put("operation", LocalizadorNativeUiDownloadMain.UI_LINK)
                    .put("privateOutputRoot", output.toString());
            input.set("sourceSettings", sourceSettings.deepCopy());
            input.set(
                    "uiAcquisitionPin", ExportValidationSupport.JSON.valueToTree(pin(acquisition)));
            input.set(
                    "uiFilterProofPin", ExportValidationSupport.JSON.valueToTree(pin(filterProof)));
            input.set("physicalContextPin", ExportValidationSupport.JSON.valueToTree(pin(context)));
            input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
            input.putArray("downloadHosts").add(LocalizadorNativeUiDownloadMain.HOST);
        }

        ObjectNode evidence(final String schema) {
            final var result =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", schema)
                            .put("profile", LocalizadorIndependentRequest.PROFILE)
                            .put("entity", "LOCALIZADOR")
                            .put("reportId", 8656)
                            .put("fileId", 42)
                            .put("periodStart", "2026-10-12")
                            .put("periodEnd", "2026-10-18")
                            .put("authoredMetadataOnly", true);
            result.set("filters", filters.deepCopy());
            return result;
        }

        ObjectNode planInput() {
            final var plan =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "OFFICIAL_UI_FILE_DOWNLOAD_PLAN_INPUT_V1")
                            .put("profile", LocalizadorIndependentRequest.PROFILE)
                            .put("fileId", 42)
                            .put("operation", LocalizadorNativeUiDownloadMain.UI_LINK);
            plan.set("sourceSettings", sourceSettings.deepCopy());
            return plan;
        }

        LocalizadorNativeUiDownloadMain.ExactUi exactUi() {
            return new LocalizadorNativeUiDownloadMain.ExactUi(
                    42,
                    java.time.LocalDate.parse("2026-10-12"),
                    java.time.LocalDate.parse("2026-10-18"),
                    3,
                    filters);
        }

        ExportValidationSupport.Pin pin(final JsonNode value) throws Exception {
            return pin(ExportValidationSupport.JSON.writeValueAsBytes(value));
        }

        ExportValidationSupport.Pin pin(final byte[] bytes) throws Exception {
            final Path path = root.resolve(UUID.randomUUID() + ".private");
            Files.write(path, bytes);
            return new ExportValidationSupport.Pin(
                    path, ExportValidationSupport.sha256(bytes), bytes.length);
        }

        ObjectNode execute() throws Exception {
            return LocalizadorNativeUiDownloadMain.execute(pin(input), this, clock);
        }

        @Override
        public CurlExportTransport.Wire download(final URI target) throws Exception {
            downloadCalls++;
            downloadTarget = target;
            return new CurlExportTransport.Wire(
                    downloadStatus, pin(downloadBytes), clock.instant());
        }

        void observedUiLink(final String mutation) throws Exception {
            final var link = evidence("ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_V1");
            link.remove("filters");
            link.put("sourceUiHref", "/analytics/report_files/42")
                    .put(
                            "targetUri",
                            "https://"
                                    + LocalizadorNativeUiDownloadMain.HOST
                                    + "/active_storage/rodogarcia/analytics_report_files/abc123-localizador-de-cargas_2026_11_07_16_28.xlsx"
                                    + "?X-Amz-Signature=authored-signature");
            link.set("uiAcquisitionPin", input.required("uiAcquisitionPin").deepCopy());
            link.set("uiFilterProofPin", input.required("uiFilterProofPin").deepCopy());
            switch (mutation) {
                case "file" -> link.put("fileId", 43);
                case "period" -> link.put("periodStart", "2026-10-19");
                case "href" -> link.put("sourceUiHref", "/analytics/report_files/43");
                case "host" -> link.put("targetUri", "https://unobserved.invalid/file?sig=x");
                case "path" ->
                        link.put(
                                "targetUri",
                                "https://"
                                        + LocalizadorNativeUiDownloadMain.HOST
                                        + "/other.xlsx?sig=x");
                default -> {
                    // The unchanged fixture needs no mutation.
                }
            }
            final var linkPin = pin(link);
            final var receipt = evidence("ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_STAGED_V1");
            receipt.remove("filters");
            receipt.put("success", true)
                    .put("pinsVerifiedBeforeAndAfter", true)
                    .put("businessValuesPublished", false)
                    .put("Source", 0)
                    .put("HTTP", "stageHttp".equals(mutation) ? 1 : 0)
                    .put("SQL", 0);
            receipt.set("privateLinkPin", ExportValidationSupport.JSON.valueToTree(linkPin));
            receipt.set("uiAcquisitionPin", input.required("uiAcquisitionPin").deepCopy());
            receipt.set("uiFilterProofPin", input.required("uiFilterProofPin").deepCopy());
            final var closure =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1")
                            .put("Source", 0)
                            .put("HTTP", 0)
                            .put("SQL", 0)
                            .put(
                                    "executingRootUUID",
                                    "actor".equals(mutation) ? UUID.randomUUID().toString() : actor)
                            .put("OriginalCalls", 1)
                            .put("success", true)
                            .put("originalClosedKnown", true)
                            .put("stateRestored", true)
                            .put("slotRetained", "closure".equals(mutation));
            if ("flag".equals(mutation)) {
                receipt.put("success", "true");
            }
            input.put("operation", LocalizadorNativeUiDownloadMain.UI_LINK);
            final var selectedLink =
                    "pin".equals(mutation) ? pin(link.deepCopy().put("fileId", 44)) : linkPin;
            input.set("uiObservedLinkPin", ExportValidationSupport.JSON.valueToTree(selectedLink));
            input.set(
                    "uiObservedLinkReceiptPin",
                    ExportValidationSupport.JSON.valueToTree(pin(receipt)));
            input.set(
                    "uiObservedLinkStageClosurePin",
                    ExportValidationSupport.JSON.valueToTree(pin(closure)));
            final var grant = evidence("ROOT_LOCALIZADOR_UI_LINK_ONLY_NATIVE_GRANT_V1");
            grant.remove("filters");
            grant.remove("authoredMetadataOnly");
            grant.put("approved", true)
                    .put("executingRootUUID", actor)
                    .put("operation", LocalizadorNativeUiDownloadMain.UI_LINK)
                    .put("zone", "America/Sao_Paulo")
                    .put("filterScopePolicy", LocalizadorIndependentRequest.FILTER_SCOPE_POLICY)
                    .put("updatedAtFilter", "")
                    .put(
                            "sourceConfigurationSha256",
                            context.path("sourceConfigurationSha256").textValue())
                    .put("codeUnitSha256", context.path("codeUnitSha256").textValue())
                    .put("OriginalMaximum", 1)
                    .put("SourceMaximum", 1)
                    .put("HTTPMaximum", 1)
                    .put("DOWNLOADHTTPMaximum", 1)
                    .put("INFOHTTPMaximum", 0)
                    .put("DATAHTTPMaximum", 0)
                    .put("SQL", 0)
                    .put("SourceSeconds", 210)
                    .put("OriginalSeconds", 300)
                    .put("OriginalBytes", 1073741824L)
                    .put("maximumDownloadBytes", 8388608)
                    .put("requestSeconds", 30)
                    .put("retryMaximum", 0)
                    .put("redirectMaximum", 0)
                    .put("downloadAuthentication", "NONE")
                    .put("createdUtc", clock.instant().minusSeconds(1).toString())
                    .put("expiresUtc", clock.instant().plusSeconds(290).toString());
            for (final String name :
                    List.of(
                            "uiAcquisitionPin",
                            "uiFilterProofPin",
                            "uiObservedLinkPin",
                            "uiObservedLinkReceiptPin",
                            "uiObservedLinkStageClosurePin")) {
                grant.set(name, input.required(name).deepCopy());
            }
            context.set(
                    "localizadorNativeGrantPin",
                    ExportValidationSupport.JSON.valueToTree(pin(grant)));
            input.set("physicalContextPin", ExportValidationSupport.JSON.valueToTree(pin(context)));
        }
    }
}
