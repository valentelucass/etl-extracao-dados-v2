package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;
import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.text;

import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Real opt-in workflow, called only inside the existing consumed operational authority. */
public final class IndependentExportRuntime {
    private IndependentExportRuntime() {}

    public static void validate(
            final JsonNode selection, final ExportValidationSupport.Budget budget)
            throws Exception {
        require(
                selection.path("maximumDurationSeconds").isIntegralNumber()
                        && selection.path("maximumDurationSeconds").asInt() >= 30
                        && selection.path("maximumDurationSeconds").asInt() <= 240
                        && selection.path("maximumHttpRequests").isIntegralNumber()
                        && selection.path("maximumHttpRequests").asInt() > 0
                        && selection.path("maximumHttpRequests").asInt() <= 64,
                "EXPORT_BOUNDED_OPERATIONAL_CYCLE");
        for (final String field :
                List.of("requestPin", "configPin", "mappingPin", "physicalContextPin")) {
            ExportValidationSupport.Pin.from(selection.required(field)).json(262144, budget);
        }
        ExportValidationSupport.Pin.from(selection.required("codeUnitPin")).read(8388608, budget);
        require(
                !(selection.has("dashboardBridgePin") && selection.has("dashboardInputRecipePin")),
                "DASHBOARD_ONE_INPUT_ROUTE_REQUIRED");
        if (selection.has("dashboardInputRecipePin")) {
            final var recipe =
                    ExportValidationSupport.Pin.from(selection.required("dashboardInputRecipePin"))
                            .json(262144, budget);
            CapturedDashboardInputProducer.validateRecipe(selection, recipe);
        }
        final var physical =
                ExportValidationSupport.Pin.from(selection.required("physicalContextPin"))
                        .json(262144, budget);
        require(
                "ROOT_INDEPENDENT_EXPORT_PHYSICAL_CONTEXT_V1".equals(text(physical, "schema"))
                        && text(selection, "sourceConfigurationSha256")
                                .equals(text(physical, "sourceConfigurationSha256"))
                        && ExportValidationSupport.Pin.from(selection.required("codeUnitPin"))
                                .sha256()
                                .equals(text(physical, "codeUnitSha256")),
                "EXPORT_ROOT_PHYSICAL_SOURCE_SCOPE");
        final var privateRoot = absolute(physical, "privateRoot");
        for (final String field :
                List.of(
                        "outputDirectory",
                        "journalDirectory",
                        "resultPath",
                        "referenceCallbackPath")) {
            final var path = absolute(selection, field);
            require(
                    path.startsWith(privateRoot) && !path.equals(privateRoot),
                    "EXPORT_PRIVATE_OUTPUT_SCOPE");
            for (Path part = path; part != null; part = part.getParent()) {
                require(!Files.isSymbolicLink(part), "EXPORT_OPERATIONAL_PATH_LINK");
            }
        }
        require(
                Files.isDirectory(absolute(selection, "outputDirectory"))
                        && Files.isDirectory(absolute(selection, "journalDirectory").getParent())
                        && Files.isDirectory(absolute(selection, "resultPath").getParent())
                        && !Files.exists(absolute(selection, "resultPath")),
                "EXPORT_FRESH_OPERATIONAL_RESULT");
        final var requestedHosts = selection.required("downloadHosts");
        require(
                requestedHosts.isArray()
                        && requestedHosts.size() <= 8
                        && requestedHosts.equals(physical.required("downloadHosts")),
                "EXPORT_DOWNLOAD_HOST_BINDING");
        if (IndependentExportRequest.PROFILE.equals(text(selection, "profile"))) {
            require(
                    selection.path("maximumPolls").isIntegralNumber()
                            && selection.path("maximumPolls").asInt() >= 0
                            && selection.path("maximumPolls").asInt() <= 10,
                    "EXPORT_FINITE_POLL_GRANT");
            IndependentExportValidation.columns(
                    ExportValidationSupport.Pin.from(selection.required("mappingPin"))
                            .json(262144, budget));
        } else {
            require(
                    ColetasIndependentRequest.PROFILE.equals(text(selection, "profile"))
                            && selection.path("maximumPages").isIntegralNumber()
                            && selection.path("maximumPages").asInt() > 0
                            && selection.path("maximumPages").asInt() <= 63
                            && selection.path("pageSize").isIntegralNumber()
                            && selection.path("pageSize").asInt() > 0
                            && selection.path("pageSize").asInt() <= 100
                            && selection.path("maximumRows").isIntegralNumber()
                            && selection.path("maximumRows").asInt() > 0
                            && selection.path("maximumRows").asInt() <= 12000,
                    "COLETAS_FINITE_CAPTURE_GRANT");
            final var template =
                    ExportValidationSupport.Pin.from(selection.required("policyTemplatePin"))
                            .json(262144, budget);
            require(
                    "COLETAS_COMMON31_POLICY_TEMPLATE_V1".equals(text(template, "schema"))
                            && template.path("rules").isObject(),
                    "COLETAS_PREAUTHORIZED_POLICY_RULES");
        }
    }

