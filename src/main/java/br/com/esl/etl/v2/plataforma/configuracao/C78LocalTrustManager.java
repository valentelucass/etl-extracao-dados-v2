package br.com.esl.etl.v2.plataforma.configuracao;

import java.io.ByteArrayInputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.util.Date;
import java.util.HexFormat;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.X509ExtendedTrustManager;

/**
 * Fixed manual trust decision. DNS exception belongs only to this scoped C78_LOCAL_SHADOW SQL mode.
 */
public final class C78LocalTrustManager extends X509ExtendedTrustManager {
    private final C78LocalTlsScope scope;
    private final Clock clock;

    public C78LocalTrustManager() {
        this(Clock.systemUTC());
    }

    C78LocalTrustManager(final Clock clock) {
        scope = C78LocalTlsScope.current();
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    @Override
    public void checkServerTrusted(
            final X509Certificate[] chain, final String authType, final Socket socket)
            throws CertificateException {
        try {
            scope.pinRefusal("CERTIFICATE_OR_PEER_REFUSED");
            C78LocalTlsScope.requireOptins();
            if (!(socket instanceof SSLSocket tls) || tls.getHandshakeSession() == null) {
                scope.pinRefusal("TLS_SOCKET_REQUIRED");
                throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_TLS_SOCKET_REQUIRED");
            }
            scope.verifyTlsPeer(socket);
            final var handshake = tls.getHandshakeSession();
            if (!"TLSv1.2".equals(handshake.getProtocol())
                    || !java.util.Set.of(
                                    "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
                                    "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384")
                            .contains(handshake.getCipherSuite())) {
                scope.pinRefusal("TLS_ALGORITHMS_REFUSED");
                throw new CertificateException("C78_LOCAL_SHADOW_TLS_ALGORITHMS_REFUSED");
            }
            if (chain == null || chain.length != 1 || chain[0] == null) {
                scope.pinRefusal("SINGLE_LEAF_REQUIRED");
                throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_SINGLE_LEAF_REQUIRED");
            }
            final byte[] der = chain[0].getEncoded();
            if (der.length == 0 || der.length > 16384) {
                scope.pinRefusal("DER_BOUND");
                throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_DER_BOUND");
            }
            final String observed =
                    HexFormat.of()
                            .withUpperCase()
                            .formatHex(MessageDigest.getInstance("SHA-256").digest(der));
            if (!C78LocalTlsScope.LEAF_SHA256.equals(observed)) {
                scope.pinRefusal("LEAF_CHANGED");
                throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_LEAF_CHANGED");
            }
            // Reparse DER; certificate subclass overrides cannot fake validity/signature/key facts.
            final var leaf =
                    (X509Certificate)
                            CertificateFactory.getInstance("X.509")
                                    .generateCertificate(new ByteArrayInputStream(der));
            leaf.checkValidity(Date.from(clock.instant()));
            if (!(leaf.getPublicKey() instanceof RSAPublicKey rsa)
                    || rsa.getModulus().bitLength() != 3072
                    || !"1.2.840.113549.1.1.11".equals(leaf.getSigAlgOID())
                    || !leaf.getSubjectX500Principal().equals(leaf.getIssuerX500Principal())) {
                throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_CRYPTO_CONTRACT_REFUSED");
            }
            leaf.verify(leaf.getPublicKey());
            scope.verifiedPin();
        } catch (final Exception failure) {
            if (failure instanceof java.security.cert.CertificateExpiredException) {
                scope.pinRefusal("LEAF_EXPIRED");
            } else if (failure instanceof java.security.cert.CertificateNotYetValidException) {
                scope.pinRefusal("LEAF_NOT_YET_VALID");
            } else if (failure instanceof java.security.SignatureException) {
                scope.pinRefusal("SELF_SIGNATURE_REFUSED");
            }
            throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_CERTIFICATE_OR_PEER_REFUSED");
        }
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        scope.pinRefusal("SOCKET_PROOF_REQUIRED");
        throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_SOCKET_PROOF_REQUIRED");
    }

    @Override
    public void checkServerTrusted(
            final X509Certificate[] chain, final String authType, final SSLEngine engine)
            throws CertificateException {
        scope.pinRefusal("SOCKET_PROOF_REQUIRED");
        throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_SOCKET_PROOF_REQUIRED");
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_CLIENT_IDENTITY_FORBIDDEN");
    }

    @Override
    public void checkClientTrusted(
            final X509Certificate[] chain, final String authType, final Socket socket)
            throws CertificateException {
        throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_CLIENT_IDENTITY_FORBIDDEN");
    }

    @Override
    public void checkClientTrusted(
            final X509Certificate[] chain, final String authType, final SSLEngine engine)
            throws CertificateException {
        throw new CertificateException("C78_LOCAL_SHADOW_SQL_PIN_CLIENT_IDENTITY_FORBIDDEN");
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return new X509Certificate[0];
    }
}
