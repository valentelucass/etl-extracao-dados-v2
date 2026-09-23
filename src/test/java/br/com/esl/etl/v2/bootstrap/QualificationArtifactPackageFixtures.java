package br.com.esl.etl.v2.bootstrap;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/** Authors two independent file bundles; delivered consumers contain no test class dependency. */
public final class QualificationArtifactPackageFixtures {
    private QualificationArtifactPackageFixtures() {}

    public static void main(final String[] args) throws Exception {
        final var folder = Path.of(args[0]);
        final var artifactRoot = folder.resolve("artifact-cases");
        QualificationArtifactIdentityFixtures.write(artifactRoot.resolve("artifact-small"));
        QualificationArtifactScenarioFixtures.write(
                artifactRoot.resolve("artifact-large"), false, 8);
        final var index =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "qualification-artifact-cases-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1");
        final var cases = index.putArray("cases");
        for (final var id : new String[] {"artifact-small", "artifact-large"}) {
            final var item = cases.addObject().put("id", id);
            final var members = item.putArray("members");
            try (var files = Files.walk(artifactRoot.resolve(id))) {
                for (final var file : files.filter(Files::isRegularFile).sorted().toList()) {
                    final var path = folder.relativize(file).toString().replace('\\', '/');
                    final var local =
                            artifactRoot.resolve(id).relativize(file).toString().replace('\\', '/');
                    final var role =
                            local.startsWith("wire/")
                                            || local.startsWith("facts/")
                                            || local.equals("oracle.json")
                                            || local.equals("outputs.json")
                                    ? "ORACLE"
                                    : "FIXTURE";
                    members.addObject().put("path", path).put("role", role);
                }
            }
        }
        Files.writeString(artifactRoot.resolve("index.json"), index.toPrettyString());
    }
}
