package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.aplicacao.ColetaTemporalReferenceStore;
import br.com.esl.etl.v2.modulos.coletas.aplicacao.PersistirReferenciasTemporaisColetas;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalIdentityBinding;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaTemporalObservation;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationSignal;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PersistirReferenciasTemporaisColetasTest {
    private static final GraphQlReadOperation OPERATION =
            GraphQlReadOperation.PICKS_TEMPORAL_REFERENCE;
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-09-10T20:00:00Z"), ZoneOffset.UTC);
    private static final String NODE =
            "{\"id\":17,\"status\":\"pending\",\"requestDate\":\"2026-09-09\","
                    + "\"statusUpdatedAt\":\"2026-09-09T19:00:00Z\"}";

    @ParameterizedTest
    @ValueSource(strings = {"success", "pageFailure", "storeFailure", "cancel", "sealFailure"})
    void stagesSynchronouslyAndSealsOnlySuccessfulTraversal(final String mode) {
        final var fixture =
                GraphQlTestSupport.fixture(
                        OPERATION, GraphQlTestSupport.page(OPERATION, false, null, NODE));
        final var parser = new GraphQlResponseParser(fixture.configuration());
        final var signal = new CancellationSignal();
        final var calls = new AtomicInteger();
        final var store = new Store(mode, signal);
        final GraphQlGateway upstream =
                request -> {
                    final int page = calls.incrementAndGet();
                    if (page == 2) {
                        assertEquals(1, store.staged);
                        assertFalse(store.complete);
                        if (mode.equals("pageFailure")) {
                            throw new IllegalStateException("synthetic-page-failure");
                        }
                    }
                    return parser.parse(
                            "{\"data\":{\"pick\":{\"edges\":[{\"node\":"
                                    + NODE
                                    + "}],\"pageInfo\":{\"hasNextPage\":"
                                    + (page == 1)
                                    + ",\"endCursor\":"
                                    + (page == 1 ? "\"synthetic-cursor\"" : "null")
                                    + "}}}}",
                            request);
                };
        final var secured =
                GraphQlContractGate.enforce(
                        GraphQlTestSupport.bound(signal, upstream),
                        fixture.configuration(),
                        fixture.guard(),
                        signal);
        final var useCase =
                new PersistirReferenciasTemporaisColetas(
                        new GraphQlPageStreamer(secured, GraphQlExtractionAudit.noop(), CLOCK),
                        store,
                        CLOCK);
        final Runnable execute =
                () ->
                        useCase.execute(
                                fixture.guard().executionContext(),
                                "SYNTHETIC_SOURCE",
                                "SYNTHETIC_TENANT",
                                LocalDate.of(2026, 9, 9),
                                new GraphQlExtractionLimits(2, 40),
                                signal);
        if (mode.equals("success")) {
            execute.run();
            assertEquals(2, store.staged);
            assertTrue(store.complete);
        } else {
            assertThrows(RuntimeException.class, execute::run);
            assertFalse(store.complete);
        }
        assertEquals(mode.equals("success") || mode.equals("sealFailure") ? 1 : 0, store.sealCalls);
        assertThrows(ContractDriftException.class, () -> fixture.guard().complete());
    }

    private static final class Store implements ColetaTemporalReferenceStore {
        private final String mode;
        private final CancellationSignal signal;
        private int staged;
        private int sealCalls;
        private boolean complete;

        private Store(final String mode, final CancellationSignal signal) {
            this.mode = mode;
            this.signal = signal;
        }

        @Override
        public void stage(
                final ColetaTemporalObservation observation, final CancellationToken cancellation) {
            assertEquals(staged + 1, observation.pageNumber());
            assertEquals(1, observation.inputOrdinal());
            staged++;
            if (mode.equals("storeFailure")) {
                throw new IllegalStateException("synthetic-store-failure");
            }
            if (mode.equals("cancel")) {
                signal.cancel();
            }
        }

        @Override
        public void complete(
                final GraphQlExtractionResult result, final CancellationToken cancellation) {
            sealCalls++;
            assertEquals(staged, result.nodesDelivered());
            assertEquals(2, result.pagesFetched());
            assertFalse(result.traversalVerification().provesCoverageOrSnapshot());
            if (mode.equals("sealFailure")) {
                throw new IllegalStateException("synthetic-seal-failure");
            }
            complete = true;
        }

        @Override
        public void bind(
                final ColetaTemporalIdentityBinding binding, final CancellationToken cancellation) {
            throw new AssertionError("A captura não cria correspondências.");
        }

        @Override
        public Qualification qualify(
                final UUID data, final UUID reference, final CancellationToken cancellation) {
            throw new AssertionError("A captura não qualifica nem promove dados.");
        }
    }
}
