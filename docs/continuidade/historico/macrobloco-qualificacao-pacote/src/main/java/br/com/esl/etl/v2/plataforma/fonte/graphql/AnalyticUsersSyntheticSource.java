package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Declared local pages traverse the existing strict Relay parser and contract gate. No transport.
 */
public final class AnalyticUsersSyntheticSource {
    private final IntFunction<String> pages;

    public AnalyticUsersSyntheticSource(final IntFunction<String> pages) {
        this.pages = Objects.requireNonNull(pages);
    }

    public GraphQlGateway bind(
            final GraphQlContractObservationConfiguration observation,
            final ContractRunGuard guard,
            final CancellationToken cancellation) {
        if (observation.operation() != GraphQlReadOperation.USERS_SNAPSHOT) {
            throw new IllegalArgumentException("ANA_USERS_OPERATION");
        }
        final var parser = new GraphQlResponseParser(observation);
        final GraphQlGateway raw =
                new GraphQlGateway() {
                    private int page;

                    @Override
                    public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
                        cancellation.throwIfCancellationRequested();
                        if (++page > 256
                                || request.pageSize() != 20
                                || page == 1 && request.after().isPresent()
                                || page > 1
                                        && (request.after().isEmpty()
                                                || !request.after()
                                                        .orElseThrow()
                                                        .value()
                                                        .equals("synthetic-page-" + (page - 1)))) {
                            throw new IllegalArgumentException("ANA_USERS_PAGE_BOUND_OR_CURSOR");
                        }
                        final String document = pages.apply(page);
                        if (document == null
                                || document.getBytes(StandardCharsets.UTF_8).length > 65536) {
                            throw new IllegalArgumentException("ANA_USERS_BODY_BOUND");
                        }
                        return parser.parse(document, request);
                    }

                    @Override
                    public void verifyCancellationToken(final CancellationToken token) {
                        if (token != cancellation) {
                            throw new IllegalArgumentException("ANA_USERS_CANCELLATION_BINDING");
                        }
                    }
                };
        return GraphQlContractGate.enforce(raw, observation, guard, cancellation);
    }
}