    public static ObjectNode execute(
            final RuntimeConfiguration configuration,
            final ExportValidationSupport.Pin selectionPin,
            final JsonNode selection,
            final long monotonicStart,
            final Instant started)
            throws Exception {
        return execute(
                configuration,
                selectionPin,
                selection,
                monotonicStart,
                started,
                RootOwnedWireChannel::new);
    }

    @FunctionalInterface
    interface WireFactory {
        OwnedExportWire create(
                ExportValidationSupport.Pin contextPin,
                PersistentExportJournal journal,
                String sourceConfigurationSha)
                throws Exception;
    }

    @FunctionalInterface
    interface JournalFactory {
        PersistentExportJournal open(
                Path directory, String requestSha, String codeSha, UUID attempt, boolean create)
                throws Exception;
    }

    // Package-private only: synthetic fixtures can traverse the real capture/comparison path.
    // Production execute above always composes the pinned Root-owned native channel.
    static ObjectNode execute(
            final RuntimeConfiguration configuration,
            final ExportValidationSupport.Pin selectionPin,
            final JsonNode selection,
            final long monotonicStart,
            final Instant started,
            final WireFactory wireFactory)
            throws Exception {
        return execute(
                configuration,
                selectionPin,
                selection,
                monotonicStart,
                started,
                wireFactory,
                System::nanoTime,
                wait -> Thread.sleep(wait.toMillis(), wait.toNanosPart() % 1000000));
    }

    // Fixture timing is package-private; the production entry above always uses the real clock and
    // wait.
    static ObjectNode execute(
            final RuntimeConfiguration configuration,
            final ExportValidationSupport.Pin selectionPin,
            final JsonNode selection,
            final long monotonicStart,
            final Instant started,
            final WireFactory wireFactory,
            final java.util.function.LongSupplier channelTime,
            final ExportFilePoller.Waiter pollWait)
            throws Exception {
        return execute(
                configuration,
                selectionPin,
                selection,
                monotonicStart,
                started,
                wireFactory,
                channelTime,
                pollWait,
                (directory, requestSha, codeSha, attempt, create) ->
                        create
                                ? PersistentExportJournal.create(
                                        directory, requestSha, codeSha, attempt)
                                : PersistentExportJournal.open(
                                        directory, requestSha, codeSha, attempt));
    }

