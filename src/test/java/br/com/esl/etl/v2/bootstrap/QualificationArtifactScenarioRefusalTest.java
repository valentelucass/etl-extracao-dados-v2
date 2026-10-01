package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationArtifactScenarioRefusalTest {
    @TempDir Path folder;

    @Test
    void baselineRasterSelectionAndSequenceAdmissionRejectBeforeJdbc() throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, false, 2);
        final var scenario =
                new LocalArtifactScenario(files.input(), files.oracle(), CancellationToken.none());
        assertNotNull(scenario.rasterGateway(2, 1));
        for (final var selection : new int[][] {{3, 1}, {2, 2}}) {
            assertEquals(
                    "LOCAL_SCENARIO_RASTER_SELECTION",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> scenario.rasterGateway(selection[0], selection[1]))
                            .getMessage());
        }
        assertEquals(
                "SEQUENCE_REQUIRES_INTEGRAL_INPUTS",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        scenario.previewSequence(
                                                null,
                                                null,
                                                AnalyticScenarioObserver.NONE,
                                                CancellationToken.none()))
                        .getMessage());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "target",
                "mode",
                "windowStart",
                "support",
                "missingFamily",
                "duplicateFamily",
                "raster"
            })
    void invalidSelectionIsRejectedBeforeSessionExists(final String field) throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, false, 2);
        final var input = (ObjectNode) QualificationJson.read(files.input(), 16384);
        switch (field) {
            case "missingFamily" ->
                    ((com.fasterxml.jackson.databind.node.ArrayNode) input.path("expansions"))
                            .remove(0);
            case "duplicateFamily" ->
                    ((com.fasterxml.jackson.databind.node.ArrayNode) input.path("expansions"))
                            .set(1, input.path("expansions").get(0));
            case "raster" -> input.remove("raster");
            default -> input.put(field, "INVALID_SELECTION");
        }
        Files.writeString(files.input(), input.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalArtifactScenario(
                                files.input(), files.oracle(), CancellationToken.none()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "inputSha256",
                "schemaSha256",
                "runtimeSha256",
                "origin",
                "outputs",
                "wire",
                "facts"
            })
    void alteredOracleBindingCannotAuthorizeComparison(final String field) throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, false, 2);
        final var oracle = (ObjectNode) QualificationJson.read(files.oracle(), 16384);
        if (oracle.path(field).isObject()) {
            ((ObjectNode) oracle.path(field)).put("sha256", "a".repeat(64));
        } else {
            oracle.put(field, field.equals("origin") ? "PROVIDER_APPROVED" : "a".repeat(64));
        }
        Files.writeString(files.oracle(), oracle.toString());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new LocalArtifactScenario(
                                files.input(), files.oracle(), CancellationToken.none()));
    }
}
