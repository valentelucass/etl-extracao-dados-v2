package br.com.esl.etl.v2.plataforma.controle;

import java.util.EnumSet;

/** Estados duráveis do control plane. Estados terminais nunca aceitam nova transição. */
public enum ExecutionState {
    PLANNED,
    EXTRACTING,
    EXTRACTED,
    STAGED,
    PROMOTED,
    RECONCILED,
    PUBLISHED,
    BLOCKED,
    SKIPPED,
    NOT_APPLICABLE,
    FAILED,
    CANCELLED,
    DEGRADED;

    public boolean isTerminal() {
        return EnumSet.of(PUBLISHED, BLOCKED, SKIPPED, NOT_APPLICABLE, FAILED, CANCELLED, DEGRADED)
                .contains(this);
    }

    public boolean canTransitionTo(final ExecutionState next) {
        if (next == null || isTerminal() || next == PUBLISHED) {
            return false;
        }
        return switch (this) {
            case PLANNED ->
                    EnumSet.of(EXTRACTING, BLOCKED, SKIPPED, NOT_APPLICABLE, FAILED, CANCELLED)
                            .contains(next);
            case EXTRACTING ->
                    EnumSet.of(EXTRACTED, BLOCKED, FAILED, CANCELLED, DEGRADED).contains(next);
            case EXTRACTED ->
                    EnumSet.of(STAGED, BLOCKED, FAILED, CANCELLED, DEGRADED).contains(next);
            // Promoção e reconciliação positivas pertencem aos procedures evidence-bound.
            case STAGED, PROMOTED ->
                    EnumSet.of(BLOCKED, FAILED, CANCELLED, DEGRADED).contains(next);
            case RECONCILED -> EnumSet.of(BLOCKED, FAILED, CANCELLED, DEGRADED).contains(next);
            default -> false;
        };
    }
}
