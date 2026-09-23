package br.com.esl.etl.v2.contratos.bloco58;

import static br.com.esl.etl.v2.contratos.bloco58.CharacterizationFixtures.input;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.contratos.bloco58.ColetasCharacterization.Fault;
import br.com.esl.etl.v2.contratos.bloco58.LocalCharacterization.Layer;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportFirstWaveContractCatalog;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
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

class ColetasCharacterizationTest {
    private static final Map<String, JsonNode> REPORTS = new TreeMap<>();
    private static final ObjectMapper JSON = new ObjectMapper();

    static Stream<JsonNode> scenarios() throws Exception {
        final var bundle = CharacterizationFixtures.bundle("coletas");
        return StreamSupport.stream(bundle.path("cases").spliterator(), false);
    }

    @ParameterizedTest(name = "synthetic Coletas case {index}")
    @MethodSource("scenarios")
    void exercise(final JsonNode scenario) {
        final String document = scenario.path("envelope").textValue();
        final var mapper =
                ColetasCharacterization.mapper(
                        page -> input(document),
                        1,
                        (page, row) -> scenario.path("expected").get(row));
        mapper.expect(Layer.NONE, 0, false);
        final var first =
                ColetasCharacterization.pipeline(
                        page -> input(page == 1 ? document : "{\"data\":[]}"),
                        DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                        1,
                        2,
                        1,
                        Fault.NONE,
                        (page, row) -> scenario.path("expected").get(row));
        final Layer firstRefusal = Layer.valueOf(scenario.path("firstWaveRefusal").textValue());
        first.expect(firstRefusal, firstRefusal == Layer.NONE ? 1 : 0, firstRefusal == Layer.NONE);
        final var decision =
                ColetasCharacterization.pipeline(
                        page -> input(page == 1 ? document : "{\"data\":[]}"),
                        ColetasCharacterization.decisionFixture(),
                        1,
                        2,
                        1,
                        Fault.NONE,
                        (page, row) -> scenario.path("expected").get(row));
        final Layer decisionRefusal = Layer.valueOf(scenario.path("decisionRefusal").textValue());
        decision.expect(
                decisionRefusal,
                decisionRefusal == Layer.NONE ? 1 : 0,
                decisionRefusal == Layer.NONE);
        final var report = JSON.createObjectNode();
        report.put("rule", scenario.path("rule").textValue());
        report.set("parserMapper", mapper.sanitized());
        report.set("firstWaveContractTraversal", first.sanitized());
        report.set("decisionFixtureTraversal", decision.sanitized());
        report.put("decisionFixtureScope", "LOCAL_TEST_SCHEMA_NOT_PROVIDER_CONTRACT");
        report.put(
                "contractDomainComparison",
                firstRefusal != Layer.NONE && decisionRefusal == Layer.NONE
                        ? "DIVERGED_BEFORE_STAGING"
                        : "COMPATIBLE_IN_EXERCISED_LAYER");
        REPORTS.put(scenario.path("id").textValue(), report);
        assertTrue(mapper.matches(), mapper::toString);
        assertTrue(first.matches(), first::toString);
        assertTrue(decision.matches(), decision::toString);
    }

    @Test
    void aValidMapperExpectationCannotTurnContractRefusalIntoSuccess() throws Exception {
        final var expected =
                JSON.readTree("{\"/quarantine\":\"NONE\",\"/id/typed\":\"INTEGER:0\"}");
        final var report =
                ColetasCharacterization.pipeline(
                        page -> input("{\"data\":[{\"id\":0}]}"),
                        DataExportFirstWaveContractCatalog.release(DataExportTemplate.COLETAS),
                        1,
                        2,
                        1,
                        Fault.NONE,
                        (page, row) -> expected);
        report.expect(Layer.NONE, 1, true);
        assertFalse(report.matches());
        REPORTS.put("SYNTH_VALID_REFUSAL_COUNTEREXAMPLE", report.sanitized());
    }

    @AfterAll
    static void save() throws Exception {
        CharacterizationFixtures.write("coletas", REPORTS);
    }
}
