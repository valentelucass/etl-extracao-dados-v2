package br.com.esl.etl.v2.plataforma.autorizacao;

import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Objects;
import javax.net.ssl.X509TrustManager;

/**
 * Exact public leaf pin administered inside the artifact; JVM truststore overrides cannot replace
 * it.
 */
public final class PinnedSqlTrustManager implements X509TrustManager {
    private final X509Certificate expected;

    public PinnedSqlTrustManager() throws CertificateException {
        this(load());
    }

    PinnedSqlTrustManager(final X509Certificate certificate) throws CertificateException {
        expected = Objects.requireNonNull(certificate);
        expected.checkValidity();
    }

    private static X509Certificate load() throws CertificateException {
        try (var input =
                PinnedSqlTrustManager.class.getResourceAsStream("/runtime-sql-public.cer")) {
            if (input == null) {
                throw new CertificateException("ADMINISTERED_SQL_CERTIFICATE_REQUIRED");
            }
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new CertificateException("SQL_CERTIFICATE_LIMIT");
            }
            try (var bounded = new java.io.ByteArrayInputStream(bytes)) {
                final var certificate =
                        (X509Certificate)
                                CertificateFactory.getInstance("X.509")
                                        .generateCertificate(bounded);
                if (bounded.available() != 0) {
                    throw new CertificateException("SQL_CERTIFICATE_TRAILING_DATA");
                }
                return certificate;
            }
        } catch (final java.io.IOException failure) {
            throw new CertificateException("SQL_CERTIFICATE_UNAVAILABLE", failure);
        }
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authentication)
            throws CertificateException {
        expected.checkValidity();
        if (chain == null
                || chain.length == 0
                || chain.length > 8
                || chain[0] == null
                || authentication == null
                || authentication.isBlank()
                || !MessageDigest.isEqual(expected.getEncoded(), chain[0].getEncoded())) {
            throw new CertificateException("SQL_CERTIFICATE_PIN_REJECTED");
        }
        chain[0].checkValidity();
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authentication)
            throws CertificateException {
        throw new CertificateException("SQL_CLIENT_CERTIFICATE_NOT_SUPPORTED");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[] {expected};
    }
}
