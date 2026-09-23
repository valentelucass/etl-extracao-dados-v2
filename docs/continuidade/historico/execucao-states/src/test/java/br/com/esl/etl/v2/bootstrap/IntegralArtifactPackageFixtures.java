package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

/** Build-time authors only. The delivered JAR consumes the copied, transitively pinned files. */
public final class IntegralArtifactPackageFixtures {
    private IntegralArtifactPackageFixtures() {}

    public static void main(final String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("INTEGRAL_EXAMPLE_DIRECTORY");
        }
        final Path folder = Path.of(args[0]).toAbsolutePath().normalize();
        if (Files.exists(folder)) {
            throw new IllegalArgumentException("INTEGRAL_EXAMPLES_ALREADY_EXIST");
        }
        final var index =
                IntegralArtifactFixtures.object()
                        .put("version", "integral-examples-v1")
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1")
                        .put("runtimeSha256", LocalArtifactScenario.runtimeFingerprint());
        final var cases = index.putArray("cases");
        final var packaged =
                IntegralArtifactFixtures.object()
                        .put("version", "qualification-artifact-cases-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1");
        final var packageMembers =
                packaged.putArray("cases")
                        .addObject()
                        .put("id", "integral-small")
                        .putArray("members");
        for (final boolean alternate : new boolean[] {false, true}) {
            final String id = alternate ? "set-b" : "set-a";
            final Path authored = folder.resolve("authoring-" + id);
            IntegralArtifactFixtures.write(authored, alternate, alternate ? 24 : 2);
            final Path delivered = folder.resolve(id);
            final var reachable = new HashSet<Path>();
            collect(authored, authored.resolve("input.json"), reachable);
            collect(authored, authored.resolve("oracle.json"), reachable);
            final var entry = cases.addObject().put("id", id).put("roots", alternate ? 24 : 2);
            final var members = entry.putArray("members");
            for (final var file : reachable.stream().sorted().toList()) {
                final Path relative = authored.relativize(file);
                final Path target = delivered.resolve(relative);
                Files.createDirectories(target.getParent());
                Files.copy(file, target);
                members.addObject()
                        .put("file", relative.toString().replace('\\', '/'))
                        .put("sha256", QualificationJson.sha256(target));
                if (!alternate) {
                    final Path packagePath =
                            folder.resolve("artifact-cases/integral-small").resolve(relative);
                    Files.createDirectories(packagePath.getParent());
                    Files.copy(target, packagePath);
                    final String name = relative.toString().replace('\\', '/');
                    packageMembers
                            .addObject()
                            .put("path", "artifact-cases/integral-small/" + name)
                            .put(
                                    "role",
                                    name.equals("oracle.json")
                                                    || name.startsWith("wire/")
                                                    || name.startsWith("facts/")
                                                    || name.startsWith("outputs/")
                                            ? "ORACLE"
                                            : "FIXTURE");
                }
            }
        }
        IntegralArtifactFixtures.save(folder.resolve("index.json"), index);
        IntegralArtifactFixtures.save(folder.resolve("artifact-cases/index.json"), packaged);
    }

    static void recordInputs(final Path folder) throws Exception {
        final Path root = folder.toAbsolutePath().normalize();
        final var reachable = new HashSet<Path>();
        collect(root, root.resolve("input.json"), reachable);
        collect(root, root.resolve("oracle.json"), reachable);
        final var receipt =
                IntegralArtifactFixtures.object()
                        .put("origin", "INDEPENDENT_SYNTHETIC_RULES_V1")
                        .put("stage", "AUTHORED_BEFORE_SQL_BEFORE_TEST_MUTATIONS")
                        .put("runtimeSha256", LocalArtifactScenario.runtimeFingerprint())
                        .put("inputSha256", QualificationJson.sha256(root.resolve("input.json")))
                        .put("oracleSha256", QualificationJson.sha256(root.resolve("oracle.json")))
                        .put("sourceCalls", 0);
        final var members = receipt.putArray("members");
        for (final var file : reachable.stream().sorted().toList()) {
            members.addObject()
                    .put("file", root.relativize(file).toString().replace('\\', '/'))
                    .put("sha256", QualificationJson.sha256(file));
        }
        IntegralArtifactFixtures.save(
                Path.of("target", "integral-authorship", java.util.UUID.randomUUID() + ".json"),
                receipt);
    }

    private static void collect(final Path root, final Path file, final Set<Path> reachable)
            throws Exception {
        final Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || reachable.size() >= 2048) {
            throw new IllegalArgumentException("INTEGRAL_EXAMPLE_GRAPH_BOUND");
        }
        if (!reachable.add(normalized)) {
            return;
        }
        visit(root, normalized.getParent(), QualificationJson.read(normalized, 524288), reachable);
    }

    private static void visit(
            final Path root, final Path directory, final JsonNode node, final Set<Path> reachable)
            throws Exception {
        if (node.isObject() && node.has("file") && node.has("sha256")) {
            final Path referenced = directory.resolve(node.path("file").asText()).normalize();
            if (!referenced.startsWith(root)
                    || !QualificationJson.sha256(referenced).equals(node.path("sha256").asText())) {
                throw new IllegalArgumentException("INTEGRAL_EXAMPLE_PIN");
            }
            collect(root, referenced, reachable);
        }
        if (node.isContainerNode()) {
            for (final var child : node) {
                visit(root, directory, child, reachable);
            }
        }
    }
}
