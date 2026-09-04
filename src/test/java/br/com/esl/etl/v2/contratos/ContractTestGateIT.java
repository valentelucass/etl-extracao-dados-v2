package br.com.esl.etl.v2.contratos;

import org.junit.jupiter.api.Test;

/** Executado exclusivamente pelo perfil Failsafe contract-tests; não realiza chamadas remotas. */
class ContractTestGateIT {

    @Test
    void requiresExplicitContractTestAuthorization() {
        ContractTestGate.requireEnabled();
    }
}
