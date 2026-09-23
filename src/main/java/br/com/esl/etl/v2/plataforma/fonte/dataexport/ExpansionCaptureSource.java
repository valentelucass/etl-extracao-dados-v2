package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionPolicy;
import java.time.LocalDate;

/** Capture boundary shared by bounded local artifacts and laboratory generators. */
public interface ExpansionCaptureSource {
    SourceContractRelease contract(DataExportTemplate template);

    DataExportGateway gateway(
            DataExportTemplate template,
            ContractRunGuard guard,
            SourceContractRelease release,
            ImmutableFingerprint configuration);

    default void validate(
            final DataExportTemplate template, final LocalDate date, final ExpansionPolicy policy) {
        policy.validate(date);
    }

    void batchStarted(int records);

    void batchStaged(int records);

    void captureClosed();

    Metrics metrics();

    interface Observer {
        Observer NONE = new Observer() {};

        default void beforeFetch() {}

        default void pageFetched(DataExportPageResponse page, long bytes) {}

        default void batchStarted(int records) {}

        default void batchStaged(int records) {}

        default void captureClosed() {}
    }

    record Metrics(int fetchedPages, long bytes, int maximumPageBytes) {}
}
