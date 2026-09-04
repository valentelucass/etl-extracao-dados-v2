package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DataExportResponseFormTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void classifiesOnlyTheSafeStructuralFormsUsedByTheSanitizedEvidence() throws Exception {
        assertEquals(
                DataExportResponseForm.ROOT_ARRAY,
                DataExportResponseForm.from(objectMapper.readTree("[]")));
        assertEquals(
                DataExportResponseForm.ENVELOPE_DATA_ARRAY,
                DataExportResponseForm.from(objectMapper.readTree("{\"data\":[]}")));
        assertEquals(
                DataExportResponseForm.ROOT_OBJECT,
                DataExportResponseForm.from(objectMapper.readTree("{\"id\":1}")));
        assertEquals(
                DataExportResponseForm.ENVELOPE_DATA_OBJECT,
                DataExportResponseForm.from(objectMapper.readTree("{\"data\":{\"id\":1}}")));
        assertTrue(DataExportResponseForm.ENVELOPE_DATA_ARRAY.isArray());
        assertThrows(
                IllegalArgumentException.class,
                () -> DataExportResponseForm.from(objectMapper.readTree("1")));
    }
}
