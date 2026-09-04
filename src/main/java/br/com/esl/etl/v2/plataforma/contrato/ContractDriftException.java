package br.com.esl.etl.v2.plataforma.contrato;

/** Falha sanitizada e fechada do gate de contrato. */
public final class ContractDriftException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    private final Reason reason;
    private final long breakingChanges;
    private final long compatibleChanges;

    public ContractDriftException(
            final Reason reason, final long breakingChanges, final long compatibleChanges) {
        super("A validação de contrato bloqueou a promoção. Motivo: " + reason + '.');
        this.reason = java.util.Objects.requireNonNull(reason, "O motivo de drift é obrigatório.");
        if (breakingChanges < 0 || compatibleChanges < 0) {
            throw new IllegalArgumentException("As contagens de drift não podem ser negativas.");
        }
        this.breakingChanges = breakingChanges;
        this.compatibleChanges = compatibleChanges;
    }

    public Reason reason() {
        return reason;
    }

    public long breakingChanges() {
        return breakingChanges;
    }

    public long compatibleChanges() {
        return compatibleChanges;
    }

    public enum Reason {
        BASELINE_MISMATCH,
        BREAKING_CHANGE,
        UNAPPROVED_COMPATIBLE_CHANGE,
        DIFF_LIMIT_EXCEEDED,
        METADATA_EVIDENCE_REQUIRED,
        RESPONSE_EVIDENCE_REQUIRED,
        TRAVERSAL_TERMINAL_EVIDENCE_REQUIRED,
        SOURCE_STATE_NOT_PROMOTABLE,
        EXECUTION_BINDING_MISMATCH,
        VALIDATION_ALREADY_TERMINAL
    }
}
