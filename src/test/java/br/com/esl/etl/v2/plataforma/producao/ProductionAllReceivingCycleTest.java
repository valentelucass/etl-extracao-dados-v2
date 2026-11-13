package br.com.esl.etl.v2.plataforma.producao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.CurlSnapshotWire;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ReportSnapshotTemplate;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ProductionAllReceivingCycleTest {
    @Test
    void rollingIncludesEightCivilDatesAcrossMonthAndLeapDay() {
        final var start = LocalDate.of(2024, 2, 25);
        final var days = ProductionAllReceivingCycle.dates(start, start.plusDays(7));
        assertEquals(8, days.size());
        assertTrue(days.contains(LocalDate.of(2024, 2, 29)));
        assertEquals(start.plusDays(7), days.get(7));
    }

    @Test
    void refusesUnboundedOrReversedRange() {
        final var day = LocalDate.of(2026, 11, 2);
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionAllReceivingCycle.dates(day, day.plusDays(8)));
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionAllReceivingCycle.dates(day, day.minusDays(1)));
    }

    @Test
    void recoveryUsesActualHolesAndStillVisitsEveryRollingDate() {
        final var start = LocalDate.of(2026, 11, 2);
        final var holes =
                List.of(
                        start.minusDays(1),
                        start.minusDays(9),
                        start.minusDays(5),
                        start.minusDays(8),
                        start.minusDays(3));
        final var plan = ProductionAllReceivingCycle.plan(holes, start, start.plusDays(7));
        assertEquals(12, plan.size());
        assertEquals(
                List.of(
                        start.minusDays(9),
                        start.minusDays(8),
                        start.minusDays(5),
                        start.minusDays(3)),
                plan.subList(0, 4));
        assertEquals(
                ProductionAllReceivingCycle.dates(start, start.plusDays(7)), plan.subList(4, 12));
    }

    @Test
    void noRecoveryHoleDoesNotInventAnOlderDate() {
        final var start = LocalDate.of(2026, 11, 2);
        assertEquals(
                ProductionAllReceivingCycle.dates(start, start.plusDays(7)),
                ProductionAllReceivingCycle.plan(List.of(), start, start.plusDays(7)));
    }

    @ParameterizedTest
    @EnumSource(
            value = ReportSnapshotTemplate.class,
            names = "COLETAS_EXCLUIDAS",
            mode = EnumSource.Mode.EXCLUDE)
    void qualifiedMetadataMustMatchEveryFieldAndFilter(final ReportSnapshotTemplate template)
            throws Exception {
        final var info = info(template);
        ProductionAllReceivingCycle.validateMetadata(template, info);
        final var missing = info.deepCopy();
        ((ObjectNode) missing.get("fields")).remove(missing.get("fields").fieldNames().next());
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionAllReceivingCycle.validateMetadata(template, missing));
        final var extra = info.deepCopy();
        ((ObjectNode) extra.get("fields")).put("undocumented_field", "Unexpected");
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionAllReceivingCycle.validateMetadata(template, extra));
        final var drift = info.deepCopy();
        drift.putArray("filters");
        assertThrows(
                IllegalArgumentException.class,
                () -> ProductionAllReceivingCycle.validateMetadata(template, drift));
        assertEquals(
                template == ReportSnapshotTemplate.FRETES ? 25 : 100,
                ProductionAllReceivingCycle.pageSize(template));
    }

    private static ObjectNode info(final ReportSnapshotTemplate template) throws Exception {
        try (var input =
                ProductionAllReceivingCycleTest.class
                        .getClassLoader()
                        .getResourceAsStream(
                                "contracts/dataexport/receiving-catalog-0762-v2.json")) {
            for (final var entity : CurlSnapshotWire.JSON.readTree(input).get("entities")) {
                if (!entity.get("entity").asText().equals(template.name())) {
                    continue;
                }
                final var info = CurlSnapshotWire.JSON.createObjectNode();
                final var fields = info.putObject("fields");
                for (final var field : entity.get("fields")) {
                    fields.put(field.asText(), "title");
                }
                info.set("filters", entity.get("filters"));
                info.set("default_order", entity.get("defaultOrder"));
                info.set("required_date_filters", entity.get("requiredDateFilters"));
                return info;
            }
        }
        throw new AssertionError("missing entity");
    }
}
