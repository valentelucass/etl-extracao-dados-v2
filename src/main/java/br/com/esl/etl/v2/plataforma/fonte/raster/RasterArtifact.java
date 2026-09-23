package br.com.esl.etl.v2.plataforma.fonte.raster;

import br.com.esl.etl.v2.modulos.raster.domain.RasterBinding;
import br.com.esl.etl.v2.modulos.raster.domain.RasterStop;
import br.com.esl.etl.v2.modulos.raster.domain.RasterTripObservation;
import br.com.esl.etl.v2.plataforma.qualificacao.PinnedLocalJson;
import br.com.esl.etl.v2.plataforma.qualificacao.QualificationJson;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Local Raster payloads and lateral identities supplied independently; only one page is retained.
 */
public final class RasterArtifact {
    private final RasterWindow window;
    private final ZoneId zone;
    private final int revision;
    private final int maximumCalls;
    private final int maximumRows;
    private final Map<RasterWindow, Page> pages = new LinkedHashMap<>();

    public RasterArtifact(final Path path) throws IOException {
        this(path, QualificationJson.read(path, 131072));
    }

    public RasterArtifact(final PinnedLocalJson pin) throws IOException {
        this(pin.file().toPath(), pin.read());
    }

    private RasterArtifact(final Path path, final JsonNode root) {
        QualificationJson.fields(
                root,
                "version",
                "origin",
                "source",
                "tenant",
                "contract",
                "zone",
                "windowStart",
                "windowEndExclusive",
                "revision",
                "maximumCalls",
                "maximumRows",
                "pages");
        final String version = QualificationJson.text(root, "version", 40);
        if (!java.util.Set.of("local-raster-artifact-v1", "local-raster-artifact-v2")
                        .contains(version)
                || !"LOCAL_SYNTHETIC_ARTIFACT_V1"
                        .equals(QualificationJson.text(root, "origin", 40))) {
            throw new IllegalArgumentException("LOCAL_RASTER_ORIGIN");
        }
        final String source = QualificationJson.text(root, "source", 40);
        final String tenant = QualificationJson.text(root, "tenant", 40);
        if (version.equals("local-raster-artifact-v1")
                && (!source.equals("SYNTHETIC_ANALYTIC_LAB")
                        || !tenant.equals("SYNTHETIC_ANALYTIC_TENANT"))) {
            throw new IllegalArgumentException("LOCAL_RASTER_LEGACY_SCOPE");
        }
        final String contract = QualificationJson.text(root, "contract", 40);
        window = window(root);
        zone = ZoneId.of(QualificationJson.text(root, "zone", 40));
        if (!zone.getId().equals("America/Sao_Paulo") && !zone.getId().equals("UTC")) {
            throw new IllegalArgumentException("LOCAL_RASTER_ZONE");
        }
        revision = QualificationJson.number(root, "revision", 1, 100000);
        maximumCalls = QualificationJson.number(root, "maximumCalls", 1, 1000);
        maximumRows = QualificationJson.number(root, "maximumRows", 1, 100000);
        // Validate the existing lateral provenance contract even for a partial/empty artifact.
        new RasterGateway.Terminal(
                window, 0, 0, "synthetic-artifact-contract", source, tenant, contract);
        QualificationJson.array(root.path("pages"), 1, Math.min(maximumCalls, 127));
        final var directory = path.toAbsolutePath().getParent();
        for (final var entry : root.path("pages")) {
            QualificationJson.fields(
                    entry,
                    "windowStart",
                    "windowEndExclusive",
                    "complete",
                    "trips",
                    "stops",
                    "receipt",
                    "body",
                    "bindings");
            final var scope = window(entry);
            if (scope.start().isBefore(window.start())
                    || scope.endExclusive().isAfter(window.endExclusive())) {
                throw new IllegalArgumentException("LOCAL_RASTER_PAGE_SCOPE");
            }
            final var terminal =
                    new RasterGateway.Terminal(
                            scope,
                            QualificationJson.number(entry, "trips", 0, 500),
                            QualificationJson.number(entry, "stops", 0, 100000),
                            QualificationJson.text(entry, "receipt", 64),
                            source,
                            tenant,
                            contract);
            final var page =
                    new Page(
                            PinnedLocalJson.reference(directory, entry.path("body"), 2097152),
                            PinnedLocalJson.reference(directory, entry.path("bindings"), 524288),
                            QualificationJson.flag(entry, "complete") ? terminal : null);
            if (pages.put(scope, page) != null) {
                throw new IllegalArgumentException("LOCAL_RASTER_DUPLICATE_WINDOW");
            }
        }
        if (!pages.containsKey(window)) {
            throw new IllegalArgumentException("LOCAL_RASTER_ROOT_WINDOW");
        }
    }

