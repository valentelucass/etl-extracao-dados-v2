package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

/** Synthetic typed root projection. Never requests or consumes a GraphQL promotion permit. */
public final class JdbcColetaTemporalLaboratory {
    private final ColetaTemporalLaboratorySession dataSource;
    private final boolean enabled;

    public JdbcColetaTemporalLaboratory(
            final ColetaTemporalLaboratorySession dataSource, final boolean enabled) {
        this.dataSource = Objects.requireNonNull(dataSource);
        this.enabled = enabled;
    }

    public void completeDataExport(final UUID execution, final CancellationToken cancellation) {
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (!enabled) {
            throw new IllegalStateException("COL_LAB_DISABLED");
        }
        try (var connection = dataSource.getConnection();
                var statement =
                        connection.prepareCall(
                                "{call stg.usp_complete_coleta_temporal_data_export(?)}")) {
            statement.setQueryTimeout(20);
            statement.setString(1, Objects.requireNonNull(execution).toString());
            statement.executeUpdate();
            cancellation.throwIfCancellationRequested();
        } catch (final SQLException failure) {
            throw new StagingPersistenceException("COL_LAB_DATA_CAPTURE_INCOMPLETE", failure);
        }
    }

    public Result consume(
            final UUID dataExecution,
            final UUID referenceExecution,
            final CancellationToken cancellation) {
        Objects.requireNonNull(dataExecution);
        Objects.requireNonNull(referenceExecution);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (!enabled) {
            throw new IllegalStateException("COL_LAB_DISABLED");
        }
        try (var connection = dataSource.getConnection()) {
            if (connection.getAutoCommit()) {
                throw new IllegalStateException("COL_LAB_OUTER_TRANSACTION_REQUIRED");
            }
            try (var statement =
                    connection.prepareCall(
                            "{call core.usp_consume_coleta_temporal_laboratory(?,?,?)}")) {
                statement.setQueryTimeout(20);
                statement.setString(1, dataExecution.toString());
                statement.setString(2, referenceExecution.toString());
                statement.setBoolean(3, enabled);
                try (var rows = statement.executeQuery()) {
                    if (!rows.next()) {
                        throw new SQLException("COL_LAB_RECONCILIATION_MISSING");
                    }
                    final var result =
                            new Result(
                                    rows.getLong("considered_roots"),
                                    rows.getLong("inserted_roots"),
                                    rows.getLong("updated_roots"),
                                    rows.getLong("noop_roots"),
                                    rows.getBoolean("replay"));
                    if (rows.next()) {
                        throw new SQLException("COL_LAB_RECONCILIATION_DUPLICATED");
                    }
                    cancellation.throwIfCancellationRequested();
                    return result;
                }
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback();
                } catch (final SQLException rollbackFailure) {
                    failure.addSuppressed(rollbackFailure);
                }
                throw failure;
            }
        } catch (final SQLException failure) {
            throw new StagingPersistenceException("COL_LAB_CONSUMPTION_FAILED", failure);
        }
    }

    public record Result(long considered, long inserted, long updated, long noop, boolean replay) {
        public Result {
            if (considered < 0
                    || inserted < 0
                    || updated < 0
                    || noop < 0
                    || considered != Math.addExact(Math.addExact(inserted, updated), noop)) {
                throw new IllegalArgumentException("COL_LAB_RECONCILIATION_INVALID");
            }
        }
    }
}
