package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageBatch;
import br.com.esl.etl.v2.modulos.localizacaocargas.domain.LocalizacaoCargaStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportBatchStaging;
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
    private final boolean rawInput;

    public ExtrairLocalizacaoCargasDataExport(
            final DataExportPageStreamer streamer,
            final LocalizacaoCargaDataExportRecordMapper mapper,
            final LocalizacaoCargaStagingGateway staging) {
        this(streamer, mapper, staging, false);
    }

    public static ExtrairLocalizacaoCargasDataExport observedWithRawInput(
            final DataExportPageStreamer streamer, final LocalizacaoCargaStagingGateway staging) {
        return new ExtrairLocalizacaoCargasDataExport(
                streamer, LocalizacaoCargaDataExportRecordMapper.observed20261024(), staging, true);
    }

    private ExtrairLocalizacaoCargasDataExport(
            final DataExportPageStreamer streamer,
            final LocalizacaoCargaDataExportRecordMapper mapper,
            final LocalizacaoCargaStagingGateway staging,
            final boolean rawInput) {
        this.rawInput = rawInput;
        Objects.requireNonNull(mapper, "O mapper de LocalizacaoCargas é obrigatório.");
        Objects.requireNonNull(staging, "O staging de LocalizacaoCargas é obrigatório.");
        final DataExportBatchStaging<LocalizacaoCargaStageRecord> target =
                (executionId, number, records, observedAt, cancellation) ->
                        staging.stage(
                                new LocalizacaoCargaStageBatch(
                                        executionId, number, records, observedAt),
                                cancellation);
        pipeline =
                rawInput
                        ? DataExportStagingPipeline.withRecordInputs(
                                DataExportTemplate.LOCALIZACAO_CARGAS,
                                streamer,
                                (ordinal, input) ->
                                        mapper.map(
                                                ordinal,
                                                input.rawJson()
                                                        .orElseThrow(
                                                                () ->
                                                                        new IllegalArgumentException(
                                                                                "OBSERVED_LOCAL_RAW_REQUIRED"))),
                                target)
                        : new DataExportStagingPipeline<>(
                                DataExportTemplate.LOCALIZACAO_CARGAS,
                                streamer,
                                mapper::map,
                                target);
    }

    public DataExportExtractionResult execute(
            final ContractRunGuard guard,
            final DataExportPageRequest initialRequest,
            final DataExportExtractionLimits limits,
            final CancellationToken cancellationToken) {
        if (rawInput
                && (!initialRequest.profile().rawRecordsRequired()
                        || !initialRequest
                                .profile()
                                .approves(initialRequest, guard.sourceSelection()))) {
            guard.failClosed(
                    br.com.esl.etl.v2.plataforma.contrato.ContractDriftException.Reason
                            .EXECUTION_BINDING_MISMATCH);
        }
        return pipeline.execute(guard, initialRequest, limits, cancellationToken);
    }
}
