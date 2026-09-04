package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SharedDataExportCircuitBreakerTest {

    @Test
    void sharesPerTemplateCircuitBetweenInfoAndDataWhileIsolatingTemplates() {
        final DataExportCircuitBreakerRegistry registry =
                new DataExportCircuitBreakerRegistry(
                        new DataExportCircuitBreakerPolicy(1, Duration.ofMinutes(1)),
                        Clock.systemUTC());
        final DataExportTemplateInfoGateway failingInfo =
                template -> {
                    throw new DataExportUnavailableException(template.templateId(), "HTTP 503");
                };
        final AtomicInteger dataCalls = new AtomicInteger();
        final DataExportGateway dataDelegate =
                request -> {
                    dataCalls.incrementAndGet();
                    return new DataExportPageResponse(List.of());
                };
        final DataExportTemplateInfoGateway info =
                new CircuitBreakingDataExportTemplateInfoGateway(failingInfo, registry);
        final DataExportGateway data = new CircuitBreakingDataExportGateway(dataDelegate, registry);

        assertThrows(
                DataExportUnavailableException.class,
                () -> info.fetchInfo(DataExportTemplate.COLETAS));
        assertThrows(
                DataExportCircuitOpenException.class,
                () -> data.fetch(request(DataExportTemplate.COLETAS)));
        assertEquals(0, dataCalls.get());

        assertEquals(0, data.fetch(request(DataExportTemplate.FRETES)).records().size());
        assertEquals(1, dataCalls.get());
    }

    private static DataExportPageRequest request(final DataExportTemplate template) {
        return new DataExportPageRequest(
                template,
                new BusinessDateRange(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1)),
                Optional.empty(),
                1,
                10,
                template.defaultOrderBy());
    }
}
