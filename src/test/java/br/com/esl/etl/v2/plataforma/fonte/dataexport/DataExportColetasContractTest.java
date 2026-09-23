package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaFreshnessOrigin;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageDisposition;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.configuracao.DataExportProperties;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Local HTTP and in-memory staging. Values are authored fixtures, never supplier records. */
class DataExportColetasContractTest {
    private static final DataExportTemplate TEMPLATE = DataExportTemplate.COLETAS;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-03-20T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void realHttpParserGateMapperAndStagingPreserveExpandedPhysicalRows() throws Exception {
        final String page = resource("6908-page.synthetic.json");
        try (var fixture =
                new Pipeline(List.of(resource("6908-info.sanitized.json"), page, "[]"), 2)) {
            fixture.bundle.templateInfoGateway().fetchInfo(TEMPLATE);
            final var result = fixture.extract(2, 5);
            assertEquals(5, fixture.staged.size());
            assertEquals(2, result.pagesFetched());
            assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
            final var original = DataExportStrictJsonParser.readTree(page);
            for (int i = 0; i < 5; i++) {
                final var row = fixture.staged.get(i);
                assertEquals(ColetaStageDisposition.VALID, row.disposition());
                assertEquals(
                        original.get(i), DataExportStrictJsonParser.readTree(row.payloadJson()));
                assertEquals(
                        "ABSENT",
                        DataExportStrictJsonParser.readTree(row.fieldPresenceJson())
                                .path("status_updated_at")
                                .asText());
                assertEquals(ColetaFreshnessOrigin.FINISH_DATE, row.freshnessOrigin());
                assertEquals(Instant.parse("2036-03-20T03:00:00Z"), row.freshnessAtUtc());
                assertEquals("finished", row.status().code());
            }
            assertEquals(fixture.staged.get(0).sourceKey(), fixture.staged.get(2).sourceKey());
            assertNotEquals(
                    fixture.staged.get(0).relationCandidatesJson(),
                    fixture.staged.get(2).relationCandidatesJson());
            assertEquals(
                    "NULL",
                    DataExportStrictJsonParser.readTree(
                                    fixture.staged.get(4).relationCandidatesJson())
                            .path("pck_mik_mft_sequence_code")
                            .path("presence")
                            .asText());
            final var requests = fixture.requests.get(5, TimeUnit.SECONDS);
            assertEquals(3, requests.size());
            final String query = URLDecoder.decode(requests.get(1), StandardCharsets.UTF_8);
            assertTrue(query.contains("search[picks][request_date]=2036-03-20 - 2036-03-20"));
            assertTrue(query.contains("order_by=sequence_code asc"));
            assertTrue(query.contains("per=2"));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"data\":[{\"id\":1}]}",
                "[{\"id\":\"1\"}]",
                "[{\"id\":null}]",
                "[{}]",
                "[{\"id\":1,\"status\":2}]",
                "[{\"id\":1,\"sequence_code\":\"1\"}]",
                "[{\"id\":1,\"pck_mik_mft_sequence_code\":\"1\"}]",
                "[{\"id\":1,\"cancellation_reason\":false}]",
                "[{\"id\":1,\"finish_date\":123}]",
                "[{\"id\":1,\"unexpected\":\"synthetic\"}]",
                "[{\"id\":1,\"invoices_weight\":{}}]",
                "[{\"id\":1,\"invoices_weight\":[]}]",
                "[{\"id\":1},{\"id\":2},{\"id\":3}]",
                "[{\"id\":1,\"id\":2}]",
                "[] []",
                "{\"errors\":[\"synthetic\"]}"
            })
    void refusesDriftAndInvalidPagesBeforeAnyStaging(final String body) throws Exception {
        try (var fixture = new Pipeline(List.of(resource("6908-info.sanitized.json"), body), 2)) {
            fixture.bundle.templateInfoGateway().fetchInfo(TEMPLATE);
            assertThrows(RuntimeException.class, () -> fixture.extract(2, 5));
            assertTrue(fixture.staged.isEmpty());
            assertThrows(ContractDriftException.class, fixture.guard::complete);
        }
    }

