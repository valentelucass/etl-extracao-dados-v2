package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticFreightRelationBinding;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Set-based relation inputs and an explicit synthetic completeness declaration. */
public final class JdbcAnalyticFreightRelations {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticFreightRelations(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public int bindBatch(
            final UUID run,
            final List<AnalyticFreightRelationBinding> bindings,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (bindings == null || bindings.isEmpty() || bindings.size() > 64) {
            throw new IllegalArgumentException("ANA_FREIGHT_RELATION_BATCH_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("kind", Types.VARCHAR);
        table.addColumnMetadata("origin_key", Types.NVARCHAR);
        table.addColumnMetadata("origin_execution", Types.VARCHAR);
        table.addColumnMetadata("freight_key", Types.NVARCHAR);
        table.addColumnMetadata("freight_execution", Types.VARCHAR);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("active", Types.BIT);
        table.addColumnMetadata("previous_freight_key", Types.NVARCHAR);
        for (final var binding : bindings) {
            Objects.requireNonNull(binding);
            table.addRow(
                    binding.kind().name(),
                    binding.originKey(),
                    binding.originExecution().toString(),
                    binding.freightKey(),
                    binding.freightExecution().toString(),
                    binding.revision(),
                    binding.active(),
                    binding.previousFreightKey());
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC stg.usp_bind_analytic_freight_relations ?,?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setStructured(2, "stg.analytic_lab_freight_relation_batch", table);
                cancellation.throwIfCancellationRequested();
                try (var row = sql.executeQuery()) {
                    if (!row.next() || row.getInt(1) != bindings.size() || row.next()) {
                        throw new SQLException("ANA_FREIGHT_RELATION_RECEIPT");
                    }
                    cancellation.throwIfCancellationRequested();
                    return bindings.size();
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

    public long sealComposition(
            final UUID run,
            final String manifestKey,
            final UUID manifestExecution,
            final int revision,
            final int expectedDirectFreights)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(manifestExecution);
        if (manifestKey == null
                || !manifestKey.matches("INTEGER:[1-9][0-9]{0,18}")
                || revision < 1
                || revision > 100000
                || expectedDirectFreights < 0
                || expectedDirectFreights > 100000) {
            throw new IllegalArgumentException("ANA_MANIFEST_COMPOSITION_BOUND");
        }
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_seal_analytic_manifest_composition ?,?,?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, manifestKey);
            sql.setString(3, manifestExecution.toString());
            sql.setInt(4, revision);
            sql.setInt(5, expectedDirectFreights);
            try (var row = sql.executeQuery()) {
                if (!row.next()) {
                    throw new SQLException("ANA_MANIFEST_COMPOSITION_RECEIPT");
                }
                final long identifier = row.getLong(1);
                if (identifier < 1 || row.next()) {
                    throw new SQLException("ANA_MANIFEST_COMPOSITION_RECEIPT");
                }
                return identifier;
            }
        }
    }
}
