package br.com.esl.etl.v2.contratos.medicao;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MeasurementPlanTest {

    @ParameterizedTest
    @ValueSource(ints = {16, 256, 4096})
    void definesTheExactDataExportScales(final int dataPages) {
        final MeasurementPlan plan = MeasurementPlan.dataExport(dataPages);

        assertEquals(MeasurementPlan.StreamerKind.DATA_EXPORT, plan.streamer());
        assertEquals("DataExportPageStreamer", plan.streamerName());
        assertEquals(dataPages, plan.dataPages());
        assertEquals(8, plan.recordsPerPage());
        assertEquals(dataPages + 1L, plan.expectedFetchedPages());
        assertEquals(dataPages, plan.expectedConsumedPages());
        assertEquals(dataPages * 8L, plan.expectedRecords());
    }

    @ParameterizedTest
    @ValueSource(ints = {16, 256, 4096})
    void definesTheExactGraphQlScales(final int dataPages) {
        final MeasurementPlan plan = MeasurementPlan.graphQl(dataPages);

        assertEquals(MeasurementPlan.StreamerKind.GRAPHQL, plan.streamer());
        assertEquals("GraphQlPageStreamer", plan.streamerName());
        assertEquals(dataPages, plan.expectedFetchedPages());
        assertEquals(dataPages, plan.expectedConsumedPages());
        assertEquals(dataPages * 8L, plan.expectedRecords());
    }

    @Test
    void closesTheStreamerVocabulary() {
        assertArrayEquals(
                new MeasurementPlan.StreamerKind[] {
                    MeasurementPlan.StreamerKind.DATA_EXPORT, MeasurementPlan.StreamerKind.GRAPHQL
                },
                MeasurementPlan.StreamerKind.values());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 15, 17, 255, 257, 4095, 4097})
    void rejectsAnyScaleOutsideTheClosedAllowlist(final int dataPages) {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MeasurementPlan(MeasurementPlan.StreamerKind.DATA_EXPORT, dataPages, 8));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 0, 1, 7, 9, 16})
    void requiresExactlyEightRecordsPerPage(final int recordsPerPage) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new MeasurementPlan(
                                MeasurementPlan.StreamerKind.GRAPHQL, 16, recordsPerPage));
    }

    @Test
    void rejectsAMissingStreamerKind() {
        assertThrows(NullPointerException.class, () -> new MeasurementPlan(null, 16, 8));
    }
}
