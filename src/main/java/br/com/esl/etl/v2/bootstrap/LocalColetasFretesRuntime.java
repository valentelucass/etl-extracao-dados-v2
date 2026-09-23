package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaPromotionGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaStagingGateway;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.ExtrairColetasDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.ExtrairFretesDataExport;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FretePromotionGateway;
import br.com.esl.etl.v2.modulos.fretes.aplicacao.FreteStagingGateway;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRuntimeWorkload;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import java.util.Objects;

/**
 * Composição injetada para harness local; nenhum DataSource, segredo ou entrypoint CLI é criado.
 */
public final class LocalColetasFretesRuntime {
    private LocalColetasFretesRuntime() {}

    public static DataExportRuntimeWorkload coletas(
            final DataExportRuntimeWorkload.Input input,
            final ColetaStagingGateway staging,
            final ColetaPromotionGateway promotion,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        requireTemplate(input, DataExportTemplate.COLETAS);
        Objects.requireNonNull(staging);
        return new DataExportRuntimeWorkload(
                input,
                (streamer, guard, request, limits, cancellation) ->
                        new ExtrairColetasDataExport(
                                        streamer, new ColetaDataExportRecordMapper(), staging)
                                .execute(guard, request, limits, cancellation),
                promotion,
                quality,
                policy);
    }

    public static DataExportRuntimeWorkload fretes(
            final DataExportRuntimeWorkload.Input input,
            final FreteStagingGateway staging,
            final FretePromotionGateway promotion,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        requireTemplate(input, DataExportTemplate.FRETES);
        Objects.requireNonNull(staging);
        return new DataExportRuntimeWorkload(
                input,
                (streamer, guard, request, limits, cancellation) ->
                        new ExtrairFretesDataExport(
                                        streamer, new FreteDataExportRecordMapper(), staging)
                                .execute(guard, request, limits, cancellation),
                promotion,
                quality,
                policy);
    }

    private static void requireTemplate(
            final DataExportRuntimeWorkload.Input input, final DataExportTemplate template) {
        if (Objects.requireNonNull(input).request().template() != template) {
            throw new IllegalArgumentException("RUNTIME_VERTICAL_TEMPLATE_MISMATCH");
        }
    }
}
