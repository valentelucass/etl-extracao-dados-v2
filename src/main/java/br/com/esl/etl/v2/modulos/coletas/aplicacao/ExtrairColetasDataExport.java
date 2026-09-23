package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportStagingPipeline;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Travessia shadow 6908 até staging, sem promoção, relação ou composição operacional. */
public final class ExtrairColetasDataExport {

    private final DataExportStagingPipeline<ColetaStageRecord> pipeline;

    public ExtrairColetasDataExport(
            final DataExportPageStreamer streamer,
            final ColetaDataExportRecordMapper mapper,
            final ColetaStagingGateway staging) {
        Objects.requireNonNull(mapper, "O mapper de Coletas é obrigatório.");
        Objects.requireNonNull(staging, "O staging de Coletas é obrigatório.");
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.COLETAS,
                        streamer,
                        mapper::map,
                        (executionId, number, records, observedAt, cancellation) ->
                                staging.stage(
                                        new ColetaStageBatch(
                                                executionId, number, records, observedAt),
                                        cancellation));
    }

    public DataExportExtractionResult execute(
            final ContractRunGuard guard,
            final DataExportPageRequest initialRequest,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellationToken) {
        return pipeline.execute(guard, initialRequest, limits, cancellationToken);
    }
}
