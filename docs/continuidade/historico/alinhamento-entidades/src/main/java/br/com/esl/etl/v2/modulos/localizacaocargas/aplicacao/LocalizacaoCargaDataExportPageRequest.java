package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import java.time.LocalDate;
import java.util.Objects;

/** Requisição local 8656; a ordem existe apenas para paridade e não governa frescor. */
public record LocalizacaoCargaDataExportPageRequest(
        LocalDate startInclusive, LocalDate endExclusive, int page) {
    public static final int TEMPLATE_ID = 8656;
    public static final int PAGE_SIZE = 100;
    public static final String ORDER_BY = "sequence_number asc";

    public LocalizacaoCargaDataExportPageRequest {
        Objects.requireNonNull(startInclusive, "O início local de service_at é obrigatório.");
        Objects.requireNonNull(endExclusive, "O fim exclusivo de service_at é obrigatório.");
        if (!endExclusive.isAfter(startInclusive)) {
            throw new IllegalArgumentException("A janela local deve ser [start,endExclusive).");
        }
        if (page < 1) {
            throw new IllegalArgumentException("A página deve ser positiva.");
        }
    }

    public int templateId() {
        return TEMPLATE_ID;
    }

    public int pageSize() {
        return PAGE_SIZE;
    }

    public String filterName() {
        return "freights.service_at";
    }

    public String orderBy() {
        return ORDER_BY;
    }

    public LocalizacaoCargaDataExportPageRequest withPage(final int nextPage) {
        return new LocalizacaoCargaDataExportPageRequest(startInclusive, endExclusive, nextPage);
    }
}
