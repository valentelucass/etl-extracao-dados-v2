package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoPromotionGateway;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoStagingGateway;
import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.ExtrairCotacoesDataExport;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.ExtrairLocalizacaoCargasDataExport;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaPromotionGateway;
import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaStagingGateway;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ExtrairManifestosDataExport;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoPromotionGateway;
import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoStagingGateway;
import br.com.esl.etl.v2.plataforma.contrato.ContractPromotionPermit;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRuntimeWorkload;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.persistencia.staging.ShadowPromotionGateway;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPublicationResult;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPromotionPermit;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/**
 * Typed additions to the existing Coletas/Fretes composition, with no domain reducer in the JVM.
 */
public final class LocalFiveVerticalRuntime {
    private LocalFiveVerticalRuntime() {}

    public static DataExportRuntimeWorkload manifestos(
            final DataExportRuntimeWorkload.Input input,
            final ManifestoStagingGateway staging,
            final ManifestoPromotionGateway promotion,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        require(input, DataExportTemplate.MANIFESTOS, staging);
        return new DataExportRuntimeWorkload(
                input,
                (streamer, guard, request, limits, cancellation) ->
                        new ExtrairManifestosDataExport(
                                        streamer, new ManifestoDataExportRecordMapper(), staging)
                                .execute(guard, request, limits, cancellation),
                promotion,
                quality,
                policy);
    }

    public static DataExportRuntimeWorkload localizacao(
            final DataExportRuntimeWorkload.Input input,
            final LocalizacaoCargaStagingGateway staging,
            final LocalizacaoCargaPromotionGateway promotion,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        require(input, DataExportTemplate.LOCALIZACAO_CARGAS, staging);
        return new DataExportRuntimeWorkload(
                input,
                (streamer, guard, request, limits, cancellation) ->
                        new ExtrairLocalizacaoCargasDataExport(
                                        streamer,
                                        new LocalizacaoCargaDataExportRecordMapper(),
                                        staging)
                                .execute(guard, request, limits, cancellation),
                promotion,
                quality,
                policy);
    }

    public static DataExportRuntimeWorkload cotacoes(
            final DataExportRuntimeWorkload.Input input,
            final CotacaoStagingGateway staging,
            final CotacaoPromotionGateway promotion,
            final long referenceReleaseId,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        require(input, DataExportTemplate.COTACOES, staging);
        Objects.requireNonNull(promotion);
        if (referenceReleaseId < 1) {
            throw new IllegalArgumentException("EXPLICIT_TARIFF_RELEASE_REQUIRED");
        }
        final ShadowPromotionGateway bound =
                new ShadowPromotionGateway() {
                    @Override
                    public void prepareCandidateSet(
                            final ContractPromotionPermit permit,
                            final CancellationToken cancellation) {
                        promotion.prepareCandidateSet(permit, cancellation);
                    }

                    @Override
                    public StagingPublicationResult applyReconcileAndPublish(
                            final ContractPromotionPermit permit,
                            final DataQualityPromotionPermit dq,
                            final CancellationToken cancellation) {
                        return promotion.applyReconcileAndPublish(
                                permit, dq, referenceReleaseId, cancellation);
                    }
                };
        return new DataExportRuntimeWorkload(
                input,
                (streamer, guard, request, limits, cancellation) ->
                        new ExtrairCotacoesDataExport(
                                        streamer, new CotacaoDataExportRecordMapper(), staging)
                                .execute(guard, request, limits, cancellation),
                bound,
                quality,
                policy);
    }

    private static void require(
            final DataExportRuntimeWorkload.Input input,
            final DataExportTemplate expected,
            final Object staging) {
        Objects.requireNonNull(staging);
        if (Objects.requireNonNull(input).request().template() != expected) {
            throw new IllegalArgumentException("RUNTIME_VERTICAL_TEMPLATE_MISMATCH");
        }
    }
}
