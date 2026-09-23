package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationArtifactOracleMismatchIT {
    @TempDir Path folder;

    @Test
    @Timeout(240)
    void knownIndependentValueDivergenceProducesNonzeroExitAndBoundedSanitizedDiagnostic()
            throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, false, 2);
        final Path outputs = folder.resolve("outputs.json");
        final var document = QualificationJson.read(outputs, 524288);
        for (final var contract : document.path("contracts")) {
            if (contract.path("id").asText().equals("SQL-06")) {
                for (final var cell : contract.path("columns")) {
                    if (cell.path("name").asText().equals("Valor")) {
                        ((ObjectNode) cell).put("value", "333.00");
                    }
                }
            }
        }
        Files.writeString(outputs, document.toString());
        final var oracle = QualificationJson.read(files.oracle(), 16384);
        ((ObjectNode) oracle.path("outputs")).put("sha256", QualificationJson.sha256(outputs));
        Files.writeString(files.oracle(), oracle.toString());
        final var bytes = new ByteArrayOutputStream();
        final var result =
                LocalArtifactScenarioMain.run(
                        new String[] {
                            "run",
                            "--input",
                            files.input().toString(),
                            "--oracle",
                            files.oracle().toString()
                        },
                        new PrintStream(bytes, true, StandardCharsets.UTF_8));
        final var log = bytes.toString(StandardCharsets.UTF_8);
        assertEquals(RuntimeExitCategory.SOURCE_DQ, result, log);
        assertTrue(log.contains("LOCAL_SCENARIO_DIFF"), log);
        assertTrue(log.contains("LOCAL_SCENARIO_ROLLBACK_CONFIRMED"), log);
        assertTrue(bytes.size() < 16384);
        assertTrue(!log.contains("333.00") && !log.contains("SYNTHETIC CLIENT"));
    }
}
