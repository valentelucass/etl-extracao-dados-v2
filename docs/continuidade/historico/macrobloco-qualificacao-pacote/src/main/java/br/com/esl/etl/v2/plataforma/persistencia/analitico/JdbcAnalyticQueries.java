package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlCatalog.Column;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlContract;
import br.com.esl.etl.v2.plataforma.analitico.AnalyticSqlValue;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.ColetaTemporalLaboratorySession;
import br.com.esl.etl.v2.plataforma.resiliencia.CancellationToken;
import java.io.IOException;
import java.io.Reader;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Closed, parameterized SQL projections streamed one typed row at a time in the rollback session.
 */
public final class JdbcAnalyticQueries {
    public static final int MAXIMUM_ROWS = 4096;
    public static final int MAXIMUM_CELL_CHARACTERS = 131072;
    public static final int MAXIMUM_ROW_CHARACTERS = 524288;
    private final ColetaTemporalLaboratorySession session;

    public JdbcAnalyticQueries(final ColetaTemporalLaboratorySession session) {
        this.session = Objects.requireNonNull(session);
    }

    public ReadReceipt read(
            final UUID run,
            final AnalyticSqlContract contract,
            final int referenceRevision,
            final int maximumRows,
            final CancellationToken token,
            final Consumer<Row> consumer)
            throws SQLException {
        Objects.requireNonNull(run);
        Objects.requireNonNull(contract);
        Objects.requireNonNull(token).throwIfCancellationRequested();
        Objects.requireNonNull(consumer);
        if (referenceRevision < 1
                || referenceRevision > 100000
                || maximumRows < 1
                || maximumRows > MAXIMUM_ROWS) {
            throw new IllegalArgumentException("ANA_QUERY_REQUEST_BOUND");
        }
        final var columns = AnalyticSqlCatalog.columns(contract);
        final String names =
                columns.stream()
                        .map(c -> "[" + c.name().replace("]", "]]") + "]")
                        .collect(Collectors.joining(","));
        final String statement =
                "SELECT TOP (?) "
                        + names
                        + " FROM "
                        + contract.localName()
                        + " WHERE run_id=?"
                        + (contract.revisionSelected() ? " AND reference_revision=?" : "")
                        + " ORDER BY "
                        + contract.technicalOrder();
        long count = 0;
        long characters = 0;
        int largestRow = 0;
        try (var connection = session.getConnection();
                var sql =
                        connection.prepareStatement(
                                statement,
                                ResultSet.TYPE_FORWARD_ONLY,
                                ResultSet.CONCUR_READ_ONLY)) {
            sql.setQueryTimeout(30);
            sql.setFetchSize(16);
            sql.setMaxRows(maximumRows + 1);
            sql.setInt(1, maximumRows + 1);
            sql.setString(2, run.toString());
            if (contract.revisionSelected()) {
                sql.setInt(3, referenceRevision);
            }
            try (var rows = sql.executeQuery()) {
                validateMetadata(rows.getMetaData(), columns);
                while (rows.next()) {
                    token.throwIfCancellationRequested();
                    if (count >= maximumRows) {
                        throw new SQLException("ANA_QUERY_ROW_LIMIT_EXCEEDED");
                    }
                    final var values = new ArrayList<AnalyticSqlValue>(columns.size());
                    int rowCharacters = 0;
                    for (final var column : columns) {
                        final var value = readValue(rows, column, token);
                        if (value instanceof AnalyticSqlValue.Missing && !column.nullable()) {
                            throw new SQLException("ANA_QUERY_UNEXPECTED_NULL");
                        }
                        if (value instanceof AnalyticSqlValue.Text text) {
                            rowCharacters = Math.addExact(rowCharacters, text.value().length());
                        }
                        if (rowCharacters > MAXIMUM_ROW_CHARACTERS) {
                            throw new SQLException("ANA_QUERY_ROW_CHARACTER_BOUND");
                        }
                        values.add(value);
                    }
                    consumer.accept(new Row(contract, values));
                    count++;
                    characters = Math.addExact(characters, rowCharacters);
                    largestRow = Math.max(largestRow, rowCharacters);
                }
            }
        }
        token.throwIfCancellationRequested();
        return new ReadReceipt(contract.id(), count, columns.size(), characters, largestRow, 16);
    }

