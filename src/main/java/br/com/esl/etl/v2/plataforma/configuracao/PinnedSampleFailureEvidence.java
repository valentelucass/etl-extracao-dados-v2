package br.com.esl.etl.v2.plataforma.configuracao;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.security.cert.CertPathBuilderException;
import java.security.cert.CertPathValidatorException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.sql.SQLException;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLProtocolException;

/**
 * Bounded exception graph projection. No messages, stacks, raw exceptions or peer values escape.
 */
public record PinnedSampleFailureEvidence(
        String descriptors,
        String reason,
        int nodes,
        int nextSqlEdges,
        int suppressedEdges,
        boolean tlsObserved,
        boolean authObserved,
        boolean cycleOrRepeatedReference,
        boolean truncated,
        boolean inspectionFailed,
        boolean cleanupUncertain) {
    private static final int LIMIT = 16;

    public static PinnedSampleFailureEvidence capture(
            final Throwable failure, final Throwable cleanup, final boolean cleanupUncertain) {
        final Throwable[] pending = new Throwable[LIMIT];
        final Throwable[] seen = new Throwable[LIMIT];
        int size = 0;
        int count = 0;
        int nextEdges = 0;
        int suppressedEdges = 0;
        int priority = 0;
        String reason = "NO_TYPED_TLS_CAUSE";
        boolean tls = false;
        boolean auth = false;
        boolean repeated = false;
        boolean truncated = false;
        boolean inspectionFailed = false;
        final var text = new StringBuilder();
        if (failure != null) {
            pending[size++] = failure;
        }
        if (cleanup != null) {
            pending[size++] = cleanup;
        }
        try {
            while (size > 0 && count < LIMIT) {
                final Throwable current = pending[--size];
                pending[size] = null;
                boolean duplicate = false;
                for (int index = 0; index < count; index++) {
                    duplicate |= seen[index] == current;
                }
                if (duplicate) {
                    repeated = true;
                    continue;
                }
                seen[count++] = current;
                if (text.length() > 0) {
                    text.append('_');
                }
                text.append(kind(current));
                tls |=
                        current instanceof SSLException
                                || current instanceof CertificateException
                                || current instanceof CertPathBuilderException
                                || current instanceof CertPathValidatorException;
                final int candidatePriority = priority(current);
                if (candidatePriority > priority) {
                    priority = candidatePriority;
                    reason = reason(current);
                }
                try {
                    if (current instanceof SQLException sql) {
                        final String state = sql.getSQLState();
                        text.append("_STATE_")
                                .append(
                                        state != null && state.matches("[A-Z0-9]{5}")
                                                ? state
                                                : "NONE")
                                .append("_CODE_")
                                .append(sql.getErrorCode() < 0 ? "MINUS_" : "")
                                .append(Math.abs((long) sql.getErrorCode()));
                        auth |= sql.getErrorCode() == 18456;
                        final SQLException next = sql.getNextException();
                        if (next != null) {
                            nextEdges++;
                            if (size < LIMIT) {
                                pending[size++] = next;
                            } else {
                                truncated = true;
                            }
                        }
                    }
                    final Throwable cause = current.getCause();
                    if (cause != null) {
                        if (size < LIMIT) {
                            pending[size++] = cause;
                        } else {
                            truncated = true;
                        }
                    }
                    final Throwable[] suppressed = current.getSuppressed();
                    suppressedEdges += Math.min(suppressed.length, LIMIT);
                    if (suppressed.length > LIMIT) {
                        truncated = true;
                    }
                    for (int index = 0; index < Math.min(suppressed.length, LIMIT); index++) {
                        if (size < LIMIT) {
                            pending[size++] = suppressed[index];
                        } else {
                            truncated = true;
                        }
                    }
                } catch (final RuntimeException | Error unavailable) {
                    inspectionFailed = true;
                }
            }
            truncated |= size > 0;
            return new PinnedSampleFailureEvidence(
                    text.length() == 0 ? "NONE" : text.toString(),
                    reason,
                    count,
                    nextEdges,
                    suppressedEdges,
                    tls,
                    auth,
                    repeated,
                    truncated,
                    inspectionFailed,
                    cleanupUncertain);
        } finally {
            java.util.Arrays.fill(pending, null);
            java.util.Arrays.fill(seen, null);
        }
    }

    private static int priority(final Throwable value) {
        if (value instanceof CertificateExpiredException
                || value instanceof CertificateNotYetValidException) {
            return 100;
        }
        if (value instanceof CertPathValidatorException) {
            return 95;
        }
        if (value instanceof CertPathBuilderException) {
            return 90;
        }
        if (value instanceof CertificateException) {
            return 80;
        }
        if (value instanceof SocketTimeoutException || value instanceof ConnectException) {
            return 75;
        }
        if (value instanceof SSLProtocolException || value instanceof SSLPeerUnverifiedException) {
            return 70;
        }
        if (value instanceof SSLHandshakeException) {
            return 60;
        }
        return value instanceof SSLException ? 50 : 0;
    }

    private static String reason(final Throwable value) {
        if (value instanceof CertificateExpiredException) {
            return "CERTIFICATE_EXPIRED";
        }
        if (value instanceof CertificateNotYetValidException) {
            return "CERTIFICATE_NOT_YET_VALID";
        }
        if (value instanceof CertPathValidatorException) {
            return "CERT_PATH_VALIDATION_FAILURE";
        }
        if (value instanceof CertPathBuilderException) {
            return "CERT_PATH_BUILD_FAILURE";
        }
        if (value instanceof CertificateException) {
            return "CERTIFICATE_EXCEPTION";
        }
        if (value instanceof SocketTimeoutException) {
            return "SOCKET_TIMEOUT";
        }
        if (value instanceof ConnectException) {
            return "SOCKET_CONNECT_FAILURE";
        }
        if (value instanceof SSLProtocolException) {
            return "TLS_PROTOCOL_FAILURE";
        }
        if (value instanceof SSLPeerUnverifiedException) {
            return "TLS_PEER_UNVERIFIED";
        }
        if (value instanceof SSLHandshakeException) {
            return "TLS_HANDSHAKE_FAILURE";
        }
        return "TLS_EXCEPTION";
    }

    private static String kind(final Throwable value) {
        if (value instanceof SQLException) {
            return "SQL_EXCEPTION";
        }
        if (value instanceof SSLProtocolException) {
            return "SSL_PROTOCOL";
        }
        if (value instanceof SSLPeerUnverifiedException) {
            return "SSL_PEER_UNVERIFIED";
        }
        if (value instanceof SSLHandshakeException) {
            return "SSL_HANDSHAKE";
        }
        if (value instanceof SSLException) {
            return "SSL_EXCEPTION";
        }
        if (value instanceof CertPathBuilderException) {
            return "CERT_PATH_BUILDER";
        }
        if (value instanceof CertPathValidatorException) {
            return "CERT_PATH_VALIDATOR";
        }
        if (value instanceof CertificateExpiredException) {
            return "CERT_EXPIRED";
        }
        if (value instanceof CertificateNotYetValidException) {
            return "CERT_NOT_YET_VALID";
        }
        if (value instanceof CertificateException) {
            return "CERTIFICATE_EXCEPTION";
        }
        if (value instanceof SocketTimeoutException) {
            return "SOCKET_TIMEOUT";
        }
        if (value instanceof ConnectException) {
            return "SOCKET_CONNECT";
        }
        if (value instanceof java.io.IOException) {
            return "IO_EXCEPTION";
        }
        if (value instanceof LinkageError) {
            return "LINKAGE_ERROR";
        }
        return "OTHER";
    }
}
