package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.SourceContractRelease;
import br.com.esl.etl.v2.plataforma.controle.ImmutableFingerprint;

/** Pages and their declared release consumed by the existing three relational extractors. */
public interface RelationalCaptureSource {
    SourceContractRelease contractRelease(DataExportTemplate template);

    DataExportGateway gateway(
            DataExportTemplate template,
            ContractRunGuard guard,
            SourceContractRelease release,
            ImmutableFingerprint configuration);

    RelationalCaptureSource withAnalyticManifestDetails();

    RelationalCaptureSource withAnalyticCollectionDetails();

    void batchStarted(int records);

    void batchStaged(int records);

    void captureClosed();

    RelationalCaptureSource observed(Observer observer);

    Metrics metrics();

    public interface Observer {
        Observer NONE = new Observer() {};

        default void beforeFetch() {}

        default void pageFetched(DataExportPageResponse page, long bytes) {}

        default void batchStarted(int records) {}

        default void batchStaged(int records) {}

        default void captureClosed() {}
    }

    record Metrics(int fetchedPages, long bytes, int maximumPageBytes) {}
}
