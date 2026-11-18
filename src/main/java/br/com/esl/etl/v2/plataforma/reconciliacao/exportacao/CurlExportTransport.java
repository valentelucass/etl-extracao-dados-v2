package br.com.esl.etl.v2.plataforma.reconciliacao.exportacao;

import static br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.ExportValidationSupport.require;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportSourceConfiguration;
import java.io.IOException;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Permanent routes over the real pinned Root/OwnJob single-wire boundary; no direct curl/JDK send.
 */
public final class CurlExportTransport {
    public record Wire(int status, ExportValidationSupport.Pin body, Instant completedAt) {
        public byte[] bytes() throws IOException {
            return body == null
                    ? new byte[0]
                    : body.read(
                            8388608, new ExportValidationSupport.Budget(Duration.ofSeconds(30)));
        }
    }

    private final DataExportSourceConfiguration source;
    private final PersistentExportJournal journal;
    private final OwnedExportWire physical;
    private final Clock clock;
    private final Instant deadline;
    private final int maximumRequests;
    private final Set<String> downloadHosts;

    public CurlExportTransport(
            final DataExportSourceConfiguration source,
            final PersistentExportJournal journal,
            final RootOwnedWireChannel physical,
            final Clock clock,
            final Instant deadline,
            final int maximumRequests,
            final Set<String> downloadHosts) {
        this(
                source,
                journal,
                (OwnedExportWire) physical,
                clock,
                deadline,
                maximumRequests,
                downloadHosts);
    }

    CurlExportTransport(
            final DataExportSourceConfiguration source,
            final PersistentExportJournal journal,
            final OwnedExportWire physical,
            final Clock clock,
            final Instant deadline,
            final int maximumRequests,
            final Set<String> downloadHosts) {
        require(
                source != null
                        && journal != null
                        && physical != null
                        && clock != null
                        && deadline != null
                        && maximumRequests > 0
                        && maximumRequests <= 64,
                "EXPORT_FINITE_SOURCE_COMPONENTS");
        require(
                source.settings().retryPolicy().maxAttempts() == 1
                        && source.settings().requestTimeout().compareTo(Duration.ofSeconds(30)) <= 0
                        && source.resiliencePolicy().maxInFlight() == 1
                        && source.resiliencePolicy()
                                .minimumRequestInterval()
                                .equals(Duration.ofSeconds(3))
                        && source.resiliencePolicy().maxRequestsPerCycle() >= maximumRequests
                        && source.resiliencePolicy().maxRequestsPerWorkload() >= maximumRequests,
                "EXPORT_EXPLICIT_SOURCE_RESILIENCE");
        final String path = source.settings().baseUri().getPath();
        require(
                path == null || path.isEmpty() || "/".equals(path),
                "EXPORT_PROFILE_API_ROOT_REQUIRED");
        this.source = source;
        this.journal = journal;
        this.physical = physical;
        this.clock = clock;
        this.deadline = deadline;
        this.maximumRequests = maximumRequests;
        this.downloadHosts = Set.copyOf(downloadHosts);
    }

    public Wire api(
            final String method,
            final String relativePath,
            final byte[] body,
            final Duration timeout)
            throws Exception {
        require(
                ("POST".equals(method) && "/api/analytics/reports/6389/export".equals(relativePath))
                        || ("GET".equals(method)
                                && relativePath.matches(
                                        "/api/analytics/report_files/[1-9][0-9]{0,17}"))
                        || ("GET".equals(method)
                                && relativePath.matches("/api/analytics/reports/6908/(info|data)"))
                        || ("GET".equals(method)
                                && "/api/analytics/reports/6399/info".equals(relativePath))
                        || ("GET".equals(method)
                                && "/api/analytics/reports/6906/info".equals(relativePath))
                        || ("GET".equals(method)
                                && "/api/analytics/reports/10633/info".equals(relativePath)),
                "EXPORT_CLOSED_API_ROUTE");
        return once(
                method,
                source.settings().baseUri().resolve(relativePath),
                body,
                true,
                relativePath.endsWith("/data") ? 8388608 : 262144,
                timeout);
    }

