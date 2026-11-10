package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;

/** Update filters observed and exercised against the ESL metadata and data endpoints. */
public record ReportUpdateContract(SearchPath path, boolean dateOnly) {
    public static final ZoneId SOURCE_ZONE = ZoneId.of("America/Sao_Paulo");

    public static ReportUpdateContract forTemplate(final ReportSnapshotTemplate template) {
        return switch (template) {
            case COLETAS, FRETES, MANIFESTOS, LOCALIZACAO_CARGAS, FATURAS_POR_CLIENTE ->
                    new ReportUpdateContract(new SearchPath("scopes", "by_updated_at"), false);
            case COTACOES -> new ReportUpdateContract(new SearchPath("quotes", "updated_at"), true);
            case INVENTARIO ->
                    new ReportUpdateContract(
                            new SearchPath("check_in_orders", "updated_at"), false);
            case CONSOLIDACAO_DETALHADA ->
                    new ReportUpdateContract(new SearchPath("consolidations", "updated_at"), true);
            case OCORRENCIAS ->
                    new ReportUpdateContract(
                            new SearchPath("invoice_occurrences", "updated_at"), false);
            case CONTAS_A_PAGAR, SINISTROS -> null;
        };
    }

    public URI query(
            final ReportSnapshotTemplate template,
            final URI origin,
            final BusinessDateRange history,
            final SourceDateTimeRange updates,
            final int page,
            final int per) {
        // Reuse the closed-origin check without broadening the original snapshot request.
        template.info(origin);
        if (!equals(forTemplate(template))
                || history == null
                || updates == null
                || ChronoUnit.DAYS.between(history.startInclusive(), history.endInclusive()) > 3660
                || page < 1
                || page > 200
                || per < 1
                || per > 100) {
            throw new IllegalArgumentException("REFRESH_UPDATE_QUERY_BOUND");
        }
        if (dateOnly
                ? ChronoUnit.DAYS.between(
                                updates.startInclusive().atZone(SOURCE_ZONE).toLocalDate(),
                                updates.endInclusive().atZone(SOURCE_ZONE).toLocalDate())
                        > 2
                : Duration.between(updates.startInclusive(), updates.endInclusive())
                                .compareTo(Duration.ofHours(24).plusMinutes(2))
                        > 0) {
            throw new IllegalArgumentException("REFRESH_UPDATE_WINDOW_BOUND");
        }
        final var filters = new LinkedHashMap<SearchPath, DataExportFilterValue>();
        // This fixed historical floor never slides forward. Coletas and Consolidação require it.
        filters.put(template.template().businessDateFilter(), history);
        filters.put(
                path,
                dateOnly
                        ? new BusinessDateRange(
                                updates.startInclusive().atZone(SOURCE_ZONE).toLocalDate(),
                                updates.endInclusive().atZone(SOURCE_ZONE).toLocalDate())
                        : updates);
        return new DataExportQueryEncoder()
                .appendQuery(
                        origin.resolve(
                                "/api/analytics/reports/"
                                        + template.template().templateId()
                                        + "/data"),
                        filters,
                        page,
                        per,
                        template == ReportSnapshotTemplate.FRETES ? List.of("id asc") : List.of(),
                        SOURCE_ZONE);
    }
}
