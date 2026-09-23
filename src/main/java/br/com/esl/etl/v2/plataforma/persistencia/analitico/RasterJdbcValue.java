package br.com.esl.etl.v2.plataforma.persistencia.analitico;

import br.com.esl.etl.v2.modulos.raster.domain.RasterTime;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/** Explicit typed binding keeps ABSENT/NULL/VALUE, original wire and sentinel provenance. */
final class RasterJdbcValue {
    private RasterJdbcValue() {}

    private static int prefix(
            final PreparedStatement statement, final int index, final ExpansionValue<?> value)
            throws SQLException {
        statement.setString(index, value.presence().name());
        statement.setString(index + 1, value.wire().name());
        statement.setString(index + 2, value.raw());
        return index + 3;
    }

    static int text(
            final PreparedStatement statement, final int index, final ExpansionValue<String> value)
            throws SQLException {
        final int next = prefix(statement, index, value);
        statement.setString(next, value.value());
        return next + 1;
    }

    static int count(
            final PreparedStatement statement, final int index, final ExpansionValue<Integer> value)
            throws SQLException {
        final int next = prefix(statement, index, value);
        statement.setObject(next, value.value(), java.sql.Types.INTEGER);
        return next + 1;
    }

    static int decimal(
            final PreparedStatement statement,
            final int index,
            final ExpansionValue<BigDecimal> value)
            throws SQLException {
        final int next = prefix(statement, index, value);
        statement.setBigDecimal(next, value.value());
        return next + 1;
    }

    static int time(
            final PreparedStatement statement,
            final int index,
            final ExpansionValue<RasterTime> value)
            throws SQLException {
        int next = prefix(statement, index, value);
        final RasterTime parsed = value.value();
        final var instant = parsed == null ? null : parsed.instant();
        statement.setObject(
                next++, instant == null ? null : instant.getEpochSecond(), java.sql.Types.BIGINT);
        statement.setObject(
                next++, instant == null ? null : instant.getNano(), java.sql.Types.INTEGER);
        statement.setObject(
                next++, parsed == null ? null : parsed.offsetSeconds(), java.sql.Types.INTEGER);
        statement.setObject(next++, parsed == null ? null : parsed.sentinel(), java.sql.Types.BIT);
        return next;
    }
}