    public Wire download(final URI target) throws Exception {
        require(
                "https".equals(target.getScheme())
                        && target.getRawUserInfo() == null
                        && target.getRawFragment() == null
                        && target.getHost() != null
                        && (target.getPort() == -1 || target.getPort() == 443)
                        && downloadHosts.contains(
                                target.getHost().toLowerCase(java.util.Locale.ROOT)),
                "EXPORT_EXPLICIT_DOWNLOAD_ORIGIN");
        return once("GET", target, null, false, 8388608, Duration.ofSeconds(30));
    }

    public Wire coletasPage(
            final com.fasterxml.jackson.databind.JsonNode request, final int page, final int per)
            throws Exception {
        require(
                source.settings().preferredTransport()
                        == br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport
                                .GET_WITH_QUERY,
                "COLETAS_QUALIFIED_GET_WITH_QUERY_REQUIRED");
        final URI endpoint =
                source.settings().baseUri().resolve("/api/analytics/reports/6908/data");
        return once(
                "GET",
                ColetasIndependentRequest.query(endpoint, request, page, per),
                null,
                true,
                8388608,
                Duration.ofSeconds(30));
    }

    /** The physical worker separately requires the current Root finite Manifestos grant. */
    public Wire manifestosPage(
            final com.fasterxml.jackson.databind.JsonNode request, final int page, final int per)
            throws Exception {
        require(
                source.settings().preferredTransport()
                        == br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport
                                .GET_WITH_QUERY,
                "MANIFESTOS_QUALIFIED_GET_WITH_QUERY_REQUIRED");
        final URI endpoint =
                source.settings().baseUri().resolve("/api/analytics/reports/6399/data");
        return once(
                "GET",
                ManifestosIndependentRequest.query(endpoint, request, page, per),
                null,
                true,
                8388608,
                Duration.ofSeconds(30));
    }

    /** Requires the separate current Root Cotações grant at the physical worker boundary. */
    public Wire cotacoesPage(
            final com.fasterxml.jackson.databind.JsonNode request, final int page, final int per)
            throws Exception {
        require(
                source.settings().preferredTransport()
                        == br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport
                                .GET_WITH_QUERY,
                "COTACOES_QUALIFIED_GET_WITH_QUERY_REQUIRED");
        final URI endpoint =
                source.settings().baseUri().resolve("/api/analytics/reports/6906/data");
        return once(
                "GET",
                CotacoesIndependentRequest.query(endpoint, request, page, per),
                null,
                true,
                8388608,
                Duration.ofSeconds(30));
    }

    /** Requires the separate current Root Inventário grant at the physical worker boundary. */
    public Wire inventarioPage(
            final com.fasterxml.jackson.databind.JsonNode request, final int page, final int per)
            throws Exception {
        require(
                source.settings().preferredTransport()
                        == br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTransport
                                .GET_WITH_QUERY,
                "INVENTARIO_QUALIFIED_GET_WITH_QUERY_REQUIRED");
        final URI endpoint =
                source.settings().baseUri().resolve("/api/analytics/reports/10633/data");
        return once(
                "GET",
                InventarioIndependentRequest.query(endpoint, request, page, per),
                null,
                true,
                8388608,
                Duration.ofSeconds(30));
    }

    private synchronized Wire once(
            final String method,
            final URI target,
            final byte[] body,
            final boolean authentication,
            final int maximumBodyBytes,
            final Duration timeout)
            throws Exception {
        require(
                clock.instant().isBefore(deadline)
                        && timeout != null
                        && !timeout.isZero()
                        && !timeout.isNegative()
                        && timeout.compareTo(Duration.ofSeconds(30)) <= 0,
                "EXPORT_SOURCE_DEADLINE");
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
                        .put("method", method)
                        .put("startedAt", clock.instant().toString())
                        .put(
                                "targetSha256",
                                ExportValidationSupport.sha256(
                                        target.toASCIIString()
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                        .put("maximumBodyBytes", maximumBodyBytes)
                        .put("timeoutMillis", effective.toMillis()));
        final var result =
                physical.exchange(
                        method,
                        target,
                        body,
                        authentication,
                        maximumBodyBytes,
                        effective,
                        deadline);
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
