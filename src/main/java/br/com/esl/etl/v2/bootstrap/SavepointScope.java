package br.com.esl.etl.v2.bootstrap;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Savepoint;

/** Rolls back the borrowed connection without replacing a failure from the isolated work. */
record SavepointScope(Connection connection, Savepoint savepoint) implements AutoCloseable {
    static SavepointScope open(final Connection connection) throws SQLException {
        return new SavepointScope(connection, connection.setSavepoint());
    }

    @Override
    public void close() throws SQLException {
        connection.rollback(savepoint);
    }
}
