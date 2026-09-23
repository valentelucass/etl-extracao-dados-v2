package br.com.esl.etl.v2.bootstrap;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterWindow;
import br.com.esl.etl.v2.plataforma.persistencia.analitico.JdbcRasterLaboratory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One response in memory; duplicate observations and identity evidence are generated separately.
 */
public final class AnalyticRasterFixtures {
    public static final LocalDate DATE = LocalDate.of(2036, 4, 1);
    private static final ObjectMapper JSON = new ObjectMapper();

    private AnalyticRasterFixtures() {}

    public static ObjectNode data() throws IOException {
        try (var input =
                AnalyticRasterFixtures.class.getResourceAsStream(
                        "/analytic-laboratory/raster.synthetic.json")) {
            if (input == null) {
                throw new IllegalArgumentException("RAS_FIXTURE_MISSING");
            }
            final byte[] bytes = input.readNBytes(16385);
            if (bytes.length > 16384) {
                throw new IllegalArgumentException("RAS_FIXTURE_BOUND");
            }
            final var envelope = JSON.readTree(bytes);
            if (!"synthetic-analytic-raster-v1".equals(envelope.path("version").asText())
                    || !envelope.path("trip").isObject()) {
                throw new IllegalArgumentException("RAS_FIXTURE_CONTRACT");
            }
            return (ObjectNode) envelope.get("trip");
        }
    }

    public static RasterGateway source(final int roots, final int revision) throws IOException {
        if (roots < 0 || roots > 4096 || revision < 1 || revision > 100000) {
            throw new IllegalArgumentException("RAS_FIXTURE_SCOPE");
        }
        return new Source(roots, revision, data());
    }

    private record Source(int roots, int revision, ObjectNode template) implements RasterGateway {
        private int boundary(final LocalDate date) {
            final long day = ChronoUnit.DAYS.between(DATE, date);
            if (day < 0 || day > 3) {
                throw new IllegalArgumentException("RAS_FIXTURE_WINDOW");
            }
            return (int) ((day * roots + 2) / 3);
        }

        @Override
        public Response fetch(final RasterWindow window) throws IOException {
            final int first = boundary(window.start());
            final int count = Math.min(500, (boundary(window.endExclusive()) - first) * 3);
            final var page = JSON.createArrayNode();
            for (int position = 0; position < count; position++) {
                final int root = first + position / 3 + 1;
                final var row = template.deepCopy();
                row.put("CodSolicitacao", Integer.toString(10000 + root));
                row.put("Sequencial", Integer.toString(20000 + root));
                page.add(row);
            }
            return new Response(
                    JSON.writeValueAsBytes(page),
                    new Terminal(
                            window,
                            count,
                            count,
                            "synthetic-raster-window-"
                                    + window.start()
                                    + "-"
                                    + window.endExclusive(),
                            JdbcRasterLaboratory.SOURCE,
                            JdbcRasterLaboratory.TENANT,
                            JdbcRasterLaboratory.VERSION));
        }

        @Override
        public RasterBinding binding(final RasterWindow window, final int trip, final int stop) {
            final int count = (boundary(window.endExclusive()) - boundary(window.start())) * 3;
            if (trip < 1 || trip > count || stop < 0 || stop > 1) {
                throw new IllegalArgumentException("RAS_FIXTURE_POSITION");
            }
            final int root = boundary(window.start()) + (trip - 1) / 3 + 1;
            return new RasterBinding(
                    "synthetic-trip-" + root,
                    stop == 0 ? null : "synthetic-stop-1",
                    revision,
                    true,
                    false,
                    "synthetic-raster-binding-v1",
                    JdbcRasterLaboratory.SOURCE,
                    JdbcRasterLaboratory.TENANT,
                    JdbcRasterLaboratory.VERSION);
        }
    }
}
