package br.com.esl.etl.v2.plataforma.fonte;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class SyntheticSourceScopeTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
            strings = {"SYNTHETIC_", "SYNTHETIC_lower", "SYNTHETIC_A ", "OTHER", "SYNTHETIC_Á"})
    void invalidSourceOrTenantCannotSelectAnUnscopedNamespace(final String invalid) {
        assertEquals(
                "LOCAL_SYNTHETIC_SOURCE_SCOPE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new SyntheticSourceScope(invalid, "SYNTHETIC_TENANT"))
                        .getMessage());
        assertEquals(
                "LOCAL_SYNTHETIC_SOURCE_SCOPE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new SyntheticSourceScope("SYNTHETIC_SOURCE", invalid))
                        .getMessage());
    }

    @Test
    void declaredLengthBoundaryMatchesTheSqlNamespaceAndLegacyRemainsExplicit() {
        final String maximum = "SYNTHETIC_" + "A".repeat(30);
        assertEquals(maximum, new SyntheticSourceScope(maximum, "SYNTHETIC_0").source());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyntheticSourceScope(maximum + "A", "SYNTHETIC_TENANT"));
        assertEquals("LOCAL_V2", new SyntheticSourceScope("LOCAL_V2", "LOCAL_V2").tenant());
    }
}