    public RasterWindow window() {
        return window;
    }

    public ZoneId zone() {
        return zone;
    }

    public int revision() {
        return revision;
    }

    public int maximumCalls() {
        return maximumCalls;
    }

    public int maximumRows() {
        return maximumRows;
    }

    public Characterization characterize(final CancellationToken token)
            throws IOException, SQLException {
        final var observed = new Totals();
        inspect(window, token, observed);
        if (observed.calls != pages.size()) {
            throw new IllegalArgumentException("LOCAL_RASTER_UNUSED_WINDOW");
        }
        return new Characterization(
                observed.calls, observed.rows, observed.invalid, observed.complete);
    }

    private void inspect(
            final RasterWindow scope, final CancellationToken token, final Totals totals)
            throws IOException, SQLException {
        token.throwIfCancellationRequested();
        if (++totals.calls > maximumCalls) {
            throw new IllegalArgumentException("LOCAL_RASTER_CALL_BOUND");
        }
        final var page = page(scope);
        final var bindings = bindings(page);
        final int[] visited = {0};
        final var counts =
                page.body()
                        .consume(
                                body ->
                                        new RasterResponseParser(zone)
                                                .parse(
                                                        body,
                                                        new RasterResponseParser.Sink() {
                                                            @Override
                                                            public void trip(
                                                                    final int position,
                                                                    final RasterTripObservation
                                                                            value) {
                                                                token
                                                                        .throwIfCancellationRequested();
                                                                final var binding =
                                                                        required(
                                                                                bindings, position,
                                                                                0);
                                                                if (binding.stopKey() != null) {
                                                                    throw new IllegalArgumentException(
                                                                            "LOCAL_RASTER_TRIP_BINDING");
                                                                }
                                                                visited[0]++;
                                                                if (!value.valid()) {
                                                                    totals.invalid++;
                                                                }
                                                            }

                                                            @Override
                                                            public void stop(
                                                                    final int trip,
                                                                    final int position,
                                                                    final RasterStop value) {
                                                                token
                                                                        .throwIfCancellationRequested();
                                                                final var binding =
                                                                        required(
                                                                                bindings, trip,
                                                                                position);
                                                                if (binding.stopKey() == null
                                                                        || !binding.tripKey()
                                                                                .equals(
                                                                                        required(
                                                                                                        bindings,
                                                                                                        trip,
                                                                                                        0)
                                                                                                .tripKey())) {
                                                                    throw new IllegalArgumentException(
                                                                            "LOCAL_RASTER_STOP_PARENT");
                                                                }
                                                                visited[0]++;
                                                                if (!value.valid()) {
                                                                    totals.invalid++;
                                                                }
                                                            }
                                                        }));
        if (visited[0] != bindings.size()) {
            throw new IllegalArgumentException("LOCAL_RASTER_EXCESS_BINDING");
        }
        if (counts.trips() == 500) {
            if (scope.days() == 1) {
                throw new IllegalArgumentException("RAS_CAP_MINIMUM_WINDOW");
            }
            final var middle = scope.start().plusDays(scope.days() / 2);
            inspect(new RasterWindow(scope.start(), middle), token, totals);
            inspect(new RasterWindow(middle, scope.endExclusive()), token, totals);
            return;
        }
        totals.rows += counts.trips() + counts.stops();
        if (totals.rows > maximumRows) {
            throw new IllegalArgumentException("LOCAL_RASTER_ROW_BOUND");
        }
        totals.complete &=
                page.terminal() != null
                        && page.terminal().trips() == counts.trips()
                        && page.terminal().stops() == counts.stops();
    }

