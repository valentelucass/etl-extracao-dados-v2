package br.com.esl.etl.v2.contratos.medicao;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Escrita allowlisted, determinística e atômica do receipt sintético sob target. */
public final class MeasurementReceiptWriter {

    private static final String RECEIPT_FILE = "measurement-foundation-receipt.json";
    private static final ObjectWriter JSON_WRITER =
            new ObjectMapper().writer(canonicalPrettyPrinter());

    private final Path targetRoot;
    private final Path outputRoot;

    private MeasurementReceiptWriter(final Path targetDirectory) {
        targetRoot =
                Objects.requireNonNull(targetDirectory, "O diretório target é obrigatório.")
                        .toAbsolutePath()
                        .normalize();
        if (targetRoot.getFileName() == null
                || !"target".equals(targetRoot.getFileName().toString())) {
            throw new IllegalArgumentException(
                    "O receipt V2-050 exige um diretório target explícito.");
        }
        outputRoot = targetRoot.resolve("v2-050-measurement").normalize();
        if (!outputRoot.startsWith(targetRoot)) {
            throw new IllegalArgumentException("O diretório do receipt escapou de target.");
        }
    }

    public static MeasurementReceiptWriter forTargetDirectory(final Path targetDirectory) {
        return new MeasurementReceiptWriter(targetDirectory);
    }

    public Path write(final String fileName, final List<MeasurementRun> runs) {
        if (!RECEIPT_FILE.equals(fileName)) {
            throw new IllegalArgumentException(
                    "O nome do receipt V2-050 não pertence à allowlist.");
        }
        final List<MeasurementRun> snapshot = copyAndSort(runs);
        final Path destination = outputRoot.resolve(fileName).normalize();
        if (!destination.startsWith(outputRoot)) {
            throw new IllegalArgumentException(
                    "O caminho do receipt escapou do diretório governado.");
        }
        final byte[] content = serialize(snapshot);

        Path temporary = null;
        try {
            rejectUnsafeReparsePoint(targetRoot);
            Files.createDirectories(targetRoot);
            rejectUnsafeReparsePoint(targetRoot);
            rejectUnsafeReparsePoint(outputRoot);
            Files.createDirectories(outputRoot);
            if (containsUnsafeReparsePoint(targetRoot, outputRoot)) {
                throw new IllegalArgumentException("O destino contém link ou reparse point.");
            }
            rejectUnsafeReparsePoint(destination);
            final Path realTarget = targetRoot.toRealPath();
            final Path realOutput = outputRoot.toRealPath();
            if (!realOutput.startsWith(realTarget)) {
                throw new IllegalArgumentException("O destino real do receipt escapou de target.");
            }

            temporary = Files.createTempFile(outputRoot, ".v2-050-measurement-", ".tmp");
            Files.write(
                    temporary,
                    content,
                    StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            Files.move(
                    temporary,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
            temporary = null;
            rejectUnsafeReparsePoint(destination);
            return destination.toAbsolutePath().normalize();
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Não foi possível gravar o receipt sintético V2-050.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (final IOException ignored) {
                    // A falha original permanece como causa relevante e sanitizada.
                }
            }
        }
    }

    private static List<MeasurementRun> copyAndSort(final List<MeasurementRun> runs) {
        final List<MeasurementRun> copy =
                new ArrayList<>(Objects.requireNonNull(runs, "As execuções são obrigatórias."));
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("O receipt exige execuções não nulas.");
        }
        copy.sort(
                Comparator.comparing(
                                (MeasurementRun run) -> run.evidence().plan().streamer().ordinal())
                        .thenComparingInt(run -> run.evidence().plan().dataPages())
                        .thenComparing(run -> run.assessment().reason().ordinal()));
        return List.copyOf(copy);
    }

    private static byte[] serialize(final List<MeasurementRun> runs) {
        final Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("schemaVersion", "2026-09-07.v2-050.measurement-receipt.1");
        receipt.put("fixtureKind", "SYNTHETIC_FIXTURE");
        receipt.put("diagnosticsPolicy", "JVM_HEAP_DIAGNOSTIC_ONLY");
        receipt.put("mutantControl", "LEAK_MUTANT_REJECTED");
        receipt.put("runs", runs.stream().map(MeasurementReceiptWriter::runEntry).toList());
        try {
            final String json = JSON_WRITER.writeValueAsString(receipt) + "\n";
            return json.getBytes(StandardCharsets.UTF_8);
        } catch (final JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Não foi possível serializar o receipt sintético V2-050.", exception);
        }
    }

    private static Map<String, Object> runEntry(final MeasurementRun run) {
        final MeasurementPlan plan = run.evidence().plan();
        final MeasurementEvidence evidence = run.evidence();
        final MeasurementDiagnostics diagnostics = run.diagnostics();
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("streamer", plan.streamerName());
        entry.put("dataPages", plan.dataPages());
        entry.put("recordsPerPage", plan.recordsPerPage());
        entry.put("evidence", evidenceEntry(evidence));
        entry.put("diagnostics", diagnosticsEntry(diagnostics));
        entry.put("assessment", assessmentEntry(run.assessment()));
        return entry;
    }

    private static Map<String, Object> evidenceEntry(final MeasurementEvidence evidence) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("fetchedPages", evidence.fetchedPages());
        entry.put("consumedPages", evidence.consumedPages());
        entry.put("records", evidence.records());
        entry.put("bytes", evidence.bytes());
        entry.put("maxInFlightPages", evidence.maxInFlightPages());
        entry.put("finalInFlightPages", evidence.finalInFlightPages());
        entry.put("acquisitions", evidence.acquisitions());
        entry.put("releases", evidence.releases());
        entry.put("maxRetainedPages", evidence.maxRetainedPages());
        entry.put("finalRetainedPages", evidence.finalRetainedPages());
        return entry;
    }

    private static Map<String, Object> diagnosticsEntry(final MeasurementDiagnostics diagnostics) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("heapBeforeBytes", diagnostics.heapBeforeBytes());
        entry.put("heapAfterBytes", diagnostics.heapAfterBytes());
        entry.put("heapPeakBytes", diagnostics.heapPeakBytes());
        entry.put("durationNanos", diagnostics.durationNanos());
        return entry;
    }

    private static Map<String, Object> assessmentEntry(final MeasurementAssessment assessment) {
        final Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("disposition", assessment.disposition().name());
        entry.put("reason", assessment.reason().name());
        return entry;
    }

    private static DefaultPrettyPrinter canonicalPrettyPrinter() {
        final DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        final DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    private static boolean containsUnsafeReparsePoint(final Path root, final Path destination) {
        Path current = root.toAbsolutePath().normalize();
        final Path normalizedDestination = destination.toAbsolutePath().normalize();
        if (!normalizedDestination.startsWith(current) || isUnsafeReparsePoint(current)) {
            return true;
        }
        for (final Path component : current.relativize(normalizedDestination)) {
            current = current.resolve(component);
            if (isUnsafeReparsePoint(current)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectUnsafeReparsePoint(final Path path) {
        if (isUnsafeReparsePoint(path)) {
            throw new IllegalArgumentException("O destino contém link ou reparse point.");
        }
    }

    private static boolean isUnsafeReparsePoint(final Path path) {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return false;
        }
        try {
            final BasicFileAttributes attributes =
                    Files.readAttributes(
                            path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return Files.isSymbolicLink(path) || attributes.isOther();
        } catch (final IOException exception) {
            throw new IllegalArgumentException(
                    "Falha ao inspecionar o destino do receipt.", exception);
        }
    }
}
