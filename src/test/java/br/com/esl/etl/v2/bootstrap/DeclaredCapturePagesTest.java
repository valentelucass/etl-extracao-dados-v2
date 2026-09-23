package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DeclaredCapturePagesTest {
    @TempDir Path folder;

    @Test
    void freightSharesTheSameDeclaredReleaseWithBothRealConsumers() throws Exception {
        final var files = write("FRE");
        final var input = load(files);
        final var relational =
                input.relational(AnalyticScenarioObserver.NONE)
                        .contractRelease(DataExportTemplate.FRETES);
        final var dependency =
                input.dependency(AnalyticScenarioObserver.NONE)
                        .contractRelease(DataExportTemplate.FRETES);
        assertEquals(relational, dependency);
        assertTrue(relational.response().find("/total").isPresent());
        assertTrue(relational.response().find("/fit_p_m_pck_sequence_code").isPresent());
        assertTrue(relational.response().find("/fit_dpn_performance_finished_at").isPresent());
        assertEquals(LocalDate.of(2037, 8, 11), input.date());
        assertEquals(7, input.revision());
    }

    @ParameterizedTest
    @ValueSource(strings = {"COL", "MAN", "COT", "LOC", "USER"})
    void acceptsExplicitPagesAndScopeForEachRemainingFamily(final String family) throws Exception {
        final var input = load(write(family));
        assertEquals(family, input.family());
        assertEquals("SYNTHETIC_CARRIER_B", input.source());
        assertEquals("SYNTHETIC_TENANT_WEST", input.tenant());
        input.verifyFiles(CancellationToken.none());
    }

    @Test
    void collectionPaginationCountsDistinctEntitiesRatherThanExpandedPhysicalRows()
            throws Exception {
        final var files = write("COL");
        final var expanded =
                "[{\"id\":917,\"synthetic_fixture\":true},"
                        + "{\"id\":917,\"synthetic_fixture\":true},"
                        + "{\"id\":917,\"synthetic_fixture\":true}]";
        final var manifest = (ObjectNode) QualificationJson.read(files, 65536);
        Files.writeString(folder.resolve("page.json"), expanded);
        ((ObjectNode) manifest.path("pages").get(0))
                .put("sha256", QualificationJson.sha256(folder.resolve("page.json")));
        manifest.put("expectedRows", 3).put("pageSize", 1);
        Files.writeString(files, manifest.toPrettyString());
        load(files).verifyFiles(CancellationToken.none());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "extra",
                "missing",
                "complete",
                "family",
                "contractSha256",
                "source",
                "tenant",
                "revision",
                "pageSize",
                "maximumRows",
                "expectedRows",
                "path"
            })
    void refusesInvalidDeclarationsBeforeAnySessionExists(final String mutation) throws Exception {
        final var files = write("COL");
        final var manifest = (ObjectNode) QualificationJson.read(files, 65536);
        switch (mutation) {
            case "extra" -> manifest.put("fallback", "PACKAGED_ANALYTIC_SUPPORT_V1");
            case "missing" -> manifest.remove("source");
            case "complete" -> manifest.put("complete", false);
            case "family" -> manifest.put("family", "UNKNOWN");
            case "contractSha256" -> manifest.put("contractSha256", "f".repeat(64));
            case "source", "tenant" -> manifest.put(mutation, "GLOBAL");
            case "revision" -> manifest.put("revision", "7");
            case "pageSize" -> manifest.put("pageSize", 17);
            case "maximumRows" -> manifest.put("maximumRows", 10001);
            case "expectedRows" -> manifest.put("expectedRows", 2);
            case "path" ->
                    ((ObjectNode) manifest.path("pages").get(0)).put("file", "../escape.json");
            default -> throw new IllegalArgumentException();
        }
        Files.writeString(files, manifest.toPrettyString());
        assertThrows(IllegalArgumentException.class, () -> load(files));
    }

    @Test
    void refusesChangedBytesAfterPreflightEvenWhenTheShapeRemainsValid() throws Exception {
        final var files = write("FRE");
        final var input = load(files);
        final var before = QualificationJson.sha256(folder.resolve("page.json"));
        Files.writeString(folder.resolve("page.json"), "[{\"id\":619,\"synthetic_fixture\":true}]");
        assertNotEquals(before, QualificationJson.sha256(folder.resolve("page.json")));
        assertEquals(
                "LOCAL_PIN_BYTES",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> input.verifyFiles(CancellationToken.none()))
                        .getMessage());
    }

    @Test
    void refusesMissingTerminalWithRecomputedManifestPins() throws Exception {
        final var files = write("MAN");
        final var manifest = (ObjectNode) QualificationJson.read(files, 65536);
        ((com.fasterxml.jackson.databind.node.ArrayNode) manifest.path("pages")).remove(1);
        Files.writeString(files, manifest.toPrettyString());
        assertEquals(
                "INTEGRAL_CAPTURE_TERMINAL",
                assertThrows(IllegalArgumentException.class, () -> load(files)).getMessage());
    }

    private DeclaredCapturePages load(final Path file) throws Exception {
        return new DeclaredCapturePages(
                PinnedLocalJson.open(file, 65536), CancellationToken.none());
    }

    private Path write(final String family) throws Exception {
        final var manifest =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "local-capture-pages-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("family", family)
                        .put("source", "SYNTHETIC_CARRIER_B")
                        .put("tenant", "SYNTHETIC_TENANT_WEST")
                        .put("date", "2037-08-11")
                        .put("revision", 7)
                        .put(
                                "contractSha256",
                                DeclaredCapturePages.release(family).contractFingerprint().sha256())
                        .put("pageSize", family.equals("USER") ? 20 : 2)
                        .put("maximumPages", 4)
                        .put("maximumRows", 100)
                        .put("expectedRows", 1)
                        .put("complete", true);
        final String key =
                switch (family) {
                    case "COL", "FRE" -> "id";
                    case "LOC" -> "corporation_sequence_number";
                    default -> "sequence_code";
                };
        final String page =
                family.equals("USER")
                        ? "{\"data\":{\"individual\":{\"edges\":[{\"node\":{\"id\":\"synthetic-user-917\",\"name\":null}}],"
                                + "\"pageInfo\":{\"hasNextPage\":false,\"endCursor\":null}}}}"
                        : "[{\"" + key + "\":917,\"synthetic_fixture\":true}]";
        Files.writeString(folder.resolve("page.json"), page);
        final var pages = manifest.putArray("pages");
        pages.addObject()
                .put("file", "page.json")
                .put("sha256", QualificationJson.sha256(folder.resolve("page.json")));
        if (!family.equals("USER")) {
            Files.writeString(folder.resolve("terminal.json"), "[]");
            pages.addObject()
                    .put("file", "terminal.json")
                    .put("sha256", QualificationJson.sha256(folder.resolve("terminal.json")));
        }
        final var file = folder.resolve("capture.json");
        Files.writeString(file, manifest.toPrettyString());
        return file;
    }
}
