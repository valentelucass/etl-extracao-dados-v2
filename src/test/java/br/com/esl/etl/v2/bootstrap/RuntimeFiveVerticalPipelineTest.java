package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.ExtrairCotacoesDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.ExtrairFretesDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.ExtrairLocalizacaoCargasDataExport;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ExtrairManifestosDataExport;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class RuntimeFiveVerticalPipelineTest {
    private static final Instant NOW = Instant.parse("2032-02-29T12:00:00Z");

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES", "MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void traversesTypedMappersWithCrossPageDuplicates(final DataExportTemplate template) {
        final var fixture = new Fixture(template, 2);
        final var result = fixture.run();
        assertEquals(16, result.recordsDelivered());
        assertEquals(2, fixture.batches);
        assertEquals(16, fixture.records);
        assertEquals(3, fixture.pages);
        assertNotNull(fixture.guard.complete());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES", "MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void sinkFailureInvalidatesTraversalAndReleasesTheBatch(final DataExportTemplate template) {
        final var fixture = new Fixture(template, 3);
        fixture.failBatch = true;
        assertThrows(IllegalStateException.class, fixture::run);
        assertEquals(1, fixture.pages);
        assertEquals(0, fixture.inFlight);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
        fixture.assertReleased();
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES", "MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void cancellationStopsBeforeNextPage(final DataExportTemplate template) {
        final var fixture = new Fixture(template, 3);
        fixture.cancelBatch = true;
        assertThrows(RuntimeException.class, fixture::run);
        assertEquals(1, fixture.pages);
        assertEquals(0, fixture.inFlight);
        assertThrows(ContractDriftException.class, fixture.guard::complete);
        fixture.assertReleased();
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    void rejectsUncontractedUpdateFilter(final DataExportTemplate template) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportPageRequest(
                                template,
                                new BusinessDateRange(
                                        LocalDate.of(2032, 2, 29), LocalDate.of(2032, 2, 29)),
                                Optional.of(new SourceDateTimeRange(NOW, NOW.plusSeconds(1))),
                                1,
                                2,
                                template.defaultOrderBy()));
        assertThrows(
                IllegalArgumentException.class,
                () -> DataExportFirstWaveContractCatalog.release(template));
        assertEquals(
                "/" + template.paginationEntityField(),
                RuntimeFiveVerticalContract.release(template).response().keyPath());
    }

    @ParameterizedTest
    @EnumSource(
            value = DataExportTemplate.class,
            names = {"COLETAS", "FRETES", "MANIFESTOS", "COTACOES", "LOCALIZACAO_CARGAS"})
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void measuresThreeScalesThroughRealTypedPipelines(final DataExportTemplate template)
            throws Exception {
        if (Runtime.getRuntime().maxMemory() > 512L * 1024 * 1024) {
            throw new IllegalStateException("MEASUREMENT_REQUIRES_512_MIB_HEAP_CAP");
        }
        final var receipts = JsonNodeFactory.instance.arrayNode();
        for (final int scale : List.of(16, 256, 4096)) {
            final var fixture = new Fixture(template, scale);
            final long start = System.nanoTime();
            final var result = fixture.run();
            final long duration = System.nanoTime() - start;
            fixture.assertReleased();
            assertEquals(scale * 8L, result.recordsDelivered());
            assertEquals(scale, fixture.batches);
            assertEquals(scale * 8L, fixture.records);
            assertEquals(0, fixture.inFlight);
            assertEquals(1, fixture.peak);
            assertNotNull(fixture.guard.complete());
            receipts.addObject()
                    .put("template", template.name())
                    .put("pages", scale)
                    .put("records", fixture.records)
                    .put("batches", fixture.batches)
                    .put("quarantines", 0)
                    .put("peakBatch", fixture.peak)
                    .put("finalInFlight", fixture.inFlight)
                    .put("durationNanos", duration)
                    .put("maximumHeapBytes", Runtime.getRuntime().maxMemory())
                    .put("releasedWeakBatchSample", true)
                    .put(
                            "heapUsedDiagnostic",
                            Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory())
                    .put("layer", "TEST_CLASSPATH_NO_HTTP_NO_JDBC")
                    .put("heapPlateauProven", false)
                    .put("productionScaleProven", false);
        }
        final var directory =
                java.nio.file.Path.of("target/bloco55/measurement", UUID.randomUUID().toString());
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(
                directory.resolve(template.name() + ".json"), receipts.toPrettyString());
    }

    @Test
    void retainedBatchMutantIsDetected() {
        final var fixture = new Fixture(DataExportTemplate.MANIFESTOS, 2);
        fixture.retainBatch = true;
        fixture.run();
        final var failure = assertThrows(IllegalStateException.class, fixture::assertReleased);
        assertEquals("EXECUTION_WIDE_BATCH_RETENTION_DETECTED", failure.getMessage());
        assertEquals(2, fixture.retained.size());
        fixture.retained.clear();
    }

    static ObjectNode row(final DataExportTemplate template, final int index) {
        final var row = JsonNodeFactory.instance.objectNode();
        return switch (template) {
            case COLETAS ->
                    row.put("id", index + 1)
                            .put("sequence_code", index + 1)
                            .putNull("pck_mik_mft_sequence_code")
                            .put("updated_at", NOW.toString())
                            .put("request_date", "2032-02-29")
                            .put("status", "done");
            case FRETES ->
                    row.put("id", index + 1)
                            .put("corporation_sequence_number", index + 1)
                            .put("finished_at", "2032-02-29")
                            .putNull("fit_dpn_performance_finished_at")
                            .putNull("fit_p_m_pck_sequence_code")
                            .put("updated_at", NOW.toString())
                            .put("servico_em", "2032-02-29")
                            .put("status", "finished");
            case MANIFESTOS ->
                    row.put("sequence_code", index + 1)
                            .put("created_at", NOW.toString())
                            .put("status", "closed")
                            .put("mdfe_status", "authorized")
                            .put("km", "0")
                            .put("mft_pfs_pck_sequence_code", index + 100)
                            .put("mft_mfs_key", "1".repeat(43) + index)
                            .put("mft_mfs_number", index + 1);
            case COTACOES ->
                    row.put("sequence_code", index + 1)
                            .put("requested_at", "2032-02-29")
                            .put("qoe_qes_total", "10.00")
                            .put("qoe_qes_ony_sae_code", "SP")
                            .put("qoe_qes_diy_sae_code", "RJ")
                            .put("qoe_uer_name", "SYNTHETIC");
            case LOCALIZACAO_CARGAS ->
                    row.put("corporation_sequence_number", index + 1)
                            .put("service_at", "2032-02-29")
                            .put("invoices_volumes", "0")
                            .put("fit_fln_status", "finished");
            default -> throw new IllegalArgumentException("FIVE_VERTICAL_FIXTURE_ONLY");
        };
    }

    private static final class Fixture {
        private final DataExportTemplate template;
        private final ContractRunGuard guard;
        private final DataExportPageStreamer streamer;
        private final int scale;
        private final CancellationSignal cancellation = new CancellationSignal();
        private final List<Object> retained = new ArrayList<>();
        private final java.util.ArrayDeque<java.lang.ref.WeakReference<Object>> batchReferences =
                new java.util.ArrayDeque<>();
        private int pages, batches, records, inFlight, peak;
        private boolean failBatch, cancelBatch, retainBatch;

        private Fixture(final DataExportTemplate template, final int scale) {
            this.template = template;
            this.scale = scale;
            final var release =
                    template.laboratoryBackfillOnly()
                            ? RuntimeFiveVerticalContract.release(template)
                            : RuntimeLaboratoryContract.release(template);
            final var policy = ContractTestSupport.policy(release);
            final var binding =
                    ContractExecutionBinding.create(
                            UUID.randomUUID(),
                            release,
                            policy,
                            new ImmutableFingerprint("synthetic-v1", "d".repeat(64)));
            guard =
                    new ContractRunGuard(
                            binding,
                            release,
                            policy,
                            ContractTestSupport.controlPlaneStart(binding),
                            ignored -> {});
            guard.bindCompletenessStatus(template.completenessStatus());
            guard.validateMetadata(release.metadata());
            final var adapter =
                    new DataExportContractAdapter(
                            ContractObservationLimits.runtimeDefaults(),
                            ContractResponsePathBoundary.forRuntime(release, policy));
            streamer =
                    new DataExportPageStreamer(
                            request -> {
                                assertEquals(++pages, request.page());
                                final var envelope = JsonNodeFactory.instance.objectNode();
                                final var array = envelope.putArray("data");
                                if (request.page() <= scale) {
                                    for (int i = 0; i < 8; i++) {
                                        array.add(row(template, i));
                                    }
                                }
                                guard.observeDataExportResponse(
                                        request.page(),
                                        adapter.response(
                                                envelope,
                                                DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                                                "/" + template.paginationEntityField()));
                                final var rows =
                                        new ArrayList<com.fasterxml.jackson.databind.JsonNode>();
                                array.forEach(rows::add);
                                return new DataExportPageResponse(rows);
                            },
                            DataExportExtractionAudit.noop(),
                            Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private void accept(final Object batch, final int number, final int size) {
            if (batchReferences.size() == 64) {
                batchReferences.removeFirst();
            }
            batchReferences.addLast(new java.lang.ref.WeakReference<>(batch));
            assertEquals(++batches, number);
            assertEquals(8, size);
            peak = Math.max(peak, ++inFlight);
            try {
                if (retainBatch) {
                    retained.add(batch);
                }
                if (failBatch) {
                    throw new IllegalStateException("SYNTHETIC_SINK_FAILURE");
                }
                records += size;
                if (cancelBatch) {
                    cancellation.cancel();
                }
            } finally {
                inFlight--;
            }
        }

        private void assertReleased() {
            // Independent weak reachability sample: the sink cannot self-report release.
            for (int attempt = 0; attempt < 5; attempt++) {
                System.gc();
                if (batchReferences.stream().noneMatch(reference -> reference.get() != null)) {
                    return;
                }
            }
            throw new IllegalStateException("EXECUTION_WIDE_BATCH_RETENTION_DETECTED");
        }

        private DataExportExtractionResult run() {
            final var request =
                    new DataExportPageRequest(
                            template,
                            new BusinessDateRange(
                                    LocalDate.of(2032, 2, 29), LocalDate.of(2032, 2, 29)),
                            Optional.empty(),
                            1,
                            8,
                            template.defaultOrderBy());
            final var limits = new DataExportExtractionLimits(scale + 1, scale * 8L, 8);
            return switch (template) {
                case COLETAS ->
                        new ExtrairColetasDataExport(
                                        streamer,
                                        new ColetaDataExportRecordMapper(),
                                        b -> {
                                            for (int i = 0; i < b.size(); i++) {
                                                assertEquals(
                                                        "VALID",
                                                        b.recordAt(i).disposition().name());
                                            }
                                            accept(b, b.batchNumber(), b.size());
                                        })
                                .execute(guard, request, limits, cancellation);
                case FRETES ->
                        new ExtrairFretesDataExport(
                                        streamer,
                                        new FreteDataExportRecordMapper(),
                                        b -> {
                                            for (int i = 0; i < b.size(); i++) {
                                                assertFalse(b.recordAt(i).quarantined());
                                            }
                                            accept(b, b.batchNumber(), b.size());
                                        })
                                .execute(guard, request, limits, cancellation);
                case MANIFESTOS ->
                        new ExtrairManifestosDataExport(
                                        streamer,
                                        new ManifestoDataExportRecordMapper(),
                                        b -> {
                                            for (int i = 0; i < b.size(); i++) {
                                                assertEquals(
                                                        "VALID",
                                                        b.recordAt(i).disposition().name());
                                            }
                                            accept(b, b.batchNumber(), b.size());
                                        })
                                .execute(guard, request, limits, cancellation);
                case COTACOES ->
                        new ExtrairCotacoesDataExport(
                                        streamer,
                                        new CotacaoDataExportRecordMapper(),
                                        b -> {
                                            for (int i = 0; i < b.size(); i++) {
                                                assertFalse(b.recordAt(i).quarantined());
                                            }
                                            accept(b, b.batchNumber(), b.size());
                                        })
                                .execute(guard, request, limits, cancellation);
                case LOCALIZACAO_CARGAS ->
                        new ExtrairLocalizacaoCargasDataExport(
                                        streamer,
                                        new LocalizacaoCargaDataExportRecordMapper(),
                                        b -> {
                                            for (int i = 0; i < b.size(); i++) {
                                                assertEquals(
                                                        "VALID",
                                                        b.recordAt(i).disposition().name());
                                            }
                                            accept(b, b.batchNumber(), b.size());
                                        })
                                .execute(guard, request, limits, cancellation);
                default -> throw new IllegalArgumentException("FIVE_VERTICAL_FIXTURE_ONLY");
            };
        }
    }
}
