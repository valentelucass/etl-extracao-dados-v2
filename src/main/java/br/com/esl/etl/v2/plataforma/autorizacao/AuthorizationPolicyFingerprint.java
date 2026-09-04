package br.com.esl.etl.v2.plataforma.autorizacao;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Impressão digital de uma política compilada, sem transportar sua origem ou seus membros. */
public record AuthorizationPolicyFingerprint(String sha256) {

    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public AuthorizationPolicyFingerprint {
        sha256 =
                Objects.requireNonNull(sha256, "A impressão da política é obrigatória.")
                        .strip()
                        .toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "A impressão da política deve ser um SHA-256 hexadecimal.");
        }
    }

    @Override
    public String toString() {
        return "AuthorizationPolicyFingerprint[redacted]";
    }
}