    private static AnalyticSqlValue readValue(
            final ResultSet row, final Column column, final CancellationToken token)
            throws SQLException {
        final int index = column.ordinal();
        switch (column.type()) {
            case "nvarchar", "varchar", "char":
                final Reader reader = row.getCharacterStream(index);
                if (reader == null) {
                    return new AnalyticSqlValue.Missing();
                }
                return new AnalyticSqlValue.Text(readText(reader, token));
            case "decimal":
                final var decimal = row.getBigDecimal(index);
                if (decimal == null) {
                    return new AnalyticSqlValue.Missing();
                }
                try {
                    final var exact = decimal.setScale(column.scale(), RoundingMode.UNNECESSARY);
                    if (exact.precision() > column.precision()) {
                        throw new SQLException("ANA_QUERY_DECIMAL_PRECISION");
                    }
                    return new AnalyticSqlValue.Decimal(exact);
                } catch (final ArithmeticException failure) {
                    throw new SQLException("ANA_QUERY_DECIMAL_SCALE", failure);
                }
            case "bigint", "int", "smallint", "tinyint":
                final long integer = row.getLong(index);
                return row.wasNull()
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.IntegerValue(integer);
            case "bit":
                final boolean flag = row.getBoolean(index);
                return row.wasNull()
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.Flag(flag);
            case "date":
                final var date = row.getObject(index, LocalDate.class);
                return date == null
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.Date(date);
            case "time":
                final var time = row.getObject(index, LocalTime.class);
                return time == null
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.Time(time);
            case "datetime2":
                final var civil = row.getObject(index, LocalDateTime.class);
                return civil == null
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.CivilDateTime(civil);
            case "datetimeoffset":
                final var offset = row.getObject(index, OffsetDateTime.class);
                return offset == null
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.OffsetDateTimeValue(offset);
            case "uniqueidentifier":
                final var id = row.getString(index);
                return id == null
                        ? new AnalyticSqlValue.Missing()
                        : new AnalyticSqlValue.Identifier(UUID.fromString(id));
            default:
                throw new SQLException("ANA_QUERY_SQL_TYPE_UNSUPPORTED");
        }
    }

    private static String readText(final Reader reader, final CancellationToken token)
            throws SQLException {
        try (reader) {
            final var value = new StringBuilder();
            final char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                token.throwIfCancellationRequested();
                if (value.length() + read > MAXIMUM_CELL_CHARACTERS) {
                    throw new SQLException("ANA_QUERY_CELL_CHARACTER_BOUND");
                }
                value.append(buffer, 0, read);
            }
            return value.toString();
        } catch (final IOException failure) {
            throw new SQLException("ANA_QUERY_TEXT_READ", failure);
        }
    }

    private static void validateMetadata(
            final ResultSetMetaData actual, final List<Column> expected) throws SQLException {
        if (actual.getColumnCount() != expected.size()) {
            throw new SQLException("ANA_QUERY_METADATA_COLUMN_COUNT");
        }
        for (final var column : expected) {
            if (!column.name().equals(actual.getColumnLabel(column.ordinal()))
                    || !column.type()
                            .equals(
                                    actual.getColumnTypeName(column.ordinal())
                                            .toLowerCase(Locale.ROOT))) {
                throw new SQLException("ANA_QUERY_METADATA_DRIFT");
            }
            if (column.type().equals("decimal")
                    && (actual.getPrecision(column.ordinal()) != column.precision()
                            || actual.getScale(column.ordinal()) != column.scale())) {
                throw new SQLException("ANA_QUERY_METADATA_DECIMAL_DRIFT");
            }
        }
    }

    public record Row(AnalyticSqlContract contract, List<AnalyticSqlValue> values) {
        public Row {
            Objects.requireNonNull(contract);
            if (values == null || values.size() != contract.columns()) {
                throw new IllegalArgumentException("ANA_QUERY_ROW_COLUMNS");
            }
            values = List.copyOf(values);
        }
    }

    public record ReadReceipt(
            String query,
            long rows,
            int columns,
            long textCharacters,
            int maximumRowCharacters,
            int fetchSize) {}
}
