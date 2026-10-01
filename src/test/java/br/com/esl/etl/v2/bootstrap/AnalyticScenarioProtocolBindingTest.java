package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

class AnalyticScenarioProtocolBindingTest {
    @Test
    void bindsSecondProtocolOnlyForExactLegacyScopeInsideLocalTransaction() throws Exception {
        final var sql = new AtomicReference<String>();
        final var binds = new HashMap<Integer, String>();
        final var calls = new AtomicInteger();
        final PreparedStatement statement =
                proxy(
                        PreparedStatement.class,
                        (method, args) -> {
                            if (method.equals("setNString")) {
                                binds.put((Integer) args[0], (String) args[1]);
                            }
                            if (method.equals("executeUpdate")) {
                                calls.incrementAndGet();
                                return 1;
                            }
                            return null;
                        });
        final Connection connection =
                proxy(
                        Connection.class,
                        (method, args) -> {
                            if (method.equals("prepareStatement")) {
                                sql.set((String) args[0]);
                                return statement;
                            }
                            return null;
                        });
        final DataSource source =
                proxy(
                        DataSource.class,
                        (method, args) -> {
                            if (method.equals("getConnection")) {
                                calls.incrementAndGet();
                                return connection;
                            }
                            return null;
                        });

        assertEquals(
                "ANA_LEGACY_PROTOCOL_SCOPE",
                assertThrows(
                                IllegalArgumentException.class,
                                () ->
                                        AnalyticScenarioRuntime.bindLegacyQuoteProtocol(
                                                source, "local_v2"))
                        .getMessage());
        assertEquals(0, calls.get());

        AnalyticScenarioRuntime.bindLegacyQuoteProtocol(source, "LOCAL_V2");
        assertEquals(2, calls.get());
        assertEquals(Map.of(1, "LOCAL_V2", 2, "LOCAL_V2"), binds);
        assertTrue(sql.get().contains("@@TRANCOUNT=0 OR DB_NAME()<>N'ETL_SISTEMA_V2_SHADOW'"));
        assertTrue(sql.get().contains("s.active=1"));
        assertTrue(sql.get().contains("b.source_kind=N'GRAPHQL' COLLATE Latin1_General_100_BIN2"));
        assertTrue(
                sql.get().contains("b.source_kind=N'DATA_EXPORT' COLLATE Latin1_General_100_BIN2"));
        assertTrue(sql.get().contains("NOT EXISTS(SELECT 1 FROM ctl.source_protocol_binding b"));
    }

    private interface Invocation {
        Object invoke(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(final Class<T> type, final Invocation invocation) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (instance, method, args) -> invocation.invoke(method.getName(), args));
    }
}
