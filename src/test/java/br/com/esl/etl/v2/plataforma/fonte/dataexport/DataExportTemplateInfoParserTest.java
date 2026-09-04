package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataExportTemplateInfoParserTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DataExportTemplateInfoParser parser = new DataExportTemplateInfoParser();

    @Test
    void parsesArrayAndObjectMetadataWithoutRetainingPayloadValues() throws IOException {
        final DataExportTemplateInfoParser.ParsedTemplateInfo coletas =
                parser.parse(readFixture("6908-info.synthetic.json"));
        final DataExportTemplateInfoParser.ParsedTemplateInfo fretes =
                parser.parse(readFixture("6389-info.synthetic.json"));

        assertEquals(
                List.of("id", "sequence_code", "updated_at"), technicalNames(coletas.fields()));
        assertEquals(
                List.of("request_date", "scopes.by_updated_at"), technicalNames(coletas.filters()));
        assertEquals(
                List.of(
                        "corporation_sequence_number",
                        "fit_p_m_pck_sequence_code",
                        "finished_at",
                        "fit_dpn_performance_finished_at",
                        "updated_at",
                        "reference_number"),
                technicalNames(fretes.fields()));
        assertEquals(
                List.of("freights.service_at", "scopes.by_updated_at"),
                technicalNames(fretes.filters()));
    }

    @Test
    void rejectsA2xxErrorEnvelopeInsteadOfTreatingItAsMetadata() throws IOException {
        final JsonNode errorEnvelope =
                objectMapper.readTree("{\"data\":{},\"errors\":[{\"message\":\"synthetic\"}]}");

        assertThrows(IllegalStateException.class, () -> parser.parse(errorEnvelope));
    }

    @Test
    void rejectsMissingFieldDeclarations() throws IOException {
        final JsonNode missingFields = objectMapper.readTree("{\"filters\":[]}");

        assertThrows(IllegalStateException.class, () -> parser.parse(missingFields));
    }

    private JsonNode readFixture(final String fixtureName) throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/" + fixtureName)) {
            if (fixture == null) {
                throw new IllegalStateException("Fixture sintética ausente: " + fixtureName);
            }
            return objectMapper.readTree(fixture);
        }
    }

    private List<String> technicalNames(final List<DataExportMetadataField> fields) {
        return fields.stream().map(DataExportMetadataField::technicalName).toList();
    }
}
