package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/** Closed 8656 GET routes over the existing pinned Root wire boundary; no direct network client. */
public final class LocalizadorTransport {
    private final DataExportSourceConfiguration source;
    private final PersistentExportJournal journal;
    private final OwnedExportWire physical;
    private final Clock clock;
    private final Instant deadline;
    private final int maximumRequests;

    public LocalizadorTransport(
            final DataExportSourceConfiguration source,
            final PersistentExportJournal journal,
            final RootOwnedWireChannel physical,
            final Clock clock,
            final Instant deadline,
            final int maximumRequests) {
        this(source, journal, (OwnedExportWire) physical, clock, deadline, maximumRequests);
    }

    LocalizadorTransport(
            final DataExportSourceConfiguration source,
            final PersistentExportJournal journal,
            final OwnedExportWire physical,
            final Clock clock,
            final Instant deadline,
            final int maximumRequests) {
        require(
                source != null
                        && journal != null
                        && physical != null
                        && clock != null
                        && deadline != null
                        && maximumRequests > 0
                        && maximumRequests <= 17,
                "LOCALIZADOR_FINITE_SOURCE_COMPONENTS");
        require(
                source.settings().retryPolicy().maxAttempts() == 1
                        && source.settings().requestTimeout().compareTo(Duration.ofSeconds(30)) <= 0
                        && source.resiliencePolicy().maxInFlight() == 1
                        && source.resiliencePolicy()
                                .minimumRequestInterval()
                                .equals(Duration.ofSeconds(3))
                        && source.resiliencePolicy().maxRequestsPerCycle() >= maximumRequests
                        && source.resiliencePolicy().maxRequestsPerWorkload() >= maximumRequests,
                "LOCALIZADOR_EXPLICIT_SOURCE_RESILIENCE");
        final URI origin = source.settings().baseUri();
        require(
                "https".equals(origin.getScheme())
                        && origin.getHost() != null
                        && origin.getRawUserInfo() == null
                        && origin.getRawFragment() == null
                        && origin.getRawQuery() == null
                        && (origin.getPort() == -1 || origin.getPort() == 443)
                        && (origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath())),
                "LOCALIZADOR_TRANSPORT_HTTPS_API_ROOT");
        this.source = source;
        this.journal = journal;
        this.physical = physical;
        this.clock = clock;
        this.deadline = deadline;
        this.maximumRequests = maximumRequests;
    }

    public CurlExportTransport.Wire api(
            final String method,
            final String relativePath,
            final byte[] body,
            final Duration timeout)
            throws Exception {
        require(
                "GET".equals(method)
                        && "/api/analytics/reports/8656/info".equals(relativePath)
                        && body == null,
                "LOCALIZADOR_CLOSED_INFO_GET_ROUTE");
        return once(source.settings().baseUri().resolve(relativePath), 262144, timeout);
    }

    /** The physical worker separately validates the current Root finite Localizador grant. */
    public CurlExportTransport.Wire page(final JsonNode request, final int page, final int per)
            throws Exception {
        require(
                source.settings().preferredTransport() == DataExportTransport.GET_WITH_QUERY,
                "LOCALIZADOR_QUALIFIED_GET_WITH_QUERY_REQUIRED");
        final URI endpoint =
                source.settings().baseUri().resolve("/api/analytics/reports/8656/data");
        return once(
                LocalizadorIndependentRequest.query(endpoint, request, page, per),
                8388608,
                Duration.ofSeconds(30));
    }

    private synchronized CurlExportTransport.Wire once(
            final URI target, final int maximumBodyBytes, final Duration timeout) throws Exception {
        require(
                clock.instant().isBefore(deadline)
                        && timeout != null
                        && !timeout.isZero()
                        && !timeout.isNegative()
                        && timeout.compareTo(Duration.ofSeconds(30)) <= 0,
                "LOCALIZADOR_SOURCE_DEADLINE");
        journal.requireRequestBudget(maximumRequests);
        final var remaining = Duration.between(clock.instant(), deadline);
        var effective = remaining.compareTo(timeout) < 0 ? remaining : timeout;
        if (source.settings().requestTimeout().compareTo(effective) < 0) {
            effective = source.settings().requestTimeout();
        }
        if (source.resiliencePolicy().requestTimeout().compareTo(effective) < 0) {
            effective = source.resiliencePolicy().requestTimeout();
        }
        journal.record(
                "HTTP_ADMITTED",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("method", "GET")
                        .put("startedAt", clock.instant().toString())
                        .put(
                                "targetSha256",
                                ExportValidationSupport.sha256(
                                        target.toASCIIString()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .put("maximumBodyBytes", maximumBodyBytes)
                        .put("timeoutMillis", effective.toMillis()));
        final var result =
                physical.exchange("GET", target, null, true, maximumBodyBytes, effective, deadline);
        journal.record(
                "HTTP_COMPLETE",
                ExportValidationSupport.JSON
                        .createObjectNode()
                        .put("status", result.status())
                        .put("completedAt", result.completedAt().toString())
                        .set("bodyPin", ExportValidationSupport.JSON.valueToTree(result.body())));
        return result;
    }
}
