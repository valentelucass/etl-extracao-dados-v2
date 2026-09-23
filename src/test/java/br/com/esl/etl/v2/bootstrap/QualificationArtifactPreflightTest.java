package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationArtifactPreflightTest {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(strings = {"EXPANSION", "RASTER", "RELATION", "WIRE", "FACT", "OUTPUT", "INPUT"})
    void mutatedFileBetweenConstructionAndExecutionIsRejectedBeforeAnySessionUse(final String kind)
            throws Exception {
        final var paths = QualificationArtifactScenarioFixtures.write(folder, false, 2);
        final var scenario =
                new LocalArtifactScenario(paths.input(), paths.oracle(), CancellationToken.none());
        final Path changed =
                switch (kind) {
                    case "EXPANSION" -> folder.resolve("cap/page-1.json");
                    case "RASTER" -> folder.resolve("raster/body.json");
                    case "RELATION" -> folder.resolve("relations/batch-1.json");
                    case "WIRE" ->
                            folder.resolve("wire")
                                    .resolve(
                                            QualificationJson.read(
                                                            folder.resolve("wire/manifest.json"),
                                                            262144)
                                                    .path("rows")
                                                    .get(0)
                                                    .path("file")
                                                    .asText());
                    case "FACT" -> folder.resolve("facts/mat03.json");
                    case "OUTPUT" -> folder.resolve("outputs.json");
                    case "INPUT" -> paths.input();
                    default -> throw new AssertionError();
                };
        Files.writeString(changed, "[]");
        // A null session is deliberate: only a pre-SQL artifact refusal can satisfy this assertion.
        assertThrows(
                IllegalArgumentException.class,
                () -> scenario.execute(null, CancellationToken.none()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"phase-0-observation-3-page-0.json", "phase-0.json", "program.json"})
    void sweepAlsoRechecksAllFourObservationsBeforeStartingItsExecutionContext(final String member)
            throws Exception {
        final var file =
                QualificationSweepArtifactFixtures.write(folder, LocalDate.of(2036, 4, 2), 3);
        final var program = new LocalCollectionSweepProgram(file, CancellationToken.none());
        Files.writeString(folder.resolve(member), "[]");
        assertThrows(
                IllegalArgumentException.class,
                () -> program.execute(null, CancellationToken.none()));
    }
}
