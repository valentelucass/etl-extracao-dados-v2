package br.com.esl.etl.v2.plataforma.persistencia.controle;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageBatch;
import br.com.esl.etl.v2.modulos.coletas.domain.ColetaStageRecord;
import br.com.esl.etl.v2.plataforma.persistencia.coletas.JdbcSqlServerColetaStagingGateway;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Proxy;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/** Own session/gateway component proof; isolated JDBC seam never proves physical admission. */
class C78SampleStagingSessionTest {
    @Test
    void realGatewayUsesSameGuardedPhysicalTransactionAndOwnerAloneRollsBack() throws Exception {
        final var f = new C78DevEngineBankAdapterTest.Fixture(C78DevEngineBankAdapterTest.scope());
        final var jdbc = new SampleBoundary();
        final var independent = new C78DevEngineBankAdapterTest.SyntheticAdmission();
        final var original = System.in;
        final var output = System.out;
        System.setIn(f.frames());
        System.setOut(new PrintStream(new ByteArrayOutputStream()));
        try (var admission =
                f.adapter(independent, new AtomicInteger(), new AtomicInteger(), jdbc)
                        .prepare(f.scope)) {
            try (var session = admission.open()) {
                final var gateway = session.sampleStagingGateway();
                assertTrue(gateway instanceof JdbcSqlServerColetaStagingGateway);
                gateway.stage(
                        new ColetaStageBatch(
                                UUID.randomUUID(),
                                1,
                                List.of(ColetaStageRecord.quarantine(1, null, "OWN_TEST")),
                                Instant.EPOCH));
                assertEquals(1, jdbc.executed);
                assertEquals(0, jdbc.commits);
                assertEquals(0, jdbc.rollbacks);
                assertEquals(0, jdbc.closes);
                try (var borrower = session.dataSource().getConnection()) {
                    assertThrows(SQLException.class, borrower::rollback);
                    assertThrows(SQLException.class, borrower::commit);
                }
                session.checkpoint();
            }
        } finally {
            System.setIn(original);
            System.setOut(output);
        }
        assertEquals(1, jdbc.rollbacks);
        assertEquals(1, jdbc.closes);
    }

    private static final class SampleBoundary extends C78DevEngineBankAdapterTest.SyntheticJdbc {
        int executed;

        @Override
        Connection connection() {
            final var owner = super.connection();
            return (Connection)
                    Proxy.newProxyInstance(
                            getClass().getClassLoader(),
                            new Class<?>[] {Connection.class},
                            (p, m, a) -> {
                                if (m.getName().equals("prepareCall")) {
                                    return Proxy.newProxyInstance(
                                            getClass().getClassLoader(),
                                            new Class<?>[] {CallableStatement.class},
                                            (s, operation, args) -> {
                                                if (operation.getName().equals("executeBatch")) {
                                                    executed++;
                                                    return new int[] {1};
                                                }
                                                return defaultValue(operation.getReturnType());
                                            });
                                }
                                try {
                                    return m.invoke(owner, a);
                                } catch (java.lang.reflect.InvocationTargetException failed) {
                                    throw failed.getCause();
                                }
                            });
        }
    }
}
