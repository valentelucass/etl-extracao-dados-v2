package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Explicit independent key assignments, applied before capture to input and authored expectations.
 */
final class QualificationArtifactIdentityFixtures {
    private QualificationArtifactIdentityFixtures() {}

    static QualificationArtifactScenarioFixtures.Paths write(final Path folder) throws Exception {
        final var result = QualificationArtifactScenarioFixtures.write(folder, true, 2);
        final Map<String, String> identities = new HashMap<>();
        for (final var family : new String[] {"CAP", "FAT", "INV", "SIN"}) {
            identities.put(family + "-root-1", "declared-alpha");
            identities.put(family + "-root-2", "declared-omega");
        }
        identities.put("part-1", "share-blue");
        identities.put("part-2", "share-green");
        identities.put("component-1", "line-cobalt");
        identities.put("component-2", "line-ochre");
        try (var files = Files.walk(folder)) {
            for (final var file : files.filter(Files::isRegularFile).toList()) {
                final var value = QualificationJson.read(file, 524288);
                replace(value, identities);
                Files.writeString(file, value.toPrettyString());
            }
        }
        repin(result.input());
        final var oracle = (ObjectNode) QualificationJson.read(result.oracle(), 65536);
        oracle.put("inputSha256", QualificationJson.sha256(result.input()));
        Files.writeString(result.oracle(), oracle.toPrettyString());
        repin(result.oracle());
        return result;
    }

    private static void replace(final JsonNode node, final Map<String, String> identities) {
        if (node.isObject()) {
            final var object = (ObjectNode) node;
            final var fields = new java.util.ArrayList<String>();
            object.fieldNames().forEachRemaining(fields::add);
            for (final var field : fields) {
                final var value = object.get(field);
                if (value.isTextual() && identities.containsKey(value.textValue())) {
                    object.set(field, TextNode.valueOf(identities.get(value.textValue())));
                } else {
                    replace(value, identities);
                }
            }
        } else if (node.isArray()) {
            for (final var item : node) {
                replace(item, identities);
            }
        }
    }

    private static void repin(final Path path) throws Exception {
        final var value = QualificationJson.read(path, 524288);
        repin(value, path.getParent());
        Files.writeString(path, value.toPrettyString());
    }

    private static void repin(final JsonNode value, final Path folder) throws Exception {
        if (value.isObject() && value.has("file") && value.has("sha256")) {
            final var file = folder.resolve(value.path("file").asText());
            repin(file);
            ((ObjectNode) value).put("sha256", QualificationJson.sha256(file));
        } else {
            for (final var child : value) {
                repin(child, folder);
            }
        }
    }
}
