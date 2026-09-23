package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class LocalizacaoCargaDataExportBoundaryTest {

    @Test
    void freezesTheLocal8656RequestWithoutEnablingAnyExternalCapability() {
        var request =
                new LocalizacaoCargaDataExportPageRequest(
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), 1);

        assertEquals(8656, request.templateId());
        assertEquals(100, request.pageSize());
        assertEquals("freights.service_at", request.filterName());
        assertEquals("sequence_number asc", request.orderBy());
        assertEquals(2, request.withPage(2).page());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalizacaoCargaDataExportPageRequest(
                                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1), 1));

        var capability = LocalizacaoCargaShadowCapability.localDenyAll();
        assertEquals(8656, capability.templateId());
        assertEquals("localizacao_cargas", capability.entity());
        assertFalse(capability.networkAllowed());
        assertFalse(capability.publicationAllowed());
        assertFalse(capability.dispatcherAllowed());
    }
}
