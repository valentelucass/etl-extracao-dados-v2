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

/**
 * Every file, port reply and closure below is synthetic authored data, never an executed
 * ESL/Original proof.
 */
class NativeUiDownloadMainTest {
    @TempDir Path directory;

    @Test
    void planIsSourceZeroWithOneExactMetadataRouteAndTwoHttpCeiling() throws Exception {
        final var fixture = new Fixture(directory);
        final var plan = NativeUiDownloadMain.plan(fixture.planInput());
        assertEquals("/api/analytics/report_files/42", plan.path("metadataPath").textValue());
        assertEquals(2, plan.path("maximumHttpRequests").intValue());
        assertEquals(262144, plan.path("maximumMetadataBytes").intValue());
        assertEquals(8388608, plan.path("maximumDownloadBytes").intValue());
        assertEquals(0, plan.path("Source").intValue());
        assertEquals(0, plan.path("HTTP").intValue());
        assertFalse(plan.path("sourceAdmissionGranted").booleanValue());
        assertFalse(plan.path("uiAcquisitionQualified").booleanValue());
    }

    @Test
    void anOfficialUiIdentifierProducesOnlyMetadataGetThenTheExactReturnedDownloadTarget()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.downloadBytes = new byte[1048576 + 7];
        fixture.downloadBytes[0] = 'P';
        fixture.downloadBytes[1] = 'K';
        final var result = fixture.execute();
        assertTrue(result.path("downloadComplete").booleanValue());
        assertEquals(1, fixture.metadataCalls);
        assertEquals(1, fixture.downloadCalls);
        assertEquals("/api/analytics/report_files/42", fixture.metadataPath);
        assertEquals(
                fixture.metadata.path("download_url").textValue(),
                fixture.downloadTarget.toASCIIString());
        assertEquals(2, result.path("HTTP").intValue());
        assertEquals(1, result.path("Source").intValue());
        assertEquals(2, result.path("responses").size());
        assertTrue(result.path("metadataBodyReadToEof").booleanValue());
        assertTrue(result.path("downloadBodyReadToEof").booleanValue());
        assertEquals("NONE", result.path("downloadAuthentication").textValue());
        assertFalse(result.path("nativeContentVerified").booleanValue());
        assertFalse(result.path("contentAccepted").booleanValue());
        assertEquals(0, result.path("POST").intValue());
        assertArrayEquals(
                fixture.downloadBytes,
                ExportValidationSupport.Pin.from(result.required("dataPin"))
                        .read(8388608, budget()));
        final String published = result.toString();
        assertFalse(published.contains("authored-signature"));
        assertFalse(published.contains("download_url"));
        assertTrue(result.path("authoredPortUsed").booleanValue());
    }

    @Test
    void unknownMetadataRetainsThePrivateBodyAndHonestOneHttpWithoutDownloadingOrRepeating()
            throws Exception {
        final var fixture = new Fixture(directory);
        fixture.metadata =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("authoredUnknownShape", "withheld-private-value");
        final var result = fixture.execute();
        assertEquals("STOP_RETAINED_METADATA_NOT_QUALIFIED", result.path("status").textValue());
        assertEquals(1, result.path("HTTP").intValue());
        assertEquals(1, result.path("Source").intValue());
        assertEquals(1, result.path("httpAdmitted").intValue());
        assertFalse(result.path("wireOutcomeUnknown").booleanValue());
        assertFalse(result.path("downloadComplete").booleanValue());
        assertTrue(result.path("metadataRetained").booleanValue());
        assertTrue(result.has("metadataBodyPin"));
        assertFalse(result.has("dataPin"));
        assertEquals(1, fixture.metadataCalls);
        assertEquals(0, fixture.downloadCalls);
        assertFalse(result.toString().contains("withheld-private-value"));
        assertEquals(
                fixture.metadata,
                ExportValidationSupport.Pin.from(result.required("metadataBodyPin"))
                        .json(262144, budget()));
    }

    @Test
    void wrongIdReportPendingAndMissingUrlCannotBecomeAnOfficialFileDownload() throws Exception {
        for (final String mutation :
                List.of("id", "report", "pending", "url", "wrapped", "nestedFilters")) {
            final var fixture = new Fixture(directory);
            switch (mutation) {
                case "id" -> fixture.metadata.put("id", 43);
                case "report" -> fixture.metadata.put("report_id", 6389);
                case "pending" -> fixture.metadata.put("status", "generating");
                case "url" -> fixture.metadata.remove("download_url");
                case "wrapped" ->
                        fixture.metadata =
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .set("data", fixture.metadata);
                default ->
                        fixture.metadata
                                .putObject("filters")
                                .putObject("accounting_debits")
                                .put("issue_date", "unobserved");
            }
            final var result = fixture.execute();
            assertFalse(result.path("downloadComplete").booleanValue());
            assertEquals(1, fixture.metadataCalls);
            assertEquals(0, fixture.downloadCalls);
            assertEquals(1, result.path("HTTP").intValue());
            assertTrue(result.has("metadataBodyPin"));
        }
    }

    @Test
    void aReturnedUriMustHaveTheLiteralHttpsHostPortSignedQueryAndNoCredentialsOrFragment()
            throws Exception {
        final var fixture = new Fixture(directory);
        final var ui = fixture.exactUi();
        for (final String url :
                List.of(
                        "http://" + NativeUiDownloadMain.HOST + "/file?sig=x",
                        "https://unobserved.invalid/file?sig=x",
                        "https://u@" + NativeUiDownloadMain.HOST + "/file?sig=x",
                        "https://" + NativeUiDownloadMain.HOST + ":444/file?sig=x",
                        "https://" + NativeUiDownloadMain.HOST + "/file?sig=x#fragment",
                        "https://" + NativeUiDownloadMain.HOST + "/file",
                        "https://" + NativeUiDownloadMain.HOST + "/file?sig=x\n")) {
            final var metadata = fixture.metadata.deepCopy().put("download_url", url);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> NativeUiDownloadMain.decodeMetadata(metadata, ui));
        }
    }

    @Test
    void exposedFlatFiltersMustBindEmissionAndKeepCreatedAtEmpty() throws Exception {
        final var fixture = new Fixture(directory);
        for (final String mutation : List.of("issue", "created")) {
            final var metadata = fixture.metadata.deepCopy();
            ((ObjectNode) metadata.path("filters"))
                    .put(
                            "issue".equals(mutation)
                                    ? ContasPagarIndependentRequest.ISSUE_DATE_FILTER
                                    : ContasPagarIndependentRequest.CREATED_AT_FILTER,
                            "2026-10-19 - 2026-10-25");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> NativeUiDownloadMain.decodeMetadata(metadata, fixture.exactUi()));
        }
    }

    @Test
    void metadata429AndDownload403StopOnceAndRetainTheirPrivateResponsePins() throws Exception {
        final var metadata = new Fixture(directory);
        metadata.metadataStatus = 429;
        final var first = metadata.execute();
        assertEquals(1, metadata.metadataCalls);
        assertEquals(0, metadata.downloadCalls);
        assertEquals(1, first.path("HTTP").intValue());
        assertFalse(first.path("downloadComplete").booleanValue());
        assertTrue(first.has("metadataBodyPin"));
        final var download = new Fixture(directory);
        download.downloadStatus = 403;
        final var second = download.execute();
        assertEquals(1, download.metadataCalls);
        assertEquals(1, download.downloadCalls);
        assertEquals(2, second.path("HTTP").intValue());
        assertFalse(second.path("downloadComplete").booleanValue());
        assertTrue(second.has("downloadResponseBodyPin"));
        assertFalse(second.has("dataPin"));
    }

    @Test
    void acquisitionAndFilterProofCannotChangeTheUiFileReportPeriodOrObservedColumnWidth()
            throws Exception {
        for (final String mutation :
                List.of("file", "report", "period", "width", "profile", "created")) {
            final var fixture = new Fixture(directory);
            final var acquired = fixture.acquisition.deepCopy();
            switch (mutation) {
                case "file" -> acquired.put("fileId", 43);
                case "report" -> acquired.put("reportId", 6389);
                case "period" -> acquired.put("periodStart", "2026-10-19");
                case "width" -> acquired.put("observedColumns", 27);
                case "profile" -> acquired.put("profile", "CONTAS_A_PAGAR_8636_COMPARISON_V1");
                default ->
                        ((ObjectNode) acquired.path("filters"))
                                .put(ContasPagarIndependentRequest.CREATED_AT_FILTER, "filled");
            }
            fixture.input.set(
                    "uiAcquisitionPin",
                    ExportValidationSupport.JSON.valueToTree(fixture.pin(acquired)));
            assertThrows(IllegalArgumentException.class, fixture::execute);
            assertEquals(0, fixture.metadataCalls);
            assertEquals(0, fixture.downloadCalls);
        }
    }

    @Test
    void hostsCapsAndAnArbitraryExtraControlCannotExtendTheWireSurface() throws Exception {
        for (final String mutation : List.of("host", "extra", "id")) {
            final var fixture = new Fixture(directory);
            switch (mutation) {
                case "host" -> fixture.input.putArray("downloadHosts").add("unobserved.invalid");
                case "extra" -> fixture.input.put("HTTPMaximum", 3);
                default -> fixture.input.put("fileId", 0);
            }
            assertThrows(IllegalArgumentException.class, fixture::execute);
            assertEquals(0, fixture.metadataCalls);
            assertEquals(0, fixture.downloadCalls);
        }
    }

    @Test
    void aRequalifiedRetainedMetadataBodyDownloadsWithoutASecondMetadataGet() throws Exception {
        final var fixture = new Fixture(directory);
        final var metadataPin = fixture.pin(fixture.metadata);
        final byte[] before = Files.readAllBytes(metadataPin.path());
        fixture.replay(metadataPin, true);
        final var result = fixture.execute();
        assertTrue(result.path("downloadComplete").booleanValue());
        assertEquals(0, fixture.metadataCalls);
        assertEquals(1, fixture.downloadCalls);
        assertEquals(1, result.path("HTTP").intValue());
        assertTrue(result.path("metadataHttpReused").booleanValue());
        assertTrue(result.has("priorExecutionReceiptPin"));
        assertTrue(result.has("priorSourceClosurePin"));
        assertArrayEquals(before, Files.readAllBytes(metadataPin.path()));
    }

    @Test
    void replayRefusesAnUnclosedPriorSourceOrDifferentRetainedBodyBeforeAnyHttp() throws Exception {
        for (final boolean closed : List.of(false, true)) {
            final var fixture = new Fixture(directory);
            final var metadataPin = fixture.pin(fixture.metadata);
            fixture.replay(metadataPin, closed);
            if (closed) {
                fixture.input.set(
                        "retainedMetadataPin",
                        ExportValidationSupport.JSON.valueToTree(
                                fixture.pin(fixture.metadata.deepCopy().put("id", 43))));
            }
            final var result = fixture.execute();
            assertFalse(result.path("downloadComplete").booleanValue());
            assertEquals(0, fixture.metadataCalls);
            assertEquals(0, fixture.downloadCalls);
            assertEquals(0, result.path("HTTP").intValue());
        }
    }

    @Test
    void existingOutputCannotOverwriteAnEarlierReceiptOrRegenerateTheUiJob() throws Exception {
        final var fixture = new Fixture(directory);
        final var result = fixture.execute();
        final var pin = ExportValidationSupport.Pin.from(result.required("executionReceiptPin"));
        final byte[] before = Files.readAllBytes(pin.path());
        assertThrows(IllegalArgumentException.class, fixture::execute);
        assertArrayEquals(before, Files.readAllBytes(pin.path()));
        assertEquals(1, fixture.metadataCalls);
        assertEquals(1, fixture.downloadCalls);
    }

    @Test
    void physicalContextCodePinOrInputPinDriftRefusesBeforeMetadataAdmission() throws Exception {
        final var fixture = new Fixture(directory);
        final var context = fixture.context.deepCopy().put("codeUnitSha256", "d".repeat(64));
        fixture.input.set(
                "physicalContextPin",
                ExportValidationSupport.JSON.valueToTree(fixture.pin(context)));
        assertThrows(IllegalArgumentException.class, fixture::execute);
        assertEquals(0, fixture.metadataCalls);
        assertEquals(0, fixture.downloadCalls);
    }

    @Test
    void observedUiLinkPlanHasOneDownloadAndNoMetadataRoute() throws Exception {
        final var f = new Fixture(directory);
        final var plan =
                NativeUiDownloadMain.plan(
                        f.planInput().put("operation", NativeUiDownloadMain.UI_LINK));
        assertEquals(1, plan.path("maximumHttpRequests").intValue());
        assertEquals(0, plan.path("maximumMetadataHttpRequests").intValue());
        assertFalse(plan.has("metadataPath"));
        assertFalse(plan.has("metadataMethod"));
    }

    @Test
    void observedUiLinkUsesExactPrivateTargetOnceWithoutAnyMetadataGetOrPublicSignature()
            throws Exception {
        final var f = new Fixture(directory);
        f.observedUiLink("valid");
        final var result = f.execute();
        assertTrue(result.path("downloadComplete").booleanValue());
        assertEquals(0, f.metadataCalls);
        assertEquals(1, f.downloadCalls);
        assertEquals(1, result.path("HTTP").intValue());
        assertEquals(0, result.path("metadataHttpThisInvocation").intValue());
        assertTrue(result.path("uiObservedDownloadLinkUsed").booleanValue());
        assertFalse(result.has("metadataBodyPin"));
        assertFalse(result.has("metadataQualified"));
        assertFalse(result.toString().contains("authored-signature"));
        assertFalse(result.toString().contains("targetUri"));
    }

    @Test
    void observedUiLinkRefusesWrongUiFileScopeOrNonzeroSourceStageBeforeHttp() throws Exception {
        for (final var mutation :
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
            final var f = new Fixture(directory);
            f.observedUiLink(mutation);
            final var result = f.execute();
            assertFalse(result.path("downloadComplete").booleanValue());
            assertEquals(0, f.metadataCalls);
            assertEquals(0, f.downloadCalls);
            assertEquals(0, result.path("httpAdmitted").intValue());
        }
    }

    @Test
    void observedUiLink403RetainsResponseAndCannotRegenerateOrRetry() throws Exception {
        final var f = new Fixture(directory);
        f.observedUiLink("valid");
        f.downloadStatus = 403;
        final var result = f.execute();
        assertFalse(result.path("downloadComplete").booleanValue());
        assertTrue(result.has("downloadResponseBodyPin"));
        assertFalse(result.has("dataPin"));
        assertEquals(0, f.metadataCalls);
        assertEquals(1, f.downloadCalls);
        assertEquals(1, result.path("HTTP").intValue());
        assertFalse(result.path("wireOutcomeUnknown").booleanValue());
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }

    private static final class Fixture implements NativeUiDownloadMain.Port {
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
        ObjectNode metadata;
        byte[] downloadBytes =
                "PK authored bytes; content verification belongs to the Native reader"
                        .getBytes(StandardCharsets.UTF_8);
        int metadataStatus = 200;
        int downloadStatus = 200;
        int metadataCalls;
        int downloadCalls;
        String metadataPath;
        URI downloadTarget;

        Fixture(final Path parent) throws Exception {
            root =
                    Files.createDirectory(parent.resolve(UUID.randomUUID().toString()))
                            .toAbsolutePath();
            output = Files.createDirectory(root.resolve("output"));
            ContasPagarIndependentRequest.FILTERS.forEach(name -> filters.put(name, ""));
            filters.put(ContasPagarIndependentRequest.ISSUE_DATE_FILTER, "2026-10-12 - 2026-10-18");
            acquisition =
                    evidence("ROOT_CONTAS_A_PAGAR_OFFICIAL_UI_ACQUISITION_V1")
                            .put("uiGenerated", true)
                            .put("observedRows", 3)
                            .put("observedColumns", 28);
            filterProof =
                    evidence("ROOT_CONTAS_A_PAGAR_UI_FILTER_PROOF_V1")
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
                                    NativeUiDownloadMain.plan(planInput())
                                            .path("sourceConfigurationSha256")
                                            .textValue())
                            .put("codeUnitSha256", codePin.sha256());
            context.putArray("downloadHosts").add(NativeUiDownloadMain.HOST);
            input = evidence("ROOT_OFFICIAL_UI_FILE_DOWNLOAD_INPUT_V1");
            input.remove("filters");
            input.remove("authoredMetadataOnly");
            input.put("operation", NativeUiDownloadMain.NORMAL)
                    .put("privateOutputRoot", output.toString());
            input.set("sourceSettings", sourceSettings.deepCopy());
            input.set(
                    "uiAcquisitionPin", ExportValidationSupport.JSON.valueToTree(pin(acquisition)));
            input.set(
                    "uiFilterProofPin", ExportValidationSupport.JSON.valueToTree(pin(filterProof)));
            input.set("physicalContextPin", ExportValidationSupport.JSON.valueToTree(pin(context)));
            input.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
            input.putArray("downloadHosts").add(NativeUiDownloadMain.HOST);
            metadata =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("id", 42)
                            .put("report_id", 8636)
                            .put("status", "generated")
                            .put(
                                    "download_url",
                                    "https://"
                                            + NativeUiDownloadMain.HOST
                                            + "/active_storage/authored-ui-file.xlsx?X-Amz-Signature=authored-signature");
            metadata.set("filters", filters.deepCopy());
        }

        ObjectNode evidence(final String schema) {
            final var result =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", schema)
                            .put("profile", ContasPagarIndependentRequest.PROFILE)
                            .put("entity", "CONTAS_A_PAGAR")
                            .put("reportId", 8636)
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
                            .put("profile", ContasPagarIndependentRequest.PROFILE)
                            .put("fileId", 42);
            plan.set("sourceSettings", sourceSettings.deepCopy());
            return plan;
        }

        NativeUiDownloadMain.ExactUi exactUi() {
            return new NativeUiDownloadMain.ExactUi(
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
            return NativeUiDownloadMain.execute(pin(input), this, clock);
        }

        @Override
        public CurlExportTransport.Wire metadata(final String path) throws Exception {
            metadataCalls++;
            metadataPath = path;
            return new CurlExportTransport.Wire(metadataStatus, pin(metadata), clock.instant());
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
                                    + NativeUiDownloadMain.HOST
                                    + "/active_storage/rodogarcia/analytics_report_files/abc123-contas-a-pagar_2026_11_07_16_28.xlsx"
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
                                "https://" + NativeUiDownloadMain.HOST + "/other.xlsx?sig=x");
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
            input.put("operation", NativeUiDownloadMain.UI_LINK);
            final var selectedLink =
                    "pin".equals(mutation) ? pin(link.deepCopy().put("fileId", 44)) : linkPin;
            input.set("uiObservedLinkPin", ExportValidationSupport.JSON.valueToTree(selectedLink));
            input.set(
                    "uiObservedLinkReceiptPin",
                    ExportValidationSupport.JSON.valueToTree(pin(receipt)));
            input.set(
                    "uiObservedLinkStageClosurePin",
                    ExportValidationSupport.JSON.valueToTree(pin(closure)));
        }

        void replay(final ExportValidationSupport.Pin metadataPin, final boolean closed)
                throws Exception {
            final var prior = evidence("OFFICIAL_UI_FILE_DOWNLOAD_EXECUTED_V1");
            prior.remove("filters");
            prior.put("operation", NativeUiDownloadMain.NORMAL)
                    .put("executingRootUUID", actor)
                    .put("status", "STOP_RETAINED_METADATA_NOT_QUALIFIED")
                    .put("downloadComplete", false)
                    .put("HTTP", 1)
                    .put("httpAdmitted", 1)
                    .put("wireOutcomeUnknown", false)
                    .put("authoredPriorDecoderStateOnly", true);
            prior.set("uiAcquisitionPin", input.required("uiAcquisitionPin").deepCopy());
            prior.set("uiFilterProofPin", input.required("uiFilterProofPin").deepCopy());
            prior.set("metadataBodyPin", ExportValidationSupport.JSON.valueToTree(metadataPin));
            final var priorPin = pin(prior);
            final var closure =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "ROOT_OFFICIAL_UI_FILE_DOWNLOAD_CLOSURE_V1")
                            .put("executingRootUUID", actor)
                            .put("success", closed)
                            .put("originalClosedKnown", closed)
                            .put("stateRestored", closed)
                            .put("slotRetained", !closed)
                            .put("OriginalCalls", 1)
                            .put("SQL", 0)
                            .put("authoredMetadataOnly", true);
            closure.set("executionReceiptPin", ExportValidationSupport.JSON.valueToTree(priorPin));
            closure.set("metadataBodyPin", ExportValidationSupport.JSON.valueToTree(metadataPin));
            input.put("operation", NativeUiDownloadMain.RETAINED);
            input.set("retainedMetadataPin", ExportValidationSupport.JSON.valueToTree(metadataPin));
            input.set(
                    "priorExecutionReceiptPin", ExportValidationSupport.JSON.valueToTree(priorPin));
            input.set(
                    "priorSourceClosurePin",
                    ExportValidationSupport.JSON.valueToTree(pin(closure)));
        }
    }
}
