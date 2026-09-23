package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.controle.ExecutionMode;
import br.com.esl.etl.v2.plataforma.fonte.raster.RasterGateway;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * SQL-authoritative capture and reconciliation; all domain DML belongs to the caller transaction.
 */
public final class JdbcRasterLaboratory {
    public static final String SOURCE = "SYNTHETIC_ANALYTIC_LAB";
    public static final String TENANT = "SYNTHETIC_ANALYTIC_TENANT";
    public static final String VERSION = "synthetic-analytic-v1";
    private final DataSource source;
    private final Clock clock;

    public JdbcRasterLaboratory(final DataSource source, final Clock clock) {
        this.source = Objects.requireNonNull(source);
        this.clock = Objects.requireNonNull(clock);
    }

    public void start(
            final UUID run,
            final LocalDate start,
            final LocalDate endExclusive,
            final ZoneId zone,
            final int maximumRows,
            final int maximumPages)
            throws SQLException {
        start(
                run,
                start,
                endExclusive,
                zone,
                maximumRows,
                maximumPages,
                new br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope(SOURCE, TENANT));
    }

    public void start(
            final UUID run,
            final LocalDate start,
            final LocalDate endExclusive,
            final ZoneId zone,
            final int maximumRows,
            final int maximumPages,
            final br.com.esl.etl.v2.plataforma.fonte.SyntheticSourceScope scope)
            throws SQLException {
        if (start == null
                || endExclusive == null
                || !start.isBefore(endExclusive)
                || maximumRows < 1
                || maximumRows > 100000
                || maximumPages < 1
                || maximumPages > 10000
                || !(zone.equals(ZoneId.of("America/Sao_Paulo"))
                        || zone.equals(ZoneId.of("UTC")))) {
            throw new IllegalArgumentException("ANA_RUN_SCOPE_BUDGET");
        }
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "INSERT ctl.analytic_lab_run(run_id,source_instance,tenant_scope,contract_version,"
                                        + "window_start,window_end_exclusive,zone_id,maximum_rows,maximum_pages,created_at)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            statement.setQueryTimeout(10);
            statement.setString(1, run.toString());
            statement.setString(2, scope.source());
            statement.setString(3, scope.tenant());
            statement.setString(4, VERSION);
            statement.setObject(5, start);
            statement.setObject(6, endExclusive);
            statement.setString(7, zone.getId());
            statement.setInt(8, maximumRows);
            statement.setInt(9, maximumPages);
            statement.setTimestamp(
                    10,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            statement.executeUpdate();
        }
    }

    public void begin(
            final UUID run,
            final UUID capture,
            final LocalDate start,
            final LocalDate endExclusive,
            final ExecutionMode mode)
            throws SQLException {
        if (mode != ExecutionMode.BOOTSTRAP
                && mode != ExecutionMode.INCREMENTAL
                && mode != ExecutionMode.BACKFILL
                && mode != ExecutionMode.REPLAY) {
            throw new IllegalArgumentException("RAS_MODE");
        }
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "INSERT ctl.analytic_raster_capture(capture_id,run_id,start_date,end_exclusive,mode,so"
                                        + "urce_instance,"
                                        + "tenant_scope,contract_version,state,extracted_at)"
                                        + " SELECT ?,run_id,?,?,?,source_instance,tenant_scope,"
                                        + "contract_version,'CAPTURING',? FROM ctl.analytic_lab_run"
                                        + " WHERE run_id=? AND window_start<=? AND window_end_exclusive>=?")) {
            statement.setQueryTimeout(10);
            statement.setString(1, capture.toString());
            statement.setObject(2, start);
            statement.setObject(3, endExclusive);
            statement.setString(4, mode.name());
            statement.setTimestamp(
                    5,
                    Timestamp.from(clock.instant()),
                    java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")));
            statement.setString(6, run.toString());
            statement.setObject(7, start);
            statement.setObject(8, endExclusive);
            if (statement.executeUpdate() != 1) {
                throw new SQLException("RAS_CAPTURE_SCOPE");
            }
        }
    }

    public void terminal(final UUID capture, final int ordinal, final RasterGateway.Terminal proof)
            throws SQLException {
        Objects.requireNonNull(proof);
        if (ordinal < 1 || ordinal > 10000) {
            throw new IllegalArgumentException("RAS_TERMINAL_ORDINAL");
        }
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement(
                                "INSERT ctl.analytic_raster_terminal_window(capture_id,ordinal,start_date,end_exclusive,"
                                        + "expected_roots,expected_stops,receipt,source_instance,tenant_scope,contract_version)"
                                        + " VALUES(?,?,?,?,?,?,?,?,?,?)")) {
            statement.setQueryTimeout(10);
            statement.setString(1, capture.toString());
            statement.setInt(2, ordinal);
            statement.setObject(3, proof.window().start());
            statement.setObject(4, proof.window().endExclusive());
            statement.setLong(5, proof.trips());
            statement.setLong(6, proof.stops());
            statement.setString(7, proof.receipt());
            statement.setString(8, proof.source());
            statement.setString(9, proof.tenant());
            statement.setString(10, proof.contract());
            statement.executeUpdate();
        }
    }

    public void seal(
            final UUID capture, final long expectedTrips, final long expectedStops, final int pages)
            throws SQLException {
        if (expectedTrips < 0 || expectedStops < 0 || pages < 1) {
            throw new IllegalArgumentException("RAS_TERMINAL_RECEIPT");
        }
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement("EXEC ctl.usp_seal_analytic_raster ?,?,?,?")) {
            statement.setQueryTimeout(10);
            statement.setString(1, capture.toString());
            statement.setLong(2, expectedTrips);
            statement.setLong(3, expectedStops);
            statement.setInt(4, pages);
            statement.execute();
        }
    }

    public Receipt apply(final UUID run, final UUID capture) throws SQLException {
        try (var connection = source.getConnection();
                var statement =
                        connection.prepareStatement("EXEC core.usp_apply_analytic_raster ?,?")) {
            statement.setQueryTimeout(20);
            statement.setString(1, run.toString());
            statement.setString(2, capture.toString());
            try (var row = statement.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("RAS_RECEIPT_MISSING");
                }
                final var result =
                        new Receipt(
                                row.getLong(1),
                                row.getLong(2),
                                row.getLong(3),
                                row.getLong(4),
                                row.getLong(5),
                                row.getLong(6),
                                row.getLong(7),
                                row.getLong(8),
                                row.getString(9));
                if (row.next()) {
                    throw new SQLException("RAS_RECEIPT_MULTIPLIED");
                }
                return result;
            }
        }
    }

    public record Receipt(
            long trips,
            long stops,
            long applied,
            long noops,
            long stale,
            long duplicates,
            long quarantine,
            long unbound,
            String state) {
        public Receipt {
            if (trips + stops != applied + noops + stale + duplicates + quarantine + unbound) {
                throw new IllegalArgumentException("RAS_EQUATION");
            }
        }

        public boolean complete() {
            return "APPLIED".equals(state);
        }
    }
}
