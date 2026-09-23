package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttempt;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportHttpAttemptObserver;

/**
 * Actual attempts, bounded per invocation. No inferred successful pages, retries or source
 * watermark.
 */
final class RuntimeHttpAttempts implements DataExportHttpAttemptObserver {
    private final int maximum;
    private int metadata;
    private int data;

    RuntimeHttpAttempts(final int maximum) {
        if (maximum < 1 || maximum > 100000) {
            throw new IllegalArgumentException("HTTP_ATTEMPT_BOUND");
        }
        this.maximum = maximum;
    }

    @Override
    public synchronized void beforeAttempt(final DataExportHttpAttempt attempt) {
        if (metadata + data >= maximum) {
            throw new IllegalStateException("HTTP_ATTEMPT_LIMIT");
        }
        if ("info".equals(attempt.operation())) {
            metadata++;
        } else if (attempt.operation().startsWith("data-")) {
            data++;
        } else {
            throw new IllegalArgumentException("HTTP_ATTEMPT_OPERATION");
        }
    }

    synchronized String summary() {
        return "RUNTIME_HTTP_ATTEMPTS metadata="
                + metadata
                + " data="
                + data
                + " total="
                + (metadata + data);
    }
}
