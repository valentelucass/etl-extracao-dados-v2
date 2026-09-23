package br.com.esl.etl.v2.plataforma.reconciliacao.sweep;

import java.util.Objects;

/** Resultado puro; inclusive o caso positivo não carrega capacidade de mutação. */
public record SweepPreviewAssessment(Disposition disposition, SweepPreviewBlockReason reason) {
    public enum Disposition {
        BLOCKED,
        PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY
    }

    public SweepPreviewAssessment {
        Objects.requireNonNull(disposition, "A disposição é obrigatória.");
        Objects.requireNonNull(reason, "O motivo é obrigatório.");
        final boolean positive = disposition == Disposition.PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY;
        final boolean positiveReason =
                reason == SweepPreviewBlockReason.NONE_PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY;
        if (positive != positiveReason) {
            throw new IllegalArgumentException("Disposição e motivo são incoerentes.");
        }
    }

    public boolean previewEligible() {
        return disposition == Disposition.PREVIEW_ELIGIBLE_NO_APPLY_CAPABILITY;
    }
}
