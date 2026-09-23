package br.com.esl.etl.v2.plataforma.persistencia.coletas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class ColetaTemporalLaboratorySessionCloseTest {
    @Test
    void rollbackFailureRemainsPrimaryWhenPhysicalCloseAlsoFails() throws Exception {
        check(true, true);
    }

    @Test
    void rollbackFailureStillClosesPhysicalConnection() throws Exception {
        check(true, false);
    }

    @Test
    void closeFailureRemainsObservableAfterSuccessfulRollback() throws Exception {
        check(false, true);
    }

    @Test
    void successfulCloseRollsBackExactlyOnce() throws Exception {
        check(false, false);
    }

    private void check(final boolean failRollback, final boolean failClose) throws Exception {
        final var calls = new ArrayList<String>();
        final var rollback = new SQLException("SYNTHETIC_ROLLBACK_FAILURE");
        final var close = new SQLException("SYNTHETIC_CLOSE_FAILURE");
        final var physical =
                (Connection)
                        Proxy.newProxyInstance(
                                Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class},
                                (proxy, method, args) -> {
                                    calls.add(method.getName());
                                    if (method.getName().equals("rollback") && failRollback) {
                                        throw rollback;
                                    }
                                    if (method.getName().equals("close") && failClose) {
                                        throw close;
                                    }
                                    return null;
                                });
        final var constructor =
                ColetaTemporalLaboratorySession.class.getDeclaredConstructor(Connection.class);
        constructor.setAccessible(true);
        final var session = constructor.newInstance(physical);
        if (failRollback || failClose) {
            final var failure = assertThrows(SQLException.class, session::close);
            assertSame(failRollback ? rollback : close, failure);
            assertEquals(failRollback && failClose ? 1 : 0, failure.getSuppressed().length);
            if (failRollback && failClose) {
                assertSame(close, failure.getSuppressed()[0]);
            }
        } else {
            session.close();
        }
        session.close();
        assertEquals(java.util.List.of("rollback", "close"), calls);
        assertThrows(SQLException.class, session::getConnection);
    }
}
