package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import org.junit.jupiter.api.Test;

class QualificationVariantInputsContractTest {
    @Test
    void acceptsOnlyTheClosedSyntheticRootWindowAndTemplates() {
        for (final var template :
                new DataExportTemplate[] {
                    DataExportTemplate.FRETES, DataExportTemplate.LOCALIZACAO_CARGAS
                }) {
            final var source = QualificationVariantInputs.source(template, 1, 2, 1);
            assertNotNull(source.contractRelease(template));
            for (final int first : new int[] {0, 33}) {
                assertEquals(
                        "QUAL_VARIANT_SOURCE_LIMIT",
                        assertThrows(
                                        IllegalArgumentException.class,
                                        () ->
                                                QualificationVariantInputs.source(
                                                        template, first, 1, 1))
                                .getMessage());
            }
            for (final int pageSize : new int[] {0, 17}) {
                assertThrows(
                        IllegalArgumentException.class,
                        () -> QualificationVariantInputs.source(template, 1, 1, pageSize));
            }
        }
        assertEquals(
                "QUAL_VARIANT_TEMPLATE",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationVariantInputs.source(
                                                DataExportTemplate.COLETAS, 1, 1, 1))
                        .getMessage());
    }

    @Test
    void hydrationRejectsOutOfWindowAndAcceptsKnownSyntheticFreight() {
        assertNotNull(
                QualificationVariantInputs.hydration("INTEGER:300001")
                        .contractRelease(DataExportTemplate.FRETES));
        for (final String key :
                new String[] {null, "INTEGER:300000", "INTEGER:300033", "STRING:300001"}) {
            assertEquals(
                    "QUAL_VARIANT_HYDRATION_TARGET",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> QualificationVariantInputs.hydration(key))
                            .getMessage());
        }
    }
}
