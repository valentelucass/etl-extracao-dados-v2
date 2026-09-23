package br.com.esl.etl.v2.plataforma.relacional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Exact synthetic capture releases; fingerprints describe content and confer no authority. */
public record RelationalCaptureContracts(String manifestos, String coletas, String fretes) {
    public RelationalCaptureContracts {
        for (final String hash : List.of(manifestos, coletas, fretes)) {
            if (!hash.matches("[a-f0-9]{64}")) {
                throw new IllegalArgumentException("REL_LAB_CAPTURE_CONTRACT");
            }
        }
    }

    public String fingerprint() {
        return digest("synthetic-relational-v1|" + manifestos + "|" + coletas + "|" + fretes);
    }

    static String digest(final String material) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (final NoSuchAlgorithmException failure) {
            throw new IllegalStateException("SHA256_UNAVAILABLE", failure);
        }
    }
}
