package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.reconciliacao.exportacao.FaturasClienteIndependentRequest;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProductionReceivingTest {
    @Test
    void databaseSuffixPreservesCertificateHostEvenWhenProfileHasNoFinalSeparator() {
        final String base =
                "jdbc:sqlserver://127.0.0.1:1433;encrypt=true;"
                        + "trustServerCertificate=false;hostNameInCertificate=qualified-host";
        assertEquals(
                base + ";databaseName=ETL_SISTEMA_V2;", ProductionIngestionStore.targetUrl(base));
        assertTrue(
                ProductionIngestionStore.targetUrl(base + ";")
                        .contains("hostNameInCertificate=qualified-host;"));
    }

    @Test
    void refusesDifferentDatabaseAndTlsBypassBeforeConnecting() {
        final String base =
                "jdbc:sqlserver://127.0.0.1:1433;encrypt=true;trustServerCertificate=false;";
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionIngestionStore.targetUrl(base + "databaseName=ETL_SISTEMA;"));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        ProductionIngestionStore.targetUrl(
                                base.replace("encrypt=true", "encrypt=false")));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionIngestionStore.targetUrl(base.replace("127.0.0.1", "remote")));
    }

    @Test
    void rollingOffsetSevenContainsEightCivilDatesAcrossMonth() {
        final var end = LocalDate.of(2026, 11, 2);
        final var days = ProductionReceivingMain.days(end.minusDays(7), end);
        assertEquals(8, days.size());
        assertEquals(LocalDate.of(2026, 10, 26), days.get(0));
        assertEquals(end, days.get(7));
    }

    @Test
    void requestedRangeIncludesBothDatesAndLeapDay() {
        final var days =
                ProductionReceivingMain.days(LocalDate.of(2024, 2, 28), LocalDate.of(2024, 3, 1));
        assertEquals(3, days.size());
        assertEquals(LocalDate.of(2024, 2, 29), days.get(1));
    }

    @Test
    void refusesReversedAndUnboundedDateIntervals() {
        final var day = LocalDate.of(2026, 11, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionReceivingMain.days(day, day.minusDays(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionReceivingMain.days(day, day.plusDays(31)));
    }

    @Test
    void partitionsIntoQualifiedServiceDatesWithoutUpdatedAtRestriction() {
        for (final var day :
                ProductionReceivingMain.days(
                        LocalDate.of(2026, 10, 30), LocalDate.of(2026, 11, 1))) {
            final UUID run = UUID.randomUUID();
            final var request = ProductionReceivingMain.request(day, run);
            FaturasClienteIndependentRequest.validate(request);
            assertEquals(18, request.required("filters").size());
            assertEquals(day.toString(), request.path("periodStart").asText());
            assertEquals(
                    day + " - " + day,
                    request.path("filters").path("freights[service_at]").asText());
            assertTrue(request.path("filters").path("scopes[by_updated_at]").asText().isEmpty());
            assertEquals(run.toString(), request.path("attemptId").asText());
        }
    }
}
