package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Set-based seals avoid a JDBC round trip for every manifest while preserving the original gate.
 */
public final class JdbcAnalyticManifestCompositions {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticManifestCompositions(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public int seal(
            final UUID run,
            final List<Declaration> declarations,
            final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (declarations == null || declarations.isEmpty() || declarations.size() > 64) {
            throw new IllegalArgumentException("ANA_COMPOSITION_BATCH_BOUND");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("manifest_key", Types.NVARCHAR);
        table.addColumnMetadata("manifest_execution", Types.VARCHAR);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("expected_direct_freights", Types.INTEGER);
        for (final var declaration : declarations) {
            Objects.requireNonNull(declaration);
            table.addRow(
                    declaration.key(),
                    declaration.execution().toString(),
                    declaration.revision(),
                    declaration.directFreights());
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var statement =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC ctl.usp_seal_analytic_manifest_compositions ?,?")) {
                statement.setQueryTimeout(20);
                statement.setString(1, run.toString());
                statement.setStructured(2, "ctl.analytic_manifest_composition_batch", table);
                try (var row = statement.executeQuery()) {
                    if (!row.next() || row.getInt(1) != declarations.size() || row.next()) {
                        throw new SQLException("ANA_COMPOSITION_BATCH_RECEIPT");
                    }
                }
                cancellation.throwIfCancellationRequested();
                return declarations.size();
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

    public record Declaration(String key, UUID execution, int revision, int directFreights) {
        public Declaration {
            Objects.requireNonNull(execution);
            if (key == null
                    || !key.matches("INTEGER:[1-9][0-9]{0,18}")
                    || revision < 1
                    || revision > 100000
                    || directFreights < 0
                    || directFreights > 100000) {
                throw new IllegalArgumentException("ANA_COMPOSITION_DECLARATION");
            }
        }
    }
}
