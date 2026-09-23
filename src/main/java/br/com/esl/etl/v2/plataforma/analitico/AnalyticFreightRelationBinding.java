package br.com.esl.etl.v2.plataforma.analitico;

import java.util.Objects;
import java.util.UUID;

/** Captured endpoints in distinct namespaces; numeric equality is not an identity proof. */
public record AnalyticFreightRelationBinding(
        Kind kind,
        String originKey,
        UUID originExecution,
        String freightKey,
        UUID freightExecution,
        int revision,
        boolean active,
        String previousFreightKey) {
    public AnalyticFreightRelationBinding {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(originExecution);
        Objects.requireNonNull(freightExecution);
        if (!validKey(originKey)
                || !validKey(freightKey)
                || revision < 1
                || revision > 100000
                || previousFreightKey != null && !validKey(previousFreightKey)
                || kind == Kind.DIRECT && previousFreightKey != null) {
            throw new IllegalArgumentException("ANA_FREIGHT_RELATION_BINDING");
        }
    }

    private static boolean validKey(final String key) {
        if (key == null || !key.matches("INTEGER:[1-9][0-9]{0,18}")) {
            return false;
        }
        try {
            return Long.parseLong(key.substring(8)) > 0;
        } catch (final NumberFormatException failure) {
            return false;
        }
    }

    public enum Kind {
        CROSSWALK,
        DIRECT
    }
}
