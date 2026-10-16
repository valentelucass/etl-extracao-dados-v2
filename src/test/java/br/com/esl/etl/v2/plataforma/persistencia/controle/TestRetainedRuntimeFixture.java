package br.com.esl.etl.v2.plataforma.persistencia.controle;

import java.sql.SQLException;
import java.util.UUID;

/** Offline bridge to Banco's fake JDBC; never opens a socket or resolves a real credential. */
public final class TestRetainedRuntimeFixture {
    private final TestRetainedSqlSessionTest.Fake fake = new TestRetainedSqlSessionTest.Fake();

    public TestRetainedRuntimeFixture(final int rows) {
        fake.typedRows = rows;
        fake.genericRows = rows;
    }

    public TestRetainedSqlSession open(final UUID executionId) throws SQLException {
        return TestRetainedSqlSession.admitted(
                fake.connection,
                new TestRetainedSqlSession.Limits(1200, 1000, 10, 60),
                executionId,
                () -> fake.clock,
                0);
    }

    public void failCommit() {
        fake.failCommit = true;
    }

    public void failClose() {
        fake.failClose = true;
    }

    public void failPost() {
        fake.failPost = true;
    }

    public int commits() {
        return fake.commits;
    }

    public int rollbacks() {
        return fake.rollbacks;
    }

    public int closes() {
        return fake.closes;
    }
}
