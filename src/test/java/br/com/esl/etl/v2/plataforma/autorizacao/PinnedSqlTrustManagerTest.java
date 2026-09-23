package br.com.esl.etl.v2.plataforma.autorizacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;

class PinnedSqlTrustManagerTest {
    @Test
    void exactPinRejectsDifferentCertificateMissingChainAndClientAuthentication() throws Exception {
        // Public JDK trust material is only a portable test certificate source. Runtime never reads
        // it.
        final var store =
                KeyStore.getInstance(
                        Path.of(System.getProperty("java.home"), "lib/security/cacerts").toFile(),
                        (char[]) null);
        final var certificates = new java.util.ArrayList<X509Certificate>();
        final var aliases = store.aliases();
        while (aliases.hasMoreElements() && certificates.size() < 2) {
            final var certificate = (X509Certificate) store.getCertificate(aliases.nextElement());
            try {
                certificate.checkValidity();
                certificates.add(certificate);
            } catch (final CertificateException expiredPublicRoot) {
                // Select two currently valid public roots; no truststore or clock is changed.
            }
        }
        assertEquals(2, certificates.size());
        final var pin = new PinnedSqlTrustManager(certificates.get(0));
        final X509Certificate[] matching = {certificates.get(0)};
        pin.checkServerTrusted(matching, "RSA");
        assertThrows(
                CertificateException.class,
                () -> pin.checkServerTrusted(new X509Certificate[] {certificates.get(1)}, "RSA"));
        assertThrows(CertificateException.class, () -> pin.checkServerTrusted(null, "RSA"));
        assertThrows(
                CertificateException.class,
                () -> pin.checkServerTrusted(new X509Certificate[0], "RSA"));
        assertThrows(
                CertificateException.class,
                () -> pin.checkServerTrusted(new X509Certificate[9], "RSA"));
        assertThrows(
                CertificateException.class,
                () -> pin.checkServerTrusted(new X509Certificate[1], "RSA"));
        assertThrows(CertificateException.class, () -> pin.checkServerTrusted(matching, null));
        assertThrows(CertificateException.class, () -> pin.checkServerTrusted(matching, " "));
        assertThrows(CertificateException.class, () -> pin.checkClientTrusted(matching, "RSA"));
        assertEquals(certificates.get(0), pin.getAcceptedIssuers()[0]);
        assertEquals(1, pin.getAcceptedIssuers().length);
        pin.getAcceptedIssuers()[0] = null;
        assertEquals(certificates.get(0), pin.getAcceptedIssuers()[0]);
        assertThrows(CertificateException.class, PinnedSqlTrustManager::new);
        assertThrows(java.sql.SQLException.class, AdministeredSqlConnection::open);
    }
}
