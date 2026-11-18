package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.configuracao.RuntimeConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Authored data only; never loads a retained book, payload, credential or physical wire resource.
 */
public final class MainProfileCaptureFixture {
    /**
     * Authored event port only; the separate PersistentExportJournal tests use the durable public
     * factories.
     */
    private static final class MemoryChain implements PersistentExportJournal.EventChain {
        private final List<br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal.Event> events =
                new java.util.ArrayList<>();

        @Override
        public synchronized br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal.Event append(
                final String caseId,
                final UUID nonce,
                final br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal.Kind kind,
                final String detail) {
            final String previous =
                    events.isEmpty() ? "0".repeat(64) : events.get(events.size() - 1).sha256();
            final int sequence = events.size() + 1;
            final String sha =
                    ExportValidationSupport.sha256(
                            (previous + sequence + caseId + nonce + kind + detail)
                                    .getBytes(StandardCharsets.UTF_8));
            final var event =
                    new br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal.Event(
                            sequence, caseId, nonce, kind, detail, previous, sha);
            events.add(event);
            return event;
        }

        @Override
        public synchronized List<br.com.esl.etl.v2.plataforma.qualificacao.CampaignJournal.Event>
                read() {
            return List.copyOf(events);
        }
    }

    /**
     * Shared logical clock for authored wire responses; no real transport can select this fixture.
     */
    public static final class VirtualClock extends Clock {
        private final java.util.concurrent.atomic.AtomicLong nanos;
        private final ZoneId zone;

        public VirtualClock() {
            this(new java.util.concurrent.atomic.AtomicLong(), ZoneId.of("UTC"));
        }

        private VirtualClock(
                final java.util.concurrent.atomic.AtomicLong nanos, final ZoneId zone) {
            this.nanos = nanos;
            this.zone = zone;
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(final ZoneId selected) {
            return new VirtualClock(nanos, selected);
        }

        @Override
        public Instant instant() {
            return Instant.now().plusNanos(nanos.get());
        }

        public long nanoTime() {
            return System.nanoTime() + nanos.get();
        }

        public void advance(final Duration wait) {
            require(!wait.isNegative(), "FIXTURE_NONNEGATIVE_WAIT");
            nanos.addAndGet(wait.toNanos());
        }
    }

    public final Path directory;
    public final ObjectNode request;
    public final ObjectNode selection = ExportValidationSupport.JSON.createObjectNode();
    public final Clock clock;
    public String mode = "EQUAL";
    public boolean typedStageMode;
    public int calls;
    public long minimumObservedGapNanos = Long.MAX_VALUE;
    public boolean authorityWasConsumedBeforeEveryWire = true;
    public java.util.function.BooleanSupplier consumed = () -> false;
    public final List<String> routes = new java.util.ArrayList<>();
    private final boolean coletas;
    private final ExportPairFixture fretes;
    private final ColetasCommon31SyntheticFixture coleta;
    private final ObjectNode callback;
    private final ObjectNode ui;
    private final ExportValidationSupport.Pin requestPin;
    private final Path output;
    private long lastResponseNanos;
    private boolean stopped;
    private int page;
    private final java.util.Map<Path, MemoryChain> chains = new java.util.HashMap<>();

