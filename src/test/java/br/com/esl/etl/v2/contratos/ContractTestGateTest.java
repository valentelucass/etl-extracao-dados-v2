package br.com.esl.etl.v2.contratos;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ContractTestGateTest {

    @Test
    void requiresBothTheFailsafeProfileMarkerAndExplicitAuthorization() {
        assertThrows(
                IllegalStateException.class,
                () -> ContractTestGate.requireEnabled("false", "false"));
        assertThrows(
                IllegalStateException.class,
                () -> ContractTestGate.requireEnabled("false", "true"));
        assertThrows(
                IllegalStateException.class,
                () -> ContractTestGate.requireEnabled("true", "false"));
        assertDoesNotThrow(() -> ContractTestGate.requireEnabled("true", "true"));
    }
}
