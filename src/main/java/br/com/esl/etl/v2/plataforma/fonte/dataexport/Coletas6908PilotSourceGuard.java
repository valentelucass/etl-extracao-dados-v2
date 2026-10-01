package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import br.com.esl.etl.v2.plataforma.configuracao.DataExportClientSettings;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Objects;

/** Preflight sem segredo ou I/O para a travessia limitada de Coletas 6908. */
public final class Coletas6908PilotSourceGuard {

    public static final int MAX_PAGES = 9;
    public static final long MAX_PHYSICAL_ROWS = 1_000;
    public static final long MAX_RESPONSE_BYTES = 10L * 1024L * 1024L;
    public static final Duration MAX_REQUEST_TIMEOUT = Duration.ofSeconds(30);

    private Coletas6908PilotSourceGuard() {}

    /** A página vazia encerra apenas a travessia local; não comprova snapshot da fonte. */
    public static void validateTraversal(
            final DataExportClientSettings settings,
            final DataExportPageRequest initialRequest,
            final DataExportExtractionLimits limits,
            final Clock clock) {
        Objects.requireNonNull(settings, "A configuração Data Export é obrigatória.");
        Objects.requireNonNull(initialRequest, "A requisição 6908 é obrigatória.");
        Objects.requireNonNull(limits, "Os limites da travessia são obrigatórios.");
        Objects.requireNonNull(clock, "O relógio do piloto é obrigatório.");

        if (settings.preferredTransport() != DataExportTransport.GET_WITH_QUERY
                || settings.retryPolicy().maxAttempts() != 1
                || settings.requestTimeout().compareTo(MAX_REQUEST_TIMEOUT) > 0
                || settings.maxResponseBytes() > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("COLETAS_6908_PILOT_SOURCE_LIMITS_REQUIRED");
        }
        if (initialRequest.template() != DataExportTemplate.COLETAS
                || initialRequest.page() != 1
                || initialRequest.updatedAtWindow().isPresent()
                || !initialRequest
                        .template()
                        .approvesRequestSemantics(
                                initialRequest.pageSize(), initialRequest.orderBy())) {
            throw new IllegalArgumentException("COLETAS_6908_PILOT_REQUEST_REQUIRED");
        }
        final LocalDate sourceToday = LocalDate.now(clock.withZone(settings.sourceZone()));
        if (!initialRequest
                        .businessDateWindow()
                        .startInclusive()
                        .equals(initialRequest.businessDateWindow().endInclusive())
                || !initialRequest.businessDateWindow().endInclusive().isBefore(sourceToday)) {
            throw new IllegalArgumentException("COLETAS_6908_PILOT_CLOSED_DAY_REQUIRED");
        }
        if (limits.maxPages() < 2
                || limits.maxPages() > MAX_PAGES
                || limits.maxRecords() > MAX_PHYSICAL_ROWS
                || limits.maxPageSize() > DataExportTemplate.COLETAS.defaultPageSize()) {
            throw new IllegalArgumentException("COLETAS_6908_PILOT_TRAVERSAL_LIMITS_REQUIRED");
        }
        limits.validate(initialRequest);
    }
}
