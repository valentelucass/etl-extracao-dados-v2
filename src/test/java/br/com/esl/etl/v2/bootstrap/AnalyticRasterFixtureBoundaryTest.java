package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class AnalyticRasterFixtureBoundaryTest {
    @Test
    void declaredWindowReturnsThreeObservationsAndSeparateBindingEvidence() throws Exception {
        final var window =
                new RasterWindow(
                        AnalyticRasterFixtures.DATE, AnalyticRasterFixtures.DATE.plusDays(1));
        final var source = AnalyticRasterFixtures.source(3, 7);
        final var response = source.fetch(window);
        final var rows = new ObjectMapper().readTree(response.body());
        assertEquals(3, rows.size());
        assertEquals(3, response.terminal().trips());
        assertEquals(3, response.terminal().stops());
        assertEquals(rows.get(0).path("CodSolicitacao"), rows.get(2).path("CodSolicitacao"));
        assertEquals("synthetic-trip-1", source.binding(window, 1, 0).tripKey());
        assertNull(source.binding(window, 1, 0).stopKey());
        assertEquals("synthetic-stop-1", source.binding(window, 3, 1).stopKey());
        assertEquals(7, source.binding(window, 3, 1).revision());

        final var partial = AnalyticScenarioFaults.incompleteRaster(source);
        assertArrayEquals(response.body(), partial.fetch(window).body());
        assertNull(partial.fetch(window).terminal());
        assertEquals(source.binding(window, 2, 1), partial.binding(window, 2, 1));
    }

    @Test
    void outOfWindowAndForeignPositionsFailClosed() throws Exception {
        final var source = AnalyticRasterFixtures.source(3, 7);
        final var window =
                new RasterWindow(
                        AnalyticRasterFixtures.DATE, AnalyticRasterFixtures.DATE.plusDays(1));
        assertEquals(
                "RAS_FIXTURE_WINDOW",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        source.fetch(
                                                new RasterWindow(
                                                        AnalyticRasterFixtures.DATE.minusDays(1),
                                                        AnalyticRasterFixtures.DATE)))
                        .getMessage());
        assertEquals(
                "RAS_FIXTURE_POSITION",
                assertThrows(IllegalArgumentException.class, () -> source.binding(window, 4, 0))
                        .getMessage());
        assertThrows(IllegalArgumentException.class, () -> source.binding(window, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> AnalyticRasterFixtures.source(-1, 7));
    }
}
