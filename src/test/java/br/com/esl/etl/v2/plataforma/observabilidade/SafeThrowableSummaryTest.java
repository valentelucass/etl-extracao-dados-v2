package br.com.esl.etl.v2.plataforma.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.sql.SQLTimeoutException;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

class SafeThrowableSummaryTest {

    @Test
    void classifiesWithoutCopyingMessagesOrStacks() {
        final SQLException sql = new SQLException("sensitive", "42000", 1234);
        sql.initCause(new IllegalStateException("also-sensitive"));

        final SafeThrowableSummary summary = SafeThrowableSummary.from(sql, 1);

        assertEquals("SQL_FAILURE", summary.categoryCode());
        assertEquals("42", summary.sqlStateClass());
        assertEquals(1234, summary.vendorCode());
        assertEquals(1, summary.observedCauseDepth());
        assertTrue(summary.capped());
        assertFalse(summary.toString().contains("sensitive"));

        assertEquals(
                "TIMEOUT",
                SafeThrowableSummary.from(new TimeoutException("secret"), 2).categoryCode());
        assertEquals(
                "TIMEOUT",
                SafeThrowableSummary.from(new SQLTimeoutException("secret", "HYT00", 0), 2)
                        .categoryCode());
        assertEquals(
                "CANCELLED",
                SafeThrowableSummary.from(new CancellationException("secret"), 2).categoryCode());
        assertEquals(
                "RUNTIME_FAILURE",
                SafeThrowableSummary.from(new IllegalStateException("secret"), 2).categoryCode());
        assertNull(
                SafeThrowableSummary.from(new SQLException("secret", "invalid", 0), 2)
                        .sqlStateClass());
    }

    @Test
    void validatesCauseDepthAndClosedCodes() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SafeThrowableSummary.from(new IllegalStateException(), 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SafeThrowableSummary("bad", null, 0, 1, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SafeThrowableSummary("SQL_FAILURE", "420", 0, 1, false));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SafeThrowableSummary("SQL_FAILURE", null, 0, 0, false));
    }
}
