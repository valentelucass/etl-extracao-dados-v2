package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.configuracao.C78LocalSqlTarget;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real files and complete readback, with synthetic catalog metadata, never physical SQL. */
final class C78DevCatalogReportStoreTest {
    @TempDir Path ownDirectory;

    private static C78DevCatalogInspector.Report report(final boolean large) {
        final var plan =
                C78DevCatalogPlan.prepare(
                        UUID.randomUUID(),
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
        final var evidence = new ArrayList<C78DevCatalogInspector.Evidence>();
        for (final var query : plan.queries()) {
            final int width = Math.min(1000, query.columns() * 200);
            final var rows = large ? Collections.nCopies(20, "A".repeat(width)) : List.<String>of();
            evidence.add(new C78DevCatalogInspector.Evidence(query, rows));
        }
        return new C78DevCatalogInspector.Report(plan, evidence, 12);
    }

    @Test
    void fullReportLargerThanConsoleCapHasExactWholeFileHash() throws Exception {
        final var stored = C78DevCatalogReportStore.persist(report(true), ownDirectory);
        final byte[] actual = Files.readAllBytes(stored.path());
        assertTrue(actual.length > 65536);
        assertTrue(actual.length <= C78DevCatalogReportStore.MAX_FILE_BYTES);
        assertEquals(actual.length, stored.bytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(actual)),
                stored.sha256());
        final String text = new String(actual, StandardCharsets.UTF_8);
        assertTrue(text.endsWith("]}\n"));
        assertTrue(text.contains("CONTEXT_POST"));
        assertFalse(text.contains("jdbc:sqlserver"));
        assertFalse(text.contains("password"));
    }

    @Test
    void existingReportCannotBeOverwrittenAndBytesRemainIdentical() throws IOException {
        final var report = report(false);
        final var stored = C78DevCatalogReportStore.persist(report, ownDirectory);
        final byte[] before = Files.readAllBytes(stored.path());
        assertThrows(
                IOException.class, () -> C78DevCatalogReportStore.persist(report, ownDirectory));
        assertEquals(
                HexFormat.of().formatHex(before),
                HexFormat.of().formatHex(Files.readAllBytes(stored.path())));
    }

    @Test
    void missingOrFileDirectoryRefusesWithoutCreatingParent() throws IOException {
        final Path missing = ownDirectory.resolve("missing");
        assertThrows(
                IOException.class, () -> C78DevCatalogReportStore.persist(report(false), missing));
        assertFalse(Files.exists(missing));
        final Path file = Files.writeString(ownDirectory.resolve("plain-file"), "owned");
        assertThrows(
                IOException.class, () -> C78DevCatalogReportStore.persist(report(false), file));
        assertEquals("owned", Files.readString(file));
    }

