package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DataExportPropertiesTest {

    @Test
    void redactsConnectionAndTokenInToString() {
        final String text = validProperties().toString();

        assertFalse(text.contains("test-token"));
        assertFalse(text.contains("dataexport.example.test"));
    }

    @Test
    void rejectsHttpOutsideLoopbackBecauseTheGatewaySendsBearerAuthentication() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportProperties(
                                URI.create("http://dataexport.example.test"),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO)));
    }

    @Test
    void rejectsFixedOffsetAsTheSourceTimezone() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportProperties(
                                URI.create("https://dataexport.example.test"),
                                "test-token",
                                ZoneOffset.ofHours(-3),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO)));
    }

    @Test
    void rejectsAmbiguousOrCredentialBearingBaseUris() {
        assertInvalidBaseUri("https://usuario:credencial@dataexport.example.test");
        assertInvalidBaseUri("https://dataexport.example.test?tenant=1");
        assertInvalidBaseUri("https://dataexport.example.test#fragmento");
        assertInvalidBaseUri("https:sem-host");
    }

    @Test
    void composesTheReportEndpointFromTheValidatedBasePath() {
        final DataExportProperties properties =
                new DataExportProperties(
                        URI.create("https://dataexport.example.test/tenant"),
                        "test-token",
                        ZoneId.of("America/Sao_Paulo"),
                        Duration.ofSeconds(5),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));

        assertEquals(
                URI.create(
                        "https://dataexport.example.test/tenant/api/analytics/reports/6908/data"),
                properties.endpointFor(6908));
        assertEquals(
                URI.create(
                        "https://dataexport.example.test/tenant/api/analytics/reports/6908/info"),
                properties.infoEndpointFor(6908));
    }

    @Test
    void rejectsMissingSecretInvalidLimitsAndInvalidTemplateId() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportProperties(
                                URI.create("https://dataexport.example.test"),
                                " ",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportProperties(
                                URI.create("https://dataexport.example.test"),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ZERO,
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportProperties(
                                URI.create("https://dataexport.example.test"),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO),
                                0));
        assertThrows(IllegalArgumentException.class, () -> validProperties().endpointFor(0));
    }

    @Test
    void acceptsLoopbackHttpAndBuildsTheAuthorizationOnlyOnDemand() {
        final DataExportProperties properties =
                new DataExportProperties(
                        URI.create("http://localhost:8080"),
                        "test-token",
                        ZoneId.of("UTC"),
                        Duration.ofSeconds(5),
                        DataExportTransport.GET_WITH_QUERY,
                        new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));

        assertTrue(properties.authorizationHeaderValue().startsWith("Bearer "));
        assertTrue(properties.authorizationHeaderValue().endsWith("test-token"));
    }

    private static DataExportProperties validProperties() {
        return new DataExportProperties(
                URI.create("https://dataexport.example.test"),
                "test-token",
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(5),
                DataExportTransport.GET_WITH_QUERY,
                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO));
    }

    private static void assertInvalidBaseUri(final String value) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportProperties(
                                URI.create(value),
                                "test-token",
                                ZoneId.of("America/Sao_Paulo"),
                                Duration.ofSeconds(5),
                                DataExportTransport.GET_WITH_QUERY,
                                new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO)));
    }
}
