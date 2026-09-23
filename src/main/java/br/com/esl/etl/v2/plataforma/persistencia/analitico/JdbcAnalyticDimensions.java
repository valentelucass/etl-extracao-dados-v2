package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticDimensionBinding;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.Date;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/** At most 64 assignments cross JDBC together; the SQL procedure resolves conflicts atomically. */
public final class JdbcAnalyticDimensions {
    private final DataSource source;

    public JdbcAnalyticDimensions(final DataSource source) {
        this.source = Objects.requireNonNull(source);
    }

    public void associate(final UUID run, final UUID expansion, final UUID relational)
            throws SQLException {
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "INSERT ctl.analytic_lab_source_group VALUES(?,?,?,'synthetic-composition-v1')")) {
            sql.setString(1, run.toString());
            sql.setString(2, expansion.toString());
            sql.setString(3, relational.toString());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }

    public int bindBatch(
            final UUID run,
            final List<AnalyticDimensionBinding> bindings,
            final CancellationToken cancellation)
            throws SQLException {
        if (bindings == null || bindings.isEmpty() || bindings.size() > 64) {
            throw new IllegalArgumentException("ANA_BINDING_BATCH_BOUND");
        }
        cancellation.throwIfCancellationRequested();
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("entity", Types.VARCHAR);
        table.addColumnMetadata("source_key", Types.NVARCHAR);
        table.addColumnMetadata("source_execution", Types.VARCHAR);
        table.addColumnMetadata("role", Types.VARCHAR);
        table.addColumnMetadata("dimension_kind", Types.VARCHAR);
        table.addColumnMetadata("entity_key", Types.VARCHAR);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("valid_from", Types.DATE);
        table.addColumnMetadata("valid_to_exclusive", Types.DATE);
        table.addColumnMetadata("active", Types.BIT);
        table.addColumnMetadata("previous_entity_key", Types.VARCHAR);
        table.addColumnMetadata("evidence", Types.VARCHAR);
        for (final var binding : bindings) {
            Objects.requireNonNull(binding);
            table.addRow(
                    binding.entity().name(),
                    binding.sourceKey(),
                    binding.sourceExecution().toString(),
                    binding.role().name(),
                    binding.role().dimension(),
                    binding.entityKey(),
                    binding.revision(),
                    Date.valueOf(binding.from()),
                    Date.valueOf(binding.toExclusive()),
                    binding.active(),
                    binding.previousEntityKey(),
                    binding.evidence());
        }
        try (var connection = source.getConnection();
                var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC ref.usp_bind_analytic_dimensions ?,?")) {
            sql.setString(1, run.toString());
            sql.setStructured(2, "ref.analytic_lab_dimension_binding_batch", table);
            sql.setQueryTimeout(10);
            cancellation.throwIfCancellationRequested();
            try (var row = sql.executeQuery()) {
                if (!row.next() || row.getInt(1) != bindings.size()) {
                    throw new SQLException("ANA_BINDING_RECEIPT");
                }
                return bindings.size();
            }
        }
    }

    public void attachExecution(
            final UUID run, final AnalyticDimensionBinding.Entity entity, final UUID execution)
            throws SQLException {
        if (entity != AnalyticDimensionBinding.Entity.USUARIO
                && entity != AnalyticDimensionBinding.Entity.COT) {
            throw new IllegalArgumentException("ANA_EXECUTION_SOURCE_KIND");
        }
        try (var connection = source.getConnection();
                var sql =
                        connection.prepareStatement(
                                "INSERT ctl.analytic_lab_execution_source VALUES(?,?,?,'synthetic-composition-v1')")) {
            sql.setString(1, run.toString());
            sql.setString(2, entity.name());
            sql.setString(3, execution.toString());
            sql.setQueryTimeout(10);
            sql.executeUpdate();
        }
    }
}
