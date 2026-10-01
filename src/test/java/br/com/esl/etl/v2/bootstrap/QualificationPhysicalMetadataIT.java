package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class QualificationPhysicalMetadataIT {
    @Test
    @Timeout(30)
    void differentNullabilityInTheExpectedPhysicalSchemaIsRefusedByTheActualCatalog()
            throws Exception {
        final var document =
                QualificationJson.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/physical-columns.v105.json"),
                        524288);
        final var first = (ObjectNode) document.path("columns").get(0);
        first.put("nullable", !first.path("nullable").booleanValue());
        final var guard = new QualificationPhysicalMetadata(105, document);
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            assertEquals(
                    "QUAL_PHYSICAL_SCHEMA_DRIFT",
                    assertThrows(SQLException.class, () -> guard.verify(session)).getMessage());
            assertEquals(0, session.openControlledStatements());
        }
    }

    @Test
    @Timeout(30)
    void allPhysicalColumnsMatchTheLocalEpochIncludingTechnicalColumns() throws Exception {
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            assertEquals(971, new QualificationPhysicalMetadata(105).verify(session));
        }
    }
}
