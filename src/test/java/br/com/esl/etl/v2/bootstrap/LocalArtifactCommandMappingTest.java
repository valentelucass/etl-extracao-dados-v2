package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcAnalyticCollectionSweep;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepApplicability;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewAssessment;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepPreviewBlockReason;
import br.com.esl.etl.v2.plataforma.reconciliacao.sweep.SweepScope;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalArtifactCommandMappingTest {
    @TempDir Path folder;

    @Test
    void sweepCliPrintsAllFourSyntheticObservationsAndNeverClaimsNominalApply() throws Exception {
        final var file =
                QualificationSweepArtifactFixtures.write(folder, LocalDate.of(2036, 4, 2), 3);
        final var output = new ByteArrayOutputStream();
        final var print = new PrintStream(output);
        final String[] arguments = {"observe", "--input", file.toString()};
        final var responsibility =
                new SweepResponsibilityPlanner.Responsibility(
                        "synthetic",
                        "COL",
                        "",
                        SweepScope.ResponsibilityKind.ROOT,
                        SweepApplicability.BLOCKED,
                        "",
                        "",
                        "",
                        "synthetic reason");
        final var preview =
                new SweepResponsibilityPlanner.Preview(
                        responsibility,
                        new SweepPreviewAssessment(
                                SweepPreviewAssessment.Disposition.BLOCKED,
                                SweepPreviewBlockReason.APPLICABILITY_NOT_ENABLED));
        final var observation =
                new LocalAnalyticCollectionSweep.Observation(
                        null,
                        new JdbcAnalyticCollectionSweep.Receipt(2, 1, 0, 1),
                        Collections.nCopies(33, preview));
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                LocalCollectionSweepMain.run(
                        arguments,
                        print,
                        (program, token) ->
                                List.of(observation, observation, observation, observation)));
        assertTrue(output.toString().contains("LOCAL_SWEEP_OBSERVATION phase=4 candidates=2"));
        assertTrue(output.toString().contains("LOCAL_SWEEP_PREVIEW responsibility=synthetic"));
        assertTrue(output.toString().contains("nominal_apply=NONE"));
        assertEquals(
                RuntimeExitCategory.CANCELLED,
                LocalCollectionSweepMain.run(
                        arguments,
                        print,
                        (program, token) -> {
                            throw new ResilienceCancelledException();
                        }));
    }

    @Test
    void scenarioCliReportsRollbackAfterItsSyntheticResultOnly() throws Exception {
        final var input = IntegralArtifactFixtures.write(folder, false, 2);
        final String[] arguments = {
            "run", "--input", input.toString(), "--oracle", folder.resolve("oracle.json").toString()
        };
        final var output = new ByteArrayOutputStream();
        final var print = new PrintStream(output);
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                LocalArtifactScenarioMain.run(arguments, print, (scenario, token, sink) -> true));
        assertTrue(output.toString().contains("LOCAL_SCENARIO_ROLLBACK_CONFIRMED"));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                LocalArtifactScenarioMain.run(arguments, print, (scenario, token, sink) -> false));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                LocalArtifactScenarioMain.run(
                        arguments,
                        print,
                        (scenario, token, sink) -> {
                            throw new SQLException("synthetic", "", 53201);
                        }));
        assertEquals(
                RuntimeExitCategory.CANCELLED,
                LocalArtifactScenarioMain.run(
                        arguments,
                        print,
                        (scenario, token, sink) -> {
                            throw new ResilienceCancelledException();
                        }));
    }

    @Test
    void sequenceCliReportsRollbackOnlyForAnAdmittedSyntheticSequence() throws Exception {
        final var file = IntegralSequenceFixtures.write(folder, false, 2);
        final String[] arguments = {"run", "--sequence", file.toString()};
        final var output = new ByteArrayOutputStream();
        final var print = new PrintStream(output);
        assertEquals(
                RuntimeExitCategory.SUCCESS,
                LocalArtifactSequenceMain.run(arguments, print, (sequence, sink) -> true));
        assertTrue(output.toString().contains("SEQUENCE_ROLLBACK_CONFIRMED"));
        assertEquals(
                RuntimeExitCategory.SOURCE_DQ,
                LocalArtifactSequenceMain.run(arguments, print, (sequence, sink) -> false));
        assertEquals(
                RuntimeExitCategory.CANCELLED,
                LocalArtifactSequenceMain.run(
                        arguments,
                        print,
                        (sequence, sink) -> {
                            throw new ResilienceCancelledException();
                        }));
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                LocalArtifactSequenceMain.run(
                        arguments,
                        print,
                        (sequence, sink) -> {
                            throw new IllegalArgumentException("SYNTHETIC_REFUSAL");
                        }));
        assertTrue(
                output.toString()
                        .contains(
                                "SEQUENCE_REJECTED type=IllegalArgumentException code=SYNTHETIC_REFUSAL"));
    }
}
