package br.com.esl.etl.v2.contratos.caracterizacao;

import static br.com.esl.etl.v2.contratos.caracterizacao.CharacterizationVocabulary.Entity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/** Writer determinístico com Clock e identificador sintético injetados, restrito a target. */
public final class CharacterizationReceiptWriter {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final Path targetRoot;
    private final Path outputRoot;
    private final Clock clock;
    private final Supplier<String> receiptIdSource;

    private CharacterizationReceiptWriter(
            final Path targetDirectory, final Clock clock, final Supplier<String> receiptIdSource) {
        final Path target =
                Objects.requireNonNull(targetDirectory, "O diretório target é obrigatório.")
                        .toAbsolutePath()
                        .normalize();
        if (target.getFileName() == null || !"target".equals(target.getFileName().toString())) {
            throw new IllegalArgumentException("Receipts V2-012 só podem ser gravados sob target.");
        }
        targetRoot = target;
        outputRoot = target.resolve("v2-012-characterization").normalize();
        if (!outputRoot.startsWith(target)) {
            throw new IllegalArgumentException("O diretório de receipts escapou de target.");
        }
        this.clock = Objects.requireNonNull(clock, "O Clock do receipt é obrigatório.");
        this.receiptIdSource =
                Objects.requireNonNull(receiptIdSource, "A fonte de ID sintético é obrigatória.");
    }

    public static CharacterizationReceiptWriter forTargetDirectory(
            final Path targetDirectory, final Clock clock, final Supplier<String> receiptIdSource) {
        return new CharacterizationReceiptWriter(targetDirectory, clock, receiptIdSource);
    }

