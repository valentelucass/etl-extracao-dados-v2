package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

class QualificationFactOracleEdgesIT {
    @TempDir Path folder;

    @Test
    @Timeout(240)
    void fiveDeclaredGrainsRejectMissingExtraDuplicateAndWrongValueWithoutLearningFromActual()
            throws Exception {
        final var files = QualificationArtifactScenarioFixtures.write(folder, false, 2);
        final var scenario =
                new LocalArtifactScenario(files.input(), files.oracle(), CancellationToken.none());
        final UUID run = UUID.randomUUID();
        try (var session = ColetaTemporalLaboratorySession.openFromEnvironment()) {
            session.controlStatements(30, 100000);
            assertEquals(
                    19,
                    scenario.execute(
                                    session,
                                    CancellationToken.none(),
                                    AnalyticScenarioObserver.NONE,
                                    run,
                                    () -> {})
                            .outputs()
                            .size());
            final UUID expansion;
            try (var connection = session.getConnection();
                    var query =
                            connection.prepareStatement(
                                    "SELECT expansion_run FROM ctl.analytic_lab_source_group WHERE run_id=?")) {
                query.setString(1, run.toString());
                query.setQueryTimeout(10);
                try (var row = query.executeQuery()) {
                    assertTrue(row.next());
                    expansion = UUID.fromString(row.getString(1));
                }
            }
            final Path manifest = folder.resolve("facts/manifest.json");
            final String originalManifest = Files.readString(manifest);
            final var root = (ObjectNode) QualificationJson.read(manifest, 16384);
            for (final var fact : root.path("facts")) {
                final String id = fact.path("id").asText();
                final Path path = folder.resolve("facts").resolve(fact.path("file").asText());
                final String original = Files.readString(path);
                for (final String mutation :
                        new String[] {"missing", "extra", "duplicate", "value"}) {
                    final var rows =
                            (ArrayNode)
                                    QualificationJson.parse(
                                            original.getBytes(
                                                    java.nio.charset.StandardCharsets.UTF_8),
                                            262144);
                    final var first = (ObjectNode) rows.get(0);
                    switch (mutation) {
                        case "missing" -> rows.remove(rows.size() - 1);
                        case "duplicate" -> rows.add(first.deepCopy());
                        case "extra" -> {
                            final var extra = first.deepCopy();
                            final String key = id.equals("MAT02") ? "branch" : "sourceKey";
                            extra.put(key, extra.path(key).asText() + "-synthetic-extra");
                            rows.add(extra);
                        }
                        case "value" -> {
                            if (id.equals("MAT02")) {
                                first.put("issued", first.path("issued").asLong() + 1);
                            } else {
                                first.put("amount", "999.12500000");
                            }
                        }
                        default -> throw new IllegalArgumentException();
                    }
                    Files.writeString(path, rows.toString());
                    ((ObjectNode) fact).put("sha256", QualificationJson.sha256(path));
                    Files.writeString(manifest, root.toString());
                    final var oracle = new LocalFactOracle(manifest);
                    final var failure =
                            assertThrows(
                                    SQLException.class,
                                    () ->
                                            oracle.verify(
                                                    session,
                                                    run,
                                                    expansion,
                                                    CancellationToken.none()),
                                    id + "/" + mutation);
                    assertEquals("LOCAL_FACT_ORACLE_DIVERGENCE_" + id, failure.getMessage());
                    Files.writeString(path, original);
                    ((ObjectNode) fact).put("sha256", QualificationJson.sha256(path));
                }
            }
            Files.writeString(manifest, originalManifest);
            new LocalFactOracle(manifest).verify(session, run, expansion, CancellationToken.none());
        }
    }
}
