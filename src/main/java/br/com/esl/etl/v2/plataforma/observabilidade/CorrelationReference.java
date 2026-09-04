package br.com.esl.etl.v2.plataforma.observabilidade;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/** Referência opaca de correlação; logs nunca recebem o identificador original. */
public record CorrelationReference(String sha256) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public CorrelationReference {
        sha256 = Objects.requireNonNull(sha256, "A referência de correlação é obrigatória.");
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException("A referência de correlação é inválida.");
        }
    }

    public static CorrelationReference fromExecutionId(final UUID executionId) {
        Objects.requireNonNull(executionId, "O execution_id é obrigatório.");
        return fromTechnicalScope(executionId.toString());
    }

    public static CorrelationReference fromTechnicalScope(final String technicalScope) {
        Objects.requireNonNull(technicalScope, "O escopo técnico é obrigatório.");
        if (technicalScope.isBlank() || technicalScope.length() > 256) {
            throw new IllegalArgumentException("O escopo técnico é inválido.");
        }
        try {
            final byte[] digest =
                    MessageDigest.getInstance("SHA-256")
                            .digest(technicalScope.getBytes(StandardCharsets.UTF_8));
            return new CorrelationReference(HexFormat.of().formatHex(digest));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", exception);
        }
    }

    @Override
    public String toString() {
        return "CorrelationReference[opaque]";
    }
}
