package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Synthetic metadata proves complete bounded serialization, never actual database contracts. */
class C78DevCatalogCurrentSampleTest {
    @TempDir Path directory;

    private static C78DevCatalogPlan plan() {
        return C78DevCatalogPlan.prepareSample12Current(
                UUID.randomUUID(),
                C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
    }

    private static List<String> modules() throws Exception {
        final byte[] raw = "A".repeat(8193).getBytes(StandardCharsets.UTF_16LE);
        final String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        final List<String> rows = new ArrayList<>();
        for (final String name : C78DevCatalogPlan.currentModuleNames()) {
            for (int offset = 0; offset < raw.length; offset += 1024) {
                rows.add(
                        name.replace('.', '|')
                                + "|"
                                + offset / 1024
                                + "|"
                                + raw.length
                                + "|"
                                + digest
                                + "|"
                                + HexFormat.of()
                                        .formatHex(
                                                raw, offset, Math.min(offset + 1024, raw.length)));
            }
        }
        return rows;
    }

    private static C78DevCatalogInspector.Report report(final List<String> modules) {
        final var selected = plan();
        final List<C78DevCatalogInspector.Evidence> evidence = new ArrayList<>();
        for (final var query : selected.queries()) {
            evidence.add(
                    new C78DevCatalogInspector.Evidence(
                            query,
                            query == C78DevCatalogPlan.Query.CURRENT_MODULES
                                    ? modules
                                    : List.of()));
        }
        return new C78DevCatalogInspector.Report(selected, evidence, 12);
    }

    @Test
    void separateCurrentPlanHasTwelveClosedSelectsAndTheFiniteCaptureDependencies() {
        final var current = plan();
        assertEquals(C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12, current.mode());
        assertEquals(12, current.queries().size());
        assertEquals(C78DevCatalogPlan.Query.CURRENT_MODULES, current.queries().get(9));
        assertEquals(C78DevCatalogPlan.Query.CURRENT_INDEXES, current.queries().get(5));
        assertTrue(
                current.sql(C78DevCatalogPlan.Query.CURRENT_INDEXES)
                        .contains("DATALENGTH(i.filter_definition)"));
        assertEquals(12, C78DevCatalogPlan.Query.CURRENT_INDEXES.columns());
        assertEquals(34, C78DevCatalogPlan.currentModuleNames().size());
        final String modules = current.sql(C78DevCatalogPlan.Query.CURRENT_MODULES);
        for (final String name : C78DevCatalogPlan.currentModuleNames()) {
            final String[] parts = name.split("\\.");
            assertTrue(modules.contains("('" + parts[0] + "','" + parts[1] + "')"));
        }
        assertTrue(
                current.sql(C78DevCatalogPlan.Query.PARAMETERS)
                        .contains("fn_runtime_tariff_valid"));
        assertTrue(current.sql(C78DevCatalogPlan.Query.PARAMETERS).contains("sys.objects p"));
        assertFalse(modules.contains("fn_runtime_coleta_publication_hash"));
        assertEquals(1900000, current.reportCharacters());
        assertEquals(2048, C78DevCatalogPlan.rowsPerQuery(C78DevCatalogPlan.Query.CURRENT_MODULES));
        assertEquals(512, C78DevCatalogPlan.rowsPerQuery(C78DevCatalogPlan.Query.COLUMNS));
    }

    @Test
    void oldModesKeepTheirQueryRowsCellAndReportLimits() {
        final var target = plan().target();
        final var original = C78DevCatalogPlan.prepare(UUID.randomUUID(), target);
        final var coverage = C78DevCatalogPlan.prepareCoverage(UUID.randomUUID(), target);
        assertEquals(262144, original.reportCharacters());
        assertEquals(1500000, coverage.reportCharacters());
        assertEquals(512, C78DevCatalogPlan.rowsPerQuery(C78DevCatalogPlan.Query.MODULE_CONTRACTS));
        assertEquals(
                512, C78DevCatalogPlan.cellCharacters(C78DevCatalogPlan.Query.MODULE_CONTRACTS));
        assertEquals(C78DevCatalogPlan.Query.DQ_METADATA, original.queries().get(9));
        assertEquals(C78DevCatalogPlan.Query.MODULE_CONTRACTS, coverage.queries().get(9));
        assertThrows(
                IllegalArgumentException.class,
                () -> original.sql(C78DevCatalogPlan.Query.CURRENT_MODULES));
        assertThrows(
                IllegalArgumentException.class,
                () -> coverage.sql(C78DevCatalogPlan.Query.CURRENT_MODULES));
    }

    @Test
    void moreThanFiveHundredTwelveSegmentsPersistWithExactWholeFileReadback() throws Exception {
        final var rows = modules();
        assertTrue(rows.size() > 512);
        final var result =
                C78DevCatalogReportStore.persist(
                        report(rows), directory, C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12);
        final byte[] actual = Files.readAllBytes(result.path());
        assertTrue(actual.length > C78DevCatalogReportStore.MAX_FILE_BYTES);
        assertTrue(actual.length <= 2097152);
        assertEquals(actual.length, result.bytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)),
                result.sha256());
        assertTrue(new String(actual, StandardCharsets.UTF_8).contains("CURRENT_MODULES"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "duplicate", "order", "hash", "unavailable", "foreign"})
    void incompleteEncryptedOrChangedModuleMaterialNeverPublishesAReport(final String mutation)
            throws Exception {
        final List<String> rows = modules();
        switch (mutation) {
            case "missing" -> rows.remove(1);
            case "duplicate" -> rows.add(1, rows.get(0));
            case "order" -> Collections.swap(rows, 0, 1);
            case "hash" -> rows.set(0, rows.get(0).replaceFirst("[a-f0-9]{64}", "0".repeat(64)));
            case "unavailable" -> {
                final String first =
                        C78DevCatalogPlan.currentModuleNames().get(0).replace('.', '|');
                rows.removeIf(row -> row.startsWith(first + "|"));
                rows.add(0, first + "|0|0|UNAVAILABLE|UNAVAILABLE");
            }
            case "foreign" -> rows.set(0, rows.get(0).replace("core|", "other|"));
            default -> throw new AssertionError(mutation);
        }
        assertThrows(
                IOException.class,
                () ->
                        C78DevCatalogReportStore.persist(
                                report(rows),
                                directory,
                                C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12));
        try (var files = Files.list(directory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void theTwoMiBTransportLimitCountsEncodedBytesAndNeverTruncates() {
        assertThrows(
                IOException.class,
                () ->
                        C78DevCatalogReportStore.encodeUtf8(
                                "é".repeat(1048577), C78DevCatalogPlan.Mode.SAMPLE12_CURRENT12));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogInspector.Evidence(
                                C78DevCatalogPlan.Query.CURRENT_MODULES,
                                Collections.nCopies(2049, "A")));
    }
}
