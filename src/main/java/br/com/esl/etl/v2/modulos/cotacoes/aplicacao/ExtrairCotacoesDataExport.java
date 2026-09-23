package br.com.esl.etl.v2.modulos.cotacoes.aplicacao;

import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageBatch;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageRecord;
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
public final class ExtrairCotacoesDataExport {

    private final DataExportStagingPipeline<CotacaoStageRecord> pipeline;

    public ExtrairCotacoesDataExport(
            final DataExportPageStreamer streamer,
            final CotacaoDataExportRecordMapper mapper,
            final CotacaoStagingGateway staging) {
        Objects.requireNonNull(mapper, "O mapper de Cotacoes é obrigatório.");
        Objects.requireNonNull(staging, "O staging de Cotacoes é obrigatório.");
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.COTACOES,
                        streamer,
                        mapper::map,
                        (executionId, number, records, observedAt, cancellation) ->
                                staging.stage(
                                        new CotacaoStageBatch(
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
