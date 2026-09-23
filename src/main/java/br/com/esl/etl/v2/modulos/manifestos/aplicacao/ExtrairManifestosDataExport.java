package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageBatch;
import br.com.esl.etl.v2.modulos.manifestos.domain.ManifestoStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportStagingPipeline;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Travessia shadow B55 até staging; sidecar e relações continuam independentes. */
public final class ExtrairManifestosDataExport {

    private final DataExportStagingPipeline<ManifestoStageRecord> pipeline;

    public ExtrairManifestosDataExport(
            final DataExportPageStreamer streamer,
            final ManifestoDataExportRecordMapper mapper,
            final ManifestoStagingGateway staging) {
        Objects.requireNonNull(mapper, "O mapper de Manifestos é obrigatório.");
        Objects.requireNonNull(staging, "O staging de Manifestos é obrigatório.");
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.MANIFESTOS,
                        streamer,
                        mapper::map,
                        (executionId, number, records, observedAt, cancellation) ->
                                staging.stage(
                                        new ManifestoStageBatch(
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
