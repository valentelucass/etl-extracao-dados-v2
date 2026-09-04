package br.com.esl.etl.v2.contratos;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Executa tráfego somente quando o profile, a flag e uma configuração CONTRACT_* remota estiverem
 * presentes. Sem essa configuração, a integração é explicitamente ignorada e não abre conexão.
 */
class ContractRemoteEvidenceIT {

    @Test
    void collectsOnlySanitizedEvidenceWhenRemoteContractConfigurationIsPresent() {
        ContractTestGate.requireEnabled();
        Assumptions.assumeTrue(
                ContractTestConfiguration.hasAnyRemoteEnvironmentConfiguration(),
                "Nenhuma configuração CONTRACT_* remota foi fornecida.");
        new ContractRemoteEvidenceRunner()
                .collectAndWrite(ContractTestConfiguration.fromEnvironment());
    }
}
