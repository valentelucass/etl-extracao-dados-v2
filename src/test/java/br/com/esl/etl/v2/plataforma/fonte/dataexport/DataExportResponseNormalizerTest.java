package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class DataExportResponseNormalizerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataExportResponseNormalizer normalizer = new DataExportResponseNormalizer();

    @Test
    void normalizesEnvelopeArray() throws Exception {
        final DataExportPageResponse page =
                normalizer.normalize(
                        objectMapper.readTree("{\"data\":[{\"id\":\"a\"},{\"id\":\"b\"}]}"));

        assertEquals(2, page.records().size());
        assertEquals("a", page.records().get(0).path("id").asText());
    }

    @Test
    void normalizesUnitObjectIntoSingleRecordList() throws Exception {
        final DataExportPageResponse page =
                normalizer.normalize(objectMapper.readTree("{\"id\":\"a\"}"));

        assertEquals(1, page.records().size());
        assertEquals("a", page.records().get(0).path("id").asText());
    }

    @Test
    void rejectsAnEmptyObjectInsteadOfTreatingItAsARecord() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () -> normalizer.normalize(objectMapper.readTree("{}")));
    }

    @Test
    void rejectsAnErrorEnvelopeInsteadOfTreatingItAsARecord() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        normalizer.normalize(
                                objectMapper.readTree("{\"error\":\"contrato inválido\"}")));
    }

    @Test
    void rejectsAnEnvelopeWithDataAndErrorsInsteadOfTreatingItAsAnEmptyTerminalPage()
            throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        normalizer.normalize(
                                objectMapper.readTree(
                                        "{\"data\":[],\"errors\":[\"contrato inválido\"]}")));
    }

    @Test
    void rejectsAnEnvelopeWithDataAndErrorInsteadOfDeliveringPartialData() throws Exception {
        assertThrows(
                IllegalStateException.class,
                () ->
                        normalizer.normalize(
                                objectMapper.readTree(
                                        "{\"data\":[{\"id\":\"a\"}],\"error\":\"contrato inválido\"}")));
    }
}
