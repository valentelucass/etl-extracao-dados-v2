package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageBatch;
import br.com.esl.etl.v2.modulos.fretes.domain.FreteStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportStagingPipeline;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Travessia shadow 6389 até staging; sidecar e relações continuam independentes. */
public final class ExtrairFretesDataExport {

    private final DataExportStagingPipeline<FreteStageRecord> pipeline;

    public ExtrairFretesDataExport(
            final DataExportPageStreamer streamer,
            final FreteDataExportRecordMapper mapper,
            final FreteStagingGateway staging) {
        Objects.requireNonNull(mapper, "O mapper de Fretes é obrigatório.");
        Objects.requireNonNull(staging, "O staging de Fretes é obrigatório.");
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.FRETES,
                        streamer,
                        mapper::map,
                        (executionId, number, records, observedAt, cancellation) ->
                                staging.stage(
                                        new FreteStageBatch(
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
