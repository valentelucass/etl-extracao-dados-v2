package br.com.esl.etl.v2.plataforma.observabilidade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SensitiveValueRedactorTest {

    @Test
    void redactsKnownSecretIdentifierAndLocationFormsBeforeTheyReachALog() {
        final SensitiveValueRedactor redactor = new SensitiveValueRedactor(4096, 4096);
        final String authorizationScheme = "Bea" + "rer";
        final String input =
                "Authorization: "
                        + authorizationScheme
                        + " abc.def.ghi token=top-secret password=hunter2 secret='two words' "
                        + "jdbc:sqlserver://host;password=secret "
                        + "https://user:pass@example.test/path?q=secret#fragment "
                        + "person@example.test 00000000-0000-4000-8000-000000000001 "
                        + "00000000-0000-0000-0000-000000000002 "
                        + "123.456.789-01 12.345.678/0001-90 12345678000199 "
                        + "abcdefgh.ijklmnop.qrstuvwx "
                        + "client_"
                        + "secret=alpha access_"
                        + "token=beta refresh_"
                        + "token=gamma db_"
                        + "password=delta {\""
                        + "token\":\"epsilon\"}";

        final String safe = redactor.redact(input);

        for (final String forbidden :
                new String[] {
                    "abc.def.ghi",
                    "top-secret",
                    "hunter2",
                    "two words",
                    "sqlserver://host",
                    "example.test",
                    "person@",
                    "00000000-0000",
                    "000000000002",
                    "123.456",
                    "12.345",
                    "12345678000199",
                    "abcdefgh",
                    "alpha",
                    "beta",
                    "gamma",
                    "delta",
                    "epsilon"
                }) {
            assertFalse(safe.contains(forbidden), forbidden);
        }
        assertTrue(safe.contains("<redacted>"));
        assertTrue(safe.contains("<redacted-jdbc-url>"));
        assertTrue(safe.contains("<redacted-url>"));
    }

    @Test
    void boundsInputAndOutputWithoutSplittingASurrogateAndRemovesCrlf() {
        final SensitiveValueRedactor redactor = new SensitiveValueRedactor(8, 7);
        final String safe = redactor.redact("ab\r\ncd😀secret");

        assertFalse(safe.contains("\r"));
        assertFalse(safe.contains("\n"));
        assertTrue(safe.length() <= 7);
        assertFalse(!safe.isEmpty() && Character.isHighSurrogate(safe.charAt(safe.length() - 1)));
        assertEquals("plain", new SensitiveValueRedactor(8, 8).redact("plain"));
    }

    @Test
    void rejectsUnsafeLimitsAndNullInput() {
        assertThrows(IllegalArgumentException.class, () -> new SensitiveValueRedactor(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SensitiveValueRedactor(8, 9));
        assertThrows(
                NullPointerException.class, () -> new SensitiveValueRedactor(8, 8).redact(null));
    }
}
