package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvironmentSecretProviderSampleSaTest {
    @Test
    void preservesOnlyTheNewSaPasswordExactlyIncludingLeadingAndTrailingSpaces() {
        final String synthetic = "  synthetic-sa-value \t ";
        final var provider =
                new EnvironmentSecretProvider(Map.of("V2_SHADOW_SAMPLE_SA_PASSWORD", synthetic));
        assertEquals(synthetic, provider.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD));
        assertFalse(provider.toString().contains(synthetic));
    }

    @Test
    void preservesLegacyTokenTrimmingAndRejectsMissingOrBlankSaSecret() {
        final var provider =
                new EnvironmentSecretProvider(
                        Map.of("V2_DATAEXPORT_TOKEN", "  synthetic-token \t "));
        assertEquals("synthetic-token", provider.require(SecretKey.DATA_EXPORT_TOKEN));
        assertThrows(
                IllegalStateException.class,
                () -> provider.require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD));
        assertThrows(
                IllegalStateException.class,
                () ->
                        new EnvironmentSecretProvider(
                                        Map.of("V2_SHADOW_SAMPLE_SA_PASSWORD", " \t "))
                                .require(SecretKey.SHADOW_SAMPLE_SA_PASSWORD));
    }
}