    // Package-private fixture port; production reaches only the real durable factory above.
    static ObjectNode execute(
            final RuntimeConfiguration configuration,
            final ExportValidationSupport.Pin selectionPin,
            final JsonNode selection,
            final long monotonicStart,
            final Instant started,
            final WireFactory wireFactory,
            final java.util.function.LongSupplier channelTime,
            final ExportFilePoller.Waiter pollWait,
            final JournalFactory journalFactory)
            throws Exception {
        final Clock clock = configuration.clock();
        final Duration duration =
                Duration.ofSeconds(selection.path("maximumDurationSeconds").asInt());
        final Instant deadline = started.plus(duration).minusSeconds(15);
        final var requestPin = ExportValidationSupport.Pin.from(selection.required("requestPin"));
        final var request = requestPin.json(262144, remaining(monotonicStart, duration));
        final var codePin = ExportValidationSupport.Pin.from(selection.required("codeUnitPin"));
        final var journalPath = absolute(selection, "journalDirectory");
        final UUID attempt = UUID.fromString(text(request, "attemptId"));
        final boolean create = !Files.exists(journalPath);
        try (var journal =
                journalFactory.open(
                        journalPath, requestPin.sha256(), codePin.sha256(), attempt, create)) {
            journal.record(
                    "CYCLE_GRANT",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("selectionSha256", selectionPin.sha256())
                            .put("startedAt", started.toString())
                            .put("deadline", deadline.toString())
                            .put(
                                    "maximumHttpRequests",
                                    selection.path("maximumHttpRequests").asInt()));
            final var physical =
                    wireFactory.create(
                            ExportValidationSupport.Pin.from(
                                    selection.required("physicalContextPin")),
                            journal,
                            text(selection, "sourceConfigurationSha256"));
            final var hosts = new HashSet<String>();
            selection
                    .required("downloadHosts")
                    .forEach(
                            host -> {
                                require(
                                        host.isTextual()
                                                && host.textValue().matches("[a-z0-9.-]{1,253}"),
                                        "EXPORT_DOWNLOAD_HOST");
                                hosts.add(host.textValue());
                            });
            final var transport =
                    new CurlExportTransport(
                            configuration.dataExport().orElseThrow(),
                            journal,
                            physical,
                            clock,
                            deadline,
                            selection.path("maximumHttpRequests").asInt(),
                            hosts);
            if (selection.has("continuationGrant")) {
                journal.regrant(
                        selection.required("continuationGrant"),
                        selectionPin.sha256(),
                        clock.instant(),
                        deadline);
            }
            // Concrete operator handoff. Source work proceeds in this same invocation without
            // waiting for human approval at each generation query.
            if (journal.latest("REFERENCE_NOTICE") == null) {
                final var notice =
                        ExportValidationSupport.JSON
                                .createObjectNode()
                                .put("schema", "INDEPENDENT_UI_REFERENCE_REQUEST_V1")
                                .put("attemptId", attempt.toString())
                                .put("profile", text(request, "profile"))
                                .put("periodStart", text(request, "periodStart"))
                                .put("periodEnd", text(request, "periodEnd"))
                                .put("zone", text(request, "zone"))
                                .put("requiredEvent", "UI_EXPORT_CLICK")
                                .put(
                                        "referenceCallbackPath",
                                        absolute(selection, "referenceCallbackPath").toString())
                                .put(
                                        "operatorAction",
                                        "Exportar pela UI com todos os filtros observados, sem reutilizar o arquivo da API");
                notice.set("filters", request.required("filters"));
                notice.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
                journal.record("REFERENCE_NOTICE", notice);
            }
            ObjectNode diagnostic;
            try {
                final JsonNode api =
                        IndependentExportRequest.PROFILE.equals(text(request, "profile"))
                                ? fretes(
                                        request,
                                        requestPin,
                                        selection,
                                        transport,
                                        journal,
                                        clock,
                                        deadline,
                                        monotonicStart,
                                        duration,
                                        channelTime,
                                        pollWait)
                                : coletas(
                                        request,
                                        requestPin,
                                        selection,
                                        transport,
                                        journal,
                                        deadline);
                if (api == null) {
                    diagnostic = pending(request, text(journal.latest("SOURCE_PENDING"), "status"));
                } else {
                    final var callbackPath = absolute(selection, "referenceCallbackPath");
                    if (!Files.isRegularFile(callbackPath)) {
                        diagnostic = pending(request, "PENDING_INDEPENDENT_UI_REFERENCE");
                    } else {
                        final var callbackPin = pinExisting(callbackPath, 262144);
                        final var callback =
                                callbackPin.json(262144, remaining(monotonicStart, duration));
                        require(
                                "INDEPENDENT_UI_REFERENCE_CALLBACK_V1"
                                                .equals(text(callback, "schema"))
                                        && attempt.toString().equals(text(callback, "attemptId"))
                                        && ExportValidationSupport.Pin.from(
                                                        callback.required("requestPin"))
                                                .equals(requestPin),
                                "EXPORT_UI_CALLBACK_BINDING");
                        final var pair =
                                ExportValidationSupport.JSON
                                        .createObjectNode()
                                        .put("schema", "INDEPENDENT_EXPORT_PAIR_V1");
                        for (final String field :
                                List.of("requestPin", "configPin", "mappingPin")) {
                            pair.set(field, selection.required(field));
                        }
                        if (ColetasIndependentRequest.PROFILE.equals(text(request, "profile"))) {
                            final var policyPin =
                                    ExportValidationSupport.Pin.from(
                                            callback.required("policyPin"));
                            final var actualPolicy =
                                    policyPin.json(262144, remaining(monotonicStart, duration));
                            final var rules = ((ObjectNode) actualPolicy).deepCopy();
                            rules.remove(
                                    List.of(
                                            "schema",
                                            "attemptId",
                                            "sourceSha256",
                                            "workbookSha256",
                                            "independentPresentationEvidencePin"));
                            final var template =
                                    ExportValidationSupport.Pin.from(
                                                    selection.required("policyTemplatePin"))
                                            .json(262144, remaining(monotonicStart, duration));
                            require(
                                    rules.equals(template.required("rules")),
                                    "COLETAS_CALLBACK_POLICY_RULES_DRIFT");
                            pair.set(
                                    "policyPin",
                                    ExportValidationSupport.JSON.valueToTree(policyPin));
                        }
                        if (selection.has("dashboardBridgePin")) {
                            pair.set(
                                    "dashboardBridgePin", selection.required("dashboardBridgePin"));
                        }
                        if (selection.has("dashboardInputRecipePin")) {
                            pair.set(
                                    "dashboardInputRecipePin",
                                    selection.required("dashboardInputRecipePin"));
                            pair.set(
                                    "sourceConfigurationSha256",
                                    selection.required("sourceConfigurationSha256"));
                        }
                        pair.set("api", api);
                        pair.set("ui", callback.required("ui"));
                        final var binding =
                                pair.deepCopy()
                                        .put("schema", "INDEPENDENT_EXPORT_PAIR_BINDING_V1")
                                        .put("attemptId", attempt.toString());
                        pair.set(
                                "admissionPin",
                                ExportValidationSupport.JSON.valueToTree(
                                        journal.pin(journal.record("PAIR_BINDING", binding))));
                        final var pairPin = journal.pin(journal.record("PAIR_INPUT", pair));
                        diagnostic =
                                IndependentExportValidation.compare(
                                        pairPin.json(262144, remaining(monotonicStart, duration)),
                                        remaining(monotonicStart, duration));
                        diagnostic.set(
                                "pairManifestPin",
                                ExportValidationSupport.JSON.valueToTree(pairPin));
                        diagnostic.set(
                                "uiCallbackPin",
                                ExportValidationSupport.JSON.valueToTree(callbackPin));
                        callbackPin.read(262144, remaining(monotonicStart, duration));
                    }
                }
            } catch (final Exception refusedOrUnknown) {
                // Record only a closed error code; parser/HTTP errors may contain private values.
                diagnostic =
                        pending(request, "PENDING_RECONCILIATION_AFTER_REFUSAL_OR_UNKNOWN_EFFECT");
                final String code = refusedOrUnknown.getMessage();
                diagnostic.put(
                        "reasonCode",
                        code != null && code.matches("[A-Z][A-Z0-9_]{0,119}")
                                ? code
                                : "EXPORT_PRIVATE_FAILURE_DETAILS_WITHHELD");
            }
            // Bind positive Coletas results too; its comparator result omits attemptId.
            diagnostic
                    .put("attemptId", attempt.toString())
                    .put("profile", text(request, "profile"));
            diagnostic.put(
                    "operationalAuthority", "EXISTING_WINDOWS_SQL_CAPABILITY_CONSUMED_BY_CALLER");
            diagnostic.put("HTTP", journal.startedWires()).put("Source", journal.startedWires());
            diagnostic.put("httpAdmittedOperations", journal.count("HTTP_ADMITTED"));
            diagnostic.put(
                    "wireOutcomeUnknown",
                    journal.count("HTTP_ADMITTED") != journal.count("HTTP_COMPLETE"));
            diagnostic.remove("SQL"); // Authority SQL is owned by the existing authority receipt.
            diagnostic.put("mainResumeAllowed", false).put("dashboardConsumptionVerified", false);
            diagnostic.set(
                    "referenceNoticePin",
                    ExportValidationSupport.JSON.valueToTree(
                            journal.latestPin("REFERENCE_NOTICE")));
            diagnostic.set("requestPin", ExportValidationSupport.JSON.valueToTree(requestPin));
            diagnostic.set("selectionPin", ExportValidationSupport.JSON.valueToTree(selectionPin));
            diagnostic.set("codeUnitPin", ExportValidationSupport.JSON.valueToTree(codePin));
            // Result publication has a separate bounded closeout; it cannot authorize more Source.
            final var closing = new ExportValidationSupport.Budget(Duration.ofSeconds(5));
            selectionPin.read(262144, closing);
            requestPin.read(262144, closing);
            final String outcome = journal.record("CYCLE_OUTCOME", diagnostic);
            final var outcomePin = journal.pin(outcome);
            return OperationalExportReceipt.persist(
                    absolute(selection, "resultPath"),
                    diagnostic,
                    selectionPin,
                    outcomePin,
                    started,
                    clock,
                    closing);
        }
    }

