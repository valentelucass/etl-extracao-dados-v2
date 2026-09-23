package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

public final class QualificationRasterArtifactFixtures {
    private QualificationRasterArtifactFixtures() {}

    static Path write(final Path folder, final int roots, final boolean alternate)
            throws Exception {
        Files.createDirectories(folder);
        final var manifest =
                object().put("version", "local-raster-artifact-v1")
                        .put("origin", "LOCAL_SYNTHETIC_ARTIFACT_V1")
                        .put("source", "SYNTHETIC_ANALYTIC_LAB")
                        .put("tenant", "SYNTHETIC_ANALYTIC_TENANT")
                        .put("contract", "synthetic-analytic-v1")
                        .put("zone", "America/Sao_Paulo")
                        .put("windowStart", "2036-04-01")
                        .put("windowEndExclusive", "2036-04-04")
                        .put("revision", 1)
                        .put("maximumCalls", 1000)
                        .put("maximumRows", 100000);
        final var page =
                manifest.putArray("pages")
                        .addObject()
                        .put("windowStart", "2036-04-01")
                        .put("windowEndExclusive", "2036-04-04")
                        .put("complete", true)
                        .put("trips", roots * 3)
                        .put("stops", roots * 3)
                        .put("receipt", "synthetic-explicit-response");
        final var rows = JsonNodeFactory.instance.arrayNode();
        final var bindings = JsonNodeFactory.instance.arrayNode();
        for (int index = 0; index < roots * 3; index++) {
            final int root = index / 3 + 1;
            final var row =
                    AnalyticRasterFixtures.data()
                            .put("CodSolicitacao", Integer.toString(10000 + root))
                            .put("Sequencial", Integer.toString(20000 + root));
            if (alternate) {
                row.put("PlacaVeiculo", "SYN0009").put("TempoTotalViagem", 125);
            }
            rows.add(row);
            for (int stop = 0; stop <= 1; stop++) {
                final var binding =
                        bindings.addObject()
                                .put("tripPosition", index + 1)
                                .put("stopPosition", stop)
                                .put("tripKey", "synthetic-explicit-journey-" + root)
                                .put("revision", 1)
                                .put("active", true)
                                .put("reactivate", false)
                                .put("evidence", "synthetic-independent-assignment")
                                .put("source", "SYNTHETIC_ANALYTIC_LAB")
                                .put("tenant", "SYNTHETIC_ANALYTIC_TENANT")
                                .put("contract", "synthetic-analytic-v1");
                if (stop == 0) {
                    binding.putNull("stopKey");
                } else {
                    binding.put("stopKey", "synthetic-explicit-destination");
                }
            }
        }
        save(folder.resolve("body.json"), rows);
        save(folder.resolve("bindings.json"), bindings);
        page.set("body", pin(folder, "body.json"));
        page.set("bindings", pin(folder, "bindings.json"));
        save(folder.resolve("raster.json"), manifest);
        return folder.resolve("raster.json");
    }

    static void mutate(final Path manifest, final String member, final Consumer<JsonNode> edit)
            throws Exception {
        final var root = (ObjectNode) QualificationJson.read(manifest, 131072);
        final var page = (ObjectNode) root.path("pages").get(0);
        if (member.equals("manifest")) {
            edit.accept(page);
        } else {
            final Path file = manifest.getParent().resolve(member + ".json");
            final var data = QualificationJson.read(file, 2097152);
            edit.accept(data);
            save(file, data);
            page.set(member, pin(manifest.getParent(), member + ".json"));
        }
        save(manifest, root);
    }

    private static ObjectNode object() {
        return JsonNodeFactory.instance.objectNode();
    }

    private static ObjectNode pin(final Path folder, final String file) throws Exception {
        return object().put("file", file)
                .put("sha256", QualificationJson.sha256(folder.resolve(file)));
    }

    private static void save(final Path path, final JsonNode value) throws Exception {
        Files.writeString(path, value.toString());
    }
}
