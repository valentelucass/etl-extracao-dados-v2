package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DataExportExtractionAuditTest {

    private static final UUID EXECUTION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000801");
    private static final Instant OBSERVED_AT = Instant.parse("2026-08-30T15:00:00Z");

    @Test
    void requiresDistinctEntitiesToFitBothTheRequestedPageAndPhysicalRows() {
        final DataExportExtractionAudit.PageRead expandedRows =
                new DataExportExtractionAudit.PageRead(EXECUTION_ID, 1, 10, 12, 10, OBSERVED_AT);
        final DataExportExtractionAudit.PageRead oneEntityPerRow =
                new DataExportExtractionAudit.PageRead(EXECUTION_ID, 2, 10, 3, 3, OBSERVED_AT);

        assertEquals(10, expandedRows.distinctEntityCount());
        assertEquals(3, oneEntityPerRow.distinctEntityCount());
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportExtractionAudit.PageRead(
                                EXECUTION_ID, 3, 10, 1, 2, OBSERVED_AT));
    }

    @Test
    void exposesOnlyTheClosedSanitizedFailureCategoriesAndRejectsNull() {
        assertEquals(
                EnumSet.of(
                        DataExportFailureCategory.SOURCE_UNAVAILABLE,
                        DataExportFailureCategory.CIRCUIT_OPEN,
                        DataExportFailureCategory.RESPONSE_LIMIT_EXCEEDED,
                        DataExportFailureCategory.BUDGET_EXHAUSTED,
                        DataExportFailureCategory.TIMEOUT,
                        DataExportFailureCategory.CANCELLED,
                        DataExportFailureCategory.CONTRACT_DRIFT,
                        DataExportFailureCategory.RUNTIME_FAILURE),
                EnumSet.allOf(DataExportFailureCategory.class));

        for (final DataExportFailureCategory category : DataExportFailureCategory.values()) {
            final DataExportExtractionAudit.ExecutionFailed event = failedEvent(category);

            assertEquals(category, event.failureCategory());
            assertTrue(event.failureCategory().name().matches("[A-Z][A-Z0-9_]*"));
        }
        assertThrows(NullPointerException.class, () -> failedEvent(null));
    }

    private static DataExportExtractionAudit.ExecutionFailed failedEvent(
            final DataExportFailureCategory category) {
        return new DataExportExtractionAudit.ExecutionFailed(
                EXECUTION_ID, DataExportTemplate.COLETAS, 1, 10, OBSERVED_AT, category);
    }
}