    public MainProfileCaptureFixture(final Path directory, final String profile, final Clock clock)
            throws Exception {
        this.directory = directory.toAbsolutePath();
        this.clock = clock;
        Files.createDirectories(this.directory);
        output = Files.createDirectory(this.directory.resolve("capture"));
        coletas = ColetasIndependentRequest.PROFILE.equals(profile);
        if (coletas) {
            fretes = null;
            coleta = new ColetasCommon31SyntheticFixture(this.directory);
            request =
                    ColetasCommon31SyntheticFixture.request()
                            .put("attemptId", UUID.randomUUID().toString());
            requestPin =
                    pin("request.json", ExportValidationSupport.JSON.writeValueAsBytes(request));
            final var config =
                    request.objectNode()
                            .put("schema", "INDEPENDENT_EXPORT_CONFIG_V1")
                            .put("profile", profile)
                            .put("zone", "America/Sao_Paulo")
                            .put("maximumRequestSeparationSeconds", 120);
            selection.set("configPin", node(pin("comparison-config.json", bytes(config))));
            selection.set(
                    "mappingPin", node(pin("mapping.json", bytes(ColetasCommonAdapter.catalog()))));
            ui =
                    descriptor(
                            pin("ui.xlsx", coleta.nativeWorkbookBytes()),
                            "UI_EXPORT_XLSX",
                            "UI_FILE_1");
            final var template =
                    request.objectNode().put("schema", "COLETAS_COMMON31_POLICY_TEMPLATE_V1");
            template.set("rules", coleta.policy.deepCopy());
            selection.set("policyTemplatePin", node(pin("policy-template.json", bytes(template))));
            selection.put("maximumPages", 2).put("pageSize", 1).put("maximumRows", 4);
        } else {
            coleta = null;
            fretes = new ExportPairFixture(this.directory);
            fretes.uiTime(observedAt(), "SECOND");
            request = fretes.request;
            final var pair = fretes.manifest();
            requestPin = ExportValidationSupport.Pin.from(pair.required("requestPin"));
            for (final String field : List.of("configPin", "mappingPin")) {
                selection.set(field, pair.required(field));
            }
            ui = (ObjectNode) pair.required("ui");
            selection.put("maximumPolls", 1);
        }
        callback =
                request.objectNode()
                        .put("schema", "INDEPENDENT_UI_REFERENCE_CALLBACK_V1")
                        .put("attemptId", request.path("attemptId").asText());
        callback.set("requestPin", node(requestPin));
        callback.set("ui", ui);
        selection
                .put("schema", "INDEPENDENT_EXPORT_OPERATIONAL_SELECTION_V1")
                .put("profile", profile)
                .put("maximumDurationSeconds", 120)
                .put("maximumHttpRequests", 3)
                .put("outputDirectory", output.toString())
                .put("journalDirectory", this.directory.resolve("journal").toString())
                .put("resultPath", this.directory.resolve("capture-result.json").toString())
                .put(
                        "referenceCallbackPath",
                        this.directory.resolve("reference-callback.json").toString());
        selection.set("requestPin", node(requestPin));
        selection.putArray("downloadHosts").add("download.synthetic.invalid");
    }

    public ExportValidationSupport.Pin freeze(final String sourceSha) throws Exception {
        selection.put("sourceConfigurationSha256", sourceSha);
        final var unit =
                pin(
                        "synthetic-code-unit.txt",
                        "SYNTHETIC_MAIN_SOURCE0_UNIT".getBytes(StandardCharsets.US_ASCII));
        selection.set("codeUnitPin", node(unit));
        final var physical =
                request.objectNode()
                        .put("schema", "ROOT_INDEPENDENT_EXPORT_PHYSICAL_CONTEXT_V1")
                        .put("sourceConfigurationSha256", sourceSha)
                        .put("codeUnitSha256", unit.sha256())
                        .put("privateRoot", directory.toString())
                        .put("fixtureOnly", true);
        physical.set("downloadHosts", selection.required("downloadHosts"));
        selection.set("physicalContextPin", node(pin("fixture-context.json", bytes(physical))));
        final var delivery =
                request.objectNode()
                        .put("schema", "INDEPENDENT_GOVERNED_CAPTURE_DELIVERY_PLAN_V1")
                        .put("kind", "EXACT_VALIDATED_CAPTURE_WITH_CONSUMER_ROUNDTRIP_V1")
                        .put("profile", request.path("profile").asText())
                        .put("attemptId", request.path("attemptId").asText())
                        .put("artifactPath", directory.resolve("delivery-capture.bin").toString())
                        .put(
                                "producerReceiptPath",
                                directory.resolve("delivery-producer.json").toString())
                        .put(
                                "consumerReceiptPath",
                                directory.resolve("delivery-consumer.json").toString())
                        .put(
                                "consumerRoundtripPath",
                                directory.resolve("delivery-roundtrip.bin").toString())
                        .put("resultPath", directory.resolve("delivery-result.json").toString());
        delivery.set("requestPin", node(requestPin));
        if (typedStageMode) {
            delivery.put("kind", "CAPTURED_GOVERNED_TYPED_STAGE_WITH_LOCAL_CONSUMER_V1");
            delivery.set("dashboardInputRecipePin", selection.required("dashboardInputRecipePin"));
        }
        selection.set("governedDeliveryPlanPin", node(pin("delivery-plan.json", bytes(delivery))));
        if ("PARTIAL".equals(mode) && coletas) {
            selection.put("maximumPages", 1);
        }
        if ("EQUAL".equals(mode) || "DIFFERENCE".equals(mode)) {
            if (!coletas) {
                if ("DIFFERENCE".equals(mode)) {
                    final var changed =
                            pin(
                                    "changed-ui.xlsx",
                                    ExportPairFixture.book(
                                            false, false, false, "1.100000000000001"));
                    // Rebuild the independent UI descriptor; no API values are changed.
                    callback.set("ui", descriptor(changed, "UI_EXPORT_XLSX", "UI_CHANGED_1"));
                }
                pin("reference-callback.json", bytes(callback));
            }
        }
        return pin("selection.json", bytes(selection));
    }

