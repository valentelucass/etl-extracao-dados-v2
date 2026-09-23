package br.com.esl.etl.v2.modulos.fretes.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageRequest;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.SourceDateTimeRange;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class FreteDataExportPageRequestFactoryTest {

    @Test
    void createsRequestForTemplate6389() {
        final DataExportPageRequest request =
                new FreteDataExportPageRequestFactory()
                        .create(
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 13), LocalDate.of(2026, 8, 13)),
                                new SourceDateTimeRange(
                                        Instant.parse("2026-08-13T03:00:00Z"),
                                        Instant.parse("2026-08-14T02:59:59Z")),
                                1);

        assertEquals(DataExportTemplate.FRETES, request.template());
        assertEquals(6389, request.template().templateId());
    }
}
