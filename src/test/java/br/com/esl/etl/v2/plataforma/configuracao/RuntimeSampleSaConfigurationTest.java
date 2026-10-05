package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RuntimeSampleSaConfigurationTest {
    private static final String CONFIG =
            "runtime.environment=LOCAL_SHADOW\nruntime.business-timezone=America/Sao_Paulo\n"
                    + "shadow.audit.enabled=true\nshadow.target-kind=LOCAL_EPHEMERAL\n"
                    + "shadow.sample-sa.enabled=true\nshadow.jdbc-url=jdbc:sqlserver://localhost;"
                    + "databaseName=ETL_SISTEMA_V2_SHADOW;integratedSecurity=false;"
                    + "encrypt=true;trustServerCertificate=false\n";
    @TempDir Path directory;

    private RuntimeConfiguration load(
            final String text, final Properties system, final Map<String, String> environment)
            throws Exception {
        final Path file = directory.resolve("runtime.properties");
        Files.writeString(file, text, StandardCharsets.UTF_8);
        return new RuntimeConfigurationFactory().load(file, system, environment, Clock.systemUTC());
    }

    @Test
    void explicitSaModeLoadsWithoutResolvingSecretAndEnvironmentOverrideIsTyped() throws Exception {
        final var sa = load(CONFIG, new Properties(), Map.of());
        assertTrue(sa.shadowStorage().usesSampleSaAuthentication());
        assertFalse(sa.shadowStorage().usesIntegratedSecurity());
        assertTrue(
                load(
                                CONFIG.replace("shadow.sample-sa.enabled=true\n", ""),
                                new Properties(),
                                Map.of("V2_SHADOW_SAMPLE_SA_ENABLED", "true"))
                        .shadowStorage()
                        .usesSampleSaAuthentication());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                CONFIG,
                                new Properties(),
                                Map.of("V2_SHADOW_SAMPLE_SA_ENABLED", "false")));
    }

    @Test
    void dormantSaSecretIsRefusedByPresenceWithoutReadingEvenBlankValue() throws Exception {
        final var reads = new AtomicInteger();
        final Map<String, String> protectedEnvironment =
                new AbstractMap<>() {
                    @Override
                    public Set<Entry<String, String>> entrySet() {
                        return Set.of(Map.entry("V2_SHADOW_SAMPLE_SA_PASSWORD", ""));
                    }

                    @Override
                    public String get(final Object key) {
                        if ("V2_SHADOW_SAMPLE_SA_PASSWORD".equals(key)) {
                            reads.incrementAndGet();
                            throw new AssertionError("PROTECTED_VALUE_MUST_NOT_BE_READ");
                        }
                        return null;
                    }
                };
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        load(
                                CONFIG.replace(
                                        "shadow.sample-sa.enabled=true",
                                        "shadow.sample-sa.enabled=false"),
                                new Properties(),
                                protectedEnvironment));
        assertEquals(0, reads.get());
    }

    @Test
    void refusesNonLocalDormantAndSecretFileOrSystemInputs() throws Exception {
        for (final String text :
                new String[] {
                    CONFIG.replace("LOCAL_SHADOW", "PRODUCTION"),
                    CONFIG.replace("LOCAL_EPHEMERAL", "APPROVED_NON_PRODUCTION"),
                    CONFIG.replace("shadow.audit.enabled=true", "shadow.audit.enabled=false"),
                    CONFIG + "shadow.password=synthetic\n",
                    CONFIG + "V2_SHADOW_SAMPLE_SA_PASSWORD=synthetic\n"
                }) {
            assertThrows(RuntimeException.class, () -> load(text, new Properties(), Map.of()));
        }
        final var system = new Properties();
        system.setProperty("V2_SHADOW_SAMPLE_SA_PASSWORD", "synthetic");
        assertThrows(IllegalArgumentException.class, () -> load(CONFIG, system, Map.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> SecretInputPolicy.rejectSecretCommandLineOption("--password=synthetic"));
    }
}
