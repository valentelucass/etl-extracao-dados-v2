package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.raster.RasterArtifact;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationRasterArtifactTest {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"2018-11-04T00:30:00", "2019-02-16T23:30:00", "not-a-time"})
    void invalidCivilTimeAndDstAreCharacterizedBeforeSql(final String time) throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 2, false);
        QualificationRasterArtifactFixtures.mutate(
                file,
                "body",
                values -> {
                    for (final var value : values) {
                        ((ObjectNode) value).put("DataHoraPrevIni", time);
                    }
                });
        final var result = new RasterArtifact(file).characterize(CancellationToken.none());
        assertEquals(6, result.invalid());
        assertFalse(result.executable());
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                LocalRasterArtifactMain.run(
                        new String[] {"capture", "--artifact", file.toString()},
                        new PrintStream(new ByteArrayOutputStream())));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MISSING", "PARENT", "DUPLICATE", "REVISION"})
    void lateralIdentityIsNeverSynthesizedOrRepairedFromPositions(final String kind)
            throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 2, false);
        QualificationRasterArtifactFixtures.mutate(
                file,
                "bindings",
                values -> {
                    final var rows = (ArrayNode) values;
                    switch (kind) {
                        case "MISSING" -> rows.remove(0);
                        case "PARENT" ->
                                ((ObjectNode) rows.get(1))
                                        .put("tripKey", "synthetic-another-parent");
                        case "DUPLICATE" -> rows.add(rows.get(0));
                        case "REVISION" -> ((ObjectNode) rows.get(0)).put("revision", 2);
                        default -> throw new AssertionError();
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> new RasterArtifact(file).characterize(CancellationToken.none()));
    }

    @Test
    void changedPayloadAfterCharacterizationIsRefusedOnTheActualFetch() throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 2, false);
        final var artifact = new RasterArtifact(file);
        artifact.characterize(CancellationToken.none());
        Files.writeString(folder.resolve("body.json"), "[]");
        assertThrows(
                IllegalArgumentException.class, () -> artifact.gateway().fetch(artifact.window()));
    }

    @Test
    void incompleteStopsAndForeignContractAreNotPositiveLocalEvidence() throws Exception {
        final var file = QualificationRasterArtifactFixtures.write(folder, 2, false);
        QualificationRasterArtifactFixtures.mutate(
                file, "manifest", value -> ((ObjectNode) value).put("stops", 7));
        assertFalse(new RasterArtifact(file).characterize(CancellationToken.none()).executable());
        final var manifest = (ObjectNode) QualificationJson.read(file, 131072);
        manifest.put("contract", "provider-claimed-contract");
        Files.writeString(file, manifest.toString());
        assertThrows(IllegalArgumentException.class, () -> new RasterArtifact(file));
    }
}
