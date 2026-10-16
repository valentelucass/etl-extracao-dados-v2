package br.com.esl.etl.v2.plataforma.fonte.dataexport;

/** OBS-PER-001: page one/per one; pagination candidates do not establish canonical identity. */
public record DataExportObservationRequest(DataExportPageRequest page) {
    public DataExportObservationRequest {
        if (page == null
                || page.template() == DataExportTemplate.COLETAS
                || page.page() != 1
                || page.pageSize() != 1
                || page.updatedAtWindow().isPresent()
                || !page.businessDateWindow()
                        .startInclusive()
                        .equals(page.businessDateWindow().endInclusive())
                || !page.orderBy().equals(page.template().defaultOrderBy())) {
            throw new IllegalArgumentException("OBS_REQUEST_BOUND");
        }
    }

    public String paginationRoot() {
        return switch (page.template()) {
            case FRETES, FATURAS_POR_CLIENTE -> "id";
            case MANIFESTOS, COTACOES, INVENTARIO, SINISTROS -> "sequence_code";
            case LOCALIZACAO_CARGAS -> "corporation_sequence_number";
            case CONTAS_A_PAGAR -> throw new IllegalArgumentException("ROOT_PER_UNVERIFIED");
            default -> throw new IllegalArgumentException("OBS_TEMPLATE_REFUSED");
        };
    }
}
