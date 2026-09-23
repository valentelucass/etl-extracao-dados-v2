package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation.Cardinality;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRelation.Kind;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Seven explicit lateral relations per synthetic root, at most 98 rows per TVP. */
public final class ExpansionLaboratoryRelationFixtures {
    private ExpansionLaboratoryRelationFixtures() {}

    public static List<ExpansionRelation> bindingBatch(
            final LocalDate date, final int first, final int count) {
        if (date == null || first < 1 || count < 1 || count > 14 || first + count > 2049) {
            throw new IllegalArgumentException("EXP_RELATION_FIXTURE_BOUND");
        }
        final var rows = new ArrayList<ExpansionRelation>(count * 7);
        for (int root = first; root < first + count; root++) {
            for (final var kind :
                    List.of(
                            Kind.FAT_DOCUMENT_FREIGHT,
                            Kind.INV_FREIGHT,
                            Kind.SIN_FREIGHT,
                            Kind.LOC_FREIGHT)) {
                for (int component = 1;
                        component <= (kind == Kind.LOC_FREIGHT ? 1 : 2);
                        component++) {
                    final String code = kind.name().substring(0, 3);
                    rows.add(
                            new ExpansionRelation(
                                    "synthetic-plan-"
                                            + code.toLowerCase(java.util.Locale.ROOT)
                                            + "-"
                                            + root
                                            + "-"
                                            + component,
                                    1,
                                    kind,
                                    kind == Kind.LOC_FREIGHT
                                            ? new ExpansionKey(
                                                    ExpansionKey.Kind.INTEGER,
                                                    Integer.toString(600000 + root))
                                            : key(code + "-root-" + root),
                                    key("part-" + root),
                                    key("component-" + component),
                                    key("document-" + root + "-" + component),
                                    "INTEGER:" + (300000 + root),
                                    date,
                                    kind == Kind.LOC_FREIGHT
                                            ? Cardinality.ONE_TO_ONE
                                            : Cardinality.MANY_TO_MANY,
                                    true,
                                    "synthetic-explicit-link-v1"));
                }
            }
        }
        return List.copyOf(rows);
    }

    private static ExpansionKey key(final String value) {
        return new ExpansionKey(ExpansionKey.Kind.STRING, value);
    }
}
