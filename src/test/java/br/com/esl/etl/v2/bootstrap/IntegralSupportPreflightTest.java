package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import br.com.esl.etl.v2.plataforma.resiliencia.ResilienceCancelledException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class IntegralSupportPreflightTest {
    @TempDir Path folder;

    @ParameterizedTest
    @ValueSource(ints = {2, 8, 32, 128})
    @Timeout(180)
    void validatesGrowingCompleteSupplementSetsWithoutSql(final int pages) throws Exception {
        final var manifest = write(pages, false);
        final long started = System.nanoTime();
        final var support = load(manifest, CancellationToken.none());
        support.verifyFiles(CancellationToken.none());
        IntegralArtifactFixtures.save(
                Path.of("target", "states-support-preflight-" + pages + ".json"),
                IntegralArtifactFixtures.object()
                        .put("pages", pages)
                        .put("rows", pages * 64)
                        .put("elapsedMilliseconds", (System.nanoTime() - started) / 1_000_000)
                        .put("verifiedPasses", 2)
                        .put("sourceCalls", 0)
                        .put("sql", false)
                        .put("heapPlateauClaimed", false));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 128})
    @Timeout(180)
    void refusesDuplicateAtTheEndOfTheDeclaredSet(final int pages) throws Exception {
        final var manifest = write(pages, true);
        assertEquals(
                "INTEGRAL_SUPPORT_DUPLICATE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> load(manifest, CancellationToken.none()))
                        .getMessage());
    }

    @Test
    void hashCollisionDoesNotBecomeSourceIdentityOrFalseDuplicate() throws Exception {
        final var manifest = write(2, false);
        final var root = (ObjectNode) IntegralArtifactFixtures.read(manifest);
        final var pins = root.path("batches").path("manifestStates");
        assertEquals("STRING:Aa".hashCode(), "STRING:BB".hashCode());
        for (int page = 0; page < 2; page++) {
            final var file = folder.resolve("page-" + page + ".json");
            final var rows = (ArrayNode) IntegralArtifactFixtures.read(file);
            ((ObjectNode) rows.get(0)).put("sourceKey", page == 0 ? "STRING:Aa" : "STRING:BB");
            IntegralArtifactFixtures.save(file, rows);
            ((ObjectNode) pins.get(page)).put("sha256", QualificationJson.sha256(file));
        }
        IntegralArtifactFixtures.save(manifest, root);
        load(manifest, CancellationToken.none()).verifyFiles(CancellationToken.none());
    }

    @ParameterizedTest
    @CsvSource({"a,false", "b,false", "a,true", "b,true"})
    void fiscalTupleBoundariesRemainDistinct(final String tag, final boolean duplicate)
            throws Exception {
        final var manifest = write(2, false);
        final var root = (ObjectNode) IntegralArtifactFixtures.read(manifest);
        final var rows = IntegralArtifactFixtures.array();
        for (int index = 0; index < 2; index++) {
            final boolean first = index == 0 || duplicate;
            final var row = rows.addObject().put("revision", 3).put("nfseSeries", "SERIES-" + tag);
            row.putObject("root").put("type", "STRING").put("value", "invoice-" + tag);
            row.putObject("part")
                    .put("type", "STRING")
                    .put("value", first ? "part-" + tag + ":STRING:middle-" + tag : "part-" + tag);
            row.putObject("component")
                    .put("type", "STRING")
                    .put("value", first ? "tail-" + tag : "middle-" + tag + ":STRING:tail-" + tag);
        }
        final var fiscal = folder.resolve("fiscal-tuples.json");
        IntegralArtifactFixtures.save(fiscal, rows);
        ((ObjectNode) root.path("batches"))
                .putArray("fiscal")
                .add(IntegralArtifactFixtures.pin(folder, fiscal));
        IntegralArtifactFixtures.save(manifest, root);
        if (duplicate) {
            assertEquals(
                    "INTEGRAL_SUPPORT_DUPLICATE",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> load(manifest, CancellationToken.none()))
                            .getMessage());
        } else {
            load(manifest, CancellationToken.none()).verifyFiles(CancellationToken.none());
        }
    }

    @Test
    void cancellationInterruptsValidationInsideAFullBatch() throws Exception {
        final var manifest = write(2, false);
        final var checks = new AtomicInteger();
        assertThrows(
                ResilienceCancelledException.class,
                () -> load(manifest, () -> checks.incrementAndGet() >= 32));
        assertEquals(32, checks.get());
    }

    @Test
    void revalidationRefusesChangedPinnedBatch() throws Exception {
        final var manifest = write(2, false);
        final var support = load(manifest, CancellationToken.none());
        final var file = folder.resolve("page-0.json");
        final var rows = (ArrayNode) IntegralArtifactFixtures.read(file);
        rows.remove(0);
        IntegralArtifactFixtures.save(file, rows);
        assertEquals(
                "LOCAL_PIN_BYTES",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> support.verifyFiles(CancellationToken.none()))
                        .getMessage());
    }

    private DeclaredAnalyticSupport load(final Path manifest, final CancellationToken token)
            throws Exception {
        return new DeclaredAnalyticSupport(
                PinnedLocalJson.open(manifest, 131072),
                LocalDate.of(2025, 2, 1),
                LocalDate.of(2025, 2, 3),
                3,
                token);
    }

    private Path write(final int pages, final boolean duplicate) throws Exception {
        final var root =
                IntegralArtifactFixtures.object()
                        .put("version", "local-analytic-support-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("complete", true);
        final var batches = root.putObject("batches");
        final var empty = folder.resolve("empty.json");
        IntegralArtifactFixtures.save(empty, IntegralArtifactFixtures.array());
        for (final var kind :
                new String[] {
                    "financial",
                    "dimensions",
                    "relational",
                    "freight",
                    "collections",
                    "manifestStates",
                    "freightRelations",
                    "compositions",
                    "fiscal"
                }) {
            final var files = batches.putArray(kind);
            if (!kind.equals("manifestStates")) {
                files.add(IntegralArtifactFixtures.pin(folder, empty));
                continue;
            }
            for (int page = 0; page < pages; page++) {
                final var rows = IntegralArtifactFixtures.array();
                for (int row = 0; row < 64; row++) {
                    final int key =
                            duplicate && page == pages - 1 && row == 63 ? 0 : page * 64 + row;
                    rows.addObject()
                            .put("sourceKey", "INTEGER:" + (710000 + key * 17))
                            .put("revision", 3)
                            .put("active", true)
                            .put("reactivate", false);
                }
                final var file = folder.resolve("page-" + page + ".json");
                IntegralArtifactFixtures.save(file, rows);
                files.add(IntegralArtifactFixtures.pin(folder, file));
            }
        }
        final var manifest = folder.resolve("support.json");
        IntegralArtifactFixtures.save(manifest, root);
        assertEquals(64, QualificationJson.read(folder.resolve("page-0.json"), 131072).size());
        return manifest;
    }
}
