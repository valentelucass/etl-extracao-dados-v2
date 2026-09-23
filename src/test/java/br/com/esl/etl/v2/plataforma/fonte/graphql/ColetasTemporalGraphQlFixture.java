package br.com.esl.etl.v2.plataforma.fonte.graphql;

import br.com.esl.etl.v2.plataforma.contrato.ContractRunGuard;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/** Test-only source; uses the unchanged observational contract, parser, gate and streamer. */
public record ColetasTemporalGraphQlFixture(ContractRunGuard guard, GraphQlPageStreamer streamer) {
    public static ColetasTemporalGraphQlFixture create(
            final List<String> pages, final Clock clock, final CancellationToken cancellation) {
        final var operation = GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
        final var fixture =
                GraphQlTestSupport.fixture(
                        operation,
                        GraphQlTestSupport.page(
                                operation,
                                false,
                                null,
                                "{\"id\":\"synthetic-17\",\"status\":\"pending\",\"requestDate\":\"2036-01-20\","
                                        + "\"statusUpdatedAt\":\"2036-01-20T10:00:00Z\"}"));
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var index = new AtomicInteger();
        final GraphQlGateway upstream =
                request -> {
                    final int next = index.getAndIncrement();
                    if (next >= pages.size()) {
                        throw new IllegalStateException("SYNTHETIC_REFERENCE_INTERRUPTED");
                    }
                    return parser.parse(pages.get(next), request);
                };
        final var guarded =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(cancellation, upstream),
                        fixture.configuration(),
                        fixture.guard(),
                        cancellation);
        return new ColetasTemporalGraphQlFixture(
                fixture.guard(),
                new GraphQlPageStreamer(guarded, GraphQlExtractionAudit.noop(), clock));
    }
}