    private static JsonNode fretes(
            final JsonNode request,
            final ExportValidationSupport.Pin requestPin,
            final JsonNode selection,
            final CurlExportTransport transport,
            final PersistentExportJournal journal,
            final Clock clock,
            final Instant deadline,
            final long monotonicStart,
            final Duration duration,
            final java.util.function.LongSupplier channelTime,
            final ExportFilePoller.Waiter pollWait)
            throws Exception {
        final var channel = new IndependentExportCycle.SerialChannel(channelTime);
        if (journal.latest("INITIAL_CONTROL") == null) {
            require(
                    journal.latest("POST_RESERVED") == null,
                    "EXPORT_POST_RECONCILIATION_REQUIRED_NO_NEW_POST");
            final var cycle =
                    new IndependentExportCycle(
                            channel,
                            (path, body, timeout) -> {
                                final var wire = transport.api("POST", path, body, timeout);
                                journal.record(
                                        "API_REQUEST_EVENT", journal.latest("ROOT_WIRE_RECEIPT"));
                                return new IndependentExportCycle.Reply(
                                        wire.status(), wire.bytes());
                            },
                            journal,
                            Duration.ofSeconds(30));
            final var submitted = cycle.submit(request);
            journal.initial(
                    submitted,
                    submitted.pendingControl(selection.path("maximumPolls").asInt(), deadline));
        }
        final var control = journal.resumeControl();
        final var poller =
                new ExportFilePoller(
                        control,
                        channel,
                        (path, timeout) -> {
                            final var wire = transport.api("GET", path, null, timeout);
                            return new IndependentExportCycle.Reply(wire.status(), wire.bytes());
                        },
                        journal,
                        clock);
        final var driven =
                poller.drive(
                        wait -> {
                            remaining(monotonicStart, duration).check();
                            pollWait.await(wait);
                        });
        if (driven.control().state() != ExportFileLifecycle.State.GENERATED) {
            journal.record(
                    "SOURCE_PENDING",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("status", driven.status())
                            .put("fileId", driven.control().fileId())
                            .put("latestReceiptSha256", driven.control().latestReceiptSha256()));
            return null;
        }
        JsonNode download = journal.latest("DOWNLOAD_COMPLETE");
        if (download == null) {
            require(
                    journal.latest("DOWNLOAD_RESERVED") == null,
                    "EXPORT_DOWNLOAD_RECONCILIATION_REQUIRED");
            final var last = journal.latest("POLL_RESPONSE");
            final var generated =
                    journal.rawResponse(text(last, "responseSha256"))
                            .json(262144, remaining(monotonicStart, duration));
            require(
                    driven.control().fileId().equals(generated.path("id").asText())
                            && "generated".equals(generated.path("status").asText()),
                    "EXPORT_GENERATED_RESPONSE_BINDING");
            journal.record(
                    "DOWNLOAD_RESERVED",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("fileId", driven.control().fileId())
                            .put("responseSha256", text(last, "responseSha256")));
            final var wire = transport.download(URI.create(text(generated, "download_url")));
            require(
                    wire.status() >= 200 && wire.status() < 300 && wire.body() != null,
                    "EXPORT_DOWNLOAD_STOP_NON_2XX");
            download =
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put("fileId", driven.control().fileId())
                            .set("dataPin", ExportValidationSupport.JSON.valueToTree(wire.body()));
            journal.record("DOWNLOAD_COMPLETE", download);
        }
        final var dataPin = ExportValidationSupport.Pin.from(download.required("dataPin"));
        final var mapping =
                ExportValidationSupport.Pin.from(selection.required("mappingPin"))
                        .json(262144, remaining(monotonicStart, duration));
        final var budget = remaining(monotonicStart, duration);
        BoundedExportWorkbook.verifyFretes(
                dataPin.read(8388608, budget),
                IndependentExportValidation.columns(mapping),
                budget,
                LocalDate.parse(text(request, "periodStart")),
                LocalDate.parse(text(request, "periodEnd")));
        journal.record("API_CONTENT_COMPLETE", download);
        return acquisition(
                request, requestPin, dataPin, text(download, "fileId"), "API_EXPORT_XLSX", journal);
    }

