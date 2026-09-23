package br.com.esl.etl.v2.contratos.caracterizacao.qfnd02;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class Qfnd02DeterminismTest {

    private static final Map<String, List<String>> EXPECTED_RESOURCE_BINDINGS =
            Map.of(
                    "V2_012_FRETES_6389",
                    List.of(
                            "e58300114443f94673545a12d8c8ece983725bfaa99be37c855df90b99451ea9",
                            "a1ce0672197da66075e8bdce4ba659438f304aa41c5b3bd067f3d1c36a227647"),
                    "V2_012_LOCALIZACAO_8656_DATA_EXPORT",
                    List.of(
                            "d557f2bbfaea0e130aded1f991b7a6c96e9850162160b83832e4b3e32b10a37f",
                            "1162b138240086e0b1b9eecfcda7d6a80d1e65a82c7a28dea8427161d06402f3"));

    @TempDir Path temporaryDirectory;

    @Test
    void fingerprintsAndSanitizedReceiptsAreStableForAnyProfileOrder() throws Exception {
        final Qfnd02Registry registry = Qfnd02Registry.loadDefault();
        final List<Qfnd02Result> forward = acceptedResults(registry);
        final List<Qfnd02Result> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);
        final Clock clock = Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC);
        final Qfnd02ReceiptWriter writer =
                Qfnd02ReceiptWriter.forTarget(
                        temporaryDirectory.resolve("target"), clock, () -> "SYNTH_QFND02_RECEIPT");

        final Path first = writer.write(forward);
        final byte[] firstBytes = Files.readAllBytes(first);
        assertEquals(
                "327ca45986ab6ebe434f56b641053faa8a68f88d946636cdea22400321fd2dd4",
                sha256(firstBytes));
        assertFalse(new String(firstBytes, StandardCharsets.UTF_8).contains("\r"));
        final Path second = writer.write(reverse);
        final String content = Files.readString(second);

        assertArrayEquals(firstBytes, Files.readAllBytes(second));
        assertTrue(
                content.contains(
                        "FOUNDATION_OFFLINE_COMPLETE_Q02_FRETES_LOCALIZACAO_PENDING_ORACLES"));
        assertTrue(content.contains("PREPARED_NOT_EXECUTED"));
        assertTrue(content.contains("ORACLE_REQUIRED"));
        assertFalse(content.contains("sourceInstance"));
        assertFalse(content.contains("tenantScope"));
        assertFalse(content.contains("payload"));
        assertFalse(content.contains("cursor"));
        assertFalse(content.contains("businessId"));
        for (final Qfnd02Result result : forward) {
            assertEquals(
                    EXPECTED_RESOURCE_BINDINGS.get(result.profileId()).get(0),
                    result.profileFingerprint());
            assertEquals(
                    EXPECTED_RESOURCE_BINDINGS.get(result.profileId()).get(1),
                    result.fixtureFingerprint());
            assertTrue(content.contains("\"profileSha256\""));
            assertTrue(content.contains("\"fixtureSha256\""));
        }
        assertEquals(
                temporaryDirectory
                        .resolve(
                                "target/v2-012-characterization/q-fnd-02/SYNTH_QFND02_RECEIPT/receipt.json")
                        .toAbsolutePath()
                        .normalize(),
                second);
        try (var files = Files.list(second.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void refusesDestinationsOutsideTargetAndUnsafeReceiptIdentifiers() {
        final Clock clock = Clock.systemUTC();
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        Qfnd02ReceiptWriter.forTarget(
                                temporaryDirectory, clock, () -> "SYNTH_QFND02_RECEIPT"));
        final Qfnd02ReceiptWriter writer =
                Qfnd02ReceiptWriter.forTarget(
                        temporaryDirectory.resolve("target"), clock, () -> "../escape");
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(acceptedResults(Qfnd02Registry.loadDefault())));
    }

    @Test
    void rejectsForgedOrDuplicateBindingsBeforeWriting() {
        final Qfnd02Registry registry = Qfnd02Registry.loadDefault();
        final List<Qfnd02Result> results = acceptedResults(registry);
        final Qfnd02Result first = results.get(0);
        final Qfnd02Result forged =
                new Qfnd02Result(
                        first.profileId(),
                        "0".repeat(64),
                        first.fixtureFingerprint(),
                        first.outcome(),
                        first.profileStatus(),
                        first.gateStatus(),
                        first.providerEvidence(),
                        false,
                        first.reasons());
        final Qfnd02Result forgedFixture =
                new Qfnd02Result(
                        first.profileId(),
                        first.profileFingerprint(),
                        "0".repeat(64),
                        first.outcome(),
                        first.profileStatus(),
                        first.gateStatus(),
                        first.providerEvidence(),
                        false,
                        first.reasons());
        final Qfnd02Result divergentWithValidBindings =
                new Qfnd02Result(
                        first.profileId(),
                        first.profileFingerprint(),
                        first.fixtureFingerprint(),
                        Qfnd02Vocabulary.Outcome.FAIL_CLOSED,
                        first.profileStatus(),
                        first.gateStatus(),
                        first.providerEvidence(),
                        false,
                        Set.of(Qfnd02Vocabulary.Reason.PATH_DRIFT));
        final Qfnd02ReceiptWriter writer =
                Qfnd02ReceiptWriter.forTarget(
                        temporaryDirectory.resolve("target"),
                        Clock.systemUTC(),
                        () -> "SYNTH_QFND02_FORGE");

        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(List.of(forged, results.get(1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(List.of(forgedFixture, results.get(1))));
        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(List.of(divergentWithValidBindings, results.get(1))));
        assertThrows(IllegalArgumentException.class, () -> writer.write(List.of(first, first)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        new Qfnd02Result(
                                "SYNTH_PAYLOAD_LIKE_IDENTIFIER",
                                "0".repeat(64),
                                "0".repeat(64),
                                first.outcome(),
                                first.profileStatus(),
                                first.gateStatus(),
                                first.providerEvidence(),
                                false,
                                first.reasons()));
    }

    @Test
    void snapshotsAnAdversarialListBeforeValidationAndSerialization() throws Exception {
        final List<Qfnd02Result> canonical = acceptedResults(Qfnd02Registry.loadDefault());
        final List<Qfnd02Result> forged =
                canonical.stream()
                        .map(
                                result ->
                                        new Qfnd02Result(
                                                result.profileId(),
                                                "0".repeat(64),
                                                result.fixtureFingerprint(),
                                                result.outcome(),
                                                result.profileStatus(),
                                                result.gateStatus(),
                                                result.providerEvidence(),
                                                result.providerPass(),
                                                result.reasons()))
                        .toList();
        final List<Qfnd02Result> switching =
                new AbstractList<>() {
                    private int traversals;

                    @Override
                    public Qfnd02Result get(final int index) {
                        if (index == 0) {
                            traversals++;
                        }
                        return (traversals == 1 ? canonical : forged).get(index);
                    }

                    @Override
                    public int size() {
                        return canonical.size();
                    }
                };
        final Qfnd02ReceiptWriter writer =
                Qfnd02ReceiptWriter.forTarget(
                        temporaryDirectory.resolve("target"),
                        Clock.fixed(Instant.parse("2030-01-02T03:04:05Z"), ZoneOffset.UTC),
                        () -> "SYNTH_QFND02_SNAPSHOT");

        final Path receipt = writer.write(switching);

        assertEquals("0".repeat(64), switching.get(0).profileFingerprint());
        assertFalse(Files.readString(receipt).contains("0".repeat(64)));
    }

    @Test
    void replacesHardLinkedDestinationWithoutMutatingExternalFile() throws Exception {
        final Path target = temporaryDirectory.resolve("hardlink/target");
        final Path destination =
                target.resolve(
                        "v2-012-characterization/q-fnd-02/SYNTH_QFND02_HARDLINK/receipt.json");
        final Path external = temporaryDirectory.resolve("external-sentinel.json");
        Files.createDirectories(destination.getParent());
        Files.writeString(external, "SYNTH_EXTERNAL_SENTINEL");
        try {
            Files.createLink(destination, external);
        } catch (final UnsupportedOperationException | java.io.IOException exception) {
            Assumptions.assumeTrue(false, "Filesystem sem hardlink para esta prova.");
        }
        final Qfnd02ReceiptWriter writer =
                Qfnd02ReceiptWriter.forTarget(
                        target, Clock.systemUTC(), () -> "SYNTH_QFND02_HARDLINK");

        final Path written = writer.write(acceptedResults(Qfnd02Registry.loadDefault()));

        assertEquals("SYNTH_EXTERNAL_SENTINEL", Files.readString(external));
        assertFalse(Files.isSameFile(external, written));
    }

    @Test
    void refusesSymbolicLinkInsideReceiptRoot() throws Exception {
        final Path target = temporaryDirectory.resolve("symlink/target");
        final Path link = target.resolve("v2-012-characterization");
        final Path outside = temporaryDirectory.resolve("outside");
        Files.createDirectories(target);
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(link, outside);
        } catch (final UnsupportedOperationException | java.io.IOException exception) {
            Assumptions.assumeTrue(false, "Filesystem sem symlink para esta prova.");
        }
        final Qfnd02ReceiptWriter writer =
                Qfnd02ReceiptWriter.forTarget(
                        target, Clock.systemUTC(), () -> "SYNTH_QFND02_SYMLINK");

        assertThrows(
                IllegalArgumentException.class,
                () -> writer.write(acceptedResults(Qfnd02Registry.loadDefault())));
        assertFalse(Files.exists(outside.resolve("q-fnd-02/SYNTH_QFND02_SYMLINK/receipt.json")));
    }

    private static List<Qfnd02Result> acceptedResults(final Qfnd02Registry registry) {
        return registry.profiles().stream()
                .map(
                        profile ->
                                registry.evaluator(profile.profileId())
                                        .evaluate(
                                                profile, registry.observation(profile.profileId())))
                .toList();
    }

    private static String sha256(final byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (final NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
