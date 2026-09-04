package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DataExportClientSettingsTest {

    private static final URI BASE_URI = URI.create("https://dataexport.example.test/tenant");
    private static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");
    private static final DataExportRetryPolicy RETRY_POLICY =
            new DataExportRetryPolicy(3, Duration.ofMillis(100), Duration.ofSeconds(1));

    @Test
    void exposesOnlyNonSecretSettingsAndMaterializesTheTokenExactlyOnce() {
        final AtomicInteger secretReads = new AtomicInteger();
        final DataExportClientSettings settings = validSettings(BASE_URI);
        final SecretProvider provider =
                key -> {
                    assertEquals(SecretKey.DATA_EXPORT_TOKEN, key);
                    secretReads.incrementAndGet();
                    return "  synthetic-materialized-value  ";
                };

        assertEquals(BASE_URI, settings.baseUri());
        assertEquals(SOURCE_ZONE, settings.sourceZone());
        assertEquals(DataExportClientSettings.MAX_REQUEST_TIMEOUT, settings.requestTimeout());
        assertEquals(DataExportTransport.GET_WITH_QUERY, settings.preferredTransport());
        assertSame(RETRY_POLICY, settings.retryPolicy());
        assertEquals(DataExportClientSettings.MAX_RESPONSE_BYTES, settings.maxResponseBytes());
        assertEquals(0, secretReads.get());

        final DataExportProperties materialized = settings.materialize(provider);

        assertEquals(1, secretReads.get());
        assertEquals("Bearer", materialized.authorizationHeaderValue().substring(0, 6));
        assertTrue(
                materialized.authorizationHeaderValue().endsWith("synthetic-materialized-value"));
        assertEquals(settings.baseUri(), materialized.baseUri());
        assertEquals(settings.sourceZone(), materialized.sourceZone());
        assertEquals(settings.requestTimeout(), materialized.requestTimeout());
        assertEquals(settings.preferredTransport(), materialized.preferredTransport());
        assertSame(settings.retryPolicy(), materialized.retryPolicy());
        assertEquals(settings.maxResponseBytes(), materialized.maxResponseBytes());
        assertFalse(settings.toString().contains("dataexport.example.test"));
        assertFalse(materialized.toString().contains("synthetic-materialized-value"));
        assertTrue(settings.toString().contains("baseUri=<redacted>"));
        assertThrows(NullPointerException.class, () -> settings.materialize(null));
    }

    @Test
    void acceptsHttpsAndOnlyTheExplicitHttpLoopbackHosts() {
        final List<URI> acceptedUris =
                List.of(
                        URI.create("https://dataexport.example.test"),
                        URI.create("http://localhost:8080"),
                        URI.create("http://127.0.0.1:8080"),
                        URI.create("http://[::1]:8080"));

        for (final URI acceptedUri : acceptedUris) {
            assertEquals(acceptedUri, validSettings(acceptedUri).baseUri());
        }
    }

    @Test
    void rejectsAmbiguousCredentialBearingOrInsecureBaseUris() {
        final List<URI> invalidUris =
                List.of(
                        URI.create("relative/path"),
                        URI.create("https:/missing-host"),
                        URI.create("https://user@dataexport.example.test"),
                        URI.create("https://dataexport.example.test?tenant=synthetic"),
                        URI.create("https://dataexport.example.test#fragment"),
                        URI.create("http://dataexport.example.test"),
                        URI.create("ftp://localhost"));

        assertThrows(NullPointerException.class, () -> validSettings(null));
        for (final URI invalidUri : invalidUris) {
            assertThrows(IllegalArgumentException.class, () -> validSettings(invalidUri));
        }
    }

    @Test
    void rejectsNullFixedOffsetOrDifferentIanaTimezoneAndInvalidTimeouts() {
        assertThrows(
                NullPointerException.class,
                () -> settings(BASE_URI, null, Duration.ofSeconds(1), RETRY_POLICY, 1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        settings(
                                BASE_URI,
                                ZoneOffset.ofHours(-3),
                                Duration.ofSeconds(1),
                                RETRY_POLICY,
                                1));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        settings(
                                BASE_URI,
                                ZoneId.of("Europe/Lisbon"),
                                Duration.ofSeconds(1),
                                RETRY_POLICY,
                                1));
        assertThrows(
                NullPointerException.class,
                () -> settings(BASE_URI, SOURCE_ZONE, null, RETRY_POLICY, 1));
        for (final Duration invalidTimeout :
                List.of(
                        Duration.ZERO,
                        Duration.ofNanos(-1),
                        DataExportClientSettings.MAX_REQUEST_TIMEOUT.plusNanos(1))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> settings(BASE_URI, SOURCE_ZONE, invalidTimeout, RETRY_POLICY, 1));
        }
    }

    @Test
    void rejectsMissingCollaboratorsAndResponseLimitsOutsideTheClosedRange() {
        assertThrows(
                NullPointerException.class,
                () ->
                        new DataExportClientSettings(
                                BASE_URI,
                                SOURCE_ZONE,
                                Duration.ofSeconds(1),
                                null,
                                RETRY_POLICY,
                                1));
        assertThrows(
                NullPointerException.class,
                () -> settings(BASE_URI, SOURCE_ZONE, Duration.ofSeconds(1), null, 1));
        for (final long invalidLimit :
                List.of(0L, -1L, DataExportClientSettings.MAX_RESPONSE_BYTES + 1L)) {
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            settings(
                                    BASE_URI,
                                    SOURCE_ZONE,
                                    Duration.ofSeconds(1),
                                    RETRY_POLICY,
                                    invalidLimit));
        }
    }

    @Test
    void environmentProviderSelectsKnownKeysSnapshotsValuesAndRedactsItself() {
        final String originalValue = "synthetic-original-value";
        final Map<String, String> environment = new HashMap<>();
        environment.put(
                SecretKey.DATA_EXPORT_TOKEN.environmentVariable(), "  " + originalValue + "  ");
        environment.put("UNRELATED_VALUE", "must-not-be-selected");

        final EnvironmentSecretProvider provider = new EnvironmentSecretProvider(environment);
        environment.put(
                SecretKey.DATA_EXPORT_TOKEN.environmentVariable(), "changed-after-snapshot");

        assertEquals(originalValue, provider.require(SecretKey.DATA_EXPORT_TOKEN));
        assertEquals("V2_DATAEXPORT_TOKEN", SecretKey.DATA_EXPORT_TOKEN.environmentVariable());
        assertEquals("V2_GRAPHQL_TOKEN", SecretKey.GRAPHQL_TOKEN.environmentVariable());
        assertEquals(2, SecretKey.values().length);
        assertEquals("EnvironmentSecretProvider[redacted]", provider.toString());
        assertFalse(provider.toString().contains(originalValue));
        assertFalse(provider.toString().contains("must-not-be-selected"));
    }

    @Test
    void environmentProviderRejectsMissingBlankOrNullReferencesWithoutLeakingValues() {
        final EnvironmentSecretProvider missing = new EnvironmentSecretProvider(Map.of());
        final EnvironmentSecretProvider blank =
                new EnvironmentSecretProvider(
                        Map.of(SecretKey.DATA_EXPORT_TOKEN.environmentVariable(), "   "));

        final IllegalStateException missingFailure =
                assertThrows(
                        IllegalStateException.class,
                        () -> missing.require(SecretKey.DATA_EXPORT_TOKEN));
        final IllegalStateException blankFailure =
                assertThrows(
                        IllegalStateException.class,
                        () -> blank.require(SecretKey.DATA_EXPORT_TOKEN));

        assertEquals(
                "Segredo obrigatório ausente no provider protegido.", missingFailure.getMessage());
        assertEquals(missingFailure.getMessage(), blankFailure.getMessage());
        assertFalse(blankFailure.toString().contains("V2_DATAEXPORT_TOKEN"));
        assertThrows(NullPointerException.class, () -> missing.require(null));
        assertThrows(NullPointerException.class, () -> new EnvironmentSecretProvider(null));
    }

    private static DataExportClientSettings validSettings(final URI baseUri) {
        return settings(
                baseUri,
                SOURCE_ZONE,
                DataExportClientSettings.MAX_REQUEST_TIMEOUT,
                RETRY_POLICY,
                DataExportClientSettings.MAX_RESPONSE_BYTES);
    }

    private static DataExportClientSettings settings(
            final URI baseUri,
            final ZoneId sourceZone,
            final Duration timeout,
            final DataExportRetryPolicy retryPolicy,
            final long maxResponseBytes) {
        return new DataExportClientSettings(
                baseUri,
                sourceZone,
                timeout,
                DataExportTransport.GET_WITH_QUERY,
                retryPolicy,
                maxResponseBytes);
    }
}
