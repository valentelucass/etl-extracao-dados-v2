package br.com.esl.etl.v2.plataforma.autorizacao;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Referência opaca para auditoria. O formato limita vazamentos acidentais; a pseudonimização deve
 * ser provada pelo futuro adaptador confiável, fora deste value object.
 */
public record PrincipalAuditReference(String opaqueValue) {

    private static final Pattern SHA_256 = Pattern.compile("[a-f0-9]{64}");

    public PrincipalAuditReference {
        opaqueValue =
                Objects.requireNonNull(opaqueValue, "A referência de auditoria é obrigatória.")
                        .strip()
                        .toLowerCase(Locale.ROOT);
        if (!SHA_256.matcher(opaqueValue).matches()) {
            throw new IllegalArgumentException(
                    "A referência opaca de auditoria deve ter 256 bits em hexadecimal.");
        }
    }

    @Override
    public String toString() {
        return "PrincipalAuditReference[redacted]";
    }
}
