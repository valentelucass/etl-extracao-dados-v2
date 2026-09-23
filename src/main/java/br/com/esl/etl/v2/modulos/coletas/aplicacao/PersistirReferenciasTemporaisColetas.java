package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamer;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Objects;

/** Liga a captura existente ao staging; falha parcial nunca sela uma travessia. */
public final class PersistirReferenciasTemporaisColetas {
    private final GraphQlPageStreamer streamer;
    private final ColetaTemporalReferenceStore store;
    private final Clock clock;

    public PersistirReferenciasTemporaisColetas(
            final GraphQlPageStreamer streamer,
            final ColetaTemporalReferenceStore store,
            final Clock clock) {
        this.streamer = Objects.requireNonNull(streamer, "O streamer é obrigatório.");
        this.store = Objects.requireNonNull(store, "O staging é obrigatório.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
    }

    public GraphQlExtractionResult execute(
            final ContractExecutionContext context,
            final String sourceInstance,
            final String tenantScope,
            final LocalDate requestDate,
            final GraphQlExtractionLimits limits,
            final CancellationToken cancellation) {
        Objects.requireNonNull(cancellation, "O cancelamento é obrigatório.");
        final var extractor =
                new ExtrairReferenciasTemporaisColetas(
                        streamer,
                        new ColetaGraphQlTemporalMapper(),
                        clock,
                        observation -> store.stage(observation, cancellation));
        final var result =
                extractor.execute(
                        context, sourceInstance, tenantScope, requestDate, limits, cancellation);
        cancellation.throwIfCancellationRequested();
        store.complete(result, cancellation);
        return result;
    }
}
