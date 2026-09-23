package br.com.esl.etl.v2.plataforma.analitico;

import java.util.Objects;
import java.util.UUID;

/** Versioned laboratory envelope, separate from the provider payload. */
public record AnalyticManifestState(
        String sourceKey, UUID sourceExecution, int revision, boolean active, boolean reactivate) {
    public AnalyticManifestState {
        Objects.requireNonNull(sourceExecution);
        if (sourceKey == null
                || !sourceKey.matches("INTEGER:[1-9][0-9]{0,18}")
                || revision < 1
                || revision > 100000
                || (reactivate && !active)) {
            throw new IllegalArgumentException("ANA_MANIFEST_STATE_CONTRACT");
        }
        try {
            Long.parseLong(sourceKey.substring(8));
        } catch (final NumberFormatException failure) {
            throw new IllegalArgumentException("ANA_MANIFEST_STATE_KEY_RANGE", failure);
        }
    }
}