    @Test
    void acceptsNullableAndAbsentNonKeyFieldsWithoutInventingFreshness() throws Exception {
        try (var fixture =
                new Pipeline(
                        List.of(
                                resource("6908-info.sanitized.json"),
                                "[{\"id\":1},{\"id\":2,\"sequence_code\":null,\"finish_date\":null}]",
                                "[]"),
                        2)) {
            fixture.bundle.templateInfoGateway().fetchInfo(TEMPLATE);
            fixture.extract(2, 2);
            assertEquals(2, fixture.staged.size());
            assertEquals(
                    ColetaFreshnessOrigin.UNAVAILABLE, fixture.staged.get(0).freshnessOrigin());
            assertEquals(
                    "ABSENT",
                    DataExportStrictJsonParser.readTree(fixture.staged.get(0).fieldPresenceJson())
                            .path("sequence_code")
                            .asText());
            assertEquals(
                    "NULL",
                    DataExportStrictJsonParser.readTree(fixture.staged.get(1).fieldPresenceJson())
                            .path("sequence_code")
                            .asText());
        }
    }

    @Test
    void recordLimitAndMissingTerminalPageCannotProduceSuccess() throws Exception {
        for (final boolean rowLimit : List.of(true, false)) {
            try (var fixture =
                    new Pipeline(
                            List.of(
                                    resource("6908-info.sanitized.json"),
                                    resource("6908-page.synthetic.json")),
                            2)) {
                fixture.bundle.templateInfoGateway().fetchInfo(TEMPLATE);
                assertThrows(RuntimeException.class, () -> fixture.extract(1, rowLimit ? 4 : 5));
                assertEquals(rowLimit ? 0 : 5, fixture.staged.size());
                assertThrows(ContractDriftException.class, fixture.guard::complete);
            }
        }
    }

    @Test
    void metadataIsExactAndDoesNotConfuseFilterNamesWithRequestPaths() throws Exception {
        for (final String info :
                List.of(
                        resource("6908-info.sanitized.json")
                                .replace("by_updated_at", "scopes.by_updated_at"),
                        resource("6908-info.sanitized.json").replace("select", "string"),
                        resource("6908-info.sanitized.json")
                                .replace("pck_ctr_name", "new_column"))) {
            try (var fixture = new Pipeline(List.of(info), 2)) {
                assertThrows(
                        ContractDriftException.class,
                        () -> fixture.bundle.templateInfoGateway().fetchInfo(TEMPLATE));
                assertTrue(fixture.staged.isEmpty());
                assertThrows(ContractDriftException.class, fixture.guard::complete);
            }
        }
    }

