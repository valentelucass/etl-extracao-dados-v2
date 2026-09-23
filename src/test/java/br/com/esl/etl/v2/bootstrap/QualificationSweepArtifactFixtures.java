package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

public final class QualificationSweepArtifactFixtures {
    private QualificationSweepArtifactFixtures() {}

    public static void main(final String[] args) throws Exception {
        write(Path.of(args[0]).resolve("first"), LocalDate.of(2036, 4, 2), 3);
        write(Path.of(args[0]).resolve("second"), LocalDate.of(2036, 4, 3), 6);
    }

    static Path write(final Path folder, final LocalDate date, final int roots) throws Exception {
        Files.createDirectories(folder);
        final var program =
                object().put("version", "local-collection-sweep-program-v1")
                        .put("mode", "LOCAL_ARTIFACT_ROLLBACK")
                        .put("target", "localhost/ETL_SISTEMA_V2_SHADOW")
                        .put("windowStart", date.toString())
                        .put("windowEndExclusive", date.plusDays(1).toString());
        final var artifacts = program.putArray("artifacts");
        for (int phase = 0; phase < 4; phase++) {
            final boolean omit = phase == 1 || phase == 2;
            final var artifact =
                    object().put("version", "local-collection-sweep-v1")
                            .put("origin", "SYNTHETIC_CLOSED_COLLECTION_UNIVERSE_V1")
                            .put("date", date.toString())
                            .put("universeRoots", roots)
                            .put("omitFirst", omit)
                            .put("pageSize", 2)
                            .put(
                                    "contractSha256",
                                    new RelationalSyntheticSource(page -> "[]")
                                            .withAnalyticCollectionDetails()
                                            .contractRelease(DataExportTemplate.COLETAS)
                                            .contractFingerprint()
                                            .sha256());
            final var observations = artifact.putArray("observations");
            for (int observation = 0; observation < 4; observation++) {
                final var pages = observations.addArray();
                final int total = (roots - (omit ? 1 : 0)) * 3;
                for (int first = 0; first < total + 2; first += 2) {
                    final var records = JsonNodeFactory.instance.arrayNode();
                    for (int index = first; index < Math.min(total, first + 2); index++) {
                        final int root = index / 3 + (omit ? 2 : 1);
                        records.add(
                                AnalyticCollectionsFixtures.data()
                                        .put("id", 200000 + root)
                                        .put("sequence_code", 100000 + root)
                                        .put("synthetic_item_key", root)
                                        .put("request_date", date.toString()));
                    }
                    final String file =
                            "phase-"
                                    + phase
                                    + "-observation-"
                                    + observation
                                    + "-page-"
                                    + first
                                    + ".json";
                    save(folder.resolve(file), records);
                    pages.add(pin(folder, file));
                }
            }
            final String file = "phase-" + phase + ".json";
            save(folder.resolve(file), artifact);
            artifacts.add(pin(folder, file));
        }
        save(folder.resolve("program.json"), program);
        return folder.resolve("program.json");
    }

    private static ObjectNode pin(final Path folder, final String file) throws Exception {
        return object().put("file", file)
                .put("sha256", QualificationJson.sha256(folder.resolve(file)));
    }

    private static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static void save(final Path path, final JsonNode value) throws Exception {
        Files.writeString(path, value.toString());
    }
}
