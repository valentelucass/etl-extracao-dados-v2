package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.contrato.ContractExecutionContext;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionLimits;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlExtractionResult;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamer;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlQueryParameters;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlReadOperation;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/** Captura local injetável, síncrona e limitada à página; não faz cruzamento ou promoção. */
public final class ExtrairReferenciasTemporaisColetas {
    private final GraphQlPageStreamer streamer;
    private final ColetaGraphQlTemporalMapper mapper;
    private final Clock clock;
    private final Consumer<ColetaTemporalObservation> consumer;

    public ExtrairReferenciasTemporaisColetas(
            final GraphQlPageStreamer streamer,
            final ColetaGraphQlTemporalMapper mapper,
            final Clock clock,
            final Consumer<ColetaTemporalObservation> consumer) {
        this.streamer = Objects.requireNonNull(streamer, "O streamer é obrigatório.");
        this.mapper = Objects.requireNonNull(mapper, "O mapper é obrigatório.");
        this.clock = Objects.requireNonNull(clock, "O relógio é obrigatório.");
        this.consumer = Objects.requireNonNull(consumer, "O consumidor é obrigatório.");
    }

    public GraphQlExtractionResult execute(
            final ContractExecutionContext context,
            final String sourceInstance,
            final String tenantScope,
            final LocalDate requestDate,
            final GraphQlExtractionLimits limits,
            final CancellationToken cancellation) {
        Objects.requireNonNull(context, "O contexto é obrigatório.");
        validateScope(sourceInstance, tenantScope);
        final var request =
                GraphQlPageRequest.initial(
                        GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE,
                        GraphQlQueryParameters.picksTemporalForDate(requestDate),
                        ColetaTemporalObservation.MAXIMUM_PAGE_SIZE);
        final AtomicInteger pageNumber = new AtomicInteger();
        return streamer.stream(
                context,
                request,
                limits,
                cancellation,
                page -> {
                    final int number = pageNumber.incrementAndGet();
                    final var observedAt = clock.instant();
                    cancellation.throwIfCancellationRequested();
                    final AtomicInteger ordinal = new AtomicInteger();
                    page.forEachNode(
                            node -> {
                                cancellation.throwIfCancellationRequested();
                                final var reference =
                                        mapper.map(
                                                context.executionId(),
                                                sourceInstance,
                                                tenantScope,
                                                requestDate,
                                                number,
                                                ordinal.incrementAndGet(),
                                                observedAt,
                                                node);
                                cancellation.throwIfCancellationRequested();
                                consumer.accept(reference);
                                cancellation.throwIfCancellationRequested();
                            });
                });
    }

    private static void validateScope(final String sourceInstance, final String tenantScope) {
        if (sourceInstance == null
                || !sourceInstance.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || tenantScope == null
                || !tenantScope.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}")
                || Set.of("GLOBAL", "SINGLETON", "DEFAULT")
                        .contains(tenantScope.toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("A captura exige origem e tenant explícitos.");
        }
    }
}
