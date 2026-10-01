package br.com.esl.etl.v2.plataforma.configuracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportRetryPolicy;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlRetryPolicy;
import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class HttpEndpointUnicodeBoundaryTest {

    private static final DataExportRetryPolicy DATA_EXPORT_RETRY =
            new DataExportRetryPolicy(1, Duration.ZERO, Duration.ZERO);
    private static final GraphQlRetryPolicy GRAPHQL_RETRY =
            new GraphQlRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    @Test
    void unicodeLookalikeHostCannotBecomeTheLoopbackHttpException() {
        final URI nonAsciiHost = URI.create("http://localho\u017Ft:8080");
        assertNull(nonAsciiHost.getHost());
        assertThrows(IllegalArgumentException.class, () -> dataExportSettings(nonAsciiHost));
        assertThrows(IllegalArgumentException.class, () -> dataExportProperties(nonAsciiHost));
        assertThrows(IllegalArgumentException.class, () -> graphQlSettings(nonAsciiHost));
    }

    @Test
    void uriParserRejectsUnicodeLookalikeSchemeBeforeConfiguration() {
        assertThrows(
                IllegalArgumentException.class, () -> URI.create("http\u017F://localhost:8080"));
    }

    @Test
    void asciiCaseInsensitiveLoopbackRemainsAccepted() {
        final URI asciiLoopback = URI.create("HTTP://LOCALHOST:8080");
        assertEquals(asciiLoopback, dataExportSettings(asciiLoopback).baseUri());
        assertEquals(asciiLoopback, dataExportProperties(asciiLoopback).baseUri());
        assertEquals(asciiLoopback, graphQlSettings(asciiLoopback).endpoint());
    }

    private static DataExportClientSettings dataExportSettings(final URI uri) {
        return new DataExportClientSettings(
                uri,
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(2),
                DataExportTransport.GET_WITH_QUERY,
                DATA_EXPORT_RETRY,
                1_024);
    }

    private static DataExportProperties dataExportProperties(final URI uri) {
        return new DataExportProperties(
                uri,
                "synthetic-token",
                ZoneId.of("America/Sao_Paulo"),
                Duration.ofSeconds(2),
                DataExportTransport.GET_WITH_QUERY,
                DATA_EXPORT_RETRY,
                1_024);
    }

    private static GraphQlClientSettings graphQlSettings(final URI uri) {
        return new GraphQlClientSettings(uri, Duration.ofSeconds(2), GRAPHQL_RETRY, 1_024);
    }
}
