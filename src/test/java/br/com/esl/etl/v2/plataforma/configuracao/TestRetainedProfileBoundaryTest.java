package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import org.junit.jupiter.api.Test;

class TestRetainedProfileBoundaryTest {
    @Test
    void targetRejectsDifferentDatabaseCredentialsTlsAndApproval() {
        for (final String wrong :
                new String[] {
                    TestRetainedSqlTarget.URL.replace("ETL_SISTEMA_V2_SHADOW", "ETL_SISTEMA"),
                    TestRetainedSqlTarget.URL.replace("localhost", "remote"),
                    TestRetainedSqlTarget.URL.replace("1433", "1434"),
                    TestRetainedSqlTarget.URL.replace("encrypt=true", "encrypt=false"),
                    TestRetainedSqlTarget.URL.replace(
                            "trustServerCertificate=false", "trustServerCertificate=true"),
                    TestRetainedSqlTarget.URL + ";user=sa",
                    TestRetainedSqlTarget.URL + ";password=synthetic-only"
                }) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new TestRetainedSqlTarget(
                                    wrong, TestRetainedSqlTarget.APPROVAL_REFERENCE));
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> TestRetainedSqlTarget.approved("HERMES325fac56a833"));
    }

    @Test
    void eachProfileRefusesTheOtherLeafWithoutChangingItsOwnDecision() throws Exception {
        TestRetainedTlsTest.opted(
                () -> {
                    final TestRetainedTlsTest.FakeGuard[] owned =
                            new TestRetainedTlsTest.FakeGuard[1];
                    try (var scope =
                            TestRetainedTlsScope.enter(
                                    TestRetainedTlsTest.target(),
                                    s -> {
                                        owned[0] = new TestRetainedTlsTest.FakeGuard(s);
                                        return owned[0];
                                    })) {
                        final var socket = new TestRetainedSocketFactory().createSocket();
                        socket.connect(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 1433),
                                1000);
                        final var trust = new TestRetainedTrustManager(TestRetainedTlsTest.VALID);
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        trust.checkServerTrusted(
                                                new X509Certificate[] {PinnedSampleTlsTest.leaf()},
                                                "ECDHE_RSA",
                                                new TestRetainedTlsTest.FakeTls(owned[0])));
                        assertEquals("LEAF_CHANGED", scope.failureClass());
                    }
                });
        PinnedSampleTlsTest.opted(
                () -> {
                    final PinnedSampleTlsTest.FakeGuard[] owned =
                            new PinnedSampleTlsTest.FakeGuard[1];
                    try (var scope =
                            PinnedSampleTlsScope.enter(
                                    PinnedSampleTlsTest.target(),
                                    s -> {
                                        owned[0] = new PinnedSampleTlsTest.FakeGuard(s);
                                        return owned[0];
                                    })) {
                        final var socket = new PinnedSampleSocketFactory().createSocket();
                        socket.connect(
                                new InetSocketAddress(InetAddress.getLoopbackAddress(), 1433),
                                1000);
                        final var trust = new PinnedSampleTrustManager(TestRetainedTlsTest.VALID);
                        assertThrows(
                                CertificateException.class,
                                () ->
                                        trust.checkServerTrusted(
                                                new X509Certificate[] {TestRetainedTlsTest.leaf()},
                                                "ECDHE_RSA",
                                                new PinnedSampleTlsTest.FakeTls(owned[0])));
                        assertEquals("LEAF_CHANGED", scope.failureClass());
                    }
                });
        assertEquals(
                "BF723AE91FC90F3CC448122366C90650CBC2C6F5CD4A849F4B3C88C68E7A43A0",
                PinnedSampleTlsScope.LEAF_SHA256);
        assertEquals(
                "E3A5A7061FB56C225F93CC330CD733F5911F7D3F420D608EBE9AFA7371ACE276",
                TestRetainedTlsScope.LEAF_SHA256);
    }
}
