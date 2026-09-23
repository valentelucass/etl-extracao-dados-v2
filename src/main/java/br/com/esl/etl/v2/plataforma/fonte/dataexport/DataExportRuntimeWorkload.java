package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionSession;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadHandler;
import br.com.esl.etl.v2.plataforma.persistencia.staging.ShadowPromotionGateway;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Uma travessia ligada explicitamente à partição, sem inventar tradução de bordas da origem. */
public final class DataExportRuntimeWorkload implements RuntimeWorkloadHandler {
    private final Input input;
    private final Extraction extraction;
    private final ShadowPromotionGateway promotion;
    private final FailClosedDataQualityEngine quality;
    private final DataQualityPolicyReference policy;

    public DataExportRuntimeWorkload(
            final Input input,
            final Extraction extraction,
            final ShadowPromotionGateway promotion,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        this.input = Objects.requireNonNull(input);
        this.extraction = Objects.requireNonNull(extraction);
        this.promotion = Objects.requireNonNull(promotion);
        this.quality = Objects.requireNonNull(quality);
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public void execute(final RuntimeExecutionSession session) {
        Objects.requireNonNull(session);
        final ContractRunGuard guard = input.guard();
        try {
            if (!input.partition().equals(session.start().partition())) {
                throw new IllegalArgumentException("RUNTIME_SOURCE_PARTITION_MISMATCH");
            }
            guard.verifyExecutionBinding(session.start());
            final CancellationToken cancellation = session.cancellation();
            cancellation.throwIfCancellationRequested();
            final var gateways =
                    DataExportContractGate.enforce(
                            input.gateways(), input.request().template(), guard);
            gateways.templateInfoGateway().fetchInfo(input.request().template());
            cancellation.throwIfCancellationRequested();
            final var streamer =
                    new DataExportPageStreamer(
                            gateways.dataGateway(),
                            session.extractionAudit(input.request().template()),
                            session.clock());
            final var result =
                    extraction.execute(
                            streamer, guard, input.request(), input.limits(), cancellation);
            final var permit = guard.complete();
            session.sealTraversal(result, permit, policy);
            session.staged(result);
            session.promote(permit, promotion, quality, policy);
        } catch (final RuntimeException exception) {
            guard.invalidateEvidence();
            throw exception;
        }
    }

    /** O vínculo janela/filtro é fornecido explicitamente pelo caller local, nunca derivado. */
    public record Input(
            ExecutionPartitionKey partition,
            ContractRunGuard guard,
            DataExportPageRequest request,
            DataExportExtractionLimits limits,
            DataExportHttpGatewayBundle gateways) {
        public Input {
            Objects.requireNonNull(partition);
            Objects.requireNonNull(guard);
            Objects.requireNonNull(request);
            Objects.requireNonNull(limits);
            Objects.requireNonNull(gateways);
            limits.validate(request);
            if (request.page() != 1
                    || !request.template()
                            .approvesRequestSemantics(request.pageSize(), request.orderBy())) {
                throw new IllegalArgumentException("RUNTIME_SOURCE_REQUEST_INVALID");
            }
        }

        @Override
        public String toString() {
            return "DataExportRuntimeInput[redacted]";
        }
    }

    @FunctionalInterface
    public interface Extraction {
        DataExportExtractionResult execute(
                DataExportPageStreamer streamer,
                ContractRunGuard guard,
                DataExportPageRequest request,
                DataExportExtractionLimits limits,
                CancellationToken cancellation);
    }
}
