package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RuntimeRequiredDriftAlertTest {
    @Test
    void rejectedContractEmitsOneAlertAndPreservesFailure() {
        final var failure =
                new ContractDriftException(ContractDriftException.Reason.BREAKING_CHANGE, 1, 0);
        final var alerts = new AtomicInteger();
        final var handler =
                RuntimeOperationalExecution.withRequiredDriftAlert(
                        session -> {
                            throw failure;
                        },
                        alert -> alerts.incrementAndGet());
        assertSame(
                failure, assertThrows(ContractDriftException.class, () -> handler.execute(null)));
        assertEquals(1, alerts.get());
    }

    @Test
    void mandatorySinkFailureCannotBecomeSuccessfulOrHideOriginalCause() {
        final var drift =
                new ContractDriftException(ContractDriftException.Reason.BREAKING_CHANGE, 1, 0);
        final var sink = new IllegalStateException("UNCONFIRMED");
        final var handler =
                RuntimeOperationalExecution.withRequiredDriftAlert(
                        session -> {
                            throw drift;
                        },
                        alert -> {
                            throw sink;
                        });
        final var result = assertThrows(IllegalStateException.class, () -> handler.execute(null));
        assertEquals("MANDATORY_DRIFT_ALERT_UNCONFIRMED", result.getMessage());
        assertSame(sink, result.getCause());
        assertSame(drift, sink.getSuppressed()[0]);
    }

    @Test
    void unrelatedFailureAndSuccessDoNotCreateContractAlerts() {
        final var alerts = new AtomicInteger();
        RuntimeOperationalExecution.withRequiredDriftAlert(
                        session -> {}, alert -> alerts.incrementAndGet())
                .execute(null);
        final var failure = new IllegalArgumentException("OTHER_FAILURE");
        final var handler =
                RuntimeOperationalExecution.withRequiredDriftAlert(
                        session -> {
                            throw failure;
                        },
                        alert -> alerts.incrementAndGet());
        assertSame(
                failure, assertThrows(IllegalArgumentException.class, () -> handler.execute(null)));
        assertEquals(0, alerts.get());
    }
}
