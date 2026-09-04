package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Porta para auditar a execução sem reter ou registrar payload de negócio. */
public interface DataExportExtractionAudit {

    void executionStarted(ExecutionStarted event);

    void pageRead(PageRead event);

    /** Registra apenas o término da travessia local; não atesta cobertura completa da origem. */
    void executionCompleted(DataExportExtractionResult result);

    void executionFailed(ExecutionFailed event);

    static DataExportExtractionAudit noop() {
        return NoopAudit.INSTANCE;
    }

    /** Metadados de início necessários para correlacionar execução, janela e template. */
    record ExecutionStarted(
            UUID executionId,
            DataExportTemplate template,
            BusinessDateRange businessDateWindow,
            Optional<SourceDateTimeRange> updatedAtWindow,
            Instant startedAt) {

        public ExecutionStarted {
            Objects.requireNonNull(executionId, "O identificador da execução é obrigatório.");
            Objects.requireNonNull(template, "O template Data Export é obrigatório.");
            Objects.requireNonNull(businessDateWindow, "A janela de negócio é obrigatória.");
            updatedAtWindow = updatedAtWindow == null ? Optional.empty() : updatedAtWindow;
            Objects.requireNonNull(startedAt, "O início da execução é obrigatório.");
        }
    }

    /**
     * Metadados de uma página já recebida, sem expor os registros nela contidos.
     *
     * <p>{@code recordCount} é a quantidade de linhas físicas. {@code distinctEntityCount} é a
     * quantidade de entidades distintas pelo {@code id} validado contra o {@code per} pedido.
     */
    record PageRead(
            UUID executionId,
            int page,
            int requestedPageSize,
            int recordCount,
            int distinctEntityCount,
            Instant readAt) {

        public PageRead {
            Objects.requireNonNull(executionId, "O identificador da execução é obrigatório.");
            Objects.requireNonNull(readAt, "O horário de leitura é obrigatório.");
            if (page < 1) {
                throw new IllegalArgumentException("A página deve ser maior que zero.");
            }
            if (requestedPageSize < 1) {
                throw new IllegalArgumentException("O per solicitado deve ser maior que zero.");
            }
            if (recordCount < 0 || distinctEntityCount < 0) {
                throw new IllegalArgumentException(
                        "As contagens de página não podem ser negativas.");
            }
            if (distinctEntityCount > requestedPageSize) {
                throw new IllegalArgumentException(
                        "A quantidade de entidades distintas não pode exceder o per solicitado.");
            }
            if (distinctEntityCount > recordCount) {
                throw new IllegalArgumentException(
                        "A quantidade de entidades distintas não pode exceder as linhas físicas.");
            }
        }
    }

    /** Falha sanitizada; o payload e a mensagem original da exceção não são propagados. */
    record ExecutionFailed(
            UUID executionId,
            DataExportTemplate template,
            int pagesFetched,
            long recordsDelivered,
            Instant failedAt,
            DataExportFailureCategory failureCategory) {

        public ExecutionFailed {
            Objects.requireNonNull(executionId, "O identificador da execução é obrigatório.");
            Objects.requireNonNull(template, "O template Data Export é obrigatório.");
            Objects.requireNonNull(failedAt, "O horário de falha é obrigatório.");
            if (pagesFetched < 0 || recordsDelivered < 0) {
                throw new IllegalArgumentException(
                        "Os volumes de execução não podem ser negativos.");
            }
            Objects.requireNonNull(failureCategory, "A categoria de falha é obrigatória.");
        }
    }

    final class NoopAudit implements DataExportExtractionAudit {

        private static final NoopAudit INSTANCE = new NoopAudit();

        private NoopAudit() {}

        @Override
        public void executionStarted(final ExecutionStarted event) {}

        @Override
        public void pageRead(final PageRead event) {}

        @Override
        public void executionCompleted(final DataExportExtractionResult result) {}

        @Override
        public void executionFailed(final ExecutionFailed event) {}
    }
}
