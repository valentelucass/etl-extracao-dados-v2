package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.plataforma.fonte.dataexport.DataExportTemplate;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.ExpansionSyntheticSource;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.RelationalSyntheticSource;
import br.com.esl.etl.v2.plataforma.persistencia.expansao.JdbcExpansionLaboratory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Locale;

/** Closed synthetic inputs for the composed runner; all destinations are populated by pipelines. */
public final class AnalyticScenarioFixtures {
    public static final LocalDate DATE = LocalDate.of(2036, 4, 1);

    private AnalyticScenarioFixtures() {}

    public static ObjectNode manifest() {
        return read("manifest");
    }

    public static ObjectNode expansionData(final DataExportTemplate template) {
        return read(JdbcExpansionLaboratory.vertical(template).toLowerCase(Locale.ROOT));
    }

    public static ExpansionSyntheticSource expansion(
            final DataExportTemplate template,
            final int first,
            final int roots,
            final int pageSize,
            final int revision) {
        bounds(first, roots, pageSize, revision);
        final var seed = expansionData(template);
        return new ExpansionSyntheticSource(
                page -> {
                    final var rows = JsonNodeFactory.instance.arrayNode();
                    final int start = Math.multiplyExact(page - 1, pageSize);
                    for (int index = start;
                            index < Math.min(roots * 3, start + pageSize);
                            index++) {
                        final int root = first + index / 3;
                        final var row =
                                ExpansionLaboratoryFixtures.envelope(
                                        template,
                                        seed.deepCopy(),
                                        index + 1,
                                        root,
                                        index % 3 == 1 ? 2 : 1);
                        ((ObjectNode) row.path("binding")).put("revision", revision);
                        rows.add(row);
                    }
                    return rows.toString();
                });
    }

    public static RelationalSyntheticSource manifests(
            final int first,
            final int roots,
            final int pageSize,
            final int revision,
            final boolean correction) {
        bounds(first, roots, pageSize, revision);
        final var seed = manifest();
        return new RelationalSyntheticSource(
                        page -> {
                            final var rows = JsonNodeFactory.instance.arrayNode();
                            final int start = Math.multiplyExact(page - 1, pageSize);
                            for (int index = start;
                                    index < Math.min(roots * 3, start + pageSize);
                                    index++) {
                                final int root = first + index / 3;
                                final var row =
                                        seed.deepCopy()
                                                .put("sequence_code", root)
                                                .put("mft_pfs_pck_sequence_code", 100000 + root)
                                                .put(
                                                        "mft_mfs_key",
                                                        "1".repeat(40)
                                                                + String.format(
                                                                        Locale.ROOT, "%04d", root));
                                row.put(
                                        "finished_at",
                                        DATE.plusDays(correction ? 1 : 0)
                                                + "T12:00:00."
                                                + String.format(Locale.ROOT, "%09d", revision)
                                                + "Z");
                                if (correction) {
                                    row.put("departured_at", DATE.plusDays(1) + "T12:00:00Z");
                                    row.put("mft_crn_psn_nickname", "SYNTHETIC BRANCH B");
                                }
                                rows.add(row);
                            }
                            return rows.toString();
                        })
                .withAnalyticManifestDetails();
    }

    private static void bounds(
            final int first, final int roots, final int pageSize, final int revision) {
        if (first < 1
                || roots < 0
                || roots > 1024
                || first + roots > 2049
                || pageSize < 1
                || pageSize > 16
                || revision < 1
                || revision > 1000) {
            throw new IllegalArgumentException("ANA_SCENARIO_FIXTURE_BOUND");
        }
    }

    private static ObjectNode read(final String name) {
        if (!java.util.Set.of("manifest", "cap", "fat", "inv", "sin").contains(name)) {
            throw new IllegalArgumentException("ANA_SCENARIO_FIXTURE_NAME");
        }
        try (var input =
                AnalyticScenarioFixtures.class.getResourceAsStream(
                        "/analytic-laboratory/" + name + ".synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("ANA_SCENARIO_FIXTURE_MISSING");
            }
            final byte[] bytes = input.readNBytes(32769);
            if (bytes.length > 32768) {
                throw new IllegalArgumentException("ANA_SCENARIO_FIXTURE_BYTES");
            }
            final var value = new ObjectMapper().readTree(bytes);
            if (!value.isObject()) {
                throw new IllegalArgumentException("ANA_SCENARIO_FIXTURE_OBJECT");
            }
            return (ObjectNode) value;
        } catch (final IOException failure) {
            throw new IllegalStateException("ANA_SCENARIO_FIXTURE_INVALID", failure);
        }
    }
}
