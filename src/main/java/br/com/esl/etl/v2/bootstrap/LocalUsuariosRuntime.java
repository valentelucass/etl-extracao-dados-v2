package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.usuarios.aplicacao.ExtrairUsuariosGraphQl;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioGraphQlNodeMapper;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioPromotionGateway;
import br.com.esl.etl.v2.modulos.usuarios.aplicacao.UsuarioStagingGateway;
import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.contrato.ContractSourceKind;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlGateway;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeExecutionSession;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeWorkloadHandler;
import br.com.esl.etl.v2.plataforma.qualidade.DataQualityPolicyReference;
import br.com.esl.etl.v2.plataforma.qualidade.FailClosedDataQualityEngine;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.util.Objects;

/** Uma travessia real de Usuários; contadores O(1), uma página, sem callback de extração. */
public final class LocalUsuariosRuntime implements RuntimeWorkloadHandler {
    private final br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey partition;
    private final ContractRunGuard guard;
    private final GraphQlExtractionLimits limits;
    private final Source source;
    private final UsuarioStagingGateway staging;
    private final UsuarioPromotionGateway promotion;
    private final FailClosedDataQualityEngine quality;
    private final DataQualityPolicyReference policy;

    public LocalUsuariosRuntime(
            final br.com.esl.etl.v2.plataforma.controle.ExecutionPartitionKey partition,
            final ContractRunGuard guard,
            final GraphQlExtractionLimits limits,
            final Source source,
            final UsuarioStagingGateway staging,
            final UsuarioPromotionGateway promotion,
            final FailClosedDataQualityEngine quality,
            final DataQualityPolicyReference policy) {
        this.partition = Objects.requireNonNull(partition);
        this.guard = Objects.requireNonNull(guard);
        this.limits = Objects.requireNonNull(limits);
        this.source = Objects.requireNonNull(source);
        this.staging = Objects.requireNonNull(staging);
        this.promotion = Objects.requireNonNull(promotion);
        this.quality = Objects.requireNonNull(quality);
        this.policy = Objects.requireNonNull(policy);
    }

    @Override
    public void execute(final RuntimeExecutionSession session) {
        try {
            if (!partition.equals(session.start().partition())) {
                throw new IllegalArgumentException("RUNTIME_USERS_PARTITION_MISMATCH");
            }
            guard.verifyExecutionBinding(session.start());
            guard.verifySource(
                    ContractSourceKind.GRAPHQL,
                    GraphQlReadOperation.USERS_SNAPSHOT.documentReference());
            final var audit = session.extractionAudit(GraphQlReadOperation.USERS_SNAPSHOT);
            // The factory, gate, streamer and use case share this exact lease-aware token.
            final var cancellation = session.cancellation();
            cancellation.throwIfCancellationRequested();
            final var gateway = source.create(guard, cancellation);
            final long[] staged = new long[2];
            final var result =
                    new ExtrairUsuariosGraphQl(
                                    new GraphQlPageStreamer(gateway, audit, session.clock()),
                                    batch -> {
                                        if (!batch.executionId()
                                                        .equals(session.start().executionId())
                                                || batch.batchNumber() != staged[0] + 1
                                                || batch.size() < 1
                                                || batch.size()
                                                        > ExtrairUsuariosGraphQl.PAGE_SIZE) {
                                            throw new IllegalStateException(
                                                    "RUNTIME_USERS_STAGE_BINDING_INVALID");
                                        }
                                        cancellation.throwIfCancellationRequested();
                                        staging.stage(batch);
                                        staged[0]++;
                                        staged[1] += batch.size();
                                    },
                                    new UsuarioGraphQlNodeMapper(),
                                    session.clock())
                            .execute(guard.executionContext(), limits, cancellation);
            if (result.pagesFetched() != staged[0]
                    || result.nodesDelivered() != staged[1]
                    || result.pagesFetched() != guard.responsePages()) {
                throw new IllegalStateException("RUNTIME_USERS_TRAVERSAL_COUNTS_INVALID");
            }
            final var permit = guard.complete();
            session.sealTraversal(result, permit, policy);
            session.staged(result);
            session.promote(permit, promotion, quality, policy);
        } catch (final RuntimeException failure) {
            guard.invalidateEvidence();
            throw failure;
        }
    }

    @FunctionalInterface
    public interface Source {
        GraphQlGateway create(ContractRunGuard guard, CancellationToken cancellation);
    }
}
