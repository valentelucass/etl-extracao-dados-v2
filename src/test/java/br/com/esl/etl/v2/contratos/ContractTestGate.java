package br.com.esl.etl.v2.contratos;

/** Segunda trava obrigatória antes de qualquer tráfego da suíte de contrato. */
public final class ContractTestGate {

    private static final String ENABLED_PROPERTY = "contract.tests.enabled";
    private static final String PROFILE_ACTIVE_PROPERTY = "contract.tests.profile.active";

    private ContractTestGate() {}

    public static void requireEnabled() {
        requireEnabled(
                System.getProperty(ENABLED_PROPERTY), System.getProperty(PROFILE_ACTIVE_PROPERTY));
    }

    static void requireEnabled(final String enabledValue, final String profileActiveValue) {
        if (!Boolean.parseBoolean(enabledValue) || !Boolean.parseBoolean(profileActiveValue)) {
            throw new IllegalStateException(
                    "Testes de contrato exigem o perfil contract-tests e -Dcontract.tests.enabled=true.");
        }
    }
}