    private static JsonNode coletas(
            final JsonNode request,
            final ExportValidationSupport.Pin requestPin,
            final JsonNode selection,
            final CurlExportTransport transport,
            final PersistentExportJournal journal,
            final Instant deadline)
            throws Exception {
        final var capture =
                ColetasIntervalCapture.capture(
                        request,
                        transport,
                        journal,
                        absolute(selection, "outputDirectory"),
                        selection.path("maximumPages").asInt(),
                        selection.path("pageSize").asInt(),
                        selection.path("maximumRows").asLong(),
                        deadline);
        if (!"COLETAS_SOURCE_COMPLETE".equals(capture.path("status").asText())
                || !capture.has("dataPin")) {
            journal.record(
                    "SOURCE_PENDING",
                    ExportValidationSupport.JSON
                            .createObjectNode()
                            .put(
                                    "status",
                                    capture.has("dataPin")
                                            ? text(capture, "status")
                                            : "PENDING_COMPLETE_EMPTY_CAPTURE_REPRESENTATION"));
            return null;
        }
        return acquisition(
                request,
                requestPin,
                ExportValidationSupport.Pin.from(capture.required("dataPin")),
                "capture:" + text(request, "attemptId"),
                "API_DATA_JSONL",
                journal);
    }

    private static JsonNode acquisition(
            final JsonNode request,
            final ExportValidationSupport.Pin requestPin,
            final ExportValidationSupport.Pin data,
            final String fileId,
            final String route,
            final PersistentExportJournal journal)
            throws Exception {
        final var event = journal.latest("API_REQUEST_EVENT");
        require(
                event != null
                        && event.path("started").asBoolean()
                        && event.path("configSubmittedAt").isTextual(),
                "EXPORT_ACTUAL_REQUEST_EVENT_REQUIRED");
        final var observed =
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put(
                                "event",
                                "API_EXPORT_XLSX".equals(route)
                                        ? "API_EXPORT_POST"
                                        : "API_DATA_INTERVAL_START")
                        .put("attemptId", text(request, "attemptId"))
                        .put("fileId", fileId)
                        .put("requestId", text(event, "operationId"))
                        .put("zone", text(request, "zone"))
                        .put(
                                "requestedAt",
                                java.time.OffsetDateTime.parse(text(event, "configSubmittedAt"))
                                        .atZoneSameInstant(ZoneId.of(text(request, "zone")))
                                        .toOffsetDateTime()
                                        .toString())
                        .put("timePrecision", "SECOND");
        observed.set("filters", request.required("filters"));
        observed.put("requestedAtBasis", "NATIVE_STDIN_CONFIG_COMPLETED");
        observed.put("networkDispatchInstantProven", false);
        observed.set(
                "wireReceiptPin",
                ExportValidationSupport.JSON.valueToTree(journal.latestPin("API_REQUEST_EVENT")));
        final var evidence =
                observed.deepCopy()
                        .put("schema", "INDEPENDENT_EXPORT_ACQUISITION_V1")
                        .put("route", route)
                        .put("cycleId", text(request, "attemptId"))
                        .put("requestSha256", requestPin.sha256())
                        .put("reportId", request.path("reportId").asInt())
                        .put("periodStart", text(request, "periodStart"))
                        .put("periodEnd", text(request, "periodEnd"));
        evidence.set(
                "observationPin",
                ExportValidationSupport.JSON.valueToTree(
                        journal.pin(journal.record("API_OBSERVATION", observed))));
        evidence.set("dataPin", ExportValidationSupport.JSON.valueToTree(data));
        final var descriptor = ExportValidationSupport.JSON.createObjectNode();
        descriptor.set("dataPin", ExportValidationSupport.JSON.valueToTree(data));
        descriptor.set(
                "evidencePin",
                ExportValidationSupport.JSON.valueToTree(
                        journal.pin(journal.record("API_ACQUISITION", evidence))));
        return descriptor;
    }

