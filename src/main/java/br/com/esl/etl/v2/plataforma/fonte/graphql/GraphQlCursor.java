package br.com.esl.etl.v2.plataforma.fonte.graphql;

import java.util.Objects;

/** Cursor opaco, limitado e deliberadamente redigido. */
public final class GraphQlCursor {

    public static final int MAXIMUM_UTF8_BYTES = 8_192;

    private final String value;

    private GraphQlCursor(final String value) {
        this.value = value;
    }

    static GraphQlCursor observed(final String value) {
        Objects.requireNonNull(value, "O cursor GraphQL é obrigatório.");
        int utf8Bytes = 0;
        boolean hasNonWhitespace = false;
        for (int index = 0; index < value.length(); index++) {
            final char character = value.charAt(index);
            final int codePoint;
            if (Character.isHighSurrogate(character)) {
                if (index + 1 >= value.length()
                        || !Character.isLowSurrogate(value.charAt(index + 1))) {
                    throw invalidCursor();
                }
                codePoint = Character.toCodePoint(character, value.charAt(++index));
            } else if (Character.isLowSurrogate(character)) {
                throw invalidCursor();
            } else {
                codePoint = character;
            }
            if (Character.isISOControl(codePoint)) {
                throw invalidCursor();
            }
            hasNonWhitespace |= !Character.isWhitespace(codePoint);
            utf8Bytes +=
                    codePoint <= 0x7f ? 1 : codePoint <= 0x7ff ? 2 : codePoint <= 0xffff ? 3 : 4;
            if (utf8Bytes > MAXIMUM_UTF8_BYTES) {
                throw invalidCursor();
            }
        }
        if (!hasNonWhitespace) {
            throw invalidCursor();
        }
        return new GraphQlCursor(value);
    }

    private static IllegalArgumentException invalidCursor() {
        return new IllegalArgumentException("O cursor GraphQL observado é inválido.");
    }

    String value() {
        return value;
    }

    @Override
    public boolean equals(final Object other) {
        return other instanceof GraphQlCursor cursor && value.equals(cursor.value);
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

    @Override
    public String toString() {
        return "GraphQlCursor[<redacted>]";
    }
}
