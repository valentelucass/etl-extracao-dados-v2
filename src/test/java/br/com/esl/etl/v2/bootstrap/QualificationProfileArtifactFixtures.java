package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QualificationProfileArtifactFixtures {
    private QualificationProfileArtifactFixtures() {}

    public static void main(final String[] args) throws Exception {
        for (final var profile : LocalCharacterizationProfile.values()) {
            for (final int identity : new int[] {73, 951}) {
                write(
                        Path.of(args[0])
                                .resolve(
                                        profile.name().toLowerCase(java.util.Locale.ROOT)
                                                + "-"
                                                + identity),
                        profile,
                        identity);
            }
        }
    }

    static Path write(
            final Path folder, final LocalCharacterizationProfile profile, final int identity)
            throws Exception {
        Files.createDirectories(folder);
        final var data = row(profile, identity);
        final var records = JsonNodeFactory.instance.arrayNode();
        records.addObject()
                .put("raw", data.toString())
                .set("sourceKey", data.at(profile.identityPath()));
        Files.writeString(folder.resolve("rows.json"), records.toString());
        Files.writeString(folder.resolve("end.json"), "[]");
        final var manifest =
                JsonNodeFactory.instance
                        .objectNode()
                        .put("version", "local-profile-artifact-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("family", profile.name())
                        .put("profileSha256", profile.sha256())
                        .put("revision", profile.revision())
                        .put("source", "SYNTHETIC_CHARACTERIZATION")
                        .put("tenant", "SYNTHETIC_TENANT")
                        .put("windowStart", "2036-04-01")
                        .put("windowEndExclusive", "2036-04-02")
                        .put("zone", "America/Sao_Paulo")
                        .put("pageSize", 2)
                        .put("maximumPages", 100)
                        .put("maximumRows", 1000)
                        .put("expectedRows", 1)
                        .put("complete", true);
        final var pages = manifest.putArray("pages");
        for (final var name : new String[] {"rows.json", "end.json"}) {
            pages.addObject()
                    .put("file", name)
                    .put("sha256", QualificationJson.sha256(folder.resolve(name)));
        }
        final var file = folder.resolve("profile.json");
        Files.writeString(file, manifest.toString());
        return file;
    }

    static ObjectNode row(final LocalCharacterizationProfile profile, final int identity) {
        final var row = JsonNodeFactory.instance.objectNode();
        row.put(profile.identityPath().substring(1), identity);
        switch (profile) {
            case COL ->
                    row.put("sequence_code", identity + 42)
                            .put("updated_at", "2036-04-01T12:00:00-03:00")
                            .put("request_date", "2036-04-01")
                            .putNull("pck_mik_mft_sequence_code");
            case MAN -> row.put("created_at", "2036-04-01T12:00:00-03:00");
            case COT -> row.put("requested_at", "2036-04-01T12:00:00-03:00");
            case FRE -> row.put("cte_created_at", "2036-04-01T12:00:00-03:00");
            case LOC ->
                    row.put("service_at", "2036-04-01T12:00:00-03:00")
                            .put("total", identity + ".25");
            case USER -> row.put("name", "Synthetic profile " + identity);
        }
        return row;
    }
}
