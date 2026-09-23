package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.relacional.JdbcRelationalLaboratory;
import br.com.esl.etl.v2.plataforma.relacional.RelationalBinding;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Packaged synthetic fixture v1. Numeric formulas apply exclusively to this declared fixture. */
public final class RelationalLaboratoryFixtures {
    private RelationalLaboratoryFixtures() {}

    public static RelationalSyntheticSource source(
            final DataExportTemplate template,
            final LocalDate date,
            final int first,
            final int count,
            final int pageSize,
            final boolean duplicates) {
        if (first < 1
                || count < 0
                || count > 4096
                || first + count > 8193
                || pageSize < 1
                || pageSize > 100) {
            throw new IllegalArgumentException("REL_LAB_FIXTURE_BOUND");
        }
        final int repetitions = duplicates ? 2 : 1;
        return new RelationalSyntheticSource(
                page -> {
                    final int offset = Math.multiplyExact(page - 1, pageSize);
                    final int end = Math.min(count * repetitions, offset + pageSize);
                    final var text = new StringBuilder("[");
                    for (int ordinal = offset; ordinal < end; ordinal++) {
                        if (ordinal > offset) {
                            text.append(',');
                        }
                        text.append(row(template, date, first + ordinal / repetitions));
                    }
                    return text.append(']').toString();
                });
    }

    private static String row(
            final DataExportTemplate template, final LocalDate date, final int index) {
        final String common = ",\"synthetic_fixture\":true}";
        return switch (template) {
            case MANIFESTOS ->
                    "{\"sequence_code\":"
                            + index
                            + ",\"mft_pfs_pck_sequence_code\":"
                            + (100_000 + index)
                            + ",\"created_at\":\""
                            + date
                            + "T10:00:00Z\",\"status\":\"pending\",\"km\":\"0\""
                            + common;
            case COLETAS ->
                    "{\"id\":"
                            + (200_000 + index)
                            + ",\"sequence_code\":"
                            + (100_000 + index)
                            + ",\"request_date\":\""
                            + date
                            + "\",\"status\":\"pending\",\"status_updated_at\":\""
                            + date
                            + "T10:00:00.123456789Z\",\"synthetic_item_key\":"
                            + index
                            + common;
            case FRETES ->
                    "{\"id\":"
                            + (300_000 + index)
                            + ",\"corporation_sequence_number\":"
                            + index
                            + ",\"criado_em\":\""
                            + date
                            + "T10:00:00Z\",\"servico_em\":\""
                            + date
                            + "T09:00:00Z\",\"synthetic_pick_item\":\"item-"
                            + index
                            + "\""
                            + common;
            default -> throw new IllegalArgumentException("REL_LAB_ENTITY_DENIED");
        };
    }

    public static List<RelationalBinding> bindingBatch(
            final LocalDate date, final int first, final int count) {
        if (first < 1 || count < 1 || count > 50 || first + count > 8193) {
            throw new IllegalArgumentException("REL_LAB_FIXTURE_BINDING_BOUND");
        }
        final var result = new ArrayList<RelationalBinding>(count * 2);
        for (int index = first; index < first + count; index++) {
            result.add(
                    new RelationalBinding(
                            "synthetic-mc-" + index,
                            RelationalBinding.Relation.MC,
                            RelationalBinding.Key.integer(index),
                            RelationalBinding.Key.integer(100_000L + index),
                            RelationalBinding.Key.integer(200_000L + index),
                            RelationalBinding.Key.root(),
                            date,
                            1,
                            RelationalBinding.Cardinality.ONE_TO_ONE));
            result.add(
                    new RelationalBinding(
                            "synthetic-cf-" + index,
                            RelationalBinding.Relation.CF,
                            RelationalBinding.Key.integer(200_000L + index),
                            RelationalBinding.Key.integer(index),
                            RelationalBinding.Key.integer(300_000L + index),
                            new RelationalBinding.Key(
                                    RelationalBinding.WireType.STRING, "item-" + index),
                            date,
                            1,
                            RelationalBinding.Cardinality.ONE_TO_ONE));
        }
        return List.copyOf(result);
    }

    public static RelationalSyntheticSource hydration(
            final JdbcRelationalLaboratory.Claim claim, final int pageSize) {
        final int base = claim.relation() == RelationalBinding.Relation.MC ? 200_000 : 300_000;
        if (!claim.targetKey().matches("INTEGER:[0-9]{6}")) {
            throw new IllegalArgumentException("REL_LAB_HYDRATION_FIXTURE_TARGET");
        }
        final int index = Integer.parseInt(claim.targetKey().substring(8)) - base;
        if (index < 1 || index > 8192) {
            throw new IllegalArgumentException("REL_LAB_HYDRATION_FIXTURE_TARGET");
        }
        return source(
                claim.relation() == RelationalBinding.Relation.MC
                        ? DataExportTemplate.COLETAS
                        : DataExportTemplate.FRETES,
                claim.date(),
                index,
                1,
                pageSize,
                false);
    }
}
