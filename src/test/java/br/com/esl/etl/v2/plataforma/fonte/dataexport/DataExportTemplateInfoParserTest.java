package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.contrato.ContractCompatibilityPolicy;
import br.com.esl.etl.v2.plataforma.contrato.ContractDriftException;
import br.com.esl.etl.v2.plataforma.contrato.ContractValidator;
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
    void readsObservedFretesMetadataWithoutTreatingTheSyntheticCatalogAsALiveRelease()
            throws IOException {
        final var parsed = parser.parse(readFixture("6389-info.observed-structure-20261024.json"));
        assertEquals(114, parsed.fields().size());
        assertEquals(16, parsed.filters().size());
        assertEquals(
                114,
                parsed.fields().stream().filter(field -> field.declaredType().isEmpty()).count());
        assertEquals(
                "date",
                parsed.filters().stream()
                        .filter(field -> field.technicalName().equals("freights.service_at"))
                        .findFirst()
                        .orElseThrow()
                        .declaredType()
                        .orElseThrow());
        final var info =
                new DataExportTemplateInfo(
                        DataExportTemplate.FRETES,
                        200,
                        java.util.Optional.empty(),
                        parsed.fields(),
                        parsed.filters());
        final var observed = DataExportContractAdapter.metadata(info);
        final var baseline = DataExportFirstWaveContractCatalog.release(DataExportTemplate.FRETES);
        final var validator =
                new ContractValidator(
                        baseline,
                        ContractCompatibilityPolicy.create(
                                "observed-metadata-regression-v1",
                                baseline.contractFingerprint(),
                                List.of()));

        assertEquals(20, validator.classifyMetadata(observed).breakingCount());
        assertThrows(ContractDriftException.class, () -> validator.validateMetadata(observed));
    }

    @Test
    void qualifiesFiltersWhoseTableAndFieldAreDeclaredSeparately() throws IOException {
        final var parsed =
                parser.parse(
                        objectMapper.readTree(
                                """
                                {"fields":["id"],"filters":[
                                  {"table":"freights","field":"service_at","type":"date"},
                                  {"table":"scopes","field":"by_updated_at","type":"datetime"}
                                ]}
                                """));

        assertEquals(
                List.of("freights.service_at", "scopes.by_updated_at"),
                technicalNames(parsed.filters()));
        assertEquals("date", parsed.filters().get(0).declaredType().orElseThrow());
    }

    @Test
    void distinguishesTheSameFilterFieldInDifferentTables() throws IOException {
        final var parsed =
                parser.parse(
                        objectMapper.readTree(
                                """
                                {"fields":["id"],"filters":[
                                  {"table":"freights","field":"status"},
                                  {"table":"picks","field":"status"}
                                ]}
                                """));

        assertEquals(List.of("freights.status", "picks.status"), technicalNames(parsed.filters()));
    }

    @Test
    void preservesExplicitNamesAndAlreadyQualifiedFields() throws IOException {
        final var parsed =
                parser.parse(
                        objectMapper.readTree(
                                """
                                {"fields":[{"table":"freights","field":"id"}],"filters":[
                                  {"name":"request_date","table":"picks","field":"request_date"},
                                  {"table":"scopes","field":"scopes.by_updated_at"},
                                  {"field":"service_at"}
                                ]}
                                """));

        assertEquals(List.of("id"), technicalNames(parsed.fields()));
        assertEquals(
                List.of("request_date", "scopes.by_updated_at", "service_at"),
                technicalNames(parsed.filters()));
    }

    @Test
    void rejectsAnAmbiguousTableAndQualifiedField() throws IOException {
        final JsonNode document =
                objectMapper.readTree(
                        """
                        {"fields":["id"],"filters":[
                          {"table":"freights","field":"picks.status"}
                        ]}
                        """);

        assertThrows(IllegalStateException.class, () -> parser.parse(document));
    }

    @Test
    void rejectsInvalidTableDeclarationsInsteadOfDroppingThem() throws IOException {
        for (final String table : List.of("null", "42", "\"\"", "\"bad table\"")) {
            final JsonNode document =
                    objectMapper.readTree(
                            "{\"fields\":[\"id\"],\"filters\":[{\"table\":"
                                    + table
                                    + ",\"field\":\"service_at\"}]}");

            assertThrows(IllegalStateException.class, () -> parser.parse(document));
        }
    }

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
