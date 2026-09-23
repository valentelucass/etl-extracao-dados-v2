package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class QualificationPhysicalMetadataTest {
    @Test
    void physicalSchemaContractRejectsCoercedTypesAndAnUnapprovedOriginBeforeJdbc()
            throws Exception {
        final var source =
                QualificationJson.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/physical-columns.v098.json"),
                        524288);
        for (final var field : List.of("nullable", "bytes", "precision", "scale", "ordinal")) {
            final var changed = source.deepCopy();
            ((ObjectNode) changed.path("columns").get(0)).put(field, "false");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new QualificationPhysicalMetadata(changed));
        }
        final var changed = (ObjectNode) source.deepCopy();
        changed.put("sourceSha256", "0".repeat(64));
        assertThrows(
                IllegalArgumentException.class, () -> new QualificationPhysicalMetadata(changed));
    }
}
