package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.ExtrairLocalizacaoCargasDataExport;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageRecord;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class DataExportObservedFlowTest {
    @Test
    void negativeZeroVolumeKeepsItsWireLexemeAndTheExistingDomainRefusal() throws Exception {
        final String row =
                ObservedFlowTestSupport.localRow(1)
                        .replace("\"invoices_volumes\":1", "\"invoices_volumes\":-0");
        final var fixture =
                new ObservedFlowTestSupport(
                        DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[" + row + "]", "[]");
        final var staged = new ArrayList<LocalizacaoCargaStageRecord>();
        ExtrairLocalizacaoCargasDataExport.observedWithRawInput(
                        fixture.streamer(), batch -> staged.add(batch.recordAt(0)))
                .execute(
                        fixture.guard,
                        fixture.request,
                        new DataExportExtractionLimits(3, 100, 100),
                        CancellationToken.none());
        assertEquals(1, staged.size());
        assertTrue(staged.get(0).quarantined());
        assertEquals(
                "-0",
                ObservedFlowTestSupport.JSON
                        .readTree(staged.get(0).fieldPresenceJson())
                        .path("fields")
                        .path("invoices_volumes")
                        .path("rawWireLexeme")
                        .asText());
    }

    @Test
    void observedLocalCompositionSelectsRaw26AtItsExtractionSeam() throws Exception {
        final var fixture =
                new ObservedFlowTestSupport(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        50,
                        "[" + ObservedFlowTestSupport.localRow(1) + "]",
                        "[]");
        final var limits = new DataExportExtractionLimits(3, 100, 100);
        final var input =
                new DataExportRuntimeWorkload.Input(
                        fixture.start.partition(),
                        fixture.guard,
                        fixture.request,
                        limits,
                        fixture.bundle);
        final var captured = new ArrayList<LocalizacaoCargaStageRecord>();
        final var promotion =
                new br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao
                        .LocalizacaoCargaPromotionGateway() {
                    @Override
                    public void prepareCandidateSet(
                            final br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit
                                    permit) {
                        throw new AssertionError("PROMOTION_NOT_PART_OF_EXTRACTION_SEAM");
                    }

                    @Override
                    public br.com.esl.etl.v2.plataforma.persistencia.staging
                                    .StagingPublicationResult
                            applyReconcileAndPublish(
                                    final br.com.esl.etl.v2.plataforma.contrato
                                                    .ContractPromotionPermit
                                            permit,
                                    final br.com.esl.etl.v2.plataforma.qualidade
                                                    .DataQualityPromotionPermit
                                            quality) {
                        throw new AssertionError("PUBLICATION_NOT_PART_OF_EXTRACTION_SEAM");
                    }
                };
        final var workload =
                br.com.esl.etl.v2.bootstrap.LocalFiveVerticalRuntime.localizacao(
                        input,
                        batch -> captured.add(batch.recordAt(0)),
                        promotion,
                        new br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine(
                                request -> {
                                    throw new AssertionError("DQ_NOT_PART_OF_EXTRACTION_SEAM");
                                }),
                        new br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference(
                                "synthetic-v1", "f".repeat(64)));
        final var seam = DataExportRuntimeWorkload.class.getDeclaredField("extraction");
        seam.setAccessible(true);
        final var extraction = (DataExportRuntimeWorkload.Extraction) seam.get(workload);
        extraction.execute(
                fixture.streamer(),
                fixture.guard,
                fixture.request,
                limits,
                CancellationToken.none());
        assertEquals(1, captured.size());
        assertFalse(captured.get(0).quarantined());
        assertEquals(
                26, ObservedFlowTestSupport.JSON.readTree(captured.get(0).payloadJson()).size());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void allThreeObservedReleasesTraverseTheRealGateAndSerializer(final DataExportTemplate template)
            throws Exception {
        final var fixture =
                new ObservedFlowTestSupport(
                        template,
                        50,
                        "[" + ObservedFlowTestSupport.structuralRow(template) + "]",
                        "[]");
        final var rows = new ArrayList<Integer>();
        final var pipeline =
                new DataExportStagingPipeline<Integer>(
                        template,
                        fixture.streamer(),
                        (ordinal, node) -> node.size(),
                        (id, batch, values, at, token) -> values.forEach(rows::add));
        final var result =
                pipeline.execute(
                        fixture.guard,
                        fixture.request,
                        new DataExportExtractionLimits(3, 100, 100),
                        CancellationToken.none());
        assertEquals(
                List.of(
                        template == DataExportTemplate.MANIFESTOS
                                ? 91
                                : template == DataExportTemplate.COTACOES ? 37 : 26),
                rows);
        assertEquals(2, result.terminalPage());
        for (final String sent : fixture.client.requestBodies) {
            assertFalse(ObservedFlowTestSupport.JSON.readTree(sent).has("order_by"));
        }
    }

    @Test
    void realGatewayGateStreamerAndObservedMapperReceiveOriginalRecordAndAll26Fields()
            throws Exception {
        final String row =
                ObservedFlowTestSupport.localRow(1)
                        .replace("\"invoices_volumes\":1", "\"invoices_volumes\":0");
        final var fixture =
                new ObservedFlowTestSupport(
                        DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[" + row + "]", "[]");
        final var staged = new ArrayList<LocalizacaoCargaStageRecord>();
        final var result =
                ExtrairLocalizacaoCargasDataExport.observedWithRawInput(
                                fixture.streamer(),
                                batch -> {
                                    for (int index = 0; index < batch.size(); index++) {
                                        staged.add(batch.recordAt(index));
                                    }
                                })
                        .execute(
                                fixture.guard,
                                fixture.request,
                                new DataExportExtractionLimits(3, 100, 100),
                                CancellationToken.none());
        assertEquals(1, staged.size());
        assertFalse(staged.get(0).quarantined());
        final var presence =
                ObservedFlowTestSupport.JSON.readTree(staged.get(0).fieldPresenceJson());
        final var payload = ObservedFlowTestSupport.JSON.readTree(staged.get(0).payloadJson());
        assertEquals(26, payload.size());
        assertEquals(26, presence.path("fields").size());
        assertEquals(
                "0",
                presence.path("fields").path("invoices_volumes").path("rawWireLexeme").asText());
        assertEquals(
                "LOCALIZACAO_8656_26_PATHS_OBSERVED_20261024",
                presence.path("policy").path("matrix").asText());
        assertEquals("synthetic-invoice", payload.path("invoices_mapping").get(0).asText());
        assertEquals(2, result.terminalPage());
        assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
        assertEquals(2, fixture.client.requests.size());
        for (int index = 0; index < 2; index++) {
            assertEquals("GET", fixture.client.requests.get(index).method());
            final var sent =
                    ObservedFlowTestSupport.JSON.readTree(fixture.client.requestBodies.get(index));
            assertFalse(sent.has("order_by"));
            assertEquals("50", sent.path("per").asText());
            assertEquals(Integer.toString(index + 1), sent.path("page").asText());
            assertTrue(sent.path("search").has("freights"));
        }
    }

    @Test
    void rawPipelineKeepsBatchNumberAndLocalOrdinalsAcrossPageBoundaries() throws Exception {
        final var rows = new ArrayList<String>();
        for (int index = 0; index < 105; index++) {
            rows.add(ObservedFlowTestSupport.localRow(index % 17 + 1));
        }
        final String first = rows.stream().collect(Collectors.joining(",", "[", "]"));
        final var fixture =
                new ObservedFlowTestSupport(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        100,
                        first,
                        "[" + ObservedFlowTestSupport.localRow(18) + "]",
                        "[]");
        final var sizes = new ArrayList<Integer>();
        final var numbers = new ArrayList<Integer>();
        final var result =
                ExtrairLocalizacaoCargasDataExport.observedWithRawInput(
                                fixture.streamer(),
                                batch -> {
                                    sizes.add(batch.size());
                                    numbers.add(batch.batchNumber());
                                    for (int index = 0; index < batch.size(); index++) {
                                        assertEquals(
                                                index + 1, batch.recordAt(index).inputOrdinal());
                                    }
                                })
                        .execute(
                                fixture.guard,
                                fixture.request,
                                new DataExportExtractionLimits(4, 200, 100),
                                CancellationToken.none());
        assertEquals(List.of(100, 5, 1), sizes);
        assertEquals(List.of(1, 2, 3), numbers);
        assertEquals(106, result.recordsDelivered());
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 405, 415, 500})
    void stopsOnFirstNonSuccessWithoutFallbackOrRetry(final int status) {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        fixture.client.status = status;
        final var gateway =
                DataExportContractGate.enforce(fixture.bundle, fixture.request, fixture.guard)
                        .dataGateway();
        fixture.guard.validateMetadata(fixture.release.metadata());
        assertThrows(RuntimeException.class, () -> gateway.fetch(fixture.request));
        assertEquals(1, fixture.client.requests.size());
    }

    @Test
    void rejectsEffectiveTransportRetryAndFallbackBeforeSubmission() {
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.gateway(
                                        ObservedFlowTestSupport.properties(
                                                DataExportTransport.GET_WITH_QUERY, 1),
                                        DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY)
                                .fetch(fixture.request));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.gateway(
                                        ObservedFlowTestSupport.properties(
                                                DataExportTransport.GET_WITH_BODY, 2),
                                        DataExportTransportFallbackPolicy.PREFERRED_TRANSPORT_ONLY)
                                .fetch(fixture.request));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        fixture.gateway(
                                        ObservedFlowTestSupport.properties(
                                                DataExportTransport.GET_WITH_BODY, 1),
                                        DataExportTransportFallbackPolicy.ALLOW_COMPATIBLE_FALLBACK)
                                .fetch(fixture.request));
        assertEquals(0, fixture.client.requests.size());
    }

    @Test
    void rootEnvelopeUnknownPathAndMissingRawNeverFallBackToLegacyMapper() throws Exception {
        for (final String document :
                List.of(
                        "{\"data\":[" + ObservedFlowTestSupport.localRow(1) + "]}",
                        "["
                                + ObservedFlowTestSupport.localRow(1)
                                        .replace("\"type\":", "\"unknown_path\":")
                                + "]")) {
            final var fixture =
                    new ObservedFlowTestSupport(
                            DataExportTemplate.LOCALIZACAO_CARGAS, 50, document);
            final var gateway =
                    DataExportContractGate.enforce(fixture.bundle, fixture.request, fixture.guard)
                            .dataGateway();
            fixture.guard.validateMetadata(fixture.release.metadata());
            assertThrows(RuntimeException.class, () -> gateway.fetch(fixture.request));
            assertEquals(1, fixture.client.requests.size());
        }
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        fixture.guard.validateMetadata(fixture.release.metadata());
        final var adapter =
                new DataExportContractAdapter(
                        fixture.observation.observationLimits(),
                        fixture.observation.responsePathBoundary());
        final var tree =
                ObservedFlowTestSupport.JSON.readTree(
                        "[" + ObservedFlowTestSupport.localRow(1) + "]");
        final var streamer =
                new DataExportPageStreamer(
                        request -> {
                            final var observed =
                                    adapter.response(
                                            tree,
                                            DataExportResponseForm.ROOT_ARRAY,
                                            "/corporation_sequence_number");
                            fixture.guard.observeDataExportResponse(request.page(), observed);
                            return new DataExportPageResponse(List.of(tree.get(0)))
                                    .withObservation(
                                            observed,
                                            adapter.limits(),
                                            fixture.observation.responsePathBoundary());
                        },
                        DataExportExtractionAudit.noop(),
                        java.time.Clock.systemUTC());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ExtrairLocalizacaoCargasDataExport.observedWithRawInput(
                                        streamer,
                                        batch -> {
                                            throw new AssertionError("MISSING_RAW_STAGED");
                                        })
                                .execute(
                                        fixture.guard,
                                        fixture.request,
                                        new DataExportExtractionLimits(2, 100, 100),
                                        CancellationToken.none()));
        assertEquals(0, fixture.client.requests.size());
    }
}
