package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionFreightTerms;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionDependencySource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionFieldParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;

/** Packaged, lazy and bounded source data; target selection is an explicit binding key. */
public final class ExpansionDependencyFixtures {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ExpansionDependencyFixtures() {}

    public static ExpansionFreightTerms financialTerms(final String sourceKey) {
        try (var input =
                ExpansionDependencyFixtures.class.getResourceAsStream(
                        "/expansion-laboratory/freight-terms.synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("EXP_TERMS_RESOURCE_MISSING");
            }
            final var data = JSON.readTree(input);
            for (final var field :
                    java.util.List.of(
                            "provenance",
                            "version",
                            "revision",
                            "billingReferenceDate",
                            "classification",
                            "courtesy",
                            "eligible",
                            "fallbackVolumes",
                            "payerReference",
                            "currency",
                            "unit",
                            "active",
                            "evidence")) {
                if (!data.has(field)) {
                    throw new IllegalArgumentException("EXP_TERMS_FIELD_ABSENT");
                }
            }
            if (!data.path("provenance").asText().equals("FIXTURE_SINTETICA_EXPLICITA")
                    || !data.path("version").asText().equals("expansion-freight-terms-v1")) {
                throw new IllegalArgumentException("EXP_TERMS_PROVENANCE");
            }
            final String volume = typed(ExpansionFieldParser.integer(data, "fallbackVolumes"));
            return new ExpansionFreightTerms(
                    sourceKey,
                    Integer.parseInt(typed(ExpansionFieldParser.integer(data, "revision"))),
                    typed(ExpansionFieldParser.date(data, "billingReferenceDate")),
                    typed(ExpansionFieldParser.text(data, "classification")),
                    typed(ExpansionFieldParser.bool(data, "courtesy")),
                    typed(ExpansionFieldParser.bool(data, "eligible")),
                    volume == null ? null : Integer.valueOf(volume),
                    typed(ExpansionFieldParser.text(data, "payerReference")),
                    typed(ExpansionFieldParser.text(data, "currency")),
                    typed(ExpansionFieldParser.text(data, "unit")),
                    java.util.Objects.requireNonNull(
                            typed(ExpansionFieldParser.bool(data, "active"))),
                    typed(ExpansionFieldParser.text(data, "evidence")));
        } catch (final IOException failure) {
            throw new IllegalStateException("EXP_TERMS_RESOURCE_INVALID", failure);
        }
    }

    private static <T> T typed(final ExpansionValue<T> field) {
        if (!field.valid()) {
            throw new IllegalArgumentException("EXP_TERMS_INVALID_FIELD");
        }
        return field.value();
    }

    public static ObjectNode data(final DataExportTemplate template, final int index) {
        if (index < 1 || index > 2048) {
            throw new IllegalArgumentException("EXP_DEP_INDEX");
        }
        final String name =
                switch (template) {
                    case FRETES -> "fre";
                    case LOCALIZACAO_CARGAS -> "loc";
                    default -> throw new IllegalArgumentException("EXP_DEP_TEMPLATE");
                };
        try (var input =
                ExpansionDependencyFixtures.class.getResourceAsStream(
                        "/expansion-laboratory/" + name + ".synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("EXP_DEP_RESOURCE_MISSING");
            }
            final var row = (ObjectNode) JSON.readTree(input);
            if (template == DataExportTemplate.FRETES) {
                row.put("id", 300000 + index);
            }
            row.put("corporation_sequence_number", 600000 + index);
            return row;
        } catch (final IOException failure) {
            throw new IllegalStateException("EXP_DEP_RESOURCE_INVALID", failure);
        }
    }

    public static ExpansionDependencySource source(
            final DataExportTemplate template, final int roots, final int pageSize) {
        return source(template, 1, roots, pageSize);
    }

    public static ExpansionDependencySource source(
            final DataExportTemplate template,
            final int firstRoot,
            final int roots,
            final int pageSize) {
        if (firstRoot < 1
                || firstRoot + roots > 2049
                || roots < 0
                || roots > 2048
                || pageSize < 1
                || pageSize > 100) {
            throw new IllegalArgumentException("EXP_DEP_SOURCE_BUDGET");
        }
        return new ExpansionDependencySource(
                page -> {
                    final var array = JSON.createArrayNode();
                    for (int ordinal = (page - 1) * pageSize;
                            ordinal < Math.min(page * pageSize, roots * 2);
                            ordinal++) {
                        array.add(data(template, ordinal / 2 + firstRoot));
                    }
                    return array.toString();
                });
    }

    public static ExpansionDependencySource hydration(
            final DataExportTemplate template, final String key) {
        if (key == null || !key.matches("INTEGER:[1-9][0-9]{5}")) {
            throw new IllegalArgumentException("EXP_DEP_TARGET_REQUIRED");
        }
        final int offset = template == DataExportTemplate.FRETES ? 300000 : 600000;
        final ObjectNode data = data(template, Integer.parseInt(key.substring(8)) - offset);
        return new ExpansionDependencySource(page -> page == 1 ? "[" + data + "]" : "[]");
    }
}
