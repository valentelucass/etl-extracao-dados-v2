package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class QualificationPhysicalMetadataTest {
    private static final Path RESOURCES = Path.of("src/main/resources/qualification-laboratory");

    @Test
    void physicalSchemaContractRejectsCoercedTypesAndAnUnapprovedOriginBeforeJdbc()
            throws Exception {
        final var source =
                QualificationJson.read(
                        Path.of(
                                "src/main/resources/qualification-laboratory/physical-columns.v098.json"),
                        524288);
        for (final var field : List.of("nullable", "bytes", "precision", "scale", "ordinal")) {
            final var changed = source.deepCopy();
            ((ObjectNode) changed.path("columns").get(0)).put(field, "false");
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new QualificationPhysicalMetadata(changed));
        }
        final var changed = (ObjectNode) source.deepCopy();
        changed.put("sourceSha256", "0".repeat(64));
        assertThrows(
                IllegalArgumentException.class, () -> new QualificationPhysicalMetadata(changed));
    }

    @Test
    void localV105IsAnOrderedExactDeltaFromPinnedV098() throws Exception {
        final var historical =
                QualificationJson.read(RESOURCES.resolve("physical-columns.v098.json"), 524288);
        final var candidate =
                QualificationJson.read(RESOURCES.resolve("physical-columns.v105.json"), 524288);
        assertEquals(
                "16ef4e66339805b2b164a7a802fcf6862a0af528bbb84aa5e6e3b03cfbfc72ff",
                QualificationJson.sha256(RESOURCES.resolve("physical-columns.v098.json")));
        assertEquals(971, historical.path("columns").size());
        assertEquals(971, candidate.path("columns").size());
        final Set<String> wider =
                Set.of(
                        "analytic_lab_sql_01:17",
                        "analytic_lab_sql_13:5",
                        "analytic_lab_sql_13:9",
                        "analytic_lab_sql_13:14",
                        "analytic_lab_sql_13:18");
        final var observedWider = new HashSet<String>();
        int changedCollations = 0;
        String previous = "";
        for (int index = 0; index < 971; index++) {
            final JsonNode before = historical.path("columns").get(index);
            final JsonNode after = candidate.path("columns").get(index);
            final String key =
                    after.path("localName").asText() + ":" + after.path("ordinal").asInt();
            final String orderedKey =
                    String.format(
                            java.util.Locale.ROOT,
                            "%s:%03d",
                            after.path("localName").asText(),
                            after.path("ordinal").asInt());
            org.junit.jupiter.api.Assertions.assertTrue(orderedKey.compareTo(previous) > 0);
            previous = orderedKey;
            final var expected = (ObjectNode) before.deepCopy();
            if ("Latin1_General_CI_AS".equals(before.path("collation").textValue())) {
                expected.put("collation", "Latin1_General_100_CI_AS_SC");
                changedCollations++;
            }
            if (wider.contains(key)) {
                assertEquals(
                        key.equals("analytic_lab_sql_01:17") ? 28 : 2048,
                        before.path("bytes").asInt());
                expected.put("bytes", key.equals("analytic_lab_sql_01:17") ? 56 : 4096);
                observedWider.add(key);
            }
            assertEquals(expected, after, key);
        }
        assertEquals(181, changedCollations);
        assertEquals(wider, observedWider);
        new QualificationPhysicalMetadata(105);
        new QualificationPhysicalMetadata();
        assertEquals(
                "QUAL_PHYSICAL_SCHEMA_EPOCH",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> new QualificationPhysicalMetadata(104))
                        .getMessage());
    }

    @Test
    void localV105RequiresItsExactResourceAndEvidencePins() throws Exception {
        final byte[] bytes = Files.readAllBytes(RESOURCES.resolve("physical-columns.v105.json"));
        assertEquals(
                105,
                QualificationPhysicalMetadata.readResource(105, new ByteArrayInputStream(bytes))
                        .path("schemaVersion")
                        .asInt());
        assertEquals(
                "QUAL_PHYSICAL_SCHEMA_RESOURCE",
                assertThrows(
                                IllegalArgumentException.class,
                                () -> QualificationPhysicalMetadata.readResource(105, null))
                        .getMessage());
        final byte[] changed = bytes.clone();
        changed[changed.length - 2] = (byte) ' ';
        assertEquals(
                "QUAL_PHYSICAL_SCHEMA_HASH",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        QualificationPhysicalMetadata.readResource(
                                                105, new ByteArrayInputStream(changed)))
                        .getMessage());
        final var candidate =
                (ObjectNode)
                        QualificationJson.read(
                                RESOURCES.resolve("physical-columns.v105.json"), 524288);
        for (final String field :
                List.of(
                        "origin",
                        "baseSha256",
                        "catalogSha256",
                        "partialDmvSha256",
                        "dmvSha256",
                        "checkpointSha256")) {
            final var mutant = candidate.deepCopy();
            mutant.put(field, "unverified");
            assertEquals(
                    "QUAL_PHYSICAL_SCHEMA_ORIGIN",
                    assertThrows(
                                    IllegalArgumentException.class,
                                    () -> new QualificationPhysicalMetadata(105, mutant))
                            .getMessage());
        }
        final var epoch = candidate.deepCopy();
        epoch.put("schemaVersion", 98);
        assertThrows(
                IllegalArgumentException.class,
                () -> new QualificationPhysicalMetadata(105, epoch));
    }

    @Test
    void changedCollationAndLengthIndependentlyCausePhysicalDrift() throws Exception {
        final var candidate =
                QualificationJson.read(RESOURCES.resolve("physical-columns.v105.json"), 524288);
        final JsonNode expected = candidate.path("columns").get(16);
        verify(expected, expected.path("bytes").asInt(), expected.path("collation").textValue());
        for (final String collation : List.of("Latin1_General_CI_AS", "Latin1_General_100_BIN2")) {
            assertEquals(
                    "QUAL_PHYSICAL_SCHEMA_DRIFT",
                    assertThrows(
                                    SQLException.class,
                                    () ->
                                            verify(
                                                    expected,
                                                    expected.path("bytes").asInt(),
                                                    collation))
                            .getMessage());
        }
        assertEquals(
                "QUAL_PHYSICAL_SCHEMA_DRIFT",
                assertThrows(
                                SQLException.class,
                                () -> verify(expected, 28, expected.path("collation").textValue()))
                        .getMessage());
        final JsonNode destination = find(candidate, "analytic_lab_sql_13", 5);
        verify(destination, 4096, destination.path("collation").textValue());
        assertEquals(
                "QUAL_PHYSICAL_SCHEMA_DRIFT",
                assertThrows(
                                SQLException.class,
                                () ->
                                        verify(
                                                destination,
                                                2048,
                                                destination.path("collation").textValue()))
                        .getMessage());
    }

    private static JsonNode find(final JsonNode document, final String name, final int ordinal) {
        for (final var column : document.path("columns")) {
            if (name.equals(column.path("localName").asText())
                    && ordinal == column.path("ordinal").asInt()) {
                return column;
            }
        }
        throw new IllegalArgumentException("missing candidate column");
    }

    private static void verify(final JsonNode expected, final int bytes, final String collation)
            throws SQLException {
        QualificationPhysicalMetadata.verifyColumn(
                expected,
                expected.path("localName").asText(),
                expected.path("ordinal").asInt(),
                expected.path("columnName").asText(),
                expected.path("sqlType").asText(),
                bytes,
                expected.path("precision").asInt(),
                expected.path("scale").asInt(),
                expected.path("nullable").asBoolean(),
                collation);
    }
}
