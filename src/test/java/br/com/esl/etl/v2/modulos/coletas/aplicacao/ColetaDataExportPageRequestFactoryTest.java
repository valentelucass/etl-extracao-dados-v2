package br.com.esl.etl.v2.modulos.coletas.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ColetaDataExportPageRequestFactoryTest {

    @Test
    void createsRequestForTemplate6908() {
        final DataExportPageRequest request =
                new ColetaDataExportPageRequestFactory()
                        .create(
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                                new SourceDateTimeRange(
                                        Instant.parse("2026-08-13T03:00:00Z"),
                                        Instant.parse("2026-08-14T02:59:59Z")),
                                1);

        assertEquals(DataExportTemplate.COLETAS, request.template());
        assertEquals(6908, request.template().templateId());
    }
}
