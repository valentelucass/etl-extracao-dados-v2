package br.com.esl.etl.v2.plataforma.fonte.graphql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.controle.ControlPlane;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GraphQlOperationalAuditTest {
    private static final Instant NOW = Instant.parse("2036-03-19T18:42:10Z");
    private static final UUID ID = UUID.fromString("00000000-0000-0000-0000-000000005901");

    @Test
    void refusesFabricatedCompletionWithoutAnyPage() {
        final var calls = new AtomicInteger();
        final var audit = audit(calls);
        audit.executionStarted(start());
        assertThrows(IllegalStateException.class, () -> audit.executionCompleted(result(1, 1)));
        assertEquals(0, calls.get());
    }

    @Test
    void refusesForeignExecutionBeforePersistingPage() {
        final var calls = new AtomicInteger();
        final var audit = audit(calls);
        audit.executionStarted(start());
        assertThrows(
                IllegalStateException.class,
                () ->
                        audit.pageRead(
                                new GraphQlExtractionAudit.PageRead(
                                        UUID.randomUUID(),
                                        GraphQlReadOperation.USERS_SNAPSHOT,
                                        1,
                                        20,
                                        1,
                                        1,
                                        80,
                                        false,
                                        NOW)));
        assertEquals(0, calls.get());
    }

    @Test
    void refusesIncompatibleCountsAfterTerminalPage() {
        final var calls = new AtomicInteger();
        final var audit = audit(calls);
        audit.executionStarted(start());
        audit.pageRead(
                new GraphQlExtractionAudit.PageRead(
                        ID, GraphQlReadOperation.USERS_SNAPSHOT, 1, 20, 2, 2, 80, false, NOW));
        assertThrows(IllegalStateException.class, () -> audit.executionCompleted(result(1, 1)));
        assertEquals(1, calls.get());
    }

    private static GraphQlExtractionAudit.ExecutionStarted start() {
        return new GraphQlExtractionAudit.ExecutionStarted(
                ID, GraphQlReadOperation.USERS_SNAPSHOT, 20, 4, 80, NOW);
    }

    private static GraphQlExtractionResult result(final int pages, final long nodes) {
        return new GraphQlExtractionResult(
                ID,
                GraphQlReadOperation.USERS_SNAPSHOT,
                pages,
                nodes,
                NOW,
                NOW,
                GraphQlTraversalVerification.LOCAL_PAGE_INFO_TERMINAL_UNVERIFIED);
    }

    private static ControlPlaneGraphQlExtractionAudit audit(final AtomicInteger calls) {
        return new ControlPlaneGraphQlExtractionAudit(
                (ControlPlane)
                        Proxy.newProxyInstance(
                                ControlPlane.class.getClassLoader(),
                                new Class<?>[] {ControlPlane.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("recordPage")) {
                                        calls.incrementAndGet();
                                    }
                                    return null;
                                }));
    }
}
