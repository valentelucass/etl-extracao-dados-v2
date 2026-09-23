package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Build-time content deduplication; identical immutable files share one package member. */
public final class IntegralCampaignPackageFixtures {
    private IntegralCampaignPackageFixtures() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("CAMPAIGN_EXAMPLE_DIRECTORY");
        }
        final var folder = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(folder)) {
            throw new IllegalArgumentException("CAMPAIGN_EXAMPLES_ALREADY_EXIST");
        }
        final var index =
                IntegralArtifactFixtures.object()
                        .put("version", "integral-sequences-v1")
                        .put("runtimeSha256", LocalArtifactScenario.runtimeFingerprint());
        final var packaged =
                IntegralArtifactFixtures.object()
                        .put("version", "qualification-artifact-cases-v2")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1");
        final var cases = packaged.putArray("cases");
        final var entries = index.putArray("cases");
        int total = 0;
        for (final boolean alternate : new boolean[] {false, true}) {
            final String id = alternate ? "sequence-b" : "sequence-a";
            final var authored = folder.resolve("authored-" + id);
            final var sequence = IntegralCampaignFixtures.write(authored, alternate, 2);
            final var delivered = folder.resolve("artifact-cases").resolve(id);
            Files.createDirectories(delivered);
            final var graph = new Graph(authored, delivered);
            final var root = IntegralArtifactFixtures.read(sequence);
            graph.visit(sequence.getParent(), root, "FIXTURE");
            IntegralArtifactFixtures.save(delivered.resolve("sequence.json"), root);
            graph.roles.put("sequence.json", "FIXTURE");
            if (graph.roles.size() > 340) {
                throw new IllegalStateException("CAMPAIGN_CASE_FILE_BOUND");
            }
            total += graph.roles.size();
            final var members =
                    cases.addObject().put("id", id).put("kind", "SEQUENCE").putArray("members");
            final var files =
                    entries.addObject()
                            .put("id", id)
                            .put("roots", 2)
                            .put("stages", 7)
                            .putArray("members");
            for (final var entry : graph.roles.entrySet()) {
                final String name = "artifact-cases/" + id + "/" + entry.getKey();
                members.addObject().put("path", name).put("role", entry.getValue());
                files.addObject()
                        .put("file", name)
                        .put("sha256", QualificationJson.sha256(delivered.resolve(entry.getKey())));
            }
        }
        if (total > 680) {
            throw new IllegalStateException("CAMPAIGN_PACKAGE_FILE_BOUND");
        }
        IntegralArtifactFixtures.save(folder.resolve("artifact-cases/index.json"), packaged);
        IntegralArtifactFixtures.save(folder.resolve("index.json"), index);
    }

    private static final class Graph {
        private final Path root;
        private final Path delivered;
        private final Map<Path, ObjectNode> copied = new LinkedHashMap<>();
        private final Map<String, String> hashes = new LinkedHashMap<>();
        private final Map<String, String> roles = new LinkedHashMap<>();

        private Graph(final Path root, final Path delivered) {
            this.root = root;
            this.delivered = delivered;
        }

        private ObjectNode copy(final Path file, final String role) throws Exception {
            final var normalized = file.toAbsolutePath().normalize();
            if (!normalized.startsWith(root) || copied.size() > 2048) {
                throw new IllegalArgumentException("CAMPAIGN_AUTHOR_GRAPH_BOUND");
            }
            if (copied.containsKey(normalized)) {
                return copied.get(normalized).deepCopy();
            }
            final String original = QualificationJson.sha256(normalized);
            final var json = IntegralArtifactFixtures.read(normalized);
            visit(normalized.getParent(), json, role);
            final byte[] bytes =
                    new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(json);
            final String hash = QualificationJson.sha256(bytes);
            final String name = (role.equals("ORACLE") ? "o-" : "f-") + hash + ".json";
            if (!Files.exists(delivered.resolve(name))) {
                Files.write(delivered.resolve(name), bytes);
            }
            roles.merge(
                    name,
                    role,
                    (left, right) ->
                            left.equals("ORACLE") || right.equals("ORACLE") ? "ORACLE" : "FIXTURE");
            hashes.put(original, hash);
            final var descriptor =
                    IntegralArtifactFixtures.object().put("file", name).put("sha256", hash);
            copied.put(normalized, descriptor);
            return descriptor.deepCopy();
        }

        private void visit(final Path directory, final JsonNode node, final String role)
                throws Exception {
            if (node.isObject() && node.has("file") && node.has("sha256")) {
                final var referenced = directory.resolve(node.path("file").asText()).normalize();
                if (!referenced.startsWith(root)
                        || !QualificationJson.sha256(referenced)
                                .equals(node.path("sha256").asText())) {
                    throw new IllegalArgumentException("CAMPAIGN_AUTHOR_INPUT_CHANGED");
                }
                final var descriptor = copy(referenced, role);
                ((ObjectNode) node)
                        .put("file", descriptor.path("file").asText())
                        .put("sha256", descriptor.path("sha256").asText());
                return;
            }
            if (node.isObject()) {
                if (node.has("inputSha256")) {
                    final String replacement = hashes.get(node.path("inputSha256").asText());
                    if (replacement == null) {
                        throw new IllegalStateException("CAMPAIGN_ORACLE_INPUT_NOT_COPIED");
                    }
                    ((ObjectNode) node).put("inputSha256", replacement);
                }
                final var fields = node.fields();
                while (fields.hasNext()) {
                    final var entry = fields.next();
                    visit(
                            directory,
                            entry.getValue(),
                            entry.getKey().equals("oracle") ? "ORACLE" : role);
                }
            } else if (node.isArray()) {
                for (final var child : node) {
                    visit(directory, child, role);
                }
            }
        }
    }
}
