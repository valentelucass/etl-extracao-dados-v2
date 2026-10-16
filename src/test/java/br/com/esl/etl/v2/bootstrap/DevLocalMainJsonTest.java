package br.com.esl.etl.v2.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import br.com.esl.etl.v2.plataforma.persistencia.devlocal.DevLocalLedger;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class DevLocalMainJsonTest {
    @Test
    void protocolReadbackPreservesExactDecimalsScaleAndFingerprint() throws Exception {
        final var mapper = DevLocalMainJson.mapper();
        final var original =
                mapper.createObjectNode()
                        .put("small", new BigDecimal("1.2300"))
                        .put("large", new BigDecimal("9007199254740993.0001"))
                        .put("fraction", new BigDecimal("9.876543210987654321012345678900"));
        final var encoded = mapper.writeValueAsBytes(original);
        final var persisted = mapper.readTree(encoded);
        assertEquals(original, persisted);
        assertEquals(4, persisted.path("small").decimalValue().scale());
        assertEquals(
                new BigDecimal("9007199254740993.0001"), persisted.path("large").decimalValue());
        assertEquals(30, persisted.path("fraction").decimalValue().scale());
        assertEquals(
                DevLocalLedger.sha256(encoded),
                DevLocalLedger.sha256(mapper.writeValueAsBytes(persisted)));
    }

    @Test
    void protocolReadbackRefusesDuplicateAndTrailingDocuments() {
        final var mapper = DevLocalMainJson.mapper();
        assertThrows(java.io.IOException.class, () -> mapper.readTree("{\"x\":1,\"x\":2}"));
        assertThrows(java.io.IOException.class, () -> mapper.readTree("{\"x\":1} {}"));
    }
}
