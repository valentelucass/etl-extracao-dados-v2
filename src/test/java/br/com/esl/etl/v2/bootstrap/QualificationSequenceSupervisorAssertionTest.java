package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

/** Executes the supervisor's private receipt assertions without starting a campaign or JDBC. */
class QualificationSequenceSupervisorAssertionTest {
    @Test
    void acceptsLateBlockedReceiptAndCompleteSuccessAndCancellationShapes() {
        final ObjectNode lateBlocked = report(5, "BLOCKED_DEPENDENCY", 0, true);
        assertDoesNotThrow(
                () -> {
                    assertStages(lateBlocked, 5, "BLOCKED_DEPENDENCY", 0);
                    assertDirectFactFailure(lateBlocked);
                });
        assertDoesNotThrow(() -> assertDefaultStages(report(7, "PASS_LOCAL", 33, false), 7));
        assertDoesNotThrow(() -> assertDefaultStages(report(7, "PASS_LOCAL", 33, false), 7));
    }

    @Test
    void rejectsPreviewStateAndOutputMutations() {
        final ObjectNode earlierPreview = report(5, "BLOCKED_DEPENDENCY", 0, true);
        ((ObjectNode) earlierPreview.path("stages").get(0)).withArray("sweepPreview").removeAll();
        assertThrows(
                AssertionError.class,
                () -> assertStages(earlierPreview, 5, "BLOCKED_DEPENDENCY", 0));

        assertThrows(
                AssertionError.class,
                () ->
                        assertStages(
                                report(5, "BLOCKED_DEPENDENCY", 33, true),
                                5,
                                "BLOCKED_DEPENDENCY",
                                0));

        assertThrows(
                AssertionError.class,
                () -> assertStages(report(5, "PASS_LOCAL", 0, true), 5, "BLOCKED_DEPENDENCY", 0));

        final ObjectNode wrongOutputs = report(5, "BLOCKED_DEPENDENCY", 0, true);
        ((ObjectNode) wrongOutputs.path("stages").get(2)).withArray("outputs").remove(18);
        assertThrows(
                AssertionError.class, () -> assertStages(wrongOutputs, 5, "BLOCKED_DEPENDENCY", 0));
    }

    private static ObjectNode report(
            final int stages,
            final String terminalState,
            final int terminalPreviews,
            final boolean factFailure) {
        final ObjectNode report = JsonNodeFactory.instance.objectNode();
        final var nodes = report.putArray("stages");
        for (int index = 0; index < stages; index++) {
            final boolean terminal = index == stages - 1;
            final ObjectNode stage = nodes.addObject();
            stage.put("selectedState", terminal ? terminalState : "PASS_LOCAL");
            final var outputs = stage.putArray("outputs");
            for (int output = 0; output < 19; output++) {
                outputs.add(output);
            }
            final var previews = stage.putArray("sweepPreview");
            final int count = terminal ? terminalPreviews : 33;
            for (int preview = 0; preview < count; preview++) {
                previews.add(preview);
            }
            if (terminal && factFailure) {
                stage.putArray("scopes")
                        .addObject()
                        .put("state", "FAILED")
                        .put("reason", "FACT_EQUATION_DIVERGENCE");
            }
        }
        return report;
    }

    private static void assertDefaultStages(final JsonNode report, final int expectedStages)
            throws Exception {
        invoke(
                "assertStageEvidence",
                new Class<?>[] {JsonNode.class, int.class},
                report,
                expectedStages);
    }

    private static void assertStages(
            final JsonNode report,
            final int expectedStages,
            final String terminalState,
            final int terminalPreviewCount)
            throws Exception {
        invoke(
                "assertStageEvidence",
                new Class<?>[] {JsonNode.class, int.class, String.class, int.class},
                report,
                expectedStages,
                terminalState,
                terminalPreviewCount);
    }

    private static void assertDirectFactFailure(final JsonNode report) throws Exception {
        invoke("assertDirectFactFailure", new Class<?>[] {JsonNode.class}, report);
    }

    private static void invoke(final String name, final Class<?>[] types, final Object... arguments)
            throws Exception {
        final Method method =
                QualificationSequenceSupervisorIT.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        try {
            method.invoke(null, arguments);
        } catch (final InvocationTargetException failure) {
            if (failure.getCause() instanceof Error error) {
                throw error;
            }
            if (failure.getCause() instanceof Exception exception) {
                throw exception;
            }
            throw failure;
        }
    }
}
