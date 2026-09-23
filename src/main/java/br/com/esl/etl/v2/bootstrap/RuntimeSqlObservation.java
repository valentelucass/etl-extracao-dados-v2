package br.com.esl.etl.v2.bootstrap;

import java.sql.SQLException;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;

/** One occurrence, measured SQL values only. Missing measures remain UNKNOWN. */
final class RuntimeSqlObservation {
    private RuntimeSqlObservation() {}

    static String read(final DataSource source, final UUID execution) {
        try (var connection = source.getConnection();
                var query = connection.prepareCall("{call ctl.usp_runtime_observation(?)}")) {
            query.setQueryTimeout(20);
            query.setFetchSize(2);
            query.setString(1, execution.toString());
            final String summary;
            try (var row = query.executeQuery()) {
                if (!row.next() || row.getMetaData().getColumnCount() != 10) {
                    throw new SQLException("RUNTIME_OBSERVATION_SHAPE");
                }
                final String state = row.getString("state"), health = row.getString("health");
                final String quality = row.getString("quality");
                if (!Set.of(
                                        "NOT_FOUND",
                                        "PLANNED",
                                        "EXTRACTING",
                                        "EXTRACTED",
                                        "STAGED",
                                        "PROMOTED",
                                        "RECONCILED",
                                        "PUBLISHED",
                                        "FAILED",
                                        "CANCELLED",
                                        "BLOCKED",
                                        "SKIPPED",
                                        "NOT_APPLICABLE",
                                        "DEGRADED")
                                .contains(state)
                        || !Set.of("UNKNOWN", "UP", "DEGRADED").contains(health)
                        || !Set.of("ABSENT", "PASSED", "FAILED", "INCOMPLETE").contains(quality)
                        || row.getTimestamp("observed_at_utc") == null) {
                    throw new SQLException("RUNTIME_OBSERVATION_VALUES");
                }
                summary =
                        "RUNTIME_SQL_OBSERVATION state="
                                + state
                                + " occurrence_health="
                                + health
                                + " quality="
                                + quality
                                + " pages="
                                + count(row, "pages", false)
                                + " physical_rows="
                                + count(row, "physical_rows", false)
                                + " audited_response_bytes="
                                + count(row, "response_bytes", false)
                                + " candidate_rows="
                                + count(row, "candidate_rows", true)
                                + " publications="
                                + count(row, "publications", false)
                                + " duration_ms="
                                + count(row, "duration_milliseconds", true)
                                + " source_lag=UNKNOWN watermark=UNKNOWN wire_response_bytes=UNKNOWN";
                if (row.next()) {
                    throw new SQLException("RUNTIME_OBSERVATION_DUPLICATED");
                }
            }
            if (query.getMoreResults() || query.getUpdateCount() != -1) {
                throw new SQLException("RUNTIME_OBSERVATION_ACK");
            }
            return summary;
        } catch (final SQLException failure) {
            throw new IllegalStateException("RUNTIME_OBSERVATION_UNCONFIRMED", failure);
        }
    }

    private static String count(
            final java.sql.ResultSet row, final String field, final boolean optional)
            throws SQLException {
        final long value = row.getLong(field);
        if (row.wasNull()) {
            if (optional) {
                return "UNKNOWN";
            }
            throw new SQLException("RUNTIME_OBSERVATION_NULL");
        }
        if (value < 0) {
            throw new SQLException("RUNTIME_OBSERVATION_NEGATIVE");
        }
        return Long.toString(value);
    }
}
