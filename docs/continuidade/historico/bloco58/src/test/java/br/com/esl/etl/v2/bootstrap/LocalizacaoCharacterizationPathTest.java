package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.mapping.MapperCharacterization;
import br.com.esl.etl.v2.contratos.mapping.MapperProjection.Entity;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.ExtrairLocalizacaoCargasDataExport;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractObservationLimits;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponsePathBoundary;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractAdapter;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportResponseForm;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LocalizacaoCharacterizationPathTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2036-03-20T12:00:00Z");

    @Test
    void distinguishesRawLexemeMapperNodeMapperAndActualPipelineContract() throws Exception {
        final var reports = JSON.createArrayNode();
        for (final String value : List.of("0", "1", "\"0\"", "null")) {
            final boolean number = value.equals("0") || value.equals("1");
            final String envelope = envelope(value);
            final var raw = compare(Entity.LOCALIZACAO_CARGAS, envelope, "NONE");
            final var node =
                    compare(
                            Entity.LOCALIZACAO_CARGAS_PIPELINE,
                            envelope,
                            number ? "UNVERIFIED_NUMERIC_WIRE_LEXEME" : "NONE");
            assertTrue(raw.matches(), raw::toString);
            assertTrue(node.matches(), node::toString);
            final var pipeline = new Pipeline(envelope);
            if (number) {
                // B55's synthetic contract requires strings here, before invoking the mapper.
                assertThrows(ContractDriftException.class, pipeline::run);
                assertEquals(0, pipeline.staged.get());
            } else {
                pipeline.run();
                assertEquals(1, pipeline.staged.get());
            }
            final var report = reports.addObject();
            report.put("scenario", "SYNTH_" + reports.size());
            report.set("rawMapper", raw.sanitized());
            report.set("pipelineMapper", node.sanitized());
            report.put("pipelineContract", number ? "REJECTED_BEFORE_STAGING" : "LOCAL_TRAVERSAL");
        }
        final Path directory = Path.of("target", "bloco57-complemento", "reports");
        Files.createDirectories(directory);
        Files.writeString(
                directory.resolve("localizacao-paths-" + UUID.randomUUID() + ".json"),
                JSON.writerWithDefaultPrettyPrinter().writeValueAsString(reports) + "\n",
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE_NEW);
    }

    @Test
    void numericNodeQuarantineCannotPassAnExpectationOfAValidRow() throws Exception {
        final var result = compare(Entity.LOCALIZACAO_CARGAS_PIPELINE, envelope("0"), "NONE");
        assertFalse(result.matches());
        assertEquals("DIVERGED", result.sanitized().path("status").textValue());
        assertEquals(
                "/quarantine",
                result.sanitized().path("differences").get(0).path("path").textValue());
    }

    private static MapperCharacterization.Report compare(
            final Entity entity, final String envelope, final String reason) throws Exception {
        final JsonNode expected = JSON.readTree("[{\"/quarantine\":\"" + reason + "\"}]");
        return MapperCharacterization.compare(
                entity,
                1,
                page -> new ByteArrayInputStream(envelope.getBytes(StandardCharsets.UTF_8)),
                page -> expected);
    }

    private static String envelope(final String value) {
        return "{\"data\":[{\"corporation_sequence_number\":1,"
                + "\"service_at\":\"2036-03-20T12:00:00Z\",\"invoices_volumes\":"
                + value
                + "}]}";
    }

    private static final class Pipeline {
        private final ContractRunGuard guard;
        private final DataExportPageStreamer streamer;
        private final AtomicInteger staged = new AtomicInteger();

        private Pipeline(final String document) throws Exception {
            final var template = DataExportTemplate.LOCALIZACAO_CARGAS;
            final var release = RuntimeFiveVerticalContract.release(template);
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
            final var first = CharacterizationParserAccess.parse(document);
            final var empty = CharacterizationParserAccess.parse("{\"data\":[]}");
            streamer =
                    new DataExportPageStreamer(
                            request -> {
                                final var envelope = request.page() == 1 ? first : empty;
                                guard.observeDataExportResponse(
                                        request.page(),
                                        adapter.response(
                                                envelope,
                                                DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                                                "/corporation_sequence_number"));
                                return new DataExportPageResponse(
                                        request.page() == 1
                                                ? List.of(envelope.path("data").get(0))
                                                : List.of());
                            },
                            DataExportExtractionAudit.noop(),
                            Clock.fixed(NOW, ZoneOffset.UTC));
        }

        private void run() {
            final var template = DataExportTemplate.LOCALIZACAO_CARGAS;
            final var request =
                    new DataExportPageRequest(
                            template,
                            new BusinessDateRange(
                                    LocalDate.of(2036, 3, 20), LocalDate.of(2036, 3, 20)),
                            Optional.empty(),
                            1,
                            1,
                            template.defaultOrderBy());
            final var result =
                    new ExtrairLocalizacaoCargasDataExport(
                                    streamer,
                                    new LocalizacaoCargaDataExportRecordMapper(),
                                    batch -> {
                                        assertEquals(1, batch.size());
                                        assertFalse(batch.recordAt(0).quarantined());
                                        staged.addAndGet(batch.size());
                                    })
                            .execute(
                                    guard,
                                    request,
                                    new DataExportExtractionLimits(2, 1, 1),
                                    CancellationToken.none());
            assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
        }
    }
}