    public Path write(final CharacterizationSummary summary, final List<ReceiptBinding> bindings) {
        final CharacterizationSummary requiredSummary =
                Objects.requireNonNull(summary, "O summary é obrigatório.");
        final Map<Entity, ReceiptBinding> indexedBindings = indexBindings(bindings);
        if (indexedBindings.size() != requiredSummary.results().size()) {
            throw new IllegalArgumentException("Os bindings não correspondem ao summary.");
        }
        final List<CharacterizationReceipt.ProfileReceipt> profiles = new ArrayList<>();
        for (final CharacterizationResult result : requiredSummary.results()) {
            final ReceiptBinding binding = indexedBindings.get(result.entity());
            if (binding == null || !binding.matches(result)) {
                throw new IllegalArgumentException("O binding sanitizado diverge do resultado.");
            }
            profiles.add(
                    new CharacterizationReceipt.ProfileReceipt(
                            result.entity(),
                            result.profileFingerprint(),
                            result.observationFingerprint(),
                            result.contractFingerprint(),
                            binding.fixtureFingerprint(),
                            result.observationStatus(),
                            result.profileStatus(),
                            result.gateStatus(),
                            result.failClosedReasons().size()));
        }
        final String receiptId = receiptIdSource.get();
        final CharacterizationReceipt receipt =
                new CharacterizationReceipt(
                        "V2_012_RECEIPT_V1",
                        receiptId,
                        Instant.now(clock).toString(),
                        requiredSummary.outcome(),
                        requiredSummary.profileCount(),
                        profiles);
        final JsonNode tree = JSON.valueToTree(receipt);
        CharacterizationReportSanitizer.requireSanitized(tree);
        final byte[] content = CharacterizationFingerprint.canonicalBytes(tree);
        final Path directory = outputRoot.resolve(receipt.receiptId()).normalize();
        if (!directory.startsWith(outputRoot)) {
            throw new IllegalArgumentException("O identificador do receipt escapou de target.");
        }
        final Path destination = directory.resolve("receipt.json").normalize();
        try {
            Files.createDirectories(targetRoot);
            rejectReparsePoint(targetRoot);
            rejectReparsePoint(outputRoot);
            Files.createDirectories(outputRoot);
            rejectReparsePoint(outputRoot);
            Files.createDirectories(directory);
            rejectReparsePoint(directory);
            rejectReparsePoint(destination);
            final Path realTarget = targetRoot.toRealPath();
            final Path realDirectory = directory.toRealPath();
            if (!realDirectory.startsWith(realTarget)) {
                throw new IllegalArgumentException("O destino real do receipt escapou de target.");
            }
            final Path temporaryReceipt = Files.createTempFile(directory, ".receipt-", ".tmp");
            try {
                Files.write(
                        temporaryReceipt,
                        content,
                        StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                try {
                    Files.move(
                            temporaryReceipt,
                            destination,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING);
                } catch (final AtomicMoveNotSupportedException exception) {
                    Files.move(temporaryReceipt, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporaryReceipt);
            }
            return destination;
        } catch (final IOException exception) {
            throw new IllegalStateException("Não foi possível gravar o receipt sanitizado V2-012.");
        }
    }

    private static void rejectReparsePoint(final Path path) throws IOException {
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        final BasicFileAttributes attributes =
                Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (Files.isSymbolicLink(path) || attributes.isOther()) {
            throw new IllegalArgumentException(
                    "O destino do receipt contém link ou reparse point.");
        }
    }

    private static Map<Entity, ReceiptBinding> indexBindings(final List<ReceiptBinding> bindings) {
        final Map<Entity, ReceiptBinding> indexed = new EnumMap<>(Entity.class);
        for (final ReceiptBinding binding :
                Objects.requireNonNull(bindings, "Os bindings de receipt são obrigatórios.")) {
            if (binding == null || indexed.put(binding.entity(), binding) != null) {
                throw new IllegalArgumentException("Há binding de receipt nulo ou duplicado.");
            }
        }
        return Map.copyOf(indexed);
    }

    /** Binding criado somente de objetos já validados pelos loaders fechados. */
    public static final class ReceiptBinding {

        private final Entity entity;
        private final String profileId;
        private final String profileFingerprint;
        private final String contractFingerprint;
        private final String fixtureFingerprint;
        private final Set<String> observationFingerprints;

        private ReceiptBinding(
                final Entity entity,
                final String profileId,
                final String profileFingerprint,
                final String contractFingerprint,
                final String fixtureFingerprint,
                final Set<String> observationFingerprints) {
            this.entity = Objects.requireNonNull(entity, "A entidade do binding é obrigatória.");
            this.profileId =
                    Objects.requireNonNull(profileId, "O profile ID do binding é obrigatório.");
            CharacterizationReceipt.ProfileReceipt.requireSha(profileFingerprint);
            CharacterizationReceipt.ProfileReceipt.requireSha(contractFingerprint);
            CharacterizationReceipt.ProfileReceipt.requireSha(fixtureFingerprint);
            this.profileFingerprint = profileFingerprint;
            this.contractFingerprint = contractFingerprint;
            this.fixtureFingerprint = fixtureFingerprint;
            this.observationFingerprints =
                    Set.copyOf(
                            Objects.requireNonNull(
                                    observationFingerprints,
                                    "Os fingerprints de observação são obrigatórios."));
            if (this.observationFingerprints.isEmpty()) {
                throw new IllegalArgumentException("A fixture do binding não possui observações.");
            }
        }

        public static ReceiptBinding from(
                final CharacterizationProfile profile, final CharacterizationFixture fixture) {
            final CharacterizationProfile requiredProfile =
                    Objects.requireNonNull(profile, "O perfil do binding é obrigatório.");
            final CharacterizationFixture requiredFixture =
                    Objects.requireNonNull(fixture, "A fixture do binding é obrigatória.");
            if (!requiredProfile.profileId().equals(requiredFixture.profileId())) {
                throw new IllegalArgumentException("A fixture não pertence ao perfil do binding.");
            }
            if (!requiredProfile
                    .fixtureFingerprint()
                    .equals(requiredFixture.fixtureFingerprint())) {
                throw new IllegalArgumentException("A fixture não corresponde ao hash do perfil.");
            }
            return new ReceiptBinding(
                    requiredProfile.entity(),
                    requiredProfile.profileId(),
                    requiredProfile.profileFingerprint(),
                    requiredProfile.contract().contractFingerprint(),
                    requiredFixture.fixtureFingerprint(),
                    requiredFixture.scenarios().stream()
                            .filter(
                                    scenario ->
                                            scenario.expectedOutcome()
                                                    == CharacterizationVocabulary.ScenarioOutcome
                                                            .SYNTHETIC_STRUCTURE_ACCEPTED)
                            .map(scenario -> scenario.observation().observationFingerprint())
                            .collect(Collectors.toUnmodifiableSet()));
        }

        private Entity entity() {
            return entity;
        }

        private String fixtureFingerprint() {
            return fixtureFingerprint;
        }

        private boolean matches(final CharacterizationResult result) {
            return entity == result.entity()
                    && profileId.equals(result.profileId())
                    && profileFingerprint.equals(result.profileFingerprint())
                    && contractFingerprint.equals(result.contractFingerprint())
                    && observationFingerprints.contains(result.observationFingerprint());
        }
    }
}
