package br.com.esl.etl.v2.modulos.manifestos.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.BusinessDateRange;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageResponse;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class ManifestoDataExportPageRequestTest {

    @Test
    void bindsOnlyTheContractedServiceDateAndNumericPage() {
        final BusinessDateRange window =
                new BusinessDateRange(LocalDate.of(2036, 1, 1), LocalDate.of(2036, 1, 31));
        final ManifestoDataExportPageRequest request =
                new ManifestoDataExportPageRequest(window, 1);
        assertEquals(6399, ManifestoDataExportPageRequest.TEMPLATE_ID);
        assertEquals(100, ManifestoDataExportPageRequest.PAGE_SIZE);
        assertEquals("manifests.service_date", request.filterName());
        assertEquals(2, request.withPage(2).page());
        assertThrows(
                IllegalArgumentException.class,
                () -> new ManifestoDataExportPageRequest(window, 0));

        final ExtrairPaginaManifestosDataExport useCase =
                new ExtrairPaginaManifestosDataExport(
                        value -> new DataExportPageResponse(java.util.List.of()));
        assertEquals(0, useCase.execute(window, 1).recordCount());
    }
}
