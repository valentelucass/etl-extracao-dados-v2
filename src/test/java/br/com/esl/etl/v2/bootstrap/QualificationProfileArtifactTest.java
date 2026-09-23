package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import br.com.esl.etl.v2.plataforma.resiliencia.RuntimeExitCategory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

class QualificationProfileArtifactTest {
    @TempDir Path folder;

    @ParameterizedTest
    @EnumSource(LocalCharacterizationProfile.class)
    void twoIndependentIdentitiesReachCurrentMapperAndDeterministicPackagedConsumer(
            final LocalCharacterizationProfile profile) throws Exception {
        for (final int identity : new int[] {73, 951}) {
            final var path =
                    QualificationProfileArtifactFixtures.write(
                            folder.resolve("case-" + identity), profile, identity);
            final var result = new LocalProfileArtifact(path).inspect(CancellationToken.none());
            assertTrue(result.structurallyValid(), result.toString());
            assertEquals(1, result.rows());
            final var first = new ByteArrayOutputStream();
            final var second = new ByteArrayOutputStream();
            final String[] args = {"local-profile", "characterize", "--artifact", path.toString()};
            assertEquals(0, Main.run(args, new PrintStream(first), new PrintStream(first)));
            assertEquals(0, Main.run(args, new PrintStream(second), new PrintStream(second)));
            assertEquals(first.toString(), second.toString());
            assertTrue(first.toString().contains("provider=UNVERIFIED nominal_parity=BLOCKED"));
            assertFalse(first.toString().contains("Synthetic profile"));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "profileSha256",
                "revision",
                "source",
                "tenant",
                "zone",
                "expectedRows",
                "maximumRows"
            })
    void wrongContractScopeCardinalityAndCapsCannotPass(final String field) throws Exception {
        final var path = input();
        edit(
                path,
                root -> {
                    if (field.equals("expectedRows")) {
                        root.put(field, 2);
                    } else if (field.equals("maximumRows")) {
                        root.put(field, 1001);
                    } else {
                        root.put(field, "0".repeat(64));
                    }
                });
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalProfileArtifact(path).inspect(CancellationToken.none()));
    }

    @Test
    void absentNullWrongTypeUnknownFreshnessAndCompletenessRemainDistinct() throws Exception {
        final var profile = LocalCharacterizationProfile.COL;
        final var row = QualificationProfileArtifactFixtures.row(profile, 73);
        row.remove("sequence_code");
        assertFalse(profile.inspect(row.toString()).valid());
        row.putNull("sequence_code");
        assertFalse(profile.inspect(row.toString()).valid());
        row.put("sequence_code", "73");
        assertFalse(profile.inspect(row.toString()).valid());
        row.put("sequence_code", 73).put("request_date", "invalid-temporal");
        assertTrue(profile.inspect(row.toString()).valid());
        assertFalse(profile.inspect(row.toString()).freshnessAvailable());
        final var path = input();
        edit(path, root -> root.put("complete", false));
        assertFalse(
                new LocalProfileArtifact(path)
                        .inspect(CancellationToken.none())
                        .structurallyValid());
    }

    @Test
    void identityBindingTruncatedPageAndLateMutationAreRejected() throws Exception {
        final var path = input();
        final var artifact = new LocalProfileArtifact(path);
        final var records = QualificationJson.read(folder.resolve("rows.json"), 65536);
        ((ObjectNode) records.get(0)).put("sourceKey", "73");
        Files.writeString(folder.resolve("rows.json"), records.toString());
        edit(
                path,
                root ->
                        ((ObjectNode) root.path("pages").get(0))
                                .put("sha256", hash(folder.resolve("rows.json"))));
        assertThrows(
                IllegalArgumentException.class, () -> artifact.inspect(CancellationToken.none()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LocalProfileArtifact(path).inspect(CancellationToken.none()));
        Files.writeString(folder.resolve("rows.json"), "[");
        final var output = new ByteArrayOutputStream();
        assertEquals(
                RuntimeExitCategory.CONFIG_AUTH,
                LocalProfileCharacterizationMain.run(
                        new String[] {"characterize", "--artifact", path.toString()},
                        new PrintStream(output)));
        assertTrue(output.size() < 100);
    }

    @Test
    void cancellationAndLocalNumericWireGrammarUseTheDeliveredBoundary() throws Exception {
        final var artifact = new LocalProfileArtifact(input());
        assertThrows(ResilienceCancelledException.class, () -> artifact.inspect(() -> true));
        assertFalse(
                LocalCharacterizationProfile.LOC
                        .inspect("{\"corporation_sequence_number\":73,\"total\":1e2}")
                        .valid());
        assertFalse(
                LocalCharacterizationProfile.USER.inspect("{\"id\":73,\"name\":false}").valid());
    }

    private Path input() throws Exception {
        return QualificationProfileArtifactFixtures.write(
                folder, LocalCharacterizationProfile.COL, 73);
    }

    @ParameterizedTest
    @ValueSource(strings = {"bytes", "paths", "nodes"})
    void severalIndividuallySmallPagesCannotExceedTheOriginalAggregateLimit(final String limit)
            throws Exception {
        final var path = input();
        final var row =
                QualificationProfileArtifactFixtures.row(LocalCharacterizationProfile.COL, 73);
        final int repetitions;
        if (limit.equals("bytes")) {
            row.put("synthetic_padding", "x".repeat(23000));
            repetitions = 3;
        } else if (limit.equals("nodes")) {
            final var padding = row.putArray("synthetic_padding");
            for (int index = 0; index < 1500; index++) {
                padding.add(index);
            }
            repetitions = 3;
        } else {
            repetitions = 52;
        }
        final var records =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        records.addObject().put("sourceKey", 73).put("raw", row.toString());
        Files.writeString(folder.resolve("rows.json"), records.toString());
        edit(
                path,
                root -> {
                    root.put("expectedRows", repetitions);
                    final var pages = root.putArray("pages");
                    for (int index = 0; index < repetitions; index++) {
                        pages.addObject()
                                .put("file", "rows.json")
                                .put("sha256", hash(folder.resolve("rows.json")));
                    }
                    pages.addObject()
                            .put("file", "end.json")
                            .put("sha256", hash(folder.resolve("end.json")));
                });
        final var failure =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new LocalProfileArtifact(path).inspect(CancellationToken.none()));
        assertEquals(
                limit.equals("bytes") ? "LOCAL_PROFILE_BYTES_BOUND" : "LOCAL_PROFILE_SHAPE_BOUND",
                failure.getMessage());
    }

    private static void edit(final Path path, final Consumer<ObjectNode> change) throws Exception {
        final var root = (ObjectNode) QualificationJson.read(path, 65536);
        change.accept(root);
        Files.writeString(path, root.toString());
    }

    private static String hash(final Path file) {
        try {
            return QualificationJson.sha256(file);
        } catch (final java.io.IOException failure) {
            throw new java.io.UncheckedIOException(failure);
        }
    }
}
