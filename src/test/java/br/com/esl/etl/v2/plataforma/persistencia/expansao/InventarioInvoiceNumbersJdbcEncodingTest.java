package br.com.esl.etl.v2.plataforma.persistencia.expansao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import br.com.esl.etl.v2.modulos.inventario.aplicacao.InventarioInvoicesFieldParser;
import br.com.esl.etl.v2.plataforma.expansao.ExpansionValue;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Pure proxy: no driver, connection, SQL execution, database or schema operation. */
class InventarioInvoiceNumbersJdbcEncodingTest {
    @Test
    void existingBindAndArrayEncoderAcceptTheScopedTypeWithoutLosingItemPositionsOrRepetitions()
            throws Exception {
        final var row = JsonMapper.builder().build().createObjectNode();
        final var array = row.putArray(InventarioInvoicesFieldParser.FIELD);
        for (int index = 0; index < 33; index++) {
            array.add(index % 2 == 0 ? "A" : "B");
        }
        final var field = InventarioInvoicesFieldParser.parse(row);
        final var values = new HashMap<Integer, Object>();
        final var batches = new ArrayList<Map<Integer, Object>>();
        final PreparedStatement statement =
                (PreparedStatement)
                        Proxy.newProxyInstance(
                                getClass().getClassLoader(),
                                new Class<?>[] {PreparedStatement.class},
                                (proxy, method, args) -> {
                                    if (method.getName().startsWith("set")) {
                                        values.put((Integer) args[0], args[1]);
                                    } else if ("addBatch".equals(method.getName())) {
                                        batches.add(new HashMap<>(values));
                                    } else {
                                        throw new AssertionError(
                                                "Unexpected non-encoding statement method");
                                    }
                                    return null;
                                });
        final var bind =
                JdbcExpansionStaging.class.getDeclaredMethod(
                        "bind",
                        PreparedStatement.class,
                        int.class,
                        ExpansionValue.class,
                        String.class,
                        StringBuilder.class);
        bind.setAccessible(true);
        assertEquals(5, bind.invoke(null, statement, 1, field, "STRINGS", new StringBuilder()));
        assertEquals(33, values.get(4));
        values.clear();
        final var encode =
                JdbcExpansionStaging.class.getDeclaredMethod(
                        "array",
                        PreparedStatement.class,
                        UUID.class,
                        long.class,
                        String.class,
                        ExpansionValue.class);
        encode.setAccessible(true);
        encode.invoke(
                null, statement, UUID.randomUUID(), 1L, InventarioInvoicesFieldParser.FIELD, field);
        assertEquals(33, batches.size());
        for (int index = 0; index < batches.size(); index++) {
            assertEquals(index, batches.get(index).get(4));
            assertEquals(index % 2 == 0 ? "A" : "B", batches.get(index).get(5));
        }
    }
}
