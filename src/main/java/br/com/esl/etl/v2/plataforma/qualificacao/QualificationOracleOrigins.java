package br.com.esl.etl.v2.plataforma.qualificacao;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Per-cell provenance resolves only declared packaged fixture members and present field paths. */
final class QualificationOracleOrigins {
    private static final Set<String> RULES =
            Set.of(
                    "MANUAL_SYNTHETIC_RULES_V1",
                    "DECLARED_CAPTURE_CONTEXT_V1",
                    "INPUT_AND_CAPTURE_CONTEXT_V1",
                    "TECHNICAL_CLOCK_BOUND_V1",
                    "DECLARED_LOGICAL_CLOCK_V1");
    private static final Set<String> FIXTURES =
            Set.of(
                    "analytic-laboratory/cap",
                    "analytic-laboratory/collection",
                    "analytic-laboratory/fat",
                    "analytic-laboratory/freight-attributes",
                    "analytic-laboratory/inv",
                    "analytic-laboratory/manifest",
                    "analytic-laboratory/quote",
                    "analytic-laboratory/qualification-representative",
                    "analytic-laboratory/raster",
                    "analytic-laboratory/sin",
                    "expansion-laboratory/fre",
                    "expansion-laboratory/loc");
    private final Map<String, JsonNode> inputs = new HashMap<>();

    String verify(final String origin) {
        if (RULES.contains(origin)) {
            return origin;
        }
        if (!origin.startsWith("fixture:")) {
            throw new IllegalArgumentException("QUAL_ORACLE_CELL_ORIGIN");
        }
        final var parts = origin.substring(8).split("#", -1);
        if (parts.length < 2 || parts.length > 4 || !FIXTURES.contains(parts[0])) {
            throw new IllegalArgumentException("QUAL_ORACLE_FIXTURE_ORIGIN");
        }
        JsonNode value = inputs.computeIfAbsent(parts[0], QualificationOracleOrigins::read);
        for (int index = 1; index < parts.length; index++) {
            if (!parts[index].matches("/[a-zA-Z_][a-zA-Z0-9_]*")) {
                throw new IllegalArgumentException("QUAL_ORACLE_ORIGIN_PATH");
            }
            value = value.at(parts[index]);
            if (value.isMissingNode()) {
                throw new IllegalArgumentException("QUAL_ORACLE_ORIGIN_FIELD_MISSING");
            }
        }
        return origin;
    }

    private static JsonNode read(final String fixture) {
        try (var input =
                QualificationOracleOrigins.class.getResourceAsStream(
                        "/" + fixture + ".synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("QUAL_ORACLE_ORIGIN_RESOURCE");
            }
            return QualificationJson.parse(input.readNBytes(131073), 131072);
        } catch (final IOException failure) {
            throw new IllegalArgumentException("QUAL_ORACLE_ORIGIN_RESOURCE", failure);
        }
    }
}
