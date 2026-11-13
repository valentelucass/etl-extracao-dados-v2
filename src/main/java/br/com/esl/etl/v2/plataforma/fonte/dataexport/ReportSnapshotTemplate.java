package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import java.net.URI;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

/** Explicit finite capture routes; these entries do not enable Main, SQL or publication. */
public enum ReportSnapshotTemplate {
    CONSOLIDACAO_DETALHADA(
            DataExportTemplate.CONSOLIDACAO_DETALHADA,
            "issue_date",
            "consolidacoes-11067-0762-v2.json"),
    OCORRENCIAS(DataExportTemplate.OCORRENCIAS, "occurrence_at", "ocorrencias-12072-0762-v2.json"),
    COLETAS(DataExportTemplate.COLETAS, "request_date", null),
    FRETES(DataExportTemplate.FRETES, "service_at", null),
    MANIFESTOS(DataExportTemplate.MANIFESTOS, null, null),
    COTACOES(DataExportTemplate.COTACOES, "requested_at", null),
    LOCALIZACAO_CARGAS(DataExportTemplate.LOCALIZACAO_CARGAS, "service_at", null),
    CONTAS_A_PAGAR(DataExportTemplate.CONTAS_A_PAGAR, "issue_date", null),
    FATURAS_POR_CLIENTE(DataExportTemplate.FATURAS_POR_CLIENTE, "service_at", null),
    INVENTARIO(DataExportTemplate.INVENTARIO, "started_at", null),
    SINISTROS(DataExportTemplate.SINISTROS, "opening_at_date", null),
    COLETAS_EXCLUIDAS(DataExportTemplate.COLETAS_EXCLUIDAS, "deleted_at", null);

    private final DataExportTemplate template;
    private final String rowDate;
    private final String contract;

    ReportSnapshotTemplate(
            final DataExportTemplate template, final String rowDate, final String contract) {
        this.template = template;
        this.rowDate = rowDate;
        this.contract = contract;
    }

    public DataExportTemplate template() {
        return template;
    }

    public String rowDate() {
        return rowDate;
    }

    public String contractResource() {
        return contract == null ? null : "contracts/dataexport/" + contract;
    }

    public URI info(final URI origin) {
        requireOrigin(origin);
        return origin.resolve("/api/analytics/reports/" + template.templateId() + "/info");
    }

    /**
     * Only business dates are sent. In particular CAP.created_at and occurrence.updated_at stay
     * absent.
     */
    public URI query(
            final URI origin, final BusinessDateRange dates, final int page, final int per) {
        requireOrigin(origin);
        if (dates == null
                || ChronoUnit.DAYS.between(dates.startInclusive(), dates.endInclusive()) > 7
                || page < 1
                || page > 200
                || per < 1
                || per > 100) {
            throw new IllegalArgumentException("SNAPSHOT_REQUEST_BOUND");
        }
        final List<String> order =
                switch (this) {
                    case CONTAS_A_PAGAR -> List.of("issue_date desc");
                    case FRETES -> List.of("id asc");
                    default -> List.of();
                };
        return new DataExportQueryEncoder()
                .appendQuery(
                        origin.resolve("/api/analytics/reports/" + template.templateId() + "/data"),
                        Map.of(template.businessDateFilter(), dates),
                        page,
                        per,
                        order,
                        ZoneId.of("America/Sao_Paulo"));
    }

    private static void requireOrigin(final URI origin) {
        if (origin == null
                || !"https".equals(origin.getScheme())
                || !"rodogarcia.eslcloud.com.br".equals(origin.getHost())
                || origin.getRawUserInfo() != null
                || origin.getRawQuery() != null
                || origin.getRawFragment() != null
                || (origin.getPort() != -1 && origin.getPort() != 443)
                || !(origin.getRawPath().isEmpty() || "/".equals(origin.getRawPath()))) {
            throw new IllegalArgumentException("SNAPSHOT_CLOSED_ORIGIN");
        }
    }
}
