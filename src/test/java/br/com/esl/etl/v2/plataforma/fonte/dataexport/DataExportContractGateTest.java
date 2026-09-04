package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataExportContractGateTest {

    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-30T12:00:00Z"), ZoneOffset.UTC);
    private static final ContractObservationLimits OBSERVATION_LIMITS =
            ContractObservationLimits.runtimeDefaults();
    private static final DataExportContractAdapter ADAPTER =
            DataExportContractAdapter.forSyntheticFixtures(OBSERVATION_LIMITS);

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void metadataAdapterExcludesLabelsTransportAndValuesFromTheFingerprint() throws Exception {
        final DataExportTemplateInfo first = info("Synthetic label A");
        final DataExportTemplateInfo second = info("Synthetic label B");
        final ContractMetadata firstMetadata = DataExportContractAdapter.metadata(first);
        final ContractMetadata secondMetadata = DataExportContractAdapter.metadata(second);
        final ContractResponse response =
                observation("{\"data\":[{\"id\":\"a\",\"status\":\"x\"}]}");

        final SourceContractRelease firstRelease = release(firstMetadata, response);
        final SourceContractRelease secondRelease = release(secondMetadata, response);

        assertEquals(firstMetadata, secondMetadata);
        assertEquals(firstRelease.metadataFingerprint(), secondRelease.metadataFingerprint());
        assertEquals(firstRelease.contractFingerprint(), secondRelease.contractFingerprint());
        assertFalse(firstMetadata.toString().contains("Synthetic label"));
        assertEquals(
                "dataexport-coletas",
                DataExportContractAdapter.documentReference(DataExportTemplate.COLETAS));
        assertNotEquals(
                DataExportContractAdapter.documentReference(DataExportTemplate.COLETAS),
                DataExportContractAdapter.documentReference(DataExportTemplate.FRETES));
    }

    @Test
    void responseAdapterPreservesAllFourRootFormsAndNestedPathsWithoutValues() throws Exception {
        final List<String> documents =
                List.of(
                        "[{\"id\":\"secret-a\",\"nested\":{\"code\":1}}]",
                        "{\"id\":\"secret-b\",\"nested\":{\"code\":1}}",
                        "{\"data\":[{\"id\":\"secret-c\",\"nested\":{\"code\":1}}]}",
                        "{\"data\":{\"id\":\"secret-d\",\"nested\":{\"code\":1}}}");
        final List<DataExportResponseForm> forms =
                List.of(
                        DataExportResponseForm.ROOT_ARRAY,
                        DataExportResponseForm.ROOT_OBJECT,
                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                        DataExportResponseForm.ENVELOPE_DATA_OBJECT);

        for (int index = 0; index < documents.size(); index++) {
            final JsonNode raw = objectMapper.readTree(documents.get(index));
            final ContractResponse response = ADAPTER.response(raw, forms.get(index), "/id");
            assertEquals(
                    forms.get(index).isArray(),
                    response.rootCardinality() == ContractResponse.Cardinality.ARRAY);
            assertEquals(index >= 2 ? "/data" : "$", response.recordRoot());
            assertTrue(response.find("/nested/code").isPresent());
            assertFalse(response.toString().contains("secret-"));
        }

        final JsonNode raw = objectMapper.readTree(documents.get(0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ADAPTER.response(raw, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));

        final DataExportTemplateInfo info = info("Synthetic label");
        final SourceContractRelease release =
                release(
                        DataExportContractAdapter.metadata(info),
                        observation("{\"data\":[{\"id\":\"a\",\"status\":\"ready\"}]}"));
        final ContractResponsePathBoundary runtimeBoundary =
                gateFixture(release).guard().responsePathBoundary();
        for (final DataExportResponseForm objectForm :
                List.of(
                        DataExportResponseForm.ROOT_OBJECT,
                        DataExportResponseForm.ENVELOPE_DATA_OBJECT)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new DataExportContractObservationConfiguration(
                                    DataExportTemplate.COLETAS,
                                    objectForm,
                                    "/id",
                                    OBSERVATION_LIMITS,
                                    runtimeBoundary,
                                    new ImmutableFingerprint("runtime-v1", "d".repeat(64))));
        }
    }

    @Test
    void strictParserRejectsDuplicateKeysAndTrailingJsonDocuments() throws Exception {
        assertEquals(
                "a", DataExportStrictJsonParser.readTree("{\"id\":\"a\"}").path("id").asText());
        assertThrows(
                JsonProcessingException.class,
                () -> DataExportStrictJsonParser.readTree("{\"id\":1,\"id\":2}"));
        assertThrows(
                JsonProcessingException.class,
                () -> DataExportStrictJsonParser.readTree("{\"id\":1} {\"id\":2}"));
        assertThrows(
                JsonProcessingException.class,
                () -> DataExportStrictJsonParser.readTree("{\"data\":[{\"id\":1},{\"id\":2}]}", 5));
        assertTrue(
                DataExportStrictJsonParser.readTree("{\"data\":[{\"id\":1},{\"id\":2}]}", 6)
                        .isObject());
        assertTrue(DataExportStrictJsonParser.readTree(nestedEnvelope(32)).isObject());
        assertThrows(
                JsonProcessingException.class,
                () -> DataExportStrictJsonParser.readTree(nestedEnvelope(33)));
        assertTrue(
                DataExportStrictJsonParser.readTree("{\"" + "n".repeat(256) + "\":1}").isObject());
        assertThrows(
                JsonProcessingException.class,
                () -> DataExportStrictJsonParser.readTree("{\"" + "n".repeat(257) + "\":1}"));
    }

    @Test
    void rejectsUnboundOrMismatchedConfigurationBeforeEitherDelegateCanOpenIo() throws Exception {
        final DataExportTemplateInfo info = info("Synthetic label");
        final SourceContractRelease release =
                release(
                        DataExportContractAdapter.metadata(info),
                        observation("{\"data\":[{\"id\":\"a\",\"status\":\"ready\"}]}"));
        final AtomicInteger dataCalls = new AtomicInteger();
        final AtomicInteger infoCalls = new AtomicInteger();

        final GateFixture unbound = gateFixture(release);
        final ContractDriftException missingBinding =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                DataExportContractGate.enforce(
                                        new DataExportHttpGatewayBundle(
                                                request -> {
                                                    dataCalls.incrementAndGet();
                                                    throw new AssertionError();
                                                },
                                                template -> {
                                                    infoCalls.incrementAndGet();
                                                    return info;
                                                }),
                                        DataExportTemplate.COLETAS,
                                        unbound.guard()));
        assertEquals(
                ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, missingBinding.reason());

        final GateFixture mismatched = gateFixture(release);
        final DataExportContractObservationConfiguration wrongConfiguration =
                new DataExportContractObservationConfiguration(
                        DataExportTemplate.COLETAS,
                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                        "/id",
                        OBSERVATION_LIMITS,
                        mismatched.guard().responsePathBoundary(),
                        new ImmutableFingerprint("runtime-v1", "e".repeat(64)));
        final ContractDriftException wrongBinding =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                DataExportContractGate.enforce(
                                        DataExportHttpGatewayBundle.contractBound(
                                                request -> {
                                                    dataCalls.incrementAndGet();
                                                    throw new AssertionError();
                                                },
                                                template -> {
                                                    infoCalls.incrementAndGet();
                                                    return info;
                                                },
                                                wrongConfiguration),
                                        DataExportTemplate.COLETAS,
                                        mismatched.guard()));
        assertEquals(
                ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, wrongBinding.reason());
        assertEquals(0, dataCalls.get());
        assertEquals(0, infoCalls.get());
    }

    @Test
    void gatewayGateObservesTheTerminalPageEvenThoughTheStreamerDoesNotDeliverIt()
            throws Exception {
        final DataExportTemplateInfo info = info("Synthetic label");
        final JsonNode populatedRaw =
                objectMapper.readTree("{\"data\":[{\"id\":\"a\",\"status\":\"ready\"}]}");
        final JsonNode emptyRaw = objectMapper.readTree("{\"data\":[]}");
        final SourceContractRelease release =
                release(
                        DataExportContractAdapter.metadata(info),
                        ADAPTER.response(
                                populatedRaw, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));
        final GateFixture fixture = gateFixture(release);
        final DataExportPageResponse populated =
                page(populatedRaw, fixture.guard().responsePathBoundary());
        final DataExportPageResponse empty = page(emptyRaw, fixture.guard().responsePathBoundary());
        final AtomicInteger calls = new AtomicInteger();
        final DataExportHttpGatewayBundle rawGateways =
                boundBundle(
                        fixture,
                        request -> calls.getAndIncrement() == 0 ? populated : empty,
                        template -> info);
        final DataExportHttpGatewayBundle secured =
                DataExportContractGate.enforce(
                        rawGateways, DataExportTemplate.COLETAS, fixture.guard());

        secured.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        final AtomicInteger delivered = new AtomicInteger();
        new DataExportPageStreamer(secured.dataGateway(), DataExportExtractionAudit.noop(), CLOCK)
                .stream(
                        fixture.guard().executionContext(),
                        request(DataExportTemplate.COLETAS),
                        new DataExportExtractionLimits(2, 10, 100),
                        page -> delivered.incrementAndGet());
        final ContractPromotionPermit permit = fixture.guard().complete();

        assertEquals(1, delivered.get());
        assertEquals(2, fixture.guard().responsePages());
        assertEquals(fixture.binding().executionId(), permit.executionId());
        assertThrows(
                ContractDriftException.class,
                () -> secured.dataGateway().fetch(request(DataExportTemplate.COLETAS).withPage(3)));
        assertEquals(2, calls.get());
    }

    @Test
    void missingObservationOrWrongTemplatePermanentlyClosesTheGate() {
        final DataExportTemplateInfo info = info("Synthetic label");
        final ContractResponse response;
        try {
            response = observation("{\"data\":[{\"id\":\"a\",\"status\":\"ready\"}]}");
        } catch (final Exception exception) {
            throw new AssertionError(exception);
        }
        final SourceContractRelease release =
                release(DataExportContractAdapter.metadata(info), response);

        final GateFixture missing = gateFixture(release);
        final DataExportHttpGatewayBundle missingObservation =
                DataExportContractGate.enforce(
                        boundBundle(
                                missing,
                                request -> new DataExportPageResponse(List.of()),
                                template -> info),
                        DataExportTemplate.COLETAS,
                        missing.guard());
        missingObservation.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        final ContractDriftException absent =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                missingObservation
                                        .dataGateway()
                                        .fetch(request(DataExportTemplate.COLETAS)));
        assertEquals(ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED, absent.reason());
        assertThrows(ContractDriftException.class, missing.guard()::complete);

        final GateFixture wrong = gateFixture(release);
        final DataExportHttpGatewayBundle wrongTemplate =
                DataExportContractGate.enforce(
                        boundBundle(
                                wrong,
                                request -> {
                                    throw new AssertionError("O delegate não deveria ser chamado.");
                                },
                                template -> info),
                        DataExportTemplate.COLETAS,
                        wrong.guard());
        final ContractDriftException mismatch =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                wrongTemplate
                                        .dataGateway()
                                        .fetch(request(DataExportTemplate.FRETES)));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, mismatch.reason());

        final GateFixture skipped = gateFixture(release);
        final AtomicInteger skippedDelegateCalls = new AtomicInteger();
        final DataExportHttpGatewayBundle skippedFirstPage =
                DataExportContractGate.enforce(
                        boundBundle(
                                skipped,
                                request -> {
                                    skippedDelegateCalls.incrementAndGet();
                                    return new DataExportPageResponse(List.of());
                                },
                                template -> info),
                        DataExportTemplate.COLETAS,
                        skipped.guard());
        skippedFirstPage.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        final ContractDriftException skippedPage =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                skippedFirstPage
                                        .dataGateway()
                                        .fetch(request(DataExportTemplate.COLETAS).withPage(2)));
        assertEquals(
                ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
                skippedPage.reason());
        assertEquals(0, skippedDelegateCalls.get());

        final GateFixture nullFixture = gateFixture(release);
        final DataExportHttpGatewayBundle nullResponse =
                DataExportContractGate.enforce(
                        boundBundle(nullFixture, request -> null, template -> info),
                        DataExportTemplate.COLETAS,
                        nullFixture.guard());
        nullResponse.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        assertThrows(
                NullPointerException.class,
                () -> nullResponse.dataGateway().fetch(request(DataExportTemplate.COLETAS)));
        assertThrows(ContractDriftException.class, nullFixture.guard()::complete);
    }

    @Test
    void keyDriftIsClassifiedAndAnObservationBoundaryMismatchFailsClosed() throws Exception {
        final DataExportTemplateInfo info = info("Synthetic label");
        final JsonNode baselineRaw =
                objectMapper.readTree("{\"data\":[{\"id\":\"a\",\"status\":\"ready\"}]}");
        final SourceContractRelease release =
                release(
                        DataExportContractAdapter.metadata(info),
                        ADAPTER.response(
                                baselineRaw, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));

        for (final String document :
                List.of(
                        "{\"data\":[{\"status\":\"ready\"}]}",
                        "{\"data\":[{\"id\":null,\"status\":\"ready\"}]}",
                        "{\"data\":[{\"id\":{},\"status\":\"ready\"}]}",
                        "{\"data\":[{\"id\":[],\"status\":\"ready\"}]}")) {
            final GateFixture fixture = gateFixture(release);
            final DataExportPageResponse drifted =
                    page(objectMapper.readTree(document), fixture.guard().responsePathBoundary());
            final DataExportHttpGatewayBundle secured =
                    DataExportContractGate.enforce(
                            boundBundle(fixture, request -> drifted, template -> info),
                            DataExportTemplate.COLETAS,
                            fixture.guard());
            secured.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);

            final ContractDriftException blocked =
                    assertThrows(
                            ContractDriftException.class,
                            () -> secured.dataGateway().fetch(request(DataExportTemplate.COLETAS)));
            assertEquals(ContractDriftException.Reason.BREAKING_CHANGE, blocked.reason());
            assertFalse(blocked.getMessage().contains("status"));
        }

        final GateFixture bound = gateFixture(release);
        final ContractCompatibilityPolicy otherPolicy =
                ContractCompatibilityPolicy.create(
                        "different-policy-v1", release.contractFingerprint(), List.of());
        final ContractResponsePathBoundary otherBoundary =
                ContractResponsePathBoundary.forRuntime(release, otherPolicy);
        final DataExportPageResponse wrongBoundary = page(baselineRaw, otherBoundary);
        final DataExportHttpGatewayBundle secured =
                DataExportContractGate.enforce(
                        boundBundle(bound, request -> wrongBoundary, template -> info),
                        DataExportTemplate.COLETAS,
                        bound.guard());
        secured.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);

        final ContractDriftException mismatch =
                assertThrows(
                        ContractDriftException.class,
                        () -> secured.dataGateway().fetch(request(DataExportTemplate.COLETAS)));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, mismatch.reason());
    }

    @Test
    void skippedPageAndPostTerminalAuditFailureCannotProduceAPermit() throws Exception {
        final DataExportTemplateInfo info = info("Synthetic label");
        final JsonNode populatedRaw =
                objectMapper.readTree("{\"data\":[{\"id\":\"a\",\"status\":\"ready\"}]}");
        final JsonNode emptyRaw = objectMapper.readTree("{\"data\":[]}");
        final SourceContractRelease release =
                release(
                        DataExportContractAdapter.metadata(info),
                        ADAPTER.response(
                                populatedRaw, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id"));

        final GateFixture skipped = gateFixture(release);
        final DataExportPageResponse skippedPopulated =
                page(populatedRaw, skipped.guard().responsePathBoundary());
        final DataExportPageResponse skippedEmpty =
                page(emptyRaw, skipped.guard().responsePathBoundary());
        final DataExportHttpGatewayBundle skippedGate =
                DataExportContractGate.enforce(
                        boundBundle(
                                skipped,
                                request -> request.page() == 1 ? skippedPopulated : skippedEmpty,
                                template -> info),
                        DataExportTemplate.COLETAS,
                        skipped.guard());
        skippedGate.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        skippedGate.dataGateway().fetch(request(DataExportTemplate.COLETAS));
        final ContractDriftException sequenceFailure =
                assertThrows(
                        ContractDriftException.class,
                        () ->
                                skippedGate
                                        .dataGateway()
                                        .fetch(request(DataExportTemplate.COLETAS).withPage(3)));
        assertEquals(
                ContractDriftException.Reason.TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
                sequenceFailure.reason());
        assertThrows(ContractDriftException.class, skipped.guard()::complete);

        final GateFixture auditFailure = gateFixture(release);
        final DataExportPageResponse auditPopulated =
                page(populatedRaw, auditFailure.guard().responsePathBoundary());
        final DataExportPageResponse auditEmpty =
                page(emptyRaw, auditFailure.guard().responsePathBoundary());
        final AtomicInteger calls = new AtomicInteger();
        final DataExportHttpGatewayBundle auditGate =
                DataExportContractGate.enforce(
                        boundBundle(
                                auditFailure,
                                request ->
                                        calls.getAndIncrement() == 0 ? auditPopulated : auditEmpty,
                                template -> info),
                        DataExportTemplate.COLETAS,
                        auditFailure.guard());
        auditGate.templateInfoGateway().fetchInfo(DataExportTemplate.COLETAS);
        final AtomicInteger prematurePermits = new AtomicInteger();
        final AtomicInteger prematureBlocks = new AtomicInteger();
        final DataExportExtractionAudit failingAudit =
                new DataExportExtractionAudit() {
                    @Override
                    public void executionStarted(final ExecutionStarted event) {}

                    @Override
                    public void pageRead(final PageRead event) {}

                    @Override
                    public void executionCompleted(final DataExportExtractionResult result) {
                        try {
                            auditFailure.guard().complete();
                            prematurePermits.incrementAndGet();
                        } catch (final ContractDriftException expected) {
                            prematureBlocks.incrementAndGet();
                        }
                        throw new IllegalStateException("synthetic-audit-failure");
                    }

                    @Override
                    public void executionFailed(final ExecutionFailed event) {}
                };
        assertThrows(
                IllegalStateException.class,
                () ->
                        new DataExportPageStreamer(auditGate.dataGateway(), failingAudit, CLOCK)
                                .stream(
                                        auditFailure.guard().executionContext(),
                                        request(DataExportTemplate.COLETAS),
                                        new DataExportExtractionLimits(2, 10, 100),
                                        page -> {}));
        assertEquals(0, prematurePermits.get());
        assertEquals(1, prematureBlocks.get());
        assertThrows(ContractDriftException.class, auditFailure.guard()::complete);
    }

    private DataExportPageResponse page(
            final JsonNode raw, final ContractResponsePathBoundary pathBoundary) {
        final DataExportResponseForm form = DataExportResponseForm.from(raw);
        final DataExportPageResponse normalized = new DataExportResponseNormalizer().normalize(raw);
        final DataExportContractAdapter runtimeAdapter =
                new DataExportContractAdapter(OBSERVATION_LIMITS, pathBoundary);
        return DataExportPageResponse.observed(
                normalized.records(),
                runtimeAdapter.response(raw, form, "/id"),
                OBSERVATION_LIMITS,
                pathBoundary);
    }

    private ContractResponse observation(final String document) throws Exception {
        final JsonNode raw = objectMapper.readTree(document);
        return ADAPTER.response(raw, DataExportResponseForm.from(raw), "/id");
    }

    private static SourceContractRelease release(
            final ContractMetadata metadata, final ContractResponse response) {
        return SourceContractRelease.create(
                ContractSourceKind.DATA_EXPORT,
                "dataexport-coletas",
                "synthetic-v1",
                metadata,
                response);
    }

    private static DataExportTemplateInfo info(final String label) {
        return new DataExportTemplateInfo(
                DataExportTemplate.COLETAS,
                200,
                Optional.empty(),
                true,
                List.of(
                        new DataExportMetadataField(
                                "id", Optional.of("string"), Optional.of(label)),
                        new DataExportMetadataField(
                                "status", Optional.of("string"), Optional.of(label))),
                List.of(
                        new DataExportMetadataField(
                                "request_date", Optional.of("date"), Optional.of(label))));
    }

    private static GateFixture gateFixture(final SourceContractRelease release) {
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "policy-v1", release.contractFingerprint(), List.of());
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(),
                        release,
                        policy,
                        new ImmutableFingerprint("runtime-v1", "d".repeat(64)));
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        alert -> {});
        return new GateFixture(
                binding,
                guard,
                new DataExportContractObservationConfiguration(
                        DataExportTemplate.COLETAS,
                        DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                        release.response().keyPath(),
                        OBSERVATION_LIMITS,
                        guard.responsePathBoundary(),
                        binding.runtimeConfigurationFingerprint()));
    }

    private static DataExportHttpGatewayBundle boundBundle(
            final GateFixture fixture,
            final DataExportGateway dataGateway,
            final DataExportTemplateInfoGateway infoGateway) {
        return DataExportHttpGatewayBundle.contractBound(
                dataGateway, infoGateway, fixture.observationConfiguration());
    }

    private static DataExportPageRequest request(final DataExportTemplate template) {
        return DataExportPageRequest.forTemplate(
                template,
                new BusinessDateRange(LocalDate.of(2026, 8, 30), LocalDate.of(2026, 8, 30)),
                new SourceDateTimeRange(
                        Instant.parse("2026-08-30T03:00:00Z"),
                        Instant.parse("2026-08-31T02:59:59Z")),
                1);
    }

    private static String nestedEnvelope(final int nestedContainers) {
        final StringBuilder document = new StringBuilder("{\"data\":[{\"id\":\"a\",\"root\":");
        for (int depth = 0; depth < nestedContainers; depth++) {
            document.append("{\"n\":");
        }
        document.append('1');
        document.append("}".repeat(nestedContainers));
        return document.append("}]}").toString();
    }

    private record GateFixture(
            ContractExecutionBinding binding,
            ContractRunGuard guard,
            DataExportContractObservationConfiguration observationConfiguration) {}
}