    public void useTypedStageCapture() throws Exception {
        typedStageMode = true;
        if (coletas) {
            coleta.source.put("pck_pds_postal_code", "12345678");
            coleta.row.set(15, ColetasCommon31SyntheticFixture.Cell.text("12345678"));
            ui.removeAll();
            ui.setAll(
                    descriptor(
                            pin("typed-ui.xlsx", coleta.nativeWorkbookBytes()),
                            "UI_EXPORT_XLSX",
                            "UI_TYPED_1"));
        } else {
            fretes.apiBook = CapturedDashboardInputProducerTest.fretesWorkbook(false, true);
            fretes.uiBook = fretes.apiBook.clone();
            ui.removeAll();
            ui.setAll(
                    descriptor(
                            pin("typed-ui.xlsx", fretes.uiBook), "UI_EXPORT_XLSX", "UI_TYPED_1"));
        }
        callback.set("ui", ui);
    }

    public ObjectNode execute(
            final RuntimeConfiguration configuration,
            final ExportValidationSupport.Pin pin,
            final JsonNode selected,
            final long nanos,
            final Instant instant)
            throws Exception {
        return IndependentExportRuntime.execute(
                configuration,
                pin,
                selected,
                nanos,
                instant,
                (context, journal, source) ->
                        (method, target, body, authenticated, maxBytes, timeout, deadline) ->
                                wire(
                                        journal,
                                        method,
                                        target,
                                        body,
                                        authenticated,
                                        maxBytes,
                                        timeout,
                                        deadline),
                ((VirtualClock) clock)::nanoTime,
                ((VirtualClock) clock)::advance,
                (path, requestSha, codeSha, attempt, create) ->
                        PersistentExportJournal.syntheticFixture(
                                path,
                                requestSha,
                                codeSha,
                                attempt,
                                create,
                                chains.computeIfAbsent(path, ignored -> new MemoryChain())));
    }

