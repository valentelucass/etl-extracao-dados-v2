package br.com.esl.etl.v2.plataforma.controle;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Versão e fingerprint SHA-256 de um artefato imutável usado por uma ocorrência. */
public record ImmutableFingerprint(String version, String sha256) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ImmutableFingerprint {
        version = required(version, 128, "A versão do artefato é obrigatória.");
        Objects.requireNonNull(sha256, "O fingerprint do artefato é obrigatório.");
        if (sha256.length() != 64) {
            throw new IllegalArgumentException(
                    "O fingerprint do artefato deve ser SHA-256 hexadecimal.");
        }
        sha256 = sha256.toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "O fingerprint do artefato deve ser SHA-256 hexadecimal.");
        }
    }

    private static String required(
            final String value, final int maximumLength, final String message) {
        if (value == null || value.length() > maximumLength) {
            throw new IllegalArgumentException(message);
        }
        final String normalized = SqlText.trimAsciiSpace(value);
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return normalized;
    }
}
