package br.com.esl.etl.v2.plataforma.qualificacao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationDeclaredWirePresenceTest {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"ABSENT", "NULL", "VALUE"})
    void declaredLateralWireRetainsAbsentNullAndEmptySeparately(final String presence)
            throws Exception {
        final var envelope = JsonNodeFactory.instance.objectNode();
        envelope.putObject("wire");
        final var lateral = envelope.putObject("lateral");
        if (presence.equals("NULL")) {
            lateral.putNull("destroyReason");
        } else if (presence.equals("VALUE")) {
            lateral.put("destroyReason", "");
        }
        envelope.putNull("manifestKey").put("sourceRows", 1);
        final var file = folder.resolve("collection.json");
        Files.writeString(file, envelope.toString());
        final var manifest =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "local-wire-expectations-v2")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1");
        manifest.putArray("rows")
                .addObject()
                .put("entity", "COL")
                .put("root", 1)
                .put("component", 1)
                .put("revision", 7)
                .put("correction", false)
                .put("rootType", "INTEGER")
                .put("rootKey", "81917")
                .put("partType", "STRING")
                .put("partKey", "unused")
                .put("componentType", "STRING")
                .put("componentKey", "unused")
                .put("file", "collection.json")
                .put("sha256", QualificationJson.sha256(file));
        final var index = folder.resolve("manifest.json");
        Files.writeString(index, manifest.toString());
        final var oracle =
                new QualificationWireOracle(
                        AnalyticScenarioVariant.BASELINE, new DeclaredWireRows(index));
        final var result = oracle.collectionLateral(1, 7, false, 917).path("destroy_reason");
        assertEquals(presence, result.path("presence").asText());
        assertEquals(presence.equals("VALUE") ? "STRING" : presence, result.path("wire").asText());
        if (presence.equals("VALUE")) {
            assertEquals("", result.path("raw").asText());
        } else {
            assertTrue(result.path("raw").isNull());
        }
        assertEquals(917, result.path("supplementId").longValue());
    }
}