    private static ObjectNode pending(final JsonNode request, final String status) {
        return ExportValidationSupport.JSON
                .createObjectNode()
                .put("schema", "INDEPENDENT_EXPORT_OPERATIONAL_RESULT_V1")
                .put("attemptId", text(request, "attemptId"))
                .put("profile", text(request, "profile"))
                .put("periodStart", text(request, "periodStart"))
                .put("periodEnd", text(request, "periodEnd"))
                .put("status", status)
                .put("snapshotIdentityProven", false)
                .put("entityQualified", false)
                .put("mainResumeAllowed", false)
                .put("dashboardConsumptionVerified", false);
    }

    private static Path absolute(final JsonNode node, final String field) {
        final var path = Path.of(text(node, field));
        require(path.isAbsolute(), "EXPORT_OPERATIONAL_ABSOLUTE_PATH");
        return path.normalize();
    }

    private static ExportValidationSupport.Pin pinExisting(final Path path, final int maximum)
            throws Exception {
        require(Files.size(path) > 0 && Files.size(path) <= maximum, "EXPORT_CALLBACK_BOUND");
        final byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(maximum + 1);
        }
        require(bytes.length <= maximum, "EXPORT_CALLBACK_GROWTH");
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }

    private static ExportValidationSupport.Budget remaining(
            final long start, final Duration maximum) {
        final long nanos =
                maximum.toNanos() - (System.nanoTime() - start) - Duration.ofSeconds(5).toNanos();
        require(nanos > 0, "EXPORT_OPERATIONAL_DEADLINE_PENDING");
        return new ExportValidationSupport.Budget(
                Duration.ofNanos(Math.min(nanos, Duration.ofSeconds(150).toNanos())));
    }
}
