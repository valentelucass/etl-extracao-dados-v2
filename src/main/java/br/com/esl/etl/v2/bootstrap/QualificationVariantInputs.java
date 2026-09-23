package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/** Explicit optional source fields enter the same lazy parser/capture path as the baseline. */
public final class QualificationVariantInputs {
    private QualificationVariantInputs() {}

    public static ExpansionDependencySource source(
            final DataExportTemplate template,
            final int first,
            final int roots,
            final int pageSize) {
        if (first < 1 || roots < 0 || first + roots > 33 || pageSize < 1 || pageSize > 16) {
            throw new IllegalArgumentException("QUAL_VARIANT_SOURCE_LIMIT");
        }
        final var patch = patch(template);
        final var source =
                new ExpansionDependencySource(
                        page -> {
                            if (page < 1 || page > 1000) {
                                throw new IllegalArgumentException("QUAL_VARIANT_PAGE");
                            }
                            final var rows = JsonNodeFactory.instance.arrayNode();
                            final int start = Math.multiplyExact(page - 1, pageSize);
                            for (int ordinal = start;
                                    ordinal < Math.min(roots * 2, start + pageSize);
                                    ordinal++) {
                                rows.add(
                                        ExpansionDependencyFixtures.data(
                                                        template, first + ordinal / 2)
                                                .setAll(patch));
                            }
                            return rows.toString();
                        });
        return template == DataExportTemplate.FRETES
                ? source.withAnalyticFreightPerformance()
                : source;
    }

    public static ExpansionDependencySource hydration(final String key) {
        if (key == null || !key.matches("INTEGER:3000[0-3][0-9]")) {
            throw new IllegalArgumentException("QUAL_VARIANT_HYDRATION_TARGET");
        }
        final int root = Integer.parseInt(key.substring(8)) - 300000;
        if (root < 1 || root > 32) {
            throw new IllegalArgumentException("QUAL_VARIANT_HYDRATION_TARGET");
        }
        final var row =
                ExpansionDependencyFixtures.data(DataExportTemplate.FRETES, root)
                        .setAll(patch(DataExportTemplate.FRETES));
        return new ExpansionDependencySource(page -> page == 1 ? "[" + row + "]" : "[]")
                .withAnalyticFreightPerformance();
    }

    private static ObjectNode patch(final DataExportTemplate template) {
        try (var input =
                QualificationVariantInputs.class.getResourceAsStream(
                        "/analytic-laboratory/qualification-representative.synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("QUAL_VARIANT_INPUT_MISSING");
            }
            final var root = QualificationJson.parse(input.readNBytes(8193), 8192);
            QualificationJson.fields(
                    root, "version", "origin", "freight", "location", "firstUserName");
            if (!"qualification-representative-v1"
                            .equals(QualificationJson.text(root, "version", 40))
                    || !"DECLARED_SYNTHETIC_INPUT_VARIANT"
                            .equals(QualificationJson.text(root, "origin", 64))
                    || !root.path("firstUserName").isNull()) {
                throw new IllegalArgumentException("QUAL_VARIANT_INPUT_SCOPE");
            }
            QualificationJson.fields(
                    root.path("freight"),
                    "criado_em",
                    "finished_at",
                    "fit_dpn_performance_finished_at");
            QualificationJson.fields(
                    root.path("location"),
                    "service_type",
                    "fit_crn_psn_nickname",
                    "fit_dpn_delivery_prediction_at",
                    "fit_dyn_name",
                    "fit_dyn_drt_nickname",
                    "fit_fsn_name",
                    "fit_o_n_name",
                    "fit_o_n_drt_nickname",
                    "fit_fln_cln_nickname");
            final var data =
                    root.path(
                            switch (template) {
                                case FRETES -> "freight";
                                case LOCALIZACAO_CARGAS -> "location";
                                default ->
                                        throw new IllegalArgumentException("QUAL_VARIANT_TEMPLATE");
                            });
            data.fieldNames().forEachRemaining(name -> QualificationJson.text(data, name, 128));
            return ((ObjectNode) data).deepCopy();
        } catch (final IOException failure) {
            throw new IllegalStateException("QUAL_VARIANT_INPUT_READ", failure);
        }
    }
}