    @Test
    void metadataJsonInjectionAndWrongQueryOrderAreRejected() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new C78DevCatalogInspector.Evidence(
                                C78DevCatalogPlan.Query.TABLE_PRESENCE, List.of("\"bad\\value\n")));
        final var report = report(false);
        final var reordered = new ArrayList<>(report.evidence());
        Collections.swap(reordered, 0, 1);
        assertThrows(
                IllegalArgumentException.class,
                () -> new C78DevCatalogInspector.Report(report.plan(), reordered, 12));
    }

    private static List<String> moduleRows() throws Exception {
        final byte[] raw = "A".repeat(6000).getBytes(StandardCharsets.UTF_16LE);
        final String digest =
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw));
        final var rows = new ArrayList<String>();
        for (final String name : C78DevCatalogPlan.moduleContractNames()) {
            final String[] parts = name.split("\\.");
            for (int offset = 0; offset < raw.length; offset += 256) {
                rows.add(
                        parts[0]
                                + "|"
                                + parts[1]
                                + "|"
                                + offset / 256
                                + "|"
                                + raw.length
                                + "|"
                                + digest
                                + "|"
                                + HexFormat.of()
                                        .formatHex(
                                                raw, offset, Math.min(offset + 256, raw.length)));
            }
        }
        return rows;
    }

    private static C78DevCatalogInspector.Report coverage(final List<String> modules) {
        final var plan =
                C78DevCatalogPlan.prepareCoverage(
                        UUID.randomUUID(),
                        C78LocalSqlTarget.approved(C78LocalSqlTarget.APPROVAL_REFERENCE));
        final var evidence = new ArrayList<C78DevCatalogInspector.Evidence>();
        for (final var query : plan.queries()) {
            final var rows =
                    query == C78DevCatalogPlan.Query.MODULE_CONTRACTS
                            ? modules
                            : Collections.nCopies(
                                    24, "A".repeat(Math.min(1000, query.columns() * 200)));
            evidence.add(new C78DevCatalogInspector.Evidence(query, rows));
        }
        return new C78DevCatalogInspector.Report(plan, evidence, 12);
    }

    @Test
    void explicitCoverageStoresWholeModulesBeyondOriginalCapWithExactByteHash() throws Exception {
        final var stored =
                C78DevCatalogReportStore.persist(
                        coverage(moduleRows()),
                        ownDirectory,
                        C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12);
        final byte[] bytes = Files.readAllBytes(stored.path());
        assertTrue(bytes.length > C78DevCatalogReportStore.MAX_FILE_BYTES);
        assertTrue(bytes.length <= 2097152);
        assertEquals(bytes.length, stored.bytes());
        assertEquals(
                HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                stored.sha256());
    }

    @Test
    void foreignAndImplicitModesRefuseBeforeCreatingAnyReport() throws Exception {
        final var coverage = coverage(moduleRows());
        assertThrows(
                IOException.class, () -> C78DevCatalogReportStore.persist(coverage, ownDirectory));
        assertThrows(
                IOException.class,
                () ->
                        C78DevCatalogReportStore.persist(
                                coverage, ownDirectory, C78DevCatalogPlan.Mode.ORIGINAL12));
        assertThrows(
                IOException.class,
                () ->
                        C78DevCatalogReportStore.persist(
                                report(false),
                                ownDirectory,
                                C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12));
        try (var files = Files.list(ownDirectory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void utf8ByteLimitsAreExactAndCannotBeReplacedByCharacterCounts() throws Exception {
        for (final var mode : C78DevCatalogPlan.Mode.values()) {
            final int limit = mode == C78DevCatalogPlan.Mode.ORIGINAL12 ? 327680 : 2097152;
            assertEquals(
                    limit, C78DevCatalogReportStore.encodeUtf8("é".repeat(limit / 2), mode).length);
            assertThrows(
                    IOException.class,
                    () -> C78DevCatalogReportStore.encodeUtf8("é".repeat(limit / 2) + "A", mode));
            assertThrows(
                    IOException.class,
                    () -> C78DevCatalogReportStore.encodeUtf8("€".repeat(limit / 2), mode));
            assertThrows(
                    IOException.class, () -> C78DevCatalogReportStore.encodeUtf8("\uD800", mode));
        }
    }

    @Test
    void readbackRejectsTruncatedAndChangedFilesAndReleasesHandle() throws Exception {
        final Path file = Files.writeString(ownDirectory.resolve("own-readback"), "ABC");
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            assertThrows(
                    IOException.class,
                    () ->
                            C78DevCatalogReportStore.readback(
                                    channel,
                                    "ABCD".getBytes(StandardCharsets.UTF_8),
                                    C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12));
            assertThrows(
                    IOException.class,
                    () ->
                            C78DevCatalogReportStore.readback(
                                    channel,
                                    "ABD".getBytes(StandardCharsets.UTF_8),
                                    C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12));
        }
        Files.delete(file);
        assertFalse(Files.exists(file));
    }

    @Test
    void modulesRejectMissingReorderedDuplicateForeignAndChangedChunks() throws Exception {
        final var original = moduleRows();
        for (int mutation = 0; mutation < 6; mutation++) {
            final var rows = new ArrayList<>(original);
            switch (mutation) {
                case 0 -> rows.remove(1);
                case 1 -> Collections.swap(rows, 0, 1);
                case 2 -> rows.add(1, rows.get(0));
                case 3 ->
                        rows.set(
                                0,
                                rows.get(0)
                                        .replace("ctl|usp_observe_platform_health", "ctl|foreign"));
                case 4 -> rows.set(0, rows.get(0).substring(0, rows.get(0).length() - 4) + "FFFF");
                case 5 -> rows.remove(rows.size() - 1);
                default -> throw new AssertionError();
            }
            final var report = coverage(rows);
            assertThrows(
                    IOException.class,
                    () ->
                            C78DevCatalogReportStore.persist(
                                    report,
                                    ownDirectory,
                                    C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12));
        }
        try (var files = Files.list(ownDirectory)) {
            assertEquals(0, files.count());
        }
    }

    @Test
    void modulesRejectInconsistentDeclaredBytesAndOversizedModuleBeforeWrite() throws Exception {
        for (final String altered : List.of("11998", "999999", "12001")) {
            final var rows = new ArrayList<>(moduleRows());
            rows.set(1, rows.get(1).replace("|12000|", "|" + altered + "|"));
            assertThrows(
                    IOException.class,
                    () ->
                            C78DevCatalogReportStore.persist(
                                    coverage(rows),
                                    ownDirectory,
                                    C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12));
        }
    }

    @Test
    void unavailableModuleIsPersistedAsUnobservedAndDoesNotBecomeQualification() throws Exception {
        final var rows = new ArrayList<String>();
        for (final String name : C78DevCatalogPlan.moduleContractNames()) {
            rows.add(name.replace('.', '|') + "|0|0|UNAVAILABLE|UNAVAILABLE");
        }
        final var stored =
                C78DevCatalogReportStore.persist(
                        coverage(rows), ownDirectory, C78DevCatalogPlan.Mode.CONTRACT_COVERAGE12);
        assertTrue(Files.readString(stored.path()).contains("UNAVAILABLE"));
    }
}