    private synchronized CurlExportTransport.Wire wire(
            final PersistentExportJournal journal,
            final String method,
            final URI target,
            final byte[] body,
            final boolean authenticated,
            final int maxBytes,
            final Duration timeout,
            final Instant deadline)
            throws Exception {
        require(!stopped && clock.instant().isBefore(deadline), "FIXTURE_STOP_OR_DEADLINE");
        authorityWasConsumedBeforeEveryWire &= consumed.getAsBoolean();
        require(consumed.getAsBoolean(), "FIXTURE_WIRE_BEFORE_AUTHORITY_CONSUME");
        if (lastResponseNanos != 0) {
            final var timing = (VirtualClock) clock;
            final long remaining =
                    Duration.ofSeconds(3).toNanos() - (timing.nanoTime() - lastResponseNanos);
            if (remaining > 0) {
                timing.advance(Duration.ofNanos(remaining));
            }
            minimumObservedGapNanos =
                    Math.min(minimumObservedGapNanos, timing.nanoTime() - lastResponseNanos);
        }
        calls++;
        routes.add(method + " " + target.getPath());
        require(
                calls <= 3 && timeout.compareTo(Duration.ofSeconds(30)) <= 0,
                "FIXTURE_FINITE_WIRE");
        if ("UNKNOWN".equals(mode)) {
            stopped = true;
            throw new java.io.IOException("FIXTURE_UNKNOWN_WIRE");
        }
        int status = "STOP429".equals(mode) ? 429 : 200;
        final byte[] response;
        if (status == 429) {
            stopped = true;
            response = "{}".getBytes(StandardCharsets.US_ASCII);
        } else if (!coletas && "POST".equals(method)) {
            require(
                    authenticated && body != null && target.getPath().endsWith("/6389/export"),
                    "FIXTURE_POST_SCOPE");
            response = bytes(fileResponse("enqueued"));
        } else if (!coletas && target.getPath().startsWith("/api/analytics/report_files/")) {
            response = bytes(fileResponse("PARTIAL".equals(mode) ? "generating" : "generated"));
        } else if (!coletas) {
            require(
                    !authenticated && target.getHost().equals("download.synthetic.invalid"),
                    "FIXTURE_DOWNLOAD_SCOPE");
            response = fretes.apiBook;
        } else if (target.getPath().endsWith("/info")) {
            final var info = request.objectNode();
            final var fields = info.putArray("fields");
            ColetasCommon31SyntheticFixture.FIELDS.forEach(field -> fields.add(field.source()));
            info.putArray("filters")
                    .add("picks.finish_date")
                    .add("picks.service_date")
                    .add("picks.request_date")
                    .add("picks.corporation_id")
                    .add("picks.status")
                    .add("scopes.by_updated_at");
            response = bytes(info);
        } else {
            require(
                    "GET".equals(method) && authenticated && target.getRawQuery() != null,
                    "FIXTURE_COLETAS_GET_QUERY");
            final var rows = request.arrayNode();
            if (++page == 1) {
                rows.add(coleta.source);
            } else if (!"NO_REFERENCE".equals(mode)) {
                coletasCallback();
            }
            response = bytes(rows);
        }
        require(response.length <= maxBytes, "FIXTURE_BODY_BOUND");
        final var responsePin = pin("wire-" + calls + ".bin", response);
        journal.record(
                "ROOT_WIRE_RECEIPT",
                request.objectNode()
                        .put("schema", "SYNTHETIC_ROOT_WIRE_RECEIPT_V1")
                        .put("operationId", UUID.randomUUID().toString())
                        .put("started", true)
                        .put("configSubmittedAt", observedAt())
                        .put("fixtureOnly", true)
                        .put("actualHTTP", 0)
                        .put("actualSource", 0)
                        .put("processesStarted", 0)
                        .set("bodyPin", node(responsePin)));
        lastResponseNanos = ((VirtualClock) clock).nanoTime();
        return new CurlExportTransport.Wire(status, responsePin, clock.instant());
    }

    private ObjectNode fileResponse(final String state) {
        final var value =
                request.objectNode()
                        .put("id", 7)
                        .put("report_id", 6389)
                        .put("status", state)
                        .put("show_url", "/api/analytics/report_files/7");
        value.putObject("filters")
                .putObject("freights")
                .put("service_at", "2025-03-03 - 2025-03-09");
        if ("generated".equals(state)) {
            value.put("download_url", "https://download.synthetic.invalid/book.xlsx");
        }
        return value;
    }

