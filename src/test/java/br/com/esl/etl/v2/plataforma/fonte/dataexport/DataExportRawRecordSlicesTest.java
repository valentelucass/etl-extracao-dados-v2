package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.localizacaocargas.aplicacao.LocalizacaoCargaDataExportRecordMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DataExportRawRecordSlicesTest {
    @ParameterizedTest
    @ValueSource(strings = {"1e2", "1E+02", "-0", "-0.000000000", "1.0", "1.000000000"})
    void preservesOriginalNumericLexemesIncludingTokensTheDomainRefuses(final String token)
            throws Exception {
        final String row =
                "{\"corporation_sequence_number\":1,\"service_at\":\"2036-03-19T12:00:00Z\",\"taxed_weight\":"
                        + token
                        + "}";
        final var slices = DataExportRawRecordSlices.capture("[ " + row + " ]", 1, 50);
        assertEquals(row, slices.record(0));
        final var mapped =
                LocalizacaoCargaDataExportRecordMapper.observed20261024().map(1, slices.record(0));
        final var evidence = ObservedFlowTestSupport.JSON.readTree(mapped.fieldPresenceJson());
        assertEquals(
                token, evidence.path("fields").path("taxed_weight").path("rawWireLexeme").asText());
        if (token.contains("e") || token.contains("E")) {
            assertTrue(mapped.quarantined());
        }
    }

    @Test
    void unicodeEscapesBracesBackslashesAndUtf16PositionsDoNotChangeSlices() throws Exception {
        final String first = "{\"s\":\"😀\\u0061 \\\" { } [ ] \\\\\"}";
        final String second = "{\"s\":\"a\",\"n\":1E+02}";
        final String document = "[\r\n " + first + ",\r\n" + second + " ]";
        final var slices = DataExportRawRecordSlices.capture(document, 2, 30);
        assertEquals(first, slices.record(0));
        assertEquals(second, slices.record(1));
        assertFalse(
                slices.record(0).equals(ObservedFlowTestSupport.JSON.readTree(first).toString()));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "{\"data\":[]}",
                "[1]",
                "[{\"x\":1,\"x\":2}]",
                "[{\"x\":1}][]",
                "[{\"x\":",
                "[{\"x\":1,\"\\u0078\":2}]"
            })
    void rejectsWrongRootInvalidRecordsDuplicatesAndTrailingDocuments(final String document) {
        assertThrows(Exception.class, () -> DataExportRawRecordSlices.capture(document, 1, 50));
    }

    @Test
    void enforcesRecordCharacterNodeAndNumericTokenBounds() throws Exception {
        final String bounded = "{\"x\":\"" + "a".repeat(1_048_576 - 8) + "\"}";
        assertEquals(
                bounded, DataExportRawRecordSlices.capture("[" + bounded + "]", 1, 10).record(0));
        assertThrows(
                Exception.class,
                () ->
                        DataExportRawRecordSlices.capture(
                                "[" + bounded.replace("a\"}", "aa\"}") + "]", 1, 10));
        assertThrows(Exception.class, () -> DataExportRawRecordSlices.capture("[{\"x\":1}]", 1, 2));
        assertThrows(
                Exception.class,
                () ->
                        DataExportRawRecordSlices.capture(
                                "[{\"x\":" + "1".repeat(1001) + "}]", 1, 10));
        assertThrows(Exception.class, () -> DataExportRawRecordSlices.capture("[{},{}]", 1, 10));
        assertEquals(0, DataExportRawRecordSlices.capture("[]", 0, 1).size());
    }

    @Test
    void withObservationPreservesRawAndVisitorsRemainDefensive() throws Exception {
        final String row = ObservedFlowTestSupport.localRow(1);
        final var fixture =
                new ObservedFlowTestSupport(DataExportTemplate.LOCALIZACAO_CARGAS, 50, "[]");
        final var page =
                new DataExportPageResponse(List.of(ObservedFlowTestSupport.JSON.readTree(row)))
                        .withRawRecords("[" + row + "]", 100)
                        .withObservation(
                                fixture.release.response(),
                                fixture.observation.observationLimits(),
                                fixture.observation.responsePathBoundary());
        final var captured = new ArrayList<String>();
        page.forEachRecordInput(
                input -> {
                    captured.add(input.rawJson().orElseThrow());
                    ((com.fasterxml.jackson.databind.node.ObjectNode) input.value())
                            .put("corporation_sequence_number", 99);
                    assertFalse(input.toString().contains("synthetic-invoice"));
                });
        assertEquals(List.of(row), captured);
        assertEquals(1, page.records().get(0).path("corporation_sequence_number").asInt());
        new DataExportPageResponse(page.records())
                .forEachRecordInput(input -> assertTrue(input.rawJson().isEmpty()));
    }
}
