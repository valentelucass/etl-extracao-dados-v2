package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationComparator;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationGate;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalSequenceCliProjectionTest {
    @Test
    void stageReceiptProjectsOnlyBoundedCountsAndAllNineteenContracts() {
        final var comparison = comparison();
        final var stage =
                new LocalArtifactSequence.StageResult(
                        "bootstrap",
                        LocalArtifactSequence.Operation.CAPTURE,
                        ExecutionMode.BOOTSTRAP,
                        2,
                        2,
                        1,
                        1,
                        "2037-08-11",
                        "2037-08-11T12:00:00Z",
                        "2037-08-12",
                        comparison,
                        123,
                        7,
                        List.of());
        final var bytes = new ByteArrayOutputStream();
        LocalArtifactSequenceMain.printStage(
                stage, new PrintStream(bytes, true, StandardCharsets.UTF_8));
        final var lines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(21, lines.size());
        assertTrue(lines.get(0).contains("state=PASS_LOCAL millis=123 jdbc=7"));
        assertTrue(lines.get(1).contains("scopes=35 previews=0"));
        assertTrue(lines.get(2).contains("contract=SQL-01 expected=1 observed=1 differences=0"));
        assertTrue(lines.get(20).contains("contract=SQL-19 expected=19 observed=19 differences=0"));
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("SYNTHETIC"));
    }

    @Test
    void scenarioResultProjectsAllContractsAndNoFixtureProse() {
        final var bytes = new ByteArrayOutputStream();
        LocalArtifactScenarioMain.printResult(
                comparison(), new PrintStream(bytes, true, StandardCharsets.UTF_8));
        final var lines = bytes.toString(StandardCharsets.UTF_8).lines().toList();
        assertEquals(20, lines.size());
        assertTrue(lines.get(0).contains("contract=SQL-01 expected=1 actual=1 differences=0"));
        assertTrue(lines.get(18).contains("contract=SQL-19 expected=19 actual=19 differences=0"));
        assertEquals(
                "LOCAL_SCENARIO_FACT_GRAINS count=5 state=PASS_LOCAL scopes=35", lines.get(19));
        assertFalse(bytes.toString(StandardCharsets.UTF_8).contains("SYNTHETIC"));
    }

    private static QualificationScenarioVerifier.Result comparison() {
        final var scopes = new LinkedHashMap<String, QualificationGate>();
        for (int index = 0; index < 35; index++) {
            final var name = "SCOPE_" + index;
            scopes.put(
                    name,
                    new QualificationGate(
                            name, QualificationGate.State.PASS_LOCAL, "SYNTHETIC", "CONTRACT"));
        }
        final var outputs = new ArrayList<QualificationComparator.Result>();
        for (int index = 1; index <= 19; index++) {
            outputs.add(
                    new QualificationComparator.Result(
                            "SQL-" + String.format("%02d", index),
                            index,
                            index,
                            0,
                            List.of(),
                            QualificationGate.State.PASS_LOCAL));
        }
        final var selected =
                new QualificationGate(
                        "SCENARIO", QualificationGate.State.PASS_LOCAL, "SYNTHETIC", "CONTRACT");
        return new QualificationScenarioVerifier.Result(scopes, outputs, selected, 0);
    }
}
