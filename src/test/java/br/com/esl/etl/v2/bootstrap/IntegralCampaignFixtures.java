package br.com.esl.etl.v2.bootstrap;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;

/** Seven dependent stages: source, reference and supplement corrections remain separate. */
final class IntegralCampaignFixtures {
    private IntegralCampaignFixtures() {}

    static Path write(final Path folder, final boolean alternate, final int roots)
            throws Exception {
        final var file = IntegralSequenceFixtures.scheduled(folder, alternate, roots);
        final var json = (ObjectNode) IntegralArtifactFixtures.read(file);
        final var advanced = new IntegralArtifactFixtures.Spec(alternate, roots, true);
        final var late = new IntegralArtifactFixtures.Spec(alternate, roots, true, false, false, 1);
        final var lateFolder = folder.resolve("late");
        IntegralArtifactFixtures.write(lateFolder, late);
        final var incrementalFolder = folder.resolve("incremental");
        copy(folder.resolve("advanced"), incrementalFolder);
        supplementRevision(incrementalFolder, advanced.revision() + 1);
        supplementRevision(lateFolder, advanced.revision() + 2);
        final var steps = (ArrayNode) json.path("steps");
        final var incremental = ((ObjectNode) steps.get(2)).deepCopy();
        incremental
                .put("id", "incremental")
                .put("predecessor", "later")
                .put("mode", "INCREMENTAL")
                .put("executionRevision", advanced.revision() + 1);
        incremental.set(
                "input",
                IntegralArtifactFixtures.pin(folder, incrementalFolder.resolve("input.json")));
        incremental.set(
                "oracle",
                IntegralArtifactFixtures.pin(folder, incrementalFolder.resolve("oracle.json")));
        steps.add(incremental);
        final var backfill = incremental.deepCopy();
        backfill.put("id", "late")
                .put("predecessor", "incremental")
                .put("mode", "BACKFILL")
                .put("executionRevision", advanced.revision() + 2)
                .put("sourceRevision", late.revision());
        backfill.set(
                "input", IntegralArtifactFixtures.pin(folder, lateFolder.resolve("input.json")));
        backfill.set(
                "oracle", IntegralArtifactFixtures.pin(folder, lateFolder.resolve("oracle.json")));
        steps.add(backfill);
        final var referenceFolder = folder.resolve("reference");
        copy(lateFolder, referenceFolder);
        supplementRevision(referenceFolder, advanced.revision() + 3);
        SequenceReferenceFixtures.revise(referenceFolder, alternate);
        final var reference = backfill.deepCopy();
        reference
                .put("id", "reference")
                .put("predecessor", "late")
                .put("executionRevision", advanced.revision() + 3)
                .put("referenceRevision", 3);
        reference.set(
                "input",
                IntegralArtifactFixtures.pin(folder, referenceFolder.resolve("input.json")));
        reference.set(
                "oracle",
                IntegralArtifactFixtures.pin(folder, referenceFolder.resolve("oracle.json")));
        steps.add(reference);
        IntegralArtifactFixtures.save(file, json);
        return SequenceRecompositionFixtures.append(
                folder, file, referenceFolder, late, "reference", advanced.revision() + 4);
    }

    static void copy(final Path source, final Path target) throws Exception {
        try (var paths = java.nio.file.Files.walk(source)) {
            for (final var file : paths.toList()) {
                final var destination = target.resolve(source.relativize(file));
                if (java.nio.file.Files.isDirectory(file)) {
                    java.nio.file.Files.createDirectories(destination);
                } else {
                    java.nio.file.Files.copy(file, destination);
                }
            }
        }
    }

    static void supplementRevision(final Path folder, final int revision) throws Exception {
        final var input = (ObjectNode) IntegralArtifactFixtures.read(folder.resolve("input.json"));
        input.put("version", "local-artifact-scenario-v3")
                .put("captureDate", input.path("windowStart").asText())
                .put("supplementRevision", revision);
        final var file = folder.resolve("support/manifest.json");
        final var support = IntegralArtifactFixtures.read(file);
        final var groups = support.path("batches").fields();
        while (groups.hasNext()) {
            final var group = groups.next();
            for (final var descriptor : group.getValue()) {
                final var batch = file.getParent().resolve(descriptor.path("file").asText());
                final var rows = IntegralArtifactFixtures.read(batch);
                for (final var row : rows) {
                    ((ObjectNode) row).put("revision", revision);
                    if (group.getKey().equals("relational")) {
                        ((ObjectNode) row)
                                .put(
                                        "evidenceId",
                                        row.path("evidenceId").asText() + "-s" + revision);
                    }
                }
                IntegralArtifactFixtures.save(batch, rows);
                ((ObjectNode) descriptor)
                        .put(
                                "sha256",
                                br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.sha256(
                                        batch));
            }
        }
        IntegralArtifactFixtures.save(file, support);
        input.set("supplements", IntegralArtifactFixtures.pin(folder, file));
        IntegralArtifactFixtures.save(folder.resolve("input.json"), input);
        final var oracle =
                (ObjectNode) IntegralArtifactFixtures.read(folder.resolve("oracle.json"));
        oracle.put(
                "inputSha256",
                br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson.sha256(
                        folder.resolve("input.json")));
        IntegralArtifactFixtures.save(folder.resolve("oracle.json"), oracle);
    }
}
