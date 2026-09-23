package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static br.com.esl.etl.v2.contratos.caracterizacao.qfnd02.Qfnd02Vocabulary.Outcome;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Escrita atômica de receipt estritamente allowlisted sob target. */
public final class Qfnd02ReceiptWriter {

    private static final Pattern RECEIPT_ID = Pattern.compile("SYNTH_[A-Z0-9_]{3,95}");
    private final Path target;
    private final Clock clock;
    private final Supplier<String> receiptIdSupplier;
    private final ObjectWriter writer = new ObjectMapper().writer(canonicalPrettyPrinter());

    private Qfnd02ReceiptWriter(
            final Path target, final Clock clock, final Supplier<String> receiptIdSupplier) {
        this.target = target.toAbsolutePath().normalize();
        this.clock = Objects.requireNonNull(clock);
        this.receiptIdSupplier = Objects.requireNonNull(receiptIdSupplier);
        if (this.target.getFileName() == null
                || !"target".equals(this.target.getFileName().toString())) {
            throw new IllegalArgumentException("Receipt deve usar diretório target explícito.");
        }
    }

    public static Qfnd02ReceiptWriter forTarget(
            final Path target, final Clock clock, final Supplier<String> receiptIdSupplier) {
        return new Qfnd02ReceiptWriter(target, clock, receiptIdSupplier);
    }

    public Path write(final List<Qfnd02Result> results) {
        final Qfnd02Registry registry = Qfnd02Registry.loadDefault();
        final Set<String> expectedIds =
                Set.of("V2_012_FRETES_6389", "V2_012_LOCALIZACAO_8656_DATA_EXPORT");
        final List<Qfnd02Result> snapshot = List.copyOf(Objects.requireNonNull(results, "results"));
        if (snapshot.size() != 2) {
            throw new IllegalArgumentException(
                    "Receipt exige os dois bindings sintéticos aceitos.");
        }
        final Map<String, Qfnd02Result> actualResults =
                snapshot.stream()
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Qfnd02Result::profileId,
                                        result -> result,
                                        (left, right) -> {
                                            throw new IllegalArgumentException(
                                                    "Receipt possui binding duplicado.");
                                        }));
        final Map<String, Qfnd02Result> expectedResults =
                registry.profiles().stream()
                        .map(
                                profile ->
                                        registry.evaluator(profile.profileId())
                                                .evaluate(
                                                        profile,
                                                        registry.observation(profile.profileId())))
                        .collect(
                                Collectors.toUnmodifiableMap(
                                        Qfnd02Result::profileId, result -> result));
        if (!actualResults.keySet().equals(expectedIds)
                || !actualResults.equals(expectedResults)
                || actualResults.values().stream()
                        .anyMatch(
                                result ->
                                        result.outcome() != Outcome.SYNTHETIC_STRUCTURE_ACCEPTED
                                                || result.providerPass()
                                                || !result.reasons().isEmpty())) {
            throw new IllegalArgumentException(
                    "Receipt exige os dois bindings sintéticos aceitos.");
        }
        final String receiptId = receiptIdSupplier.get();
        if (receiptId == null || !RECEIPT_ID.matcher(receiptId).matches()) {
            throw new IllegalArgumentException("Identificador sintético de receipt inválido.");
        }
        final Path root = target.resolve("v2-012-characterization/q-fnd-02").normalize();
        final Path directory = root.resolve(receiptId).normalize();
        final Path destination = directory.resolve("receipt.json").normalize();
        if (!destination.startsWith(root) || containsUnsafeReparsePoint(target, directory)) {
            throw new IllegalArgumentException("Destino de receipt inseguro.");
        }
        Path temporary = null;
        try {
            Files.createDirectories(target);
            rejectUnsafeReparsePoint(target);
            Files.createDirectories(directory);
            if (containsUnsafeReparsePoint(target, directory)) {
                throw new IllegalArgumentException("Destino de receipt inseguro.");
            }
            rejectUnsafeReparsePoint(destination);
            final Path realTarget = target.toRealPath();
            final Path realDirectory = directory.toRealPath();
            if (!realDirectory.startsWith(realTarget)) {
                throw new IllegalArgumentException("Destino real de receipt escapou de target.");
            }
            final List<Qfnd02Result> sorted =
                    snapshot.stream()
                            .sorted(Comparator.comparing(Qfnd02Result::profileId))
                            .toList();
            final Map<String, Object> receipt = new LinkedHashMap<>();
            receipt.put("schemaVersion", "V2_012_Q_FND_02_RECEIPT_V1");
            receipt.put("receiptId", receiptId);
            receipt.put("createdAt", DateTimeFormatter.ISO_INSTANT.format(clock.instant()));
            receipt.put(
                    "outcome",
                    "FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES");
            receipt.put("profileStatus", "PREPARED_NOT_EXECUTED");
            receipt.put("gateStatus", "ORACLE_REQUIRED");
            receipt.put("providerEvidence", "NOT_EXECUTED");
            receipt.put(
                    "profiles", sorted.stream().map(Qfnd02ReceiptWriter::profileReceipt).toList());
            final byte[] content = writer.writeValueAsBytes(receipt);
            temporary = Files.createTempFile(directory, ".qfnd02-", ".tmp");
            Files.write(temporary, content);
            moveReplacing(temporary, destination);
            temporary = null;
            return destination.toAbsolutePath().normalize();
        } catch (final IOException exception) {
            throw new IllegalStateException(
                    "Falha sanitizada ao escrever receipt Q-FND-02.", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (final IOException ignored) {
                    // A falha original continua sendo a causa relevante.
                }
            }
        }
    }

    private static DefaultPrettyPrinter canonicalPrettyPrinter() {
        final DefaultIndenter indenter = new DefaultIndenter("  ", "\n");
        final DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.indentObjectsWith(indenter);
        printer.indentArraysWith(indenter);
        return printer;
    }

    private static Map<String, Object> profileReceipt(final Qfnd02Result result) {
        final Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("profileId", result.profileId());
        profile.put("profileSha256", result.profileFingerprint());
        profile.put("fixtureSha256", result.fixtureFingerprint());
        profile.put("outcome", result.outcome().name());
        return profile;
    }

    private static boolean containsUnsafeReparsePoint(final Path root, final Path destination) {
        Path current = root.toAbsolutePath().normalize();
        final Path normalizedDestination = destination.toAbsolutePath().normalize();
        if (isUnsafeReparsePoint(current)) {
            return true;
        }
        final Path relative = current.relativize(normalizedDestination);
        for (final Path component : relative) {
            current = current.resolve(component);
            if (isUnsafeReparsePoint(current)) {
                return true;
            }
        }
        return false;
    }

    private static void rejectUnsafeReparsePoint(final Path path) throws IOException {
        if (isUnsafeReparsePoint(path)) {
            throw new IllegalArgumentException("Destino contém link ou reparse point.");
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
                    "Falha ao inspecionar destino de receipt.", exception);
        }
    }

    private static void moveReplacing(final Path source, final Path destination)
            throws IOException {
        Files.move(
                source,
                destination,
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING);
    }
}
