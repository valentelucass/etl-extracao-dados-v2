package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Namespace and integrity component tests; no fixture grants Root/Source/physical admission. */
class C78Sample12NamespaceTest {
    @Test
    void originalIdentityAndPinsRemainExact() throws Exception {
        final var actual = C78Sample12Namespace.from(Path.of("hermesf4eda62e83d1"));
        assertEquals(C78Sample12Namespace.F4, actual);
        assertEquals("HERMES_f4eda62e83d1", actual.request);
        assertEquals("IssueSample12Admission113.py", actual.issuerName);
        assertEquals("RunC78Sample12Admission113.py", actual.controllerName);
        assertEquals(
                "4c0b4e7c5e5518df5782579b4d32b1d8723af196a65a6f3eb5e690848296d87d",
                actual.issuerSHA256);
        assertEquals(
                "39b4b8048fd1673fafa576afb1aea71eb05a93b8f26784fb57bc1181a99d7844",
                actual.controllerSHA256);
    }

    @Test
    void currentIdentityPinsActualPreparedBytes() throws Exception {
        final var actual = C78Sample12Namespace.from(Path.of("hermesc54d6a25880d"));
        assertEquals(C78Sample12Namespace.C54, actual);
        assertEquals("HERMES_c54d6a25880d", actual.request);
        final Path material = Path.of(System.getProperty("bank.c54.material"));
        final var result = new ObjectMapper().readTree(material.toFile());
        assertEquals(result.path("issuerPin").textValue(), actual.issuerSHA256);
        assertEquals(result.path("controllerPin").textValue(), actual.controllerSHA256);
        assertEquals(
                actual.issuerSHA256,
                digest(material.getParent().resolve("RootSample12IssuerMaterialC54.py")));
        assertEquals(
                actual.controllerSHA256,
                digest(material.getParent().resolve(actual.controllerName)));
        assertFalse(result.path("authorityIssued").booleanValue());
        assertFalse(result.path("claimsIssued").booleanValue());
    }

    @Test
    void unknownCaseAndPrefixAliasesCannotSelectAnotherAuthority() {
        for (String name :
                List.of(
                        "hermesc54d6a25880d-extra",
                        "hermesC54d6a25880d",
                        "hermesforeign",
                        "root",
                        "hermesf4eda62e83d1-copy")) {
            assertThrows(IOException.class, () -> C78Sample12Namespace.from(Path.of(name)));
        }
        assertThrows(IOException.class, () -> C78Sample12Namespace.from(null));
    }

    @Test
    void currentAndConsumedNamespacesCannotReplayEachOthersDispatchIds() {
        final String suffix = "a".repeat(32);
        assertTrue(("C54SAMPLE12-" + suffix).matches(C78Sample12Namespace.C54.dispatchPattern));
        assertFalse(("C78SAMPLE12-" + suffix).matches(C78Sample12Namespace.C54.dispatchPattern));
        assertFalse(("C54SAMPLE12-" + suffix).matches(C78Sample12Namespace.F4.dispatchPattern));
        assertFalse(
                ("C54SAMPLE12-" + suffix + "0").matches(C78Sample12Namespace.C54.dispatchPattern));
    }

    private static String digest(final Path path) throws Exception {
        return HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
