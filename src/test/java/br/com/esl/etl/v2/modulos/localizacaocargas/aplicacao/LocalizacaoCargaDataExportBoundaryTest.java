package br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LocalizacaoCargaDataExportBoundaryTest {

    @Test
    void keepsTheLocal8656CapabilityWithoutEnablingExternalExecution() {
        var capability = LocalizacaoCargaShadowCapability.localDenyAll();
        assertEquals(8656, capability.templateId());
        assertEquals("localizacao_cargas", capability.entity());
        assertFalse(capability.networkAllowed());
        assertFalse(capability.publicationAllowed());
        assertFalse(capability.dispatcherAllowed());
    }
}
