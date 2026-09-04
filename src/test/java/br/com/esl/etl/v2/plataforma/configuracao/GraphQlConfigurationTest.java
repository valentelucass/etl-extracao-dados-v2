package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import br.com.esl.etl.v2.plataforma.resiliencia.EslResiliencePolicy;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class GraphQlConfigurationTest {

    @Test
    void settingsMaterializeOnlyTheDedicatedSecretAndRedactTechnicalRepresentations() {
        final GraphQlClientSettings settings = settings("https://synthetic.invalid/graphql");
        final AtomicReference<SecretKey> requested = new AtomicReference<>();
        final GraphQlProperties properties =
                settings.materialize(
                        key -> {
                            requested.set(key);
                            return "synthetic-secret";
                        });

        assertEquals(SecretKey.GRAPHQL_TOKEN, requested.get());
        assertEquals(settings.endpoint(), properties.endpoint());
        assertEquals(settings.requestTimeout(), properties.requestTimeout());
        assertEquals(settings.retryPolicy(), properties.retryPolicy());
        assertEquals(settings.maxResponseBytes(), properties.maxResponseBytes());
        assertEquals(
                String.join(" ", "Bearer", "synthetic-secret"),
                properties.authorizationHeaderValue());
        assertFalse(settings.toString().contains("synthetic.invalid"));
        assertFalse(properties.toString().contains("synthetic-secret"));
        assertFalse(properties.toString().contains("synthetic.invalid"));
        assertTrue(properties.toString().contains("<redacted>"));
    }

    @Test
    void endpointTimeoutResponseAndSecretValidationFailFast() {
        for (final String invalid :
                List.of(
                        "/graphql",
                        "http://example.invalid/graphql",
                        "https://user@example.invalid/graphql",
                        "https://example.invalid/graphql?q=1",
                        "https://example.invalid/graphql#fragment")) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new GraphQlClientSettings(
                                    URI.create(invalid), Duration.ofSeconds(1), retry(), 1_024));
        }
        assertEquals("http", settings("http://127.0.0.1:8080/graphql").endpoint().getScheme());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphQlClientSettings(
                                URI.create("https://synthetic.invalid/graphql"),
                                Duration.ZERO,
                                retry(),
                                1_024));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphQlClientSettings(
                                URI.create("https://synthetic.invalid/graphql"),
                                GraphQlClientSettings.ABSOLUTE_MAXIMUM_REQUEST_TIMEOUT.plusNanos(1),
                                retry(),
                                1_024));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphQlClientSettings(
                                URI.create("https://synthetic.invalid/graphql"),
                                Duration.ofSeconds(1),
                                retry(),
                                0));
        assertThrows(
                IllegalArgumentException.class,
                () -> settings("https://synthetic.invalid/graphql").materialize(key -> " "));
        for (final String invalidToken :
                List.of(
                        " synthetic-secret",
                        "synthetic-secret ",
                        "synthetic secret",
                        "synthetic-secret\r\nInjected: value",
                        "synthetic-secret\u0000")) {
            final IllegalArgumentException exception =
                    assertThrows(
                            IllegalArgumentException.class,
                            () ->
                                    settings("https://synthetic.invalid/graphql")
                                            .materialize(key -> invalidToken));
            assertEquals("O token GraphQL é obrigatório.", exception.getMessage());
            assertFalse(exception.toString().contains(invalidToken));
        }
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        settings("https://synthetic.invalid/graphql")
                                .materialize(key -> "x".repeat(16_385)));
    }

    @Test
    void sourceIdentifiersAndFingerprintAreStableSecretIndependentAndConfigSensitive() {
        final GraphQlSourceConfiguration baseline =
                new GraphQlSourceConfiguration(
                        "source-a",
                        "tenant-a",
                        settings("https://synthetic.invalid/a"),
                        policy(10));
        final GraphQlSourceConfiguration same =
                new GraphQlSourceConfiguration(
                        "source-a",
                        "tenant-a",
                        settings("https://synthetic.invalid/a"),
                        policy(10));

        assertEquals(
                GraphQlRuntimeConfigurationFingerprint.from(baseline),
                GraphQlRuntimeConfigurationFingerprint.from(same));
        assertEquals(
                GraphQlRuntimeConfigurationFingerprint.VERSION,
                GraphQlRuntimeConfigurationFingerprint.from(baseline).version());
        assertNotEquals(
                GraphQlRuntimeConfigurationFingerprint.from(baseline),
                GraphQlRuntimeConfigurationFingerprint.from(
                        new GraphQlSourceConfiguration(
                                "source-b",
                                "tenant-a",
                                settings("https://synthetic.invalid/a"),
                                policy(10))));
        assertNotEquals(
                GraphQlRuntimeConfigurationFingerprint.from(baseline),
                GraphQlRuntimeConfigurationFingerprint.from(
                        new GraphQlSourceConfiguration(
                                "source-a",
                                "tenant-b",
                                settings("https://synthetic.invalid/b"),
                                policy(11))));
        assertEquals(
                baseline.settings().materialize(key -> "secret-a").endpoint(),
                baseline.settings().materialize(key -> "secret-b").endpoint());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphQlSourceConfiguration(
                                "bad source", "tenant-a", baseline.settings(), policy(10)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new GraphQlSourceConfiguration(
                                "source-a", "", baseline.settings(), policy(10)));
        assertFalse(baseline.toString().contains("source-a"));
        assertFalse(baseline.toString().contains("tenant-a"));
        assertFalse(baseline.toString().contains("synthetic.invalid"));
    }

    private static GraphQlClientSettings settings(final String endpoint) {
        return new GraphQlClientSettings(
                URI.create(endpoint), Duration.ofSeconds(2), retry(), 4_096);
    }

    private static GraphQlRetryPolicy retry() {
        return new GraphQlRetryPolicy(2, Duration.ofMillis(10), Duration.ofMillis(100));
    }

    private static EslResiliencePolicy policy(final int requestsPerCycle) {
        return new EslResiliencePolicy(
                Duration.ZERO,
                1,
                requestsPerCycle,
                5,
                Duration.ofSeconds(2),
                Duration.ofSeconds(5),
                Duration.ofSeconds(10),
                Duration.ofSeconds(1),
                2,
                3,
                Duration.ofSeconds(1));
    }
}
