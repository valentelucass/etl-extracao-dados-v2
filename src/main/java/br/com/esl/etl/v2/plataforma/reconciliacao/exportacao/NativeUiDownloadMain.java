package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportRuntimeConfigurationFingerprint;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.PrintStream;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Transfers an already generated official UI file; never creates a report job or treats API rows as
 * Native.
 */
public final class NativeUiDownloadMain {
    public static final String HOST = "eslcloud.s3.sa-east-1.amazonaws.com";
    public static final String NORMAL = "metadata_then_download";
    public static final String RETAINED = "requalify_retained_metadata_and_download";
    public static final String UI_LINK = "download_from_observed_ui_link";
    private static final int FILE_CAP = 8388608;
    private static final int METADATA_CAP = 262144;

    interface Port {
        CurlExportTransport.Wire metadata(String exactPath) throws Exception;

        CurlExportTransport.Wire download(URI returnedTarget) throws Exception;
    }

    /** Public Source0 decoder input. This record alone grants no physical Source authority. */
    public record ExactUi(
            long fileId, LocalDate start, LocalDate end, long observedRows, JsonNode filters) {
        public ExactUi {
            require(
                    fileId > 0
                            && Long.toString(fileId).length() <= 18
                            && start != null
                            && end != null
                            && !end.isBefore(start)
                            && end.isBefore(start.plusDays(7))
                            && observedRows >= 0
                            && observedRows <= 12000
                            && filters != null
                            && filters.isObject(),
                    "UI_FILE_EXACT_UI_SCOPE");
            filters = filters.deepCopy();
            final var request =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "CONTAS_A_PAGAR_INDEPENDENT_REQUEST_V1")
                            .put("profile", ContasPagarIndependentRequest.PROFILE)
                            .put("entity", "CONTAS_A_PAGAR")
                            .put("reportId", 8636)
                            .put("attemptId", "00000000-0000-0000-0000-000000000000")
                            .put("periodStart", start.toString())
                            .put("periodEnd", end.toString())
                            .put("zone", "America/Sao_Paulo");
            request.set("filters", filters);
            ContasPagarIndependentRequest.validate(request);
        }

