package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class LocalPackageCliAdmissionTest {
    @Test
    void packagedCommandsRefuseIncompleteFlagsBeforeOpeningTheShadowSession() {
        assertRejected(
                output -> LocalArtifactSequenceMain.run(new String[0], output),
                RuntimeExitCategory.CONFIG_AUTH,
                "SEQUENCE_REJECTED");
        assertRejected(
                output -> LocalArtifactScenarioMain.run(new String[0], output),
                RuntimeExitCategory.CONFIG_AUTH,
                "LOCAL_SCENARIO_INPUT_OR_EXECUTION_REJECTED");
        assertRejected(
                output -> LocalCollectionSweepMain.run(new String[0], output),
                RuntimeExitCategory.SOURCE_DQ,
                "LOCAL_SWEEP_INPUT_OR_EXECUTION_REJECTED");
        assertRejected(
                output -> AnalyticLaboratoryMain.run(new String[0], output),
                RuntimeExitCategory.CONFIG_AUTH,
                "ANA_LAB_CONFIG_REJECTED");
        assertRejected(
                output -> RelationalLaboratoryMain.run(new String[0], output),
                RuntimeExitCategory.CONFIG_AUTH,
                "REL_LAB_CONFIG_REJECTED");
        assertRejected(
                output -> LocalDataLaboratoryMain.run(new String[0], output),
                RuntimeExitCategory.CONFIG_AUTH,
                "LOCAL_DATA_INPUT_OR_CONFIG_REJECTED");
        assertRejected(
                output -> LocalRasterArtifactMain.run(new String[0], output),
                RuntimeExitCategory.SOURCE_DQ,
                "LOCAL_RASTER_INPUT_OR_EXECUTION_REJECTED");
    }

    private static void assertRejected(
            final Function<PrintStream, RuntimeExitCategory> command,
            final RuntimeExitCategory expected,
            final String marker) {
        final var bytes = new ByteArrayOutputStream();
        assertEquals(expected, command.apply(new PrintStream(bytes, true, StandardCharsets.UTF_8)));
        final String text = bytes.toString(StandardCharsets.UTF_8);
        assertTrue(text.contains(marker));
        assertFalse(text.contains("ROLLBACK_CONFIRMED"));
    }
}
