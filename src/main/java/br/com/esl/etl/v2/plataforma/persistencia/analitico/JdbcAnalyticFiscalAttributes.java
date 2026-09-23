package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticFiscalAttribute;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** One bounded TVP, immutable revisions and capture-scoped series; rollback-only laboratory. */
public final class JdbcAnalyticFiscalAttributes {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticFiscalAttributes(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public int bindBatch(
            final UUID run,
            final List<AnalyticFiscalAttribute> batch,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (batch == null || batch.isEmpty() || batch.size() > 64) {
            throw new IllegalArgumentException("ANA_FISCAL_ATTRIBUTE_BATCH_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("component_id", Types.BIGINT);
        table.addColumnMetadata("source_execution", Types.VARCHAR);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("presence", Types.VARCHAR);
        table.addColumnMetadata("nfse_series", Types.NVARCHAR);
        table.addColumnMetadata("evidence", Types.VARCHAR);
        for (final var item : batch) {
            Objects.requireNonNull(item);
            table.addRow(
                    item.componentId(),
                    item.sourceExecution().toString(),
                    item.revision(),
                    item.nfseSeries() == null ? "NULL" : "VALUE",
                    item.nfseSeries(),
                    "synthetic-fiscal-attribute-v1");
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC ref.usp_bind_analytic_fiscal_attributes ?,?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setStructured(2, "stg.analytic_lab_fiscal_attribute_batch", table);
                cancellation.throwIfCancellationRequested();
                try (var rows = sql.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != batch.size() || rows.next()) {
                        throw new SQLException("ANA_FISCAL_ATTRIBUTE_RECEIPT");
                    }
                    cancellation.throwIfCancellationRequested();
                    return batch.size();
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
