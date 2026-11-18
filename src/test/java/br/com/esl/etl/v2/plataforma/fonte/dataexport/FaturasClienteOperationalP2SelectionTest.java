package br.com.esl.etl.v2.plataforma.fonte.dataexport;

import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.plan;
import static br.com.esl.etl.v2.plataforma.fonte.dataexport.ObservedExpansionSchemaFixtures.raw;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.expansao.OrderedExpansionStrings;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Requires the Root-adopted final P2 resource and FAT-specific array parser together. */
final class FaturasClienteOperationalP2SelectionTest {
    private static final ObservedExpansionCaptureTemplate FAT =
            ObservedExpansionCaptureTemplate.FAT;

    @Test
    void currentFatUsesP2WhileHistoricalSelectionKeepsExactP1Bytes() throws Exception {
        final var current = ObservedExpansionSchemaCatalog.select(FAT);
        final var historical = ObservedExpansionSchemaCatalog.selectHistorical(FAT);
        assertEquals("observed-expansion-wire-4924-20261108-p2", current.schemaRelease());
        assertEquals("observed-expansion-wire-4924-20261025-v1", historical.schemaRelease());
        assertEquals(
                "4bdc78464659c30367ba8d97ed7e9a5f88824503beaa05aaff621c5e97354405",
                historical.schemaResourceSha256());
        assertFalse(current.resource().equals(historical.resource()));
        assertFalse(current.schemaResourceSha256().equals(historical.schemaResourceSha256()));
        final byte[] p1;
        try (var input = getClass().getResourceAsStream(historical.resource())) {
            p1 = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        // This would fail if read() looked up the current default instead of its Selection.
        final var read =
                ObservedExpansionSchemaCatalog.read(historical, new ByteArrayInputStream(p1));
        assertEquals(52, read.fields().size());
        assertEquals(70, read.metadata().elements().size());
        assertThrows(
                IllegalStateException.class,
                () -> ObservedExpansionSchemaCatalog.read(current, new ByteArrayInputStream(p1)));
    }

    @Test
    void reviewedWireFamiliesReachActualMapperOnlyUnderTheCurrentP2Guard() {
        final var p = plan(FAT);
        final var historical = bound(p, true);
        final var baseline = validHistoricalRow();
        // Use an independent actual guard/projector chain for every reviewed field mutation.
        for (final String path :
                List.of(
                        "comments",
                        "fit_ant_ils_atn_transaction_date",
                        "fit_dyn_drt_nickname",
                        "fit_nse_number",
                        "fit_pyr_cor_billing_cycle",
                        "fit_pyr_cor_billing_due_in_days",
                        "fit_sps_slr_psn_name",
                        "reference_number")) {
            final var changed = baseline.deepCopy();
            applyObservedUnion(changed, path);
            final var guard = bound(p, false);
            final var inspection = guard.inspect(raw(p, changed));
            final var oldInspection = historical.inspect(raw(p, changed));
            assertFalse(oldInspection.schemaMatched(), path);
            assertThrows(IllegalArgumentException.class, () -> historical.project(oldInspection));
            assertTrue(inspection.schemaMatched(), path);
            final var step = guard.project(inspection);
            assertEquals(ObservedExpansionPageProjection.Status.MAPPED, step.projection().status());
            assertEquals(0, step.projection().fieldIssueRows(), path);
            final var admitted = guard.admit(inspection, step);
            assertEquals(guard.schemaRelease(), admitted.schemaRelease());
            assertEquals(1, admitted.physicalRows().orElseThrow());
        }
    }

    @Test
    void bothFiftyItemArraysPassGuardMapperAndAdmissionWithoutDroppingDuplicates() {
        final var p = plan(FAT);
        final var row = validHistoricalRow();
        for (final String path : List.of("fit_fte_invoices_order_number", "invoices_mapping")) {
            final var array = row.putArray(path);
            for (int index = 0; index < 50; index++) {
                array.add("same-synthetic-item");
            }
        }
        final var page = raw(p, row);
        final var guard = bound(p, false);
        final var inspection = guard.inspect(page);
        assertTrue(inspection.schemaMatched());
        final var step = guard.project(inspection);
        assertEquals(0, step.projection().fieldIssueRows());
        final var record = step.projection().records().get(0);
        for (final String path : List.of("fit_fte_invoices_order_number", "invoices_mapping")) {
            final var field =
                    record.fields().stream()
                            .filter(f -> path.equals(f.path()))
                            .findFirst()
                            .orElseThrow();
            assertTrue(field.value().valid());
            final var values = (OrderedExpansionStrings) field.value().value();
            assertEquals(50, values.items().size());
            assertTrue(values.items().stream().allMatch("same-synthetic-item"::equals));
        }
        final var admitted = guard.admit(inspection, step);
        assertSame(page, admitted.rawPage());
        assertArrayEquals(page.body(), admitted.rawPage().body());
        assertEquals(0, admitted.admittedIssueRows());
    }

    @Test
    void stillUnobservedNseStringIsRefusedBeforeTheMapperCouldCoerceIt() {
        final var p = plan(FAT);
        final var changed = validHistoricalRow();
        changed.put("fit_nse_number", "1");
        final var guard = bound(p, false);
        final var inspection = guard.inspect(raw(p, changed));
        assertFalse(inspection.schemaMatched());
        assertEquals(ObservedExpansionSchemaGuard.Issue.WIRE_TYPE, inspection.issue());
        assertThrows(IllegalArgumentException.class, () -> guard.project(inspection));
        assertEquals(0, guard.admittedPages());
    }

    @Test
    void integerAndNullNseFamiliesAreAcceptedWithoutInventingAnIdentifier() {
        final var p = plan(FAT);
        for (final boolean isNull : List.of(true, false)) {
            final var row = validHistoricalRow();
            if (isNull) {
                row.putNull("fit_nse_number");
            } else {
                row.put("fit_nse_number", 17);
            }
            final var guard = bound(p, false);
            final var inspected = guard.inspect(raw(p, row));
            assertTrue(inspected.schemaMatched());
            final var step = guard.project(inspected);
            assertTrue(step.projection().records().get(0).mapperValid());
            assertNull(step.projection().records().get(0).captured().binding());
        }
    }

    @Test
    void sameIdAndMinutaStayTwoPhysicalOccurrencesInActualProjection() {
        final var p = plan(FAT);
        final var row = validHistoricalRow();
        final var page = raw(p, "[" + row + "," + row + "]");
        final var guard = bound(p, false);
        final var inspected = guard.inspect(page);
        final var step = guard.project(inspected);
        assertEquals(2, step.projection().records().size());
        for (int index = 0; index < 2; index++) {
            final var record = step.projection().records().get(index);
            assertEquals(index + 1, record.occurrence());
            assertTrue(record.mapperValid());
            assertNull(record.captured().binding());
            assertEquals("UNBOUND", record.bindingStatus());
        }
        assertEquals(2, guard.admit(inspected, step).admittedRows());
        assertArrayEquals(page.body(), step.projection().rawPage().body());
    }

    @Test
    void fieldsetArrayItemAndResourceIntegrityGuardsRemainClosed() throws Exception {
        final var p = plan(FAT);
        final var guard = bound(p, false);
        final var missing = validHistoricalRow();
        missing.remove("service_at");
        assertEquals(
                ObservedExpansionSchemaGuard.Issue.MISSING_FIELD,
                guard.inspect(raw(p, missing)).issue());
        final var extra = validHistoricalRow();
        extra.put("unobserved_field", "synthetic");
        assertEquals(
                ObservedExpansionSchemaGuard.Issue.UNDECLARED_FIELD,
                guard.inspect(raw(p, extra)).issue());
        for (final String path : List.of("fit_fte_invoices_order_number", "invoices_mapping")) {
            final var wrongItem = validHistoricalRow();
            wrongItem.putArray(path).add(1);
            assertEquals(
                    ObservedExpansionSchemaGuard.Issue.ARRAY_ITEM_TYPE,
                    guard.inspect(raw(p, wrongItem)).issue());
        }
        final var current = ObservedExpansionSchemaCatalog.select(FAT);
        final byte[] bytes;
        try (var input = getClass().getResourceAsStream(current.resource())) {
            bytes = java.util.Objects.requireNonNull(input).readAllBytes();
        }
        bytes[0] ^= 1;
        assertThrows(
                IllegalStateException.class,
                () ->
                        ObservedExpansionSchemaCatalog.read(
                                current, new ByteArrayInputStream(bytes)));
    }

    @Test
    void otherThreeCurrentPinsStayEqualToTheirExplicitHistoricalSelections() {
        for (final var template :
                List.of(
                        ObservedExpansionCaptureTemplate.CAP,
                        ObservedExpansionCaptureTemplate.INV,
                        ObservedExpansionCaptureTemplate.SIN)) {
            final var current = ObservedExpansionSchemaCatalog.select(template);
            final var historical = ObservedExpansionSchemaCatalog.selectHistorical(template);
            assertEquals(current.schemaRelease(), historical.schemaRelease());
            assertEquals(current.resource(), historical.resource());
            assertEquals(current.schemaResourceSha256(), historical.schemaResourceSha256());
            assertEquals(
                    ObservedExpansionSchemaCatalog.schema(current).metadata(),
                    ObservedExpansionSchemaCatalog.schema(historical).metadata());
        }
    }

    private static ObservedExpansionSchemaGuard.BoundSchema bound(
            final ObservedExpansionCapturePlan plan, final boolean historical) {
        final var selection =
                historical
                        ? ObservedExpansionSchemaCatalog.selectHistorical(FAT)
                        : ObservedExpansionSchemaCatalog.select(FAT);
        return ObservedExpansionSchemaGuard.bind(
                plan, selection, 4924, ObservedExpansionSchemaCatalog.schema(selection).metadata());
    }

    private static ObjectNode validHistoricalRow() {
        final var row = ObservedExpansionSchemaFixtures.row(FAT);
        for (final String path :
                List.of(
                        "fit_ant_ils_due_date",
                        "fit_ant_ils_original_due_date",
                        "fit_ant_issue_date")) {
            row.put(path, "2026-10-17");
        }
        for (final String path :
                List.of(
                        "finished_at",
                        "fit_d_t_created_at",
                        "fit_fhe_cte_issued_at",
                        "service_at")) {
            row.put(path, "2026-10-17T12:00:00-03:00");
        }
        for (final String path :
                List.of(
                        "fit_ant_discount_value",
                        "fit_ant_interest_value",
                        "fit_ant_value",
                        "total")) {
            row.put(path, "1.2500");
        }
        return row;
    }

    private static void applyObservedUnion(final ObjectNode row, final String path) {
        switch (path) {
            case "fit_ant_ils_atn_transaction_date" -> row.put(path, "2026-10-17");
            case "fit_nse_number" -> row.put(path, 1);
            default -> row.putNull(path);
        }
    }
}
