package br.com.esl.etl.v2.bootstrap;

import com.fasterxml.jackson.databind.node.ObjectNode;

/** Preserves completed stages when a later stage fails; failure remains a failed execution. */
final class SequenceExecutionFailure extends Exception {
    private static final long serialVersionUID = 1L;
    private final ObjectNode report;

    SequenceExecutionFailure(final Exception cause, final ObjectNode report) {
        super("SEQUENCE_STAGE_EXECUTION_FAILED", cause);
        this.report = report.deepCopy();
    }

    ObjectNode report() {
        return report.deepCopy();
    }
}
