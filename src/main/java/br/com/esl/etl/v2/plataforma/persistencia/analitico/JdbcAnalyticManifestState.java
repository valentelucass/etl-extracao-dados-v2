package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticManifestState;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Bounded TVP for explicit manifest activation, exclusion and authorized reappearance. */
public final class JdbcAnalyticManifestState {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticManifestState(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public int bindBatch(
            final UUID run,
            final List<AnalyticManifestState> states,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (states == null || states.isEmpty() || states.size() > 64) {
            throw new IllegalArgumentException("ANA_MANIFEST_STATE_BATCH_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("source_key", Types.NVARCHAR);
        table.addColumnMetadata("source_execution", Types.VARCHAR);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("active", Types.BIT);
        table.addColumnMetadata("reactivate", Types.BIT);
        table.addColumnMetadata("evidence", Types.VARCHAR);
        for (final var state : states) {
            Objects.requireNonNull(state);
            table.addRow(
                    state.sourceKey(),
                    state.sourceExecution().toString(),
                    state.revision(),
                    state.active(),
                    state.reactivate(),
                    "synthetic-manifest-state-v1");
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC ref.usp_bind_analytic_manifest_state ?,?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setStructured(2, "ref.analytic_lab_manifest_state_batch", table);
                cancellation.throwIfCancellationRequested();
                try (var row = sql.executeQuery()) {
                    if (!row.next() || row.getInt(1) != states.size() || row.next()) {
                        throw new SQLException("ANA_MANIFEST_STATE_RECEIPT");
                    }
                    cancellation.throwIfCancellationRequested();
                    return states.size();
                }
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        }
    }
}
