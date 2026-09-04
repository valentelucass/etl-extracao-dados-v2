package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataExportExtractionLimitsTest {

    @Test
    void rejectsNonPositivePageLimit() {
        assertThrows(
                IllegalArgumentException.class, () -> new DataExportExtractionLimits(0, 10, 10));
    }

    @Test
    void rejectsNonPositiveRecordLimit() {
        assertThrows(
                IllegalArgumentException.class, () -> new DataExportExtractionLimits(1, 0, 10));
    }

    @Test
    void rejectsNonPositivePageSizeLimit() {
        assertThrows(
                IllegalArgumentException.class, () -> new DataExportExtractionLimits(1, 10, 0));
    }

    @Test
    void rejectsARequestAboveThePageSizeLimit() {
        final DataExportExtractionLimits limits = new DataExportExtractionLimits(1, 10, 2);

        assertThrows(IllegalArgumentException.class, () -> limits.validate(request(3)));
    }

    @Test
    void acceptsARequestWithinThePageSizeLimit() {
        final DataExportExtractionLimits limits = new DataExportExtractionLimits(1, 10, 2);

        assertDoesNotThrow(() -> limits.validate(request(2)));
    }

    @Test
    void rejectsATraversalThatDoesNotStartAtPageOne() {
        final DataExportExtractionLimits limits = new DataExportExtractionLimits(1, 10, 2);

        assertThrows(IllegalArgumentException.class, () -> limits.validate(request(2).withPage(2)));
    }

    private DataExportPageRequest request(final int pageSize) {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
                Optional.empty(),
                1,
                pageSize,
                DataExportTemplate.COLETAS.defaultOrderBy());
    }
}
