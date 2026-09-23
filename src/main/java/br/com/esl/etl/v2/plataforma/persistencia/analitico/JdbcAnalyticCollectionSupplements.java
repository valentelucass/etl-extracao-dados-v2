package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticCollectionSupplement;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFieldCatalog;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A single TVP associates at most 16 typed supplements with sealed source observations. */
public final class JdbcAnalyticCollectionSupplements {
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticCollectionSupplements(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public int bind(
            final UUID run, final List<Binding> bindings, final CancellationToken cancellation)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (bindings == null || bindings.isEmpty() || bindings.size() > 16) {
            throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_BATCH");
        }
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("source_key", Types.NVARCHAR);
        table.addColumnMetadata("source_execution", Types.NVARCHAR);
        table.addColumnMetadata("revision", Types.INTEGER);
        table.addColumnMetadata("comparison_bytes", Types.VARBINARY);
        table.addColumnMetadata("cancellation_user_key", Types.NVARCHAR);
        table.addColumnMetadata("destroy_user_key", Types.NVARCHAR);
        for (int index = 0; index < AnalyticFieldCatalog.collectionSupplement().size(); index++) {
            final String name = AnalyticFieldCatalog.collectionSupplement().get(index);
            table.addColumnMetadata(name + "_p", Types.VARCHAR);
            table.addColumnMetadata(name + "_w", Types.VARCHAR);
            table.addColumnMetadata(name + "_raw", Types.NVARCHAR);
            table.addColumnMetadata(
                    name, index == 0 || index == 11 ? Types.BIGINT : Types.NVARCHAR);
            if (index == 11) {
                table.addColumnMetadata(name + "_nano", Types.INTEGER);
            }
        }
        int bytes = 0;
        for (final var binding : bindings) {
            final var comparison = new AnalyticFieldComparison();
            binding.attributes().fields().forEach(comparison::value);
            comparison.text(binding.cancellationUserKey());
            comparison.text(binding.destroyUserKey());
            final byte[] compared = comparison.bytesLimited(262144);
            bytes = Math.addExact(bytes, compared.length);
            if (bytes > 262144) {
                throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_BYTES");
            }
            final var cells = new ArrayList<Object>();
            cells.add(binding.sourceKey());
            cells.add(binding.execution().toString());
            cells.add(binding.revision());
            cells.add(compared);
            cells.add(binding.cancellationUserKey());
            cells.add(binding.destroyUserKey());
            final var values = binding.attributes().fields();
            for (int index = 0; index < values.size(); index++) {
                final var value = values.get(index);
                cells.add(value.presence().name());
                cells.add(value.wire().name());
                cells.add(value.raw());
                if (index == 0) {
                    cells.add(
                            value.value() == null
                                    ? null
                                    : ((LocalTime) value.value()).toNanoOfDay());
                } else if (index == 11) {
                    final var instant = (Instant) value.value();
                    cells.add(instant == null ? null : instant.getEpochSecond());
                    cells.add(instant == null ? null : instant.getNano());
                } else {
                    cells.add(value.value());
                }
            }
            table.addRow(cells.toArray());
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try (var sql =
                    (SQLServerPreparedStatement)
                            connection.prepareStatement(
                                    "EXEC stg.usp_bind_analytic_collection_supplements ?,?")) {
                sql.setQueryTimeout(10);
                sql.setString(1, run.toString());
                sql.setStructured(2, "stg.analytic_collection_supplement_batch", table);
                cancellation.throwIfCancellationRequested();
                try (var rows = sql.executeQuery()) {
                    if (!rows.next() || rows.getInt(1) != bindings.size() || rows.next()) {
                        throw new SQLException("ANA_COLLECTION_SUPPLEMENT_RECEIPT");
                    }
                }
                cancellation.throwIfCancellationRequested();
                return bindings.size();
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

    public record Binding(
            String sourceKey,
            UUID execution,
            int revision,
            AnalyticCollectionSupplement attributes,
            String cancellationUserKey,
            String destroyUserKey) {
        public Binding {
            if (sourceKey == null
                    || !sourceKey.matches("INTEGER:[1-9][0-9]{0,18}")
                    || revision < 1
                    || revision > 100000) {
                throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_IDENTITY");
            }
            Objects.requireNonNull(execution);
            if (!Objects.requireNonNull(attributes).valid()) {
                throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_ATTRIBUTE");
            }
            for (final String key : new String[] {cancellationUserKey, destroyUserKey}) {
                if (key != null && (key.isBlank() || key.length() > 256)) {
                    throw new IllegalArgumentException("ANA_COLLECTION_SUPPLEMENT_USER_KEY");
                }
            }
        }
    }
}