    public RasterGateway gateway() {
        return new RasterGateway() {
            private RasterWindow current;
            private Map<Position, RasterBinding> selected = Map.of();

            @Override
            public Response fetch(final RasterWindow scope) throws IOException {
                final var page = page(scope);
                selected = Map.of();
                current = null;
                final var response =
                        page.body().consume(body -> new Response(body, page.terminal()));
                selected = bindings(page);
                current = scope;
                return response;
            }

            @Override
            public RasterBinding binding(final RasterWindow scope, final int trip, final int stop) {
                if (!scope.equals(current)) {
                    throw new IllegalArgumentException("LOCAL_RASTER_BINDING_WINDOW");
                }
                return required(selected, trip, stop);
            }
        };
    }

    private Page page(final RasterWindow scope) {
        final var page = pages.get(scope);
        if (page == null) {
            throw new IllegalArgumentException("LOCAL_RASTER_WINDOW_MISSING");
        }
        return page;
    }

    private Map<Position, RasterBinding> bindings(final Page page) throws IOException {
        final var rows = page.bindings().read();
        QualificationJson.array(rows, 0, 2048);
        final var result = new LinkedHashMap<Position, RasterBinding>();
        for (final var row : rows) {
            QualificationJson.fields(
                    row,
                    "tripPosition",
                    "stopPosition",
                    "tripKey",
                    "stopKey",
                    "revision",
                    "active",
                    "reactivate",
                    "evidence",
                    "source",
                    "tenant",
                    "contract");
            final var position =
                    new Position(
                            QualificationJson.number(row, "tripPosition", 1, 500),
                            QualificationJson.number(row, "stopPosition", 0, 500));
            final var binding =
                    new RasterBinding(
                            QualificationJson.text(row, "tripKey", 64),
                            row.path("stopKey").isNull()
                                    ? null
                                    : QualificationJson.text(row, "stopKey", 64),
                            QualificationJson.number(row, "revision", 1, 100000),
                            QualificationJson.flag(row, "active"),
                            QualificationJson.flag(row, "reactivate"),
                            QualificationJson.text(row, "evidence", 64),
                            QualificationJson.text(row, "source", 40),
                            QualificationJson.text(row, "tenant", 40),
                            QualificationJson.text(row, "contract", 40));
            if (binding.revision() != revision || result.put(position, binding) != null) {
                throw new IllegalArgumentException("LOCAL_RASTER_BINDING_REVISION_OR_DUPLICATE");
            }
        }
        return Map.copyOf(result);
    }

    private static RasterBinding required(
            final Map<Position, RasterBinding> bindings, final int trip, final int stop) {
        final var result = bindings.get(new Position(trip, stop));
        if (result == null) {
            throw new IllegalArgumentException("LOCAL_RASTER_BINDING_MISSING");
        }
        return result;
    }

    private static RasterWindow window(final JsonNode node) {
        return new RasterWindow(
                LocalDate.parse(QualificationJson.text(node, "windowStart", 10)),
                LocalDate.parse(QualificationJson.text(node, "windowEndExclusive", 10)));
    }

    private record Position(int trip, int stop) {}

    private record Page(
            PinnedLocalJson body, PinnedLocalJson bindings, RasterGateway.Terminal terminal) {}

    private static final class Totals {
        private int calls;
        private long rows;
        private long invalid;
        private boolean complete = true;
    }

    public record Characterization(int calls, long rows, long invalid, boolean completeSynthetic) {
        public boolean executable() {
            return completeSynthetic && invalid == 0;
        }
    }
}
