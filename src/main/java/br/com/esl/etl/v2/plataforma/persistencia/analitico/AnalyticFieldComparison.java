package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Length-prefixed, bounded per observation comparison; no ambiguous delimiter serialization. */
final class AnalyticFieldComparison {
    private final ByteArrayOutputStream output = new ByteArrayOutputStream();

    void value(final ExpansionValue<?> value) {
        text(value.presence().name());
        text(value.wire().name());
        text(value.raw());
        text(value.issue());
        text(value.value() == null ? null : value.value().toString());
    }

    void flag(final Boolean value) {
        text(value == null ? null : value.toString());
    }

    void text(final String value) {
        final byte[] bytes = value == null ? new byte[0] : value.getBytes(StandardCharsets.UTF_8);
        final int length = value == null ? -1 : bytes.length;
        output.write(length >>> 24);
        output.write(length >>> 16);
        output.write(length >>> 8);
        output.write(length);
        output.writeBytes(bytes);
        if (output.size() > 256 * 1024) {
            throw new IllegalArgumentException("ANA_COMPARISON_BOUND");
        }
    }

    byte[] bytesLimited(final int maximum) {
        if (maximum < 1 || maximum > 256 * 1024 || output.size() > maximum) {
            throw new IllegalArgumentException("ANA_COMPARISON_BOUND");
        }
        return output.toByteArray();
    }
}
