package br.com.esl.etl.v2.modulos.fretes.domain;

import java.time.Instant;
import java.util.Objects;

/** Evidência temporal unitária com bruto, presença, parse state e path. */
public record FreteTemporalValue(
        FreteAttributePresence presence,
        String sourcePath,
        String rawJson,
        ParseState parseState,
        Instant instantUtc) {

    public FreteTemporalValue {
        presence = Objects.requireNonNull(presence, "A presença temporal é obrigatória.");
        sourcePath = requiredPath(sourcePath);
        parseState = Objects.requireNonNull(parseState, "O parse state é obrigatório.");
        if (presence == FreteAttributePresence.VALUE) {
            if (rawJson == null
                    || (parseState == ParseState.VALID) != (instantUtc != null)
                    || parseState == ParseState.NOT_PRESENT
                    || parseState == ParseState.EXPLICIT_NULL) {
                throw new IllegalArgumentException("Evidência temporal VALUE incoerente.");
            }
        } else if (rawJson != null
                || instantUtc != null
                || (presence == FreteAttributePresence.ABSENT
                        && parseState != ParseState.NOT_PRESENT)
                || (presence == FreteAttributePresence.NULL
                        && parseState != ParseState.EXPLICIT_NULL)) {
            throw new IllegalArgumentException("Evidência temporal sem valor incoerente.");
        }
    }

    public static FreteTemporalValue absent(final String path) {
        return new FreteTemporalValue(
                FreteAttributePresence.ABSENT, path, null, ParseState.NOT_PRESENT, null);
    }

    public static FreteTemporalValue explicitNull(final String path) {
        return new FreteTemporalValue(
                FreteAttributePresence.NULL, path, null, ParseState.EXPLICIT_NULL, null);
    }

    public static FreteTemporalValue valid(
            final String path, final String rawJson, final Instant instantUtc) {
        return new FreteTemporalValue(
                FreteAttributePresence.VALUE,
                path,
                rawJson,
                ParseState.VALID,
                Objects.requireNonNull(instantUtc, "O instante tipado é obrigatório."));
    }

    public static FreteTemporalValue invalid(final String path, final String rawJson) {
        return new FreteTemporalValue(
                FreteAttributePresence.VALUE,
                path,
                Objects.requireNonNull(rawJson, "O valor bruto inválido é obrigatório."),
                ParseState.INVALID,
                null);
    }

    private static String requiredPath(final String value) {
        if (value == null || !value.matches("/[a-z][a-z0-9_]*")) {
            throw new IllegalArgumentException("O path temporal deve ser canônico.");
        }
        return value;
    }

    public enum ParseState {
        NOT_PRESENT,
        EXPLICIT_NULL,
        VALID,
        INVALID
    }
}
