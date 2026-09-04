package br.com.esl.etl.v2.modulos.usuarios.aplicacao;

import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageBatch;
import br.com.esl.etl.v2.modulos.usuarios.domain.UsuarioStageRecord;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlQueryParameters;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/** Caso de uso shadow: reinicia sem cursor persistido e stageia exatamente uma página por vez. */
public final class ExtrairUsuariosGraphQl {

    public static final int PAGE_SIZE = 20;

    private final GraphQlPageStreamer pageStreamer;
    private final UsuarioStagingGateway stagingGateway;
    private final UsuarioGraphQlNodeMapper mapper;
    private final Clock clock;

    public ExtrairUsuariosGraphQl(
            final GraphQlPageStreamer pageStreamer,
            final UsuarioStagingGateway stagingGateway,
            final UsuarioGraphQlNodeMapper mapper,
            final Clock clock) {
        this.pageStreamer =
                Objects.requireNonNull(pageStreamer, "O streamer GraphQL é obrigatório.");
        this.stagingGateway =
                Objects.requireNonNull(stagingGateway, "O staging de Usuários é obrigatório.");
        this.mapper = Objects.requireNonNull(mapper, "O mapper de Usuários é obrigatório.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
    }

    public GraphQlExtractionResult execute(
            final ContractExecutionContext executionContext,
            final GraphQlExtractionLimits limits,
            final CancellationToken cancellationToken) {
        final ContractExecutionContext context =
                Objects.requireNonNull(executionContext, "O contexto contratual é obrigatório.");
        final AtomicInteger batchNumber = new AtomicInteger();
        return pageStreamer.stream(
                context,
                GraphQlPageRequest.initial(
                        GraphQlReadOperation.USERS_SNAPSHOT,
                        GraphQlQueryParameters.enabledUsers(),
                        PAGE_SIZE),
                Objects.requireNonNull(limits, "Os limites de Usuários são obrigatórios."),
                Objects.requireNonNull(cancellationToken, "O cancelamento é obrigatório."),
                page -> {
                    final List<UsuarioStageRecord> records =
                            new ArrayList<>(Math.min(page.nodeCount(), PAGE_SIZE));
                    final AtomicInteger ordinal = new AtomicInteger();
                    page.forEachNode(
                            node -> records.add(mapper.map(ordinal.incrementAndGet(), node)));
                    stagingGateway.stage(
                            new UsuarioStageBatch(
                                    context.executionId(),
                                    batchNumber.incrementAndGet(),
                                    records,
                                    clock.instant()));
                });
    }
}
