package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.security.cert.CertPathBuilderException;
import java.sql.SQLException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class PinnedSampleFailureEvidenceTest {
    private static final String SENSITIVE = "synthetic-only-must-never-escape";

    @Test
    void traversesCauseNextSqlAndSuppressedWithoutReadingTheirText() {
        final var sql = new SQLException(SENSITIVE, "08001", 0);
        final var tls = new SSLHandshakeException(SENSITIVE);
        tls.initCause(new CertPathBuilderException(SENSITIVE));
        sql.initCause(tls);
        sql.setNextException(new SQLException(SENSITIVE, "28000", 18456));
        sql.addSuppressed(new IOException(SENSITIVE));
        final var result = PinnedSampleFailureEvidence.capture(sql, null, true);
        assertTrue(result.tlsObserved());
        assertTrue(result.authObserved());
        assertEquals("CERT_PATH_BUILD_FAILURE", result.reason());
        assertEquals(5, result.nodes());
        assertEquals(1, result.nextSqlEdges());
        assertEquals(1, result.suppressedEdges());
        assertTrue(result.descriptors().contains("CODE_18456"));
        assertFalse(result.toString().contains(SENSITIVE));
        assertTrue(result.cleanupUncertain());
    }

    @Test
    void repeatedReferenceAndDeepGraphAreBounded() {
        final var one = new SQLException(SENSITIVE);
        final var two = new IOException(SENSITIVE);
        one.initCause(two);
        two.initCause(one);
        assertTrue(
                PinnedSampleFailureEvidence.capture(one, null, false).cycleOrRepeatedReference());
        Throwable head = new IOException(SENSITIVE);
        for (int n = 0; n < 32; n++) {
            head = new IOException(SENSITIVE, head);
        }
        final var bounded = PinnedSampleFailureEvidence.capture(head, null, false);
        assertEquals(16, bounded.nodes());
        assertTrue(bounded.truncated());
        assertFalse(bounded.toString().contains(SENSITIVE));
    }

    @Test
    void messagesStacksAndUnavailableInspectionCannotLeakOrHideCleanup() {
        final var unsafe =
                new SQLException(SENSITIVE, SENSITIVE, -1) {
                    private static final long serialVersionUID = 1L;

                    @Override
                    public String getMessage() {
                        throw new AssertionError("MESSAGE_READ");
                    }

                    @Override
                    public StackTraceElement[] getStackTrace() {
                        throw new AssertionError("STACK_READ");
                    }

                    @Override
                    public synchronized Throwable getCause() {
                        throw new IllegalStateException(SENSITIVE);
                    }
                };
        final var result =
                PinnedSampleFailureEvidence.capture(unsafe, new IOException(SENSITIVE), true);
        assertTrue(result.inspectionFailed());
        assertTrue(result.cleanupUncertain());
        assertTrue(result.descriptors().contains("STATE_NONE_CODE_MINUS_1"));
        assertFalse(result.toString().contains(SENSITIVE));
    }
}
