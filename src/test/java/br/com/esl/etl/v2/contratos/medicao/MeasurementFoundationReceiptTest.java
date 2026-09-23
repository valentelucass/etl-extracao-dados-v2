package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportPageStreamerMultiscaleMeasurementTest;
import br.com.esl.etl.v2.plataforma.fonte.graphql.GraphQlPageStreamerMultiscaleMeasurementTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class MeasurementFoundationReceiptTest {

    private static final String RECEIPT_FILE = "measurement-foundation-receipt.json";

    @Test
    void writesTheSixGovernedSyntheticMeasurementsOnlyWhenExplicitlyEnabled() {
        Assumptions.assumeTrue(
                Boolean.getBoolean("v2.measurement.receipt"),
                "Receipt V2-050 exige opt-in explícito.");

        final List<MeasurementRun> runs =
                List.of(
                        DataExportPageStreamerMultiscaleMeasurementTest
                                .runHealthyScenarioForReceipt(16),
                        DataExportPageStreamerMultiscaleMeasurementTest
                                .runHealthyScenarioForReceipt(256),
                        DataExportPageStreamerMultiscaleMeasurementTest
                                .runHealthyScenarioForReceipt(4096),
                        GraphQlPageStreamerMultiscaleMeasurementTest.runHealthyScenarioForReceipt(
                                16),
                        GraphQlPageStreamerMultiscaleMeasurementTest.runHealthyScenarioForReceipt(
                                256),
                        GraphQlPageStreamerMultiscaleMeasurementTest.runHealthyScenarioForReceipt(
                                4096));

        assertEquals(6, runs.size());
        assertTrue(runs.stream().allMatch(run -> run.assessment().proven()));
        final Path buildDirectory =
                Path.of(System.getProperty("project.build.directory", "target"));
        final Path receipt =
                MeasurementReceiptWriter.forTargetDirectory(buildDirectory)
                        .write(RECEIPT_FILE, runs);

        assertTrue(Files.isRegularFile(receipt));
        assertEquals(
                buildDirectory
                        .toAbsolutePath()
                        .normalize()
                        .resolve("v2-050-measurement")
                        .resolve(RECEIPT_FILE),
                receipt);
    }
}
