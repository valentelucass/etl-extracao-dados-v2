package br.com.esl.etl.v2.plataforma.persistencia.controle;

import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPlanner;
import br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalPolicy;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;

/** Bounded SQL summaries, no history materialization in the JVM and no implicit retry. */
public final class JdbcSqlServerTemporalPlan
        implements br.com.esl.etl.v2.plataforma.orquestracao.RuntimeTemporalStore {
    private final DataSource dataSource;

    public JdbcSqlServerTemporalPlan(final DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource);
    }

    public int persist(
            final UUID plan,
            final String namespaceHash,
            final RuntimeTemporalPolicy policy,
            final RuntimeTemporalPlanner.Result result) {
        requireHash(namespaceHash);
        Objects.requireNonNull(plan);
        Objects.requireNonNull(policy);
        Objects.requireNonNull(result);
        if (result.windows().isEmpty()) {
            return 0;
        }
        if (result.windows().size() > policy.maximumBacklog()) {
            throw new IllegalArgumentException("TEMPORAL_PLAN_LIMIT");
        }
        final var windows =
                com.fasterxml.jackson.databind.node.JsonNodeFactory.instance.arrayNode();
        for (final var window : result.windows()) {
            final UUID execution =
                    UUID.nameUUIDFromBytes(
                            (namespaceHash
                                            + "|"
                                            + window.partitionStart()
                                            + "|"
                                            + window.endExclusive())
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            windows.addObject()
                    .put("execution", execution.toString())
                    .put("start", window.partitionStart().toString())
                    .put("endExclusive", window.endExclusive().toString())
                    .put("extractionStart", window.extractionStart().toString())
                    .put("due", window.dueAt().toString())
                    .put("deadline", window.deadlineAt().toString());
        }
        try (var connection = dataSource.getConnection();
                var query =
                        connection.prepareCall(
                                "{call ctl.usp_runtime_temporal_plan(?,?,?,?,?,?)}")) {
            query.setQueryTimeout(20);
            query.setString(1, plan.toString());
            query.setString(2, namespaceHash);
            query.setNString(3, policy.version());
            query.setString(4, policy.fingerprint().sha256());
            query.setNString(5, policy.material());
            query.setNString(6, windows.toString());
            try (var row = query.executeQuery()) {
                if (!row.next() || row.getLong(1) != result.windows().size() || row.next()) {
                    throw new SQLException("TEMPORAL_PLAN_RECEIPT_INVALID");
                }
            }
            if (query.getMoreResults() || query.getUpdateCount() != -1) {
                throw new SQLException("TEMPORAL_PLAN_ACK_INVALID");
            }
            return result.windows().size();
        } catch (final SQLException failure) {
            throw new IllegalStateException("TEMPORAL_PLAN_UNCONFIRMED", failure);
        }
    }

    public List<Gap> readGapPage(
            final String namespaceHash, final int maximum, final Instant after) {
        requireHash(namespaceHash);
        Objects.requireNonNull(after);
        if (maximum < 1 || maximum > 64) {
            throw new IllegalArgumentException("TEMPORAL_RECONCILIATION_LIMIT");
        }
        try (var connection = dataSource.getConnection();
                var query = connection.prepareCall("{call ctl.usp_runtime_temporal_gaps(?,?,?)}")) {
            query.setQueryTimeout(20);
            query.setFetchSize(maximum + 1);
            query.setString(1, namespaceHash);
            query.setInt(2, maximum);
            query.setTimestamp(
                    3, Timestamp.from(after), Calendar.getInstance(TimeZone.getTimeZone("UTC")));
            final var gaps = new ArrayList<Gap>();
            try (var row = query.executeQuery()) {
                while (row.next()) {
                    if (gaps.size() == maximum) {
                        throw new SQLException("TEMPORAL_RECONCILIATION_OVERFLOW");
                    }
                    final var calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
                    final var gap =
                            new Gap(
                                    UUID.fromString(row.getString("execution_id")),
                                    row.getTimestamp("partition_start_utc", calendar).toInstant(),
                                    row.getTimestamp("partition_end_exclusive_utc", calendar)
                                            .toInstant(),
                                    row.getString("state"));
                    if (!gaps.isEmpty()) {
                        final var previous = gaps.get(gaps.size() - 1);
                        // SQL sorts the namespace by start/end. Different plans may overlap;
                        // contiguity is checked by the coordinator after selecting its plan.
                        if (gap.start().isBefore(previous.start())
                                || gap.start().equals(previous.start())
                                        && !gap.endExclusive().isAfter(previous.endExclusive())) {
                            throw new SQLException("TEMPORAL_RECONCILIATION_ORDER");
                        }
                    }
                    gaps.add(gap);
                }
            }
            return List.copyOf(gaps);
        } catch (final SQLException failure) {
            throw new IllegalStateException("TEMPORAL_RECONCILIATION_UNAVAILABLE", failure);
        }
    }

    private static void requireHash(final String value) {
        if (value == null || !value.matches("[a-f0-9]{64}")) {
            throw new IllegalArgumentException("TEMPORAL_NAMESPACE_REQUIRED");
        }
    }
}
