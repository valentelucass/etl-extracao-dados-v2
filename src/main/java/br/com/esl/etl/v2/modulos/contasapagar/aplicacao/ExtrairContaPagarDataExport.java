package br.com.esl.etl.v2.modulos.contasapagar.aplicacao;

import br.com.esl.etl.v2.modulos.contasapagar.domain.ContaPagarObservation;
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
public final class ExtrairContaPagarDataExport {
    private final DataExportStagingPipeline<ExpansionCaptured<ContaPagarObservation>> pipeline;

    public ExtrairContaPagarDataExport(
            final DataExportPageStreamer streamer,
            final DataExportBatchStaging<ExpansionCaptured<ContaPagarObservation>> staging) {
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.CONTAS_A_PAGAR,
                        streamer,
                        (ordinal, envelope) ->
                                ExpansionEnvelope.capture(
                                        envelope,
                                        new ContaPagarDataExportMapper().map(ordinal, envelope)),
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
