package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;

/**
 * Liga travessia, mapper e staging síncrono com uma página mais um lote limitado em memória.
 * Terminalidade local não publica, não promove e não altera watermark.
 */
public final class DataExportStagingPipeline<T> {

    public static final int MAXIMUM_BATCH_SIZE = 100;

    private final DataExportTemplate template;
    private final DataExportPageStreamer streamer;
    private final RecordMapper<T> mapper;
    private final DataExportBatchStaging<T> staging;

    public DataExportStagingPipeline(
            final DataExportTemplate template,
            final DataExportPageStreamer streamer,
            final BiFunction<Integer, JsonNode, T> mapper,
            final DataExportBatchStaging<T> staging) {
        this(
                template,
                streamer,
                (ordinal, input) -> mapper.apply(ordinal, input.value()),
                staging,
                true);
        Objects.requireNonNull(mapper, "O mapper é obrigatório.");
    }

    private DataExportStagingPipeline(
            final DataExportTemplate template,
            final DataExportPageStreamer streamer,
            final RecordMapper<T> mapper,
            final DataExportBatchStaging<T> staging,
            final boolean recordInput) {
        this.template = Objects.requireNonNull(template, "O template é obrigatório.");
        this.streamer = Objects.requireNonNull(streamer, "O streamer é obrigatório.");
        this.mapper = Objects.requireNonNull(mapper, "O mapper é obrigatório.");
        this.staging = Objects.requireNonNull(staging, "O staging é obrigatório.");
    }

    public static <T> DataExportStagingPipeline<T> withRecordInputs(
            final DataExportTemplate template,
            final DataExportPageStreamer streamer,
            final RecordMapper<T> mapper,
            final DataExportBatchStaging<T> staging) {
        return new DataExportStagingPipeline<>(template, streamer, mapper, staging, true);
    }

    @FunctionalInterface
    public interface RecordMapper<T> {
        T map(int ordinal, DataExportRecordInput input);
    }

    /** Cada tentativa reinicia na página 1 com um guard próprio; staging parcial não é sucesso. */
    public DataExportExtractionResult execute(
            final ContractRunGuard guard,
            final DataExportPageRequest initialRequest,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellationToken) {
        Objects.requireNonNull(guard, "O guard de contrato é obrigatório.");
        Objects.requireNonNull(initialRequest, "A requisição é obrigatória.");
        Objects.requireNonNull(limits, "Os limites são obrigatórios.");
        final CancellationToken cancellation =
                Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório.");
        try {
            if (initialRequest.template() != template) {
                throw new IllegalArgumentException("O template não corresponde à vertical.");
            }
            if (!initialRequest.profile().approves(initialRequest, guard.sourceSelection())) {
                guard.failClosed(ContractDriftException.Reason.EXECUTION_BINDING_MISMATCH);
            }
            limits.validate(initialRequest);
            guard.verifySource(
                    ContractSourceKind.DATA_EXPORT,
                    DataExportContractAdapter.documentReference(template));
            guard.verifyNextDataExportPage(initialRequest.page());
            cancellation.throwIfCancellationRequested();
            final BatchConsumer consumer = new BatchConsumer(guard, cancellation);
            final DataExportExtractionResult result =
                    streamer.stream(
                            guard.executionContext(),
                            initialRequest,
                            limits,
                            cancellation,
                            consumer::accept);
            verifyObservedPage(guard, result.terminalPage());
            cancellation.throwIfCancellationRequested();
            return result;
        } catch (final RuntimeException exception) {
            guard.invalidateEvidence();
            throw exception;
        }
    }

    private static void verifyObservedPage(final ContractRunGuard guard, final int page) {
        if (guard.responsePages() != page) {
            guard.failClosed(ContractDriftException.Reason.RESPONSE_EVIDENCE_REQUIRED);
        }
    }

    private final class BatchConsumer {
        private final ContractRunGuard guard;
        private final CancellationToken cancellation;
        private final List<T> records = new ArrayList<>(MAXIMUM_BATCH_SIZE);
        private int batchNumber;

        private BatchConsumer(final ContractRunGuard guard, final CancellationToken cancellation) {
            this.guard = guard;
            this.cancellation = cancellation;
        }

        private void accept(final DataExportReadPage page) {
            try {
                verifyObservedPage(guard, page.page());
                page.response()
                        .forEachRecordInput(
                                record -> {
                                    cancellation.throwIfCancellationRequested();
                                    final T mapped =
                                            Objects.requireNonNull(
                                                    mapper.map(records.size() + 1, record),
                                                    "O mapper retornou registro nulo.");
                                    cancellation.throwIfCancellationRequested();
                                    records.add(mapped);
                                    if (records.size() == MAXIMUM_BATCH_SIZE) {
                                        flush(page);
                                    }
                                });
                if (!records.isEmpty()) {
                    flush(page);
                }
            } finally {
                records.clear();
            }
        }

        private void flush(final DataExportReadPage page) {
            cancellation.throwIfCancellationRequested();
            batchNumber = Math.incrementExact(batchNumber);
            staging.stage(
                    page.executionId(),
                    batchNumber,
                    List.copyOf(records),
                    page.readAt(),
                    cancellation);
            records.clear();
            cancellation.throwIfCancellationRequested();
        }
    }
}
