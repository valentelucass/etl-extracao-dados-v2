package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.CharacterizationFixtures.input;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.contratos.bloco58.UsuariosCharacterization.Fault;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class UsuariosCharacterizationTest {
    private static final Map<String, JsonNode> REPORTS = new TreeMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<JsonNode> scenarios() throws Exception {
        return StreamSupport.stream(
                CharacterizationFixtures.bundle("usuarios").path("cases").spliterator(), false);
    }

    @ParameterizedTest(name = "synthetic users case {index}")
    @MethodSource("scenarios")
    void comparesIndependentExpectations(final JsonNode scenario) {
        final String document = scenario.path("envelope").textValue();
        final var mapper =
                UsuariosCharacterization.mapper(
                        page -> input(document), (page, row) -> scenario.path("expected").get(row));
        mapper.expect(Layer.NONE, 0, false);
        final var pipeline =
                UsuariosCharacterization.pipeline(
                        page -> input(document),
                        1,
                        20,
                        Fault.NONE,
                        (page, row) -> scenario.path("expected").get(row));
        final var refusal = Layer.valueOf(scenario.path("pipelineRefusal").textValue());
        pipeline.expect(
                refusal,
                refusal == Layer.NONE ? scenario.path("expected").size() : 0,
                refusal == Layer.NONE);
        final var record = JSON.createObjectNode();
        record.put("rule", "USR-01_USR-02_USR-03_USR-04_ADR0019");
        record.set("strictJsonMapper", mapper.sanitized());
        record.set("relayContractTraversal", pipeline.sanitized());
        record.put(
                "historicalProfileComparison", scenario.path("historicalDifference").textValue());
        REPORTS.put(scenario.path("id").textValue(), record);
        assertTrue(mapper.matches(), mapper::toString);
        assertTrue(pipeline.matches(), pipeline::toString);
    }

    @Test
    void comparisonDetectsChangedValueAndTypeWithoutReportingValues() throws Exception {
        final var oracle =
                JSON.readTree(
                        "{\"/quarantine\":\"NONE\",\"/id/typed\":\"STRING:0\",\"/name/value\":\"synthetic-expected\"}");
        final var result =
                UsuariosCharacterization.pipeline(
                        page ->
                                input(
                                        UsuariosTraversalCharacterizationTest.envelope(
                                                "{\"id\":0,\"name\":\"synthetic-actual\"}",
                                                false,
                                                "null")),
                        1,
                        20,
                        Fault.NONE,
                        (page, row) -> oracle);
        result.expect(Layer.NONE, 1, true);
        assertFalse(result.matches());
        final String report = result.toString();
        assertFalse(report.contains("synthetic-actual"));
        assertFalse(report.contains("synthetic-expected"));
        assertFalse(report.contains("STRING:0"));
        REPORTS.put("SYNTH_COMPARISON_COUNTEREXAMPLE", result.sanitized());
    }

    @AfterAll
    static void save() throws Exception {
        CharacterizationFixtures.write("usuarios", REPORTS);
    }
}
