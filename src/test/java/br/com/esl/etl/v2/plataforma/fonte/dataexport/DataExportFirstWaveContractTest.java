package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.contrato.ContractClassification;
import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractMetadata;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.FirstWaveContractManifestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceCompletenessStatus;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.contrato.SourceDataEffect;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataExportFirstWaveContractTest {

    private static final int MAXIMUM_FIXTURE_BYTES = 64 * 1024;
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final ContractObservationLimits LIMITS =
            ContractObservationLimits.runtimeDefaults();
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-04-13T15:00:00Z"), ZoneOffset.UTC);
    private static final ImmutableFingerprint RUNTIME_FINGERPRINT =
            new ImmutableFingerprint("first-wave-runtime-v1", "d".repeat(64));
    private static final ObjectMapper MAPPER =
            new ObjectMapper(
                    JsonFactory.builder()
                            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                            .build());
    private static final DataExportResponseNormalizer NORMALIZER =
            new DataExportResponseNormalizer();

    @Test
    void templateDefinitionsMatchTheVersionedContractWithoutConflatingOrderAndIdentity()
            throws Exception {
        final DataExportTemplate coletas = DataExportTemplate.COLETAS;
        final DataExportTemplate fretes = DataExportTemplate.FRETES;

        assertEquals(6908, coletas.templateId());
        assertEquals("request_date", coletas.metadataBusinessDateFilterName());
        assertEquals(
                "search[picks][request_date]", coletas.businessDateFilter().asQueryParameterName());
        assertEquals("sequence_code asc", coletas.defaultOrderBy().get(0));
        assertEquals("id", coletas.paginationEntityField());

        assertEquals(6389, fretes.templateId());
        assertEquals("freights.service_at", fretes.metadataBusinessDateFilterName());
        assertEquals(
                "search[freights][service_at]", fretes.businessDateFilter().asQueryParameterName());
        assertEquals("corporation_sequence_number asc", fretes.defaultOrderBy().get(0));
        assertEquals("id", fretes.paginationEntityField());
        assertNotEquals(fretes.defaultOrderBy().get(0), fretes.paginationEntityField());

        for (final DataExportTemplate template : DataExportTemplate.values()) {
            assertEquals(DataExportTransport.GET_WITH_QUERY, template.approvedTransport());
            assertEquals(
                    DataExportResponseForm.ENVELOPE_DATA_ARRAY, template.promotableResponseForm());
            assertEquals(ContractClassification.TRANSITIONAL, template.contractClassification());
            assertEquals(
                    SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF,
                    template.completenessStatus());
            final JsonNode manifest = manifest(template);
            FirstWaveContractManifestSupport.assertSemantics(
                    manifest, template.contractSemanticsFingerprint());
            FirstWaveContractManifestSupport.assertAspect(
                    manifest,
                    "temporalTranslation",
                    ContractClassification.BUSINESS_DECISION_PENDING);
            FirstWaveContractManifestSupport.assertAspect(
                    manifest, "completeness", ContractClassification.ABSENT);
            FirstWaveContractManifestSupport.assertFailClosedCapabilities(manifest);
        }
    }

    @Test
    void canonicalSyntheticFixturesProduceTheVersionedReleases() throws Exception {
        for (final DataExportTemplate template : DataExportTemplate.values()) {
            final DataExportTemplateInfo info = templateInfo(template);
            final JsonNode populated =
                    read(
                            "/contracts/first-wave/"
                                    + template.templateId()
                                    + "-per-3-page-1.synthetic.json");
            final SourceContractRelease release =
                    DataExportFirstWaveContractCatalog.release(template);
            final var observedResponse =
                    DataExportContractAdapter.forSyntheticFixtures(LIMITS)
                            .response(populated, DataExportResponseForm.ENVELOPE_DATA_ARRAY, "/id");

            FirstWaveContractManifestSupport.assertRelease(manifest(template), release);
            assertEquals(release.metadata(), DataExportContractAdapter.metadata(info));
            assertEquals(release.response(), observedResponse);
            assertEquals(ContractMetadata.ElementKind.DATA_FILTER, filter(info, template).kind());
        }
    }

    @Test
    void twoBoundedPerValuesPreserveTheSameSyntheticEntitiesAndLocalTerminalOnly()
            throws Exception {
        for (final DataExportTemplate template : DataExportTemplate.values()) {
            final Set<String> perTwo = entitySet(template, 2, 2);
            final Set<String> perThree = entitySet(template, 3, 1);

            assertEquals(Set.of(expectedIds(template)), perTwo);
            assertEquals(perTwo, perThree);
            assertEquals(3, perTwo.size());
            final ContractDriftException blockedSweep =
                    org.junit.jupiter.api.Assertions.assertThrows(
                            ContractDriftException.class,
                            () ->
                                    entitySet(
                                            template,
                                            3,
                                            1,
                                            SourceDataEffect.SWEEP_OR_DEACTIVATION));
            assertEquals(
                    ContractDriftException.Reason.SOURCE_STATE_NOT_PROMOTABLE,
                    blockedSweep.reason());
            assertEquals(
                    SourceCompletenessStatus.BLOCKED_NO_COMPLETENESS_PROOF,
                    DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED.completenessStatus());
            assertFalse(
                    DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED
                            .provesCoverageOrSnapshot());
            assertTrue(template.completenessStatus().permits(SourceDataEffect.SHADOW_UPSERT));
            assertFalse(
                    template.completenessStatus().permits(SourceDataEffect.SWEEP_OR_DEACTIVATION));
        }
    }

    @Test
    void temporalWireFormattingIsExplicitWhileBoundaryTranslationRemainsPending() throws Exception {
        final BusinessDateRange business =
                new BusinessDateRange(LocalDate.of(2026, 4, 12), LocalDate.of(2026, 4, 13));
        final SourceDateTimeRange updated =
                new SourceDateTimeRange(
                        Instant.parse("2026-04-13T03:00:00Z"),
                        Instant.parse("2026-04-14T02:59:59Z"));

        assertEquals("2026-04-12 - 2026-04-13", business.formatForSource(SOURCE_ZONE));
        assertEquals(
                "2026-04-13 00:00:00 - 2026-04-13 23:59:59", updated.formatForSource(SOURCE_ZONE));
        assertEquals(
                ContractClassification.BUSINESS_DECISION_PENDING.name(),
                manifest(DataExportTemplate.FRETES)
                        .at("/aspects/temporalTranslation/classification")
                        .asText());
        assertFalse(
                manifest(DataExportTemplate.FRETES)
                        .at("/aspects/temporalTranslation/contract")
                        .asText()
                        .contains("SUBTRACT"));
    }

    @Test
    void contractGateRejectsUnversionedOrderAndPerBeforeCallingTheSource() throws Exception {
        for (final DataExportTemplate template : DataExportTemplate.values()) {
            assertRequestRejected(
                    template, template.defaultPageSize() + 1, template.defaultOrderBy());
            assertRequestRejected(template, 2, List.of("unversioned_order asc"));
        }
    }

    private static ContractMetadata.Element filter(
            final DataExportTemplateInfo info, final DataExportTemplate template) {
        return DataExportContractAdapter.metadata(info)
                .find(
                        ContractMetadata.ElementKind.DATA_FILTER,
                        template.metadataBusinessDateFilterName())
                .orElseThrow();
    }

    private static Set<String> entitySet(
            final DataExportTemplate template, final int pageSize, final int populatedPages)
            throws IOException {
        return entitySet(template, pageSize, populatedPages, SourceDataEffect.SHADOW_UPSERT);
    }

    private static Set<String> entitySet(
            final DataExportTemplate template,
            final int pageSize,
            final int populatedPages,
            final SourceDataEffect effect)
            throws IOException {
        final SourceContractRelease release = DataExportFirstWaveContractCatalog.release(template);
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "first-wave-policy-v1", release.contractFingerprint(), List.of());
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, RUNTIME_FINGERPRINT, LIMITS);
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final ContractResponsePathBoundary boundary = guard.responsePathBoundary();
        final Queue<DataExportPageResponse> pages = new ArrayDeque<>();
        for (int page = 1; page <= populatedPages; page++) {
            pages.add(
                    observedPage(
                            read(
                                    "/contracts/first-wave/"
                                            + template.templateId()
                                            + "-per-"
                                            + pageSize
                                            + "-page-"
                                            + page
                                            + ".synthetic.json"),
                            boundary));
        }
        pages.add(
                observedPage(
                        read(
                                "/contracts/first-wave/"
                                        + template.templateId()
                                        + "-per-"
                                        + pageSize
                                        + "-terminal.synthetic.json"),
                        boundary));
        final DataExportContractObservationConfiguration observation =
                new DataExportContractObservationConfiguration(
                        template,
                        template.promotableResponseForm(),
                        release.response().keyPath(),
                        LIMITS,
                        boundary,
                        RUNTIME_FINGERPRINT);
        final DataExportTemplateInfo info = templateInfo(template);
        final DataExportHttpGatewayBundle secured =
                DataExportContractGate.enforce(
                        DataExportHttpGatewayBundle.contractBound(
                                ignored -> pages.remove(), ignored -> info, observation),
                        template,
                        guard);
        secured.templateInfoGateway().fetchInfo(template);
        final Set<String> ids = new HashSet<>();
        final DataExportExtractionResult result =
                new DataExportPageStreamer(
                                secured.dataGateway(), DataExportExtractionAudit.noop(), CLOCK)
                        .stream(
                                guard.executionContext(),
                                request(template, pageSize, 1),
                                new DataExportExtractionLimits(
                                        populatedPages + 1, 100, template.defaultPageSize()),
                                readPage ->
                                        readPage.response()
                                                .records()
                                                .forEach(
                                                        record ->
                                                                ids.add(
                                                                        record.path("id")
                                                                                .asText())));
        guard.complete(effect);
        assertEquals(populatedPages + 1, result.pagesFetched());
        assertEquals(
                DataExportTraversalVerification.LOCAL_TERMINAL_UNVERIFIED,
                result.traversalVerification());
        assertTrue(pages.isEmpty());
        return Set.copyOf(ids);
    }

    private static DataExportPageResponse observedPage(
            final JsonNode raw, final ContractResponsePathBoundary boundary) {
        final DataExportPageResponse normalized = NORMALIZER.normalize(raw);
        final DataExportContractAdapter adapter = new DataExportContractAdapter(LIMITS, boundary);
        return DataExportPageResponse.observed(
                normalized.records(),
                adapter.response(raw, DataExportResponseForm.from(raw), "/id"),
                LIMITS,
                boundary);
    }

    private static DataExportTemplateInfo templateInfo(final DataExportTemplate template)
            throws IOException {
        final DataExportTemplateInfoParser.ParsedTemplateInfo parsed =
                new DataExportTemplateInfoParser()
                        .parse(
                                read(
                                        "/contracts/"
                                                + template.templateId()
                                                + "-info.synthetic.json"));
        return new DataExportTemplateInfo(
                template, 200, Optional.empty(), true, parsed.fields(), parsed.filters());
    }

    private static DataExportPageRequest request(
            final DataExportTemplate template, final int pageSize, final int page) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 13)),
                Optional.empty(),
                page,
                pageSize,
                template.defaultOrderBy());
    }

    private static void assertRequestRejected(
            final DataExportTemplate template, final int pageSize, final List<String> orderBy)
            throws IOException {
        final SourceContractRelease release = DataExportFirstWaveContractCatalog.release(template);
        final ContractCompatibilityPolicy policy =
                ContractCompatibilityPolicy.create(
                        "first-wave-policy-v1", release.contractFingerprint(), List.of());
        final ContractExecutionBinding binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(), release, policy, RUNTIME_FINGERPRINT, LIMITS);
        final ContractRunGuard guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final DataExportContractObservationConfiguration observation =
                new DataExportContractObservationConfiguration(
                        template,
                        template.promotableResponseForm(),
                        release.response().keyPath(),
                        LIMITS,
                        guard.responsePathBoundary(),
                        RUNTIME_FINGERPRINT);
        final AtomicInteger sourceCalls = new AtomicInteger();
        final DataExportTemplateInfo info = templateInfo(template);
        final DataExportHttpGatewayBundle secured =
                DataExportContractGate.enforce(
                        DataExportHttpGatewayBundle.contractBound(
                                ignored -> {
                                    sourceCalls.incrementAndGet();
                                    throw new AssertionError("A fonte não deveria ser chamada.");
                                },
                                ignored -> info,
                                observation),
                        template,
                        guard);
        secured.templateInfoGateway().fetchInfo(template);
        final DataExportPageRequest rejected =
                new DataExportPageRequest(
                        template,
                        new BusinessDateRange(LocalDate.of(2026, 4, 13), LocalDate.of(2026, 4, 13)),
                        Optional.empty(),
                        1,
                        pageSize,
                        orderBy);

        final ContractDriftException failure =
                org.junit.jupiter.api.Assertions.assertThrows(
                        ContractDriftException.class, () -> secured.dataGateway().fetch(rejected));
        assertEquals(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH, failure.reason());
        assertEquals(0, sourceCalls.get());
    }

    private static String[] expectedIds(final DataExportTemplate template) {
        return template == DataExportTemplate.COLETAS
                ? new String[] {"910001", "910002", "910003"}
                : new String[] {"920001", "920002", "920003"};
    }

    private static JsonNode manifest(final DataExportTemplate template) throws IOException {
        return FirstWaveContractManifestSupport.contract("dataexport-" + template.templateId());
    }

    private static JsonNode read(final String resource) throws IOException {
        try (InputStream input =
                DataExportFirstWaveContractTest.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("Fixture sintética V2-025a ausente.");
            }
            final byte[] bytes = input.readNBytes(MAXIMUM_FIXTURE_BYTES + 1);
            if (bytes.length > MAXIMUM_FIXTURE_BYTES) {
                throw new IOException("Fixture sintética V2-025a excede o limite.");
            }
            return MAPPER.readTree(bytes);
        }
    }
}
