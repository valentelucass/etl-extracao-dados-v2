package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DataExportRetryPolicyTest {

    @Test
    void capsExponentialBackoffAtConfiguredLimit() {
        final DataExportRetryPolicy policy =
                new DataExportRetryPolicy(5, Duration.ofMillis(500), Duration.ofSeconds(2));

        assertEquals(Duration.ofMillis(500), policy.delayAfterFailure(1));
        assertEquals(Duration.ofSeconds(1), policy.delayAfterFailure(2));
        assertEquals(Duration.ofSeconds(2), policy.delayAfterFailure(3));
        assertEquals(Duration.ofSeconds(2), policy.delayAfterFailure(4));
    }

    @Test
    void preservesSubMillisecondPrecisionWhileApplyingBackoff() {
        final DataExportRetryPolicy policy =
                new DataExportRetryPolicy(
                        4, Duration.ofNanos(500_000L), Duration.ofNanos(2_000_000L));

        assertEquals(Duration.ofNanos(500_000L), policy.delayAfterFailure(1));
        assertEquals(Duration.ofMillis(1), policy.delayAfterFailure(2));
        assertEquals(Duration.ofMillis(2), policy.delayAfterFailure(3));
        assertEquals(Duration.ofMillis(2), policy.delayAfterFailure(4));
    }
}
