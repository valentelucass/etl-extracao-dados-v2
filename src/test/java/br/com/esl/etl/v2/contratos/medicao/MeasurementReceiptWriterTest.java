package br.com.esl.etl.v2.contratos.medicao;

import static java.nio.charset.CodingErrorAction.REPORT;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MeasurementReceiptWriterTest {

    private static final String RECEIPT_FILE = "measurement-foundation-receipt.json";

    @TempDir Path temporaryDirectory;

    @Test
    void writesDeterministicStrictUtf8WithExplicitLfAndNoTemporaryResidue() throws Exception {
        final Path target = temporaryDirectory.resolve("determinism/target");
        final MeasurementReceiptWriter writer = MeasurementReceiptWriter.forTargetDirectory(target);
        final List<MeasurementRun> runs = List.of(leakRun(), healthyRun());

        final Path receipt = writer.write(RECEIPT_FILE, runs);
        final byte[] first = Files.readAllBytes(receipt);
        final byte[] second = Files.readAllBytes(writer.write(RECEIPT_FILE, runs));
        final String text =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(REPORT)
                        .onUnmappableCharacter(REPORT)
                        .decode(ByteBuffer.wrap(second))
                        .toString();

        assertArrayEquals(first, second);
        assertTrue(receipt.startsWith(target.toAbsolutePath().normalize()));
        assertEquals(
                target.toAbsolutePath()
                        .normalize()
                        .resolve("v2-050-measurement")
                        .resolve(RECEIPT_FILE),
                receipt);
        assertFalse(
                second.length >= 3
                        && second[0] == (byte) 0xEF
                        && second[1] == (byte) 0xBB
                        && second[2] == (byte) 0xBF);
        assertTrue(text.endsWith("\n"));
        assertFalse(text.contains("\r"));
        assertTrue(text.contains("SYNTHETIC_FIXTURE"));
        assertTrue(text.contains("DataExportPageStreamer"));
        assertTrue(text.contains("MANAGED_IN_FLIGHT_BOUND_PROVEN_SYNTHETICALLY"));
        assertTrue(text.contains("LEAK_MUTANT_REJECTED"));
        assertTrue(text.contains("JVM_HEAP_DIAGNOSTIC_ONLY"));
        assertFalse(text.contains("https://"));
        assertFalse(text.contains("cursor"));
        assertFalse(text.contains("payload"));
        try (var files = Files.list(receipt.getParent())) {
            assertFalse(
                    files.anyMatch(
                            path ->
                                    path.getFileName().toString().endsWith(".tmp")
                                            || path.getFileName().toString().startsWith(".")));
        }
    }

    @Test
    void rejectsAnOutsideRootAndTraversalOrUnallowlistedFileNames() {
        assertThrows(
                IllegalArgumentException.class,
                () -> MeasurementReceiptWriter.forTargetDirectory(temporaryDirectory));

        final Path target = temporaryDirectory.resolve("containment/target");
        final MeasurementReceiptWriter writer = MeasurementReceiptWriter.forTargetDirectory(target);
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write("../escape.json", List.of(healthyRun())));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write("../../outside.json", List.of(healthyRun())));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write("Measurement-Foundation-Receipt.json", List.of(healthyRun())));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write("receipt.txt", List.of(healthyRun())));
        assertFalse(Files.exists(target.resolve("escape.json")));
        assertFalse(Files.exists(temporaryDirectory.resolve("outside.json")));
    }

    @Test
    void refusesASymbolicLinkInsideTheMeasurementRootWhenSupported() throws Exception {
        final Path target = temporaryDirectory.resolve("symlink/target");
        final Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(target);
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(target.resolve("v2-050-measurement"), outside);
        } catch (final UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Filesystem sem symlink para esta prova.");
        }
        final MeasurementReceiptWriter writer = MeasurementReceiptWriter.forTargetDirectory(target);

        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(RECEIPT_FILE, List.of(healthyRun())));
        assertFalse(Files.exists(outside.resolve(RECEIPT_FILE)));
    }

    @Test
    void atomicallyReplacesAHardLinkedDestinationWithoutChangingTheExternalInode()
            throws Exception {
        final Path target = temporaryDirectory.resolve("hardlink/target");
        final Path destination = target.resolve("v2-050-measurement").resolve(RECEIPT_FILE);
        final Path external = temporaryDirectory.resolve("external-sentinel.json");
        Files.createDirectories(destination.getParent());
        Files.writeString(external, "SYNTHETIC_EXTERNAL_SENTINEL", StandardCharsets.UTF_8);
        try {
            Files.createLink(destination, external);
        } catch (final UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.assumeTrue(false, "Filesystem sem hardlink para esta prova.");
        }

        final Path written =
                MeasurementReceiptWriter.forTargetDirectory(target)
                        .write(RECEIPT_FILE, List.of(healthyRun()));

        assertEquals(
                "SYNTHETIC_EXTERNAL_SENTINEL", Files.readString(external, StandardCharsets.UTF_8));
        assertFalse(Files.isSameFile(external, written));
        assertTrue(Files.readString(written, StandardCharsets.UTF_8).contains("SYNTHETIC_FIXTURE"));
    }

    private static MeasurementRun healthyRun() {
        final MeasurementEvidence evidence =
                new MeasurementEvidence(
                        MeasurementPlan.dataExport(16),
                        17L,
                        16L,
                        128L,
                        1_026L,
                        1L,
                        0L,
                        17L,
                        17L,
                        0L,
                        0L);
        return new MeasurementRun(
                evidence,
                new MeasurementDiagnostics(1_000L, 1_100L, 1_250L, 75L),
                new MeasurementEvaluator().evaluate(evidence));
    }

    private static MeasurementRun leakRun() {
        final MeasurementEvidence evidence =
                new MeasurementEvidence(
                        MeasurementPlan.graphQl(16),
                        16L,
                        16L,
                        128L,
                        512L,
                        1L,
                        0L,
                        16L,
                        16L,
                        1L,
                        1L);
        return new MeasurementRun(
                evidence,
                new MeasurementDiagnostics(2_000L, 2_100L, 2_250L, 95L),
                new MeasurementEvaluator().evaluate(evidence));
    }
}
