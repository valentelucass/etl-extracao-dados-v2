package br.com.esl.etl.v2.plataforma.qualificacao;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticScenarioVariant;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Fixed hashes authored from input literals and an independently recorded envelope example. */
public final class QualificationLocationOracle {
    private final List<String> hashes;

    public QualificationLocationOracle() throws IOException {
        this(AnalyticScenarioVariant.BASELINE);
    }

    public QualificationLocationOracle(final AnalyticScenarioVariant variant) throws IOException {
        final String resource =
                switch (variant) {
                    case BASELINE -> "/qualification-laboratory/location-hashes.synthetic.json";
                    case VALUES_AND_NULLS ->
                            "/qualification-laboratory/location-representative-hashes.synthetic.json";
                };
        try (var input = QualificationLocationOracle.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalArgumentException("QUAL_LOCATION_ORACLE_MISSING");
            }
            final var root = QualificationJson.parse(input.readNBytes(32769), 32768);
            QualificationJson.fields(root, "version", "origin", "example", "hashes");
            if (!"qualification-location-hashes-v1"
                            .equals(QualificationJson.text(root, "version", 40))
                    || !"INDEPENDENT_LOC_ENVELOPE_V2"
                            .equals(QualificationJson.text(root, "origin", 40))) {
                throw new IllegalArgumentException("QUAL_LOCATION_ORACLE_ORIGIN");
            }
            QualificationJson.array(root.path("hashes"), 32, 32);
            final var values = new ArrayList<String>();
            for (final var item : root.path("hashes")) {
                QualificationJson.fields(item, "root", "sha256");
                if (QualificationJson.number(item, "root", 1, 32) != values.size() + 1) {
                    throw new IllegalArgumentException("QUAL_LOCATION_ORACLE_ORDER");
                }
                final String hash = QualificationJson.text(item, "sha256", 64);
                if (!hash.matches("[0-9a-f]{64}")) {
                    throw new IllegalArgumentException("QUAL_LOCATION_ORACLE_HASH");
                }
                values.add(hash);
            }
            hashes = List.copyOf(values);
        }
    }

    public String hash(final int root) {
        if (root < 1 || root > 32) {
            throw new IllegalArgumentException("QUAL_LOCATION_ORACLE_SCOPE");
        }
        return hashes.get(root - 1);
    }
}
