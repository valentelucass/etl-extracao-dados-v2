package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageBatch;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageRecord;
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
public final class ExtrairLocalizacaoCargasDataExport {

    private final DataExportStagingPipeline<LocalizacaoCargaStageRecord> pipeline;

    public ExtrairLocalizacaoCargasDataExport(
            final DataExportPageStreamer streamer,
            final LocalizacaoCargaDataExportRecordMapper mapper,
            final LocalizacaoCargaStagingGateway staging) {
        Objects.requireNonNull(mapper, "O mapper de LocalizacaoCargas é obrigatório.");
        Objects.requireNonNull(staging, "O staging de LocalizacaoCargas é obrigatório.");
        pipeline =
                new DataExportStagingPipeline<>(
                        DataExportTemplate.LOCALIZACAO_CARGAS,
                        streamer,
                        mapper::map,
                        (executionId, number, records, observedAt, cancellation) ->
                                staging.stage(
                                        new LocalizacaoCargaStageBatch(
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