    @Test
    void releaseSelectionPreservesHistoryAndRejectsAnotherSource() {
        final var current = DataExportColetasContractCatalog.release();
        final var old = DataExportFirstWaveContractCatalog.release(TEMPLATE);
        final var limits = ContractObservationLimits.runtimeDefaults();
        final var boundary =
                ContractResponsePathBoundary.forRuntime(
                        current, ContractTestSupport.policy(current));
        final var fingerprint = new ImmutableFingerprint("synthetic-b62", "b".repeat(64));
        assertNotEquals(old.contractFingerprint(), current.contractFingerprint());
        assertEquals(
                DataExportResponseForm.ROOT_ARRAY,
                DataExportContractObservationConfiguration.forRelease(
                                TEMPLATE, current, limits, boundary, fingerprint)
                        .expectedResponseForm());
        assertEquals(
                DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                DataExportContractObservationConfiguration.forRelease(
                                TEMPLATE,
                                old,
                                limits,
                                ContractResponsePathBoundary.forRuntime(
                                        old, ContractTestSupport.policy(old)),
                                fingerprint)
                        .expectedResponseForm());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DataExportContractObservationConfiguration.forRelease(
                                DataExportTemplate.FRETES, current, limits, boundary, fingerprint));
        final var objectRelease =
                SourceContractRelease.create(
                        current.sourceKind(),
                        current.documentReference(),
                        "synthetic-object",
                        current.metadata(),
                        new ContractResponse(
                                "$",
                                ContractResponse.Cardinality.OBJECT,
                                ContractResponse.ObservationState.POPULATED,
                                "/id",
                                current.response().fields()));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        DataExportContractObservationConfiguration.forRelease(
                                TEMPLATE, objectRelease, limits, boundary, fingerprint));
    }

    private static String resource(final String name) throws IOException {
        try (var stream =
                DataExportColetasContractTest.class.getResourceAsStream(
                        "/contracts/bloco62/" + name)) {
            return new String(
                    Objects.requireNonNull(stream).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class Pipeline implements AutoCloseable {
        private final ServerSocket server;
        private final ExecutorService executor = Executors.newSingleThreadExecutor();
        private final Future<List<String>> requests;
        private final ContractRunGuard guard;
        private final DataExportHttpGatewayBundle bundle;
        private final List<ColetaStageRecord> staged = new ArrayList<>();
        private final int per;

        private Pipeline(final List<String> bodies, final int per) throws IOException {
            this.per = per;
            server = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
            server.setSoTimeout(5000);
            requests =
                    executor.submit(
                            () -> {
                                final var observed = new ArrayList<String>();
                                for (final String body : bodies) {
                                    try (var socket = server.accept()) {
                                        socket.setSoTimeout(5000);
                                        final var reader =
                                                new BufferedReader(
                                                        new InputStreamReader(
                                                                socket.getInputStream(),
                                                                StandardCharsets.UTF_8));
                                        observed.add(reader.readLine());
                                        String line;
                                        int headerCount = 0;
                                        while ((line = reader.readLine()) != null
                                                && !line.isEmpty()) {
                                            assertTrue(++headerCount <= 64);
                                        }
                                        final var out = socket.getOutputStream();
                                        final byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                                        out.write(
                                                ("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: "
                                                                + bytes.length
                                                                + "\r\nConnection: close\r\n\r\n")
                                                        .getBytes(StandardCharsets.US_ASCII));
                                        out.write(bytes);
                                        out.flush();
                                    }
                                }
                                return observed;
                            });
            final var release = DataExportColetasContractCatalog.release();
            final var policy = ContractTestSupport.policy(release);
            final var fingerprint = new ImmutableFingerprint("synthetic-b62", "b".repeat(64));
            final var binding =
                    ContractExecutionBinding.create(
                            UUID.randomUUID(), release, policy, fingerprint);
            guard =
                    new ContractRunGuard(
                            binding,
                            release,
                            policy,
                            ContractTestSupport.controlPlaneStart(binding),
                            ignored -> {});
            final var observation =
                    DataExportContractObservationConfiguration.forRelease(
                            TEMPLATE,
                            release,
                            guard.observationLimits(),
                            guard.responsePathBoundary(),
                            fingerprint);
            final var properties =
                    new DataExportProperties(
                            URI.create("http://127.0.0.1:" + server.getLocalPort()),
                            "synthetic-token",
                            ZoneId.of("America/Sao_Paulo"),
                            Duration.ofSeconds(5),
                            DataExportTransport.GET_WITH_QUERY,
                            new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                            65536);
            bundle =
                    DataExportContractGate.enforce(
                            DataExportHttpGatewayBundle.contractBound(
                                    new HttpDataExportGateway(
                                            properties,
                                            DataExportHttpAttemptObserver.noop(),
                                            observation),
                                    new HttpDataExportTemplateInfoGateway(properties),
                                    observation),
                            TEMPLATE,
                            guard);
        }

        private DataExportExtractionResult extract(final int pages, final int rows) {
            return new ExtrairColetasDataExport(
                            new DataExportPageStreamer(
                                    bundle.dataGateway(), DataExportExtractionAudit.noop(), CLOCK),
                            new ColetaDataExportRecordMapper(),
                            batch -> {
                                for (int i = 0; i < batch.size(); i++) {
                                    staged.add(batch.recordAt(i));
                                }
                            })
                    .execute(
                            guard,
                            new DataExportPageRequest(
                                    TEMPLATE,
                                    new BusinessDateRange(
                                            LocalDate.of(2036, 3, 20), LocalDate.of(2036, 3, 20)),
                                    Optional.empty(),
                                    1,
                                    per,
                                    TEMPLATE.defaultOrderBy()),
                            new DataExportExtractionLimits(pages, rows, 100),
                            new CancellationSignal());
        }

        @Override
        public void close() throws IOException {
            try {
                server.close();
            } finally {
                executor.shutdownNow();
                try {
                    assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
                } catch (final InterruptedException failure) {
                    Thread.currentThread().interrupt();
                    throw new IOException("SYNTHETIC_SERVER_INTERRUPTED", failure);
                }
            }
        }
    }
}