    private void coletasCallback() throws Exception {
        final byte[] retained = Files.readAllBytes(output.resolve("coletas-mapped.partial.jsonl"));
        // Ensure this policy binds the payload produced by the unchanged real mapper, not a second
        // source.
        final byte[] mapped =
                (new ColetaDataExportRecordMapper().map(1, coleta.source).payloadJson() + "\n")
                        .getBytes(StandardCharsets.UTF_8);
        require(java.util.Arrays.equals(retained, mapped), "FIXTURE_REAL_MAPPER_PAYLOAD");
        if ("DIFFERENCE".equals(mode)) {
            coleta.row.set(8, ColetasCommon31SyntheticFixture.Cell.number("3"));
            final var changed = pin("changed-ui.xlsx", coleta.nativeWorkbookBytes());
            callback.set("ui", descriptor(changed, "UI_EXPORT_XLSX", "UI_CHANGED_1"));
        }
        final var actualUi =
                ExportValidationSupport.Pin.from(callback.path("ui").required("dataPin"));
        final var evidence =
                coleta.evidence
                        .deepCopy()
                        .put("schema", "COLETAS_COMMON31_PRESENTATION_EVIDENCE_V1")
                        .put("sourceSha256", ExportValidationSupport.sha256(retained))
                        .put("workbookSha256", actualUi.sha256())
                        .put("catalogSha256", ColetasCommon31SyntheticFixture.CATALOG_SHA);
        final var evidencePin = pin("presentation-evidence.json", bytes(evidence));
        final var policy =
                coleta.policy
                        .deepCopy()
                        .put("schema", "COLETAS_COMMON31_POLICY_V1")
                        .put("attemptId", request.path("attemptId").asText())
                        .put("sourceSha256", ExportValidationSupport.sha256(retained))
                        .put("workbookSha256", actualUi.sha256());
        policy.set("independentPresentationEvidencePin", node(evidencePin));
        callback.set("policyPin", node(pin("policy.json", bytes(policy))));
        pin("reference-callback.json", bytes(callback));
    }

    private ObjectNode descriptor(
            final ExportValidationSupport.Pin data, final String route, final String fileId)
            throws Exception {
        final var observed =
                request.objectNode()
                        .put("event", "UI_EXPORT_CLICK")
                        .put("attemptId", request.path("attemptId").asText())
                        .put("requestId", "UI_REQUEST_1")
                        .put("fileId", fileId)
                        .put("requestedAt", observedAt())
                        .put("timePrecision", "SECOND")
                        .put("zone", "America/Sao_Paulo");
        observed.set("filters", request.required("filters"));
        final var evidence =
                observed.deepCopy()
                        .put("schema", "INDEPENDENT_EXPORT_ACQUISITION_V1")
                        .put("route", route)
                        .put("cycleId", request.path("attemptId").asText())
                        .put("requestSha256", requestPin.sha256())
                        .put("reportId", request.path("reportId").asInt())
                        .put("periodStart", request.path("periodStart").asText())
                        .put("periodEnd", request.path("periodEnd").asText());
        evidence.set("dataPin", node(data));
        evidence.set("observationPin", node(pin(fileId + "-observed.json", bytes(observed))));
        final var result = request.objectNode();
        result.set("dataPin", node(data));
        result.set("evidencePin", node(pin(fileId + "-evidence.json", bytes(evidence))));
        return result;
    }

    private String observedAt() {
        return clock.instant().atZone(ZoneId.of("America/Sao_Paulo")).toOffsetDateTime().toString();
    }

    private static byte[] bytes(final JsonNode node) throws Exception {
        return ExportValidationSupport.JSON.writeValueAsBytes(node);
    }

    private static ObjectNode node(final ExportValidationSupport.Pin pin) {
        return ExportValidationSupport.JSON.valueToTree(pin);
    }

    private ExportValidationSupport.Pin pin(final String name, final byte[] bytes)
            throws Exception {
        final var path = directory.resolve(name);
        Files.write(path, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        return new ExportValidationSupport.Pin(
                path, ExportValidationSupport.sha256(bytes), bytes.length);
    }
}
