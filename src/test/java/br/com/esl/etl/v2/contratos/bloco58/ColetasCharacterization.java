package br.com.esl.etl.v2.contratos.bloco58;

import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Pages;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Report;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionBinding;
import br.com.esl.etl.v2.plataforma.contrato.ContractResponse;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractTestSupport;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.Bloco58DataExportAccess;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.CharacterizationParserAccess;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportContractObservationConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionAudit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiFunction;

/** Actual mapper and two explicitly named contract paths, each reading the original envelope. */
public final class ColetasCharacterization {
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2036-03-20T12:00:00Z"), ZoneOffset.UTC);

    private ColetasCharacterization() {}

    public enum Fault {
        NONE,
        CANCEL_BEFORE,
        CANCEL_AFTER_FETCH,
        CANCEL_AFTER_STAGE,
        STAGE_FIRST,
        STAGE_SECOND
    }

    public static Report mapper(
            final Pages pages,
            final int pageCount,
            final BiFunction<Integer, Integer, JsonNode> expected) {
        final var report = new Report();
        if (pageCount < 1 || pageCount > LocalCharacterization.MAXIMUM_PAGES) {
            report.refuse(Layer.INPUT, "PAGE_LIMIT");
            return report;
        }
        try {
            for (int page = 1; page <= pageCount; page++) {
                report.enter(Layer.INPUT);
                final String document;
                try (var input = pages.open(page)) {
                    document = LocalCharacterization.read(input);
                }
                report.enter(Layer.PARSER);
                final var envelope = CharacterizationParserAccess.parse(document);
                if (!envelope.isObject()
                        || !envelope.path("data").isArray()
                        || envelope.has("errors")
                        || envelope.has("error")) {
                    throw new IllegalArgumentException("INVALID_ENVELOPE");
                }
                final var rows = envelope.path("data");
                if (rows.size() > LocalCharacterization.MAXIMUM_ROWS - report.mappedRows()) {
                    report.refuse(Layer.INPUT, "ROW_LIMIT");
                    return report;
                }
                report.page();
                for (int row = 0; row < rows.size(); row++) {
                    report.enter(Layer.MAPPER);
                    final var mapped = new ColetaDataExportRecordMapper().map(1, rows.get(row));
                    report.mapped();
                    report.compare(expected.apply(page, row), ColetasProjection.observe(mapped));
                }
            }
        } catch (final IOException failure) {
            report.refuse(Layer.INPUT, "READ_FAILURE");
        } catch (final RuntimeException failure) {
            report.failed(failure);
        }
        return report;
    }

