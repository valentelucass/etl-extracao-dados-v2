package br.com.esl.etl.v2.modulos.faturasporcliente.aplicacao;

import br.com.esl.etl.v2.modulos.faturasporcliente.domain.FaturaClienteObservation;
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
public final class ExtrairFaturaClienteDataExport {
    private final DataExportStagingPipeline<ExpansionCaptured<FaturaClienteObservation>> pipeline;

    public ExtrairFaturaClienteDataExport(
            final DataExportPageStreamer streamer,
            final DataExportBatchStaging<ExpansionCaptured<FaturaClienteObservation>> staging) {
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.FATURAS_POR_CLIENTE,
                        streamer,
                        (ordinal, envelope) ->
                                ExpansionEnvelope.capture(
                                        envelope,
                                        new FaturaClienteDataExportMapper().map(ordinal, envelope)),
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
