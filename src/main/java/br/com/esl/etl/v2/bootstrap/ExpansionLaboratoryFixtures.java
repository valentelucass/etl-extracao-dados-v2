package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.util.Locale;

/** Lazy packaged fixtures: two distinct components and an exact repeat crossing page boundaries. */
public final class ExpansionLaboratoryFixtures {
    private ExpansionLaboratoryFixtures() {}

    public static ExpansionSyntheticSource source(
            final DataExportTemplate template, final int roots, final int pageSize) {
        return source(template, 1, roots, pageSize, 1);
    }

    public static ExpansionSyntheticSource source(
            final DataExportTemplate template,
            final int firstRoot,
            final int roots,
            final int pageSize,
            final int revision) {
        if (firstRoot < 1
                || firstRoot + roots > 2049
                || revision < 1
                || revision > 1000
                || roots < 0
                || roots > 2048
                || pageSize < 1
                || pageSize > 100) {
            throw new IllegalArgumentException("EXP_FIXTURE_BOUND");
        }
        final var seed = data(template);
        return new ExpansionSyntheticSource(
                page -> {
                    final var rows = JsonNodeFactory.instance.arrayNode();
                    final int begin = Math.multiplyExact(page - 1, pageSize),
                            end = Math.min(roots * 3, begin + pageSize);
                    for (int index = begin; index < end; index++) {
                        final int root = index / 3 + firstRoot, component = index % 3 == 1 ? 2 : 1;
                        final var value =
                                envelope(template, seed.deepCopy(), index + 1, root, component);
                        ((ObjectNode) value.path("binding")).put("revision", revision);
                        rows.add(value);
                    }
                    return rows.toString();
                });
    }

    public static ObjectNode data(final DataExportTemplate template) {
        final String name = JdbcExpansionLaboratory.vertical(template).toLowerCase(Locale.ROOT);
        try (var input =
                ExpansionLaboratoryFixtures.class.getResourceAsStream(
                        "/expansion-laboratory/" + name + ".synthetic.json")) {
            if (input == null) {
                throw new IllegalStateException("EXP_PACKAGED_FIXTURE_MISSING");
            }
            return (ObjectNode) new ObjectMapper().readTree(input);
        } catch (final IOException failure) {
            throw new IllegalStateException("EXP_PACKAGED_FIXTURE_INVALID", failure);
        }
    }

    public static ObjectNode envelope(
            final DataExportTemplate template,
            final ObjectNode data,
            final long occurrence,
            final int root,
            final int component) {
        final String code = JdbcExpansionLaboratory.vertical(template);
        switch (template) {
            case CONTAS_A_PAGAR -> {
                data.put("ant_ils_sequence_code", root);
                data.put("ant_ces_value", component == 1 ? "40.00" : "60.00");
                data.put("ant_ils_pas_value", component == 1 ? "40.00" : "60.00");
            }
            case FATURAS_POR_CLIENTE -> {
                data.put("id", root * 10 + component);
                data.put("corporation_sequence_number", root);
            }
            case INVENTARIO -> {
                data.put("sequence_code", root);
                data.put("cnr_c_s_fit_corporation_sequence_number", root * 10 + component);
            }
            case SINISTROS -> {
                data.put("sequence_code", root);
                data.put("icm_fis_fit_corporation_sequence_number", root * 10 + component);
                data.put("icm_fis_ioe_number", "synthetic-occurrence-" + component);
            }
            default -> throw new IllegalArgumentException("EXP_TEMPLATE_REQUIRED");
        }
        final var envelope = JsonNodeFactory.instance.objectNode();
        envelope.put("capture_occurrence", occurrence)
                .put("provenance", "FIXTURE_SINTETICA_EXPLICITA");
        envelope.set("data", data);
        envelope.putObject("binding")
                .put("root", code + "-root-" + root)
                .put("part", "part-" + root)
                .put("component", "component-" + component)
                .put("revision", 1)
                .put("evidence", "synthetic-expansion-v1")
                .put("currency", "BRL")
                .put("unit", "MAJOR")
                .put("additive_allocation", code.equals("CAP"))
                .put("active", true)
                .put("reactivation", false);
        return envelope;
    }
}
