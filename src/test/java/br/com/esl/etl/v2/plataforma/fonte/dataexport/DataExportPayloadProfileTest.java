package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataExportPayloadProfileTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void profilesTypesPresenceNullsAndTemporalShapesWithoutKeepingValues() throws IOException {
        final JsonNode response = readFixture("6389-data.synthetic.json");
        final DataExportPageResponse page = new DataExportResponseNormalizer().normalize(response);

        final DataExportPayloadProfile profile =
                DataExportPayloadProfile.fromRecords(page.records());

        assertEquals(2, profile.recordCount());
        final DataExportPayloadFieldProfile updatedAt = field(profile, "updated_at");
        assertEquals(List.of("string"), updatedAt.jsonTypes());
        assertEquals(List.of("offset-date-time"), updatedAt.textualFormats());
        final DataExportPayloadFieldProfile referenceNumber = field(profile, "reference_number");
        assertEquals(2, referenceNumber.presentCount());
        assertEquals(1, referenceNumber.nullCount());
        assertEquals(List.of("blank"), referenceNumber.textualFormats());
        assertFalse(profile.toString().contains("900002"));
        assertFalse(profile.toString().contains("800001"));
    }

    @Test
    void retainsTheLocalDatetimeShapeForTheSyntheticColetaFixture() throws IOException {
        final DataExportPageResponse page =
                new DataExportResponseNormalizer()
                        .normalize(readFixture("6908-data.synthetic.json"));

        final DataExportPayloadProfile profile =
                DataExportPayloadProfile.fromRecords(page.records());

        assertEquals(List.of("local-date-time"), field(profile, "updated_at").textualFormats());
        assertEquals(1, field(profile, "pck_mik_mft_sequence_code").nullCount());
    }

    private DataExportPayloadFieldProfile field(
            final DataExportPayloadProfile profile, final String name) {
        return profile.fields().stream()
                .filter(field -> field.technicalName().equals(name))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Campo sintético ausente: " + name));
    }

    private JsonNode readFixture(final String fixtureName) throws IOException {
        try (InputStream fixture = getClass().getResourceAsStream("/contracts/" + fixtureName)) {
            if (fixture == null) {
                throw new IllegalStateException("Fixture sintética ausente: " + fixtureName);
            }
            return objectMapper.readTree(fixture);
        }
    }
}