    public static Report pipeline(
            final Pages pages,
            final SourceContractRelease release,
            final int per,
            final int maximumPages,
            final int maximumRows,
            final Fault fault,
            final BiFunction<Integer, Integer, JsonNode> expected) {
        final var report = new Report();
        if (maximumPages < 1 || maximumPages > 100 || maximumRows < 1 || maximumRows > 1_000) {
            report.refuse(Layer.INPUT, "CASE_LIMIT");
            return report;
        }
        final var policy = ContractTestSupport.policy(release);
        final var fingerprint = new ImmutableFingerprint("bloco58-synthetic-v1", "b".repeat(64));
        final var binding =
                ContractExecutionBinding.create(
                        UUID.randomUUID(),
                        release,
                        policy,
                        fingerprint,
                        LocalCharacterization.LIMITS);
        final var guard =
                new ContractRunGuard(
                        binding,
                        release,
                        policy,
                        ContractTestSupport.controlPlaneStart(binding),
                        ignored -> {});
        final var cancellation = new CancellationSignal();
        if (fault == Fault.CANCEL_BEFORE) {
            cancellation.cancel();
        }
        final int[] currentPage = {0};
        final int[] currentRow = {0};
        try {
            final var configuration =
                    DataExportContractObservationConfiguration.forRelease(
                            DataExportTemplate.COLETAS,
                            release,
                            LocalCharacterization.LIMITS,
                            guard.responsePathBoundary(),
                            fingerprint);
            final var source =
                    Bloco58DataExportAccess.enforce(
                            request -> {
                                currentPage[0] = request.page();
                                currentRow[0] = 0;
                                report.enter(Layer.INPUT);
                                final String document;
                                try (var input = pages.open(request.page())) {
                                    document = LocalCharacterization.read(input);
                                } catch (final IOException failure) {
                                    throw new IllegalStateException("READ_FAILURE");
                                }
                                report.page();
                                final var page =
                                        Bloco58DataExportAccess.parse(
                                                document, request, configuration, report::enter);
                                if (fault == Fault.CANCEL_AFTER_FETCH) {
                                    cancellation.cancel();
                                }
                                return page;
                            },
                            configuration,
                            guard);
            guard.validateMetadata(release.metadata());
            final var streamer =
                    new DataExportPageStreamer(
                            request -> {
                                final var page = source.fetch(request);
                                report.enter(Layer.TRAVERSAL);
                                return page;
                            },
                            DataExportExtractionAudit.noop(),
                            CLOCK);
            final var result =
                    new ExtrairColetasDataExport(
                                    streamer,
                                    new ColetaDataExportRecordMapper(),
                                    batch -> {
                                        report.enter(Layer.STAGING);
                                        for (int index = 0; index < batch.size(); index++) {
                                            report.mapped();
                                        }
                                        if (fault == Fault.STAGE_FIRST
                                                || fault == Fault.STAGE_SECOND
                                                        && batch.batchNumber() == 2) {
                                            throw new IllegalStateException("STAGING_FAILURE");
                                        }
                                        for (int index = 0; index < batch.size(); index++) {
                                            report.staged();
                                            report.compare(
                                                    expected.apply(currentPage[0], currentRow[0]++),
                                                    ColetasProjection.observe(
                                                            batch.recordAt(index)));
                                        }
                                        if (fault == Fault.CANCEL_AFTER_STAGE) {
                                            cancellation.cancel();
                                        }
                                        report.enter(Layer.TRAVERSAL);
                                    })
                            .execute(
                                    guard,
                                    new DataExportPageRequest(
                                            DataExportTemplate.COLETAS,
                                            new BusinessDateRange(
                                                    LocalDate.of(2036, 3, 20),
                                                    LocalDate.of(2036, 3, 20)),
                                            Optional.empty(),
                                            1,
                                            per,
                                            DataExportTemplate.COLETAS.defaultOrderBy()),
                                    new DataExportExtractionLimits(maximumPages, maximumRows, 100),
                                    cancellation);
            if (result.traversalVerification().provesCoverageOrSnapshot()) {
                report.difference("/flow/snapshot");
            }
            report.terminal();
        } catch (final RuntimeException failure) {
            report.failed(failure);
            try {
                guard.complete();
                report.difference("/flow/incompletePermit");
            } catch (
                    final br.com.esl.etl.v2.plataforma.contrato.ContractDriftException
                            expectedFailure) {
                /* Incomplete remains unusable. */
            }
        }
        return report;
    }

    /** Fixed local schema from COL/ADR0023; neither observed ESL shape nor replacement release. */
    public static SourceContractRelease decisionFixture() {
        final var baseline = DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS);
        final List<ContractResponse.Field> fields = new ArrayList<>();
        fields.add(
                new ContractResponse.Field(
                        ContractResponse.FieldScope.ENVELOPE,
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.Presence.REQUIRED,
                        false,
                        List.of(ContractResponse.JsonType.ARRAY)));
        for (final String field : ColetasProjection.FIELDS) {
            final boolean integer =
                    List.of(
                                    "id",
                                    "sequence_code",
                                    "pick_item_id",
                                    "fit_p_m_pck_sequence_code",
                                    "pck_mik_mft_sequence_code")
                            .contains(field);
            final boolean object = List.of("manifesto", "frete").contains(field);
            fields.add(
                    new ContractResponse.Field(
                            "/" + field,
                            object
                                    ? ContractResponse.Cardinality.OBJECT
                                    : ContractResponse.Cardinality.SCALAR,
                            field.equals("id")
                                    ? ContractResponse.Presence.REQUIRED
                                    : ContractResponse.Presence.OPTIONAL,
                            !field.equals("id"),
                            List.of(
                                    integer
                                            ? ContractResponse.JsonType.INTEGER
                                            : object
                                                    ? ContractResponse.JsonType.OBJECT
                                                    : ContractResponse.JsonType.STRING)));
        }
        return SourceContractRelease.create(
                baseline.sourceKind(),
                baseline.documentReference(),
                "bloco58-decision-fixture-v1",
                baseline.metadata(),
                new ContractResponse(
                        "/data",
                        ContractResponse.Cardinality.ARRAY,
                        ContractResponse.ObservationState.POPULATED,
                        "/id",
                        fields));
    }
}
