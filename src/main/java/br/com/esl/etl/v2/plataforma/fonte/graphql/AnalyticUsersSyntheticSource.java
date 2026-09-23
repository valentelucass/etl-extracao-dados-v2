package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.fonte.SyntheticCaptureObserver;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.IntFunction;

/**
 * Declared local pages traverse the existing strict Relay parser and contract gate. No transport.
 */
public final class AnalyticUsersSyntheticSource implements AnalyticUsersCaptureSource {
    private final IntFunction<String> pages;
    private final SyntheticCaptureObserver observer;
    private final boolean declaredCursors;

    public AnalyticUsersSyntheticSource(final IntFunction<String> pages) {
        this(pages, SyntheticCaptureObserver.NONE);
    }

    private AnalyticUsersSyntheticSource(
            final IntFunction<String> pages, final SyntheticCaptureObserver observer) {
        this(pages, observer, false);
    }

    private AnalyticUsersSyntheticSource(
            final IntFunction<String> pages,
            final SyntheticCaptureObserver observer,
            final boolean declaredCursors) {
        this.pages = Objects.requireNonNull(pages);
        this.observer = Objects.requireNonNull(observer);
        this.declaredCursors = declaredCursors;
    }

    /** Uses the cursor actually returned by each declared page, with no positional identity. */
    public static AnalyticUsersCaptureSource declared(final IntFunction<String> pages) {
        return new AnalyticUsersSyntheticSource(pages, SyntheticCaptureObserver.NONE, true);
    }

    public AnalyticUsersSyntheticSource observed(final SyntheticCaptureObserver value) {
        return new AnalyticUsersSyntheticSource(pages, value, declaredCursors);
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
                    private java.util.Optional<GraphQlCursor> previousCursor =
                            java.util.Optional.empty();

                    @Override
                    public GraphQlPageResponse fetch(final GraphQlPageRequest request) {
                        cancellation.throwIfCancellationRequested();
                        observer.beforeFetch();
                        if (++page > 256
                                || request.pageSize() != 20
                                || page == 1 && request.after().isPresent()
                                || declaredCursors && !request.after().equals(previousCursor)
                                || !declaredCursors
                                        && page > 1
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
                        final var result = parser.parse(document, request);
                        previousCursor = result.endCursor();
                        observer.pageBytes(document.getBytes(StandardCharsets.UTF_8).length);
                        return result;
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
