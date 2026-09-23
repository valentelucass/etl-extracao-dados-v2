package br.com.esl.etl.v2.modulos.sinistros.aplicacao;

import br.com.esl.etl.v2.modulos.sinistros.domain.SinistroObservation;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionCaptured;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportBatchStaging;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportStagingPipeline;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionEnvelope;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;

/** Real bounded streamer/guard/audit pipeline, with source and binding supplied at the edge. */
public final class ExtrairSinistroDataExport {
    private final DataExportStagingPipeline<ExpansionCaptured<SinistroObservation>> pipeline;

    public ExtrairSinistroDataExport(
            final DataExportPageStreamer streamer,
            final DataExportBatchStaging<ExpansionCaptured<SinistroObservation>> staging) {
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.SINISTROS,
                        streamer,
                        (ordinal, envelope) ->
                                ExpansionEnvelope.capture(
                                        envelope,
                                        new SinistroDataExportMapper().map(ordinal, envelope)),
                        staging);
    }

    public DataExportExtractionResult execute(
            final ContractRunGuard guard,
            final DataExportPageRequest request,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellation) {
        return pipeline.execute(guard, request, limits, cancellation);
    }
}
