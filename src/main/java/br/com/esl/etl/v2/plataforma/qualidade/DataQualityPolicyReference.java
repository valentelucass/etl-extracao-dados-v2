package br.com.esl.etl.v2.plataforma.qualidade;

import br.com.esl.etl.v2.plataforma.SqlText;
import java.util.Objects;
import java.util.regex.Pattern;

/** Referência exata a uma policy governada e persistida; não carrega defaults produtivos. */
public record DataQualityPolicyReference(String version, String sha256) {

    private static final Pattern VERSION = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public DataQualityPolicyReference {
        version = requiredVersion(version);
        sha256 = Objects.requireNonNull(sha256, "O fingerprint da policy é obrigatório.");
        if (!SHA_256.matcher(sha256).matches()) {
            throw new IllegalArgumentException(
                    "O fingerprint da policy deve ser SHA-256 hexadecimal minúsculo.");
        }
    }

    private static String requiredVersion(final String value) {
        Objects.requireNonNull(value, "A versão da policy é obrigatória.");
        final String canonical = SqlText.trimAsciiSpace(value);
        if (!canonical.equals(value) || !VERSION.matcher(canonical).matches()) {
            throw new IllegalArgumentException("A versão da policy é inválida.");
        }
        return canonical;
    }

    @Override
    public String toString() {
        return "DataQualityPolicyReference[version=" + version + "]";
    }
}
