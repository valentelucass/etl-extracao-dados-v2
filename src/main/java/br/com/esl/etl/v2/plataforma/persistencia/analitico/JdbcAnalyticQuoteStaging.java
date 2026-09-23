package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.modulos.cotacoes.aplicacao.CotacaoStagingGateway;
import br.com.esl.etl.v2.modulos.cotacoes.domain.CotacaoStageBatch;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFieldCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticFieldCatalog.Kind;
import br.com.esl.etl.v2.plataforma.fonte.dataexport.AnalyticQuoteMapper;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.persistencia.cotacoes.JdbcSqlServerCotacaoStagingGateway;
import br.com.esl.etl.v2.plataforma.persistencia.staging.StagingPersistenceException;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.sqlserver.jdbc.SQLServerDataTable;
import com.microsoft.sqlserver.jdbc.SQLServerPreparedStatement;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;

/** Existing quote staging plus one typed TVP, bounded by page and byte budget. */
public final class JdbcAnalyticQuoteStaging implements CotacaoStagingGateway {
    private final ColetaTemporalLaboratorySession session;
    private final UUID run;

    public JdbcAnalyticQuoteStaging(final ColetaTemporalLaboratorySession session, final UUID run) {
        this.session = Objects.requireNonNull(session);
        this.run = Objects.requireNonNull(run);
    }

    public void begin(final UUID execution, final String fingerprint) throws SQLException {
        if (execution == null || fingerprint == null || !fingerprint.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("ANA_QUOTE_CAPTURE_BINDING");
        }
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                "EXEC ctl.usp_begin_analytic_quote_capture ?,?,?")) {
            sql.setQueryTimeout(10);
            sql.setString(1, run.toString());
            sql.setString(2, execution.toString());
            sql.setString(3, fingerprint);
            sql.execute();
        }
    }

    @Override
    public void stage(final CotacaoStageBatch batch) {
        stage(batch, CancellationToken.none());
    }

    @Override
    public void stage(final CotacaoStageBatch batch, final CancellationToken cancellation) {
        Objects.requireNonNull(batch);
        Objects.requireNonNull(cancellation).throwIfCancellationRequested();
        if (batch.size() > 16) {
            throw new IllegalArgumentException("ANA_QUOTE_STAGE_PAGE_BOUND");
        }
        try (var connection = session.getConnection()) {
            final var savepoint = connection.setSavepoint();
            try {
                final var table = table(batch);
                new JdbcSqlServerCotacaoStagingGateway(session).stage(batch, cancellation);
                try (var sql =
                        (SQLServerPreparedStatement)
                                connection.prepareStatement(
                                        "EXEC stg.usp_stage_analytic_quote_attributes ?,?,?,?")) {
                    sql.setQueryTimeout(10);
                    sql.setString(1, run.toString());
                    sql.setString(2, batch.executionId().toString());
                    sql.setInt(3, batch.batchNumber());
                    sql.setStructured(4, "stg.analytic_quote_attribute_batch", table);
                    cancellation.throwIfCancellationRequested();
                    try (var rows = sql.executeQuery()) {
                        if (!rows.next() || rows.getInt(1) != batch.size() || rows.next()) {
                            throw new SQLException("ANA_QUOTE_ATTRIBUTE_RECEIPT");
                        }
                    }
                    cancellation.throwIfCancellationRequested();
                }
            } catch (final SQLException | RuntimeException failure) {
                try {
                    connection.rollback(savepoint);
                } catch (final SQLException rollback) {
                    failure.addSuppressed(rollback);
                }
                throw failure;
            }
        } catch (final SQLException failure) {
            throw new StagingPersistenceException("analytic quote attribute batch", failure);
        }
    }

    private static SQLServerDataTable table(final CotacaoStageBatch batch) throws SQLException {
        final var table = new SQLServerDataTable();
        table.addColumnMetadata("input_ordinal", Types.INTEGER);
        table.addColumnMetadata("source_key", Types.NVARCHAR);
        table.addColumnMetadata("comparison_bytes", Types.VARBINARY);
        for (final var field : AnalyticFieldCatalog.quotes()) {
            table.addColumnMetadata(field.name() + "_p", Types.VARCHAR);
            table.addColumnMetadata(field.name() + "_w", Types.VARCHAR);
            table.addColumnMetadata(field.name() + "_raw", Types.NVARCHAR);
            table.addColumnMetadata(
                    field.name(),
                    field.kind() == Kind.DECIMAL
                            ? Types.DECIMAL
                            : field.kind() == Kind.INSTANT ? Types.BIGINT : Types.NVARCHAR);
            if (field.kind() == Kind.INSTANT) {
                table.addColumnMetadata(field.name() + "_nano", Types.INTEGER);
            }
        }
        int bytes = 0;
        for (int index = 0; index < batch.size(); index++) {
            final var row = batch.recordAt(index);
            if (row.sourceKey() == null || row.quarantineReasonCode() != null) {
                throw new IllegalArgumentException("ANA_QUOTE_ORIGINAL_QUARANTINE");
            }
            final br.com.esl.etl.v2.plataforma.analitico.AnalyticQuoteAttributes attributes;
            try {
                attributes =
                        new AnalyticQuoteMapper()
                                .map(new ObjectMapper().readTree(row.payloadJson()));
            } catch (final IOException failure) {
                throw new IllegalArgumentException("ANA_QUOTE_PAYLOAD_JSON", failure);
            }
            if (!attributes.valid()) {
                throw new IllegalArgumentException("ANA_QUOTE_ATTRIBUTE_PARSE");
            }
            final var values = attributes.fields();
            final var comparison = new AnalyticFieldComparison();
            values.forEach(comparison::value);
            final byte[] compared = comparison.bytesLimited(262144);
            bytes = Math.addExact(bytes, compared.length);
            if (bytes > 262144) {
                throw new IllegalArgumentException("ANA_QUOTE_STAGE_BYTE_BOUND");
            }
            final var cells = new ArrayList<Object>();
            cells.add(row.inputOrdinal());
            cells.add(row.sourceKey().storageValue());
            cells.add(compared);
            for (int field = 0; field < AnalyticFieldCatalog.quotes().size(); field++) {
                final var value = values.get(field);
                cells.add(value.presence().name());
                cells.add(value.wire().name());
                cells.add(value.raw());
                if (AnalyticFieldCatalog.quotes().get(field).kind() == Kind.INSTANT) {
                    final Instant instant = (Instant) value.value();
                    cells.add(instant == null ? null : instant.getEpochSecond());
                    cells.add(instant == null ? null : instant.getNano());
                } else {
                    cells.add(value.value());
                }
            }
            table.addRow(cells.toArray());
        }
        return table;
    }
}
