package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IntegralArtifactAdmissionTest {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void admitsTwoFullyDeclaredCivilPeriodsAndIndependentOracles(final boolean alternate)
            throws Exception {
        final int roots = alternate ? 24 : 2;
        final var input = IntegralArtifactFixtures.write(folder, alternate, roots);
        final var scenario =
                new LocalArtifactScenario(
                        input, folder.resolve("oracle.json"), CancellationToken.none());
        assertEquals(roots, scenario.roots());
        scenario.verifyFiles(CancellationToken.none());
    }

    @Test
    void completeModeRejectsEveryMissingSourceAndSupportBeforeSql() throws Exception {
        final var input = IntegralArtifactFixtures.write(folder, false, 2);
        final var original = (ObjectNode) QualificationJson.read(input, 16384);
        for (final String family : new String[] {"COL", "FRE", "MAN", "COT", "LOC", "USER"}) {
            final var candidate = original.deepCopy();
            ((ObjectNode) candidate.path("sources")).remove(family);
            IntegralArtifactFixtures.save(input, candidate);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new DeclaredIntegralInputs(
                                    PinnedLocalJson.open(input, 16384), CancellationToken.none()));
        }
        for (final String member :
                new String[] {
                    "raster",
                    "expansions",
                    "relations",
                    "references",
                    "supplements",
                    "sweep",
                    "logicalClock",
                    "zone",
                    "revision"
                }) {
            final var candidate = original.deepCopy();
            candidate.remove(member);
            IntegralArtifactFixtures.save(input, candidate);
            assertThrows(
                    IllegalArgumentException.class,
                    () ->
                            new DeclaredIntegralInputs(
                                    PinnedLocalJson.open(input, 16384), CancellationToken.none()));
        }
    }

    @ParameterizedTest
    @CsvSource({
        "expansion,labels",
        "dimensions,registry",
        "fleet,aliases",
        "regions,rows",
        "tariffs,rows"
    })
    void nestedReferenceMembersAreClosedBeforeSql(final String family, final String collection)
            throws Exception {
        final Path input = IntegralArtifactFixtures.write(folder, false, 2);
        final var root = (ObjectNode) IntegralArtifactFixtures.read(input);
        final Path manifestFile = folder.resolve(root.path("references").path("file").textValue());
        final var manifest = (ObjectNode) IntegralArtifactFixtures.read(manifestFile);
        final var pin = (ObjectNode) manifest.path("files").path(family);
        final Path dataFile = manifestFile.getParent().resolve(pin.path("file").textValue());
        final var data = IntegralArtifactFixtures.read(dataFile);
        ((ObjectNode) data.path(collection).path(0)).put("unrecognized", "synthetic");
        IntegralArtifactFixtures.save(dataFile, data);
        pin.put("sha256", QualificationJson.sha256(dataFile));
        IntegralArtifactFixtures.save(manifestFile, manifest);
        root.set("references", IntegralArtifactFixtures.pin(folder, manifestFile));
        IntegralArtifactFixtures.save(input, root);
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () ->
                                new DeclaredIntegralInputs(
                                        PinnedLocalJson.open(input, 16384),
                                        CancellationToken.none()));
        assertEquals("INTEGRAL_REFERENCE_UNKNOWN_FIELD", failure.getMessage());
    }
}
