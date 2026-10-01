package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class QualificationWorkerFailureEvidenceTest {
    @Test
    void recoveryFailureReportsNestedSqlNumberWithoutDriverText() {
        final var report = JsonNodeFactory.instance.objectNode();
        final var sql = new SQLException("row payload must remain private", "S0001", 53721);
        final var capture =
                new SQLException(
                        "ANA_QUOTE_CAPTURE_RECOVERY_REQUIRED",
                        new IllegalStateException("ANA_QUOTE_BEGIN", sql));

        QualificationWorker.failureEvidence(report, capture);

        assertEquals("SQLException", report.path("failureClass").asText());
        assertEquals("ANA_QUOTE_CAPTURE_RECOVERY_REQUIRED", report.path("failureCode").asText());
        assertEquals("ANA_QUOTE_BEGIN", report.path("failureCauseCode").asText());
        assertEquals(53721, report.path("failureSqlNumber").asInt());
        assertFalse(report.toString().contains("row payload"));
    }

    @Test
    void unsafeCauseTextAndMissingSqlNumberStayOutOfReceipt() {
        final var report = JsonNodeFactory.instance.objectNode();
        final var capture =
                new SQLException(
                        "ANA_QUOTE_CAPTURE_RECOVERY_REQUIRED",
                        new IllegalStateException(
                                "private value", new SQLException("driver detail")));

        QualificationWorker.failureEvidence(report, capture);

        assertFalse(report.has("failureCauseCode"));
        assertFalse(report.has("failureSqlNumber"));
        assertFalse(report.toString().contains("private value"));
        assertFalse(report.toString().contains("driver detail"));
    }
}
