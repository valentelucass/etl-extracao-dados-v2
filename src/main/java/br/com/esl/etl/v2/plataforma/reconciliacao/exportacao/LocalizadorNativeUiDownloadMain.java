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
public final class LocalizadorNativeUiDownloadMain {
    public static final String HOST = "eslcloud.s3.sa-east-1.amazonaws.com";
    public static final String UI_LINK = "download_from_observed_ui_link";
    private static final int FILE_CAP = 8388608;
    private static final int CONTROL_CAP = 262144;

    interface Port {
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
                            .put("schema", "LOCALIZADOR_INDEPENDENT_REQUEST_V1")
                            .put("profile", LocalizadorIndependentRequest.PROFILE)
                            .put("entity", "LOCALIZADOR")
                            .put("reportId", 8656)
                            .put("attemptId", "00000000-0000-0000-0000-000000000000")
                            .put("periodStart", start.toString())
                            .put("periodEnd", end.toString())
                            .put("zone", "America/Sao_Paulo");
            request.set("filters", filters);
            LocalizadorIndependentRequest.validate(request);
        }

        public JsonNode filters() {
            return filters.deepCopy();
        }
    }

    private LocalizadorNativeUiDownloadMain() {}

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
                    planning ? plan(inputPin.json(CONTROL_CAP, budget())) : execute(inputPin);
            inputPin.read(CONTROL_CAP, budget());
            out.println(ExportValidationSupport.JSON.writeValueAsString(result));
            return planning || result.path("downloadComplete").booleanValue() ? 0 : 3;
        } catch (final Exception failure) {
            err.println(code(failure));
            return 2;
        }
    }

    public static ObjectNode plan(final JsonNode input) {
        fields(input, Set.of("schema", "sourceSettings", "profile", "fileId", "operation"));
        require(
                "OFFICIAL_UI_FILE_DOWNLOAD_PLAN_INPUT_V1".equals(text(input, "schema"))
                        && LocalizadorIndependentRequest.PROFILE.equals(text(input, "profile"))
                        && UI_LINK.equals(text(input, "operation")),
                "LOCALIZADOR_UI_LINK_ONLY_PLAN");
        final long id = number(input, "fileId");
        require(id > 0 && Long.toString(id).length() <= 18, "UI_FILE_PLAN_ID");
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("schema", "OFFICIAL_UI_FILE_DOWNLOAD_PLAN_V1")
                .put("profile", LocalizadorIndependentRequest.PROFILE)
                .put("fileId", id)
                .put("operation", UI_LINK)
                .put(
                        "sourceConfigurationSha256",
                        DataExportRuntimeConfigurationFingerprint.from(
                                        configuration(input.required("sourceSettings")))
                                .sha256())
                .put("downloadHost", HOST)
                .put("maximumHttpRequests", 1)
                .put("maximumMetadataHttpRequests", 0)
                .put("maximumDownloadHttpRequests", 1)
                .put("maximumControlBytes", CONTROL_CAP)
                .put("maximumDownloadBytes", FILE_CAP)
                .put("sourceSeconds", 210)
                .put("requestSeconds", 30)
                .put("OriginalMaximumBytes", 1073741824L)
                .put("OriginalMaximumSeconds", 300)
                .put("JavaMaximumHeapMiB", 512)
                .put("retryMaximum", 0)
                .put("redirectMaximum", 0)
                .put("downloadAuthentication", "NONE")
                .put("Source", 0)
                .put("HTTP", 0)
                .put("SQL", 0)
                .put("sourceAdmissionGranted", false)
                .put("uiAcquisitionQualified", false)
                .put("downloadComplete", false);
    }

    public static ObjectNode execute(final ExportValidationSupport.Pin inputPin) throws Exception {
        return execute(inputPin, null, Clock.systemUTC());
    }

    static ObjectNode execute(
            final ExportValidationSupport.Pin inputPin, final Port authoredPort, final Clock clock)
            throws Exception {
        final var validationBudget = budget();
        final var input = inputPin.json(CONTROL_CAP, validationBudget);
        final String operation = text(input, "operation");
        require(UI_LINK.equals(operation), "LOCALIZADOR_UI_LINK_ONLY_OPERATION");
        final var members =
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
                        "downloadHosts",
                        "uiObservedLinkPin",
                        "uiObservedLinkReceiptPin",
                        "uiObservedLinkStageClosurePin");
        fields(input, members);
        require(
                "ROOT_OFFICIAL_UI_FILE_DOWNLOAD_INPUT_V1".equals(text(input, "schema")),
                "UI_FILE_DOWNLOAD_INPUT_SCHEMA");
        requireProfile(input);
        final var uiPin = pin(input.required("uiAcquisitionPin"));
        final var filterPin = pin(input.required("uiFilterProofPin"));
        final var uiJson = uiPin.json(CONTROL_CAP, validationBudget);
        final var filterJson = filterPin.json(CONTROL_CAP, validationBudget);
        final var ui = exactUi(input, uiJson, filterJson);
        final var contextPin = pin(input.required("physicalContextPin"));
        final var codePin = pin(input.required("codeUnitPin"));
        final var context = contextPin.json(CONTROL_CAP, validationBudget);
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
        final int maximumHttp = 1;
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
                            .put("profile", LocalizadorIndependentRequest.PROFILE)
                            .put("entity", "LOCALIZADOR")
                            .put("reportId", 8656)
                            .put("fileId", ui.fileId())
                            .put("periodStart", ui.start().toString())
                            .put("periodEnd", ui.end().toString())
                            .put("uiObservedRows", ui.observedRows())
                            .put("uiObservedColumns", 26)
                            .put(
                                    "filterScopePolicy",
                                    LocalizadorIndependentRequest.FILTER_SCOPE_POLICY)
                            .put("updatedAtFilter", "")
                            .put("uiFileNotRegenerated", true)
                            .put("POST", 0)
                            .put("SQL", 0)
                            .put("metadataHttpThisInvocation", 0)
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
                final URI target =
                        observedUiLink(
                                input,
                                ui,
                                uiPin,
                                filterPin,
                                text(context, "executingRootUUID"),
                                pins);
                final var grantPin = pin(context.required("localizadorNativeGrantPin"));
                final var grant = grantPin.json(CONTROL_CAP, budget());
                requireGrant(grant, input, context, fingerprint, codePin, started, deadline);
                pins.add(grantPin);
                result.set("grantPin", ExportValidationSupport.JSON.valueToTree(grantPin));
                result.set("uiObservedLinkPin", input.required("uiObservedLinkPin").deepCopy());
                result.set(
                        "uiObservedLinkReceiptPin",
                        input.required("uiObservedLinkReceiptPin").deepCopy());
                result.set(
                        "uiObservedLinkStageClosurePin",
                        input.required("uiObservedLinkStageClosurePin").deepCopy());
                result.put("metadataHttpThisInvocation", 0).put("uiObservedDownloadLinkUsed", true);
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
        final var link = linkPin.json(CONTROL_CAP, budget());
        final var receipt = receiptPin.json(CONTROL_CAP, budget());
        final var closure = closurePin.json(CONTROL_CAP, budget());
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
                                        + "[a-z0-9]+-localizador-de-cargas_[0-9]{4}_[0-9]{2}_[0-9]{2}_[0-9]{2}_[0-9]{2}\\.xlsx"),
                "LOCALIZADOR_UI_LINK_EXACT_FILENAME_FAMILY");
        pins.add(linkPin);
        pins.add(receiptPin);
        pins.add(closurePin);
        return target;
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

    private static ExactUi exactUi(
            final JsonNode input, final JsonNode acquired, final JsonNode proof) {
        require(
                "ROOT_LOCALIZADOR_OFFICIAL_UI_ACQUISITION_V1".equals(text(acquired, "schema"))
                        && acquired.path("uiGenerated").isBoolean()
                        && acquired.path("uiGenerated").booleanValue()
                        && number(acquired, "observedColumns") == 26,
                "UI_FILE_OFFICIAL_UI_ACQUISITION_REQUIRED");
        require(
                "ROOT_LOCALIZADOR_UI_FILTER_PROOF_V1".equals(text(proof, "schema"))
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
                LocalizadorIndependentRequest.PROFILE.equals(text(value, "profile"))
                        && "LOCALIZADOR".equals(text(value, "entity"))
                        && number(value, "reportId") == 8656,
                "LOCALIZADOR_NATIVE_PROFILE_ONLY");
    }

    static void requireGrant(
            final JsonNode grant,
            final JsonNode input,
            final JsonNode context,
            final String fingerprint,
            final ExportValidationSupport.Pin codePin,
            final Instant started,
            final Instant deadline) {
        require(
                "ROOT_LOCALIZADOR_UI_LINK_ONLY_NATIVE_GRANT_V1".equals(text(grant, "schema"))
                        && grant.path("approved").isBoolean()
                        && grant.get("approved").booleanValue()
                        && UI_LINK.equals(text(grant, "operation"))
                        && text(context, "executingRootUUID")
                                .equals(text(grant, "executingRootUUID"))
                        && fingerprint.equals(text(grant, "sourceConfigurationSha256"))
                        && codePin.sha256().equals(text(grant, "codeUnitSha256")),
                "LOCALIZADOR_NATIVE_EXACT_ROOT_GRANT");
        requireProfile(grant);
        require(
                number(grant, "fileId") == number(input, "fileId")
                        && text(input, "periodStart").equals(text(grant, "periodStart"))
                        && text(input, "periodEnd").equals(text(grant, "periodEnd"))
                        && "America/Sao_Paulo".equals(text(grant, "zone"))
                        && LocalizadorIndependentRequest.FILTER_SCOPE_POLICY.equals(
                                text(grant, "filterScopePolicy"))
                        && grant.path("updatedAtFilter").isTextual()
                        && grant.get("updatedAtFilter").textValue().isEmpty()
                        && "NONE".equals(text(grant, "downloadAuthentication")),
                "LOCALIZADOR_NATIVE_GRANT_UI_SCOPE");
        for (final String name :
                Set.of(
                        "uiAcquisitionPin",
                        "uiFilterProofPin",
                        "uiObservedLinkPin",
                        "uiObservedLinkReceiptPin",
                        "uiObservedLinkStageClosurePin")) {
            require(
                    pin(input.required(name)).equals(pin(grant.required(name))),
                    "LOCALIZADOR_NATIVE_GRANT_EXACT_UI_PINS");
        }
        for (final String name :
                Set.of("OriginalMaximum", "SourceMaximum", "HTTPMaximum", "DOWNLOADHTTPMaximum")) {
            require(number(grant, name) == 1, "LOCALIZADOR_NATIVE_GRANT_ONE_DOWNLOAD");
        }
        for (final String name :
                Set.of(
                        "INFOHTTPMaximum",
                        "DATAHTTPMaximum",
                        "retryMaximum",
                        "redirectMaximum",
                        "SQL")) {
            require(number(grant, name) == 0, "LOCALIZADOR_NATIVE_GRANT_NO_METADATA_OR_FALLBACK");
        }
        require(
                number(grant, "SourceSeconds") == 210
                        && number(grant, "OriginalSeconds") == 300
                        && number(grant, "OriginalBytes") == 1073741824L
                        && number(grant, "maximumDownloadBytes") == FILE_CAP
                        && number(grant, "requestSeconds") == 30
                        && !grant.has("pageIndex")
                        && !grant.has("effectiveHTTPMaximum"),
                "LOCALIZADOR_NATIVE_GRANT_FINITE_CAPS");
        final Instant created = Instant.parse(text(grant, "createdUtc"));
        final Instant expires = Instant.parse(text(grant, "expiresUtc"));
        require(
                !created.isAfter(started)
                        && deadline.isBefore(expires)
                        && expires.isAfter(created)
                        && Duration.between(created, expires).compareTo(Duration.ofSeconds(300))
                                <= 0,
                "LOCALIZADOR_NATIVE_GRANT_FRESH_FINITE_WINDOW");
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
        require(bytes.length <= CONTROL_CAP, "UI_FILE_RESULT_BOUND");
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
        resultPin.read(CONTROL_CAP, postcheck);
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
                        1,
                        1,
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
