package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionLocalContract;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/**
 * Independent artifact author at the test edge. Keys are declared literals, never row positions.
 */
final class QualificationArtifactFixtures {
    private QualificationArtifactFixtures() {}

    static Path write(
            final Path directory,
            final DataExportTemplate template,
            final String family,
            final Consumer<ObjectNode> change)
            throws IOException {
        Files.createDirectories(directory);
        final var envelope = JsonNodeFactory.instance.objectNode();
        envelope.put("capture_occurrence", 73).put("provenance", "FIXTURE_SINTETICA_EXPLICITA");
        envelope.set("data", ExpansionLaboratoryFixtures.data(template));
        envelope.putObject("binding")
                .put("root", "independent-root-alpha")
                .put("part", "independent-part-beta")
                .put("component", "independent-component-gamma")
                .put("revision", 1)
                .put("evidence", "synthetic-independent-artifact-v1")
                .put("currency", "BRL")
                .put("unit", "MAJOR")
                .put("additive_allocation", template == DataExportTemplate.CONTAS_A_PAGAR)
                .put("active", true)
                .put("reactivation", false);
        change.accept(envelope);
        final byte[] page =
                JsonNodeFactory.instance
                        .arrayNode()
                        .add(envelope)
                        .toString()
                        .getBytes(StandardCharsets.UTF_8);
        final byte[] terminal = "[]".getBytes(StandardCharsets.UTF_8);
        Files.write(directory.resolve("page-one.json"), page);
        Files.write(directory.resolve("page-end.json"), terminal);
        final var manifest = JsonNodeFactory.instance.objectNode();
        manifest.put("version", 1)
                .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                .put("family", family)
                .put(
                        "contractSha256",
                        ExpansionLocalContract.release(template).contractFingerprint().sha256())
                .put("date", "2036-04-01")
                .put("source", "SYNTHETIC_EXPANSION_LAB")
                .put("tenant", "SYNTHETIC_TENANT")
                .put("pageSize", 2)
                .put("maximumPages", 10)
                .put("maximumRows", 100)
                .put("complete", true);
        final var pages = manifest.putArray("pages");
        pages.addObject()
                .put("file", "page-one.json")
                .put("sha256", QualificationJson.sha256(page));
        pages.addObject()
                .put("file", "page-end.json")
                .put("sha256", QualificationJson.sha256(terminal));
        final Path path = directory.resolve("capture.json");
        Files.writeString(path, manifest.toString());
        return path;
    }

    static void manifest(final Path path, final Consumer<ObjectNode> change) throws IOException {
        final var root = (ObjectNode) QualificationJson.read(path, 262144);
        change.accept(root);
        Files.writeString(path, root.toString());
    }
}
