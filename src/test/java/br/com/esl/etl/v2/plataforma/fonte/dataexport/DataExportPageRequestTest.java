package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class DataExportPageRequestTest {

    @Test
    void rejectsAnEmptyOrderBecausePaginationMustBeTraceable() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new DataExportPageRequest(
                                DataExportTemplate.COLETAS,
                                new BusinessDateRange(
                                        LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24)),
                                Optional.empty(),
                                1,
                                100,
                                List.of()));
    }

    @Test
    void rejectsBlankOrNullOrderElementsBeforeCopyingTheList() {
        assertThrows(IllegalArgumentException.class, () -> requestWithOrder(List.of(" ")));
        assertThrows(
                IllegalArgumentException.class,
                () -> requestWithOrder(Collections.singletonList(null)));
    }

    private static DataExportPageRequest requestWithOrder(final List<String> orderBy) {
        return new DataExportPageRequest(
                DataExportTemplate.COLETAS,
                new BusinessDateRange(LocalDate.of(2026, 8, 24), LocalDate.of(2026, 8, 24)),
                Optional.empty(),
                1,
                100,
                orderBy);
    }
}