        public JsonNode filters() {
            return filters.deepCopy();
        }
    }

    private NativeUiDownloadMain() {}

    public static void main(final String[] args) {
        final int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    static int run(final String[] args, final PrintStream out, final PrintStream err) {
        if (args == null
                || args.length != 5
                || !("capture".equals(args[0]) || "plan".equals(args[0]))
                || !"--input".equals(args[1])
                || !"--sha256".equals(args[3])) {
            err.println("UI_FILE_DOWNLOAD_USAGE");
            return 2;
        }
        try {
            final Path path = Path.of(args[2]).toAbsolutePath().normalize();
            final var inputPin =
                    new ExportValidationSupport.Pin(
                            path, args[4].toLowerCase(Locale.ROOT), Files.size(path));
            final boolean planning = "plan".equals(args[0]);
            final var result =
                    planning ? plan(inputPin.json(METADATA_CAP, budget())) : execute(inputPin);
            inputPin.read(METADATA_CAP, budget());
            out.println(ExportValidationSupport.JSON.writeValueAsString(result));
            return planning || result.path("downloadComplete").booleanValue() ? 0 : 3;
        } catch (final Exception failure) {
            err.println(code(failure));
            return 2;
        }
    }

    public static ObjectNode plan(final JsonNode input) {
        final boolean uiLink = input.has("operation") && UI_LINK.equals(text(input, "operation"));
        fields(
                input,
                uiLink
                        ? Set.of("schema", "sourceSettings", "profile", "fileId", "operation")
                        : Set.of("schema", "sourceSettings", "profile", "fileId"));
        require(
                "OFFICIAL_UI_FILE_DOWNLOAD_PLAN_INPUT_V1".equals(text(input, "schema"))
                        && ContasPagarIndependentRequest.PROFILE.equals(text(input, "profile")),
                "UI_FILE_PLAN_PROFILE");
        final long id = number(input, "fileId");
        require(id > 0 && Long.toString(id).length() <= 18, "UI_FILE_PLAN_ID");
        final var result =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("schema", "OFFICIAL_UI_FILE_DOWNLOAD_PLAN_V1")
                        .put("profile", ContasPagarIndependentRequest.PROFILE)
                        .put("fileId", id)
                        .put("metadataMethod", "GET")
                        .put("metadataPath", "/api/analytics/report_files/" + id)
                        .put(
                                "sourceConfigurationSha256",
                                DataExportRuntimeConfigurationFingerprint.from(
                                                configuration(input.required("sourceSettings")))
                                        .sha256())
                        .put("downloadHost", HOST)
                        .put("maximumHttpRequests", 2)
                        .put("maximumMetadataBytes", METADATA_CAP)
                        .put("maximumDownloadBytes", FILE_CAP)
                        .put("sourceSeconds", 210)
                        .put("requestSeconds", 30)
                        .put("OriginalMaximumBytes", 1073741824L)
                        .put("OriginalMaximumSeconds", 300)
                        .put("JavaMaximumHeapMiB", 512)
                        .put("retryMaximum", 0)
                        .put("redirectMaximum", 0)
                        .put("Source", 0)
                        .put("HTTP", 0)
                        .put("SQL", 0)
                        .put("sourceAdmissionGranted", false)
                        .put("uiAcquisitionQualified", false)
                        .put("downloadComplete", false);
        if (uiLink) {
            result.remove(List.of("metadataMethod", "metadataPath"));
            result.put("operation", UI_LINK)
                    .put("maximumHttpRequests", 1)
                    .put("maximumMetadataHttpRequests", 0)
                    .put("maximumDownloadHttpRequests", 1)
                    .put("downloadAuthentication", "NONE");
        }
        return result;
    }

    public static ObjectNode execute(final ExportValidationSupport.Pin inputPin) throws Exception {
        return execute(inputPin, null, Clock.systemUTC());
    }

    static ObjectNode execute(
            final ExportValidationSupport.Pin inputPin, final Port authoredPort, final Clock clock)
            throws Exception {
        final var validationBudget = budget();
        final var input = inputPin.json(METADATA_CAP, validationBudget);
        final String operation = text(input, "operation");
        require(
                NORMAL.equals(operation) || RETAINED.equals(operation) || UI_LINK.equals(operation),
                "UI_FILE_DOWNLOAD_OPERATION");
        final var members =
                new HashSet<>(
                        Set.of(
                                "schema",
                                "operation",
                                "profile",
                                "entity",
                                "fileId",
                                "reportId",
                                "periodStart",
                                "periodEnd",
                                "uiAcquisitionPin",
                                "uiFilterProofPin",
                                "sourceSettings",
                                "physicalContextPin",
                                "codeUnitPin",
                                "privateOutputRoot",
                                "downloadHosts"));
        if (RETAINED.equals(operation)) {
            members.addAll(
                    Set.of(
                            "retainedMetadataPin",
                            "priorExecutionReceiptPin",
                            "priorSourceClosurePin"));
        }
        if (UI_LINK.equals(operation)) {
            members.addAll(
                    Set.of(
                            "uiObservedLinkPin",
                            "uiObservedLinkReceiptPin",
                            "uiObservedLinkStageClosurePin"));
        }
        fields(input, members);
        require(
                "ROOT_OFFICIAL_UI_FILE_DOWNLOAD_INPUT_V1".equals(text(input, "schema")),
                "UI_FILE_DOWNLOAD_INPUT_SCHEMA");
        requireProfile(input);
        final var uiPin = pin(input.required("uiAcquisitionPin"));
        final var filterPin = pin(input.required("uiFilterProofPin"));
        final var uiJson = uiPin.json(METADATA_CAP, validationBudget);
        final var filterJson = filterPin.json(METADATA_CAP, validationBudget);
        final var ui = exactUi(input, uiJson, filterJson);
        final var contextPin = pin(input.required("physicalContextPin"));
        final var codePin = pin(input.required("codeUnitPin"));
        final var context = contextPin.json(METADATA_CAP, validationBudget);
        final var source = configuration(input.required("sourceSettings"));
        final String fingerprint = DataExportRuntimeConfigurationFingerprint.from(source).sha256();
        require(
                "ROOT_INDEPENDENT_EXPORT_PHYSICAL_CONTEXT_V1".equals(text(context, "schema"))
                        && fingerprint.equals(text(context, "sourceConfigurationSha256"))
                        && codePin.sha256().equals(text(context, "codeUnitSha256")),
                "UI_FILE_DOWNLOAD_PHYSICAL_BINDING");
        requireHostList(input.required("downloadHosts"));
        requireHostList(context.required("downloadHosts"));
        final Path privateRoot = absolute(text(context, "privateRoot"));
        final Path outputRoot = absolute(text(input, "privateOutputRoot"));
        require(
                outputRoot.startsWith(privateRoot) && Files.isDirectory(outputRoot),
                "UI_FILE_DOWNLOAD_PRIVATE_OUTPUT");
        noLinks(outputRoot);
        final Path resultPath = outputRoot.resolve("native-ui-download-result.json");
        final Path journalPath = outputRoot.resolve("native-ui-download-journal");
        require(
                !Files.exists(resultPath) && !Files.exists(journalPath),
                "UI_FILE_DOWNLOAD_FRESH_OUTPUT");
        codePin.read(FILE_CAP, validationBudget);
        final var pins =
                new ArrayList<ExportValidationSupport.Pin>(
                        List.of(inputPin, uiPin, filterPin, contextPin, codePin));
        final Instant started = clock.instant();
        final Instant deadline = started.plusSeconds(210);
        final long monotonicStarted = System.nanoTime();
        final int maximumHttp = NORMAL.equals(operation) ? 2 : 1;
        try (var journal =
                PersistentExportJournal.create(
                        journalPath, inputPin.sha256(), codePin.sha256(), UUID.randomUUID())) {
            final Port port;
            if (authoredPort == null) {
                final var channel = new RootOwnedWireChannel(contextPin, journal, fingerprint);
                final var transport =
                        new CurlExportTransport(
                                source,
                                journal,
                                channel,
                                clock,
                                deadline,
                                maximumHttp,
                                Set.of(HOST));
                port =
                        new Port() {
                            @Override
                            public CurlExportTransport.Wire metadata(final String path)
                                    throws Exception {
                                return transport.api("GET", path, null, Duration.ofSeconds(30));
                            }

                            @Override
                            public CurlExportTransport.Wire download(final URI target)
                                    throws Exception {
                                return transport.download(target);
                            }
                        };
            } else {
                port = authoredPort;
            }
            final var result =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("schema", "OFFICIAL_UI_FILE_DOWNLOAD_EXECUTED_V1")
                            .put("operation", operation)
                            .put("executingRootUUID", text(context, "executingRootUUID"))
                            .put("profile", ContasPagarIndependentRequest.PROFILE)
                            .put("entity", "CONTAS_A_PAGAR")
                            .put("reportId", 8636)
                            .put("fileId", ui.fileId())
                            .put("periodStart", ui.start().toString())
                            .put("periodEnd", ui.end().toString())
                            .put("uiObservedRows", ui.observedRows())
                            .put("uiObservedColumns", 28)
                            .put(
                                    "filterScopePolicy",
                                    ContasPagarIndependentRequest.FILTER_SCOPE_POLICY)
                            .put("createdAtFilter", "")
                            .put("uiFileNotRegenerated", true)
                            .put("POST", 0)
                            .put("SQL", 0)
                            .put("downloadComplete", false)
                            .put("contentAccepted", false)
                            .put("entityQualified", false)
                            .put("nativeContentVerified", false)
                            .put("apiRowsUsedAsNativeOracle", false)
                            .put("mainResumeAllowed", false)
                            .put("businessValuesPublished", false)
                            .put("authoredPortUsed", authoredPort != null)
                            .put("startedAt", started.toString());
            result.set("executionInputPin", ExportValidationSupport.JSON.valueToTree(inputPin));
            result.set("uiAcquisitionPin", ExportValidationSupport.JSON.valueToTree(uiPin));
            result.set("uiFilterProofPin", ExportValidationSupport.JSON.valueToTree(filterPin));
            result.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
            int admitted = 0;
            int completed = 0;
            final var responses = result.putArray("responses");
            try {
                final URI target;
                if (UI_LINK.equals(operation)) {
                    target =
                            observedUiLink(
                                    input,
                                    ui,
                                    uiPin,
                                    filterPin,
                                    text(context, "executingRootUUID"),
                                    pins);
                    result.set("uiObservedLinkPin", input.required("uiObservedLinkPin").deepCopy());
                    result.set(
                            "uiObservedLinkReceiptPin",
                            input.required("uiObservedLinkReceiptPin").deepCopy());
                    result.set(
                            "uiObservedLinkStageClosurePin",
                            input.required("uiObservedLinkStageClosurePin").deepCopy());
                    result.put("metadataHttpThisInvocation", 0)
                            .put("uiObservedDownloadLinkUsed", true);
                } else {
                    final ExportValidationSupport.Pin metadataPin;
                    if (NORMAL.equals(operation)) {
                        checkTime(clock, deadline, monotonicStarted);
                        admitted++;
                        final var wire =
                                port.metadata("/api/analytics/report_files/" + ui.fileId());
                        observe(
                                wire,
                                responses,
                                "metadata",
                                METADATA_CAP,
                                started,
                                deadline,
                                clock);
                        completed++;
                        metadataPin = wire.body();
                        result.set(
                                "metadataBodyPin",
                                ExportValidationSupport.JSON.valueToTree(metadataPin));
                        result.put("metadataRetained", true).put("metadataBodyReadToEof", true);
                        pins.add(metadataPin);
                        require(wire.status() == 200, "UI_FILE_METADATA_STOP_NON_200_NO_RETRY");
                    } else {
                        metadataPin = retained(input, ui, uiPin, filterPin, pins);
                        result.set(
                                "priorExecutionReceiptPin",
                                input.required("priorExecutionReceiptPin").deepCopy());
                        result.set(
                                "priorSourceClosurePin",
                                input.required("priorSourceClosurePin").deepCopy());
                        result.put("metadataHttpReused", true).put("metadataHttpThisInvocation", 0);
                    }
                    result.set(
                            "metadataBodyPin",
                            ExportValidationSupport.JSON.valueToTree(metadataPin));
                    result.put("metadataBodyReadToEof", true);
                    pins.add(metadataPin);
                    try {
                        target = decodeMetadata(metadataPin.json(METADATA_CAP, budget()), ui);
                    } catch (final Exception unknownMetadata) {
                        result.put("status", "STOP_RETAINED_METADATA_NOT_QUALIFIED")
                                .put("refusalCode", code(unknownMetadata))
                                .put("metadataRetained", true)
                                .put("metadataQualified", false);
                        return persist(result, resultPath, pins, admitted, completed, clock);
                    }
                }
                checkTime(clock, deadline, monotonicStarted);
                admitted++;
                final var download = port.download(target);
                observe(download, responses, "download", FILE_CAP, started, deadline, clock);
                completed++;
                result.set(
                        "downloadResponseBodyPin",
                        ExportValidationSupport.JSON.valueToTree(download.body()));
                pins.add(download.body());
                require(download.status() == 200, "UI_FILE_DOWNLOAD_STOP_NON_200_NO_RETRY");
                result.set("dataPin", ExportValidationSupport.JSON.valueToTree(download.body()));
                require(
                        admitted == maximumHttp && completed == maximumHttp,
                        "UI_FILE_DOWNLOAD_EXACT_FINITE_HTTP");
                result.put("status", "OFFICIAL_UI_FILE_BYTES_RETAINED_NOT_CONTENT_QUALIFIED")
                        .put("downloadComplete", true)
                        .put("downloadBodyReadToEof", true)
                        .put("downloadAuthentication", "NONE");
                if (!UI_LINK.equals(operation)) {
                    result.put("metadataQualified", true)
                            .put("metadataRetained", true)
                            .put("metadataBodyReadToEof", true);
                }
            } catch (final Exception stopped) {
                result.put("status", "STOP_UI_FILE_TRANSFER_RECONCILIATION_REQUIRED")
                        .put("refusalCode", code(stopped));
            }
            return persist(result, resultPath, pins, admitted, completed, clock);
        }
    }

    private static URI observedUiLink(
            final JsonNode input,
            final ExactUi ui,
            final ExportValidationSupport.Pin uiPin,
            final ExportValidationSupport.Pin filterPin,
            final String actor,
            final List<ExportValidationSupport.Pin> pins)
            throws Exception {
        final var linkPin = pin(input.required("uiObservedLinkPin"));
        final var receiptPin = pin(input.required("uiObservedLinkReceiptPin"));
        final var closurePin = pin(input.required("uiObservedLinkStageClosurePin"));
        final var link = linkPin.json(METADATA_CAP, budget());
        final var receipt = receiptPin.json(METADATA_CAP, budget());
        final var closure = closurePin.json(METADATA_CAP, budget());
        require(
                "ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_V1".equals(text(link, "schema"))
                        && "ROOT_UI_OBSERVED_SIGNED_DOWNLOAD_LINK_STAGED_V1"
                                .equals(text(receipt, "schema")),
                "UI_FILE_OBSERVED_LINK_STAGE_REQUIRED");
        for (final var value : List.of(link, receipt)) {
            requireProfile(value);
            require(
                    number(value, "fileId") == ui.fileId()
                            && ui.start().toString().equals(text(value, "periodStart"))
                            && ui.end().toString().equals(text(value, "periodEnd"))
                            && uiPin.equals(pin(value.required("uiAcquisitionPin")))
                            && filterPin.equals(pin(value.required("uiFilterProofPin"))),
                    "UI_FILE_OBSERVED_LINK_UI_BINDING");
        }
        for (final String flag :
                Set.of("success", "pinsVerifiedBeforeAndAfter", "businessValuesPublished")) {
            require(receipt.path(flag).isBoolean(), "UI_FILE_OBSERVED_LINK_TYPED_STAGE_FLAGS");
        }
        for (final String flag :
                Set.of("success", "originalClosedKnown", "stateRestored", "slotRetained")) {
            require(closure.path(flag).isBoolean(), "UI_FILE_OBSERVED_LINK_TYPED_CLOSURE_FLAGS");
        }
        require(
                linkPin.equals(pin(receipt.required("privateLinkPin")))
                        && receipt.path("success").asBoolean()
                        && receipt.path("pinsVerifiedBeforeAndAfter").asBoolean()
                        && !receipt.path("businessValuesPublished").asBoolean(true)
                        && number(receipt, "Source") == 0
                        && number(receipt, "HTTP") == 0
                        && number(receipt, "SQL") == 0,
                "UI_FILE_OBSERVED_LINK_SOURCE_ZERO_STAGE");
        require(
                "ROOT_FRESH_PAIR_COMPARISON_CLOSURE0664_V1".equals(text(closure, "schema"))
                        && actor.equals(text(closure, "executingRootUUID"))
                        && number(closure, "Source") == 0
                        && number(closure, "HTTP") == 0
                        && number(closure, "SQL") == 0
                        && number(closure, "OriginalCalls") == 1
                        && closurePin.path().getParent().equals(receiptPin.path().getParent())
                        && closure.path("success").asBoolean()
                        && closure.path("originalClosedKnown").asBoolean()
                        && closure.path("stateRestored").asBoolean()
                        && !closure.path("slotRetained").asBoolean(true),
                "UI_FILE_OBSERVED_LINK_STAGE_CLOSED");
        require(
                ("/analytics/report_files/" + ui.fileId()).equals(text(link, "sourceUiHref")),
                "UI_FILE_OBSERVED_LINK_LITERAL_UI_HREF");
        // The signed target was observed after clicking the exact official UI row. It is
        // private transport input, never an API-row oracle or a fabricated metadata response.
        final var target = decodeTarget(text(link, "targetUri"));
        require(
                target.getRawPath()
                        .matches(
                                "/active_storage/rodogarcia/analytics_report_files/"
                                        + "[a-z0-9]+-contas-a-pagar_[0-9]{4}_[0-9]{2}_[0-9]{2}_[0-9]{2}_[0-9]{2}\\.xlsx"),
                "UI_FILE_OBSERVED_LINK_CAP_PATH");
        pins.add(linkPin);
        pins.add(receiptPin);
        pins.add(closurePin);
        return target;
    }

    /**
     * Strict initial decoder for the existing report_files JSON vocabulary; unknown shape is
     * retained and refused.
     */
    public static URI decodeMetadata(final JsonNode metadata, final ExactUi ui) {
        require(metadata != null && metadata.isObject(), "UI_FILE_METADATA_OBJECT_REQUIRED");
        require(
                number(metadata, "id") == ui.fileId() && number(metadata, "report_id") == 8636,
                "UI_FILE_METADATA_EXACT_UI_ID_REPORT");
        require(
                "generated".equals(text(metadata, "status")),
                "UI_FILE_METADATA_NOT_GENERATED_NO_POLL");
        if (metadata.has("filters")) {
            final var filters = metadata.get("filters");
            require(filters.isObject(), "UI_FILE_METADATA_FILTER_SHAPE_UNOBSERVED");
            final var keys = new HashSet<String>();
            filters.fieldNames().forEachRemaining(keys::add);
            require(
                    keys.equals(ContasPagarIndependentRequest.FILTERS),
                    "UI_FILE_METADATA_FILTER_SHAPE_UNOBSERVED");
            for (final String key : keys) {
                require(filters.get(key).isTextual(), "UI_FILE_METADATA_FILTER_SHAPE_UNOBSERVED");
                if (ContasPagarIndependentRequest.ISSUE_DATE_FILTER.equals(key)) {
                    require(
                            (ui.start() + " - " + ui.end())
                                    .equals(
                                            IndependentExportRequest.canonicalPeriod(
                                                    filters.get(key).textValue())),
                            "UI_FILE_METADATA_ISSUE_DATE_MISMATCH");
                } else {
                    require(
                            filters.get(key).textValue().isEmpty(),
                            "UI_FILE_METADATA_FILTER_MISMATCH");
                }
            }
        }
        final String raw = text(metadata, "download_url");
        return decodeTarget(raw);
    }

    private static URI decodeTarget(final String raw) {
        require(
                raw.length() <= 16384 && !raw.matches("(?s).*[\\x00-\\x20\\x7f].*"),
                "UI_FILE_RETURNED_URI_BOUND");
        final URI target;
        try {
            target = URI.create(raw);
        } catch (final IllegalArgumentException invalid) {
            throw new IllegalArgumentException("UI_FILE_RETURNED_URI_INVALID", invalid);
        }
        require(
                "https".equals(target.getScheme())
                        && HOST.equals(target.getHost())
                        && (target.getPort() == -1 || target.getPort() == 443)
                        && target.getRawUserInfo() == null
                        && target.getRawFragment() == null
                        && target.getRawPath() != null
                        && !target.getRawPath().isEmpty()
                        && target.getRawQuery() != null
                        && !target.getRawQuery().isEmpty(),
                "UI_FILE_RETURNED_URI_ORIGIN");
        return target;
    }

    private static ExportValidationSupport.Pin retained(
            final JsonNode input,
            final ExactUi ui,
            final ExportValidationSupport.Pin uiPin,
            final ExportValidationSupport.Pin filterPin,
            final List<ExportValidationSupport.Pin> pins)
            throws Exception {
        final var metadataPin = pin(input.required("retainedMetadataPin"));
        final var priorPin = pin(input.required("priorExecutionReceiptPin"));
        final var closurePin = pin(input.required("priorSourceClosurePin"));
        final var prior = priorPin.json(METADATA_CAP, budget());
        final var closure = closurePin.json(METADATA_CAP, budget());
        require(
                "OFFICIAL_UI_FILE_DOWNLOAD_EXECUTED_V1".equals(text(prior, "schema"))
                        && NORMAL.equals(text(prior, "operation"))
                        && !prior.path("downloadComplete").asBoolean(true)
                        && "STOP_RETAINED_METADATA_NOT_QUALIFIED".equals(text(prior, "status"))
                        && number(prior, "fileId") == ui.fileId()
                        && number(prior, "reportId") == 8636
                        && number(prior, "HTTP") == 1
                        && number(prior, "httpAdmitted") == 1
                        && !prior.path("wireOutcomeUnknown").asBoolean(true)
                        && uiPin.equals(pin(prior.required("uiAcquisitionPin")))
                        && filterPin.equals(pin(prior.required("uiFilterProofPin")))
                        && metadataPin.equals(pin(prior.required("metadataBodyPin")))
                        && ContasPagarIndependentRequest.PROFILE.equals(text(prior, "profile"))
                        && ui.start().toString().equals(text(prior, "periodStart"))
                        && ui.end().toString().equals(text(prior, "periodEnd")),
                "UI_FILE_RETAINED_PRIOR_BINDING");
        require(
                "ROOT_OFFICIAL_UI_FILE_DOWNLOAD_CLOSURE_V1".equals(text(closure, "schema"))
                        && text(prior, "executingRootUUID")
                                .equals(text(closure, "executingRootUUID"))
                        && priorPin.equals(pin(closure.required("executionReceiptPin")))
                        && metadataPin.equals(pin(closure.required("metadataBodyPin"))),
                "UI_FILE_RETAINED_CLOSURE_BINDING");
        for (final String flag : Set.of("success", "originalClosedKnown", "stateRestored")) {
            require(
                    closure.path(flag).isBoolean() && closure.path(flag).booleanValue(),
                    "UI_FILE_RETAINED_PRIOR_CLOSED");
        }
        require(
                closure.path("slotRetained").isBoolean()
                        && !closure.path("slotRetained").booleanValue()
                        && number(closure, "OriginalCalls") == 1
                        && number(closure, "SQL") == 0,
                "UI_FILE_RETAINED_PRIOR_CLOSED");
        metadataPin.read(METADATA_CAP, budget());
        pins.add(metadataPin);
        pins.add(priorPin);
        pins.add(closurePin);
        return metadataPin;
    }

    private static ExactUi exactUi(
            final JsonNode input, final JsonNode acquired, final JsonNode proof) {
        require(
                "ROOT_CONTAS_A_PAGAR_OFFICIAL_UI_ACQUISITION_V1".equals(text(acquired, "schema"))
                        && acquired.path("uiGenerated").isBoolean()
                        && acquired.path("uiGenerated").booleanValue()
                        && number(acquired, "observedColumns") == 28,
                "UI_FILE_OFFICIAL_UI_ACQUISITION_REQUIRED");
        require(
                "ROOT_CONTAS_A_PAGAR_UI_FILTER_PROOF_V1".equals(text(proof, "schema"))
                        && proof.path("configurationObserved").isBoolean()
                        && proof.path("configurationObserved").booleanValue(),
                "UI_FILE_OBSERVED_FILTER_PROOF_REQUIRED");
        final long id = number(input, "fileId");
        final String start = text(input, "periodStart");
        final String end = text(input, "periodEnd");
        require(
                start.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
                        && end.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"),
                "UI_FILE_EXACT_PERIOD_FORMAT");
        for (final var evidence : List.of(acquired, proof)) {
            requireProfile(evidence);
            require(
                    number(evidence, "fileId") == id
                            && start.equals(text(evidence, "periodStart"))
                            && end.equals(text(evidence, "periodEnd")),
                    "UI_FILE_UI_EVIDENCE_BINDING");
        }
        require(
                acquired.required("filters").equals(proof.required("filters")),
                "UI_FILE_EXACT_UI_FILTER_BINDING");
        return new ExactUi(
                id,
                LocalDate.parse(start),
                LocalDate.parse(end),
                number(acquired, "observedRows"),
                acquired.required("filters"));
    }

    private static void requireProfile(final JsonNode value) {
        require(
                ContasPagarIndependentRequest.PROFILE.equals(text(value, "profile"))
                        && "CONTAS_A_PAGAR".equals(text(value, "entity"))
                        && number(value, "reportId") == 8636,
                "UI_FILE_CAP_ISSUE_PROFILE_ONLY");
    }

    private static ObjectNode persist(
            final ObjectNode result,
            final Path path,
            final List<ExportValidationSupport.Pin> pins,
            final int admitted,
            final int completed,
            final Clock clock)
            throws Exception {
        final var postcheck = budget();
        for (final var pin : pins) {
            pin.read(FILE_CAP, postcheck);
        }
        result.put("HTTP", completed)
                .put("httpAdmitted", admitted)
                .put("Source", admitted > 0 ? 1 : 0)
                .put("wireOutcomeUnknown", admitted != completed)
                .put("pinsVerifiedBeforeAndAfter", true)
                .put("finishedAt", clock.instant().toString());
        final byte[] bytes = ExportValidationSupport.JSON.writeValueAsBytes(result);
        require(bytes.length <= METADATA_CAP, "UI_FILE_RESULT_BOUND");
        try (var file =
                FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            final var buffer = ByteBuffer.wrap(bytes);
            while (buffer.hasRemaining()) {
                require(file.write(buffer) > 0, "UI_FILE_RESULT_WRITE_PROGRESS");
            }
            file.force(true);
        }
        final var resultPin =
                new ExportValidationSupport.Pin(
                        path, ExportValidationSupport.sha256(bytes), bytes.length);
        resultPin.read(METADATA_CAP, postcheck);
        final var publicResult = result.deepCopy();
        publicResult.set(
                "executionReceiptPin", ExportValidationSupport.JSON.valueToTree(resultPin));
        return publicResult;
    }

    private static void observe(
            final CurlExportTransport.Wire wire,
            final com.fasterxml.jackson.databind.node.ArrayNode responses,
            final String role,
            final int maximum,
            final Instant started,
            final Instant deadline,
            final Clock clock)
            throws Exception {
        require(
                wire != null && wire.body() != null && wire.completedAt() != null,
                "UI_FILE_COMPLETE_WIRE_REQUIRED");
        wire.body().read(maximum, budget());
        require(
                !wire.completedAt().isBefore(started)
                        && !wire.completedAt().isAfter(deadline)
                        && !wire.completedAt().isAfter(clock.instant()),
                "UI_FILE_WIRE_COMPLETION_WINDOW");
        final var response =
                responses
                        .addObject()
                        .put("role", role)
                        .put("status", wire.status())
                        .put("bodyReadToEof", true)
                        .put("completedAt", wire.completedAt().toString());
        response.set("bodyPin", ExportValidationSupport.JSON.valueToTree(wire.body()));
    }

    private static void checkTime(final Clock clock, final Instant deadline, final long start) {
        require(
                clock.instant().isBefore(deadline)
                        && System.nanoTime() - start < Duration.ofSeconds(210).toNanos(),
                "UI_FILE_SOURCE_210_SECONDS");
    }

    private static DataExportSourceConfiguration configuration(final JsonNode settings) {
        fields(settings, Set.of("sourceInstance", "tenantScope", "apiOrigin"));
        final URI origin = URI.create(text(settings, "apiOrigin"));
        require(
                "https".equals(origin.getScheme())
                        && origin.getHost() != null
                        && origin.getRawUserInfo() == null
                        && origin.getRawQuery() == null
                        && origin.getRawFragment() == null
                        && (origin.getPort() == -1 || origin.getPort() == 443)
                        && (origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath())),
                "UI_FILE_EXACT_API_ORIGIN");
        return new DataExportSourceConfiguration(
                text(settings, "sourceInstance"),
                text(settings, "tenantScope"),
                new DataExportClientSettings(
                        origin,
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(30),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                        FILE_CAP),
                new EslResiliencePolicy(
                        Duration.ofSeconds(3),
                        1,
                        2,
                        2,
                        Duration.ofSeconds(30),
                        Duration.ofSeconds(210),
                        Duration.ofSeconds(210),
                        Duration.ZERO,
                        0,
                        1,
                        Duration.ofSeconds(60)));
    }

    private static void requireHostList(final JsonNode value) {
        require(
                value.isArray()
                        && value.size() == 1
                        && value.get(0).isTextual()
                        && HOST.equals(value.get(0).textValue()),
                "UI_FILE_LITERAL_DOWNLOAD_HOST_ONLY");
    }

    private static long number(final JsonNode node, final String name) {
        final var value = node.path(name);
        require(
                value.isIntegralNumber() && value.canConvertToLong(),
                "UI_FILE_EXACT_INTEGER_REQUIRED");
        return value.longValue();
    }

    private static ExportValidationSupport.Pin pin(final JsonNode value) {
        return new ExportValidationSupport.Pin(
                Path.of(text(value, "path")),
                text(value, "sha256").toLowerCase(Locale.ROOT),
                number(value, "bytes"));
    }

    private static Path absolute(final String value) {
        final Path path = Path.of(value);
        require(
                path.isAbsolute() && path.normalize().equals(path),
                "UI_FILE_NORMALIZED_ABSOLUTE_PATH");
        return path;
    }

    private static void noLinks(final Path path) {
        for (Path at = path; at != null; at = at.getParent()) {
            require(!Files.isSymbolicLink(at), "UI_FILE_PATH_LINK");
        }
    }

    private static void fields(final JsonNode value, final Set<String> expected) {
        require(value != null && value.isObject(), "UI_FILE_EXACT_OBJECT_REQUIRED");
        final var actual = new HashSet<String>();
        value.fieldNames().forEachRemaining(actual::add);
        require(actual.equals(expected), "UI_FILE_EXACT_CONTROL_FIELDS");
    }

    private static ExportValidationSupport.Budget budget() {
        return new ExportValidationSupport.Budget(Duration.ofSeconds(30));
    }

    private static String code(final Exception failure) {
        final String message = failure.getMessage();
        return message != null && message.matches("[A-Z][A-Z0-9_]{1,159}")
                ? message
                : "UI_FILE_PRIVATE_FAILURE_WITHHELD";
    }
}
