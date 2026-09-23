package br.com.esl.etl.v2.plataforma.fonte;

/** Explicit local source/tenant pair; legacy LOCAL_V2 is retained for packaged examples. */
public record SyntheticSourceScope(String source, String tenant) {
    public SyntheticSourceScope {
        if (!valid(source) || !valid(tenant)) {
            throw new IllegalArgumentException("LOCAL_SYNTHETIC_SOURCE_SCOPE");
        }
    }

    private static boolean valid(final String value) {
        return value != null
                && (value.equals("LOCAL_V2") || value.matches("SYNTHETIC_[A-Z0-9_]{1,30}"));
    }
}
