package br.com.esl.etl.v2.modulos.manifestos.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.manifestos.aplicacao.ManifestoDataExportRecordMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ManifestoRootReducerTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private final ManifestoDataExportRecordMapper mapper = new ManifestoDataExportRecordMapper();
    private final ManifestoRootReducer reducer = new ManifestoRootReducer();

    @Test
    void reducesKnownStatusMetricComplementDistinctChildrenAndOlderObservation() {
        final ManifestoReductionResult result =
                reducer.reduce(
                        List.of(
                                record(
                                        1,
                                        """
                                        {"sequence_code":91,"status":"pending","mdfe_status":"ok",
                                         "finished_at":"2036-01-02T10:00:00Z","departured_at":"2036-01-01T10:00:00Z",
                                         "km":null,"total_cost":0,"mft_pfs_pck_sequence_code":10,
                                         "mft_mfs_number":1,"mft_mfs_key":"12345678901234567890123456789012345678901234"}
                                        """),
                                record(
                                        2,
                                        """
                                        {"sequence_code":91,"status":"closed","mdfe_status":"ok",
                                         "finished_at":"2036-01-02T10:00:00Z","departured_at":"2036-01-01T10:00:00Z",
                                         "km":0,"total_cost":null,"mft_pfs_pck_sequence_code":11,
                                         "mft_mfs_number":2,"mft_mfs_key":"22345678901234567890123456789012345678901234"}
                                        """),
                                record(
                                        3,
                                        """
                                        {"sequence_code":91,"status":"in_transit","mdfe_status":"old",
                                         "finished_at":"2036-01-01T10:00:00Z","departured_at":"2036-01-01T10:00:00Z",
                                         "km":9}
                                        """)));

        assertTrue(result.promotable());
        assertEquals("\"closed\"", result.rootFields().get("status").canonicalJson());
        assertEquals("\"ok\"", result.rootFields().get("mdfe_status").canonicalJson());
        assertEquals("0", result.metrics().get(ManifestoMetric.KM).canonicalNumber());
        assertEquals("0", result.metrics().get(ManifestoMetric.TOTAL_COST).canonicalNumber());
        assertEquals(2, result.pickCandidates().size());
        assertEquals(2, result.mdfeCandidates().size());
        assertTrue(result.childQuarantineReasons().isEmpty());
        assertEquals("2036-01-02T10:00:00Z", result.freshnessAtUtc().toString());
    }

    @Test
    void blocksRootOnDefaultStatusAndMetricConflictsButKeepsChildConflictSeparate() {
        final ManifestoReductionResult rootConflict =
                reducer.reduce(
                        List.of(
                                record(1, withFreshness("\"status\":null,\"km\":0")),
                                record(2, withFreshness("\"status\":\"pending\",\"km\":1"))));
        assertFalse(rootConflict.promotable());
        assertEquals("EQUAL_FRESHNESS_CONFLICT", rootConflict.rootQuarantineReason().orElseThrow());

        final ManifestoReductionResult childConflict =
                reducer.reduce(
                        List.of(
                                record(1, withMdfeNumber(1)),
                                record(2, withMdfeNumber(2)),
                                record(3, withMdfeNumber(1))));
        assertTrue(childConflict.promotable());
        assertTrue(childConflict.mdfeCandidates().isEmpty());
        assertEquals(List.of("MDFE_ATTRIBUTE_CONFLICT"), childConflict.childQuarantineReasons());
    }

    @Test
    void preservesRepeatedUnknownButRejectsKnownUnknownAndDifferentRoots() {
        final ManifestoReductionResult unknown =
                reducer.reduce(
                        List.of(
                                record(1, withFreshness("\"status\":\"future\"")),
                                record(2, withFreshness("\"status\":\"future\""))));
        assertTrue(unknown.promotable());
        assertEquals("\"future\"", unknown.rootFields().get("status").canonicalJson());

        final ManifestoReductionResult mixed =
                reducer.reduce(
                        List.of(
                                record(1, withFreshness("\"status\":\"future\"")),
                                record(2, withFreshness("\"status\":\"closed\""))));
        assertFalse(mixed.promotable());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        reducer.reduce(
                                List.of(
                                        record(
                                                1,
                                                "{\"sequence_code\":91,\"finished_at\":\"2036-01-02T10:00:00Z\"}"),
                                        record(
                                                2,
                                                "{\"sequence_code\":92,\"finished_at\":\"2036-01-02T10:00:00Z\"}"))));
    }

    @Test
    void appliesCanonicalFreshnessOriginPrecedenceIndependentlyOfInputOrder() {
        final ManifestoStageRecord finished =
                record(1, "{\"sequence_code\":91,\"finished_at\":\"2036-01-02T10:00:00Z\"}");
        final ManifestoStageRecord closed =
                record(2, "{\"sequence_code\":91,\"closed_at\":\"2036-01-02T10:00:00Z\"}");

        final ManifestoReductionResult closedThenFinished =
                reducer.reduce(List.of(closed, finished));
        final ManifestoReductionResult finishedThenClosed =
                reducer.reduce(List.of(finished, closed));

        assertEquals(ManifestoFreshnessOrigin.FINISHED_AT, closedThenFinished.freshnessOrigin());
        assertEquals(ManifestoFreshnessOrigin.FINISHED_AT, finishedThenClosed.freshnessOrigin());
        assertEquals(closedThenFinished.freshnessAtUtc(), finishedThenClosed.freshnessAtUtc());
        assertEquals(closedThenFinished.rootFields(), finishedThenClosed.rootFields());
    }

    @Test
    void quarantinesConflictingMdfeIndependentlyOfInputOrder() {
        final ManifestoStageRecord numberOne = record(1, withMdfeNumber(1));
        final ManifestoStageRecord numberTwo = record(2, withMdfeNumber(2));
        final ManifestoStageRecord repeatedNumberOne = record(3, withMdfeNumber(1));

        final ManifestoReductionResult oneThenTwo =
                reducer.reduce(List.of(numberOne, numberTwo, repeatedNumberOne));
        final ManifestoReductionResult twoThenOne =
                reducer.reduce(List.of(numberTwo, repeatedNumberOne, numberOne));

        assertTrue(oneThenTwo.promotable());
        assertTrue(twoThenOne.promotable());
        assertEquals(List.of(), oneThenTwo.mdfeCandidates());
        assertEquals(oneThenTwo.mdfeCandidates(), twoThenOne.mdfeCandidates());
        assertEquals(List.of("MDFE_ATTRIBUTE_CONFLICT"), oneThenTwo.childQuarantineReasons());
        assertEquals(oneThenTwo.childQuarantineReasons(), twoThenOne.childQuarantineReasons());
    }

    private ManifestoStageRecord record(final int ordinal, final String document) {
        final ManifestoStageRecord record = mapper.map(ordinal, node(document));
        assertEquals(ManifestoStageDisposition.VALID, record.disposition());
        return record;
    }

    private static String withFreshness(final String fields) {
        return "{\"sequence_code\":91,\"finished_at\":\"2036-01-02T10:00:00Z\"," + fields + "}";
    }

    private static String withMdfeNumber(final int number) {
        return withFreshness(
                "\"mft_mfs_number\":"
                        + number
                        + ",\"mft_mfs_key\":\"12345678901234567890123456789012345678901234\"");
    }

    private static JsonNode node(final String document) {
        try {
            return JSON.readTree(document);
        } catch (final JsonProcessingException exception) {
            throw new AssertionError(exception);
        }
    }
}
