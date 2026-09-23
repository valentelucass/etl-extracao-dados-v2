package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import br.com.esl.etl.v2.plataforma.expansao.ExpansionInvoiceFact;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionKey;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionProjection;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionRevenueFact;
import java.math.BigInteger;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** Fixed projection contracts, keyset pagination and a bounded result list. */
public final class JdbcExpansionQueries {
    private final DataSource source;

    public List<ExpansionRevenueFact> revenueFactsPage(
            final UUID run, final long after, final int maximum) throws SQLException {
        if (after < 0 || maximum < 1 || maximum > 100) {
            throw new IllegalArgumentException("EXP_QUERY_BOUND");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(?) * FROM mart.expansion_lab_revenue WHERE run_id=? AND "
                                        + "dependency_id>? AND disposition<>'INACTIVE' ORDER BY dependency_id")) {
            sql.setQueryTimeout(10);
            sql.setInt(1, maximum);
            sql.setString(2, run.toString());
            sql.setLong(3, after);
            try (var r = sql.executeQuery()) {
                final var result = new ArrayList<ExpansionRevenueFact>();
                while (r.next()) {
                    if (result.size() == maximum) {
                        throw new SQLException("EXP_QUERY_RESULT_BOUND");
                    }
                    result.add(
                            new ExpansionRevenueFact(
                                    r.getLong("dependency_id"),
                                    r.getString("source_key"),
                                    r.getLong("freight_stage_id"),
                                    nullableLong(r, "term_id"),
                                    nullableInteger(r, "term_revision"),
                                    date(r, "original_reference_date"),
                                    date(r, "billing_reference_date"),
                                    r.getString("branch_code"),
                                    nullableLong(r, "calendar_release"),
                                    nullableLong(r, "branch_release"),
                                    nullableLong(r, "payer_release"),
                                    r.getBigDecimal("source_value"),
                                    r.getBigDecimal("revenue_value"),
                                    r.getString("currency"),
                                    r.getString("unit"),
                                    r.getBoolean("cancelled"),
                                    r.getString("cancellation_provenance"),
                                    r.getBoolean("billing_block"),
                                    nullableBoolean(r, "courtesy"),
                                    nullableBoolean(r, "eligible"),
                                    nullableBoolean(r, "active"),
                                    nullableInteger(r, "volumes"),
                                    r.getString("volume_provenance"),
                                    nullableLong(r, "location_stage_id"),
                                    r.getString("disposition"),
                                    r.getLong("invoice_count"),
                                    r.getLong("document_count"),
                                    r.getInt("reference_revision"),
                                    date(r, "business_date"),
                                    UUID.fromString(r.getString("last_receipt"))));
                }
                return List.copyOf(result);
            }
        }
    }

    private static Boolean nullableBoolean(final ResultSet r, final String name)
            throws SQLException {
        final boolean value = r.getBoolean(name);
        return r.wasNull() ? null : value;
    }

    public List<ExpansionInvoiceFact> invoiceFactsPage(
            final UUID run, final long after, final int maximum) throws SQLException {
        if (after < 0 || maximum < 1 || maximum > 100) {
            throw new IllegalArgumentException("EXP_QUERY_BOUND");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(?) * FROM mart.expansion_lab_invoice WHERE run_id=? AND root_id>? AND "
                                        + "disposition<>'INACTIVE' ORDER BY root_id")) {
            sql.setQueryTimeout(10);
            sql.setInt(1, maximum);
            sql.setString(2, run.toString());
            sql.setLong(3, after);
            try (var r = sql.executeQuery()) {
                final var result = new ArrayList<ExpansionInvoiceFact>();
                while (r.next()) {
                    if (result.size() == maximum) {
                        throw new SQLException("EXP_QUERY_RESULT_BOUND");
                    }
                    final boolean invoice = r.getBoolean("has_invoice");
                    final Boolean hasInvoice = r.wasNull() ? null : invoice;
                    result.add(
                            new ExpansionInvoiceFact(
                                    r.getLong("root_id"),
                                    date(r, "issue_date"),
                                    date(r, "due_date"),
                                    date(r, "paid_date"),
                                    date(r, "base_date"),
                                    date(r, "monthly_reference_date"),
                                    r.getString("client_key"),
                                    r.getString("client_provenance"),
                                    hasInvoice,
                                    r.getString("process_state"),
                                    r.getString("payment_state"),
                                    nullableInteger(r, "days_past_due"),
                                    r.getBigDecimal("operational_value"),
                                    r.getString("currency"),
                                    r.getString("unit"),
                                    r.getString("disposition"),
                                    r.getLong("component_count"),
                                    r.getLong("document_count"),
                                    r.getLong("freight_count"),
                                    r.getInt("reference_revision"),
                                    nullableLong(r, "label_release"),
                                    date(r, "business_date"),
                                    UUID.fromString(r.getString("last_receipt"))));
                }
                return List.copyOf(result);
            }
        }
    }

    public JdbcExpansionQueries(final DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public enum Vertical {
        CAP,
        FAT,
        INV,
        SIN
    }

    public List<ExpansionProjection> detailPage(
            final UUID run,
            final Vertical vertical,
            final int referenceRevision,
            final long after,
            final int maximum)
            throws SQLException {
        if (referenceRevision < 1
                || referenceRevision > 1000
                || after < 0
                || maximum < 1
                || maximum > 100) {
            throw new IllegalArgumentException("EXP_QUERY_BOUND");
        }
        final String expression =
                switch (vertical) {
                    case CAP -> "pub.ufn_expansion_cap(?,?)";
                    case FAT -> "pub.ufn_expansion_fat(?,?)";
                    case INV -> "pub.ufn_expansion_inv(?)";
                    case SIN -> "pub.ufn_expansion_sin(?)";
                };
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "SELECT TOP(?) * FROM "
                                        + expression
                                        + " WHERE component_id>? ORDER BY component_id")) {
            sql.setQueryTimeout(10);
            sql.setInt(1, maximum);
            sql.setString(2, run.toString());
            if (vertical == Vertical.CAP || vertical == Vertical.FAT) {
                sql.setInt(3, referenceRevision);
                sql.setLong(4, after);
            } else {
                sql.setLong(3, after);
            }
            try (var rows = sql.executeQuery()) {
                final var result = new ArrayList<ExpansionProjection>();
                while (rows.next()) {
                    if (result.size() == maximum) {
                        throw new SQLException("EXP_QUERY_RESULT_BOUND");
                    }
                    result.add(map(rows, vertical));
                }
                return List.copyOf(result);
            }
        }
    }

    private static ExpansionProjection map(final ResultSet r, final Vertical vertical)
            throws SQLException {
        final var lineage =
                new ExpansionProjection.Lineage(
                        r.getLong("component_id"),
                        r.getLong("root_id"),
                        UUID.fromString(r.getString("execution_id")),
                        r.getLong("occurrence"),
                        key(r, "root"),
                        key(r, "part"),
                        key(r, "component"),
                        r.getString("currency"),
                        r.getString("unit"),
                        r.getBoolean("unresolved_conflict"));
        return switch (vertical) {
            case CAP ->
                    new ExpansionProjection.Payable(
                            lineage,
                            r.getBigDecimal("root_amount"),
                            r.getBigDecimal("part_amount"),
                            r.getBigDecimal("allocation_amount"),
                            r.getBoolean("additive_allocation"),
                            r.getString("payment_state"),
                            r.getString("paid_label"),
                            r.getString("reconciliation_label"),
                            r.getString("type_raw"),
                            r.getString("type_label"),
                            r.getString("classification_raw"),
                            r.getString("classification_label"),
                            nullableLong(r, "label_release"),
                            date(r, "issue_date"));
            case FAT ->
                    new ExpansionProjection.InvoiceCustomer(
                            lineage,
                            r.getString("line_candidate"),
                            r.getString("line_wire"),
                            r.getString("document_raw"),
                            r.getBoolean("has_invoice"),
                            r.getString("cte_number"),
                            r.getString("nfse_number"),
                            r.getString("nfse_alias"),
                            r.getString("official_number"),
                            r.getString("fiscal_state"),
                            r.getString("nfse_series_state"),
                            r.getString("cte_status_raw"),
                            r.getString("cte_status_label"),
                            nullableLong(r, "label_release"),
                            date(r, "issue_date"),
                            date(r, "due_date"),
                            date(r, "paid_date"),
                            r.getBigDecimal("title_value"),
                            r.getBigDecimal("freight_value"),
                            r.getString("client_key"),
                            r.getString("client_provenance"),
                            r.getString("freight_type"));
            case INV ->
                    new ExpansionProjection.Inventory(
                            lineage,
                            r.getString("sequence_candidate"),
                            r.getString("sequence_wire"),
                            r.getString("freight_candidate"),
                            r.getBoolean("proof_attached"),
                            r.getBigDecimal("invoices_value"),
                            integer(r, "volumes"),
                            r.getBigDecimal("real_weight"),
                            r.getBigDecimal("taxed_weight"),
                            r.getBigDecimal("cubic_volume"),
                            nullableInteger(r, "mapping_count"),
                            r.getString("mapping_presence"),
                            r.getString("status_raw"));
            case SIN ->
                    new ExpansionProjection.InsuranceClaim(
                            lineage,
                            r.getString("sequence_candidate"),
                            r.getString("sequence_wire"),
                            r.getString("freight_candidate"),
                            r.getString("occurrence_candidate"),
                            r.getBigDecimal("insurance_claim_total"),
                            r.getBigDecimal("invoices_value"),
                            integer(r, "invoices_volumes"),
                            r.getBigDecimal("invoices_weight"),
                            date(r, "opening_at_date"),
                            date(r, "occurrence_at_date"),
                            time(r, "occurrence_time_nano"),
                            date(r, "finished_at_date"),
                            time(r, "finished_time_nano"),
                            r.getString("icm_ttt_dealing_type"),
                            r.getString("icm_ttt_solution_type"));
        };
    }

    private static ExpansionKey key(final ResultSet r, final String prefix) throws SQLException {
        return new ExpansionKey(
                ExpansionKey.Kind.valueOf(r.getString(prefix + "_type")),
                r.getString(prefix + "_key"));
    }

    private static LocalDate date(final ResultSet r, final String name) throws SQLException {
        final var value = r.getDate(name);
        return value == null ? null : value.toLocalDate();
    }

    private static Long nullableLong(final ResultSet r, final String name) throws SQLException {
        final long v = r.getLong(name);
        return r.wasNull() ? null : v;
    }

    private static Integer nullableInteger(final ResultSet r, final String name)
            throws SQLException {
        final int v = r.getInt(name);
        return r.wasNull() ? null : v;
    }

    private static LocalTime time(final ResultSet r, final String name) throws SQLException {
        final Long v = nullableLong(r, name);
        return v == null ? null : LocalTime.ofNanoOfDay(v);
    }

    private static BigInteger integer(final ResultSet r, final String name) throws SQLException {
        final String v = r.getString(name);
        return v == null ? null : new BigInteger(v);
    }
}
