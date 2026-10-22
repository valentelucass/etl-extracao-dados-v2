package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Pure preparation and synthetic reports. No admission, socket or physical SQL. */
class C78DevOwnedRecoveryPlanTest {
    @TempDir Path directory;

    private static C78DevCatalogPlan plan() {
        return C78DevCatalogPlan.prepareOwnedSample12Recovery(
                UUID.randomUUID(),
                C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE),
                UUID.fromString("065aaf34-6f37-4b4e-a84f-9f295968a255"),
                38360,
                58506);
    }

    private static List<String> counts() {
        return C78DevCatalogPlan.ownedRecoveryMetricNames().stream()
                .map(name -> name + "|0")
                .toList();
    }

    private static C78DevCatalogInspector.Report report(List<String> rows) {
        var plan = plan();
        return new C78DevCatalogInspector.Report(
                plan,
                plan.queries().stream()
                        .map(
                                query ->
                                        new C78DevCatalogInspector.Evidence(
                                                query,
                                                query
                                                                == C78DevCatalogPlan.Query
                                                                        .OWNED_RECOVERY_SUMMARY
                                                        ? rows
                                                        : List.of()))
                        .toList(),
                12);
    }

    @Test
    void onlyTheTenthSelectChangesFromCurrentMetadataAndScopeIsTyped() {
        var recovery = plan();
        var current =
                C78DevCatalogPlan.prepareSample12Current(
                        recovery.invocationId(), recovery.target());
        assertEquals(12, recovery.queries().size());
        assertEquals(C78DevCatalogPlan.Query.OWNED_RECOVERY_SUMMARY, recovery.queries().get(9));
        for (int index = 0; index < 12; index++) {
            if (index != 9) {
                assertEquals(current.queries().get(index), recovery.queries().get(index));
                assertEquals(
                        current.sql(current.queries().get(index)),
                        recovery.sql(recovery.queries().get(index)));
            }
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> recovery.sql(C78DevCatalogPlan.Query.CURRENT_MODULES));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogPlan(
                                recovery.invocationId(),
                                recovery.target(),
                                C78DevCatalogPlan.Mode.OWNED_SAMPLE12_RECOVERY12));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogPlan(
                                recovery.invocationId(),
                                recovery.target(),
                                C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12,
                                recovery.ownedRecoveryScope()));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1433, 65536, -1})
    void invalidSessionCoordinatesCannotSelectARecoveryPlan(int port) {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        C78DevCatalogPlan.prepareOwnedSample12Recovery(
                                UUID.randomUUID(),
                                plan().target(),
                                UUID.randomUUID(),
                                38360,
                                port));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        C78DevCatalogPlan.prepareOwnedSample12Recovery(
                                UUID.randomUUID(), plan().target(), UUID.randomUUID(), 0, 58506));
    }

    @Test
    void summaryIsReadOnlyAndScopesSessionByPidPortLoopbackAndDatabase() {
        String sql = plan().sql(C78DevCatalogPlan.Query.OWNED_RECOVERY_SUMMARY);
        assertTrue(sql.contains("host_process_id=38360"));
        assertTrue(sql.contains("client_tcp_port=58506"));
        assertTrue(sql.contains("s.database_id=DB_ID()"));
        assertTrue(sql.contains("c.client_net_address=N'127.0.0.1'"));
        assertTrue(sql.contains("c.local_tcp_port=1433"));
        assertTrue(sql.contains("JOIN stg.coleta_record r ON r.stage_record_id=t.stage_record_id"));
        assertTrue(sql.contains("WHERE r.execution_id=(SELECT execution_id FROM unit)"));
        assertFalse(sql.contains("coleta_exact_time WHERE execution_id"));
        for (String forbidden :
                List.of(
                        "INSERT",
                        "UPDATE",
                        "DELETE",
                        "MERGE",
                        "CREATE",
                        "ALTER",
                        "COMMIT",
                        "NOLOCK",
                        "READUNCOMMITTED",
                        "source_key",
                        "native_raw_json",
                        "SELECT *")) {
            assertFalse(sql.contains(forbidden), forbidden);
        }
        assertEquals(27, counts().size());
        assertEquals(
                27, C78DevCatalogPlan.rowsPerQuery(C78DevCatalogPlan.Query.OWNED_RECOVERY_SUMMARY));
    }

    @Test
    void completeZeroCountsMeanOnlyCurrentAbsenceAndUseASeparateReportSchema() throws Exception {
        var stored =
                C78DevCatalogReportStore.persist(
                        report(counts()),
                        directory,
                        C78DevCatalogPlan.Mode.OWNED_SAMPLE12_RECOVERY12);
        String text = Files.readString(stored.path());
        assertTrue(text.contains("C78_DEV_OWNED_RECOVERY_REPORT_V1"));
        assertTrue(text.contains("owned_occurrence_recovery_counts_only"));
        assertFalse(text.contains("SAMPLE_STAGED_READBACK_ROLLED_BACK"));
        assertFalse(text.contains("CURRENT_MODULES"));
        assertTrue(stored.bytes() <= 2097152);
        assertThrows(
                IOException.class,
                () ->
                        C78DevCatalogReportStore.persist(
                                report(counts()),
                                directory,
                                C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "order", "negative", "overflow", "extra"})
    void incompleteOrForgedSummaryCannotBePublished(String mutation) {
        var rows = new ArrayList<>(counts());
        switch (mutation) {
            case "missing" -> rows.remove(0);
            case "duplicate" -> rows.set(1, rows.get(0));
            case "order" -> Collections.swap(rows, 0, 1);
            case "negative" -> rows.set(0, rows.get(0).replace("|0", "|-1"));
            case "overflow" -> rows.set(0, rows.get(0).replace("|0", "|9999999999999999999"));
            case "extra" -> rows.add("UNKNOWN|0");
            default -> throw new AssertionError(mutation);
        }
        assertThrows(
                Exception.class,
                () ->
                        C78DevCatalogReportStore.persist(
                                report(rows),
                                directory,
                                C78DevCatalogPlan.Mode.OWNED_SAMPLE12_RECOVERY12));
    }
}
