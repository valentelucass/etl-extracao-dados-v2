package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.bootstrap.RuntimeUsersPhysicalEntry;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeUsersPhysicalEntryTest {
    @Test
    void lostConsumptionConfirmationIsClassifiedWithoutRetryOrSensitiveCause() {
        final var calls = new AtomicInteger();
        final var messages = new ArrayList<String>();
        final var result =
                RuntimeUsersPhysicalEntry.execute(
                        () -> {
                            calls.incrementAndGet();
                            throw new DurableAuthorizationException(
                                    DurableAuthorizationException.Reason.AUTHORITY_UNAVAILABLE,
                                    new IllegalStateException("private-diagnostic"));
                        },
                        messages::add);
        assertEquals(RuntimeExitCategory.CONFIG_AUTH, result);
        assertEquals(20, result.code());
        assertEquals(1, calls.get());
        assertEquals(
                java.util.List.of("Autorização operacional recusada: AUTHORITY_UNAVAILABLE"),
                messages);
    }

    @Test
    void preservesOperationalResultWithoutAddingDiagnostics() {
        final var messages = new ArrayList<String>();
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                RuntimeUsersPhysicalEntry.execute(
                        () -> RuntimeExitCategory.SOURCE_DQ, messages::add));
        assertTrue(messages.isEmpty());
    }

    @Test
    void unrelatedFailuresRemainFailures() {
        final var failure = new IllegalStateException("unrelated");
        assertSame(
                failure,
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                RuntimeUsersPhysicalEntry.execute(
                                        () -> {
                                            throw failure;
                                        },
                                        ignored -> {})));
    }
}
